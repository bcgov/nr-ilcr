package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
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
 * Unit test for {@link Schedule1OtherCostsController}. Verifies the controller validates the
 * mill/year context, delegates to the service, and decorates mutations with the verbatim bundle
 * message — with all collaborators mocked (no Spring context, no {@code @PreAuthorize} evaluation).
 */
@ExtendWith(MockitoExtension.class)
class Schedule1OtherCostsControllerTest {

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

  private Schedule1OtherCostsController controller;

  @BeforeEach
  void setUp() {
    controller = new Schedule1OtherCostsController(
        millContextService, schedule1Service, permissions, messageSource);
  }

  private OtherCostsDocument mockDocEchoingMessage() {
    OtherCostsDocument doc = mock(OtherCostsDocument.class);
    when(doc.withMessage(any())).thenReturn(doc);
    return doc;
  }

  @Test
  void getOtherCosts_validatesContext_andPassesEditFlag() {
    OtherCostsDocument doc = mock(OtherCostsDocument.class);
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule1Service.getOtherCostsDocument(MILL_ID, YEAR, true)).thenReturn(doc);

    ResponseEntity<OtherCostsDocument> response =
        controller.getOtherCosts(MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertSame(doc, response.getBody());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
  }

  @Test
  void addOtherCost_delegates_andAppliesSavedMessage() {
    OtherCostRequest request = mock(OtherCostRequest.class);
    OtherCostsDocument doc = mockDocEchoingMessage();
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule1Service.addOtherCost(MILL_ID, YEAR, request, "dev-admin")).thenReturn(doc);
    when(messageSource.getMessage(eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully.");

    ResponseEntity<OtherCostsDocument> response =
        controller.addOtherCost(MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void updateOtherCost_delegates_andAppliesSavedMessage() {
    OtherCostRequest request = mock(OtherCostRequest.class);
    OtherCostsDocument doc = mockDocEchoingMessage();
    when(authentication.getName()).thenReturn("dev-admin");
    when(schedule1Service.updateOtherCost(MILL_ID, YEAR, 7, request, "dev-admin")).thenReturn(doc);
    when(messageSource.getMessage(eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully.");

    ResponseEntity<OtherCostsDocument> response =
        controller.updateOtherCost(7, MILL_ID, YEAR, request, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }

  @Test
  void deleteOtherCost_delegates_andAppliesDeletedMessage() {
    OtherCostsDocument doc = mockDocEchoingMessage();
    when(schedule1Service.deleteOtherCost(MILL_ID, YEAR, 7)).thenReturn(doc);
    when(messageSource.getMessage(
        eq("dataDeletedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data deleted successfully.");

    ResponseEntity<OtherCostsDocument> response =
        controller.deleteOtherCost(7, MILL_ID, YEAR, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(millContextService).validateScheduleViewable(MILL_ID, YEAR, CATEGORY);
    verify(doc).withMessage(any());
  }
}
