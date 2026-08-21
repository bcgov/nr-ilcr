package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule4.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule4.dto.LocationCheckResult;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4SubPageRowRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
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
 * Unit test for {@link Schedule4Controller}: the no-summary-required context guard ({@code
 * validateMillYearActive}), service delegation, the server-derived {@code editable} flag, the
 * verbatim AD-8 success-message decoration, and the check-status schedule/location/issue key
 * resolution — collaborators mocked, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4ControllerTest {

  private static final long MILL_ID = 546L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;

  @Mock private Schedule4Service schedule4Service;

  @Mock private SchedulePermissions permissions;

  @Mock private MessageSource messageSource;

  @Mock private Authentication authentication;

  @InjectMocks private Schedule4Controller controller;

  @Test
  void getSchedule4_validatesContext_derivesEditFlag_returnsDocument() {
    Schedule4Response doc = mock(Schedule4Response.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule4Service.getSchedule4(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<Schedule4Response> response =
        controller.getSchedule4(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    // Schedule 4 has no summary of its own — mill/year existence + active only (no viewable guard).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void saveLocation_delegates_andAppliesSavedMessage() {
    Schedule4LocationRequest request = mock(Schedule4LocationRequest.class);
    Schedule4Response saved = mock(Schedule4Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule4Service.saveLocation(MILL_ID, YEAR, request, true, "dev-admin"))
        .thenReturn(saved);
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully");

    ResponseEntity<Schedule4Response> response =
        controller.saveLocation(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteLocation_delegates_andReturnsDeletedMessage() {
    when(messageSource.getMessage(
            eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully");

    ResponseEntity<MessageResponse> response =
        controller.deleteLocation(MILL_ID, YEAR, 8001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(schedule4Service).deleteLocation(MILL_ID, YEAR, 8001);
  }

  @Test
  void addSubPageRow_delegates_andAppliesSavedMessage() {
    Schedule4SubPageRowRequest request = mock(Schedule4SubPageRowRequest.class);
    Schedule4Response saved = mock(Schedule4Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule4Service.addSubPageRow(MILL_ID, YEAR, 8001, request, true, "dev-admin"))
        .thenReturn(saved);
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully");

    ResponseEntity<Schedule4Response> response =
        controller.addSubPageRow(MILL_ID, YEAR, 8001, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteSubPageRow_delegates_andAppliesDeletedMessage() {
    Schedule4Response updated = mock(Schedule4Response.class);
    when(updated.withMessage(any())).thenReturn(updated);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule4Service.deleteSubPageRow(MILL_ID, YEAR, 8001, 9001, true)).thenReturn(updated);
    when(messageSource.getMessage(
            eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully");

    ResponseEntity<Schedule4Response> response =
        controller.deleteSubPageRow(MILL_ID, YEAR, 8001, 9001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(updated).withMessage(any());
  }

  @Test
  void checkStatus_resolvesScheduleBanner_locationMessages_andFieldIssues() {
    // ISSUES with one failing location that carries both a met-style message and a field issue, so
    // the schedule-level, per-location, and per-issue key→text resolution branches are all
    // exercised.
    Schedule4CheckStatusResponse raw =
        new Schedule4CheckStatusResponse(
            "ISSUES",
            List.of(new MessageInfo("scheduleRequirementsMetMsg", null)),
            List.of(
                new LocationCheckResult(
                    8001,
                    "Dump A",
                    false,
                    List.of(new MessageInfo("locationRequirementsMetMsg", null)),
                    List.of(
                        new FieldIssue(47, new MessageInfo("missingRequiredFieldMsg", null))))));
    when(schedule4Service.checkStatus(MILL_ID, YEAR)).thenReturn(raw);
    when(messageSource.getMessage(anyString(), any(), any(), any(Locale.class)))
        .thenReturn("resolved text");

    ResponseEntity<Schedule4CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("ISSUES", response.getBody().outcome());
    assertEquals("resolved text", response.getBody().messages().get(0).text());
    LocationCheckResult location = response.getBody().locations().get(0);
    assertEquals("resolved text", location.messages().get(0).text());
    assertEquals("resolved text", location.issues().get(0).message().text());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }
}
