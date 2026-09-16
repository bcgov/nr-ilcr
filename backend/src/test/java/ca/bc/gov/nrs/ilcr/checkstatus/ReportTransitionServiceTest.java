package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.exception.ReportNotSubmittedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionRejectedException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the transition rules an acceptance test can only infer. The two tables here are
 * legacy's, and both are easy to transcribe wrongly: the legality table is a four-cell whitelist
 * whose rejected cases no fixture can reach through the endpoint, and the category-state table is
 * keyed current-then-target while legacy's own method signature takes them target-first.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportTransitionService — the transition rules (Story 17.1)")
class ReportTransitionServiceTest {

  private static final long MILL = 757L;
  private static final int YEAR = 2021;
  private static final String USER = "verifyadmin";
  private static final String GUID = "VERIFYADMIN0000111122223333AAAA1";

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportTransitionWriter writer;
  @InjectMocks private ReportTransitionService service;

  private static ScheduleCheckResult met(String schedule) {
    return new ScheduleCheckResult(schedule, true, null);
  }

  private static ScheduleCheckResult notMet(String schedule) {
    return new ScheduleCheckResult(schedule, false, null);
  }

  private void givenTrackAt(String code) {
    when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(code, "D")));
  }

  /**
   * The eleven verdicts the 1&ndash;10 track really yields (7A and 7B are separate {@code
   * CheckedSchedule}s). Eleven, not a convenient two: the service asserts the count, because a
   * short list rolls up through {@code allMatch} as a pass and would verify a schedule nothing
   * checked.
   */
  private static List<ScheduleCheckResult> elevenMet() {
    return List.of(
        met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("7B"), met("8"),
        met("9"), met("10"));
  }

  private static List<ScheduleCheckResult> elevenWithOneFailing() {
    return List.of(
        met("1"),
        notMet("2"),
        met("3"),
        met("4"),
        met("5"),
        met("6"),
        met("7A"),
        met("7B"),
        met("8"),
        met("9"),
        met("10"));
  }

  private void givenGatePasses() {
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(elevenMet());
  }

  /** The write succeeds and reports the target status back. */
  private void givenTheWriteSucceeds() {
    when(writer.write(MILL, YEAR, "V", "V", USER, GUID)).thenReturn("V");
  }

  @Nested
  @DisplayName("the legality table")
  class Legality {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({"D,S", "S,V", "V,S", "S,D"})
    @DisplayName("the four moves legacy permitted")
    void legalMoves(String current, String target) {
      assertThat(ReportTransitionService.isTransitionLegal(current, target)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({"D,D", "S,S", "V,V", "D,V", "V,D"})
    @DisplayName("no-ops and both direct Draft<->Verified jumps")
    void illegalMoves(String current, String target) {
      assertThat(ReportTransitionService.isTransitionLegal(current, target)).isFalse();
    }

    @Test
    @DisplayName("a null current status is never legal")
    void nullStatus() {
      assertThat(ReportTransitionService.isTransitionLegal(null, "V")).isFalse();
    }
  }

  @Nested
  @DisplayName("the category-state table")
  class CategoryStates {

    @ParameterizedTest(name = "{0} then {1} yields category state {2}")
    @CsvSource({"D,S,A", "S,V,V", "V,S,A", "S,D,D"})
    @DisplayName("keyed current-then-target, not the other way round")
    void pairs(String current, String target, String expected) {
      assertThat(ReportTransitionService.categoryStateFor(current, target)).isEqualTo(expected);
    }

    @Test
    @DisplayName("verify yields V, which the reversed key would have made A")
    void verifyIsNotReversed() {
      assertThat(ReportTransitionService.categoryStateFor("S", "V")).isEqualTo("V");
      assertThat(ReportTransitionService.categoryStateFor("V", "S")).isEqualTo("A");
    }

    @Test
    @DisplayName("a pair with no cell is refused rather than writing a null state")
    void unmappedPair() {
      assertThatThrownBy(() -> ReportTransitionService.categoryStateFor("D", "V"))
          .isInstanceOf(ReportTransitionRejectedException.class);
    }
  }

  @Test
  @DisplayName("AC9: an empty verdict list is a failed gate, not a vacuous pass")
  void emptyVerdictListFailsTheGate() {
    givenTrackAt("S");
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(List.of());

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
  }

  @Test
  @DisplayName("AC9: TEN all-met verdicts is a failed gate too — the track owes eleven")
  void shortVerdictListFailsTheGate() {
    givenTrackAt("S");
    // Every verdict MET, one schedule simply missing from the sweep. TrackCheckResult rolls up
    // with allMatch, so this is the shape that would otherwise verify a report whose 7B was never
    // checked — and the old isEmpty() guard could not see it.
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(
            List.of(
                met("1"), met("2"), met("3"), met("4"), met("5"), met("6"), met("7A"), met("8"),
                met("9"), met("10")));

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
  }

  @Test
  @DisplayName("no status row -> 404, before the gate is consulted")
  void missingStatusRow() {
    when(millContextService.findTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("a no-op is refused as an error and never reaches the writer (UC-CHK-007-S07)")
  void noOpIsRefused() {
    givenTrackAt("V");
    givenGatePasses();

    // Legacy showed the verified message here, because CheckStatusMB.submitReport:271 called the
    // DAO as a bare statement and never captured its false. UC-CHK-007-S07 records that as a known
    // defect ("defect — should be an error"), so it is not ported.
    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("the illegal D->V jump is refused as an error too, and writes nothing")
  void draftJumpIsRefused() {
    givenTrackAt("D");
    givenGatePasses();

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("a legal S->V delegates to the transactional writer with the SV category state")
  void legalTransitionDelegatesToTheWriter() {
    givenTrackAt("S");
    givenGatePasses();
    givenTheWriteSucceeds();

    assertThat(service.verifySchedules1To10(MILL, YEAR, USER, GUID)).isEqualTo("V");
    verify(writer).write(MILL, YEAR, "V", "V", USER, GUID);
  }

  @Test
  @DisplayName("the gate runs BEFORE the writer is touched, as legacy gated before its DAO call")
  void gateRunsBeforeTheWrite() {
    givenTrackAt("S");
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(elevenWithOneFailing());

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(writer);
  }

  @Test
  @DisplayName("a stored NULL track code is a refused transition, not a 404 for a missing report")
  void nullTrackCodeIsRefusedNotNotFound() {
    // The column is nullable and a null 1-10 code has been a tolerated shape since Story 1.2.
    // Collapsing it into the Optional answered 404 for a report whose status row plainly exists.
    when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(null, "D")));

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class);
  }

  @Test
  @DisplayName("a failing schedule fails the gate before any write")
  void failingScheduleFailsTheGate() {
    givenTrackAt("S");
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(elevenWithOneFailing());

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
  }

  @Test
  @DisplayName("the gate runs before the legality check, as legacy's did")
  void gateRunsBeforeLegality() {
    givenTrackAt("D");
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenReturn(elevenWithOneFailing());

    // A Draft track is also an illegal target, but the gate is what answers first. The list is
    // eleven long so this fails on the failing schedule, not on the verdict count.
    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportNotSubmittedException.class);
  }
}
