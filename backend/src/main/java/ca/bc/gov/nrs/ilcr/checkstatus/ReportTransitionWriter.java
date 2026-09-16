package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.ReportTransitionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write half of a Schedules 1&ndash;10 status transition: status row, audit sweep, category
 * advance, in one transaction.
 *
 * <p>A separate bean rather than a method on {@link ReportTransitionService} because the boundary
 * has to sit <em>here</em> and nowhere wider. Legacy ran its eleven validators in the managed bean
 * <strong>before</strong> {@code SubmitReportDAO.submitReport} opened a transaction ({@code
 * CheckStatusMB.submitReport:247-261} gates, {@code :271} calls), and it took no row lock at all.
 * Keeping the gate inside the write transaction — as an earlier cut did — held a connection across
 * the eleven-schedule fan-out, which {@code CheckStatusSweepService}'s own javadoc warns against in
 * bold. Spring's proxying means self-invocation would not start a transaction, so the split is what
 * makes "gate outside, write inside" real rather than annotational.
 *
 * <p>Consequence, accepted deliberately as legacy parity (ratified 2026-09-16): the gate's verdict
 * is read before the write begins, so a concurrent schedule save can invalidate it in between.
 * Legacy had that same window and a wider one, since its verdict came from a {@code @ViewScoped}
 * in-memory snapshot rather than from the database.
 */
@Service
@Slf4j
public class ReportTransitionWriter {

  /**
   * The category rows a 1&ndash;10 transition must advance: ten of the eleven a mill/year carries,
   * because category {@code '11'} is Schedule 11's and never moves with this track.
   */
  static final int EXPECTED_CATEGORY_ROWS = 10;

  private final ReportTransitionRepository repository;

  public ReportTransitionWriter(ReportTransitionRepository repository) {
    this.repository = repository;
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
   * @throws ReportTransitionFailedException the writes could not be persisted, or left the report
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
      Long auditorMillId =
          actorGuid == null ? null : repository.findUserXrefMillId(millId, actorGuid).orElse(null);
      String auditorGuid = auditorMillId == null ? null : actorGuid;

      int updated =
          repository.updateTrackStatusWithAuditor(
              millId, year, targetStatus, auditorMillId, auditorGuid, actingUser);
      if (updated != 1) {
        throw new ReportTransitionFailedException(
            "Expected to move exactly one status row for millId="
                + millId
                + " year="
                + year
                + " but "
                + updated
                + " were updated");
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

      int categories = repository.advanceCategoryStates(millId, year, categoryState, actingUser);
      // Legacy dereferenced findReportCategory.uniqueResult() per schedule, so a missing category
      // row failed the whole transition at once. Set-based UPDATEs skip it silently instead, and a
      // mill/year enrolled by an interrupted year-open really does carry fewer than eleven rows
      // (ReportingYearService's EnrolmentState.PARTIAL, error.mill.activate.partialrecords). Fail
      // loudly rather than commit a half-transitioned report behind a 200.
      if (categories != EXPECTED_CATEGORY_ROWS) {
        throw new ReportTransitionFailedException(
            "Expected "
                + EXPECTED_CATEGORY_ROWS
                + " category rows to advance for millId="
                + millId
                + " year="
                + year
                + " but "
                + categories
                + " were updated");
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
      throw new ReportTransitionFailedException(persistence);
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
    rows += repository.stampReportSummaries(millId, year, user);
    rows += repository.stampSummaryCostDetails(millId, year, user);
    rows += repository.stampTransportationReports(millId, year, user);
    rows += repository.stampTransportationCostDetails(millId, year, user);
    rows += repository.stampCampReports(millId, year, user);
    rows += repository.stampCampCostDetails(millId, year, user);
    rows += repository.stampRoadMaintenanceReports(millId, year, user);
    rows += repository.stampRoadMaintenanceCostDetails(millId, year, user);
    rows += repository.stampBridgeReports(millId, year, user);
    rows += repository.stampBridgeCostDetails(millId, year, user);
    rows += repository.stampCulvertReports(millId, year, user);
    rows += repository.stampCulvertCostDetails(millId, year, user);
    rows += repository.stampTreeToTruckReports(millId, year, user);
    rows += repository.stampTreeToTruckDetails(millId, year, user);
    rows += repository.stampTreeToTruckRateDetails(millId, year, user);
    rows += repository.stampContractualWorkReports(millId, year, user);
    rows += repository.stampContractualWorkCostDetails(millId, year, user);
    rows += repository.stampRoadConstructionReports(millId, year, user);
    rows += repository.stampRoadConstructionDetails(millId, year, user);
    rows += repository.stampRoadConstructionCostDetails(millId, year, user);
    return rows;
  }
}
