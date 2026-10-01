package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckRequest.LineEntry;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 3 Check Status logic (Story 4.2, BR-11/BR-03/BR-10). Mocked repository
 * + message source — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3CheckStatusServiceTest {

  private static final long MILL = 572L;
  private static final int YEAR = 2021;

  @Mock private Schedule3Repository repository;

  @Mock private Schedule1Service schedule1Service;

  @Mock private MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule3Service service;

  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null, null);
  }

  private static DetailRow volume(int code, String vol) {
    return new DetailRow(code, new BigDecimal(vol), null, null, null);
  }

  /**
   * A complete, valid document: all 11 Harvest present, 8 PO&P present with harvest ≥ pop, volumes.
   */
  private static List<DetailRow> fullValid() {
    List<DetailRow> rows = new ArrayList<>();
    for (int harvest : new int[] {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37}) {
      rows.add(cost(harvest, 1000));
    }
    for (int pop : new int[] {125, 126, 128, 129, 130, 132, 133, 134}) {
      rows.add(cost(pop, 500));
    }
    rows.add(volume(118, "1000"));
    rows.add(volume(119, "1000"));
    return rows;
  }

  /**
   * Defect #296: Check Status no longer 404s on a mill/year with no summary. Distinct from the
   * existing empty-document test, which has a summary PRESENT with no detail rows — this is the
   * absent-summary branch the fix added, including the {@code override} guard that must not NPE.
   */
  @Test
  void checkStatus_noSummary_reportsMissingFields_ratherThanThrowing() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());

    Schedule3CheckStatusResponse res = service.checkStatusStored(MILL, YEAR);

    assertFalse(res.requirementsMet(), "a schedule with nothing entered cannot be complete");
    assertFalse(res.errors().isEmpty(), "every mandatory field must be reported missing");
    verify(repository, never()).findDetails(anyInt());
  }

  private void stub(String location, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1044, location, "c", 0)));
    when(repository.findDetails(1044)).thenReturn(details);
    // Which message keys resolve depends on the scenario, so keep these lenient.
    lenient()
        .when(
            messageSource.getMessage(
                eq("missingRequiredFieldMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value Required");
    lenient()
        .when(
            messageSource.getMessage(
                eq("harvestNotGreaterThanPopErrorMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value must be greater than or equal to the corresponding PO&P Cost");
    lenient()
        .when(
            messageSource.getMessage(
                eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");
  }

  private static boolean hasError(
      Schedule3CheckStatusResponse r, String key, String labelFragment) {
    return r.errors().stream()
        .anyMatch(m -> m.key().equals(key) && m.text().contains(labelFragment));
  }

  @Test
  void allPresentValid_requirementsMet() {
    stub("N", fullValid());
    Schedule3CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertTrue(result.requirementsMet());
    assertTrue(result.errors().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", result.message().key());
  }

  @Test
  void emptyDocument_reportsMissingRequiredFields() {
    stub("N", List.of());
    Schedule3CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertFalse(result.requirementsMet());
    assertTrue(
        hasError(result, "missingRequiredFieldMsg", "Licenses, Fees, Insurance (Harvest Total $)"));
    assertTrue(hasError(result, "missingRequiredFieldMsg", "Annual Rents (Harvest Total $)"));
    assertTrue(hasError(result, "missingRequiredFieldMsg", "Crown Timber Harvest (Volume m³)"));
    // Harvest-only lines never emit a PO&P-required error.
    assertFalse(hasError(result, "missingRequiredFieldMsg", "Annual Rents (PO&P $)"));
  }

  @Test
  void harvestLessThanPop_reportsBr03Violation() {
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.removeIf(r -> r.costItemCode() == 27 || r.costItemCode() == 125);
    rows.add(cost(27, 100)); // Licenses harvest 100 < pop 500 → BR-03 violation
    rows.add(cost(125, 500));
    stub("N", rows);
    Schedule3CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertFalse(result.requirementsMet());
    assertTrue(
        hasError(
            result,
            "harvestNotGreaterThanPopErrorMsg",
            "Licenses, Fees, Insurance (Harvest Total $)"));
  }

  @Test
  void overrideYes_suppressesBr03OnAllLines() {
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.removeIf(r -> r.costItemCode() == 27 || r.costItemCode() == 125);
    rows.add(cost(27, 100)); // harvest < pop, but override = "Y"
    rows.add(cost(125, 500));
    stub("Y", rows);
    Schedule3CheckStatusResponse result = service.checkStatusStored(MILL, YEAR);
    assertTrue(result.requirementsMet()); // BR-03 suppressed; everything else present
    assertFalse(
        hasError(
            result,
            "harvestNotGreaterThanPopErrorMsg",
            "Licenses, Fees, Insurance (Harvest Total $)"));
  }

  // ---------------------------------------------------------------------------------------------
  // The payload path (#359): the endpoint judges the SCREEN. The body's fixed lines, timber volumes
  // and Override replace the stored ones; the item-124/38 sub-page rows stay database-sourced.
  // ---------------------------------------------------------------------------------------------

  private static final int[] HARVEST_CODES = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
  private static final Map<Integer, Integer> POP_CODE_OF =
      Map.of(27, 125, 28, 126, 30, 128, 31, 129, 32, 130, 34, 132, 35, 133, 36, 134);

  /**
   * The screen that mirrors a set of stored rows: every fixed line's Harvest, its PO&amp;P read
   * from the PO&amp;P row (null for the Harvest-only lines), and both timber volumes — exactly what
   * the page seeds its form from.
   */
  private static Schedule3CheckRequest screenOf(String override, List<DetailRow> rows) {
    Map<Integer, DetailRow> byCode = new HashMap<>();
    rows.forEach(r -> byCode.putIfAbsent(r.costItemCode(), r));
    List<LineEntry> lines = new ArrayList<>();
    for (int code : HARVEST_CODES) {
      DetailRow harvest = byCode.get(code);
      Integer popCode = POP_CODE_OF.get(code);
      DetailRow pop = popCode == null ? null : byCode.get(popCode);
      lines.add(
          new LineEntry(
              code, harvest == null ? null : harvest.cost(), pop == null ? null : pop.cost()));
    }
    DetailRow popTimber = byCode.get(118);
    DetailRow crownTimber = byCode.get(119);
    return new Schedule3CheckRequest(
        override,
        lines,
        popTimber == null ? null : popTimber.volume(),
        crownTimber == null ? null : crownTimber.volume());
  }

  /** {@link #fullValid} with one code's cost replaced. */
  private static List<DetailRow> fullValidWith(int code, Integer amount) {
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.replaceAll(r -> r.costItemCode() == code ? cost(code, amount) : r);
    return rows;
  }

  @Test
  void payload_clearedHarvest_isReported_evenThoughStoredHasIt() {
    // S25: Office Expense Harvest cleared on screen; Oracle still holds 1000.
    stub("N", fullValid());
    Schedule3CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, screenOf("N", fullValidWith(32, null)));

    assertFalse(r.requirementsMet());
    assertNull(r.message(), "no MET line beside a finding");
    assertEquals(
        List.of("Office Expense (Harvest Total $): Value Required"),
        r.errors().stream().map(m -> m.text()).toList());
  }

  @Test
  void payload_suppliedHarvest_clearsTheFinding_andNothingElseChanges() {
    // S26: Oracle has no Wages Harvest; 60000 is typed on screen.
    List<DetailRow> stored = new ArrayList<>(fullValid());
    stored.removeIf(r -> r.costItemCode() == 30);
    stub("N", stored);
    Schedule3CheckStatusResponse storedVerdict = service.checkStatusStored(MILL, YEAR);
    Schedule3CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, screenOf("N", fullValidWith(30, 60000)));

    assertTrue(hasError(storedVerdict, "missingRequiredFieldMsg", "Wages/Salaries, incl Benefits"));
    assertTrue(r.requirementsMet());
    assertTrue(r.errors().isEmpty());
  }

  @Test
  void payload_typedZero_isPresent() {
    stub("N", fullValid());
    Schedule3CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, screenOf("N", fullValidWith(29, 0)));

    assertTrue(r.requirementsMet(), "0 is present — null test, not truthiness");
  }

  /**
   * S12: the stored Override is "Y", but the screen has switched it to "N" without saving. The
   * on-screen value drives BOTH Harvest&lt;PO&amp;P rules — the fixed-line one AND the item-124
   * subtotal one, whose rows come from the database.
   */
  @Test
  void payload_overrideSwitchedToNo_raisesBothHarvestLessThanPopFindings() {
    List<DetailRow> rows = new ArrayList<>(fullValidWith(27, 100)); // 100 < PO&P 500
    rows.add(oa(100, "Consulting", "SCH3_2_TOT_GRP1"));
    rows.add(oa(500, "Consulting", "SCH3_2_POP_GRP1"));
    stub("Y", rows);

    Schedule3CheckStatusResponse saved = service.checkStatusStored(MILL, YEAR);
    Schedule3CheckStatusResponse onScreen = service.checkStatus(MILL, YEAR, screenOf("N", rows));

    assertTrue(saved.requirementsMet(), "the stored Override suppresses both");
    assertEquals(
        List.of(
            "Licenses, Fees, Insurance (Harvest Total $): "
                + "Value must be greater than or equal to the corresponding PO&P Cost",
            "Subtotal Other Costs (Harvest Total $): "
                + "Value must be greater than or equal to the corresponding PO&P Cost"),
        onScreen.errors().stream().map(m -> m.text()).toList());
  }

  @Test
  void payload_overrideSwitchedToYes_suppressesBoth() {
    List<DetailRow> rows = new ArrayList<>(fullValidWith(27, 100));
    rows.add(oa(100, "Consulting", "SCH3_2_TOT_GRP1"));
    rows.add(oa(500, "Consulting", "SCH3_2_POP_GRP1"));
    stub("N", rows);

    assertTrue(service.checkStatus(MILL, YEAR, screenOf("Y", rows)).requirementsMet());
    // Anything but "Y" is not an override (normalizeOverride), a null included.
    assertFalse(service.checkStatus(MILL, YEAR, screenOf(null, rows)).requirementsMet());
    assertFalse(service.checkStatus(MILL, YEAR, screenOf("y", rows)).requirementsMet());
  }

  @Test
  void payload_subPageRows_areReportedExactlyAsStored() {
    // The screen carries no sub-page rows; incomplete item-124/38 rows in Oracle still report.
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.add(oa(null, "  ", "SCH3_2_TOT_GRP1"));
    rows.add(new DetailRow(38, null, null, "  ", null));
    stub("N", rows);

    Schedule3CheckStatusResponse r = service.checkStatus(MILL, YEAR, screenOf("N", fullValid()));

    assertEquals(
        List.of(
            "Subtotal Other Costs (Description): Value Required",
            "Subtotal Other Costs (Harvest Total $): Value Required",
            "Subtotal Other Costs (PO&P $): Value Required",
            "Included Unacceptable Costs (Description): Value Required",
            "Included Unacceptable Costs (Total $): Value Required"),
        r.errors().stream().map(m -> m.text()).toList());
  }

  @Test
  void payload_screenValuesAreNeverTakenFromTheDatabase() {
    // Oracle is complete, the screen is empty: every fixed line and both volumes are reported.
    stub("N", fullValid());
    Schedule3CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, new Schedule3CheckRequest("N", null, null, null));

    stub("N", List.of());
    assertEquals(service.checkStatusStored(MILL, YEAR), r);
  }

  @Test
  void payload_noSummary_isCheckable() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    lenient()
        .when(
            messageSource.getMessage(
                eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");

    Schedule3CheckStatusResponse r = service.checkStatus(MILL, YEAR, screenOf("N", fullValid()));

    assertTrue(r.requirementsMet(), "an unsaved schedule fully entered on screen is complete");
    verify(repository, never()).findDetails(anyInt());
  }

  /**
   * One evaluator, two sources (AD-5): a screen that mirrors the record yields a byte-identical
   * verdict on both paths, across every branch — MET, missing Harvest/PO&amp;P/volumes, the
   * fixed-line and subtotal Harvest&lt;PO&amp;P under both Override values, and the sub-pages.
   */
  @Test
  void storedAndPayload_agree_whenTheScreenMirrorsTheRecord() {
    List<DetailRow> withSubPages = new ArrayList<>(fullValidWith(28, 100));
    withSubPages.add(oa(100, "Consulting", "SCH3_2_TOT_GRP1"));
    withSubPages.add(oa(500, "Consulting", "SCH3_2_POP_GRP1"));
    withSubPages.add(oa(null, null, "SCH3_2_TOT_GRP2"));
    withSubPages.add(new DetailRow(38, null, null, "  ", null));
    List<DetailRow> missingPopAndVolume = new ArrayList<>(fullValid());
    missingPopAndVolume.removeIf(r -> r.costItemCode() == 130 || r.costItemCode() == 118);
    List<List<DetailRow>> fixtures =
        List.of(fullValid(), List.of(), withSubPages, missingPopAndVolume, fullValidWith(36, 0));
    for (String override : new String[] {"N", "Y"}) {
      for (List<DetailRow> rows : fixtures) {
        lenient()
            .when(repository.findSummary(MILL, YEAR))
            .thenReturn(Optional.of(new SummaryRow(1044, override, "c", 0)));
        lenient().when(repository.findDetails(1044)).thenReturn(rows);
        stubMessagesLeniently();

        assertEquals(
            service.checkStatusStored(MILL, YEAR),
            service.checkStatus(MILL, YEAR, screenOf(override, rows)),
            () -> "override=" + override + " rows=" + rows);
      }
    }
  }

  private void stubMessagesLeniently() {
    lenient()
        .when(
            messageSource.getMessage(
                eq("missingRequiredFieldMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value Required");
    lenient()
        .when(
            messageSource.getMessage(
                eq("harvestNotGreaterThanPopErrorMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value must be greater than or equal to the corresponding PO&P Cost");
    lenient()
        .when(
            messageSource.getMessage(
                eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");
  }

  private static DetailRow oa(Integer amount, String desc, String comments) {
    return new DetailRow(124, null, amount, desc, comments);
  }
}
