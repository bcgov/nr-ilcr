package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeCodeLists;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
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
 * Unit tests for {@link Schedule7aController} — verifies each endpoint delegates mill/year
 * validation to {@code MillContextService}, resolves editability via {@code SchedulePermissions},
 * echoes the correct verbatim success key on a mutation (the SUC-002-vs-SUC-003 delete branch,
 * AD-8), and passes the audit user through on writes. The authorization annotations themselves are
 * exercised by the {@code *IT} suite; here the method bodies run with the collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule7aController — delegation, editability, success-message echo")
class Schedule7aControllerTest {

  @Mock private MillContextService millContextService;
  @Mock private Schedule7aService schedule7aService;
  @Mock private SchedulePermissions permissions;
  @Mock private MessageSource messageSource;
  @Mock private Authentication authentication;
  @InjectMocks private Schedule7aController controller;

  private static Schedule7aResponse doc(List<Bridge> bridges) {
    return new Schedule7aResponse(
        514L,
        2021,
        "D",
        true,
        bridges,
        new BridgeCodeLists(List.of(), List.of(), List.of(), List.of(), List.of()),
        null);
  }

  private static Bridge oneBridge() {
    return new Bridge(
        1L,
        1,
        "North Fork",
        "2020-06",
        "N",
        "STL",
        "WD",
        "CONC",
        "L100",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0);
  }

  private static BridgeRequest anyRequest() {
    return new BridgeRequest(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("GET validates context, resolves editability, and returns the served document")
  void get_delegatesAndResolvesEditability() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    Schedule7aResponse served = doc(List.of());
    when(schedule7aService.getSchedule7a(514L, 2021, true)).thenReturn(served);

    ResponseEntity<Schedule7aResponse> result =
        controller.getSchedule7a("514", "2021", authentication);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    assertThat(result.getBody()).isSameAs(served);
  }

  @Test
  @DisplayName("POST adds the bridge, passes the audit user, and echoes SUC-001")
  void add_echoesSaveMessageAndAuditUser() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));
    when(authentication.getName()).thenReturn("submitter");
    BridgeRequest request = anyRequest();
    when(schedule7aService.addBridge(514L, 2021, request, true, "submitter"))
        .thenReturn(doc(List.of()));

    ResponseEntity<Schedule7aResponse> result =
        controller.addBridge("514", "2021", request, authentication);

    assertThat(result.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("PUT corrects the bridge, passes the audit user, and echoes SUC-001")
  void update_echoesSaveMessageAndAuditUser() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));
    when(authentication.getName()).thenReturn("submitter");
    BridgeRequest request = anyRequest();
    when(schedule7aService.updateBridge(514L, 2021, 7601L, request, true, "submitter"))
        .thenReturn(doc(List.of()));

    ResponseEntity<Schedule7aResponse> result =
        controller.updateBridge(7601L, "514", "2021", request, authentication);

    assertThat(result.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("PUT /bridges saves every bridge in one call and echoes SUC-001")
  void saveAll_echoesSaveMessageAndAuditUser() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));
    when(authentication.getName()).thenReturn("submitter");
    BridgeSaveAllRequest request =
        new BridgeSaveAllRequest(
            List.of(
                new BridgeSaveAllRequest.Item(7601L, anyRequest()),
                new BridgeSaveAllRequest.Item(7602L, anyRequest())));
    when(schedule7aService.saveAllBridges(514L, 2021, request, true, "submitter"))
        .thenReturn(doc(List.of(oneBridge())));

    ResponseEntity<Schedule7aResponse> result =
        controller.saveAllBridges("514", "2021", request, authentication);

    assertThat(result.getBody().message().key()).isEqualTo("dataSavedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("DELETE echoes SUC-003 (empty) when the last bridge is removed, SUC-002 otherwise")
  void delete_echoesEmptyVsDeletedByRemainingCount() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));

    when(schedule7aService.deleteBridge(514L, 2021, 7601L, true)).thenReturn(doc(List.of()));
    ResponseEntity<Schedule7aResponse> emptied =
        controller.deleteBridge(7601L, "514", "2021", authentication);
    assertThat(emptied.getBody().message().key()).isEqualTo("anyDataToSaveInfoMsg");

    when(schedule7aService.deleteBridge(514L, 2021, 7602L, true))
        .thenReturn(doc(List.of(oneBridge())));
    ResponseEntity<Schedule7aResponse> remaining =
        controller.deleteBridge(7602L, "514", "2021", authentication);
    assertThat(remaining.getBody().message().key()).isEqualTo("dataDeletedSuccesfullyInfoMsg");
  }

  @Test
  @DisplayName("POST /check-status validates context and returns the readiness result unchanged")
  void checkStatus_delegates() {
    when(millContextService.validateMillYearActive("514", "2021"))
        .thenReturn(new MillYearContext(514L, 2021));
    Schedule7aCheckStatusResponse readiness =
        new Schedule7aCheckStatusResponse(true, List.of(), List.of(), null);
    when(schedule7aService.checkStatus(514L, 2021)).thenReturn(readiness);

    ResponseEntity<Schedule7aCheckStatusResponse> result =
        controller.checkStatus("514", "2021", authentication);

    assertThat(result.getBody()).isSameAs(readiness);
    verify(schedule7aService).checkStatus(514L, 2021);
  }
}
