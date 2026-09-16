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

import ca.bc.gov.nrs.ilcr.exception.ReportTransitionFailedException;
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

  private static final long MILL = 757L;
  private static final int YEAR = 2021;
  private static final String USER = "verifyadmin";
  private static final String GUID = "VERIFYADMIN0000111122223333AAAA1";

  @Mock private ReportTransitionRepository repository;
  @InjectMocks private ReportTransitionWriter writer;

  private void givenTheStatusRowMoves() {
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
  }

  private void givenTenCategoryRowsAdvance() {
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(10);
  }

  @Test
  @DisplayName("the audit stamps run BEFORE the category advance — legacy's order")
  void stampsPrecedeTheCategoryAdvance() {
    givenTheStatusRowMoves();
    givenTenCategoryRowsAdvance();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));

    writer.write(MILL, YEAR, "V", "V", USER, GUID);

    // The ONLY guard against re-inverting this. Delivery derives ILCR_*_AUD.RECORD_STATE_CODE in a
    // BEFORE-UPDATE trigger from the (CATEGORY_STATE_CODE, mill status) pair, and (D,S) is the only
    // pair that writes the 'S' snapshot Epic 16's original-value indicators read. S->V is
    // indifferent to the order, so no acceptance test can see it — the IT snapshot has no triggers
    // at all. D->S is NOT indifferent, and Story 15.3 / Epic 18 extend this component.
    InOrder order = inOrder(repository);
    order.verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", MILL, GUID, USER);
    order.verify(repository).stampReportSummaries(MILL, YEAR, USER);
    order.verify(repository).stampRoadConstructionCostDetails(MILL, YEAR, USER);
    order.verify(repository).advanceCategoryStates(MILL, YEAR, "V", USER);
  }

  @Test
  @DisplayName("AC3: all twenty audit statements — thirteen distinct tables — are invoked")
  void invokesEveryAuditSweep() {
    givenTheStatusRowMoves();
    givenTenCategoryRowsAdvance();
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));

    writer.write(MILL, YEAR, "V", "V", USER, GUID);

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
  @DisplayName("fewer than ten category rows advanced fails the transition rather than committing")
  void shortCategoryAdvanceFailsTheTransition() {
    givenTheStatusRowMoves();
    // A mill/year enrolled by an interrupted year-open really does carry fewer than eleven category
    // rows — ReportingYearService models it as EnrolmentState.PARTIAL and mill activation refuses
    // it. Legacy failed loudly; a set-based UPDATE skips the missing rows silently, so without this
    // the endpoint answered 200 on a half-transitioned report. No fixture reaches the shape.
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(8);
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
  }

  @Test
  @DisplayName("zero category rows advanced is refused too, not treated as nothing to do")
  void zeroCategoryAdvanceFailsTheTransition() {
    givenTheStatusRowMoves();
    when(repository.advanceCategoryStates(MILL, YEAR, "V", USER)).thenReturn(0);
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
  }

  @Test
  @DisplayName("a status write that moves no row fails before the sweep runs at all")
  void statusWriteMustMoveExactlyOneRow() {
    when(repository.updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyString()))
        .thenReturn(0);
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
    verify(repository, never()).stampReportSummaries(anyLong(), anyInt(), anyString());
    verify(repository, never())
        .advanceCategoryStates(anyLong(), anyInt(), anyString(), anyString());
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
    verify(repository, never()).findUserXrefMillId(anyLong(), any());
    verify(repository).updateTrackStatusWithAuditor(MILL, YEAR, "V", null, null, USER);
  }

  @Test
  @DisplayName("an admin with no cross-reference records NULL in both auditor columns")
  void noXrefRecordsNullAuditor() {
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.empty());
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
    when(repository.findUserXrefMillId(MILL, GUID)).thenReturn(Optional.of(MILL));
    when(repository.stampReportSummaries(MILL, YEAR, USER))
        .thenThrow(new DataIntegrityViolationException("sweep failed"));

    assertThatThrownBy(() -> writer.write(MILL, YEAR, "V", "V", USER, GUID))
        .isInstanceOf(ReportTransitionFailedException.class);
    verify(repository, never())
        .advanceCategoryStates(anyLong(), anyInt(), anyString(), anyString());
  }
}
