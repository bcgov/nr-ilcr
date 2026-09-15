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

import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostSaveRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Story 2.4 Subtotal Other Costs operations on {@link Schedule1Service}
 * (server-side derivation + Draft gate + not-found). Mocked repository — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1OtherCostsServiceTest {

  private static final long MILL = 523L;
  private static final int YEAR = 2021;
  private static final int SUMMARY = 1025;
  private static final String USER = "tester";

  @Mock private Schedule1Repository repository;

  @Mock private MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", and a mock would make every
  // original-value assertion below an assertion about the mock (Story 16.2).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule1Service service;

  private void stubContext(String trackStatus) {
    lenient()
        .when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(trackStatus));
  }

  private void stubRows(BigDecimal sharedVolume, List<OtherCostDetailRow> rows) {
    lenient()
        .when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.ofNullable(sharedVolume));
    lenient().when(repository.findOtherCostRows(SUMMARY)).thenReturn(rows);
  }

  @Test
  void getDocument_derivesSubtotalCountAndPerRowPerUnit() {
    stubContext("D");
    stubRows(
        new BigDecimal("5000"),
        List.of(
            new OtherCostDetailRow(5051, "Existing Row A", 3000, new BigDecimal("5000")),
            new OtherCostDetailRow(5052, "Existing Row B", null, new BigDecimal("5000"))));

    OtherCostsDocument doc = service.getOtherCostsDocument(MILL, YEAR, CallerRights.SUBMITTER);

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
    assertFalse(service.getOtherCostsDocument(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void add_inheritsSharedVolume_andPersists() {
    stubContext("D");
    stubRows(new BigDecimal("6000"), List.of());
    service.addOtherCost(
        MILL, YEAR, new OtherCostRequest("New Row", 1200), CallerRights.SUBMITTER, USER);
    // BR-06: the new row inherits the shared Other-Costs volume (6000).
    verify(repository).insertOtherCost(SUMMARY, "New Row", 1200, new BigDecimal("6000"), USER);
  }

  @Test
  void add_nullCostAccepted() {
    stubContext("D");
    stubRows(new BigDecimal("6000"), List.of());
    service.addOtherCost(
        MILL, YEAR, new OtherCostRequest("No cost row", null), CallerRights.SUBMITTER, USER);
    verify(repository).insertOtherCost(SUMMARY, "No cost row", null, new BigDecimal("6000"), USER);
  }

  @Test
  void add_nonDraft_throws409() {
    stubContext("S");
    OtherCostRequest request = new OtherCostRequest("x", 1);
    assertThrows(
        ScheduleNotEditableException.class,
        () -> service.addOtherCost(MILL, YEAR, request, CallerRights.SUBMITTER, USER));
  }

  @Test
  void update_unknownId_throws404() {
    stubContext("D");
    when(repository.updateOtherCost(999999, SUMMARY, "x", 1, USER)).thenReturn(0);
    OtherCostRequest request = new OtherCostRequest("x", 1);
    assertThrows(
        OtherCostNotFoundException.class,
        () -> service.updateOtherCost(MILL, YEAR, 999999, request, CallerRights.SUBMITTER, USER));
  }

  @Test
  void delete_unknownId_throws404() {
    stubContext("D");
    when(repository.deleteOtherCost(999999, SUMMARY)).thenReturn(0);
    assertThrows(
        OtherCostNotFoundException.class,
        () -> service.deleteOtherCost(MILL, YEAR, 999999, CallerRights.SUBMITTER));
  }

  @Test
  void getDocument_editableFalseWhenCallerCannotEdit() {
    // Draft track but caller lacks EDIT_SCHEDULE: the callerMayEdit short-circuit keeps it
    // read-only.
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of());
    assertFalse(service.getOtherCostsDocument(MILL, YEAR, CallerRights.NONE).editable());
  }

  @Test
  void add_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.of(new BigDecimal("6000")));
    doThrow(new DataIntegrityViolationException("boom"))
        .when(repository)
        .insertOtherCost(eq(SUMMARY), any(), any(), any(), eq(USER));

    OtherCostRequest request = new OtherCostRequest("x", 1);
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.addOtherCost(MILL, YEAR, request, CallerRights.SUBMITTER, USER));
  }

  @Test
  void update_happyPath_returnsRebuiltDocument() {
    stubContext("D");
    stubRows(
        new BigDecimal("5000"),
        List.of(new OtherCostDetailRow(5051, "Row A", 3000, new BigDecimal("5000"))));
    when(repository.updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER)).thenReturn(1);

    OtherCostsDocument doc =
        service.updateOtherCost(
            MILL, YEAR, 5051, new OtherCostRequest("Row A+", 3200), CallerRights.SUBMITTER, USER);

    assertEquals(1, doc.count());
    assertTrue(doc.editable());
    verify(repository).updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER);
  }

  @Test
  void save_reconcilesUpdateInsertAndDelete() {
    stubContext("D");
    stubRows(
        new BigDecimal("5000"),
        List.of(
            new OtherCostDetailRow(5051, "Row A", 3000, new BigDecimal("5000")),
            new OtherCostDetailRow(5052, "Row B", 100, new BigDecimal("5000"))));

    // Update 5051, insert a fresh row (inherits shared volume), and drop 5052 (absent → delete).
    service.saveOtherCosts(
        MILL,
        YEAR,
        List.of(
            new OtherCostSaveRequest.Row(5051, "Row A+", 3200),
            new OtherCostSaveRequest.Row(null, "Fresh", 500)),
        CallerRights.SUBMITTER,
        USER);

    verify(repository).updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER);
    verify(repository).insertOtherCost(SUMMARY, "Fresh", 500, new BigDecimal("5000"), USER);
    verify(repository).deleteOtherCost(5052, SUMMARY);
  }

  @Test
  void save_unknownId_throwsNotFound() {
    stubContext("D");
    stubRows(
        new BigDecimal("5000"),
        List.of(new OtherCostDetailRow(5051, "Row A", 3000, new BigDecimal("5000"))));

    // A row references an id that is not an itemized item-19 row here → conflict, not a silent
    // insert.
    List<OtherCostSaveRequest.Row> rows = List.of(new OtherCostSaveRequest.Row(999999, "Ghost", 1));
    assertThrows(
        OtherCostNotFoundException.class,
        () -> service.saveOtherCosts(MILL, YEAR, rows, CallerRights.SUBMITTER, USER));
  }

  @Test
  void save_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    stubRows(
        new BigDecimal("5000"),
        List.of(new OtherCostDetailRow(5051, "Row A", 3000, new BigDecimal("5000"))));
    when(repository.updateOtherCost(5051, SUMMARY, "Row A+", 3200, USER))
        .thenThrow(new DataIntegrityViolationException("boom"));

    List<OtherCostSaveRequest.Row> rows =
        List.of(new OtherCostSaveRequest.Row(5051, "Row A+", 3200));
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.saveOtherCosts(MILL, YEAR, rows, CallerRights.SUBMITTER, USER));
  }

  @Test
  void update_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.updateOtherCost(5051, SUMMARY, "x", 1, USER))
        .thenThrow(new DataIntegrityViolationException("boom"));

    OtherCostRequest request = new OtherCostRequest("x", 1);
    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.updateOtherCost(MILL, YEAR, 5051, request, CallerRights.SUBMITTER, USER));
  }

  @Test
  void delete_happyPath_returnsRebuiltDocument() {
    stubContext("D");
    stubRows(new BigDecimal("5000"), List.of());
    when(repository.deleteOtherCost(5051, SUMMARY)).thenReturn(1);

    OtherCostsDocument doc = service.deleteOtherCost(MILL, YEAR, 5051, CallerRights.SUBMITTER);

    assertEquals(0, doc.count());
    assertTrue(doc.editable());
    verify(repository).deleteOtherCost(5051, SUMMARY);
  }

  @Test
  void delete_persistenceFailure_translatesToScheduleNotSaved() {
    stubContext("D");
    when(repository.deleteOtherCost(5051, SUMMARY))
        .thenThrow(new DataIntegrityViolationException("boom"));

    assertThrows(
        ScheduleNotSavedException.class,
        () -> service.deleteOtherCost(MILL, YEAR, 5051, CallerRights.SUBMITTER));
  }
}
