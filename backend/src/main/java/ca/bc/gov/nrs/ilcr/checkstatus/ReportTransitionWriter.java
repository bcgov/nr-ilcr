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
 * <p>Used by VERIFY only. Submit reaches the same {@link ReportTrackTransitionRepository} from
 * inside its own transaction, because 15.3 ruled the opposite boundary for that transition &mdash;
 * lock the status row first, then gate inside the write. Both rulings stand; this class is where
 * the legacy-faithful one is expressed, and the repository beneath it is now shared.
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
   * @param targetStatus the status code to move the track to
   * @param categoryState the category state the pair table yields for this move
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, or null to record no auditor
   * @return {@code targetStatus}, once persisted
   * @throws ReportSubmissionException the writes could not be persisted, or left the report
   *     half-transitioned
   */
  @Transactional
  public String write(
      long millId,
      int year,
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
              millId, year, targetStatus, auditorMillId, auditorGuid, actingUser);
      if (updated != 1) {
        log.error(
            "Verify failed for millId={} year={}: status row update affected {} rows",
            millId,
            year,
            updated);
        throw new ReportSubmissionException();
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
            "Verify failed for millId={} year={}: expected {} category rows to advance for millId={}"
                + " year={} but {} were updated",
            EXPECTED_CATEGORY_ROWS,
            millId,
            year,
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
   * Stamp the actor and timestamp on every table holding the track's data — thirteen of them via
   * twenty statements, reproducing legacy's per-schedule sweep. Business data is untouched here.
   *
   * @return the total rows stamped, for the audit log line
   */
  private int stampAuditColumns(long millId, int year, String user) {
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
