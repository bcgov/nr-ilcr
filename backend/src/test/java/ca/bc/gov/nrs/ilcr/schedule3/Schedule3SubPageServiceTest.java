package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SubPageRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableSaveRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableSaveRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit test for the Schedule 3 sub-page logic (Story 4.4): item-124 TOT+PO&P group pairing/encoding,
 * next-group-number generation, 404 on an unknown id, the item-38 document, and the check-status
 * sub-page branches (missing description/total/PO&P + S12 Override suppression). Mocked repository —
 * no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3SubPageServiceTest {

  private static final long MILL = 574L;
  private static final int YEAR = 2021;
  private static final int SUMMARY = 1050;

  @Mock private Schedule3Repository repository;
  @Mock private Schedule1Service schedule1Service;
  @Mock private MessageSource messageSource;
  @InjectMocks private Schedule3Service service;

  private static SubPageRow tot(int id, Integer cost, String desc, int group) {
    return new SubPageRow(id, cost, desc, "SCH3_2_TOT_GRP" + group);
  }

  private static SubPageRow pop(int id, Integer cost, String desc, int group) {
    return new SubPageRow(id, cost, desc, "SCH3_2_POP_GRP" + group);
  }

  private void stubDraft() {
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, "N", "c", 0)));
  }

  // ---- Other Acceptable document (pairing + derivation) ----

  @Test
  void getOtherAcceptable_pairsGroupsAndDerivesCrownAndSubtotal() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 124)).thenReturn(List.of(
        tot(5503, 600, "Travel", 2), pop(5504, 200, "Travel", 2),
        tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));

    OtherAcceptableDocument doc = service.getOtherAcceptableDocument(MILL, YEAR, true);

    assertTrue(doc.editable());
    assertEquals(2, doc.count());
    // Rows ordered by TOT detail id (5501 before 5503).
    assertEquals(5501, doc.rows().get(0).id());
    assertEquals("Consulting", doc.rows().get(0).description());
    assertEquals(500, doc.rows().get(0).crown()); // 800 − 300
    assertEquals(400, doc.rows().get(1).crown()); // 600 − 200
    assertEquals(1400L, doc.subtotal().harvest());
    assertEquals(500L, doc.subtotal().pop());
    assertEquals(900L, doc.subtotal().crown());
  }

  @Test
  void getOtherAcceptable_totWithoutPop_crownEqualsTotal() {
    stubDraft();
    // A group with a TOT row but no PO&P peer: legacy DescriptionCostType.getCrownCost
    // (bigDecimalCostSubtraction) returns the Total itself when PO&P is blank — Crown = Total, NOT null.
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1)));

    OtherAcceptableDocument doc = service.getOtherAcceptableDocument(MILL, YEAR, true);

    assertEquals(1, doc.count());
    assertEquals(800, doc.rows().get(0).total());
    assertNull(doc.rows().get(0).pop());
    assertEquals(800, doc.rows().get(0).crown()); // = Total when PO&P is blank
    assertEquals(800L, doc.subtotal().harvest());
    assertEquals(0L, doc.subtotal().pop());
    assertEquals(800L, doc.subtotal().crown());
  }

  @Test
  void addOtherAcceptable_insertsTotAndPopWithNextGroupNumber() {
    stubDraft();
    // One existing group (GRP1) → next group number is 2.
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));

    service.addOtherAcceptable(MILL, YEAR, new OtherAcceptableRequest("New", 900, 100), "user");

    verify(repository).insertSubPageRow(SUMMARY, 124, 900, "New", "SCH3_2_TOT_GRP2", "user");
    verify(repository).insertSubPageRow(SUMMARY, 124, 100, "New", "SCH3_2_POP_GRP2", "user");
  }

  @Test
  void updateOtherAcceptable_unknownId_throwsNotFound() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));

    OtherAcceptableRequest request = new OtherAcceptableRequest("X", 1, 0);
    assertThrows(OtherCostNotFoundException.class, () ->
        service.updateOtherAcceptable(MILL, YEAR, 999999, request, "user"));
  }

  @Test
  void updateOtherAcceptable_updatesTotByIdAndPopPeerByComments() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));

    service.updateOtherAcceptable(MILL, YEAR, 5501,
        new OtherAcceptableRequest("Updated", 1000, 400), "user");

    verify(repository).updateSubPageRowById(5501, SUMMARY, 124, 1000, "Updated", "user");
    verify(repository)
        .updateSubPageRowByComments(SUMMARY, 124, 400, "Updated", "SCH3_2_POP_GRP1", "user");
  }

  @Test
  void saveOtherAcceptable_reconcilesUpdateInsertAndDelete() {
    stubDraft();
    // Existing: GRP1 (Consulting, id 5501) + GRP2 (Travel, id 5503) → next group number is 3.
    when(repository.findSubPageRows(SUMMARY, 124)).thenReturn(List.of(
        tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1),
        tot(5503, 600, "Travel", 2), pop(5504, 200, "Travel", 2)));

    // Request: update GRP1, insert a fresh group, and drop GRP2 (absent → delete).
    service.saveOtherAcceptable(MILL, YEAR, List.of(
        new OtherAcceptableSaveRequest.Row(5501, "Consulting2", 850, 350),
        new OtherAcceptableSaveRequest.Row(null, "Fresh", 900, 100)), "user");

    // Update GRP1 (TOT by id + PO&P peer by comments).
    verify(repository).updateSubPageRowById(5501, SUMMARY, 124, 850, "Consulting2", "user");
    verify(repository)
        .updateSubPageRowByComments(SUMMARY, 124, 350, "Consulting2", "SCH3_2_POP_GRP1", "user");
    // Insert the new group as the next free number (3).
    verify(repository).insertSubPageRow(SUMMARY, 124, 900, "Fresh", "SCH3_2_TOT_GRP3", "user");
    verify(repository).insertSubPageRow(SUMMARY, 124, 100, "Fresh", "SCH3_2_POP_GRP3", "user");
    // Delete the omitted GRP2 (TOT by id + PO&P peer by comments).
    verify(repository).deleteSubPageRowById(5503, SUMMARY, 124);
    verify(repository).deleteSubPageRowByComments(SUMMARY, 124, "SCH3_2_POP_GRP2");
    verify(repository).touchSummary(SUMMARY, "user");
  }

  @Test
  void saveOtherAcceptable_persistenceFailure_translatesToScheduleNotSaved() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));
    when(repository.updateSubPageRowById(5501, SUMMARY, 124, 850, "Consulting2", "user"))
        .thenThrow(new DataIntegrityViolationException("boom"));

    List<OtherAcceptableSaveRequest.Row> rows =
        List.of(new OtherAcceptableSaveRequest.Row(5501, "Consulting2", 850, 350));
    assertThrows(ScheduleNotSavedException.class,
        () -> service.saveOtherAcceptable(MILL, YEAR, rows, "user"));
  }

  @Test
  void saveOtherAcceptable_unknownId_throwsNotFound() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 124))
        .thenReturn(List.of(tot(5501, 800, "Consulting", 1), pop(5502, 300, "Consulting", 1)));

    // A row references a TOT id that is not a group under this summary → conflict, not a silent insert.
    List<OtherAcceptableSaveRequest.Row> rows =
        List.of(new OtherAcceptableSaveRequest.Row(999999, "Ghost", 1, 0));
    assertThrows(OtherCostNotFoundException.class,
        () -> service.saveOtherAcceptable(MILL, YEAR, rows, "user"));
  }

  // ---- Included Unacceptable document ----

  @Test
  void getUnacceptable_sumsRowsAndReadsAnnualRents() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 38))
        .thenReturn(List.of(new SubPageRow(5505, 250, "Penalty", null)));
    when(repository.findDetails(SUMMARY))
        .thenReturn(List.of(new DetailRow(29, null, 777, null, null)));

    UnacceptableDocument doc = service.getUnacceptableDocument(MILL, YEAR, true);

    assertEquals(1, doc.count());
    // Legacy footer total = Σ item-38 rows (250) + Annual Rents Harvest (777) = 1027.
    assertEquals(1027L, doc.subtotalTotal());
    assertEquals(777, doc.annualRentsTotal()); // item-29 Harvest, read-only
  }

  @Test
  void getUnacceptable_nullAnnualRentsCost_returnsNullWithoutNpe() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 38))
        .thenReturn(List.of(new SubPageRow(5505, 250, "Penalty", null)));
    // Annual Rents (item 29) row present but its Harvest cost is null (not entered). Legacy renders
    // this blank, since the annual-rents harvest total cost is nullable, so the read of the first cost
    // must yield null rather than throwing when the selected detail row maps to a null cost.
    when(repository.findDetails(SUMMARY))
        .thenReturn(List.of(new DetailRow(29, null, null, null, null)));

    UnacceptableDocument doc = service.getUnacceptableDocument(MILL, YEAR, true);

    assertEquals(1, doc.count());
    assertEquals(250L, doc.subtotalTotal());
    assertNull(doc.annualRentsTotal());
  }

  @Test
  void saveUnacceptable_reconcilesUpdateInsertAndDelete() {
    stubDraft();
    // Existing item-38 rows 5505 + 5506; findDetails (Annual Rents) empty for the rebuilt doc.
    when(repository.findSubPageRows(SUMMARY, 38)).thenReturn(List.of(
        new SubPageRow(5505, 250, "Penalty", null),
        new SubPageRow(5506, 100, "Old", null)));
    lenient().when(repository.findDetails(SUMMARY)).thenReturn(List.of());

    // Request: update 5505, insert a new row, and drop 5506 (absent → delete).
    service.saveUnacceptable(MILL, YEAR, List.of(
        new UnacceptableSaveRequest.Row(5505, "Penalty!", 260),
        new UnacceptableSaveRequest.Row(null, "New", 500)), "user");

    verify(repository).updateSubPageRowById(5505, SUMMARY, 38, 260, "Penalty!", "user");
    verify(repository).insertSubPageRow(SUMMARY, 38, 500, "New", null, "user");
    verify(repository).deleteSubPageRowById(5506, SUMMARY, 38);
    verify(repository).touchSummary(SUMMARY, "user");
  }

  @Test
  void saveUnacceptable_persistenceFailure_translatesToScheduleNotSaved() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 38))
        .thenReturn(List.of(new SubPageRow(5505, 250, "Penalty", null)));
    when(repository.updateSubPageRowById(5505, SUMMARY, 38, 260, "Penalty!", "user"))
        .thenThrow(new DataIntegrityViolationException("boom"));

    List<UnacceptableSaveRequest.Row> rows =
        List.of(new UnacceptableSaveRequest.Row(5505, "Penalty!", 260));
    assertThrows(ScheduleNotSavedException.class,
        () -> service.saveUnacceptable(MILL, YEAR, rows, "user"));
  }

  @Test
  void saveUnacceptable_unknownId_throwsNotFound() {
    stubDraft();
    when(repository.findSubPageRows(SUMMARY, 38))
        .thenReturn(List.of(new SubPageRow(5505, 250, "Penalty", null)));

    // A row references a detail id that is not an item-38 row here → conflict, not a silent insert.
    List<UnacceptableSaveRequest.Row> rows =
        List.of(new UnacceptableSaveRequest.Row(999999, "Ghost", 1));
    assertThrows(OtherCostNotFoundException.class,
        () -> service.saveUnacceptable(MILL, YEAR, rows, "user"));
  }

  // ---- Check-status sub-page branches ----

  private void stubCheckStatus(String override, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, override, "c", 0)));
    when(repository.findDetails(SUMMARY)).thenReturn(details);
    lenient().when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value Required");
    lenient().when(messageSource.getMessage(eq("harvestNotGreaterThanPopErrorMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value must be greater than or equal to the corresponding PO&P Cost");
    lenient().when(messageSource.getMessage(eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");
  }

  private static DetailRow oa(Integer cost, String desc, String comments) {
    return new DetailRow(124, null, cost, desc, comments);
  }

  private static boolean hasError(Schedule3CheckStatusResponse r, String key, String labelFragment) {
    return r.errors().stream()
        .anyMatch(m -> m.key().equals(key) && m.text().contains(labelFragment));
  }

  @Test
  void checkStatus_otherAcceptableMissingDescriptionTotalAndPop() {
    // A group with a blank description, null total (TOT), and no PO&P row.
    List<DetailRow> details = new ArrayList<>();
    details.add(oa(null, "  ", "SCH3_2_TOT_GRP1"));
    stubCheckStatus("N", details);

    Schedule3CheckStatusResponse r = service.checkSchedule3Status(MILL, YEAR);
    assertFalse(r.requirementsMet());
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Subtotal Other Costs (Description)"));
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Subtotal Other Costs (Harvest Total $)"));
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Subtotal Other Costs (PO&P $)"));
  }

  @Test
  void checkStatus_otherAcceptableHarvestLessThanPop_flaggedThenSuppressedByOverride() {
    List<DetailRow> details = List.of(
        oa(100, "Consulting", "SCH3_2_TOT_GRP1"),
        oa(500, "Consulting", "SCH3_2_POP_GRP1"));
    stubCheckStatus("N", new ArrayList<>(details));
    Schedule3CheckStatusResponse flagged = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(hasError(flagged, "harvestNotGreaterThanPopErrorMsg",
        "Subtotal Other Costs (Harvest Total $)"));

    stubCheckStatus("Y", new ArrayList<>(details));
    Schedule3CheckStatusResponse suppressed = service.checkSchedule3Status(MILL, YEAR);
    assertFalse(hasError(suppressed, "harvestNotGreaterThanPopErrorMsg",
        "Subtotal Other Costs (Harvest Total $)"));
  }

  @Test
  void checkStatus_unacceptableMissingDescriptionAndTotal() {
    List<DetailRow> details = new ArrayList<>();
    details.add(new DetailRow(38, null, null, "  ", null)); // blank description + null total
    stubCheckStatus("N", details);

    Schedule3CheckStatusResponse r = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Included Unacceptable Costs (Description)"));
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Included Unacceptable Costs (Total $)"));
  }
}
