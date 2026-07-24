package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Story 2.4 Subtotal Other Costs operations on {@link Schedule1Service} (server-side
 * derivation + Draft gate + not-found). Mocked repository — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1OtherCostsServiceTest {

  private static final long MILL = 523L;
  private static final int YEAR = 2021;
  private static final int SUMMARY = 1025;
  private static final String USER = "tester";

  @Mock
  private Schedule1Repository repository;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule1Service service;

  private void stubContext(String trackStatus) {
    lenient().when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(trackStatus));
  }

  private void stubRows(BigDecimal sharedVolume, List<OtherCostDetailRow> rows) {
    lenient().when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.ofNullable(sharedVolume));
    lenient().when(repository.findOtherCostRows(SUMMARY)).thenReturn(rows);
  }

  @Test
  void getDocument_derivesSubtotalCountAndPerRowPerUnit() {
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of(
        new OtherCostDetailRow(5051, "Existing Row A", 3000, new BigDecimal("5000")),
        new OtherCostDetailRow(5052, "Existing Row B", null, new BigDecimal("5000"))));

    OtherCostsDocument doc = service.getOtherCostsDocument(MILL, YEAR, true);

    assertEquals(0, new BigDecimal("5000").compareTo(doc.volume()));
    assertEquals(3000L, doc.costSubtotal());
    assertEquals(2, doc.count());
    assertTrue(doc.editable());
    // Row perUnit = cost / shared volume (BR-06): 3000 / 5000 = 0.6; null cost -> null.
    assertEquals(0, new BigDecimal("0.6").compareTo(doc.rows().get(0).perUnit()));
    assertNull(doc.rows().get(1).cost());
    assertNull(doc.rows().get(1).perUnit());
  }

  @Test
  void getDocument_editableFalseWhenNotDraft() {
    stubContext("S");
    stubRows(new BigDecimal("5000"), List.of());
    assertFalse(service.getOtherCostsDocument(MILL, YEAR, true).editable());
  }

  @Test
  void add_inheritsSharedVolume_andPersists() {
    stubContext("D");
    stubRows(new BigDecimal("6000"), List.of());
    service.addOtherCost(MILL, YEAR, new OtherCostRequest("New Row", 1200), USER);
    // BR-06: the new row inherits the shared Other-Costs volume (6000).
    verify(repository).insertOtherCost(
        eq(SUMMARY), eq("New Row"), eq(1200), eq(new BigDecimal("6000")), eq(USER));
  }

  @Test
  void add_nullCostAccepted() {
    stubContext("D");
    stubRows(new BigDecimal("6000"), List.of());
    service.addOtherCost(MILL, YEAR, new OtherCostRequest("No cost row", null), USER);
    verify(repository).insertOtherCost(
        eq(SUMMARY), eq("No cost row"), eq(null), eq(new BigDecimal("6000")), eq(USER));
  }

  @Test
  void add_nonDraft_throws409() {
    stubContext("S");
    assertThrows(ScheduleNotEditableException.class,
        () -> service.addOtherCost(MILL, YEAR, new OtherCostRequest("x", 1), USER));
  }

  @Test
  void update_unknownId_throws404() {
    stubContext("D");
    when(repository.updateOtherCost(999999, SUMMARY, "x", 1, USER)).thenReturn(0);
    assertThrows(OtherCostNotFoundException.class,
        () -> service.updateOtherCost(MILL, YEAR, 999999, new OtherCostRequest("x", 1), USER));
  }

  @Test
  void delete_unknownId_throws404() {
    stubContext("D");
    when(repository.deleteOtherCost(999999, SUMMARY)).thenReturn(0);
    assertThrows(OtherCostNotFoundException.class,
        () -> service.deleteOtherCost(MILL, YEAR, 999999));
  }

  @Test
  void getDocument_editableFalseWhenCallerCannotEdit() {
    // Draft track but caller lacks EDIT_SCHEDULE: the callerMayEdit short-circuit keeps it read-only.
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of());
    assertFalse(service.getOtherCostsDocument(MILL, YEAR, false).editable());
  }

  @Test
  void add_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.of(new BigDecimal("6000")));
    doThrow(new DataIntegrityViolationException("boom"))
        .when(repository).insertOtherCost(eq(SUMMARY), any(), any(), any(), eq(USER));

    assertThrows(ScheduleNotSavedException.class,
        () -> service.addOtherCost(MILL, YEAR, new OtherCostRequest("x", 1), USER));
  }

  @Test
  void update_happyPath_returnsRebuiltDocument() {
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of(
        new OtherCostDetailRow(5051, "Row A", 3000, new BigDecimal("5000"))));
    when(repository.updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER)).thenReturn(1);

    OtherCostsDocument doc =
        service.updateOtherCost(MILL, YEAR, 5051, new OtherCostRequest("Row A+", 3200), USER);

    assertEquals(1, doc.count());
    assertTrue(doc.editable());
    verify(repository).updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER);
  }

  @Test
  void update_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.updateOtherCost(5051, SUMMARY, "x", 1, USER))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class,
        () -> service.updateOtherCost(MILL, YEAR, 5051, new OtherCostRequest("x", 1), USER));
  }

  @Test
  void delete_happyPath_returnsRebuiltDocument() {
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of());
    when(repository.deleteOtherCost(5051, SUMMARY)).thenReturn(1);

    OtherCostsDocument doc = service.deleteOtherCost(MILL, YEAR, 5051);

    assertEquals(0, doc.count());
    assertTrue(doc.editable());
    verify(repository).deleteOtherCost(5051, SUMMARY);
  }

  @Test
  void delete_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.deleteOtherCost(5051, SUMMARY))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(ScheduleNotSavedException.class,
        () -> service.deleteOtherCost(MILL, YEAR, 5051));
  }
}
