package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit test for {@link Schedule1Controller}. Verifies context validation, service delegation, the
 * server-derived {@code editable} flag, and the verbatim success-message decoration — collaborators
 * mocked, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1ControllerTest {

  private static final long MILL_ID = 514L;
  private static final int YEAR = 2021;
  private static final String CATEGORY = "1";

  @Mock
  private MillContextService millContextService;

  @Mock
  private Schedule1Service schedule1Service;

  @Mock
  private SchedulePermissions permissions;

  @Mock
  private MessageSource messageSource;

  @Mock
  private Authentication authentication;

  @InjectMocks
  private Schedule1Controller controller;

  @Test
  void getSchedule1_validatesContext_derivesEditFlag_andReturnsDocument() {
    Schedule1Response doc = mock(Schedule1Response.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule1Service.getSchedule1(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<Schedule1Response> response =
        controller.getSchedule1(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
  }

  @Test
  void saveSchedule1_delegates_andAppliesSavedMessage() {
    Schedule1Request request = mock(Schedule1Request.class);
    Schedule1Response saved = mock(Schedule1Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule1Service.saveSchedule1(MILL_ID, YEAR, request, true, "dev-admin"))
        .thenReturn(saved);
    when(messageSource.getMessage(eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully.");

    ResponseEntity<Schedule1Response> response =
        controller.saveSchedule1(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteSchedule1_delegates_andReturnsDeletedMessage() {
    when(messageSource.getMessage(
        eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully.");

    ResponseEntity<MessageResponse> response =
        controller.deleteSchedule1(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(schedule1Service).deleteSchedule1(MILL_ID, YEAR);
  }

  @Test
  void checkStatus_validatesContext_andReturnsServiceResult() {
    CheckStatusResponse status = mock(CheckStatusResponse.class);
    when(schedule1Service.checkSchedule1Status(MILL_ID, YEAR)).thenReturn(status);

    ResponseEntity<CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(status, response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
  }
}
