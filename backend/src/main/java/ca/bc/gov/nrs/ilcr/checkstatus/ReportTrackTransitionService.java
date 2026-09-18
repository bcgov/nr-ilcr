package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every report-status transition on the Schedules 1&ndash;10 track. The workflow domain owns these,
 * not any schedule service (AD-5/AD-9): a transition spans all ten categories and belongs to none
 * of them. {@link TrackTransition} is the single rule table &mdash; legacy drove submit, verify and
 * both reversals through one parameterized method ({@code SubmitReportDAO.submitReport}) keyed on
 * the (from, to) pair, and that enum is that logic in full, so Story 18.1's reversals extend this
 * class rather than fork it. Story 17.1 did fork it; this class is the two halves put back.
 *
 * <p><strong>The two transitions do NOT share a boundary, and that is deliberate on both
 * sides.</strong> Submit locks the status row first and gates inside the write transaction (15.3's
 * D10, which also buys it a specific "no longer in Draft" refusal message &mdash; a departure from
 * legacy the business ruled on 2026-09-17). Verify takes no lock and gates outside the write,
 * because that is where legacy put it, and answers a refusal with legacy's own verbatim {@code
 * reportSubmissionErrorMsg}; Epic 17's tie-breaker is legacy behaviour, and the resulting
 * gate&rarr;write race is legacy's too and is recorded open rather than closed. What they share is
 * everything below the boundary: one repository, one rule table, one exception family, one
 * identity-resolution rule. Do not "harmonise" the two boundaries without re-opening both rulings.
 *
 * <h2>Submit</h2>
 *
 * <p>Submits the Schedules 1&ndash;10 track (UC-CHK-002, FR5) &mdash; the modern shape of legacy
 * {@code CheckStatusMB.submitReport("S")} plus {@code SubmitReportDAO.submitReport()}, in ONE write
 * transaction that holds the status row from its first statement to its commit.
 *
 * <p><strong>The order is the feature.</strong> Inside the transaction: (1) lock the status row;
 * (2) refuse unless the caller may submit at the row's current status; (3) re-run all eleven
 * Schedule 1&ndash;10 validations and refuse unless every one is met; (4) write status {@code S};
 * (5) touch every Schedule 1&ndash;10 row's audit columns; (6) advance the ten category rows to
 * {@code A}. Steps 4&ndash;6 must run in that order because the delivery audit triggers stamp the
 * {@code S} snapshot Story 16.2 reads only while the row's category is still {@code D} and the
 * track is already {@code S} &mdash; legacy got it by accident of its loop; this class gets it on
 * purpose. Legacy interleaved touch-then-advance per category ({@code SubmitReportDAO:74-120}); the
 * sequence here is trigger-equivalent because each row's trigger reads only its own category's
 * state.
 *
 * <p>The lock is taken BEFORE the eleven checks (D10), so no write that also locks the status row
 * &mdash; every schedule's editability gate, since Story 15.3 &mdash; can land between the gate and
 * the transition; Story 15.1's don't-wrap-a-fan-out warning does not apply because all eleven
 * checks join this transaction on its one connection (none uses {@code REQUIRES_NEW}, a thread or a
 * raw connection). The cost is lock duration for the length of the fan-out, and only writers to
 * this mill/year wait behind it.
 *
 * <p>Two legacy defects are not reproduced: the guard-{@code false} path that left the Hibernate
 * transaction open ({@code SubmitReportDAO:69-72}) &mdash; every refusal here is a rollback or no
 * write at all &mdash; and the impossible double message when {@code saveSession()} failed after
 * the commit ({@code CheckStatusMB:278-283}) &mdash; the success message is issued only after
 * commit. Neither is a "silent success": legacy's service threw on the guard's {@code false} and
 * the bean showed the error, exactly as this class does.
 *
 * <h2>Verify</h2>
 *
 * <p>Signs a submitted report off for rate setting (UC-CHK-007/012) &mdash; {@code S}&rarr;{@code
 * V} behind the same eleven-schedule gate, recording the acting user as the report's AUDITOR rather
 * than its licensee ({@link TrackTransition.Recorded}). Order is legacy's: read the current codes,
 * run the gate, check the transition is legal, then write. See {@link ReportTransitionWriter} for
 * why the write is a separate bean.
 *
 * <p>Logs mill/year and the failure class only &mdash; never a cost or volume value, never a
 * directory identifier (AD-11).
 */
@Service
@Slf4j
public class ReportTrackTransitionService {

  /**
   * The verdicts the 1&ndash;10 gate must produce: eleven, because 7A and 7B are separate {@code
   * CheckedSchedule}s.
   */
  static final int EXPECTED_TRACK_VERDICTS = 11;

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;
  private final ReportSubmission reportSubmission;
  private final MillUserXrefRepository millUserXrefRepository;
  private final ReportTrackTransitionRepository repository;
  private final ReportTransitionWriter writer;

  /** Wires the locked status read, the ten-schedule gate, the offer rule and the one writer. */
  public ReportTrackTransitionService(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportSubmission reportSubmission,
      MillUserXrefRepository millUserXrefRepository,
      ReportTrackTransitionRepository repository,
      ReportTransitionWriter writer) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.reportSubmission = reportSubmission;
    this.millUserXrefRepository = millUserXrefRepository;
    this.repository = repository;
    this.writer = writer;
  }

  /**
   * Draft &rarr; Submitted for the Schedules 1&ndash;10 track of a mill/year whose context the
   * caller has already validated ({@code MillContextService.validateMillYearActive}, AD-4 &mdash;
   * not repeated here; the locked read is the in-transaction existence check). Schedule 11's status
   * column and category row {@code '11'} are never touched (BR-08).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param authentication the caller &mdash; decides whether Submit is offered at this status and
   *     which {@code ILCR_MILL_USER_XREF} row becomes the report's licensee
   * @param user the caller's audit name ({@code Authentication.getName()})
   * @return the legacy bundle key of the success message
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportTransitionRejectedException 409 {@code submitNotDraftErrorMsg} &mdash; the track
   *     is not at Draft
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the eleven checks fails
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  @Transactional
  public String submit(long millId, int year, Authentication authentication, String user) {
    ScheduleTrack track = ScheduleTrack.SCHEDULES_1_TO_10;

    TrackStatusCodes codes =
        millContextService
            .lockTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current = codes.schedules1To10Code();
    TrackTransition transition =
        TrackTransition.resolve(current, TrackTransition.SUBMIT.to())
            .filter(TrackTransition.SUBMIT::equals)
            .orElseThrow(() -> new ReportTransitionRejectedException(TrackTransition.SUBMIT));
    if (!reportSubmission.canSubmit(authentication, current)) {
      log.debug("Submit refused for millId={} year={}: track not at Draft", millId, year);
      throw new ReportTransitionRejectedException(TrackTransition.SUBMIT);
    }

    try {
      TrackCheckResult gate =
          TrackCheckResult.of(current, sweepService.checkTrack(track, millId, year));
      if (!gate.requirementsMet()) {
        log.debug("Submit refused for millId={} year={}: validation gate not met", millId, year);
        throw new ReportNotSubmittedException();
      }

      Licensee licensee = resolveLicensee(authentication, millId);
      requireOneRow(
          repository.updateTrackStatus(
              millId, year, current, transition.to(), licensee.millId(), licensee.userGuid(), user),
          millId,
          year,
          "status row");
      writer.stampAuditColumns(millId, year, user);
      for (String categoryId : track.categoryIds()) {
        requireOneRow(
            repository.advanceCategoryState(
                millId, year, categoryId, transition.categoryState(), user),
            millId,
            year,
            "category row '" + categoryId + "'");
      }
    } catch (DataAccessException ex) {
      log.error(
          "Submit failed for millId={} year={}: {}",
          millId,
          year,
          NestedExceptionUtils.getMostSpecificCause(ex).toString());
      throw new ReportSubmissionException();
    }
    log.info("Schedules 1-10 submitted for millId={} year={}", millId, year);
    return transition.successKey();
  }

  /**
   * The report's licensee, as legacy recorded it ({@code SubmitReportDAO:405-410}): the caller's
   * {@code ILCR_MILL_USER_XREF} row for this mill, or NULLs when there is none. The directory GUID
   * comes from the {@code custom:idp_user_id} claim of a real principal, or from the typed dev/e2e
   * {@link MockUserPrincipal} &mdash; deliberately not from {@code getName()}, which is the short
   * audit name.
   */
  private Licensee resolveLicensee(Authentication authentication, long millId) {
    String userGuid = directoryGuid(authentication);
    if (userGuid == null || userGuid.isBlank()) {
      return Licensee.NONE;
    }
    return millUserXrefRepository
        .findAssignment(millId, userGuid)
        .map(xref -> new Licensee(xref.millId(), xref.userGuid()))
        .orElse(Licensee.NONE);
  }

  private static String directoryGuid(Authentication authentication) {
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (principal instanceof Jwt jwt) {
      return JwtPrincipalUtil.getIdpUserId(jwt);
    }
    if (principal instanceof MockUserPrincipal mock) {
      return mock.userGuid();
    }
    return null;
  }

  private static void requireOneRow(int rowsAffected, long millId, int year, String what) {
    if (rowsAffected != 1) {
      log.error(
          "Submit failed for millId={} year={}: {} update affected {} rows",
          millId,
          year,
          what,
          rowsAffected);
      throw new ReportSubmissionException();
    }
  }

  /**
   * Verify a submitted Schedules 1&ndash;10 track: {@code S}&rarr;{@code V}, behind the same
   * eleven-schedule gate that guards submission, recording the acting user as the report's auditor
   * and advancing the ten category states. Schedule 11 is never read or written (BR-08).
   *
   * <p>Not {@code @Transactional}: the gate runs HERE, outside the write, exactly where legacy ran
   * it ({@code CheckStatusMB.submitReport:247-261} gates, {@code :271} calls the DAO). {@link
   * ReportTransitionWriter} owns the transaction. No row lock, and no {@code expectedCurrent}
   * predicate on the status UPDATE &mdash; both are legacy's, and closing the race they leave open
   * would itself be the divergence.
   *
   * @param millId the mill, already validated as an active context by the caller (AD-4)
   * @param year the reporting year
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, or null to record no auditor
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportTransitionRejectedException 409 with legacy's verbatim {@code
   *     reportSubmissionErrorMsg} &mdash; a no-op, either illegal Draft&harr;Verified jump, or a
   *     stored NULL code
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the eleven checks fails
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  public String verify(long millId, int year, String actingUser, String actorGuid) {
    TrackStatusCodes codes =
        millContextService
            .findTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current = codes.schedules1To10Code();

    // A NULL code is NOT a missing row, and NOT a legality refusal either: legacy dereferenced the
    // status association here and would have thrown NPE straight to the JSF error page
    // (isMillReportStatusValid:451-455). A crash is not portable, and claiming success on a state
    // nobody can name would be worse, so this stays a refused transition (409). Unreachable in
    // delivery, whose status columns are NOT NULL (2026-09-16 probe) — defensive only.
    if (current == null) {
      log.info(
          "Verify 409: the 1-10 track carries no status code for millId={} year={}", millId, year);
      throw new ReportTransitionRejectedException(null);
    }

    // The gate runs BEFORE the write transaction and takes no row lock, as legacy did:
    // CheckStatusMB.submitReport evaluated all eleven validators in the bean and only then called
    // the service, which delegated to a DAO that opened its own transaction. Ratified 2026-09-16 as
    // legacy parity; a concurrent schedule save can invalidate the verdict between gate and write.
    requireTrackPassesValidation(millId, year);

    TrackTransition transition =
        TrackTransition.resolve(current, TrackTransition.VERIFY.to())
            .filter(TrackTransition.VERIFY::equals)
            .orElseThrow(() -> refusedVerify(current, millId, year));

    return writer.write(
        millId, year, transition.to(), transition.categoryState(), actingUser, actorGuid);
  }

  /**
   * The 409 for a verify the track cannot make &mdash; a no-op, either direct Draft&harr;Verified
   * jump, or a pair legacy's category map never named.
   *
   * <p><strong>It carries legacy's generic {@code reportSubmissionErrorMsg}, and that is legacy,
   * not a fallback.</strong> The legacy DAO's guard returned {@code false} without writing; the
   * service above it was declared {@code void} and converted that into {@code
   * ILCSException(SCHEDULE_NOT_SUBMITTED)}, which the exception map keyed to that message, and the
   * managed bean's catch block rendered it <em>instead of</em> the verified message it would
   * otherwise have added. So a refused verify showed an error over an unchanged database. {@code
   * UC-CHK-007-S07} records a "silent success" defect here; that record stops at the bean and the
   * DAO without reading the service, and hedges itself as unconfirmed.
   *
   * <p>Passing {@code null} rather than {@link TrackTransition#VERIFY} is therefore deliberate:
   * {@link TrackTransition#rejectedKey()} carries Story 15.4's more specific wording, which is a
   * business-ruled DEPARTURE from legacy and belongs to submit. Epic 17's tie-breaker is legacy.
   *
   * @param current the track's stored status code, for the log line only
   * @param millId the mill
   * @param year the reporting year
   * @return the exception to throw
   */
  private static ReportTransitionRejectedException refusedVerify(
      String current, long millId, int year) {
    log.info(
        "Verify 409: transition {}->{} is not legal for millId={} year={}",
        current,
        TrackTransition.VERIFY.to(),
        millId,
        year);
    return new ReportTransitionRejectedException(null);
  }

  /**
   * Re-run the track's own validation gate against the database. Legacy evaluated its eleven
   * validators against the screen's in-memory {@code @ViewScoped} snapshot instead, which is why a
   * report could be verified on the strength of a stale view; the position of the gate is legacy's,
   * its source is not.
   *
   * <p>An empty or short verdict list is refused rather than treated as a pass: {@link
   * TrackCheckResult} rolls up with {@code allMatch}, so a dropped or mis-tracked adapter would
   * report nothing-to-check as fully validated and verify a schedule nothing checked.
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
   * The {@code LICENSEE_MILL_ID} / {@code LICENSEE_USER_GUID} pair to record; both null when none.
   */
  private record Licensee(Long millId, String userGuid) {
    static final Licensee NONE = new Licensee(null, null);
  }
}
