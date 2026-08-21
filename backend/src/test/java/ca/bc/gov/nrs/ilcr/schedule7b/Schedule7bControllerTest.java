package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertCodeLists;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link Schedule7bController} — verifies each endpoint delegates mill/year
 * validation to {@code MillContextService}, resolves editability via {@code SchedulePermissions},
 * echoes the correct verbatim success key on a mutation (including the delete branch that switches
 * to the empty-schedule message, AD-8), and passes the audit user through on writes. The
 * authorization annotations themselves are exercised by the {@code *IT} suite; here the method
 * bodies run with the collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule7bController — delegation, editability, success-message echo")
class Schedule7bControllerTest {

  private static final String MILL_PARAM = "514";
  private static final String YEAR_PARAM = "2021";
  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;
  @Mock private Schedule7bService schedule7bService;
  @Mock private SchedulePermissions permissions;
  @Mock private MessageSource messageSource;
  @Mock private Authentication authentication;
  @InjectMocks private Schedule7bController controller;

  private static Schedule7bResponse doc(List<Culvert> culverts) {
    return new Schedule7bResponse(
        MILL, YEAR, "D", true, culverts, new CulvertCodeLists(List.of()), null);
  }

  private static Culvert oneCulvert() {
    return new Culvert(7801L, 1, "R", 1200, 900, null, 3, 4000, 1500, 5500, null, 0);
  }

  private static CulvertRequest request(Integer revision) {
    return new CulvertRequest("R", 1200, 900, null, 3, 4000, 1500, null, revision);
  }

  private void contextResolves() {
    when(millContextService.validateMillYearActive(MILL_PARAM, YEAR_PARAM))
        .thenReturn(new MillYearContext(MILL, YEAR));
  }

  /**
   * Resolve any bundle key to its own key text, so the assertions pin the KEY the controller chose.
   */
  private void echoKeys() {
    when(messageSource.getMessage(anyString(), any(), anyString(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("GET delegates context validation and derives editability from EDIT_SCHEDULE")
  void getDelegatesAndDerivesEditability() {
    contextResolves();
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule7bService.getSchedule7b(MILL, YEAR, true)).thenReturn(doc(List.of(oneCulvert())));

    ResponseEntity<Schedule7bResponse> response =
        controller.getSchedule7b(MILL_PARAM, YEAR_PARAM, authentication);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().culverts()).hasSize(1);
    assertThat(response.getBody().message()).isNull();
    verify(millContextService).validateMillYearActive(MILL_PARAM, YEAR_PARAM);
  }

  @Test
  @DisplayName("GET passes callerMayEdit=false when the caller lacks EDIT_SCHEDULE")
  void getPassesReadOnlyAuthority() {
    contextResolves();
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule7bService.getSchedule7b(MILL, YEAR, false)).thenReturn(doc(List.of()));

    controller.getSchedule7b(MILL_PARAM, YEAR_PARAM, authentication);

    verify(schedule7bService).getSchedule7b(MILL, YEAR, false);
  }

  @Test
  @DisplayName("POST echoes SUC-001 and passes the audit user through")
  void addEchoesSavedAndPassesUser() {
    contextResolves();
    echoKeys();
    when(authentication.getName()).thenReturn("tester");
    CulvertRequest request = request(null);
    when(schedule7bService.addCulvert(MILL, YEAR, request, true, "tester"))
        .thenReturn(doc(List.of(oneCulvert())));

    ResponseEntity<Schedule7bResponse> response =
        controller.addCulvert(MILL_PARAM, YEAR_PARAM, request, authentication);

    assertThat(response.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
    verify(schedule7bService).addCulvert(MILL, YEAR, request, true, "tester");
  }

  @Test
  @DisplayName("PUT /{id} echoes SUC-001 for the addressed culvert")
  void updateEchoesSaved() {
    contextResolves();
    echoKeys();
    when(authentication.getName()).thenReturn("tester");
    CulvertRequest request = request(0);
    when(schedule7bService.updateCulvert(MILL, YEAR, 7801L, request, true, "tester"))
        .thenReturn(doc(List.of(oneCulvert())));

    ResponseEntity<Schedule7bResponse> response =
        controller.updateCulvert(7801L, MILL_PARAM, YEAR_PARAM, request, authentication);

    assertThat(response.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("PUT (save all) echoes SUC-001 for the whole batch")
  void saveAllEchoesSaved() {
    contextResolves();
    echoKeys();
    when(authentication.getName()).thenReturn("tester");
    CulvertSaveAllRequest batch =
        new CulvertSaveAllRequest(List.of(new CulvertSaveAllRequest.Item(7801L, request(0))));
    when(schedule7bService.saveAllCulverts(MILL, YEAR, batch, true, "tester"))
        .thenReturn(doc(List.of(oneCulvert())));

    ResponseEntity<Schedule7bResponse> response =
        controller.saveAllCulverts(MILL_PARAM, YEAR_PARAM, batch, authentication);

    assertThat(response.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("DELETE echoes SUC-002 while culverts remain")
  void deleteEchoesDeletedWhenCulvertsRemain() {
    contextResolves();
    echoKeys();
    when(schedule7bService.deleteCulvert(MILL, YEAR, 7801L, true))
        .thenReturn(doc(List.of(oneCulvert())));

    ResponseEntity<Schedule7bResponse> response =
        controller.deleteCulvert(7801L, MILL_PARAM, YEAR_PARAM, authentication);

    assertThat(response.getBody().message().key()).isEqualTo("dataDeletedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("DELETE still echoes SUC-002 when the last culvert goes — no 7A empty-list branch")
  void deleteEchoesDeletedEvenWhenLastCulvertRemoved() {
    contextResolves();
    echoKeys();
    when(schedule7bService.deleteCulvert(MILL, YEAR, 7801L, true)).thenReturn(doc(List.of()));

    ResponseEntity<Schedule7bResponse> response =
        controller.deleteCulvert(7801L, MILL_PARAM, YEAR_PARAM, authentication);

    // Legacy Schedule7bMB.update() always emits the key it was passed; the empty-list swap to
    // anyDataToSaveInfoMsg exists ONLY in Schedule7aMB.java:374 and that string appears nowhere
    // else
    // in the legacy source. Emitting it here would tell a reporter data "was saved" on a delete.
    assertThat(response.getBody().message().key()).isEqualTo("dataDeletedSuccesfullyInfoMsg");
    assertThat(response.getBody().culverts()).isEmpty();
  }

  @Test
  @DisplayName("Check Status delegates context validation and returns the service result unchanged")
  void checkStatusDelegates() {
    contextResolves();
    Schedule7bCheckStatusResponse result = new Schedule7bCheckStatusResponse(true, List.of(), null);
    when(schedule7bService.checkStatus(MILL, YEAR)).thenReturn(result);

    ResponseEntity<Schedule7bCheckStatusResponse> response =
        controller.checkStatus(MILL_PARAM, YEAR_PARAM, authentication);

    assertThat(response.getBody()).isSameAs(result);
    verify(millContextService).validateMillYearActive(MILL_PARAM, YEAR_PARAM);
  }
}
