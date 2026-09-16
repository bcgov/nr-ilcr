package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.exception.ReportNotSubmittedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionFailedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionRejectedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report-status transitions on the Schedules 1&ndash;10 track. The workflow domain owns these, not
 * any schedule service (AD-5/AD-9): a transition spans all ten categories and belongs to none of
 * them.
 *
 * <p>Legacy drove submit, verify and both reversals through one parameterized method ({@code
 * SubmitReportDAO.submitReport}), gating every one of them on the same validation sweep and
 * deriving the category state from the pair of old and new status codes. The two rule tables here —
 * {@link #isTransitionLegal} and {@link #categoryStateFor} — are that logic in full, so the
 * remaining transitions extend this component rather than fork it. Only Submitted&rarr;Verified has
 * a write path today.
 *
 * <p>The Schedule 11 track is never read or written here. It has its own status column and its own
 * workflow, and the two are independent (AD-9).
 */
@Service
@Slf4j
public class ReportTransitionService {

  static final String DRAFT = "D";
  static final String SUBMITTED = "S";
  static final String VERIFIED = "V";

  /** Category state codes, which are not the same alphabet as the track status codes. */
  static final String CATEGORY_DRAFT = "D";

  static final String CATEGORY_AUDITED = "A";
  static final String CATEGORY_VERIFIED = "V";

  /**
   * The ten Schedules 1&ndash;10 category rows a transition must advance. Eleven exist per
   * mill/year; category {@code '11'} is Schedule 11's and never moves with this track.
   */
  static final int EXPECTED_CATEGORY_ROWS = 10;

  /**
   * The verdicts the 1&ndash;10 gate must produce: eleven, because 7A and 7B are separate {@code
   * CheckedSchedule}s. Asserted rather than merely checked for non-emptiness — {@link
   * TrackCheckResult} rolls up with {@code allMatch}, so a dropped or mis-tracked adapter would
   * report a short list as fully validated and verify a schedule nothing checked.
   */
  static final int EXPECTED_TRACK_VERDICTS = 11;

  /**
   * Legacy's category-state pair table, keyed current-then-target. Transcribed from {@code
   * SubmitReportDAO.setCategoryStateCode}, whose own parameter order is the reverse of its switch
   * key — reading that signature left to right yields the wrong cell.
   */
  private static final Map<String, String> CATEGORY_STATE_BY_TRANSITION =
      Map.of(
          DRAFT + SUBMITTED, CATEGORY_AUDITED,
          SUBMITTED + VERIFIED, CATEGORY_VERIFIED,
          VERIFIED + SUBMITTED, CATEGORY_AUDITED,
          SUBMITTED + DRAFT, CATEGORY_DRAFT);

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;
  private final ReportTransitionRepository repository;

  /** Wires the track-status read, the validation gate and the transition SQL. */
  public ReportTransitionService(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportTransitionRepository repository) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.repository = repository;
  }

  /**
   * Verify a submitted Schedules 1&ndash;10 track: {@code S}&rarr;{@code V}, behind the same
   * eleven-schedule gate that guards submission, recording the acting user as the report's auditor
   * and advancing the ten category states.
   *
   * <p>Order matters: lock the status row, read the current codes, re-run the gate, check the
   * transition is legal, then write &mdash; status, audit stamps, category. The lock is taken
   * before any guard so a concurrent schedule save serializes against this transaction rather than
   * racing it. The write order is legacy's and is load-bearing; see the comment on the writes.
   * (Legacy itself validated <em>before</em> opening its transaction, in {@code
   * CheckStatusMB.submitReport}; re-running the gate inside the write transaction is this story's
   * recorded deviation, which is why the lock is held across the fan-out.)
   *
   * @param millId the mill, already validated as an active context by the caller
   * @param year the reporting year
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, used to find the auditor cross-reference;
   *     null when the request carries no directory identity, which records no auditor
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException the mill/year has no status row
   * @throws ReportNotSubmittedException one or more schedules fail validation
   * @throws ReportTransitionRejectedException the track is not Submitted
   * @throws StaleRevisionException the row changed between the lock and the write
   * @throws ReportTransitionFailedException the writes could not be persisted
   */
  @Transactional
  public String verifySchedules1To10(long millId, int year, String actingUser, String actorGuid) {

    // final because the guards below sit between this read and its use at the write: the value is
    // deliberately the one taken under the lock, not a re-read.
    final int revision =
        repository.lockAndReadRevision(millId, year).orElseThrow(ScheduleNotFoundException::new);

    TrackStatusCodes codes =
        millContextService
            .findTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);

    // A NULL code is NOT a missing row. The column is nullable and a null 1-10 code has been a
    // tolerated shape since Story 1.2, so collapsing it into the Optional would answer 404
    // "schedules have not been found" for a report that plainly exists. It is a refused
    // transition, which is what the legality table would have said had it been reachable.
    String current = codes.schedules1To10Code();
    if (current == null) {
      log.info(
          "Verify 409: the 1-10 track carries no status code for millId={} year={}", millId, year);
      throw new ReportTransitionRejectedException();
    }

    requireTrackPassesValidation(millId, year);

    if (!isTransitionLegal(current, VERIFIED)) {
      log.info(
          "Verify 409: transition {}->{} is not legal for millId={} year={}",
          current,
          VERIFIED,
          millId,
          year);
      throw new ReportTransitionRejectedException();
    }

    String categoryState = categoryStateFor(current, VERIFIED);

    try {
      Long auditorMillId =
          actorGuid == null ? null : repository.findUserXrefMillId(millId, actorGuid).orElse(null);
      String auditorGuid = auditorMillId == null ? null : actorGuid;

      int updated =
          repository.updateTrackStatusWithAuditor(
              millId, year, VERIFIED, auditorMillId, auditorGuid, revision, actingUser);
      if (updated == 0) {
        throw new StaleRevisionException();
      }

      // ORDER IS LOAD-BEARING — stamps first, category second, as legacy did
      // (SubmitReportDAO.submitReport:73-118 stamps each schedule's rows, then advances that
      // schedule's category). Delivery derives ILCR_*_AUD.RECORD_STATE_CODE in a BEFORE-UPDATE row
      // trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the ONLY pair that
      // writes an 'S' snapshot — the snapshot Epic 16's original-value indicators read. S->V is
      // indifferent (both (A,V) and (V,V) map to 'V'), but D->S is not, and 15.3/Epic 18 extend
      // this method. Advance the category first and submit stamps (A,S)->'A', no 'S' snapshot is
      // ever written, and every original-value indicator silently serves nothing. The test
      // snapshot has no triggers, so no test here can catch a re-inversion.
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
          "Verified millId={} year={}: status S->V, {} category rows -> {}, {} audit rows stamped,"
              + " auditor recorded={}",
          millId,
          year,
          categories,
          categoryState,
          stamped,
          auditorMillId != null);

      return VERIFIED;
    } catch (DataAccessException persistence) {
      log.error("Verify failed to persist for millId={} year={}", millId, year, persistence);
      throw new ReportTransitionFailedException(persistence);
    }
  }

  /**
   * Re-run the track's own validation gate against the database, inside this transaction. Legacy
   * evaluated its eleven validators against the screen's in-memory snapshot instead, which is why a
   * report could be verified on the strength of a stale view.
   *
   * <p>An empty verdict list is refused rather than treated as a pass: {@link TrackCheckResult}
   * rolls up with {@code allMatch}, so nothing to check would otherwise verify vacuously.
   */
  private void requireTrackPassesValidation(long millId, int year) {
    List<ScheduleCheckResult> verdicts =
        sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, millId, year);
    if (verdicts.size() != EXPECTED_TRACK_VERDICTS) {
      log.warn(
          "Verify 409: expected {} schedule verdicts for millId={} year={} but got {}",
          EXPECTED_TRACK_VERDICTS,
          millId,
          year,
          verdicts.size());
      throw new ReportNotSubmittedException();
    }
    if (!TrackCheckResult.of(null, verdicts).requirementsMet()) {
      log.info("Verify 409: validation gate failed for millId={} year={}", millId, year);
      throw new ReportNotSubmittedException();
    }
  }

  /**
   * Whether legacy's persistence layer would accept this transition. It refused a no-op and both
   * direct Draft&harr;Verified jumps, leaving four legal moves; the UI's own gating is advisory and
   * this runs regardless of it.
   *
   * @param current the track's stored status code
   * @param target the requested status code
   * @return true when the move is one of the four legacy permitted
   */
  static boolean isTransitionLegal(String current, String target) {
    if (current == null || target == null || current.equals(target)) {
      return false;
    }
    return CATEGORY_STATE_BY_TRANSITION.containsKey(current + target);
  }

  /**
   * The category state a transition drives the ten Schedules 1&ndash;10 category rows to.
   *
   * @param current the track's stored status code
   * @param target the requested status code
   * @return the category state code
   * @throws ReportTransitionRejectedException the pair has no cell, which means the legality check
   *     was bypassed
   */
  static String categoryStateFor(String current, String target) {
    String state = CATEGORY_STATE_BY_TRANSITION.get(current + target);
    if (state == null) {
      throw new ReportTransitionRejectedException();
    }
    return state;
  }

  /**
   * Stamp the actor and timestamp on every table holding the track's data — thirteen of them,
   * reproducing legacy's per-schedule sweep. Business data is untouched here.
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
