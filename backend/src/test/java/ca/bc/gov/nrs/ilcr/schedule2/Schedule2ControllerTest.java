package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit test for {@link Schedule2Controller}. Verifies the no-summary-required context guard ({@code
 * validateMillYearActive}, the Schedule 2 divergence from Schedule 1), service delegation, the
 * server-derived {@code editable} flag, verbatim success-message decoration, and the check-status
 * field-label prefixing — collaborators mocked, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class Schedule2ControllerTest {

  private static final long MILL_ID = 514L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;

  @Mock private Schedule2Service schedule2Service;

  @Mock private SchedulePermissions permissions;

  @Mock private MessageSource messageSource;

  @Mock private Authentication authentication;

  private Schedule2Controller controller;

  /**
   * Built by hand rather than {@code @InjectMocks} since Story 15.0: the check-status composition
   * moved to {@link Schedule2CheckStatusResolver}, and a MOCK resolver would make the two
   * check-status assertions below vacuous. Wiring the real one keeps them proving the actual
   * label-prefix bytes, through the same path the endpoint takes.
   */
  @BeforeEach
  void setUp() {
    controller =
        new Schedule2Controller(
            millContextService,
            schedule2Service,
            permissions,
            messageSource,
            new Schedule2CheckStatusResolver(schedule2Service, messageSource));
  }

  @Test
  void getSchedule2_validatesContext_derivesEditFlag_andReturnsDocument() {
    Schedule2Response doc = mock(Schedule2Response.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule2Service.getSchedule2(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<Schedule2Response> response =
        controller.getSchedule2(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    // Schedule 2 uses the no-summary-required guard, NOT validateScheduleViewable (AC4/AC6).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void saveSchedule2_delegates_andAppliesSavedMessage() {
    Schedule2Request request = mock(Schedule2Request.class);
    Schedule2Response saved = mock(Schedule2Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule2Service.saveSchedule2(MILL_ID, YEAR, request, true, "dev-admin"))
        .thenReturn(saved);
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully");

    ResponseEntity<Schedule2Response> response =
        controller.saveSchedule2(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteSchedule2_removedARow_returnsDeletedMessage() {
    when(schedule2Service.deleteSchedule2(MILL_ID, YEAR)).thenReturn(true);
    when(messageSource.getMessage(
            eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully");

    ResponseEntity<MessageResponse> response =
        controller.deleteSchedule2(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("dataDeletedSuccesfullyInfoMsg", response.getBody().message().key());
    assertEquals("Data deleted successfully", response.getBody().message().text());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(schedule2Service).deleteSchedule2(MILL_ID, YEAR);
  }

  /**
   * The idempotent no-op (Draft mill/year with no Schedule 2) stays 200 but must NOT reuse the
   * success text of a delete that removed something — the whole point of defect #292's backend
   * ruling. Without this the API tells every client, UI or otherwise, that it deleted a record that
   * never existed.
   */
  @Test
  void deleteSchedule2_removedNothing_returnsNoDataToDeleteMessage() {
    when(schedule2Service.deleteSchedule2(MILL_ID, YEAR)).thenReturn(false);
    when(messageSource.getMessage(eq("noDataToDeleteInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("No saved data was found, so nothing was deleted");

    ResponseEntity<MessageResponse> response =
        controller.deleteSchedule2(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("noDataToDeleteInfoMsg", response.getBody().message().key());
    assertEquals(
        "No saved data was found, so nothing was deleted", response.getBody().message().text());
  }

  @Test
  void checkStatus_metOutcome_resolvesBareMessage_noLabelPrefix() {
    Schedule2CheckStatusResponse serviceResult =
        new Schedule2CheckStatusResponse(
            "MET", List.of(new MessageInfo("scheduleRequirementsMetMsg", null)));
    when(schedule2Service.checkStatus(MILL_ID, YEAR)).thenReturn(serviceResult);
    when(messageSource.getMessage(
            eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");

    ResponseEntity<Schedule2CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("MET", response.getBody().outcome());
    MessageInfo msg = response.getBody().messages().get(0);
    assertEquals("scheduleRequirementsMetMsg", msg.key());
    assertEquals("All requirements for this schedule have been met", msg.text());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void checkStatus_issuesOutcome_prefixesFieldLabelIntoText() {
    // The service carries the field label in MessageInfo.text; the controller prefixes it as
    // "<label>: <resolvedText>" (legacy Schedule2MB:168 + Schedule 1 valueRequired parity).
    Schedule2CheckStatusResponse serviceResult =
        new Schedule2CheckStatusResponse(
            "ISSUES",
            List.of(
                new MessageInfo("missingRequiredFieldMsg", "Purchased/Private Log Costs - Cost")));
    when(schedule2Service.checkStatus(MILL_ID, YEAR)).thenReturn(serviceResult);
    when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value Required");

    ResponseEntity<Schedule2CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals("ISSUES", response.getBody().outcome());
    MessageInfo msg = response.getBody().messages().get(0);
    assertEquals("missingRequiredFieldMsg", msg.key());
    assertEquals("Purchased/Private Log Costs - Cost: Value Required", msg.text());
  }
}
