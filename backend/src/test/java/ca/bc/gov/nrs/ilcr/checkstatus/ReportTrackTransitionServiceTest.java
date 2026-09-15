package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefEntity;
import ca.bc.gov.nrs.ilcr.assignment.MillUserXrefRepository;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test for {@link ReportTrackTransitionService} — the ONE ordering an IT cannot prove (Story
 * 15.3 AC 4: the test schema has no audit triggers, so only the statement ORDER pins the {@code S}
 * snapshot Story 16.2 reads), plus the two 409 refusals writing nothing, the licensee resolution,
 * and the 500 mapping (AC 2/3/7). Mocked collaborators, no Spring, no database.
 */
@ExtendWith(MockitoExtension.class)
class ReportTrackTransitionServiceTest {

  private static final long MILL = 760L;
  private static final int YEAR = 2021;
  private static final String USER = "dev-submitter";
  private static final String GUID = "CANONSUBMITTERBBBBCCCCDDDD000001";

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportSubmission reportSubmission;
  @Mock private MillUserXrefRepository millUserXrefRepository;
  @Mock private ReportTrackTransitionRepository repository;

  @InjectMocks private ReportTrackTransitionService service;

  private Authentication submitter;

  @BeforeEach
  void principal() {
    submitter =
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal(USER, GUID),
            "N/A",
            List.of(new SimpleGrantedAuthority("SUBMITTER")));
  }

  private void trackAt(String code) {
    when(millContextService.lockTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(code, "D")));
  }

  private void gate(boolean met) {
    List<ScheduleCheckResult> verdicts =
        List.of(
            new ScheduleCheckResult("1", true, Map.of()),
            new ScheduleCheckResult("7A", met, Map.of()),
            new ScheduleCheckResult("10", true, Map.of()));
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR)).thenReturn(verdicts);
  }

  private void writesSucceed() {
    when(repository.updateTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryState(
            anyLong(), anyInt(), anyString(), anyString(), anyString()))
        .thenReturn(1);
  }

  private void assigned() {
    when(millUserXrefRepository.findAssignment(MILL, GUID))
        .thenReturn(
            Optional.of(
                new MillUserXrefEntity(
                    MILL, GUID, LocalDateTime.now(), null, 0, "canon", null, "canon", null)));
  }

  @Test
  @DisplayName(
      "AC 4: lock, gate, status S, every touch, then category A for '1'..'10' — in that order")
  void happyPath_statementOrder() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    assigned();
    writesSucceed();

    String key = service.submit(MILL, YEAR, submitter, USER);

    assertThat(key).isEqualTo("sch1-10SubmittedMsg");
    InOrder order = inOrder(millContextService, sweepService, repository);
    order.verify(millContextService).lockTrackStatusCodes(MILL, YEAR);
    order.verify(sweepService).checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR);
    order.verify(repository).updateTrackStatus(MILL, YEAR, "D", "S", MILL, GUID, USER);
    // The thirteen row families in legacy tab order; the S snapshot needs every one of them touched
    // AFTER the status is S and BEFORE its category is A.
    order.verify(repository).touchReportSummaries(MILL, YEAR, USER);
    order.verify(repository).touchReportSummaryCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchTransportationReports(MILL, YEAR, USER);
    order.verify(repository).touchTransportationCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchCamps(MILL, YEAR, USER);
    order.verify(repository).touchCampCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchRoadMaintenanceReports(MILL, YEAR, USER);
    order.verify(repository).touchRoadMaintenanceCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchBridges(MILL, YEAR, USER);
    order.verify(repository).touchBridgeCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchCulverts(MILL, YEAR, USER);
    order.verify(repository).touchCulvertCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchTreeToTruckReports(MILL, YEAR, USER);
    order.verify(repository).touchTreeToTruckDetailReports(MILL, YEAR, USER);
    order.verify(repository).touchTreeToTruckRateDetails(MILL, YEAR, USER);
    order.verify(repository).touchContractualWorkReports(MILL, YEAR, USER);
    order.verify(repository).touchContractualWorkCostDetails(MILL, YEAR, USER);
    order.verify(repository).touchRoadConstructionReports(MILL, YEAR, USER);
    order.verify(repository).touchRoadConstructionDetails(MILL, YEAR, USER);
    order.verify(repository).touchRoadConstructionCostDetails(MILL, YEAR, USER);
    for (String category : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")) {
      order.verify(repository).advanceCategoryState(MILL, YEAR, category, "A", USER);
    }
    // Never category '11', never a second status write.
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), eq("11"), any(), any());
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("BR-05: a caller with no xref row for the mill records NULL licensee columns")
  void happyPath_noAssignment_writesNullLicensee() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    when(millUserXrefRepository.findAssignment(MILL, GUID)).thenReturn(Optional.empty());
    writesSucceed();

    service.submit(MILL, YEAR, submitter, USER);

    verify(repository).updateTrackStatus(MILL, YEAR, "D", "S", null, null, USER);
  }

  @Test
  @DisplayName("the licensee GUID comes from a real JWT's custom:idp_user_id claim")
  void happyPath_jwtPrincipal_resolvesGuidFromClaim() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("custom:idp_user_id", GUID)
            .claim("cognito:groups", List.of("ILCR_SUBMITTER"))
            .build();
    Authentication real =
        new UsernamePasswordAuthenticationToken(
            jwt, "N/A", List.of(new SimpleGrantedAuthority("SUBMITTER")));
    trackAt("D");
    when(reportSubmission.canSubmit(real, "D")).thenReturn(true);
    gate(true);
    assigned();
    writesSucceed();

    service.submit(MILL, YEAR, real, "IDIR\\JSMITH");

    verify(millUserXrefRepository).findAssignment(MILL, GUID);
    verify(repository).updateTrackStatus(MILL, YEAR, "D", "S", MILL, GUID, "IDIR\\JSMITH");
  }

  @Test
  @DisplayName("a principal that is neither a JWT nor the mock never reaches the xref lookup")
  void happyPath_opaquePrincipal_noXrefLookup() {
    Authentication opaque =
        new UsernamePasswordAuthenticationToken(
            "someone", "N/A", List.of(new SimpleGrantedAuthority("SUBMITTER")));
    trackAt("D");
    when(reportSubmission.canSubmit(opaque, "D")).thenReturn(true);
    gate(true);
    writesSucceed();

    service.submit(MILL, YEAR, opaque, "someone");

    verifyNoInteractions(millUserXrefRepository);
    verify(repository)
        .updateTrackStatus(eq(MILL), eq(YEAR), eq("D"), eq("S"), isNull(), isNull(), eq("someone"));
  }

  @Test
  @DisplayName("AC 2: one failing check -> 409 reportNotSubmittedErrorMsg, no write of any kind")
  void gateFails_409_writesNothing() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(false);

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg");
            });
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("AC 3: a track not at Draft -> 409 reportSubmissionErrorMsg BEFORE the gate runs")
  void notDraft_409_neverRunsTheGate() {
    trackAt("S");
    when(reportSubmission.canSubmit(submitter, "S")).thenReturn(false);

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
            });
    verifyNoInteractions(sweepService, repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("a row with a NULL 1-10 code is a 409, not a 404 — the row exists")
  void nullCode_409() {
    trackAt(null);
    when(reportSubmission.canSubmit(submitter, null)).thenReturn(false);

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportTransitionRejectedException.class);
  }

  @Test
  @DisplayName("no status row -> the shared guard's not-found, which the controller re-keys")
  void noStatusRow_scheduleNotFound() {
    when(millContextService.lockTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOf(ScheduleNotFoundException.class);
    verifyNoInteractions(sweepService, repository);
  }

  @Test
  @DisplayName("AC 7: a DataAccessException after the guard -> 500 reportSubmissionErrorMsg")
  void persistenceFailure_500() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    assigned();
    when(repository.updateTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.touchCamps(MILL, YEAR, USER))
        .thenThrow(new DataAccessResourceFailureException("ORA-00060: deadlock detected"));

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportSubmissionException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
            });
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("D6: a category UPDATE affecting no row -> 500, the remaining categories untouched")
  void missingCategoryRow_500() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    assigned();
    when(repository.updateTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryState(
            anyLong(), anyInt(), anyString(), anyString(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryState(MILL, YEAR, "7", "A", USER)).thenReturn(0);

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).advanceCategoryState(MILL, YEAR, "8", "A", USER);
  }

  @Test
  @DisplayName("a status UPDATE affecting no row (the row moved under us) -> 500, no touch")
  void statusRowMoved_500() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    assigned();
    when(repository.updateTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(0);

    assertThatThrownBy(() -> service.submit(MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), any());
  }
}
