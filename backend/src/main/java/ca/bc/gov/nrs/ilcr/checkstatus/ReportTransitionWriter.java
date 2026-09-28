package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write half of a status transition: status row, audit sweep, category advance, in one
 * transaction. {@link #write} (Verify) and {@link #writeReversal} (Set to Draft, Set to Submit)
 * each serve both tracks, dispatched by a {@link ScheduleTrack} parameter.
 *
 * <p>A separate bean rather than a method on {@link ReportTrackTransitionService} because the
 * boundary has to sit <em>here</em> and nowhere wider. Legacy ran its eleven validators in the
 * managed bean <strong>before</strong> {@code SubmitReportDAO.submitReport} opened a transaction
 * ({@code CheckStatusMB.submitReport:247-261} gates, {@code :271} calls), and it took no row lock
 * at all. Keeping the gate inside the write transaction — as an earlier cut did — held a connection
 * across the eleven-schedule fan-out, which {@code CheckStatusSweepService}'s own javadoc warns
 * against in bold. Spring's proxying means self-invocation would not start a transaction, so the
 * split is what makes "gate outside, write inside" real rather than annotational.
 *
 * <p>Consequence, accepted deliberately as legacy parity (ratified 2026-09-16): the gate's verdict
 * is read before the write begins, so a concurrent schedule save can invalidate it in between.
 * Legacy had that same window and a wider one, since its verdict came from a {@code @ViewScoped}
 * in-memory snapshot rather than from the database.
 *
 * <p>{@link #write} is used by VERIFY only, and {@link #writeReversal} by the two admin reversals —
 * same three steps in the same order, differing only in the status statement, because Set to Draft
 * and Set to Submit write no identity pair at all (deviation (S) for Set to Submit). They are two
 * methods, not one dispatch: {@link #write} always writes the AUDITOR pair, so a reversal routed
 * through it would overwrite a recorded auditor that legacy preserved. Submit reaches the same
 * {@link ReportTrackTransitionRepository} from inside its own transaction, because 15.3 ruled the
 * opposite boundary for that transition &mdash; lock the status row first, then gate inside the
 * write. Both rulings stand; this class is where the legacy-faithful one is expressed, and the
 * repository beneath it is now shared. What the two transitions <em>do</em> share is {@link
 * #stampAuditColumns}: the twenty-statement audit sweep is identical for both, so it lives here
 * once and submit calls it from inside its own transaction.
 */
@Service
@Slf4j
public class ReportTransitionWriter {

  private final ReportTrackTransitionRepository repository;
  private final MillUserXrefRepository millUserXrefRepository;

  public ReportTransitionWriter(
      ReportTrackTransitionRepository repository, MillUserXrefRepository millUserXrefRepository) {
    this.repository = repository;
    this.millUserXrefRepository = millUserXrefRepository;
  }

  /**
   * Apply the transition on one track: status row first, then the audit sweep, then the category
   * advance. The track selects all three, exactly as legacy's boolean {@code isSchedule11Submitted}
   * did ({@code SubmitReportDAO.submitReportSchedule11():161-164} against {@code
   * submitReport():61-142}): which status column and identity statement, which row family is
   * stamped, and which category rows advance. Nothing on the other track is named.
   *
   * @param track the track to transition
   * @param millId the mill
   * @param year the reporting year
   * @param expectedStatus the status code the track must still hold when the write lands
   * @param targetStatus the status code to move the track to
   * @param categoryState the category state the pair table yields for this move
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, or null to record no auditor
   * @return {@code targetStatus}, once persisted
   * @throws ReportTransitionRejectedException 409 &mdash; the track left {@code expectedStatus}
   *     between the caller's unlocked read and this write (Story 18.1's review; see {@link
   *     ReportTrackTransitionRepository#updateTrackStatusWithAuditor})
   * @throws ReportSubmissionException the writes could not be persisted, or left the report
   *     half-transitioned
   */
  @Transactional
  public String write(
      ScheduleTrack track,
      long millId,
      int year,
      String expectedStatus,
      String targetStatus,
      String categoryState,
      String actingUser,
      String actorGuid) {
    Objects.requireNonNull(track, "track");
    try {
      // Legacy recorded the auditor only when the acting user held an ILCR_MILL_USER_XREF row for
      // the mill, and wrote NULLs otherwise (SubmitReportDAO:405-410). Resolved through the same
      // repository submit's licensee lookup uses, so one composite-FK rule serves both.
      Long auditorMillId =
          actorGuid == null || actorGuid.isBlank()
              ? null
              : millUserXrefRepository
                  .findAssignment(millId, actorGuid)
                  .map(x -> x.millId())
                  .orElse(null);
      String auditorGuid = auditorMillId == null ? null : actorGuid;

      int updated =
          track == ScheduleTrack.SCHEDULE_11
              ? repository.updateSilvicultureTrackStatusWithAuditor(
                  millId,
                  year,
                  targetStatus,
                  expectedStatus,
                  auditorMillId,
                  auditorGuid,
                  actingUser)
              : repository.updateTrackStatusWithAuditor(
                  millId,
                  year,
                  targetStatus,
                  expectedStatus,
                  auditorMillId,
                  auditorGuid,
                  actingUser);
      if (updated != 1) {
        // Zero rows is a REFUSAL, not a failure: the track left expectedStatus between the
        // service's unlocked read and this write. Legacy's generic text, because VERIFY's other
        // refusals carry it too (Epic 17's parity tie-breaker) — null selects it.
        log.info(
            "{}->{} 409: the {} track left {} for millId={} year={} before the write",
            expectedStatus,
            targetStatus,
            track,
            expectedStatus,
            millId,
            year);
        throw new ReportTransitionRejectedException();
      }

      // ORDER IS LOAD-BEARING — stamps first, category second, as legacy did.
      // SubmitReportDAO.submitReport:75-118 interleaves them PER SCHEDULE (stamp schedule N, then
      // advance category N); these are batched (all stamps, then all advances). Per row the
      // trigger pair below sees the same values either way, so the batching is not a divergence —
      // but the relative order of stamp-before-advance is. Delivery derives
      // ILCR_*_AUD.RECORD_STATE_CODE in a BEFORE-UPDATE row
      // trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the ONLY pair that
      // writes an 'S' snapshot — the snapshot Epic 16's original-value indicators read. S->V is
      // indifferent (both (A,V) and (V,V) map to 'V'), but D->S is not, and 15.3/Epic 18 extend
      // this component. Advance the category first and submit stamps (A,S)->'A', no 'S' snapshot is
      // ever written, and every original-value indicator silently serves nothing. The test snapshot
      // has no triggers, so no acceptance test here can catch a re-inversion. Schedule 11 follows
      // the same order (legacy submitReportSchedule11:161-164); its S->V is indifferent too.
      int stamped =
          track == ScheduleTrack.SCHEDULE_11
              ? stampSchedule11AuditColumns(millId, year, actingUser)
              : stampAuditColumns(millId, year, actingUser);

      int categories = 0;
      for (String categoryId : track.categoryIds()) {
        categories +=
            repository.advanceCategoryState(millId, year, categoryId, categoryState, actingUser);
      }
      // Legacy dereferenced findReportCategory.uniqueResult() per schedule, so a missing category
      // row failed the whole transition at once. Set-based UPDATEs skip it silently instead, and a
      // mill/year enrolled by an interrupted year-open really does carry fewer than eleven rows
      // (ReportingYearService's EnrolmentState.PARTIAL, error.mill.activate.partialrecords). Fail
      // loudly rather than commit a half-transitioned report behind a 200.
      int expectedCategories = track.categoryIds().size();
      if (categories != expectedCategories) {
        log.error(
            "Verify failed for millId={} year={}: expected {} {} category rows to advance but {}"
                + " were updated",
            millId,
            year,
            expectedCategories,
            track,
            categories);
        throw new ReportSubmissionException();
      }

      log.info(
          "Transitioned {} for millId={} year={} to {}: {} category rows -> {}, {} audit rows"
              + " stamped, auditor recorded={}",
          track,
          millId,
          year,
          targetStatus,
          categories,
          categoryState,
          stamped,
          auditorMillId != null);

      return targetStatus;
    } catch (DataAccessException persistence) {
      log.error("Transition failed to persist for millId={} year={}", millId, year, persistence);
      throw new ReportSubmissionException();
    }
  }

  /**
   * Apply one of the two admin reversals — Submitted&rarr;Draft or Verified&rarr;Submitted — on one
   * track, in the same three steps and the same order as {@link #write}, differing only in the
   * status statement. The track selects the status column, the row family stamped and the category
   * rows advanced, exactly as legacy's {@code submitReportSchedule11():161-164} differed from
   * {@code submitReport()}.
   *
   * <p>It takes no actor GUID, and that absence is the whole point. Legacy keyed the identity write
   * on the TARGET status alone ({@code SubmitReportDAO.updateILCRMillReportStatus:401-412}): a
   * {@code 'D'} target skips the association block entirely, and a {@code 'S'} target wrote the
   * LICENSEE pair from the acting user's cross-reference — which for an ADMIN reversing a
   * verification means overwriting the record of who actually submitted, with NULLs whenever that
   * admin has no assignment for the mill. Neither reversal writes either pair, on either track
   * (deviation (S)). On Schedule 11 both pairs are also shared with Schedules 1&ndash;10, and the
   * LICENSEE pair is the one legacy's Set to Submit would have overwritten.
   *
   * <p><strong>Order: status, then stamps, then category.</strong> The delivery trigger records the
   * (category state, track status) pair holding when each row is stamped. Status before the stamps
   * is the load-bearing half: a Set to Draft that moved the category first would stamp {@code
   * (D,S)}, a spurious {@code 'S'} snapshot that re-baselines the original-value indicators to the
   * pre-reversal values. After the status has moved, stamps and category are interchangeable: Set
   * to Draft records {@code D} either way, Set to Submit {@code A}. Legacy's order is kept, and
   * {@code ReportTransitionWriterTest}'s {@code InOrder} cases are the only guard on it, because
   * the test schema has no triggers.
   *
   * <p>A status write matching no row is a REFUSAL, not a failure. The statement carries {@code
   * transition.from()} as its {@code expectedCode}, so zero rows means the track moved between this
   * request's unlocked read and its write — a lost update, whose loser has done nothing wrong and
   * gets the transition's refusal text for the track rather than a 500 (deviation (U)). Nothing has
   * been written at that point, and the surrounding transaction rolls back regardless.
   *
   * @param track the track to reverse
   * @param millId the mill
   * @param year the reporting year
   * @param transition the reversal to apply — {@link TrackTransition#SET_TO_DRAFT} or {@link
   *     TrackTransition#SET_TO_SUBMIT}
   * @param actingUser the value for the audit columns, at most 30 characters
   * @return the track's status code after the transition
   * @throws ReportTransitionRejectedException 409 — the track left {@code transition.from()}
   *     between the caller's read and this write
   * @throws ReportSubmissionException 500 — a write failed or left the report half-transitioned
   */
  @Transactional
  public String writeReversal(
      ScheduleTrack track, long millId, int year, TrackTransition transition, String actingUser) {
    Objects.requireNonNull(track, "track");
    // This method writes no identity pair, so it can only honour a transition that owes none.
    // SUBMIT owes LICENSEE_* and VERIFY owes AUDITOR_*; routed here they would commit without
    // them — a submit with no licensee, a verify with no auditor — and nothing downstream would
    // notice.
    if (transition.recorded() != TrackTransition.Recorded.NONE) {
      throw new IllegalArgumentException(
          transition + " records an identity pair and cannot be written as a reversal");
    }
    try {
      int updated =
          track == ScheduleTrack.SCHEDULE_11
              ? repository.updateSilvicultureTrackStatusWithoutIdentity(
                  millId, year, transition.to(), transition.from(), actingUser)
              : repository.updateTrackStatusWithoutIdentity(
                  millId, year, transition.to(), transition.from(), actingUser);
      if (updated != 1) {
        log.info(
            "{} {}->{} 409: the track left {} for millId={} year={} before the write",
            track,
            transition.from(),
            transition.to(),
            transition.from(),
            millId,
            year);
        throw new ReportTransitionRejectedException(transition, track);
      }

      int stamped =
          track == ScheduleTrack.SCHEDULE_11
              ? stampSchedule11AuditColumns(millId, year, actingUser)
              : stampAuditColumns(millId, year, actingUser);

      int categories = 0;
      for (String categoryId : track.categoryIds()) {
        categories +=
            repository.advanceCategoryState(
                millId, year, categoryId, transition.categoryState(), actingUser);
      }
      int expectedCategories = track.categoryIds().size();
      if (categories != expectedCategories) {
        log.error(
            "{} {}->{} failed for millId={} year={}: expected {} category rows to advance but {}"
                + " were updated",
            track,
            transition.from(),
            transition.to(),
            millId,
            year,
            expectedCategories,
            categories);
        throw new ReportSubmissionException();
      }

      log.info(
          "Reversed {} for millId={} year={} {}->{}: {} category rows -> {}, {} audit rows"
              + " stamped, no identity pair written",
          track,
          millId,
          year,
          transition.from(),
          transition.to(),
          categories,
          transition.categoryState(),
          stamped);

      return transition.to();
    } catch (DataAccessException persistence) {
      log.error(
          "{} {}->{} failed to persist for millId={} year={}",
          track,
          transition.from(),
          transition.to(),
          millId,
          year,
          persistence);
      throw new ReportSubmissionException();
    }
  }

  /**
   * Stamp the actor and timestamp on every table holding the track's data — the thirteen Schedule
   * 1&ndash;10 row families in legacy tab order, via twenty statements, reproducing legacy's
   * per-schedule sweep. Each statement is scoped by its schedule's own mill/year predicate; zero
   * rows is normal for a schedule with no data. Business data is untouched here.
   *
   * <p>Package-private because submit reaches it too: {@link ReportTrackTransitionService#submit}
   * calls it from inside <em>its</em> transaction, which is sound precisely because this method
   * carries no {@code @Transactional} of its own — it joins whichever transaction is already open.
   * One copy, because two copies of twenty repository calls drift.
   *
   * @return the total rows stamped, for the audit log line
   */
  int stampAuditColumns(long millId, int year, String user) {
    int rows = 0;
    rows += repository.touchReportSummaries(millId, year, user);
    rows += repository.touchReportSummaryCostDetails(millId, year, user);
    rows += repository.touchTransportationReports(millId, year, user);
    rows += repository.touchTransportationCostDetails(millId, year, user);
    rows += repository.touchCamps(millId, year, user);
    rows += repository.touchCampCostDetails(millId, year, user);
    rows += repository.touchRoadMaintenanceReports(millId, year, user);
    rows += repository.touchRoadMaintenanceCostDetails(millId, year, user);
    rows += repository.touchBridges(millId, year, user);
    rows += repository.touchBridgeCostDetails(millId, year, user);
    rows += repository.touchCulverts(millId, year, user);
    rows += repository.touchCulvertCostDetails(millId, year, user);
    rows += repository.touchTreeToTruckReports(millId, year, user);
    rows += repository.touchTreeToTruckDetailReports(millId, year, user);
    rows += repository.touchTreeToTruckRateDetails(millId, year, user);
    rows += repository.touchContractualWorkReports(millId, year, user);
    rows += repository.touchContractualWorkCostDetails(millId, year, user);
    rows += repository.touchRoadConstructionReports(millId, year, user);
    rows += repository.touchRoadConstructionDetails(millId, year, user);
    rows += repository.touchRoadConstructionCostDetails(millId, year, user);
    return rows;
  }

  /**
   * Stamp the actor and timestamp on Schedule 11's one row family &mdash; every location row and
   * every cost detail under it, as legacy {@code updateBasicSilvicultureReport():328-337} did. The
   * Schedule 11 counterpart of {@link #stampAuditColumns}, which names no Schedule 11 table; same
   * contract: no {@code @Transactional}, joins the caller's, zero rows is normal (a mill/year with
   * no locations is vacuously met).
   *
   * @return the total rows stamped, for the audit log line
   */
  int stampSchedule11AuditColumns(long millId, int year, String user) {
    return repository.touchBasicSilvicultureReports(millId, year, user)
        + repository.touchBasicSilvicultureCostDetails(millId, year, user);
  }
}
