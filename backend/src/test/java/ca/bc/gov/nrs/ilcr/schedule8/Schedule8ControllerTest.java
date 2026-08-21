package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Options;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8RateRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
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
 * Unit test for {@link Schedule8Controller}: the no-summary-required context guard ({@code
 * validateMillYearActive}), service delegation across the read / page / sample / rate write
 * endpoints, the server-derived {@code callerMayEdit} flag, the verbatim AD-8 saved/deleted message
 * decoration, and the check-status schedule/page/sample/issue key resolution — collaborators
 * mocked, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8ControllerTest {

  private static final long MILL_ID = 546L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;

  @Mock private Schedule8Service schedule8Service;

  @Mock private SchedulePermissions permissions;

  @Mock private MessageSource messageSource;

  @Mock private Authentication authentication;

  @InjectMocks private Schedule8Controller controller;

  private void stubSaved(String key, String text) {
    when(messageSource.getMessage(eq(key), any(), any(), any(Locale.class))).thenReturn(text);
  }

  @Test
  void getSchedule8_validatesContext_derivesEditFlag_returnsDocument() {
    Schedule8Response doc = mock(Schedule8Response.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule8Service.getSchedule8(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<Schedule8Response> response =
        controller.getSchedule8(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void getOptions_delegates_withoutContextValidation() {
    Schedule8Options options = mock(Schedule8Options.class);
    when(schedule8Service.getOptions()).thenReturn(options);

    ResponseEntity<Schedule8Options> response = controller.getOptions(authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(options, response.getBody());
    // Reference data is global — the controller must NOT run the mill/year context guard.
    verify(millContextService, never()).validateMillYearActive(anyLong(), anyInt());
  }

  @Test
  void savePage_delegates_andAppliesSavedMessage() {
    Schedule8PageRequest request = mock(Schedule8PageRequest.class);
    Schedule8Response saved = mock(Schedule8Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule8Service.savePage(MILL_ID, YEAR, request, true, "dev-admin")).thenReturn(saved);
    stubSaved("dataSavedSuccesfullyInfoMsg", "Data saved successfully");

    ResponseEntity<Schedule8Response> response =
        controller.savePage(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deletePage_delegates_andReturnsDeletedMessage() {
    stubSaved("dataDeletedSuccesfullyInfoMsg", "Data deleted successfully");

    ResponseEntity<MessageResponse> response =
        controller.deletePage(MILL_ID, YEAR, 8001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(schedule8Service).deletePage(MILL_ID, YEAR, 8001);
  }

  @Test
  void saveSample_delegates_andAppliesSavedMessage() {
    Schedule8SampleRequest request = mock(Schedule8SampleRequest.class);
    Schedule8Response saved = mock(Schedule8Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule8Service.saveSample(MILL_ID, YEAR, 8001, request, true, "dev-admin"))
        .thenReturn(saved);
    stubSaved("dataSavedSuccesfullyInfoMsg", "Data saved successfully");

    ResponseEntity<Schedule8Response> response =
        controller.saveSample(MILL_ID, YEAR, 8001, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteSample_delegates_andAppliesDeletedMessage() {
    Schedule8Response updated = mock(Schedule8Response.class);
    when(updated.withMessage(any())).thenReturn(updated);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule8Service.deleteSample(MILL_ID, YEAR, 8001, 9001, true)).thenReturn(updated);
    stubSaved("dataDeletedSuccesfullyInfoMsg", "Data deleted successfully");

    ResponseEntity<Schedule8Response> response =
        controller.deleteSample(MILL_ID, YEAR, 8001, 9001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(updated).withMessage(any());
  }

  @Test
  void addRate_delegates_withNullRowId_andAppliesSavedMessage() {
    Schedule8RateRequest request = mock(Schedule8RateRequest.class);
    Schedule8Response saved = mock(Schedule8Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule8Service.saveRate(MILL_ID, YEAR, 9001, null, request, true, "dev-admin"))
        .thenReturn(saved);
    stubSaved("dataSavedSuccesfullyInfoMsg", "Data saved successfully");

    ResponseEntity<Schedule8Response> response =
        controller.addRate(MILL_ID, YEAR, 9001, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(schedule8Service).saveRate(MILL_ID, YEAR, 9001, null, request, true, "dev-admin");
    verify(saved).withMessage(any());
  }

  @Test
  void updateRate_delegates_withRowId_andAppliesSavedMessage() {
    Schedule8RateRequest request = mock(Schedule8RateRequest.class);
    Schedule8Response saved = mock(Schedule8Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule8Service.saveRate(MILL_ID, YEAR, 9001, 7001, request, true, "dev-admin"))
        .thenReturn(saved);
    stubSaved("dataSavedSuccesfullyInfoMsg", "Data saved successfully");

    ResponseEntity<Schedule8Response> response =
        controller.updateRate(MILL_ID, YEAR, 9001, 7001, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(schedule8Service).saveRate(MILL_ID, YEAR, 9001, 7001, request, true, "dev-admin");
    verify(saved).withMessage(any());
  }

  @Test
  void deleteRate_delegates_andAppliesDeletedMessage() {
    Schedule8Response updated = mock(Schedule8Response.class);
    when(updated.withMessage(any())).thenReturn(updated);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule8Service.deleteRate(MILL_ID, YEAR, 9001, 7001, true)).thenReturn(updated);
    stubSaved("dataDeletedSuccesfullyInfoMsg", "Data deleted successfully");

    ResponseEntity<Schedule8Response> response =
        controller.deleteRate(MILL_ID, YEAR, 9001, 7001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(updated).withMessage(any());
  }

  @Test
  void checkStatus_resolvesScheduleBanner_pageSample_andFieldIssueKeys() {
    // One failing page carrying a page-level field issue and one sample with its own issue, so the
    // schedule-level, per-page, and per-sample key→text resolution branches are all exercised.
    Schedule8CheckStatusResponse raw =
        new Schedule8CheckStatusResponse(
            "ISSUES",
            List.of(new MessageInfo("scheduleRequirementsMetMsg", null)),
            List.of(
                new Schedule8PageCheckResult(
                    8001,
                    false,
                    List.of(
                        new Schedule8CheckFieldIssue(
                            "Contact", new MessageInfo("missingRequiredFieldMsg", null))),
                    List.of(
                        new Schedule8SampleCheckResult(
                            9001,
                            false,
                            List.of(
                                new Schedule8CheckFieldIssue(
                                    "Skidding/Yarding",
                                    new MessageInfo(
                                        "skiddingYardingEqualsCentPercent", null))))))));
    when(schedule8Service.checkStatus(MILL_ID, YEAR)).thenReturn(raw);
    when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
        .thenReturn("resolved text");

    ResponseEntity<Schedule8CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("ISSUES", response.getBody().outcome());
    assertEquals("resolved text", response.getBody().messages().get(0).text());
    Schedule8PageCheckResult page = response.getBody().pages().get(0);
    assertEquals("resolved text", page.issues().get(0).message().text());
    assertEquals("resolved text", page.samples().get(0).issues().get(0).message().text());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void checkStatusPage_delegatesToSinglePageScope_andResolvesKeys() {
    Schedule8CheckStatusResponse raw =
        new Schedule8CheckStatusResponse(
            "MET",
            List.of(),
            List.of(new Schedule8PageCheckResult(8001, true, List.of(), List.of())));
    when(schedule8Service.checkStatusPage(MILL_ID, YEAR, 8001)).thenReturn(raw);

    ResponseEntity<Schedule8CheckStatusResponse> response =
        controller.checkStatusPage(MILL_ID, YEAR, 8001, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("MET", response.getBody().outcome());
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }
}
