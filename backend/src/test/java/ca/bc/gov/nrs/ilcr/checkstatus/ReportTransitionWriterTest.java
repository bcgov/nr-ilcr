package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefEntity;
import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the write half of a Schedules 1&ndash;10 transition.
 *
 * <p>These exist because an earlier round extracted {@link ReportTransitionWriter} out of {@code
 * ReportTransitionService} and <strong>deleted</strong> the service tests that had guarded the
 * moved code instead of moving them with it. Every case here is one of those, restored: the write
 * order, the completeness of the twenty-statement sweep, both fail-closed row-count guards, and the
 * no-directory-GUID short-circuit. None of them is observable from an acceptance test — the Oracle
 * snapshot carries no audit triggers and no fixture reaches a partial-enrolment shape — so this
 * class is the only thing standing between those behaviours and a silent regression. It is also
 * what puts the component in the surefire stream the coverage gate actually reads.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportTransitionWriter — the transactional write half (Story 17.1)")
class ReportTransitionWriterTest {

  private static final long MILL = 764L;
  private static final int YEAR = 2021;
  private static final String USER = "verifyadmin";
  private static final String GUID = "VERIFYADMIN0000111122223333AAAA1";

  @Mock private ReportTrackTransitionRepository repository;
  @Mock private MillUserXrefRepository millUserXrefRepository;
  @InjectMocks private ReportTransitionWriter writer;

  /** The ten category rows a 1-10 transition advances, in the order the track declares them. */
  private static final java.util.List<String> CATEGORIES =
      ScheduleTrack.SCHEDULES_1_TO_10.categoryIds();

  private void givenTheStatusRowMoves() {
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
  }

  private void givenTenCategoryRowsAdvance() {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(MILL, YEAR, categoryId, "V", USER)).thenReturn(1);
    }
  }

  /** As above, but ONE category row missing — nine advance, the partial-enrolment shape. */
  private void givenOneCategoryRowIsMissing() {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(MILL, YEAR, categoryId, "V", USER))
          .thenReturn(CATEGORIES.get(0).equals(categoryId) ? 0 : 1);
    }
  }

  /** No category row advances at all — zero, which must not read as "nothing to do". */
  private void givenNoCategoryRowsAdvance() {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(MILL, YEAR, categoryId, "V", USER)).thenReturn(0);
    }
  }

  private void givenTheAuditorIsAssigned() {
    when(millUserXrefRepository.findAssignment(MILL, GUID))
        .thenReturn(
            Optional.of(new MillUserXrefEntity(MILL, GUID, null, null, 0, null, null, null, null)));
  }

  @Test
  @DisplayName("the audit stamps run BEFORE the category advance — legacy's order")
  void stampsPrecedeTheCategoryAdvance() {
    givenTheStatusRowMoves();
    givenTenCategoryRowsAdvance();
    givenTheAuditorIsAssigned();

    writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID);

    // The ONLY guard against re-inverting this. Delivery derives ILCR_*_AUD.RECORD_STATE_CODE in a
    // BEFORE-UPDATE trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the only
    // pair that writes the 'S' snapshot Epic 16's original-value indicators read. S->V is
    // indifferent to the order, so no acceptance test can see it — the IT snapshot has no triggers
    // at all. D->S is NOT indifferent, and Story 15.3 / Epic 18 extend this component.
    InOrder order = inOrder(repository);
    order.verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", "S", MILL, GUID, USER);
    order.verify(repository).touchReportSummaries(MILL, YEAR, USER);
    order.verify(repository).touchRoadConstructionCostDetails(MILL, YEAR, USER);
    order.verify(repository).advanceCategoryState(MILL, YEAR, CATEGORIES.get(0), "V", USER);
  }

  @Test
  @DisplayName("AC3: all twenty audit statements — thirteen distinct tables — are invoked")
  void invokesEveryAuditSweep() {
    givenTheStatusRowMoves();
    givenTenCategoryRowsAdvance();
    givenTheAuditorIsAssigned();

    writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID);

    verify(repository).touchReportSummaries(MILL, YEAR, USER);
    verify(repository).touchReportSummaryCostDetails(MILL, YEAR, USER);
    verify(repository).touchTransportationReports(MILL, YEAR, USER);
    verify(repository).touchTransportationCostDetails(MILL, YEAR, USER);
    verify(repository).touchCamps(MILL, YEAR, USER);
    verify(repository).touchCampCostDetails(MILL, YEAR, USER);
    verify(repository).touchRoadMaintenanceReports(MILL, YEAR, USER);
    verify(repository).touchRoadMaintenanceCostDetails(MILL, YEAR, USER);
    verify(repository).touchBridges(MILL, YEAR, USER);
    verify(repository).touchBridgeCostDetails(MILL, YEAR, USER);
    verify(repository).touchCulverts(MILL, YEAR, USER);
    verify(repository).touchCulvertCostDetails(MILL, YEAR, USER);
    verify(repository).touchTreeToTruckReports(MILL, YEAR, USER);
    verify(repository).touchTreeToTruckDetailReports(MILL, YEAR, USER);
    verify(repository).touchTreeToTruckRateDetails(MILL, YEAR, USER);
    verify(repository).touchContractualWorkReports(MILL, YEAR, USER);
    verify(repository).touchContractualWorkCostDetails(MILL, YEAR, USER);
    verify(repository).touchRoadConstructionReports(MILL, YEAR, USER);
    verify(repository).touchRoadConstructionDetails(MILL, YEAR, USER);
    verify(repository).touchRoadConstructionCostDetails(MILL, YEAR, USER);
  }

  @Test
  @DisplayName("fewer than ten category rows advanced fails the transition rather than committing")
  void shortCategoryAdvanceFailsTheTransition() {
    givenTheStatusRowMoves();
    // A mill/year enrolled by an interrupted year-open really does carry fewer than eleven category
    // rows — ReportingYearService models it as EnrolmentState.PARTIAL and mill activation refuses
    // it. Legacy failed loudly; a set-based UPDATE skips the missing rows silently, so without this
    // the endpoint answered 200 on a half-transitioned report. No fixture reaches the shape.
    givenOneCategoryRowIsMissing();
    givenTheAuditorIsAssigned();

    assertThatThrownBy(
            () ->
                writer.write(
                    ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName("zero category rows advanced is refused too, not treated as nothing to do")
  void zeroCategoryAdvanceFailsTheTransition() {
    givenTheStatusRowMoves();
    givenNoCategoryRowsAdvance();
    givenTheAuditorIsAssigned();

    assertThatThrownBy(
            () ->
                writer.write(
                    ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName(
      "a verify whose status write matches no row is a 409 refusal, not a 500, and the sweep never runs")
  void verifyLostUpdateIsRefusedNotFailed() {
    // Added by Story 18.1's code review. Before 18.1 the only other writer of an S row was another
    // verify, so 17.1 left the UPDATE unconditional; 18.1's Set to Draft made S leave by a second
    // door, and a verify landing after it would have committed the illegal D->V jump with a 200.
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(0);
    givenTheAuditorIsAssigned();

    assertThatThrownBy(
            () ->
                writer.write(
                    ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class)
        .hasMessage(ReportTransitionRejectedException.GENERIC_KEY);
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SUBMIT", "VERIFY"})
  @DisplayName("a transition that owes an identity pair is refused by writeReversal before any SQL")
  void writeReversalRefusesIdentityOwingTransitions(TrackTransition transition) {
    assertThatThrownBy(() -> writer.writeReversal(REVERSAL_MILL, YEAR, transition, USER))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("all twenty audit statements — thirteen tables — are invoked on the reversal path")
  void reversalInvokesEveryAuditSweep(TrackTransition transition) {
    // The twin of invokesEveryAuditSweep above, which exercises write() only. Without this, a
    // narrowed reversal sweep (say, stamping Sch 1-3 alone) ships green: R__56 carries no Sch
    // 4-10 rows, so the ITs cannot see those seventeen statements, and the InOrder case verifies
    // just two. Found by the 18.1 code review's verification-gap layer.
    givenTheReversalStatusRowMoves(transition);
    givenTenCategoryRowsAdvanceTo(transition.categoryState());

    writer.writeReversal(REVERSAL_MILL, YEAR, transition, USER);

    verify(repository).touchReportSummaries(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchReportSummaryCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchTransportationReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchTransportationCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchCamps(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchCampCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchRoadMaintenanceReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchRoadMaintenanceCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchBridges(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchBridgeCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchCulverts(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchCulvertCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchTreeToTruckReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchTreeToTruckDetailReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchTreeToTruckRateDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchContractualWorkReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchContractualWorkCostDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchRoadConstructionReports(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchRoadConstructionDetails(REVERSAL_MILL, YEAR, USER);
    verify(repository).touchRoadConstructionCostDetails(REVERSAL_MILL, YEAR, USER);
  }

  @Test
  @DisplayName("no directory GUID records no auditor, and the transition still succeeds")
  void noDirectoryGuidRecordsNoAuditor() {
    when(repository.updateTrackStatusWithAuditor(MILL, YEAR, "V", "S", null, null, USER))
        .thenReturn(1);
    givenTenCategoryRowsAdvance();

    assertThat(writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, null))
        .isEqualTo("V");
    // The cross-reference lookup is skipped entirely rather than run with a null key.
    verify(millUserXrefRepository, never()).findAssignment(anyLong(), any());
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", "S", null, null, USER);
  }

  @Test
  @DisplayName("an admin with no cross-reference records NULL in both auditor columns")
  void noXrefRecordsNullAuditor() {
    when(millUserXrefRepository.findAssignment(MILL, GUID)).thenReturn(Optional.empty());
    when(repository.updateTrackStatusWithAuditor(MILL, YEAR, "V", "S", null, null, USER))
        .thenReturn(1);
    givenTenCategoryRowsAdvance();

    assertThat(writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .isEqualTo("V");
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", "S", null, null, USER);
  }

  @Test
  @DisplayName("a persistence failure becomes a 500 and stops the sweep")
  void persistenceFailure() {
    givenTheStatusRowMoves();
    givenTheAuditorIsAssigned();
    when(repository.touchReportSummaries(MILL, YEAR, USER))
        .thenThrow(new DataIntegrityViolationException("sweep failed"));

    assertThatThrownBy(
            () ->
                writer.write(
                    ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  // -----------------------------------------------------------------------------------------------
  // Story 18.1 — the two admin reversals. Same write half, one different status statement.
  // -----------------------------------------------------------------------------------------------

  private static final long REVERSAL_MILL = 790L;

  private void givenTheReversalStatusRowMoves(TrackTransition transition) {
    when(repository.updateTrackStatusWithoutIdentity(
            REVERSAL_MILL, YEAR, transition.to(), transition.from(), USER))
        .thenReturn(1);
  }

  private void givenTenCategoryRowsAdvanceTo(String categoryState) {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(REVERSAL_MILL, YEAR, categoryId, categoryState, USER))
          .thenReturn(1);
    }
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC4: status write, then the audit stamps, then the category advance")
  void reversalStatementOrderIsLoadBearing(TrackTransition transition) {
    givenTheReversalStatusRowMoves(transition);
    givenTenCategoryRowsAdvanceTo(transition.categoryState());

    assertThat(writer.writeReversal(REVERSAL_MILL, YEAR, transition, USER))
        .isEqualTo(transition.to());

    // THE ONLY GUARD against re-inverting this, and the reason Story 18.1 writes it first. The
    // delivery BEFORE-UPDATE triggers derive ILCR_*_AUD.RECORD_STATE_CODE from the
    // (CATEGORY_STATE_CODE, mill status) pair, and Set to Submit's (V,S) maps to 'A' only while the
    // category is still V (V20260910__the_audit_shadow_tables…:18-32). Advance the category first
    // and the pair seen is (A,S), a different snapshot. The IT schema carries no triggers at all,
    // so no acceptance test in this repo can see the difference.
    InOrder order = inOrder(repository);
    order
        .verify(repository)
        .updateTrackStatusWithoutIdentity(
            REVERSAL_MILL, YEAR, transition.to(), transition.from(), USER);
    order.verify(repository).touchReportSummaries(REVERSAL_MILL, YEAR, USER);
    order.verify(repository).touchRoadConstructionCostDetails(REVERSAL_MILL, YEAR, USER);
    order
        .verify(repository)
        .advanceCategoryState(
            REVERSAL_MILL, YEAR, CATEGORIES.get(0), transition.categoryState(), USER);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC3: neither reversal reaches a statement that writes an identity pair")
  void reversalsWriteNoIdentityColumn(TrackTransition transition) {
    givenTheReversalStatusRowMoves(transition);
    givenTenCategoryRowsAdvanceTo(transition.categoryState());

    writer.writeReversal(REVERSAL_MILL, YEAR, transition, USER);

    // updateTrackStatusWithAuditor is the ONLY statement naming AUDITOR_*, updateTrackStatus the
    // only one naming LICENSEE_*. Reaching neither is what "unchanged by value" means in SQL terms;
    // SetToDraftIT/SetToSubmitIT assert the columns themselves against Oracle.
    verify(repository, never())
        .updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString());
    verify(repository, never())
        .updateTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString());
    // No directory lookup either: with no identity pair to write there is no xref to resolve.
    verify(millUserXrefRepository, never()).findAssignment(anyLong(), any());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = TrackTransition.class,
      names = {"SET_TO_DRAFT", "SET_TO_SUBMIT"})
  @DisplayName("AC9: a status write matching no row is a 409 refusal, not a 500")
  void lostUpdateIsRefusedNotFailed(TrackTransition transition) {
    // The expectedCode predicate matched nothing: another request moved the track between this
    // one's unlocked read and its write. That user has done nothing wrong, so they get the
    // transition's own "no longer in …" text rather than "contact ILCR application support".
    when(repository.updateTrackStatusWithoutIdentity(
            REVERSAL_MILL, YEAR, transition.to(), transition.from(), USER))
        .thenReturn(0);

    assertThatThrownBy(() -> writer.writeReversal(REVERSAL_MILL, YEAR, transition, USER))
        .isInstanceOf(ReportTransitionRejectedException.class)
        .hasMessage(transition.rejectedKey(ScheduleTrack.SCHEDULES_1_TO_10));
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("a reversal advancing fewer than ten category rows fails rather than committing")
  void reversalShortCategoryAdvanceFails() {
    givenTheReversalStatusRowMoves(TrackTransition.SET_TO_DRAFT);
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(REVERSAL_MILL, YEAR, categoryId, "D", USER))
          .thenReturn(CATEGORIES.get(0).equals(categoryId) ? 0 : 1);
    }

    assertThatThrownBy(
            () -> writer.writeReversal(REVERSAL_MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName("a reversal persistence failure becomes a 500 and stops before the categories")
  void reversalPersistenceFailure() {
    givenTheReversalStatusRowMoves(TrackTransition.SET_TO_DRAFT);
    when(repository.touchReportSummaries(REVERSAL_MILL, YEAR, USER))
        .thenThrow(new DataIntegrityViolationException("sweep failed"));

    assertThatThrownBy(
            () -> writer.writeReversal(REVERSAL_MILL, YEAR, TrackTransition.SET_TO_DRAFT, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  // -----------------------------------------------------------------------------------------------
  // Story 26.3 — Verify on the Schedule 11 track. Same write half, dispatched by track: the
  // silviculture status statement, Schedule 11's one row family, category '11' alone.
  // -----------------------------------------------------------------------------------------------

  private static final ScheduleTrack ELEVEN = ScheduleTrack.SCHEDULE_11;
  private static final long SCH11_MILL = 807L;

  private void givenTheSilvicultureStatusRowMoves() {
    when(repository.updateSilvicultureTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
  }

  private void givenTheSchedule11AuditorIsAssigned() {
    when(millUserXrefRepository.findAssignment(SCH11_MILL, GUID))
        .thenReturn(
            Optional.of(
                new MillUserXrefEntity(SCH11_MILL, GUID, null, null, 0, null, null, null, null)));
  }

  @Test
  @DisplayName("Schedule 11: silviculture status, then both location touches, then category '11'")
  void schedule11StatementOrder() {
    givenTheSilvicultureStatusRowMoves();
    givenTheSchedule11AuditorIsAssigned();
    when(repository.advanceCategoryState(SCH11_MILL, YEAR, "11", "V", USER)).thenReturn(1);

    assertThat(writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID)).isEqualTo("V");

    // Legacy's order (SubmitReportDAO.submitReportSchedule11:161-164). S->V is trigger-indifferent,
    // but Story 26.5's V->S will reuse this dispatch and is not, so the order is pinned now.
    InOrder order = inOrder(repository);
    order
        .verify(repository)
        .updateSilvicultureTrackStatusWithAuditor(
            SCH11_MILL, YEAR, "V", "S", SCH11_MILL, GUID, USER);
    order.verify(repository).touchBasicSilvicultureReports(SCH11_MILL, YEAR, USER);
    order.verify(repository).touchBasicSilvicultureCostDetails(SCH11_MILL, YEAR, USER);
    order.verify(repository).advanceCategoryState(SCH11_MILL, YEAR, "11", "V", USER);
  }

  @Test
  @DisplayName("Schedule 11 never reaches a 1-10 statement: status, stamps or categories 1-10")
  void schedule11NeverTouchesTheOtherTrack() {
    givenTheSilvicultureStatusRowMoves();
    givenTheSchedule11AuditorIsAssigned();
    when(repository.advanceCategoryState(SCH11_MILL, YEAR, "11", "V", USER)).thenReturn(1);

    writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID);

    verify(repository, never())
        .updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString());
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), anyString());
    verify(repository, never()).touchRoadConstructionCostDetails(anyLong(), anyInt(), anyString());
    for (String categoryId : CATEGORIES) {
      verify(repository, never())
          .advanceCategoryState(anyLong(), anyInt(), eq(categoryId), anyString(), anyString());
    }
  }

  @Test
  @DisplayName("1-10 never reaches a Schedule 11 statement: status, locations or category '11'")
  void oneToTenNeverTouchesSchedule11() {
    givenTheStatusRowMoves();
    givenTenCategoryRowsAdvance();
    givenTheAuditorIsAssigned();

    writer.write(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR, "S", "V", "V", USER, GUID);

    verify(repository, never())
        .updateSilvicultureTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString());
    verify(repository, never()).touchBasicSilvicultureReports(anyLong(), anyInt(), anyString());
    verify(repository, never()).touchBasicSilvicultureCostDetails(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), eq("11"), anyString(), anyString());
  }

  @Test
  @DisplayName("Schedule 11: a silviculture status write matching no row is the generic 409")
  void schedule11LostUpdateIsRefused() {
    when(repository.updateSilvicultureTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(0);
    givenTheSchedule11AuditorIsAssigned();

    assertThatThrownBy(() -> writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionRejectedException.class)
        .hasMessage(ReportTransitionRejectedException.GENERIC_KEY);
    verify(repository, never()).touchBasicSilvicultureReports(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("Schedule 11: no category '11' row fails the transition — its one row, not ten")
  void schedule11MissingCategoryRowFails() {
    givenTheSilvicultureStatusRowMoves();
    givenTheSchedule11AuditorIsAssigned();
    when(repository.advanceCategoryState(SCH11_MILL, YEAR, "11", "V", USER)).thenReturn(0);

    assertThatThrownBy(() -> writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName("Schedule 11: an admin with no cross-reference records NULL in both auditor columns")
  void schedule11NoXrefRecordsNullAuditor() {
    when(millUserXrefRepository.findAssignment(SCH11_MILL, GUID)).thenReturn(Optional.empty());
    when(repository.updateSilvicultureTrackStatusWithAuditor(
            SCH11_MILL, YEAR, "V", "S", null, null, USER))
        .thenReturn(1);
    when(repository.advanceCategoryState(SCH11_MILL, YEAR, "11", "V", USER)).thenReturn(1);

    assertThat(writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID)).isEqualTo("V");
    verify(repository)
        .updateSilvicultureTrackStatusWithAuditor(SCH11_MILL, YEAR, "V", "S", null, null, USER);
  }

  @Test
  @DisplayName("Schedule 11: a persistence failure becomes a 500 and stops before the category")
  void schedule11PersistenceFailure() {
    givenTheSilvicultureStatusRowMoves();
    givenTheSchedule11AuditorIsAssigned();
    when(repository.touchBasicSilvicultureReports(SCH11_MILL, YEAR, USER))
        .thenThrow(new DataIntegrityViolationException("touch failed"));

    assertThatThrownBy(() -> writer.write(ELEVEN, SCH11_MILL, YEAR, "S", "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }
}
