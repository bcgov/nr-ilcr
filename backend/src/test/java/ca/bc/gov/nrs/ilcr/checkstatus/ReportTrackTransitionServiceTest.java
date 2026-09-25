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
  private static final ScheduleTrack ONE_TO_TEN = ScheduleTrack.SCHEDULES_1_TO_10;
  private static final ScheduleTrack ELEVEN = ScheduleTrack.SCHEDULE_11;

  @Mock private MillContextService millContextService;
  @Mock private CheckStatusSweepService sweepService;
  @Mock private ReportSubmission reportSubmission;
  @Mock private MillUserXrefRepository millUserXrefRepository;
  @Mock private ReportTrackTransitionRepository repository;

  private ReportTrackTransitionService service;

  private Authentication submitter;

  /**
   * One fixture method, not two: JUnit 5 does not order {@code @BeforeEach} methods across a class,
   * so anything a second one set up would be sequenced only by luck (and Sonar rule S8745 says so).
   *
   * <p>The writer is REAL, over the same mocked repository. Submit's audit sweep lives in {@link
   * ReportTransitionWriter#stampAuditColumns} (one copy, shared with verify), so a mocked writer
   * would silently retire every touch assertion below — the statement-order test, the deadlock arm,
   * and the two never()-touched arms. Its {@code @Transactional} is inert here: nothing proxies it
   * in a plain unit test, and in the application the method carries none of its own and joins
   * submit's transaction.
   */
  @BeforeEach
  void wireServiceAndPrincipal() {
    service =
        new ReportTrackTransitionService(
            millContextService,
            sweepService,
            reportSubmission,
            millUserXrefRepository,
            repository,
            new ReportTransitionWriter(repository, millUserXrefRepository));
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

  /**
   * All eleven 1-10 verdicts, 7A carrying {@code met}. Eleven since Story 26.1, which made submit
   * require the count as verify and the reversals already did — three stand-ins would now be
   * refused as a short list, which is exactly the guard's job.
   */
  private void gate(boolean met) {
    List<ScheduleCheckResult> verdicts =
        List.of("1", "2", "3", "4", "5", "6", "7A", "7B", "8", "9", "10").stream()
            .map(id -> new ScheduleCheckResult(id, !"7A".equals(id) || met, Map.of()))
            .toList();
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

    String key = service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER);

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

    service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER);

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

    service.submit(ONE_TO_TEN, MILL, YEAR, real, "IDIR\\JSMITH");

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

    service.submit(ONE_TO_TEN, MILL, YEAR, opaque, "someone");

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

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg");
            });
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("AC 3: a track not at Draft -> 409 submitNotDraftErrorMsg BEFORE the gate runs")
  void notDraft_409_neverRunsTheGate() {
    trackAt("S");

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("submitNotDraftErrorMsg");
            });
    verifyNoInteractions(sweepService, repository, millUserXrefRepository);
  }

  @Test
  @DisplayName(
      "a row with a NULL 1-10 code is a 409, not a 404 — with legacy's generic text, since the"
          + " track was never in Draft (26.1 review 1a); nothing checked or written")
  void nullCode_409() {
    trackAt(null);

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(sweepService, repository, reportSubmission);
  }

  @Test
  @DisplayName("26.1 review 1a: a NULL silviculture code is the same generic 409 on Schedule 11")
  void schedule11_nullCode_409_genericText() {
    codes("D", null);

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(sweepService, repository, reportSubmission);
  }

  @Test
  @DisplayName("no status row -> the shared guard's not-found, which the controller re-keys")
  void noStatusRow_scheduleNotFound() {
    when(millContextService.lockTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
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

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportSubmissionException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
            });
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("AC 7: a validation-read DataAccessException uses reportSubmissionErrorMsg")
  void validationReadFailure_500() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    when(sweepService.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR))
        .thenThrow(new DataAccessResourceFailureException("validation read failed"));

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportSubmissionException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("AC 7: a licensee-read DataAccessException uses reportSubmissionErrorMsg")
  void licenseeReadFailure_500() {
    trackAt("D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    when(millUserXrefRepository.findAssignment(MILL, GUID))
        .thenThrow(new DataAccessResourceFailureException("assignment read failed"));

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportSubmissionException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg"));
    verifyNoInteractions(repository);
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

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
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

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).touchReportSummaries(anyLong(), anyInt(), any());
  }

  // --- Schedule 11 (Story 26.1): the same method, the other track --------------------------------

  private void codes(String schedules1To10, String schedule11) {
    when(millContextService.lockTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes(schedules1To10, schedule11)));
  }

  private void gate11(List<ScheduleCheckResult> verdicts) {
    when(sweepService.checkTrack(ELEVEN, MILL, YEAR)).thenReturn(verdicts);
  }

  private static List<ScheduleCheckResult> verdict11(boolean met) {
    return List.of(new ScheduleCheckResult("11", met, Map.of()));
  }

  private void schedule11WritesSucceed() {
    when(repository.updateSilvicultureTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryState(MILL, YEAR, "11", "A", USER)).thenReturn(1);
  }

  @Test
  @DisplayName(
      "26.1 AC 1: lock, Schedule 11 gate, silviculture S, BSR rows, their cost details, category"
          + " '11' A — in that order, and nothing of 1-10")
  void schedule11_happyPath_statementOrder() {
    // 1-10 at S and Schedule 11 at D: the codes differ, so reading the wrong one would refuse.
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(verdict11(true));
    assigned();
    schedule11WritesSucceed();

    String key = service.submit(ELEVEN, MILL, YEAR, submitter, USER);

    assertThat(key).isEqualTo("sch11SubmittedMsg");
    InOrder order = inOrder(millContextService, sweepService, repository);
    order.verify(millContextService).lockTrackStatusCodes(MILL, YEAR);
    order.verify(sweepService).checkTrack(ELEVEN, MILL, YEAR);
    // D1: the LICENSEE pair is written on the Schedule 11 submit, as legacy's shared helper did.
    order.verify(repository).updateSilvicultureTrackStatus(MILL, YEAR, "D", "S", MILL, GUID, USER);
    // Status first, stamps second, category last — the only order in which the delivery trigger
    // writes the 'S' snapshot BASIC_SILVICULTURE_REPORT_S_VW reads. The test schema has no
    // triggers, so this is the only guard on a re-inversion.
    order.verify(repository).touchBasicSilvicultureReports(MILL, YEAR, USER);
    order.verify(repository).touchBasicSilvicultureCostDetails(MILL, YEAR, USER);
    order.verify(repository).advanceCategoryState(MILL, YEAR, "11", "A", USER);
    // BR-06: the 1-10 gate, status column, row families and categories are never named.
    verify(sweepService, never()).checkTrack(ONE_TO_TEN, MILL, YEAR);
    verify(repository, never())
        .updateTrackStatus(anyLong(), anyInt(), any(), any(), any(), any(), any());
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("26.1 AC 8: the 1-10 submit never names a Schedule 11 statement (the mirror)")
  void oneToTen_neverTouchesSchedule11() {
    codes("D", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate(true);
    assigned();
    writesSucceed();

    service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER);

    verify(sweepService, never()).checkTrack(ELEVEN, MILL, YEAR);
    verify(repository, never())
        .updateSilvicultureTrackStatus(anyLong(), anyInt(), any(), any(), any(), any(), any());
    verify(repository, never()).touchBasicSilvicultureReports(anyLong(), anyInt(), any());
    verify(repository, never()).touchBasicSilvicultureCostDetails(anyLong(), anyInt(), any());
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), eq("11"), any(), any());
  }

  @Test
  @DisplayName("26.1 AC 5: Schedule 11 not at Draft -> 409 in Schedule 11's words, before the gate")
  void schedule11_notDraft_409_neverRunsTheGate() {
    // 1-10 at D, Schedule 11 at S: a service that read the 1-10 code would proceed.
    codes("D", "S");

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("sch11SubmitNotDraftErrorMsg");
            });
    verifyNoInteractions(sweepService, repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("26.1 AC 6: Submit not offered at the Schedule 11 code -> 409, nothing run")
  void schedule11_notOffered_409() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(false);

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportTransitionRejectedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("sch11SubmitNotDraftErrorMsg"));
    verifyNoInteractions(sweepService, repository);
  }

  @Test
  @DisplayName("26.1 AC 2: Schedule 11 gate fails -> 409 reportNotSubmittedErrorMsg, no write")
  void schedule11_gateFails_409_writesNothing() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(verdict11(false));

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg");
            });
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("26.1 trap 4: an EMPTY Schedule 11 verdict list is refused, not rolled up as met")
  void schedule11_noVerdict_409() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(List.of());

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(repository, millUserXrefRepository);
  }

  @Test
  @DisplayName("26.1 trap 4: two Schedule 11 verdicts (a mis-tracked adapter) are refused too")
  void schedule11_twoVerdicts_409() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(
        List.of(
            new ScheduleCheckResult("11", true, Map.of()),
            new ScheduleCheckResult("10", true, Map.of())));

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportNotSubmittedException.class);
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("the 1-10 submit also refuses a short verdict list rather than submitting it")
  void oneToTen_shortVerdictList_409() {
    codes("D", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    when(sweepService.checkTrack(ONE_TO_TEN, MILL, YEAR))
        .thenReturn(List.of(new ScheduleCheckResult("1", true, Map.of())));

    assertThatThrownBy(() -> service.submit(ONE_TO_TEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportNotSubmittedException.class,
            ex -> assertThat(ex.getMessageKey()).isEqualTo("reportNotSubmittedErrorMsg"));
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("26.1 AC 5: no category '11' row -> 500 reportSubmissionErrorMsg")
  void schedule11_missingCategoryRow_500() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(verdict11(true));
    assigned();
    when(repository.updateSilvicultureTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.advanceCategoryState(MILL, YEAR, "11", "A", USER)).thenReturn(0);

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOfSatisfying(
            ReportSubmissionException.class,
            ex -> {
              assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(ex.getMessageKey()).isEqualTo("reportSubmissionErrorMsg");
            });
  }

  @Test
  @DisplayName("26.1 AC 5: a silviculture status UPDATE affecting no row -> 500, nothing stamped")
  void schedule11_statusRowMoved_500() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(verdict11(true));
    assigned();
    when(repository.updateSilvicultureTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(0);

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).touchBasicSilvicultureReports(anyLong(), anyInt(), any());
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("26.1 AC 5: a DataAccessException stamping Schedule 11 -> 500, category untouched")
  void schedule11_persistenceFailure_500() {
    codes("S", "D");
    when(reportSubmission.canSubmit(submitter, "D")).thenReturn(true);
    gate11(verdict11(true));
    assigned();
    when(repository.updateSilvicultureTrackStatus(
            anyLong(), anyInt(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(1);
    when(repository.touchBasicSilvicultureCostDetails(MILL, YEAR, USER))
        .thenThrow(new DataAccessResourceFailureException("ORA-00060: deadlock detected"));

    assertThatThrownBy(() -> service.submit(ELEVEN, MILL, YEAR, submitter, USER))
        .isInstanceOf(ReportSubmissionException.class);
    verify(repository, never()).advanceCategoryState(anyLong(), anyInt(), any(), any(), any());
  }

  @Test
  @DisplayName("there is no default track: a null track is refused before anything is read")
  void nullTrack_isRefused() {
    assertThatThrownBy(() -> service.submit(null, MILL, YEAR, submitter, USER))
        .isInstanceOf(NullPointerException.class);
    verifyNoInteractions(millContextService, sweepService, repository);
  }
}
