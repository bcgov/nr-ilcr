package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableSaveRequest;
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
 * Unit test for the batch-save endpoint on {@link Schedule3OtherCostsController}: validates the
 * mill/year context, delegates to the service, and picks the save vs delete message from {@code
 * intent} (legacy parity). Collaborators mocked (no Spring, no {@code @PreAuthorize}).
 */
@ExtendWith(MockitoExtension.class)
class Schedule3OtherCostsControllerTest {

  private static final long MILL_ID = 574L;
  private static final int YEAR = 2021;
  private static final String CATEGORY = "3";

  @Mock private MillContextService millContextService;
  @Mock private Schedule3Service schedule3Service;
  @Mock private SchedulePermissions permissions;
  @Mock private MessageSource messageSource;
  @Mock private Authentication authentication;

  @InjectMocks private Schedule3OtherCostsController controller;

  private OtherAcceptableDocument mockDocEchoingMessage() {
    OtherAcceptableDocument doc = mock(OtherAcceptableDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    return doc;
  }

  @Test
  void saveOtherAcceptable_saveIntent_appliesSavedMessage() {
    List<OtherAcceptableSaveRequest.Row> rows = List.of();
    OtherAcceptableSaveRequest request = mock(OtherAcceptableSaveRequest.class);
    when(request.rows()).thenReturn(rows);
    OtherAcceptableDocument doc = mockDocEchoingMessage();
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule3Service.saveOtherAcceptable(MILL_ID, YEAR, rows, "dev-admin")).thenReturn(doc);
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully.");

    ResponseEntity<OtherAcceptableDocument> response =
        controller.saveOtherAcceptable(MILL_ID, YEAR, "save", request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(messageSource)
        .getMessage(eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class));
  }

  @Test
  void saveOtherAcceptable_deleteIntent_appliesDeletedMessage() {
    List<OtherAcceptableSaveRequest.Row> rows = List.of();
    OtherAcceptableSaveRequest request = mock(OtherAcceptableSaveRequest.class);
    when(request.rows()).thenReturn(rows);
    OtherAcceptableDocument doc = mockDocEchoingMessage();
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule3Service.saveOtherAcceptable(MILL_ID, YEAR, rows, "dev-admin")).thenReturn(doc);
    when(messageSource.getMessage(
            eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully.");

    ResponseEntity<OtherAcceptableDocument> response =
        controller.saveOtherAcceptable(MILL_ID, YEAR, "delete", request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(messageSource)
        .getMessage(eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class));
  }
}
