package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation.Schedule1Sources;
import java.math.BigDecimal;
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
 * Unit test for the Schedule 1 document assembly + server-side derivation (AD-5/AD-6), including the
 * Story 2.3 BR-03 Crown Timber pre-fill and BR-04 Schedule 3 admin-cost pulls. Mocked repository —
 * no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final String WARN_CROWN = "crownVolumeSetForSchedule1";
  private static final String WARN_TEXT =
      "The Crown Timber (Sch 3) volume has been set for volume fields. Please check and save schedule.";

  @Mock
  private Schedule1Repository repository;

  @Mock
  private Schedule3CostDerivation schedule3CostDerivation;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule1Service service;

  /** Stub the Schedule 1 side (summary + details + track). Schedule 3 defaults to empty (no pull). */
  private void stub(String trackStatus, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(1001, 12345, "c", 3)));
    lenient().when(repository.findDetails(1001)).thenReturn(details);
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    // Schedule 3 defaults to absent: no crown, no pulled admin costs (overridden per-test below).
    lenient().when(schedule3CostDerivation.schedule1Sources(MILL, YEAR))
        .thenReturn(new Schedule1Sources(null, null, null));
  }

  /**
   * Stub the Schedule-3-derived sources Schedule 1 reads (BR-03 crown volume, BR-04 Less Silv Admin
   * cost, BR-04 Forest Mgmt Admin crown cost) — the values {@link Schedule3CostDerivation} computes.
   */
  private void stubSchedule3(BigDecimal crownVolume, Integer silvAdminCost, Long forestMgmtAdminCost) {
    lenient().when(schedule3CostDerivation.schedule1Sources(MILL, YEAR))
        .thenReturn(new Schedule1Sources(crownVolume, silvAdminCost, forestMgmtAdminCost));
  }

  private void stubWarningText() {
    lenient().when(messageSource.getMessage(eq(WARN_CROWN), any(), any(), any(Locale.class)))
        .thenReturn(WARN_TEXT);
  }

  private LineItem lineItem(Schedule1Response doc, int code) {
    return doc.lineItems().stream().filter(li -> li.costItemCode() == code).findFirst().orElseThrow();
  }

  @Test
  void perUnit_isCostOverVolume_asDecimal() {
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000.0000"), 50000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    // 50000 / 1000 = 50.0 (kept as a decimal, scale >= 1)
    assertEquals(0, new BigDecimal("50.0").compareTo(lineItem(doc, 12).perUnit()));
  }

  @Test
  void perUnit_nullOrZeroVolume_isNull() {
    stub("D", List.of(
        new DetailRow(12, BigDecimal.ZERO, 50000, null),
        new DetailRow(13, null, 40000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertNull(lineItem(doc, 12).perUnit());
    assertNull(lineItem(doc, 13).perUnit());
  }

  @Test
  void otherCosts_subtotalCountPerUnit_fromItem19Rows() {
    stub("D", List.of(
        new DetailRow(19, new BigDecimal("8000"), null, null),   // shared volume row
        new DetailRow(19, null, 12000, "Row A"),                 // itemized
        new DetailRow(19, null, 12000, "Row B")));               // itemized
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(0, new BigDecimal("8000").compareTo(doc.otherCosts().volume()));
    assertEquals(24000L, doc.otherCosts().costSubtotal());
    assertEquals(2, doc.otherCosts().count());
    assertEquals(0, new BigDecimal("3.0").compareTo(doc.otherCosts().perUnit()));
  }

  @Test
  void otherCosts_whitespaceDescription_countsAsItemized_matchesLegacy() {
    // Legacy isNullOrEmptyString treats only null/"" as the shared (non-itemized) row; a
    // whitespace-only description is an itemized row (isNotEmpty), so it counts toward N and its cost
    // toward the subtotal — not folded into the shared-volume row.
    stub("D", List.of(
        new DetailRow(19, new BigDecimal("1000"), null, null),  // shared volume row (null desc)
        new DetailRow(19, null, 500, "   ")));                   // whitespace desc -> itemized
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(1, doc.otherCosts().count());
    assertEquals(500L, doc.otherCosts().costSubtotal());
    assertEquals(0, new BigDecimal("1000").compareTo(doc.otherCosts().volume()));
  }

  @Test
  void otherCosts_sharedRowWithNullVolume_readsAsNullVolume_notAnNpe() {
    // Regression: a stored shared row (null description) carrying no volume 500'd the whole GET —
    // toOtherCosts mapped to the nullable volume BEFORE findFirst(), and Stream.findFirst() throws NPE
    // when the selected element is itself null. The shared volume must simply read back as null while
    // the itemized rows still aggregate (perUnit null — no volume to divide by).
    stub("D", List.of(
        new DetailRow(19, null, null, null),        // shared row, volume never entered / cleared
        new DetailRow(19, null, 12000, "Row A")));  // itemized
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertNull(doc.otherCosts().volume());
    assertEquals(12000L, doc.otherCosts().costSubtotal());
    assertEquals(1, doc.otherCosts().count());
    assertNull(doc.otherCosts().perUnit());
  }

  @Test
  void otherCosts_alwaysPresent_whenNoItem19Rows() {
    // A schedule with no Other Costs still carries a zero-summary (present, not null) so the
    // client can tell "zero" from "missing".
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000"), 50000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(0, doc.otherCosts().count());
    assertEquals(0L, doc.otherCosts().costSubtotal());
    assertNull(doc.otherCosts().volume());
    assertNull(doc.otherCosts().perUnit());
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stub("D", List.of());
    assertTrue(service.getSchedule1(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stub("S", List.of());
    assertFalse(service.getSchedule1(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stub("D", List.of());
    assertFalse(service.getSchedule1(MILL, YEAR, false).editable());
  }

  @Test
  void silvicultureBlock_mappedFromSchedule1Rows() {
    // The silviculture block line items still come from Schedule 1's own detail rows (only the
    // top-level pulled scalars change source — see the BR-04 tests).
    stub("D", List.of(
        new DetailRow(1, new BigDecimal("100"), 500, null),      // silviculture actual
        new DetailRow(139, new BigDecimal("50"), 300, null)));   // less silv admin (block line item)
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(500, doc.silviculture().actualSpent().cost());
    assertEquals(300, doc.silviculture().lessAdmin().cost());
  }

  // ---- Story 2.3: BR-04 Schedule 3 pulls -----------------------------------------------------------

  @Test
  void br04_pulledAdminCosts_comeFromSchedule3_notSchedule1() {
    // Even with 143/139 rows on Schedule 1, the pulled scalars are sourced from Schedule 3's derived
    // Subtotal Actual Costs crown (Forest Mgmt Admin) and item-37 cost (Less Silv Admin).
    stub("D", List.of(
        new DetailRow(143, new BigDecimal("70"), 700, null),
        new DetailRow(139, new BigDecimal("50"), 300, null)));
    stubSchedule3(null, 150000, 600000L);
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(600000, doc.forestMgmtAdminCost());
    assertEquals(150000, doc.lessSilvAdminCost());
  }

  @Test
  void derivedTotals_foldInSchedule3AndLineItems() {
    stub("D", List.of(
        new DetailRow(12, new BigDecimal("100"), 40000, null),   // logging line
        new DetailRow(13, new BigDecimal("50"), 10000, null),    // logging line
        new DetailRow(143, new BigDecimal("1000"), null, null),  // FMA volume (cost pulled from Sch3)
        new DetailRow(144, new BigDecimal("2000"), null, null),  // Subtotal Company Logging volume
        new DetailRow(1, new BigDecimal("10"), 200000, null),    // Silviculture Actual $ Spent
        new DetailRow(2, new BigDecimal("10"), 50000, null),     // Accrued less Actual
        new DetailRow(140, new BigDecimal("500"), null, null))); // Total Silviculture volume
    // crown volume 4000 (prefill won't fire — Sch1 has volumes); FMA 600000; Less Silv Admin 150000.
    stubSchedule3(new BigDecimal("4000"), 150000, 600000L);
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    // Subtotal Company Logging = (40000 + 10000) + 600000 (FMA) + 0 (other costs) = 650000.
    assertEquals(650000L, doc.subtotalCompanyLoggingCost());
    // Total Silviculture = 200000 (actual) − 150000 (Sch3 silv admin) + 50000 (accrued) = 100000.
    assertEquals(100000L, doc.totalSilvicultureCost());
    // Total Company Logging = 650000 + 100000 = 750000; its $/m³ = 750000 / 4000 crown volume = 187.5.
    assertEquals(750000L, doc.totalCompanyLoggingCost());
    assertEquals(0, new BigDecimal("187.5").compareTo(doc.totalCompanyLoggingPerUnit()));
    // Per-unit cells: FMA 600000/1000 = 600.0; Subtotal 650000/2000 = 325.0.
    assertEquals(0, new BigDecimal("600.0").compareTo(doc.forestMgmtAdminPerUnit()));
    assertEquals(0, new BigDecimal("325.0").compareTo(doc.subtotalCompanyLoggingPerUnit()));
  }

  @Test
  void totalSilviculture_blankWhenSilvCostsAbsent_matchesLegacyNullSemantics() {
    // Legacy Schedule1MB.getTotalSilvCost uses CoreUtil null-propagation: with Actual $ Spent (and
    // Accrued) cost blank it returns null (blank) and does NOT subtract the Sch3 admin cost, and a
    // blank Total Silviculture leaves Total Company Logging equal to the subtotal. This is the S02
    // crown-prefill screen (costs blank, Sch3 Less Silv Admin present) — it must show blank, not −admin.
    stub("D", List.of(new DetailRow(12, new BigDecimal("100"), 40000, null))); // logging line, no silv costs
    stubSchedule3(null, 150000, null); // Less Silv Admin (item-37 crown) = 150000; no FMA
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertNull(doc.totalSilvicultureCost());                // blank — NOT -150000
    assertEquals(40000L, doc.subtotalCompanyLoggingCost()); // logging line only
    assertEquals(40000L, doc.totalCompanyLoggingCost());    // subtotal + blank = subtotal, not subtotal-admin
    assertNull(doc.totalSilviculturePerUnit());
  }

  @Test
  void br04_forestMgmtAdmin_derivedSubtotalIsZero_notNull_whenSchedule3Empty() {
    // A Schedule 3 that exists but has no cost lines yields a Subtotal Actual Costs crown of 0 (the
    // subtotal seeds at 0 — legacy sumCostType), NOT null. Null is reserved for "no Schedule 3 at all"
    // (the next test). This distinguishes an opened-but-empty Schedule 3 from a missing one.
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000"), 50000, null)));
    stubSchedule3(null, null, 0L);
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(0L, doc.forestMgmtAdminCost());
    assertNull(doc.lessSilvAdminCost());
  }

  @Test
  void br04_pulledAdminCosts_nullWhenSchedule3Absent() {
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000"), 50000, null)));
    // Default stub → no Schedule 3 summary → all sources null (legacy shows those cells blank).
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertNull(doc.forestMgmtAdminCost());
    assertNull(doc.lessSilvAdminCost());
  }

  // ---- Story 2.3: BR-03 Crown Timber pre-fill (S02) ----------------------------------------------

  @Test
  void br03_prefill_firesWhenAllVolumesEmptyAndSch3CrownPresent() {
    stub("D", List.of());  // first entry: no stored detail rows
    stubSchedule3(new BigDecimal("7777"), null, null);
    stubWarningText();
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);

    // Every savable volume field carries the copied crown value (codes 12-18 + silviculture 1 & 2).
    for (int code : List.of(12, 13, 14, 15, 16, 17, 18)) {
      assertEquals(0, new BigDecimal("7777").compareTo(lineItem(doc, code).volume()));
    }
    assertEquals(0, new BigDecimal("7777").compareTo(doc.silviculture().actualSpent().volume()));
    assertEquals(0, new BigDecimal("7777").compareTo(doc.silviculture().accruedLessActual().volume()));
    assertEquals(0, new BigDecimal("7777").compareTo(doc.schedule3CrownVolume()));
    // WRN-001 rides on warnings with verbatim text (AD-8).
    assertEquals(1, doc.warnings().size());
    assertEquals(WARN_CROWN, doc.warnings().get(0).key());
    assertEquals(WARN_TEXT, doc.warnings().get(0).text());
  }

  @Test
  void br03_prefill_doesNotFireWhenAnyVolumePresent() {
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000"), 50000, null)));  // populated
    stubSchedule3(new BigDecimal("7777"), null, null);
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    // No copy: code 12 keeps its stored 1000; no warning.
    assertEquals(0, new BigDecimal("1000").compareTo(lineItem(doc, 12).volume()));
    assertTrue(doc.warnings().isEmpty());
  }

  @Test
  void br03_prefill_doesNotFireWhenNoSch3Crown() {
    stub("D", List.of());  // empty, but no Schedule 3 crown
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertTrue(doc.warnings().isEmpty());
    assertTrue(doc.lineItems().isEmpty());
    assertNull(doc.schedule3CrownVolume());
  }
}
