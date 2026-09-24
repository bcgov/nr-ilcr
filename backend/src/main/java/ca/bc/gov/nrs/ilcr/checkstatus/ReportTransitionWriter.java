package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write half of a Schedules 1&ndash;10 status transition: status row, audit sweep, category
 * advance, in one transaction.
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
 * <p>{@link #write} is used by VERIFY only, and {@link #writeReversal} by the two Story 18.1
 * reversals — same three steps in the same order, differing only in the status statement, because
 * Set to Draft and Set to Submit write no identity pair at all (D1). Submit reaches the same {@link
 * ReportTrackTransitionRepository} from inside its own transaction, because 15.3 ruled the opposite
 * boundary for that transition &mdash; lock the status row first, then gate inside the write. Both
 * rulings stand; this class is where the legacy-faithful one is expressed, and the repository
 * beneath it is now shared. What the two transitions <em>do</em> share is {@link
 * #stampAuditColumns}: the twenty-statement audit sweep is identical for both, so it lives here
 * once and submit calls it from inside its own transaction.
 */
@Service
@Slf4j
public class ReportTransitionWriter {

  /**
   * The category rows a 1&ndash;10 transition must advance: ten of the eleven a mill/year carries,
   * because category {@code '11'} is Schedule 11's and never moves with this track.
   */
  static final int EXPECTED_CATEGORY_ROWS = 10;

  private final ReportTrackTransitionRepository repository;
  private final MillUserXrefRepository millUserXrefRepository;

  public ReportTransitionWriter(
      ReportTrackTransitionRepository repository, MillUserXrefRepository millUserXrefRepository) {
    this.repository = repository;
    this.millUserXrefRepository = millUserXrefRepository;
  }

  /**
   * Apply the transition: status row first, then the audit sweep, then the category advance.
   *
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
      long millId,
      int year,
      String expectedStatus,
      String targetStatus,
      String categoryState,
      String actingUser,
      String actorGuid) {
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
          repository.updateTrackStatusWithAuditor(
              millId, year, targetStatus, expectedStatus, auditorMillId, auditorGuid, actingUser);
      if (updated != 1) {
        // Zero rows is a REFUSAL, not a failure: the track left expectedStatus between the
        // service's unlocked read and this write. Legacy's generic text, because VERIFY's other
        // refusals carry it too (Epic 17's parity tie-breaker) — null selects it.
        log.info(
            "Verify 409: the 1-10 track left {} for millId={} year={} before the write",
            expectedStatus,
            millId,
            year);
        throw new ReportTransitionRejectedException(null);
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
      // has no triggers, so no acceptance test here can catch a re-inversion.
      int stamped = stampAuditColumns(millId, year, actingUser);

      int categories = 0;
      for (String categoryId : ScheduleTrack.SCHEDULES_1_TO_10.categoryIds()) {
        categories +=
            repository.advanceCategoryState(millId, year, categoryId, categoryState, actingUser);
      }
      // Legacy dereferenced findReportCategory.uniqueResult() per schedule, so a missing category
      // row failed the whole transition at once. Set-based UPDATEs skip it silently instead, and a
      // mill/year enrolled by an interrupted year-open really does carry fewer than eleven rows
      // (ReportingYearService's EnrolmentState.PARTIAL, error.mill.activate.partialrecords). Fail
      // loudly rather than commit a half-transitioned report behind a 200.
      if (categories != EXPECTED_CATEGORY_ROWS) {
        log.error(
            "Verify failed for millId={} year={}: expected {} category rows to advance but {} were"
                + " updated",
            millId,
            year,
            EXPECTED_CATEGORY_ROWS,
            categories);
        throw new ReportSubmissionException();
      }

      log.info(
          "Transitioned millId={} year={} to {}: {} category rows -> {}, {} audit rows stamped,"
              + " auditor recorded={}",
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
   * Apply one of the two admin reversals — Submitted&rarr;Draft or Verified&rarr;Submitted (Story
   * 18.1) — in the same three steps and the same order as {@link #write}, differing only in the
   * status statement.
   *
   * <p>It takes no actor GUID, and that absence is the whole point. Legacy keyed the identity write
   * on the TARGET status alone ({@code SubmitReportDAO.updateILCRMillReportStatus:401-412}): a
   * {@code 'D'} target skips the association block entirely, and a {@code 'S'} target wrote the
   * LICENSEE pair from the acting user's cross-reference — which for an ADMIN reversing a
   * verification means overwriting the record of who actually submitted, with NULLs whenever that
   * admin has no assignment for the mill. Story 18.1 D1(a) ratified writing neither pair for either
   * reversal (deviation (S)), so there is no cross-reference to resolve and {@link
   * ReportTrackTransitionRepository#updateTrackStatusWithoutIdentity} names no identity column.
   *
   * <p>A status write matching no row is a REFUSAL, not a failure. The statement carries {@code
   * transition.from()} as its {@code expectedCode} (D2), so zero rows means the track moved between
   * this request's unlocked read and its write — a lost update, whose loser has done nothing wrong
   * and gets the transition's own "no longer in …" 409 rather than a 500 telling them to contact
   * support (AC 9, deviation (U)). Nothing has been written at that point, and the surrounding
   * transaction rolls back regardless.
   *
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
      long millId, int year, TrackTransition transition, String actingUser) {
    // This method writes no identity pair, so it can only honour a transition that owes none.
    // SUBMIT owes LICENSEE_* and VERIFY owes AUDITOR_*; routed here they would commit without
    // them — a submit with no licensee, a verify with no auditor — and nothing downstream would
    // notice. One caller today, but Story 26.1 reuses this enum.
    if (transition.recorded() != TrackTransition.Recorded.NONE) {
      throw new IllegalArgumentException(
          transition + " records an identity pair and cannot be written as a reversal");
    }
    try {
      int updated =
          repository.updateTrackStatusWithoutIdentity(
              millId, year, transition.to(), transition.from(), actingUser);
      if (updated != 1) {
        log.info(
            "{} 409: the 1-10 track left {} for millId={} year={} before the write",
            transition,
            transition.from(),
            millId,
            year);
        throw new ReportTransitionRejectedException(transition);
      }

      // ORDER IS LOAD-BEARING — see the note in write() above. Set to Submit's (V,S) pair maps to
      // the 'A' snapshot only while the category row is still V, so advancing the category first
      // would change what the delivery BEFORE-UPDATE trigger records. The test schema has no
      // triggers, so ReportTransitionWriterTest's InOrder case is the only guard on this.
      int stamped = stampAuditColumns(millId, year, actingUser);

      int categories = 0;
      for (String categoryId : ScheduleTrack.SCHEDULES_1_TO_10.categoryIds()) {
        categories +=
            repository.advanceCategoryState(
                millId, year, categoryId, transition.categoryState(), actingUser);
      }
      if (categories != EXPECTED_CATEGORY_ROWS) {
        log.error(
            "{} failed for millId={} year={}: expected {} category rows to advance but {} were"
                + " updated",
            transition,
            millId,
            year,
            EXPECTED_CATEGORY_ROWS,
            categories);
        throw new ReportSubmissionException();
      }

      log.info(
          "Reversed millId={} year={} {}->{}: {} category rows -> {}, {} audit rows stamped, no"
              + " identity pair written",
          millId,
          year,
          transition.from(),
          transition.to(),
          categories,
          transition.categoryState(),
          stamped);

      return transition.to();
    } catch (DataAccessException persistence) {
      log.error("Reversal failed to persist for millId={} year={}", millId, year, persistence);
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
}
