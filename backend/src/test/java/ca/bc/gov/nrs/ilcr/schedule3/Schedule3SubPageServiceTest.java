package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import ca.bc.gov.nrs.ilcr.schedule3.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
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

/**
 * Unit test for the Schedule 3 sub-page logic (Story 4.4): item-124 TOT+PO&P group pairing/encoding,
 * next-group-number generation, 404 on an unknown id, the item-38 document, and the check-status
 * sub-page branches (missing description/total/PO&P + S12 Override suppression). Mocked repository —
 * no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3SubPageServiceTest {

  private static final long MILL = 550L;
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

    assertThrows(OtherCostNotFoundException.class, () ->
        service.updateOtherAcceptable(MILL, YEAR, 999999,
            new OtherAcceptableRequest("X", 1, 0), "user"));
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
    assertEquals(250L, doc.subtotalTotal());
    assertEquals(777, doc.annualRentsTotal()); // item-29 Harvest, read-only
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

  private static boolean hasError(CheckStatusResponse r, String key, String labelFragment) {
    return r.errors().stream()
        .anyMatch(m -> m.key().equals(key) && m.text().contains(labelFragment));
  }

  @Test
  void checkStatus_otherAcceptableMissingDescriptionTotalAndPop() {
    // A group with a blank description, null total (TOT), and no PO&P row.
    List<DetailRow> details = new ArrayList<>();
    details.add(oa(null, "  ", "SCH3_2_TOT_GRP1"));
    stubCheckStatus("N", details);

    CheckStatusResponse r = service.checkSchedule3Status(MILL, YEAR);
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
    CheckStatusResponse flagged = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(hasError(flagged, "harvestNotGreaterThanPopErrorMsg",
        "Subtotal Other Costs (Harvest Total $)"));

    stubCheckStatus("Y", new ArrayList<>(details));
    CheckStatusResponse suppressed = service.checkSchedule3Status(MILL, YEAR);
    assertFalse(hasError(suppressed, "harvestNotGreaterThanPopErrorMsg",
        "Subtotal Other Costs (Harvest Total $)"));
  }

  @Test
  void checkStatus_unacceptableMissingDescriptionAndTotal() {
    List<DetailRow> details = new ArrayList<>();
    details.add(new DetailRow(38, null, null, "  ", null)); // blank description + null total
    stubCheckStatus("N", details);

    CheckStatusResponse r = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Included Unacceptable Costs (Description)"));
    assertTrue(hasError(r, "missingRequiredFieldMsg", "Included Unacceptable Costs (Total $)"));
  }
}
