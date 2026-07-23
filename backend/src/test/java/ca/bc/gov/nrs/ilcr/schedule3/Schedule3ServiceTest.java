package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 3 document assembly + server-side derivation cascade (AD-5/AD-6),
 * reproducing the legacy {@code Schedule3DO}/{@code CostType} getter arithmetic. Mocked repository —
 * no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule3Repository repository;

  @Mock
  private ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service schedule1Service;

  @Mock
  private org.springframework.context.MessageSource messageSource;

  @InjectMocks
  private Schedule3Service service;

  /** A cost detail row (no comments). */
  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null, null);
  }

  /** A volume detail row. */
  private static DetailRow volume(int code, String vol) {
    return new DetailRow(code, new BigDecimal(vol), null, null, null);
  }

  private void stub(String trackStatus, String location, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1003, location, "comment", 0)));
    lenient().when(repository.findDetails(1003)).thenReturn(details);
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
  }

  private static CostLine line(Schedule3Response doc, int code) {
    return doc.lineItems().stream().filter(l -> l.costItemCode() == code).findFirst().orElseThrow();
  }

  /** The full hand-checkable document mirroring the V8 seed on 514/2021. */
  private static List<DetailRow> fullDocument() {
    List<DetailRow> rows = new ArrayList<>();
    rows.add(cost(27, 100000));
    rows.add(cost(125, 40000));
    rows.add(cost(28, 50000));
    rows.add(cost(126, 20000));
    rows.add(cost(29, 30000));       // Annual Rents — Harvest-only
    rows.add(cost(30, 285000));
    rows.add(cost(128, 155000));
    rows.add(cost(31, 40000));
    rows.add(cost(129, 10000));
    rows.add(cost(32, 25000));
    rows.add(cost(130, 5000));
    rows.add(cost(33, 60000));       // Scaling — PO&P derived
    rows.add(cost(34, 35000));
    rows.add(cost(132, 15000));
    rows.add(cost(35, 45000));
    rows.add(cost(133, 5000));
    rows.add(cost(36, 80000));
    rows.add(cost(134, 20000));
    rows.add(cost(37, 150000));      // Silviculture Admin — Harvest-only (V5)
    rows.add(volume(118, "54321"));  // PO&P Timber volume
    rows.add(volume(119, "54321"));  // Crown Timber volume
    return rows;
  }

  @Test
  void normalLine_crownIsHarvestMinusPop() {
    stub("D", "N", List.of(cost(27, 100000), cost(125, 40000)));
    CostLine licenses = line(service.getSchedule3(MILL, YEAR, true), 27);
    assertEquals(100000, licenses.harvest());
    assertEquals(40000, licenses.pop());
    assertEquals(60000, licenses.crown());
  }

  @Test
  void harvestOnlyLines_popForcedZero_crownEqualsHarvest() {
    stub("D", "N", List.of(cost(29, 30000), cost(37, 150000)));
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, true);
    assertEquals(0, line(doc, 29).pop());
    assertEquals(30000, line(doc, 29).crown());
    assertEquals(0, line(doc, 37).pop());
    assertEquals(150000, line(doc, 37).crown());
  }

  @Test
  void scalingPop_derivedFromTimberVolumeRatio() {
    // ratio = popTimberVol / (popTimberVol + crownTimberVol) = 54321/108642 = 0.5; pop = 0.5 * 60000.
    stub("D", "N", List.of(cost(33, 60000), volume(118, "54321"), volume(119, "54321")));
    CostLine scaling = line(service.getSchedule3(MILL, YEAR, true), 33);
    assertEquals(60000, scaling.harvest());
    assertEquals(30000, scaling.pop());
    assertEquals(30000, scaling.crown());
  }

  @Test
  void fullDocument_derivedCascadeMatchesLegacy() {
    stub("D", "Y", fullDocument());
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, true);

    // Subtotal Actual = 900000 / 300000 (== the V5-stored 115/135). Totals are Long (overflow-safe).
    assertEquals(900000L, doc.subtotalActualCosts().harvest().longValue());
    assertEquals(300000L, doc.subtotalActualCosts().pop().longValue());
    assertEquals(600000L, doc.subtotalActualCosts().crown().longValue());

    // Included Unacceptable = Annual Rents harvest (no item-38 rows); PO&P forced 0, crown = harvest.
    assertEquals(30000L, doc.includedUnacceptableCosts().harvest().longValue());
    assertEquals(0L, doc.includedUnacceptableCosts().pop().longValue());
    assertEquals(30000L, doc.includedUnacceptableCosts().crown().longValue());

    // Total Costs = Subtotal Actual − Included Unacceptable.
    assertEquals(870000L, doc.totalCosts().harvest().longValue());
    assertEquals(300000L, doc.totalCosts().pop().longValue());
    assertEquals(570000L, doc.totalCosts().crown().longValue());

    // Timber costs pushed from Total Costs; overhead sums the two.
    assertEquals(300000L, doc.popTimber().cost().longValue());
    assertEquals(570000L, doc.crownTimber().cost().longValue());
    assertEquals(870000L, doc.totalOverhead().cost().longValue());
    assertEquals(0, new BigDecimal("54321").compareTo(doc.popTimber().volume()));
    assertEquals(0, new BigDecimal("108642").compareTo(doc.totalOverhead().volume()));

    // perUnit = cost/volume at scale 2 HALF_UP (legacy CoreUtil.bigDecimalDivision).
    assertEquals(0, new BigDecimal("5.52").compareTo(doc.popTimber().perUnit()));
    assertEquals(0, new BigDecimal("10.49").compareTo(doc.crownTimber().perUnit()));
    assertEquals(0, new BigDecimal("8.01").compareTo(doc.totalOverhead().perUnit()));

    // Override read from summary LOCATION; counts.
    assertEquals("Y", doc.overrideHarvestTotalPop());
    assertEquals(0, doc.otherAcceptableCount());
    assertEquals(1, doc.unacceptableCount());  // +1 for the non-zero Annual Rents harvest
  }

  @Test
  void unacceptableCount_addsItem38RowsPlusAnnualRents() {
    List<DetailRow> rows = new ArrayList<>();
    rows.add(cost(29, 30000));                                   // Annual Rents present → +1
    rows.add(new DetailRow(38, null, 1000, "Fine A", null));    // two item-38 rows
    rows.add(new DetailRow(38, null, 2000, "Fine B", null));
    stub("D", "N", rows);
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, true);
    assertEquals(3, doc.unacceptableCount());
    // Included Unacceptable harvest = 1000 + 2000 + 30000 (Annual Rents).
    assertEquals(33000, doc.includedUnacceptableCosts().harvest());
  }

  @Test
  void unacceptableCount_noAnnualRents_noPlusOne() {
    stub("D", "N", List.of(new DetailRow(38, null, 1000, "Fine A", null)));
    assertEquals(1, service.getSchedule3(MILL, YEAR, true).unacceptableCount());
  }

  @Test
  void otherAcceptableCosts_groupedByCommentsKey() {
    // Two groups (GRP1/GRP2), each a TOT row (harvest) + a POP row.
    List<DetailRow> rows = List.of(
        new DetailRow(124, null, 10000, "Fuel", "SCH3_2_TOT_GRP1"),
        new DetailRow(124, null, 4000, "Fuel", "SCH3_2_POP_GRP1"),
        new DetailRow(124, null, 6000, "Tools", "SCH3_2_TOT_GRP2"),
        new DetailRow(124, null, 1000, "Tools", "SCH3_2_POP_GRP2"));
    stub("D", "N", rows);
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, true);
    assertEquals(2, doc.otherAcceptableCount());
    assertEquals(16000L, doc.subtotalOtherCosts().harvest().longValue());   // 10000 + 6000
    assertEquals(5000L, doc.subtotalOtherCosts().pop().longValue());        // 4000 + 1000
    assertEquals(11000L, doc.subtotalOtherCosts().crown().longValue());
  }

  @Test
  void overrideDefaultsToN_whenLocationNull() {
    stub("D", null, List.of());
    assertEquals("N", service.getSchedule3(MILL, YEAR, true).overrideHarvestTotalPop());
  }

  @Test
  void emptySchedule_subtotalsAreZero() {
    stub("D", "N", List.of());
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, true);
    assertEquals(0L, doc.subtotalActualCosts().harvest().longValue());
    assertEquals(0L, doc.subtotalActualCosts().pop().longValue());
    assertEquals(0L, doc.totalCosts().harvest().longValue());
    assertEquals(0, doc.unacceptableCount());
    assertTrue(doc.lineItems().isEmpty());
    assertNull(doc.popTimber().volume());
    assertNull(doc.popTimber().perUnit());
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stub("D", "N", List.of());
    assertTrue(service.getSchedule3(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stub("S", "N", List.of());
    assertFalse(service.getSchedule3(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stub("D", "N", List.of());
    assertFalse(service.getSchedule3(MILL, YEAR, false).editable());
  }
}
