package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
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
 * repository — no DB, no Spring. Covers the Schedule2MB formula set, the carried-figure mapping,
 * null propagation when Schedule 3 data is absent, editability, and the unsaved (empty) path.
 */
@ExtendWith(MockitoExtension.class)
class Schedule2ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule2Repository repository;

  @InjectMocks
  private Schedule2Service service;

  /** Full Draft fixture matching the V5 514/2021 numbers. */
  private void stubFullDraft() {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1002, "c", 0)));
    lenient().when(repository.findDetails(1002)).thenReturn(List.of(
        new DetailRow(25, null, 500000),
        new DetailRow(26, new BigDecimal("2000"), 100000)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findSch3PopTimberVolume(MILL, YEAR))
        .thenReturn(Optional.of(new BigDecimal("10000")));
    lenient().when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.of(20000));
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR))
        .thenReturn(Optional.of(new BigDecimal("12345")));
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR))
        .thenReturn(Optional.of(617250));
  }

  private void stubNoCrossSchedule(String trackStatus, Optional<SummaryRow> summary,
      List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR)).thenReturn(summary);
    summary.ifPresent(s -> lenient().when(repository.findDetails(s.summaryId())).thenReturn(details));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    lenient().when(repository.findSch3PopTimberVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR)).thenReturn(Optional.empty());
  }

  private static void eq(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        () -> "expected " + expected + " but was " + actual);
  }

  @Test
  void storedLineItems_mappedFrom25And26() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertEquals(500000, doc.purchasedLogCost().cost());       // item 25 cost
    eq("2000", doc.lessLogSales().volume());                   // item 26 volume
    assertEquals(100000, doc.lessLogSales().cost());           // item 26 cost
    assertEquals("c", doc.comments());
    assertEquals(0, doc.revisionCount());
  }

  @Test
  void carriedFigures_fromSchedule3() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    // purchasedLogCost.volume and purchasedWoodOverhead.volume both = Sch3 118 (BR-03).
    eq("10000", doc.purchasedLogCost().volume());
    eq("10000", doc.purchasedWoodOverhead().volume());
    assertEquals(20000, doc.purchasedWoodOverhead().cost());   // Sch3 135
    eq("12345", doc.totalCompanyLogging().volume());           // Sch3 Crown (119)
    assertEquals(617250, doc.totalCompanyLogging().cost());    // Sch1 144
  }

  @Test
  void derivedFigures_computedByServer() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    eq("50.0", doc.purchasedLogCost().perUnit());              // 500000/10000
    eq("2.0", doc.purchasedWoodOverhead().perUnit());          // 20000/10000
    // subtotal: cost 500000+20000=520000, vol 10000, perUnit 52.0
    assertEquals(520000, doc.subtotal().cost());
    eq("52.0", doc.subtotal().perUnit());
    eq("50.0", doc.lessLogSales().perUnit());                  // 100000/2000
    // netPurchased: vol 10000-2000=8000, cost 520000-100000=420000, perUnit 52.5
    eq("8000", doc.netPurchased().volume());
    assertEquals(420000, doc.netPurchased().cost());
    eq("52.5", doc.netPurchased().perUnit());
    eq("50.0", doc.totalCompanyLogging().perUnit());           // 617250/12345
    // totalAverage: vol 8000+12345=20345, cost 420000+617250=1037250
    eq("20345", doc.totalAverage().volume());
    assertEquals(1037250, doc.totalAverage().cost());
  }

  @Test
  void absentSchedule3_dependentFiguresNull() {
    // Sch2 present, but no Sch3 118/119/135 and no Sch1 144 -> carried/derived dependents null.
    stubNoCrossSchedule("D", Optional.of(new SummaryRow(1028, "c", 3)),
        List.of(new DetailRow(25, null, 333000), new DetailRow(26, new BigDecimal("500"), 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertNull(doc.purchasedLogCost().volume());               // no Sch3 118
    assertNull(doc.purchasedLogCost().perUnit());              // cost present but volume null
    assertNull(doc.purchasedWoodOverhead().cost());            // no Sch3 135
    assertNull(doc.totalCompanyLogging().volume());            // no Crown
    assertNull(doc.totalCompanyLogging().cost());              // no Sch1 144
    // subtotal cost = item25 (500000-ish) + null 135 -> item25 cost only (CoreUtil addition).
    assertEquals(333000, doc.subtotal().cost());
    // lessLogSales still derived from stored 26.
    eq("50.0", doc.lessLogSales().perUnit());                  // 25000/500
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
    when(repository.findSch3PopTimberVolume(MILL, YEAR))
        .thenReturn(Optional.of(new BigDecimal("10000")));
    when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.of(20000));
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR))
        .thenReturn(Optional.of(new BigDecimal("12345")));
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR)).thenReturn(Optional.empty());
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    eq("10000", doc.purchasedWoodOverhead().volume());
    assertEquals(20000, doc.purchasedWoodOverhead().cost());
    assertNull(doc.purchasedLogCost().cost());                 // still unsaved item 25
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
    stubNoCrossSchedule("D", Optional.of(new SummaryRow(1028, "c", 0)),
        List.of(new DetailRow(26, BigDecimal.ZERO, 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertNull(doc.lessLogSales().perUnit());
  }

  @Test
  void perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient() {
    // 200000 / 30000 = 6.66666... Exact-string assertion (compareTo is scale-insensitive and would
    // pass on a broken scale). Scale-4 HALF_UP -> 6.6667; truncation/DOWN would give 6.6666. This is
    // the only test that actually exercises the rounding frozen into the wire contract.
    stubNoCrossSchedule("D", Optional.of(new SummaryRow(1028, "c", 0)),
        List.of(new DetailRow(26, new BigDecimal("30000"), 200000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, true);
    assertEquals("6.6667", doc.lessLogSales().perUnit().toPlainString());
  }
}
