package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.exception.ReportNotSubmittedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionFailedException;
import ca.bc.gov.nrs.ilcr.exception.ReportTransitionRejectedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
  @Mock private ReportTransitionRepository repository;
  @InjectMocks private ReportTransitionService service;

  private static ScheduleCheckResult met(String schedule) {
    return new ScheduleCheckResult(schedule, true, null);
  }

  private static ScheduleCheckResult notMet(String schedule) {
    return new ScheduleCheckResult(schedule, false, null);
  }

  private void givenTrackAt(String code) {
    when(repository.lockAndReadRevision(MILL, YEAR)).thenReturn(Optional.of(0));
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

  /** The category advance must report ten rows or the service refuses to commit. */
  private void givenTenCategoryRowsAdvance() {
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(10);
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
  @DisplayName("a stored NULL track code is a refused transition, not a 404 for a missing report")
  void nullTrackCodeIsRefusedNotNotFound() {
    when(repository.lockAndReadRevision(MILL, YEAR)).thenReturn(Optional.of(0));
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

  @Test
  @DisplayName("no status row -> 404, before the gate is even consulted")
  void missingStatusRow() {
    when(repository.lockAndReadRevision(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ScheduleNotFoundException.class);
  }

  @Test
  @DisplayName("AC12: a stale revision -> StaleRevisionException")
  void staleRevision() {
    givenTrackAt("S");
    givenGatePasses();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(0);

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(StaleRevisionException.class);
  }

  @Test
  @DisplayName("AC11: a persistence failure becomes a 500 and the sweep stops")
  void persistenceFailure() {
    givenTrackAt("S");
    givenGatePasses();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryStates(anyLong(), anyInt(), anyString(), anyString()))
        .thenThrow(new DataIntegrityViolationException("category update failed"));

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
  }

  @Test
  @DisplayName("no directory GUID records no auditor, and the transition still succeeds")
  void noDirectoryGuidRecordsNoAuditor() {
    givenTrackAt("S");
    givenGatePasses();
    when(repository.updateTrackStatusWithAuditor(
            eq(MILL), eq(YEAR), eq("V"), eq(null), eq(null), eq(0), eq(USER)))
        .thenReturn(1);
    givenTenCategoryRowsAdvance();

    assertThat(service.verifySchedules1To10(MILL, YEAR, USER, null)).isEqualTo("V");
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", null, null, 0, USER);
  }

  @Test
  @DisplayName("AC3: all twenty audit statements — thirteen distinct tables — are invoked")
  void invokesEveryAuditSweep() {
    givenTrackAt("S");
    givenGatePasses();
    givenTenCategoryRowsAdvance();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(1);

    service.verifySchedules1To10(MILL, YEAR, USER, GUID);

    verify(repository).advanceCategoryStates(MILL, YEAR, "V", USER);
    verify(repository).stampReportSummaries(MILL, YEAR, USER);
    verify(repository).stampSummaryCostDetails(MILL, YEAR, USER);
    verify(repository).stampTransportationReports(MILL, YEAR, USER);
    verify(repository).stampTransportationCostDetails(MILL, YEAR, USER);
    verify(repository).stampCampReports(MILL, YEAR, USER);
    verify(repository).stampCampCostDetails(MILL, YEAR, USER);
    verify(repository).stampRoadMaintenanceReports(MILL, YEAR, USER);
    verify(repository).stampRoadMaintenanceCostDetails(MILL, YEAR, USER);
    verify(repository).stampBridgeReports(MILL, YEAR, USER);
    verify(repository).stampBridgeCostDetails(MILL, YEAR, USER);
    verify(repository).stampCulvertReports(MILL, YEAR, USER);
    verify(repository).stampCulvertCostDetails(MILL, YEAR, USER);
    verify(repository).stampTreeToTruckReports(MILL, YEAR, USER);
    verify(repository).stampTreeToTruckDetails(MILL, YEAR, USER);
    verify(repository).stampTreeToTruckRateDetails(MILL, YEAR, USER);
    verify(repository).stampContractualWorkReports(MILL, YEAR, USER);
    verify(repository).stampContractualWorkCostDetails(MILL, YEAR, USER);
    verify(repository).stampRoadConstructionReports(MILL, YEAR, USER);
    verify(repository).stampRoadConstructionDetails(MILL, YEAR, USER);
    verify(repository).stampRoadConstructionCostDetails(MILL, YEAR, USER);
  }

  @Test
  @DisplayName("the audit stamps run BEFORE the category advance — legacy's order")
  void stampsPrecedeTheCategoryAdvance() {
    givenTrackAt("S");
    givenGatePasses();
    givenTenCategoryRowsAdvance();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(1);

    service.verifySchedules1To10(MILL, YEAR, USER, GUID);

    // The ONLY guard against re-inverting this. Delivery derives ILCR_*_AUD.RECORD_STATE_CODE in a
    // BEFORE-UPDATE trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the only
    // pair that writes the 'S' snapshot Epic 16's original-value indicators read. S->V is
    // indifferent, so no acceptance test — and no fixture, the test snapshot has no triggers — can
    // see the order. D->S is NOT indifferent, and 15.3/Epic 18 extend this method.
    InOrder order = inOrder(repository);
    order.verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", MILL, GUID, 0, USER);
    order.verify(repository).stampReportSummaries(MILL, YEAR, USER);
    order.verify(repository).stampRoadConstructionCostDetails(MILL, YEAR, USER);
    order.verify(repository).advanceCategoryStates(MILL, YEAR, "V", USER);
  }

  @Test
  @DisplayName("fewer than ten category rows advanced fails the transition rather than committing")
  void shortCategoryAdvanceFailsTheTransition() {
    givenTrackAt("S");
    givenGatePasses();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(1);
    // A mill/year enrolled by an interrupted year-open really does carry fewer than eleven
    // category rows — ReportingYearService models it as EnrolmentState.PARTIAL and mill activation
    // refuses it. Legacy failed loudly here; a set-based UPDATE skips the missing rows silently, so
    // without this guard the endpoint answered 200 on a half-transitioned report.
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(8);

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
  }

  @Test
  @DisplayName("zero category rows advanced is refused too, not treated as nothing to do")
  void zeroCategoryAdvanceFailsTheTransition() {
    givenTrackAt("S");
    givenGatePasses();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(0);

    assertThatThrownBy(() -> service.verifySchedules1To10(MILL, YEAR, USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
  }
}
