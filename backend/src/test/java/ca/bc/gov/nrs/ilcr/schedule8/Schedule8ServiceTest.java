package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Options;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 8 read assembly + server-side derivation (AD-5/AD-6). Mocked repository —
 * no DB, no Spring. Covers the three-level page → sample → rate assembly, the addition/deduction split
 * by cost-item subcategory (§Decision 1), the code→label resolution (§Decision 3), the computed
 * {@code percentTotal}/{@code actualHarvested}/{@code additionsTotal}/{@code deductionsTotal}/
 * {@code finalRate} + counts, the Y/N indicator booleans, editability, and the no-pages empty list.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8ServiceTest {

  private static final long MILL = 570L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule8Repository repository;

  @InjectMocks
  private Schedule8Service service;

  @BeforeEach
  void stubLabelMapsAndSubcategories() {
    lenient().when(repository.supportCentreLabels())
        .thenReturn(Map.of("SC1", "Support Centre One"));
    lenient().when(repository.regionLabels()).thenReturn(Map.of("R1", "Region One"));
    lenient().when(repository.becZoneLabels()).thenReturn(Map.of("BZ1", "BEC Zone One"));
    lenient().when(repository.tsaNumberLabels()).thenReturn(Map.of("TSA5", "Test TSA Five"));
    lenient().when(repository.supplyBlockLabels()).thenReturn(Map.of("B", "Supply Block B"));
    lenient().when(repository.tflNumberLabels()).thenReturn(Map.of("48", "Tree Farm Licence 48"));
    lenient().when(repository.skidTypeLabels()).thenReturn(Map.of("ST1", "Skid Type One"));
    lenient().when(repository.costTypeLabels())
        .thenReturn(Map.of("CT1", "Cost Type One", "CT2", "Cost Type Two"));
    // §Decision 1: '1'/'2' = addition, '3'/'4' = deduction.
    lenient().when(repository.costItemSubcategories())
        .thenReturn(Map.of(82, "1", 100, "2", 101, "3", 107, "4"));
  }

  private static void eq(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        () -> "expected " + expected + " but was " + actual);
  }

  /**
   * One Draft page → one sample (60/40 = 100%, harvested 700+300=1000, rate 25.50) → one addition
   * (item 82 subcat '1', 5.00) + one deduction (item 101 subcat '3', 2.00) ⇒ final 28.50.
   */
  private void stubOnePageOneSample() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8500)));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample(8600, 8500)));
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of(
        rate(8700, 8600, "CT1", 82, "Add A", "5.00"),
        rate(8701, 8600, "CT2", 101, "Ded A", "2.00")));
  }

  private static TreeToTruckReportEntity page(int id) {
    return new TreeToTruckReportEntity(id, "SC1", "R1", "BZ1", "TSA5", "B", "48", "CP1", "L570",
        "North Div", "Pat Contact", "2505551212", "Seed page", 0);
  }

  private static TreeToTruckDetailReportEntity sample(int id, int reportId) {
    return new TreeToTruckDetailReportEntity(id, reportId, "C1", "CB1", 60, 40, 0, 0, 0, 0,
        null, null, null, null, null, "N", "Y", "ST1", 700, 300, new BigDecimal("25.50"), 0);
  }

  private static TreeToTruckRateDetailEntity rate(
      int id, int detailReportId, String costType, int costItem, String desc, String rate) {
    return new TreeToTruckRateDetailEntity(
        id, detailReportId, costType, costItem, desc, new BigDecimal(rate), 0);
  }

  @Test
  void threeLevelAssembly_pageCarriesItsSampleAndSampleCount() {
    stubOnePageOneSample();
    Schedule8Response doc = service.getSchedule8(MILL, YEAR, true);
    assertEquals(1, doc.pages().size());
    Page page = doc.pages().get(0);
    assertEquals(8500, page.id());
    assertEquals(1, page.sampleCount());
    assertEquals(1, page.samples().size());
    assertEquals(8600, page.samples().get(0).id());
  }

  @Test
  void additionsAndDeductions_splitBySubcategory() {
    stubOnePageOneSample();
    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);
    assertEquals(1, sample.additionCount());
    assertEquals(1, sample.deductionCount());
    assertEquals(82, sample.additions().get(0).costItemCode()); // subcat '1'
    assertEquals(101, sample.deductions().get(0).costItemCode()); // subcat '3'
  }

  @Test
  void computedRollups_totalsAndFinalRate() {
    stubOnePageOneSample();
    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);
    assertEquals(100, sample.percentTotal());       // 60 + 40
    assertEquals(1000, sample.actualHarvested());    // 700 + 300
    eq("5", sample.additionsTotal());
    eq("2", sample.deductionsTotal());
    eq("28.5", sample.finalRate());                  // 25.50 + 5.00 − 2.00
    eq("25.5", sample.originalRate());
  }

  @Test
  void unclassifiableRateRow_droppedFromRollup() {
    // A stored rate row whose cost item has no addition/deduction subcategory (legacy / out-of-band
    // data the write path now rejects) is excluded from both lists and the totals — never surfaced.
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8500)));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample(8600, 8500)));
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of(
        rate(8700, 8600, "CT1", 82, "Add A", "5.00"),      // subcat '1' — addition
        rate(8702, 8600, "CT1", 999, "Orphan", "9.99")));  // 999 not in subcategories → dropped

    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);

    assertEquals(1, sample.additionCount());   // only the classifiable addition
    assertEquals(0, sample.deductionCount());
    eq("5", sample.additionsTotal());          // the orphan 9.99 is NOT summed in
    eq("30.5", sample.finalRate());            // 25.50 + 5.00 − 0
  }

  @Test
  void codeLabels_resolvedFromCodeTables() {
    stubOnePageOneSample();
    Page page = service.getSchedule8(MILL, YEAR, true).pages().get(0);
    assertEquals("SC1", page.supportCentre());
    assertEquals("Support Centre One", page.supportCentreLabel());
    assertEquals("Region One", page.regionLabel());
    assertEquals("Tree Farm Licence 48", page.tflNumberLabel());
    Sample sample = page.samples().get(0);
    assertEquals("Skid Type One", sample.skidTypeDescription());
    assertEquals("Cost Type One", sample.additions().get(0).costTypeDescription());
  }

  @Test
  void ynIndicators_mappedToBooleans() {
    stubOnePageOneSample();
    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);
    assertTrue(sample.uphillDirection());        // "Y"
    assertFalse(sample.waterDumpDestination());  // "N"
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stubOnePageOneSample();
    assertTrue(service.getSchedule8(MILL, YEAR, true).editable());
    assertFalse(service.getSchedule8(MILL, YEAR, false).editable());
  }

  @Test
  void editable_falseWhenNotDraft_pagesStillListed() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8500)));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    Schedule8Response doc = service.getSchedule8(MILL, YEAR, true);
    assertFalse(doc.editable());
    assertEquals(1, doc.pages().size());
    assertEquals("S", doc.trackStatus());
  }

  @Test
  void noPages_returnsEmptyListNot404() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    Schedule8Response doc = service.getSchedule8(MILL, YEAR, true);
    assertTrue(doc.pages().isEmpty());
    assertTrue(doc.editable()); // editable per Draft track even with no pages
  }

  @Test
  void multiRow_rollupSumsEveryAdditionAndDeduction_acrossAllFourSubcategories() {
    // Two additions (subcats '1' + '2') and two deductions ('3' + '4') — proves the totals SUM the
    // rows (n=1 can't distinguish sum from passthrough) and exercises subcategories '2'/'4' (M5).
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8500)));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample(8600, 8500)));
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of(
        rate(8700, 8600, "CT1", 82, "Add subcat 1", "5.00"),
        rate(8701, 8600, "CT1", 100, "Add subcat 2", "3.25"),
        rate(8702, 8600, "CT2", 101, "Ded subcat 3", "2.00"),
        rate(8703, 8600, "CT2", 107, "Ded subcat 4", "1.75")));

    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);

    assertEquals(2, sample.additionCount());
    assertEquals(2, sample.deductionCount());
    eq("8.25", sample.additionsTotal());   // 5.00 + 3.25  (subcats '1' + '2')
    eq("3.75", sample.deductionsTotal());  // 2.00 + 1.75  (subcats '3' + '4')
    eq("30", sample.finalRate());          // 25.50 + 8.25 − 3.75
  }

  @Test
  void getOptions_partitionsCategory8CostItemsIntoAdditionsAndDeductions() {
    // Category-8 cost items spanning all four subcategories: '1'/'2' are additions, '3'/'4' deductions
    // (§Decision 1). getOptions must split the single query result into the two per-form lists, keying
    // each choice by String(id) + itemName and preserving the query's ITEM_NAME order.
    when(repository.findCategory8CostItems()).thenReturn(List.of(
        new Schedule8Repository.CostItemRow(82, "Add subcat 1", "1"),
        new Schedule8Repository.CostItemRow(100, "Add subcat 2", "2"),
        new Schedule8Repository.CostItemRow(101, "Ded subcat 3", "3"),
        new Schedule8Repository.CostItemRow(107, "Ded subcat 4", "4")));

    Schedule8Options options = service.getOptions();

    assertEquals(
        List.of(
            new Schedule8Options.CodeOption("82", "Add subcat 1"),
            new Schedule8Options.CodeOption("100", "Add subcat 2")),
        options.additionCostItems());
    assertEquals(
        List.of(
            new Schedule8Options.CodeOption("101", "Ded subcat 3"),
            new Schedule8Options.CodeOption("107", "Ded subcat 4")),
        options.deductionCostItems());
    // The plain code→label lists reshape the label maps (§Decision 3), code + DESCRIPTION.
    assertEquals(
        List.of(new Schedule8Options.CodeOption("SC1", "Support Centre One")),
        options.supportCentres());
    assertEquals(
        List.of(new Schedule8Options.CodeOption("48", "Tree Farm Licence 48")),
        options.tflNumbers());
  }

  @Test
  void sampleWithNoRateRows_finalRateEqualsOriginal() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findPages(MILL, YEAR)).thenReturn(List.of(page(8500)));
    when(repository.findSamples(MILL, YEAR)).thenReturn(List.of(sample(8600, 8500)));
    when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    Sample sample = service.getSchedule8(MILL, YEAR, true).pages().get(0).samples().get(0);
    assertEquals(0, sample.additionCount());
    assertEquals(0, sample.deductionCount());
    eq("0", sample.additionsTotal());
    eq("25.5", sample.finalRate()); // originalRate only
  }
}
