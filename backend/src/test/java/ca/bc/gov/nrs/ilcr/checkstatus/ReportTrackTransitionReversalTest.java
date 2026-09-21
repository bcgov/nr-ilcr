package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The two admin reversals on {@link ReportTrackTransitionService} (Story 18.1) — Set to Draft
 * ({@code S}&rarr;{@code D}, UC-CHK-016) and Set to Submit ({@code V}&rarr;{@code S}, UC-CHK-018).
 *
 * <p>A third file beside the submit and verify tests for the reason the verify one gives: each
 * transition's own order of operations is what its story ruled, and keeping them apart is what
 * stops one story's ruling being "tidied" into another's. The legality table and the category-state
 * map are NOT retested here — they are {@link TrackTransition}'s and {@code TrackTransitionTest}
 * owns them.
 *
 * <p>What IS here and nowhere else: that the gate runs before legality (so a report failing
 * validation is refused for validation even when the transition it asked for was also illegal),
 * that the refusals carry each transition's own text rather than 17.1's generic fallback (D3), and
 * that no refusal of any kind reaches the writer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportTrackTransitionService.reverse — the two admin reversals (Story 18.1)")
class ReportTrackTransitionReversalTest {

  private static final long MILL = 790L;
  private static final int YEAR = 2021;
  private static final String USER = "reversaladmin";

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportSubmission reportSubmission;
  @Mock private MillUserXrefRepository millUserXrefRepository;
  @Mock private ReportTrackTransitionRepository repository;
  @Mock private ReportTransitionWriter writer;
  @InjectMocks private ReportTrackTransitionService service;

  private static ScheduleCheckResult met(String schedule) {
    return new ScheduleCheckResult(schedule, true, null);
  }

  private void givenTrackAt(String code) {
    when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(code, "D")));
  }

  /** All eleven verdicts met — 7A and 7B are separate CheckedSchedules. */
  private void givenGatePasses() {
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(
            List.of(
                met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("7B"),
                met("8"), met("9"), met("10")));
  }

  private void givenGateFails() {
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(
            List.of(
                new ScheduleCheckResult("1", false, "Value Required"),
                met("2"),
                met("3"),
                met("4"),
                met("5"),
                met("6"),
                met("7A"),
                met("7B"),
                met("8"),
                met("9"),
                met("10")));
  }

  // -----------------------------------------------------------------------------------------------
  // Happy paths
  // -----------------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC1: a legal S->D delegates to the writer and returns the new status")
  void setToDraftDelegatesToTheWriter() {
    givenTrackAt("S");
    givenGatePasses();
    when(writer.writeReversal(MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER)).thenReturn("D");

    assertThat(service.reverse(MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER)).isEqualTo("D");
    verify(writer).writeReversal(MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER);
  }

  @Test
  @DisplayName("AC2: a legal V->S delegates to the writer and returns the new status")
  void setToSubmitDelegatesToTheWriter() {
    givenTrackAt("V");
    givenGatePasses();
    when(writer.writeReversal(MILL, YEAR, TrackTransition.SET_TO_SUBMIT, USER)).thenReturn("S");

    assertThat(service.reverse(MILL, YEAR, TrackTransition.SET_TO_SUBMIT, USER)).isEqualTo("S");
    verify(writer).writeReversal(MILL, YEAR, TrackTransition.SET_TO_SUBMIT, USER);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("D2: the reversals read the status WITHOUT a lock, as verify does")
  void reversalsDoNotLockTheStatusRow(TrackTransition transition) {
    givenTrackAt(transition.from());
    givenGatePasses();
    when(writer.writeReversal(MILL, YEAR, transition, USER)).thenReturn(transition.to());

    service.reverse(MILL, YEAR, transition, USER);

    verify(millContextService).findTrackStatusCodes(MILL, YEAR);
    // Submit's FOR UPDATE is 15.3's D10. Two more holders of an unbounded lock across the
    // eleven-schedule fan-out is the wrong direction while deferred-work.md:12-16 is open; the
    // expectedCode predicate on the status UPDATE buys the lost-update refusal instead (D2).
    verify(millContextService, never()).lockTrackStatusCodes(MILL, YEAR);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC3: no identity pair is resolved — the reversals never look up a cross-reference")
  void reversalsResolveNoIdentity(TrackTransition transition) {
    givenTrackAt(transition.from());
    givenGatePasses();
    when(writer.writeReversal(MILL, YEAR, transition, USER)).thenReturn(transition.to());

    service.reverse(MILL, YEAR, transition, USER);

    // Submit and verify both resolve an ILCR_MILL_USER_XREF row to write into their identity pair.
    // With Recorded.NONE on both reversals there is nothing to resolve, and the service signature
    // takes no directory GUID at all — this pins that the lookup was not left behind.
    verifyNoInteractions(millUserXrefRepository, reportSubmission, repository);
  }

  // -----------------------------------------------------------------------------------------------
  // The gate (AC5) — and the fact that it runs BEFORE legality
  // -----------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC5: a failing verdict refuses the reversal and writes nothing")
  void failingGateRefusesTheReversal(TrackTransition transition) {
    givenTrackAt(transition.from());
    givenGateFails();

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "SET_TO_DRAFT, setToDraftNotValidErrorMsg",
    "SET_TO_SUBMIT, setToSubmitNotValidErrorMsg",
  })
  @DisplayName("deviation (V): the gate refusal names the action the user took, not 'submitted'")
  void gateRefusalCarriesTheReversalText(TrackTransition transition, String expectedKey) {
    // Legacy reused reportNotSubmittedErrorMsg here ("The report cannot be submitted...") for every
    // transition, so a ministry user clicking Set to Draft was told their SUBMISSION had failed.
    // BA-ratified 2026-09-21: the gate is correct and unchanged — a Submitted report can only
    // acquire errors because a ministry user introduced them, and ADMIN edit rights at Submitted
    // (ScheduleEditability:63-64) let that user correct them in place and retry. Wording only.
    givenTrackAt(transition.from());
    givenGateFails();

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo(expectedKey));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("a short verdict list carries the reversal text too, not legacy's")
  void shortVerdictListCarriesTheReversalText(TrackTransition transition) {
    givenTrackAt(transition.from());
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(List.of(met("1")));

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo(transition.gateFailedKey()));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC5: an empty verdict list is a failed gate, not a vacuous pass")
  void emptyVerdictListFailsTheGate(TrackTransition transition) {
    givenTrackAt(transition.from());
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC5: TEN all-met verdicts is a failed gate too — the track owes eleven")
  void shortVerdictListFailsTheGate(TrackTransition transition) {
    givenTrackAt(transition.from());
    // Every verdict MET, one schedule simply absent from the sweep. TrackCheckResult rolls up with
    // allMatch, so a dropped adapter would otherwise read as fully validated and an isEmpty() guard
    // could not see it.
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(
            List.of(
                met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("8"),
                met("9"), met("10")));

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName(
      "D5: the gate runs BEFORE legality, so an illegal reversal on a failing report"
          + " is refused for validation")
  void gatePrecedesLegality() {
    // Track at Draft, so S->D is illegal — AND the report fails validation. Legacy's single
    // submitReport evaluated all eleven validators in the bean before the DAO's guard ever ran
    // (CheckStatusMB:247-271), so validation is what the user is told about. This ordering is also
    // what makes D5's consequence real: a Submitted report that fails validation cannot be sent
    // back to Draft to be repaired, because the gate blocks the repair.
    givenTrackAt("D");
    givenGateFails();

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  // -----------------------------------------------------------------------------------------------
  // Legality (AC7) — every illegal pair, and the specific text (D3)
  // -----------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{1} -> Set to Draft is refused")
  @CsvSource({
    // the no-op: already at Draft
    "SET_TO_DRAFT, D",
    // the wrong direction: Set to Draft at Verified is the illegal V->D jump
    "SET_TO_DRAFT, V",
    // the dead O status, which no category-state pair ever named
    "SET_TO_DRAFT, O",
    // the no-op: already at Submitted
    "SET_TO_SUBMIT, S",
    // D->S is a LEGAL pair, but it is the licensee's SUBMIT, not this endpoint's reversal
    "SET_TO_SUBMIT, D",
    "SET_TO_SUBMIT, O",
  })
  @DisplayName("AC7: every pair that is not this transition is refused, and nothing is written")
  void illegalPairsAreRefused(TrackTransition transition, String current) {
    givenTrackAt(current);
    givenGatePasses();

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "SET_TO_DRAFT, setToDraftNotSubmittedErrorMsg",
    "SET_TO_SUBMIT, setToSubmitNotVerifiedErrorMsg",
  })
  @DisplayName("D3: a refusal carries the transition's own text, not 17.1's generic fallback")
  void refusalCarriesTheSpecificKey(TrackTransition transition, String expectedKey) {
    // VERIFY deliberately passes null so the exception falls back to reportSubmissionErrorMsg
    // ("contact ILCR application support"). Both reversal keys were written into the bundle FOR
    // this story (messages.properties:41-45), and a status-legality refusal is strictly more
    // truthful than a support message. Deviation (T) — 17.1 chose differently for VERIFY and that
    // ruling stands.
    givenTrackAt(transition.to());
    givenGatePasses();

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo(expectedKey));
  }

  // -----------------------------------------------------------------------------------------------
  // Context (AC8) and the defensive NULL
  // -----------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("no status row -> 404, before the gate is consulted")
  void missingStatusRow(TrackTransition transition) {
    when(millContextService.findTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOf(ScheduleNotFoundException.class);
    verifyNoInteractions(sweepService, writer);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("a stored NULL status code is refused with the transition's text, not a 500")
  void nullStatusCodeIsRefused(TrackTransition transition) {
    // Unreachable in delivery — the column is NOT NULL (2026-09-16 probe) — and legacy would have
    // thrown NPE straight to the JSF error page here (isMillReportStatusValid:451-455). A crash is
    // not portable and a success on a state nobody can name would be worse, so it is a 409. Unlike
    // verify(), the refusal names the transition (D3), so the text says which status was required.
    givenTrackAt(null);

    assertThatThrownBy(() -> service.reverse(MILL, YEAR, transition, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo(transition.rejectedKey()));
    verifyNoInteractions(sweepService, writer);
  }
}
