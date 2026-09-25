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
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every report-status transition on the Schedules 1&ndash;10 track, and the Schedule 11 submit and
 * verify. The workflow domain owns these, not any schedule service (AD-5/AD-9): a transition spans
 * a track's categories and belongs to none of them. {@link TrackTransition} is the single rule
 * table &mdash; legacy drove submit, verify and both reversals through one parameterized method
 * ({@code SubmitReportDAO.submitReport}) keyed on the (from, to) pair, and that enum is that logic
 * in full, so Story 18.1's reversals extend this class rather than fork it. Story 17.1 did fork it;
 * this class is the two halves put back.
 *
 * <p><strong>The two transitions do NOT share a boundary, and that is deliberate on both
 * sides.</strong> Submit locks the status row first and gates inside the write transaction (15.3's
 * D10, which also buys it a specific "no longer in Draft" refusal message &mdash; a departure from
 * legacy the business ruled on 2026-09-17). Verify takes no lock and gates outside the write,
 * because that is where legacy put it, and answers a refusal with legacy's own verbatim {@code
 * reportSubmissionErrorMsg}; Epic 17's tie-breaker is legacy behaviour, and the resulting
 * gate&rarr;write race is legacy's too and is recorded open rather than closed &mdash; though since
 * Story 18.1's review its LOSER is refused rather than allowed to overwrite (an {@code
 * expectedCode} predicate on every status UPDATE), because 18.1 made {@code S} leave by a second
 * door. What they share is everything below the boundary: one repository, one rule table, one
 * exception family, one identity-resolution rule. Do not "harmonise" the two boundaries without
 * re-opening both rulings.
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
 * V} behind the track's own gate (eleven verdicts for 1&ndash;10, one for Schedule 11), recording
 * the acting user as the report's AUDITOR rather than its licensee ({@link
 * TrackTransition.Recorded}). Order is legacy's: read the current codes, run the gate, check the
 * transition is legal, then write. See {@link ReportTransitionWriter} for why the write is a
 * separate bean.
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

  /** The verdicts the Schedule 11 gate must produce: exactly one. */
  static final int EXPECTED_SCHEDULE_11_VERDICTS = 1;

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
   * Draft &rarr; Submitted for one track of a mill/year whose context the caller has already
   * validated ({@code MillContextService.validateMillYearActive}, AD-4 &mdash; not repeated here;
   * the locked read is the in-transaction existence check). The other track's status column and
   * category rows are never touched (UC-CHK-002 BR-08, UC-CHK-003 BR-06).
   *
   * <p>One method for both tracks because legacy had one shape for both: {@code
   * SubmitReportDAO.submitReportSchedule11():150-187} ran the same guard, the same status helper
   * and the same category map as {@code submitReport()}, differing only in which status column,
   * which row family and which category it named. {@code track} selects exactly those, plus how
   * many verdicts the gate must produce and which texts answer.
   *
   * @param track the track to submit
   * @param millId the mill id
   * @param year the reporting year
   * @param authentication the caller &mdash; decides whether Submit is offered at this status and
   *     which {@code ILCR_MILL_USER_XREF} row becomes the report's licensee
   * @param user the caller's audit name ({@code Authentication.getName()})
   * @return the legacy bundle key of the success message
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportTransitionRejectedException 409 &mdash; the track is not at Draft, in that
   *     track's words
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the track's checks fails
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  @Transactional
  public String submit(
      ScheduleTrack track, long millId, int year, Authentication authentication, String user) {
    Objects.requireNonNull(track, "track");
    TrackTransition submit = TrackTransition.SUBMIT;

    TrackStatusCodes codes =
        millContextService
            .lockTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current =
        track == ScheduleTrack.SCHEDULE_11 ? codes.schedule11Code() : codes.schedules1To10Code();
    // A NULL code is a refused transition with legacy's generic text, as verify() records: the
    // track
    // was never in Draft, so "no longer in Draft" would be untrue. Legacy dereferenced the status
    // here and NPE'd. Real rows carry a NULL silviculture code (the sweep withholds the button for
    // them, so only a direct request lands here).
    if (current == null) {
      log.info("{} submit 409: no status code for millId={} year={}", track, millId, year);
      throw new ReportTransitionRejectedException();
    }
    TrackTransition transition =
        TrackTransition.resolve(current, submit.to())
            .filter(submit::equals)
            .orElseThrow(() -> new ReportTransitionRejectedException(submit, track));
    if (!reportSubmission.canSubmit(authentication, current)) {
      log.debug("{} submit refused for millId={} year={}: not offered", track, millId, year);
      throw new ReportTransitionRejectedException(submit, track);
    }

    try {
      // The count is checked before the roll-up: TrackCheckResult rolls an empty list up as met, so
      // a dropped or mis-tracked adapter would otherwise submit a schedule nothing checked.
      List<ScheduleCheckResult> verdicts = sweepService.checkTrack(track, millId, year);
      int expected = expectedVerdicts(track);
      if (verdicts.size() != expected) {
        log.warn(
            "{} submit 409: expected {} schedule verdicts for millId={} year={} but got {}",
            track,
            expected,
            millId,
            year,
            verdicts.size());
        throw new ReportNotSubmittedException(submit, track);
      }
      if (!TrackCheckResult.of(current, verdicts).requirementsMet()) {
        log.debug("{} submit refused for millId={} year={}: gate not met", track, millId, year);
        throw new ReportNotSubmittedException(submit, track);
      }

      // ORDER IS LOAD-BEARING — status, then stamps, then category: only the (category D, status
      // S) pair makes the delivery BEFORE-UPDATE triggers write the 'S' snapshot the original-value
      // indicators read. See ReportTransitionWriter.write for the full note.
      Licensee licensee = resolveLicensee(authentication, millId);
      requireOneRow(
          writeSubmittedStatus(track, millId, year, current, transition.to(), licensee, user),
          millId,
          year,
          "status row");
      if (track == ScheduleTrack.SCHEDULE_11) {
        writer.stampSchedule11AuditColumns(millId, year, user);
      } else {
        writer.stampAuditColumns(millId, year, user);
      }
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
          "{} submit failed for millId={} year={}: {}",
          track,
          millId,
          year,
          NestedExceptionUtils.getMostSpecificCause(ex).toString());
      throw new ReportSubmissionException();
    }
    log.info("{} submitted for millId={} year={}", track, millId, year);
    return transition.successKey(track);
  }

  /** The one status statement that names this track's column, and only this track's. */
  private int writeSubmittedStatus(
      ScheduleTrack track,
      long millId,
      int year,
      String expectedCode,
      String newCode,
      Licensee licensee,
      String user) {
    if (track == ScheduleTrack.SCHEDULE_11) {
      return repository.updateSilvicultureTrackStatus(
          millId, year, expectedCode, newCode, licensee.millId(), licensee.userGuid(), user);
    }
    return repository.updateTrackStatus(
        millId, year, expectedCode, newCode, licensee.millId(), licensee.userGuid(), user);
  }

  /** Eleven verdicts for 1&ndash;10 (7A and 7B separately), one for Schedule 11. */
  private static int expectedVerdicts(ScheduleTrack track) {
    return track == ScheduleTrack.SCHEDULE_11
        ? EXPECTED_SCHEDULE_11_VERDICTS
        : EXPECTED_TRACK_VERDICTS;
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
   * Verify a submitted track: {@code S}&rarr;{@code V}, behind the same gate that guards that
   * track's submission, recording the acting user as the report's auditor and advancing the track's
   * category states. The other track is never read or written (BR-07/BR-08).
   *
   * <p>One method for both tracks, because legacy had one: {@code
   * CheckStatusMB.verifiedSchedule11():208-210} is a single call into the same {@code
   * submitSchedule11(code)} the Schedule 11 submit uses, and that reaches the same {@code
   * updateILCRMillReportStatus} as 1&ndash;10. {@code track} selects the status column, how many
   * verdicts the gate owes, and what the writer names.
   *
   * <p>Not {@code @Transactional}: the gate runs HERE, outside the write, exactly where legacy ran
   * it ({@code CheckStatusMB.submitReport:247-261} gates, {@code :271} calls the DAO). {@link
   * ReportTransitionWriter} owns the transaction. No row lock &mdash; legacy's, and D2 keeps it.
   * The status UPDATE does carry an {@code expectedCode} predicate since Story 18.1's code review:
   * the gate&rarr;write window stays open, but its loser is now refused (409) instead of
   * overwriting a concurrent Set to Draft with the illegal {@code D}&rarr;{@code V} jump. 17.1 left
   * the predicate off because, before 18.1, nothing but another verify could move an {@code S} row.
   *
   * @param track the track to verify
   * @param millId the mill, already validated as an active context by the caller (AD-4)
   * @param year the reporting year
   * @param actingUser the value for the audit columns, at most 30 characters
   * @param actorGuid the acting user's directory GUID, or null to record no auditor
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportTransitionRejectedException 409 with legacy's verbatim {@code
   *     reportSubmissionErrorMsg} &mdash; a no-op, either illegal Draft&harr;Verified jump, or a
   *     stored NULL code
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the track's checks fails
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  public String verify(
      ScheduleTrack track, long millId, int year, String actingUser, String actorGuid) {
    Objects.requireNonNull(track, "track");
    TrackStatusCodes codes =
        millContextService
            .findTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current =
        track == ScheduleTrack.SCHEDULE_11 ? codes.schedule11Code() : codes.schedules1To10Code();

    // A NULL code is NOT a missing row, and NOT a legality refusal either: legacy dereferenced the
    // status association here and would have thrown NPE straight to the JSF error page
    // (isMillReportStatusValid:451-455). A crash is not portable, and claiming success on a state
    // nobody can name would be worse, so this stays a refused transition (409). The 1-10 column is
    // NOT NULL in delivery (2026-09-16 probe); real rows DO carry a NULL silviculture code.
    if (current == null) {
      log.info(
          "Verify 409: the {} track carries no status code for millId={} year={}",
          track,
          millId,
          year);
      throw new ReportTransitionRejectedException();
    }

    // The gate runs BEFORE the write transaction and takes no row lock, as legacy did:
    // CheckStatusMB.submitReport evaluated all eleven validators in the bean and only then called
    // the service, which delegated to a DAO that opened its own transaction; submitSchedule11 ran
    // isSchedule11Valid() the same way (:213). Ratified 2026-09-16 as legacy parity; a concurrent
    // schedule save can invalidate the verdict between gate and write.
    requireTrackPassesValidation(TrackTransition.VERIFY, track, millId, year);

    TrackTransition transition =
        TrackTransition.resolve(current, TrackTransition.VERIFY.to())
            .filter(TrackTransition.VERIFY::equals)
            .orElseThrow(() -> refusedVerify(track, current, millId, year));

    return writer.write(
        track,
        millId,
        year,
        transition.from(),
        transition.to(),
        transition.categoryState(),
        actingUser,
        actorGuid);
  }

  /**
   * Reverse a Schedules 1&ndash;10 track: {@code S}&rarr;{@code D} (Set to Draft, UC-CHK-016) or
   * {@code V}&rarr;{@code S} (Set to Submit, UC-CHK-018), Story 18.1. One method for both, because
   * legacy had one: {@code CheckStatusMB.setToDraft()} and {@code setBackToSubmit()} are two
   * one-line calls into the same {@code submitReport(String)} that {@code submit()} and {@code
   * verified()} call, differing only in the status code they pass ({@code :302-318}).
   *
   * <p>Not {@code @Transactional}, and the order is VERIFY's, which is legacy's (D2): read the
   * codes, run the gate, check the transition is legal, then write. {@link ReportTransitionWriter}
   * owns the transaction. No row lock &mdash; two more holders of an unbounded {@code FOR UPDATE}
   * across the eleven-schedule fan-out is the wrong direction &mdash; but unlike VERIFY the status
   * UPDATE does carry an {@code expectedCode} predicate, so the gate&rarr;write race answers a 409
   * refusal instead of silently overwriting a concurrent transition (deviation (U)).
   *
   * <p><strong>The gate runs on the way back too, and it stays.</strong> {@code epics.md:2098},
   * CHK-016 BR-03, CHK-018 BR-03 and PRD FR5 all require it, and legacy's single {@code
   * submitReport} validated before every transition. D5, BA-ratified 2026-09-21: a Submitted report
   * can only acquire errors because a ministry user introduced them, and ADMIN edit rights at
   * Submitted ({@code ScheduleEditability.java:63-64}) let that user correct them in place and
   * retry &mdash; the gate stops the LICENSEE doing the repair, not the repair. What legacy got
   * wrong was the sentence: it reused the submit text here. See {@link
   * TrackTransition#gateFailedKey} (deviation (V)).
   *
   * <p>Neither reversal writes an identity pair (D1, AC 3), so no directory GUID is taken.
   *
   * @param millId the mill, already validated as an active context by the caller (AD-4)
   * @param year the reporting year
   * @param expected the reversal requested &mdash; {@link TrackTransition#SET_TO_DRAFT} or {@link
   *     TrackTransition#SET_TO_SUBMIT}
   * @param actingUser the value for the audit columns, at most 30 characters
   * @return the track's status code after the transition
   * @throws ScheduleNotFoundException no status row for the mill/year (the controller re-keys it)
   * @throws ReportNotSubmittedException 409 &mdash; at least one of the eleven checks fails
   * @throws ReportTransitionRejectedException 409 with the transition's own text (D3) &mdash; the
   *     track is not at {@code expected.from()}, which covers the no-op, both {@code D}&harr;{@code
   *     V} jumps, the wrong-direction reversal, the dead {@code O} code and a stored NULL
   * @throws ReportSubmissionException 500 &mdash; a write failed or affected no row; rolled back
   */
  public String reverse(long millId, int year, TrackTransition expected, String actingUser) {
    // Only the two identity-free transitions come through here. SUBMIT and VERIFY each owe an
    // identity pair that writeReversal never writes, so either routed this way would commit a
    // submit with no licensee or a verify with no auditor — silently. One call site today, but
    // both tracks share the enum, and the guard is cheaper than the incident.
    if (expected.recorded() != TrackTransition.Recorded.NONE) {
      throw new IllegalArgumentException(
          expected + " records an identity pair and is not a reversal");
    }
    TrackStatusCodes codes =
        millContextService
            .findTrackStatusCodes(millId, year)
            .orElseThrow(ScheduleNotFoundException::new);
    String current = codes.schedules1To10Code();

    // A NULL code is a refused transition, not a missing row and not a 500 — the same reading
    // verify() records at :257-261. Unreachable in delivery (the column is NOT NULL); defensive.
    // Unlike verify(), the refusal names the transition, so the text says which status the track
    // would have had to be in (D3).
    if (current == null) {
      log.info(
          "{} 409: the 1-10 track carries no status code for millId={} year={}",
          expected,
          millId,
          year);
      throw new ReportTransitionRejectedException(expected, ScheduleTrack.SCHEDULES_1_TO_10);
    }

    // Gate BEFORE legality, as legacy did and as verify() does — CheckStatusMB.submitReport
    // evaluated all eleven validators before calling the service at all (:247-271). A report that
    // fails validation is therefore refused with the transition's own gate text (gateFailedKey,
    // deviation (V)) even when the status transition it asked for was itself illegal.
    requireTrackPassesValidation(expected, ScheduleTrack.SCHEDULES_1_TO_10, millId, year);

    TrackTransition transition =
        TrackTransition.resolve(current, expected.to())
            .filter(expected::equals)
            .orElseThrow(
                () -> {
                  log.info(
                      "{} 409: transition {}->{} is not legal for millId={} year={}",
                      expected,
                      current,
                      expected.to(),
                      millId,
                      year);
                  return new ReportTransitionRejectedException(
                      expected, ScheduleTrack.SCHEDULES_1_TO_10);
                });

    return writer.writeReversal(millId, year, transition, actingUser);
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
   * <p>Naming no transition rather than {@link TrackTransition#VERIFY} is therefore deliberate:
   * {@link TrackTransition#rejectedKey} carries Story 15.4's more specific wording on 1&ndash;10,
   * which is a business-ruled DEPARTURE from legacy and belongs to submit. Epic 17's tie-breaker is
   * legacy, and Epic 26 rules the same text for Schedule 11 (whose VERIFY {@code rejectedKey} names
   * this generic key, so the two agree).
   *
   * @param track the track, for the log line only
   * @param current the track's stored status code, for the log line only
   * @param millId the mill
   * @param year the reporting year
   * @return the exception to throw
   */
  private static ReportTransitionRejectedException refusedVerify(
      ScheduleTrack track, String current, long millId, int year) {
    log.info(
        "Verify 409: {} transition {}->{} is not legal for millId={} year={}",
        track,
        current,
        TrackTransition.VERIFY.to(),
        millId,
        year);
    return new ReportTransitionRejectedException();
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
   *
   * <p>Shared by Verify and both Story 18.1 reversals, which is legacy: one {@code
   * submitReport(String)} ran the identical eleven-validator expression before every transition
   * ({@code CheckStatusMB:247-261}). The {@code transition} names the log line AND the refusal text
   * &mdash; verify keeps legacy's generic message, the reversals carry their own (deviation (V),
   * {@link TrackTransition#gateFailedKey}). The gate itself is identical for all three.
   *
   * <p>A short verdict list raises the same exception as a genuine failure. It is an internal
   * integrity fault rather than a user error, but it is unreachable by construction and the log
   * lines distinguish them; inventing a second user-facing text for a state nobody can reach would
   * be worse than reusing this one.
   */
  private void requireTrackPassesValidation(
      TrackTransition transition, ScheduleTrack track, long millId, int year) {
    List<ScheduleCheckResult> verdicts = sweepService.checkTrack(track, millId, year);
    int expected = expectedVerdicts(track);
    if (verdicts.size() != expected) {
      log.warn(
          "{} {} 409: expected {} schedule verdicts for millId={} year={} but got {}",
          track,
          transition,
          expected,
          millId,
          year,
          verdicts.size());
      throw new ReportNotSubmittedException(transition, track);
    }
    if (!TrackCheckResult.of(null, verdicts).requirementsMet()) {
      log.info(
          "{} {} 409: validation gate failed for millId={} year={}",
          track,
          transition,
          millId,
          year);
      throw new ReportNotSubmittedException(transition, track);
    }
  }

  /**
   * The {@code LICENSEE_MILL_ID} / {@code LICENSEE_USER_GUID} pair to record; both null when none.
   */
  private record Licensee(Long millId, String userGuid) {
    static final Licensee NONE = new Licensee(null, null);
  }
}
