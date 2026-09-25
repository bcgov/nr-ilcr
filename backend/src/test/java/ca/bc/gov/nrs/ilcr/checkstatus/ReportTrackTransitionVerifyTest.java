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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The VERIFY half of {@link ReportTrackTransitionService} — what an acceptance test can only infer.
 * Ported from {@code ReportTransitionServiceTest} when Story 17.1's forked service was folded into
 * this one; a separate file from {@code ReportTrackTransitionServiceTest} so Story 15.3's submit
 * tests stay exactly as they shipped.
 *
 * <p>The legality table and the category-state pairs are NOT retested here — they are {@link
 * TrackTransition}'s now, and {@code TrackTransitionTest} owns them. That is the whole point of the
 * unification: one rule table, tested once. What remains here is the service's own order of
 * operations, which is legacy's and is the part that was duplicated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportTrackTransitionService.verify — the transition rules (Story 17.1)")
class ReportTrackTransitionVerifyTest {

  private static final long MILL = 764L;
  private static final int YEAR = 2021;
  private static final String USER = "verifyadmin";
  private static final String GUID = "VERIFYADMIN0000111122223333AAAA1";

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

  @Test
  @DisplayName("verify reads the status WITHOUT a lock — legacy's position, and 15.3's is not it")
  void verifyDoesNotLockTheStatusRow() {
    givenTrackAt("S");
    givenGatePasses();
    when(writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .thenReturn("V");

    service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID);

    verify(millContextService).findTrackStatusCodes(MILL, YEAR);
    // The lock is submit's (15.3 D10). Verify keeping it off is Epic 17's ratified legacy parity,
    // and the gate->write race it leaves is recorded open in deferred-work.md, not closed here.
    verify(millContextService, never()).lockTrackStatusCodes(MILL, YEAR);
  }

  @Test
  @DisplayName("AC9: an empty verdict list is a failed gate, not a vacuous pass")
  void emptyVerdictListFailsTheGate() {
    givenTrackAt("S");
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(List.of());

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("AC9: TEN all-met verdicts is a failed gate too — the track owes eleven")
  void shortVerdictListFailsTheGate() {
    givenTrackAt("S");
    // Every verdict MET, one schedule simply missing from the sweep. TrackCheckResult rolls up
    // with allMatch, so this is the shape that would otherwise verify a report whose 7B was never
    // checked — and an isEmpty() guard could not see it.
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(
            List.of(
                met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("8"),
                met("9"), met("10")));

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("no status row -> 404, before the gate is consulted")
  void missingStatusRow() {
    when(millContextService.findTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ScheduleNotFoundException.class);
    verifyNoInteractions(sweepService, writer);
  }

  @Test
  @DisplayName("a stored NULL status code is refused as an error, not treated as a missing row")
  void nullStatusCodeIsRefused() {
    givenTrackAt(null);

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("a no-op is refused as an error and never reaches the writer")
  void noOpIsRefused() {
    givenTrackAt("V");
    givenGatePasses();

    // Legacy's outcome too: the DAO's false became ILCSException(SCHEDULE_NOT_SUBMITTED) in
    // ILCRService.submitReport:718-723, and the bean's catch rendered the error instead of the
    // verified message. UC-CHK-007-S07 calls it a silent success, having read only the bean and
    // the DAO.
    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("the illegal D->V jump is refused as an error too, and never reaches the writer")
  void draftJumpIsRefused() {
    givenTrackAt("D");
    givenGatePasses();

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("a refused verify carries LEGACY's generic text, not 15.4's specific wording")
  void refusalUsesTheLegacyGenericKey() {
    givenTrackAt("V");
    givenGatePasses();

    // TrackTransition.VERIFY.rejectedKey(SCHEDULES_1_TO_10) is "verifyNotSubmittedErrorMsg" —
    // 15.4's ruled
    // DEPARTURE from legacy, which belongs to submit. Verify answers what legacy answered.
    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
  }

  @Test
  @DisplayName("a legal S->V delegates to the transactional writer with the SV category state")
  void legalTransitionDelegatesToTheWriter() {
    givenTrackAt("S");
    givenGatePasses();
    when(writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .thenReturn("V");

    assertThat(service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isEqualTo("V");
    verify(writer).write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID);
  }

  @Test
  @DisplayName("1-10 verify never runs the Schedule 11 gate")
  void oneToTenNeverChecksSchedule11() {
    givenTrackAt("S");
    givenGatePasses();
    when(writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .thenReturn("V");

    service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID);

    verify(sweepService, never()).checkTrack(ELEVEN, MILL, YEAR);
  }

  // -----------------------------------------------------------------------------------------------
  // Story 26.3 — the same verify on the Schedule 11 track. Legacy verifiedSchedule11() was one line
  // into the same submitSchedule11(code) as the Schedule 11 submit (CheckStatusMB:208-210), so the
  // track is a parameter here too, never a second method.
  // -----------------------------------------------------------------------------------------------

  private static final ScheduleTrack ELEVEN = ScheduleTrack.SCHEDULE_11;

  /** The two codes deliberately differ, so reading the wrong column is visible. */
  private void givenCodes(String schedules1To10, String schedule11) {
    when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(schedules1To10, schedule11)));
  }

  private void givenSchedule11Gate(List<ScheduleCheckResult> verdicts) {
    when(sweepService.checkTrack(ELEVEN, MILL, YEAR)).thenReturn(verdicts);
  }

  @Test
  @DisplayName("Schedule 11: S->V reads the silviculture code, runs ITS gate, writes on its track")
  void schedule11LegalTransitionDelegatesOnItsTrack() {
    givenCodes("D", "S");
    givenSchedule11Gate(List.of(met("11")));
    when(writer.write(ELEVEN, MILL, YEAR, "S", "V", "V", USER, GUID)).thenReturn("V");

    assertThat(service.verify(ELEVEN, MILL, YEAR, USER, GUID)).isEqualTo("V");

    verify(writer).write(ELEVEN, MILL, YEAR, "S", "V", "V", USER, GUID);
    verify(sweepService, never()).checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR);
    verify(millContextService, never()).lockTrackStatusCodes(MILL, YEAR);
  }

  @Test
  @DisplayName("Schedule 11: a 1-10 track at S does not make Schedule 11 verifiable")
  void schedule11ReadsItsOwnColumn() {
    // 1-10 at S, Schedule 11 at D: reading the 1-10 column would admit this verify.
    givenCodes("S", "D");
    givenSchedule11Gate(List.of(met("11")));

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("1-10: a Schedule 11 track at S does not make 1-10 verifiable")
  void oneToTenReadsItsOwnColumn() {
    givenCodes("D", "S");
    givenGatePasses();

    assertThatThrownBy(
            () -> service.verify(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("Schedule 11: a failing location check is the gate's 409, nothing written")
  void schedule11GateFails() {
    givenCodes("D", "S");
    givenSchedule11Gate(List.of(new ScheduleCheckResult("11", false, null)));

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg"));
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("Schedule 11: zero verdicts is a failed gate, not a vacuous pass")
  void schedule11NoVerdictFailsTheGate() {
    givenCodes("D", "S");
    givenSchedule11Gate(List.of());

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("Schedule 11: two verdicts is a failed gate — the track owes exactly one")
  void schedule11TwoVerdictsFailTheGate() {
    givenCodes("D", "S");
    givenSchedule11Gate(List.of(met("11"), met("11")));

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("Schedule 11: eleven met verdicts (the 1-10 count) is a failed gate too")
  void schedule11ElevenVerdictsFailTheGate() {
    // Pins that the count follows the track: a gate still comparing against 1-10's eleven would
    // pass this list and verify a Schedule 11 that nothing checked as Schedule 11.
    givenCodes("D", "S");
    givenSchedule11Gate(
        List.of(
            met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("7B"),
            met("8"), met("9"), met("10")));

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("Schedule 11: a NULL silviculture code is the generic 409, before the gate")
  void schedule11NullCodeIsRefused() {
    givenCodes("S", null);

    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(sweepService, writer);
  }

  @ParameterizedTest(name = "from {0}")
  @ValueSource(strings = {"D", "V"})
  @DisplayName("Schedule 11: a track not at S is refused with legacy's generic text")
  void schedule11NotSubmittedIsRefused(String code) {
    givenCodes("D", code);
    givenSchedule11Gate(List.of(met("11")));

    // The epic's ruling (legacy parity, not a fix): no Schedule 11 "not Submitted" text exists.
    assertThatThrownBy(() -> service.verify(ELEVEN, MILL, YEAR, USER, GUID))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("there is no default track: a null track is refused before any read")
  void nullTrackIsRefused() {
    assertThatThrownBy(() -> service.verify(null, MILL, YEAR, USER, GUID))
        .isInstanceOf(NullPointerException.class);
    verifyNoInteractions(millContextService, sweepService, writer);
  }
}
