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
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefEntity;
import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            anyLong(), anyInt(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
  }

  private void givenTenCategoryRowsAdvance() {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(MILL, YEAR, categoryId, "V", USER)).thenReturn(1);
    }
  }

  /** As above, but one category row missing — the partial-enrolment shape the guard exists for. */
  private void givenOneCategoryRowIsMissing() {
    for (String categoryId : CATEGORIES) {
      when(repository.advanceCategoryState(MILL, YEAR, categoryId, "V", USER))
          .thenReturn(CATEGORIES.get(0).equals(categoryId) ? 0 : 1);
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

    writer.write(MILL, YEAR, "V", "V", USER, GUID);

    // The ONLY guard against re-inverting this. Delivery derives ILCR_*_AUD.RECORD_STATE_CODE in a
    // BEFORE-UPDATE trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the only
    // pair that writes the 'S' snapshot Epic 16's original-value indicators read. S->V is
    // indifferent to the order, so no acceptance test can see it — the IT snapshot has no triggers
    // at all. D->S is NOT indifferent, and Story 15.3 / Epic 18 extend this component.
    InOrder order = inOrder(repository);
    order.verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", MILL, GUID, USER);
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

    writer.write(MILL, YEAR, "V", "V", USER, GUID);

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

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName("zero category rows advanced is refused too, not treated as nothing to do")
  void zeroCategoryAdvanceFailsTheTransition() {
    givenTheStatusRowMoves();
    givenOneCategoryRowIsMissing();
    givenTheAuditorIsAssigned();

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
  }

  @Test
  @DisplayName("a status write that moves no row fails before the sweep runs at all")
  void statusWriteMustMoveExactlyOneRow() {
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyString()))
        .thenReturn(0);
    givenTheAuditorIsAssigned();

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("no directory GUID records no auditor, and the transition still succeeds")
  void noDirectoryGuidRecordsNoAuditor() {
    when(repository.updateTrackStatusWithAuditor(
            eq(MILL), eq(YEAR), eq("V"), eq(null), eq(null), eq(USER)))
        .thenReturn(1);
    givenTenCategoryRowsAdvance();

    assertThat(writer.write(MILL, YEAR, "V", "V", USER, null)).isEqualTo("V");
    // The cross-reference lookup is skipped entirely rather than run with a null key.
    verify(millUserXrefRepository, never()).findAssignment(anyLong(), any());
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", null, null, USER);
  }

  @Test
  @DisplayName("an admin with no cross-reference records NULL in both auditor columns")
  void noXrefRecordsNullAuditor() {
    when(millUserXrefRepository.findAssignment(MILL, GUID)).thenReturn(Optional.empty());
    when(repository.updateTrackStatusWithAuditor(
            eq(MILL), eq(YEAR), eq("V"), eq(null), eq(null), eq(USER)))
        .thenReturn(1);
    givenTenCategoryRowsAdvance();

    assertThat(writer.write(MILL, YEAR, "V", "V", USER, GUID)).isEqualTo("V");
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", null, null, USER);
  }

  @Test
  @DisplayName("a persistence failure becomes a 500 and stops the sweep")
  void persistenceFailure() {
    givenTheStatusRowMoves();
    givenTheAuditorIsAssigned();
    when(repository.touchReportSummaries(MILL, YEAR, USER))
        .thenThrow(new DataIntegrityViolationException("sweep failed"));

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never())
        .advanceCategoryState(anyLong(), anyInt(), anyString(), anyString(), anyString());
  }
}
