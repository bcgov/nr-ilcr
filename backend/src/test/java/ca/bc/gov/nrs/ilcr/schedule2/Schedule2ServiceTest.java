package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 2 document assembly + server-side derivation (AD-5/AD-6). Mocked
 * repository + Schedule 3 service — no DB, no Spring. Covers the Schedule2MB formula set, the
 * carried Schedule-3 figures (PO&amp;P/Crown timber volumes + Subtotal Actual Costs PO&amp;P/Crown
 * columns + Silviculture Admin crown, all sourced from the Schedule 3 computed document), null
 * propagation when Schedule 3 is absent (getSchedule3 → 404 → Schedule 2 never 404s), editability,
 * and the unsaved path.
 */
@ExtendWith(MockitoExtension.class)
class Schedule2ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule2Repository repository;

  @Mock private Schedule1Service schedule1Service;

  @Mock private Schedule3Service schedule3Service;

  @InjectMocks private Schedule2Service service;

  /** A Schedule 1 document mock exposing only the two figures Schedule 2 reads. */
  private Schedule1Response sch1Doc(long subtotalCompanyLoggingWithFma, Long forestMgmtAdminCost) {
    Schedule1Response sch1 = mock(Schedule1Response.class);
    lenient().when(sch1.subtotalCompanyLoggingCost()).thenReturn(subtotalCompanyLoggingWithFma);
    lenient().when(sch1.forestMgmtAdminCost()).thenReturn(forestMgmtAdminCost);
    return sch1;
  }

  /**
   * A Schedule 3 document exposing only the figures Schedule 2 carries: PO&amp;P + Crown timber
   * volumes, the Subtotal Actual Costs PO&amp;P/Crown columns, and the item-37 Silviculture Admin
   * line (Harvest-only → crown = its cost). Everything else is null/zero — Schedule 2 never reads
   * it.
   */
  private static Schedule3Response sch3Doc(
      BigDecimal popVolume,
      long popActualCost,
      BigDecimal crownVolume,
      long subtotalActualsCrown,
      Integer silvAdminCrown) {
    TimberBlock popTimber = new TimberBlock(popVolume, null, null);
    TimberBlock crownTimber = new TimberBlock(crownVolume, null, null);
    ThreeColumnTotal subtotalActual =
        new ThreeColumnTotal(null, popActualCost, subtotalActualsCrown);
    ThreeColumnTotal zero = new ThreeColumnTotal(0L, 0L, 0L);
    List<CostLine> lineItems =
        silvAdminCrown == null
            ? List.of()
            : List.of(new CostLine(37, silvAdminCrown, 0, silvAdminCrown));
    return new Schedule3Response(
        MILL,
        YEAR,
        "D",
        false,
        0,
        null,
        null,
        lineItems,
        popTimber,
        crownTimber,
        null,
        zero,
        subtotalActual,
        zero,
        zero,
        0,
        0,
        List.of(),
        null);
  }

  /** Full Draft fixture: Schedule 2 items 25/26 + Schedule 3 carried figures + Schedule 1 terms. */
  private void stubFullDraft() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.of(new SummaryRow(1002, "c", 0)));
    lenient()
        .when(repository.findDetails(1002))
        .thenReturn(
            List.of(
                new DetailRow(25, null, 500000),
                new DetailRow(26, new BigDecimal("2000"), 100000)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    // Sch3: popVol 10000, PO&P actual cost (Subtotal Actual Costs PO&P col) 20000, crownVol 12345,
    // Subtotal Actual Costs Crown col 100000, Silviculture Admin (item 37) crown 5000.
    lenient()
        .when(schedule3Service.getSchedule3(MILL, YEAR, false))
        .thenReturn(sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000));
    // Sch1 computed subtotal company logging (no FMA) = 617250 (subtotalWithFma 617250, FMA 0).
    // Build the Sch1 mock BEFORE the outer stub — nesting when() inside .thenReturn() breaks
    // Mockito.
    Schedule1Response sch1 = sch1Doc(617250L, 0L);
    lenient().when(schedule1Service.getSchedule1(MILL, YEAR, false)).thenReturn(sch1);
    lenient()
        .when(repository.findSch1SilvActualSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(20000));
    lenient()
        .when(repository.findSch1SilvAccruedSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(8450));
  }

  /** No Schedule 3 (getSchedule3 → 404) and no Schedule 1 cross-figures. */
  private void stubNoCrossSchedule(
      String trackStatus, Optional<SummaryRow> summary, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR)).thenReturn(summary);
    summary.ifPresent(
        s -> lenient().when(repository.findDetails(s.summaryId())).thenReturn(details));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    lenient()
        .when(schedule3Service.getSchedule3(MILL, YEAR, false))
        .thenThrow(new ScheduleNotFoundException());
    lenient()
        .when(schedule1Service.getSchedule1(MILL, YEAR, false))
        .thenThrow(new ScheduleNotFoundException());
  }

  private static void eq(String expected, BigDecimal actual) {
    assertEquals(
        0,
        new BigDecimal(expected).compareTo(actual),
        () -> "expected " + expected + " but was " + actual);
  }

  @Test
  void storedLineItems_mappedFrom25And26() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertEquals(500000, doc.purchasedLogCost().cost()); // item 25 cost
    eq("2000", doc.lessLogSales().volume()); // item 26 volume
    assertEquals(100000, doc.lessLogSales().cost()); // item 26 cost
    assertEquals("c", doc.comments());
    assertEquals(0, doc.revisionCount());
  }

  @Test
  void carriedFigures_fromSchedule3() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    // purchasedLogCost.volume and purchasedWoodOverhead.volume both = Sch3 PO&P timber volume
    // (BR-03).
    eq("10000", doc.purchasedLogCost().volume());
    eq("10000", doc.purchasedWoodOverhead().volume());
    // purchasedWoodOverhead.cost = Sch3 Subtotal Actual Costs PO&P column (computed subtotal).
    assertEquals(20000, doc.purchasedWoodOverhead().cost());
    eq("12345", doc.totalCompanyLogging().volume()); // Sch3 Crown timber volume
    // totalCompanyLogging.cost = 617250 (Sch1 144) + 100000 (Sch3 actual-costs crown)
    //   + ((20000 Sch1 silvActual − 5000 Sch3 silvAdmin crown) + 8450 Sch1 silvAccrued) = 740700.
    assertEquals(740700, doc.totalCompanyLogging().cost());
  }

  @Test
  void derivedFigures_computedByServer() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    eq("50.0", doc.purchasedLogCost().perUnit()); // 500000/10000
    eq("2.0", doc.purchasedWoodOverhead().perUnit()); // 20000/10000
    // subtotal: cost 500000+20000=520000, vol 10000, perUnit 52.0
    assertEquals(520000, doc.subtotal().cost());
    eq("52.0", doc.subtotal().perUnit());
    eq("50.0", doc.lessLogSales().perUnit()); // 100000/2000
    // netPurchased: vol 10000-2000=8000, cost 520000-100000=420000, perUnit 52.5
    eq("8000", doc.netPurchased().volume());
    assertEquals(420000, doc.netPurchased().cost());
    eq("52.5", doc.netPurchased().perUnit());
    eq("60.0", doc.totalCompanyLogging().perUnit()); // 740700/12345
    // totalAverage: vol 8000+12345=20345, cost 420000+740700=1160700
    eq("20345", doc.totalAverage().volume());
    assertEquals(1160700, doc.totalAverage().cost());
  }

  @Test
  void totalCompanyLogging_usesSchedule1SubtotalMinusFma_notRawSubtotal() {
    // The no-FMA logging subtotal Schedule 2 consumes is Schedule 1's subtotalCompanyLoggingCost
    // (which INCLUDES Forest Management Admin) MINUS forestMgmtAdminCost. The stubFullDraft fixture
    // uses FMA=0, so the subtraction is a no-op there. Here FMA is non-zero: a regression that fed
    // the
    // raw subtotal into the total would inflate it by exactly the FMA (823450 instead of 740700).
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.of(new SummaryRow(1002, "c", 0)));
    lenient()
        .when(repository.findDetails(1002))
        .thenReturn(
            List.of(
                new DetailRow(25, null, 500000),
                new DetailRow(26, new BigDecimal("2000"), 100000)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient()
        .when(schedule3Service.getSchedule3(MILL, YEAR, false))
        .thenReturn(sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000));
    // subtotalWithFma 700000, FMA 82750 -> no-FMA subtotal 617250 (same downstream total as the
    // FMA=0
    // fixture, isolating the subtraction as the only thing under test).
    Schedule1Response sch1 = sch1Doc(700000L, 82750L);
    lenient().when(schedule1Service.getSchedule1(MILL, YEAR, false)).thenReturn(sch1);
    lenient()
        .when(repository.findSch1SilvActualSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(20000));
    lenient()
        .when(repository.findSch1SilvAccruedSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(8450));

    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    // 617250 (700000 − 82750) + 100000 crown + ((20000 − 5000) + 8450) = 740700, NOT 823450.
    assertEquals(740700, doc.totalCompanyLogging().cost());
  }

  @Test
  void absentSchedule3_dependentFiguresNull() {
    // Sch2 present, but Schedule 3 absent (404) and no Sch1 144 -> carried/derived dependents null.
    stubNoCrossSchedule(
        "D",
        Optional.of(new SummaryRow(1028, "c", 3)),
        List.of(new DetailRow(25, null, 333000), new DetailRow(26, new BigDecimal("500"), 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertNull(doc.purchasedLogCost().volume()); // no Sch3 PO&P timber volume
    assertNull(doc.purchasedLogCost().perUnit()); // cost present but volume null
    assertNull(doc.purchasedWoodOverhead().cost()); // no Sch3 actual-costs PO&P
    assertNull(doc.totalCompanyLogging().volume()); // no Crown
    assertNull(doc.totalCompanyLogging().cost()); // no Sch1 144, no Sch3 crown
    // subtotal cost = item25 (333000) + null PO&P -> item25 cost only (CoreUtil addition).
    assertEquals(333000, doc.subtotal().cost());
    // lessLogSales still derived from stored 26.
    eq("50.0", doc.lessLogSales().perUnit()); // 25000/500
  }

  @Test
  void unsavedSchedule_returnsEmptyEditableDocument() {
    // No category-"2" summary, Draft track, no Sch3 -> empty editable doc, no NPE, no 404.
    stubNoCrossSchedule("D", Optional.empty(), List.of());
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertTrue(doc.editable());
    assertNull(doc.revisionCount());
    assertNull(doc.comments());
    assertNull(doc.purchasedLogCost().cost());
    assertNull(doc.lessLogSales().volume());
    assertNull(doc.subtotal().cost());
    assertNull(doc.totalAverage().volume());
  }

  @Test
  void unsavedSchedule_carriedSch3FiguresStillPopulated() {
    // No Sch2 summary but Sch3 data exists -> carried figures still present (AC6).
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(schedule3Service.getSchedule3(MILL, YEAR, false))
        .thenReturn(sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000));
    lenient()
        .when(schedule1Service.getSchedule1(MILL, YEAR, false))
        .thenThrow(new ScheduleNotFoundException());
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    eq("10000", doc.purchasedWoodOverhead().volume());
    assertEquals(20000, doc.purchasedWoodOverhead().cost());
    assertNull(doc.purchasedLogCost().cost()); // still unsaved item 25
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stubNoCrossSchedule("D", Optional.empty(), List.of());
    assertTrue(service.getSchedule2(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stubNoCrossSchedule("S", Optional.of(new SummaryRow(1028, "c", 3)), List.of());
    assertFalse(service.getSchedule2(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stubNoCrossSchedule("D", Optional.empty(), List.of());
    assertFalse(service.getSchedule2(MILL, YEAR, false).editable());
  }

  @Test
  void perUnit_nullWhenVolumeZero() {
    stubNoCrossSchedule(
        "D",
        Optional.of(new SummaryRow(1028, "c", 0)),
        List.of(new DetailRow(26, BigDecimal.ZERO, 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertNull(doc.lessLogSales().perUnit());
  }

  @Test
  void perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient() {
    // 200000 / 30000 = 6.66666... Exact-string assertion (compareTo is scale-insensitive and would
    // pass on a broken scale). Scale-4 HALF_UP -> 6.6667; truncation/DOWN would give 6.6666. This
    // is
    // the only test that actually exercises the rounding frozen into the wire contract.
    stubNoCrossSchedule(
        "D",
        Optional.of(new SummaryRow(1028, "c", 0)),
        List.of(new DetailRow(26, new BigDecimal("30000"), 200000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertEquals("6.6667", doc.lessLogSales().perUnit().toPlainString());
  }
}
