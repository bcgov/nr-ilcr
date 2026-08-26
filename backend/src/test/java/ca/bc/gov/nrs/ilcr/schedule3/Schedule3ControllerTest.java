package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Unit test for the three Schedule 3 controllers ({@link Schedule3Controller}, {@link
 * Schedule3OtherCostsController}, {@link Schedule3UnacceptableCostsController}). Verifies the
 * context guards (main page: {@code validateMillYearActive}; sub-pages: the summary-required {@code
 * validateScheduleViewable}), the server-derived {@code editable} flag (from {@code
 * EDIT_SCHEDULE}), service delegation, and verbatim success-message decoration on the mutating
 * responses (AD-8) — collaborators mocked, no Spring context. Mirrors {@code
 * Schedule2ControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3ControllerTest {

  private static final long MILL_ID = 522L;
  private static final int YEAR = 2021;
  private static final String CATEGORY = "3";
  private static final String USER = "dev-admin";

  @Mock private MillContextService millContextService;

  @Mock private Schedule3Service schedule3Service;

  @Mock private SchedulePermissions permissions;

  @Mock private org.springframework.context.MessageSource messageSource;

  @Mock private Authentication authentication;

  @InjectMocks private Schedule3Controller controller;

  @InjectMocks private Schedule3OtherCostsController otherCostsController;

  @InjectMocks private Schedule3UnacceptableCostsController unacceptableController;

  // ---- main document ----------------------------------------------------------------------------

  @Test
  void getSchedule3_validatesContext_derivesEditFlag_andReturnsDocument() {
    Schedule3Response doc = mock(Schedule3Response.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule3Service.getSchedule3(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<Schedule3Response> response =
        controller.getSchedule3(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    // validateMillYearActive, NOT validateScheduleViewable — the latter required a category-"3"
    // summary to exist, which is what made an unsaved Schedule 3 a 404 (defect #296). The
    // sub-page tests below deliberately keep the summary-required guard (D1).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  @Test
  void saveSchedule3_delegates_asEditor_andAppliesSavedMessage() {
    Schedule3Request request = mock(Schedule3Request.class);
    Schedule3Response saved = mock(Schedule3Response.class);
    when(saved.withMessage(any())).thenReturn(saved);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.saveSchedule3(MILL_ID, YEAR, request, true, USER)).thenReturn(saved);

    ResponseEntity<Schedule3Response> response =
        controller.saveSchedule3(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    // validateMillYearActive, NOT validateScheduleViewable — the latter required a category-"3"
    // summary to exist, which is what made an unsaved Schedule 3 a 404 (defect #296). The
    // sub-page tests below deliberately keep the summary-required guard (D1).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(saved).withMessage(any());
  }

  @Test
  void deleteSchedule3_delegates_andReturnsDeletedMessage() {
    when(schedule3Service.deleteSchedule3(MILL_ID, YEAR)).thenReturn(true);
    ResponseEntity<MessageResponse> response =
        controller.deleteSchedule3(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    // validateMillYearActive, NOT validateScheduleViewable — the latter required a category-"3"
    // summary to exist, which is what made an unsaved Schedule 3 a 404 (defect #296). The
    // sub-page tests below deliberately keep the summary-required guard (D1).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
    verify(schedule3Service).deleteSchedule3(MILL_ID, YEAR);
  }

  /**
   * Defect #296: the idempotent no-op must NOT claim a delete happened — the #292 rule, which
   * Schedule 3's controller carried with no test at all until the #296 code review. Without this,
   * swapping the ternary arms was invisible.
   */
  @Test
  void deleteSchedule3_noOp_saysNothingWasDeleted() {
    when(schedule3Service.deleteSchedule3(MILL_ID, YEAR)).thenReturn(false);
    when(messageSource.getMessage(eq("noDataToDeleteInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("No saved data was found, so nothing was deleted");

    ResponseEntity<MessageResponse> response =
        controller.deleteSchedule3(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(
        "No saved data was found, so nothing was deleted", response.getBody().message().text());
  }

  @Test
  void checkStatus_delegates_andReturnsServiceResult() {
    Schedule3CheckStatusResponse result = mock(Schedule3CheckStatusResponse.class);
    when(schedule3Service.checkSchedule3Status(MILL_ID, YEAR)).thenReturn(result);

    ResponseEntity<Schedule3CheckStatusResponse> response =
        controller.checkStatus(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(result, response.getBody());
    // validateMillYearActive, NOT validateScheduleViewable — the latter required a category-"3"
    // summary to exist, which is what made an unsaved Schedule 3 a 404 (defect #296). The
    // sub-page tests below deliberately keep the summary-required guard (D1).
    verify(millContextService).validateMillYearActive(MILL_ID, YEAR);
  }

  // ---- Other Acceptable Costs sub-resource ------------------------------------------------------

  @Test
  void getOtherAcceptable_validatesContext_derivesEditFlag_andReturnsDocument() {
    OtherAcceptableDocument doc = mock(OtherAcceptableDocument.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule3Service.getOtherAcceptableDocument(MILL_ID, YEAR, true)).thenReturn(doc);

    ResponseEntity<OtherAcceptableDocument> response =
        otherCostsController.getOtherAcceptable(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
  }

  @Test
  void addOtherAcceptable_delegates_andAppliesSavedMessage() {
    OtherAcceptableRequest request = mock(OtherAcceptableRequest.class);
    OtherAcceptableDocument doc = mock(OtherAcceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.addOtherAcceptable(MILL_ID, YEAR, request, USER)).thenReturn(doc);

    ResponseEntity<OtherAcceptableDocument> response =
        otherCostsController.addOtherAcceptable(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void updateOtherAcceptable_delegates_andAppliesSavedMessage() {
    OtherAcceptableRequest request = mock(OtherAcceptableRequest.class);
    OtherAcceptableDocument doc = mock(OtherAcceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.updateOtherAcceptable(MILL_ID, YEAR, 7, request, USER)).thenReturn(doc);

    ResponseEntity<OtherAcceptableDocument> response =
        otherCostsController.updateOtherAcceptable(7, MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void deleteOtherAcceptable_delegates_andAppliesDeletedMessage() {
    OtherAcceptableDocument doc = mock(OtherAcceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.deleteOtherAcceptable(MILL_ID, YEAR, 7, USER)).thenReturn(doc);

    ResponseEntity<OtherAcceptableDocument> response =
        otherCostsController.deleteOtherAcceptable(7, MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  // ---- Included Unacceptable Costs sub-resource -------------------------------------------------

  @Test
  void getUnacceptable_validatesContext_derivesEditFlag_andReturnsDocument() {
    UnacceptableDocument doc = mock(UnacceptableDocument.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule3Service.getUnacceptableDocument(MILL_ID, YEAR, false)).thenReturn(doc);

    ResponseEntity<UnacceptableDocument> response =
        unacceptableController.getUnacceptable(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
  }

  @Test
  void addUnacceptable_delegates_andAppliesSavedMessage() {
    UnacceptableRequest request = mock(UnacceptableRequest.class);
    UnacceptableDocument doc = mock(UnacceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.addUnacceptable(MILL_ID, YEAR, request, USER)).thenReturn(doc);

    ResponseEntity<UnacceptableDocument> response =
        unacceptableController.addUnacceptable(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void updateUnacceptable_delegates_andAppliesSavedMessage() {
    UnacceptableRequest request = mock(UnacceptableRequest.class);
    UnacceptableDocument doc = mock(UnacceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.updateUnacceptable(MILL_ID, YEAR, 9, request, USER)).thenReturn(doc);

    ResponseEntity<UnacceptableDocument> response =
        unacceptableController.updateUnacceptable(9, MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void deleteUnacceptable_delegates_andAppliesDeletedMessage() {
    UnacceptableDocument doc = mock(UnacceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    when(authentication.getName()).thenReturn(USER);
    when(schedule3Service.deleteUnacceptable(MILL_ID, YEAR, 9, USER)).thenReturn(doc);

    ResponseEntity<UnacceptableDocument> response =
        unacceptableController.deleteUnacceptable(9, MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }
}
