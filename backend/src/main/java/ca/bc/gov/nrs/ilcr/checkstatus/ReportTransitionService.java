package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.exception.ReportNotSubmittedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionRejectedException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * <p>Deliberately legacy-shaped, per the 2026-09-16 ratification that legacy behaviour wins for
 * Epic 17 <strong>except where the use cases record a defect</strong>: the gate runs outside the
 * write transaction and takes no row lock, and the status row carries no optimistic guard, because
 * legacy had neither and neither is recorded as a defect.
 *
 * <p>Two things are deliberately NOT legacy. A refused transition answers an error rather than
 * legacy's success message, because {@code UC-CHK-007-S07} records that silent success as a known
 * defect and states the correct behaviour is an error. And authorization is enforced server-side on
 * {@code SET_REPORT_STATUS}, where legacy gated verify only by a render-time {@code disabled=}
 * attribute and a negative role test; project policy requires the backend to enforce it (AD-7).
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
  private final ReportTransitionWriter writer;

  /** Wires the track-status read, the validation gate and the transactional write. */
  public ReportTransitionService(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportTransitionWriter writer) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.writer = writer;
  }

  /**
   * Verify a submitted Schedules 1&ndash;10 track: {@code S}&rarr;{@code V}, behind the same
   * eleven-schedule gate that guards submission, recording the acting user as the report's auditor
   * and advancing the ten category states.
   *
   * <p>Order is legacy's: read the current codes, run the gate, check the transition is legal, then
   * write. No row lock, and the gate sits outside the write transaction &mdash; see {@link
   * ReportTransitionWriter}. A refused transition writes nothing and answers 409: legacy reported
   * success there, but {@code UC-CHK-007-S07} records that as a known defect, so it is not ported.
   *
   * @param millId the mill, already validated as an active context by the caller
   * @param year the reporting year
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, used to find the auditor cross-reference;
   *     null when the request carries no directory identity, which records no auditor
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException the mill/year has no status row
   * @throws ReportNotSubmittedException one or more schedules fail validation
   * @throws ReportTransitionRejectedException the track is not Submitted, or its stored status code
   *     is NULL
   * @throws ReportTransitionFailedException the writes could not be persisted
   */
  public String verifySchedules1To10(long millId, int year, String actingUser, String actorGuid) {

    TrackStatusCodes codes =
        millContextService
            .findTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);

    // A NULL code is NOT a missing row, and it is NOT a legality refusal either: legacy
    // dereferenced the status association here and would have thrown NPE straight to the JSF error
    // page (isMillReportStatusValid:451-455). A crash is not portable, and claiming success on a
    // state nobody can name would be worse, so this one stays a refused transition (409).
    String current = codes.schedules1To10Code();
    if (current == null) {
      log.info(
          "Verify 409: the 1-10 track carries no status code for millId={} year={}", millId, year);
      throw new ReportTransitionRejectedException();
    }

    // The gate runs BEFORE the write transaction and takes no row lock, as legacy did:
    // CheckStatusMB.submitReport:247-261 evaluated all eleven validators in the managed bean and
    // only then called the DAO, which opened its own transaction. Accepted consequence, ratified
    // 2026-09-16 as legacy parity: a concurrent schedule save can invalidate the verdict between
    // the gate and the write. Legacy had the same window, and a wider one — its verdict came from
    // a @ViewScoped in-memory snapshot rather than from the database.
    requireTrackPassesValidation(millId, year);

    if (!isTransitionLegal(current, VERIFIED)) {
      // A refused transition is an ERROR, not a success. Legacy reported success here — it called
      // the DAO as a bare statement (CheckStatusMB.submitReport:271), never captured the false
      // that isMillReportStatusValid returned for a no-op or either illegal Draft<->Verified jump,
      // and emitted sch1-10VerifiedMsg unconditionally at :283 — but the requirements baseline
      // records that as a DEFECT rather than behaviour to preserve. UC-CHK-007-S07 is titled
      // "Verification Silently Reports Success Despite Rejected Transition (Known Defect)", its
      // slice table says outright "defect — should be an error", and it calls the observed outcome
      // a confirmed source discrepancy against the high-level UC's stated EF4 behaviour. Epic 17's
      // parity rule is legacy-wins EXCEPT where the use cases record a defect; this is the one
      // behaviour in the epic that they do.
      log.info(
          "Verify 409: transition {}->{} is not legal for millId={} year={}",
          current,
          VERIFIED,
          millId,
          year);
      throw new ReportTransitionRejectedException();
    }

    return writer.write(
        millId, year, VERIFIED, categoryStateFor(current, VERIFIED), actingUser, actorGuid);
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
}
