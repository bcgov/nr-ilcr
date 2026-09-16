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
 * <p>Deliberately legacy-shaped throughout, per the 2026-09-16 ratification that legacy behaviour
 * wins for Epic 17: the gate runs outside the write transaction and takes no row lock, and the
 * status row carries no optimistic guard on its own code.
 *
 * <p><strong>PARITY LEDGER.</strong> Every point on this surface where the code does NOT do what
 * 2.0.4 did is listed here, with why — nothing implicit, so a future reader can tell a deliberate
 * choice from an oversight.
 *
 * <ul>
 *   <li><strong>A refused transition answers 409 {@code reportSubmissionErrorMsg}</strong> — which
 *       is legacy, not a fix, once the service layer is read. {@code
 *       ILCRService.submitReport:718-723} is {@code void} and converts the DAO's {@code false} into
 *       {@code ILCSException(SCHEDULE_NOT_SUBMITTED)}, mapped to that key at {@code
 *       ILCSException:50}; the bean's {@code catch} at {@code CheckStatusMB.submitReport:289-292}
 *       shows it as an error, skipping the verified message at {@code :284}. {@code UC-CHK-007-S07}
 *       calls this a silent-success defect, but that record stops at the bean and the DAO without
 *       opening the service, and hedges itself as unconfirmed. Not a divergence.
 *   <li><strong>Authorization is enforced server-side.</strong> DIVERGES. Legacy gated verify only
 *       by a render-time {@code disabled=} attribute plus {@code
 *       UserSessionMB.canUserVerifyReport:523-541}, a <em>negative</em> role test ({@code !=
 *       ILCR_LICENSEE}) that admitted any unrecognised role. {@code project-context.md} makes
 *       backend enforcement non-negotiable (AD-7, PRD DL-6), which sits above the epic's rule.
 *   <li><strong>A closed mill is refused (ERR-002).</strong> DIVERGES, by its own ratified decision
 *       (the story's decision D1): legacy had no confirmed message for this on this page.
 *   <li><strong>A missing status row answers 404; a stored NULL status code answers 409.</strong>
 *       DIVERGES. Legacy threw {@code NullPointerException} in both cases — {@code
 *       SubmitReportDAO.isMillReportStatusValid:450} dereferences the row and {@code :455} its
 *       status association, neither null-checked — reaching the JSF error page. A crash is not
 *       portable, so each answers a truthful error over an unchanged database. The NULL branch is
 *       additionally unreachable in delivery: both status columns are {@code NOT NULL} there
 *       (2026-09-16 probe), so it is defensive only.
 *   <li><strong>The gate reads the database.</strong> DIVERGES in source only, not in position:
 *       legacy's eleven validators ran against a {@code @ViewScoped} in-memory snapshot, which has
 *       no analogue in this architecture. Its <em>position</em> is legacy's — outside the write
 *       transaction, no row lock (see {@link ReportTransitionWriter}).
 *   <li><strong>One timestamp per statement, not per row.</strong> Legacy called {@code
 *       AbstractHibernateDAO.getUpdateTimestamp:30-32} ({@code new Date()}) once per entity, so it
 *       wrote a fresh millisecond-precision value per row. These are twenty set-based UPDATEs, each
 *       evaluating {@code SYSDATE} once, so rows in one transition can differ by a second across a
 *       statement boundary. Nothing reads these columns, so the difference is inert.
 *   <li><strong>Two guards with no legacy analogue, neither reachable where legacy behaved
 *       differently:</strong> the status write must affect exactly one row (legacy loaded the
 *       entity and saved it, so a vanished row could not arise), and the sweep must yield eleven
 *       verdicts (legacy hard-coded eleven boolean calls, so a short list could not occur). Both
 *       fail closed with verbatim legacy error text rather than inventing an outcome.
 *   <li><strong>The gate&rarr;write race is legacy's, and is kept.</strong> Neither legacy nor this
 *       code locks the status row, so a concurrent schedule save can invalidate the gate's verdict
 *       before the write commits, and two concurrent verifies both succeed with the second
 *       overwriting the auditor columns. Legacy's window was wider still, its verdict coming from a
 *       {@code @ViewScoped} snapshot. Kept under the Epic 17 parity rule and recorded rather than
 *       closed: closing it (an {@code expectedCurrent} predicate on the UPDATE, or a lock plus a
 *       re-gate) would itself be a divergence.
 * </ul>
 *
 * <p>Reproduced faithfully, and therefore NOT listed above: the four-cell legality outcome, the
 * category-state pair table keyed current-then-target, the thirteen-table sweep with its category
 * scoping and its stamps-before-category order, the auditor composite FK with no active-date
 * predicate and NULL when absent, {@code REVISION_COUNT} untouched, no row lock, no {@code _AUD}
 * insert, and all message text verbatim.
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
   * ReportTransitionWriter}. A refused transition writes nothing and answers 409 with legacy's own
   * {@code reportSubmissionErrorMsg} — what legacy did, once its service layer is read.
   *
   * @param millId the mill, already validated as an active context by the caller
   * @param year the reporting year
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, used to find the auditor cross-reference;
   *     null when the request carries no directory identity, which records no auditor
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException the mill/year has no status row
   * @throws ReportNotSubmittedException one or more schedules fail validation
   * @throws ReportTransitionRejectedException the stored status code is NULL
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
    // CheckStatusMB.submitReport (method at :245) evaluated all eleven validators in the bean at
    // :257-261 and only then called the service at :274, which delegated to a DAO that opened its
    // own transaction. Accepted consequence, ratified
    // 2026-09-16 as legacy parity: a concurrent schedule save can invalidate the verdict between
    // the gate and the write. Legacy had the same window, and a wider one — its verdict came from
    // a @ViewScoped in-memory snapshot rather than from the database.
    requireTrackPassesValidation(millId, year);

    if (!isTransitionLegal(current, VERIFIED)) {
      // 409, and this IS legacy parity — the earlier reading that legacy reported success here was
      // wrong, and wrong because it stopped one layer short. The DAO returns false
      // (SubmitReportDAO.isMillReportStatusValid:448 refuses a no-op and both direct D<->V jumps;
      // submitReport:69-71 returns false without writing), and the SERVICE captures it:
      // ILCRService.submitReport:718-723 is `public void ... throws ILCSException` and does
      // `if (!getSubmitReportDAO().submitReport(...)) throw new
      // ILCSException(ExceptionCode.SCHEDULE_NOT_SUBMITTED)`. ILCSException:50 maps that code to
      // reportSubmissionErrorMsg. In the bean, that service call is the FIRST statement inside the
      // try (CheckStatusMB.submitReport:274, method at :245), addInfoMessage("sch1-10VerifiedMsg")
      // is at :284 AFTER it in the same try, and catch (ILCSException e) ->
      // addErrorMessage(e.getErrorCode()) is at :289-292. A refused transition therefore threw,
      // skipped the verified message entirely, and displayed the support-escalation error.
      //
      // UC-CHK-007-S07 records a "silent success" defect here. That record reads the bean and the
      // DAO but never opens ILCRService, and its own technical sidecar hedges the finding as an
      // unconfirmed assumption. There is no defect on this path. (There IS an adjacent one: when
      // saveSession() returned false, legacy emitted the error AND the success message, :278-285.)
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
