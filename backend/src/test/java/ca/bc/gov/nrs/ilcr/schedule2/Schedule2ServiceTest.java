package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1CostDerivation;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 2 document assembly + server-side derivation (AD-5/AD-6). Mocked
 * repository + Schedule 3 service — no DB, no Spring. Covers the Schedule2MB formula set, the
 * carried Schedule-3 figures (PO&amp;P/Crown timber volumes + Subtotal Actual Costs PO&amp;P/Crown
 * columns + Silviculture Admin crown, all sourced from the Schedule 3 computed document), null
 * propagation when Schedule 3 is absent (findSchedule3 → empty → carried figures null),
 * editability, and the unsaved path.
 */
@ExtendWith(MockitoExtension.class)
class Schedule2ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule2Repository repository;

  @Mock private Schedule1CostDerivation schedule1CostDerivation;

  @Mock private Schedule3Service schedule3Service;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule2Service service;

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
            : List.of(new CostLine(37, silvAdminCrown, 0, silvAdminCrown, null));
    return new Schedule3Response(
        MILL,
        YEAR,
        "D",
        false,
        0,
        null,
        null,
        null, // originalValues — Draft, so none
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
        .when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(
            Optional.of(
                sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000)));
    // Sch1 "Subtotal Company Logging Cost (no silviculture)", the no-FMA figure = 617250.
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.of(617250L));
    lenient()
        .when(repository.findSch1SilvActualSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(20000));
    lenient()
        .when(repository.findSch1SilvAccruedSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(8450));
  }

  /**
   * No Schedule 3 and no Schedule 1 cross-figures. Since defect #296 the absence signal is an empty
   * Optional from find*, not a thrown ScheduleNotFoundException from get* — get* now serves an
   * EMPTY document whose subtotals seed at ZERO, which would turn these carried figures into $0.
   */
  private void stubNoCrossSchedule(
      String trackStatus, Optional<SummaryRow> summary, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR)).thenReturn(summary);
    summary.ifPresent(
        s -> lenient().when(repository.findDetails(s.summaryId())).thenReturn(details));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    lenient()
        .when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(Optional.empty());
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.empty());
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
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    assertEquals(500000, doc.purchasedLogCost().cost()); // item 25 cost
    eq("2000", doc.lessLogSales().volume()); // item 26 volume
    assertEquals(100000, doc.lessLogSales().cost()); // item 26 cost
    assertEquals("c", doc.comments());
    assertEquals(0, doc.revisionCount());
  }

  @Test
  void carriedFigures_fromSchedule3() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
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
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
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
  void totalCompanyLogging_consumesTheNamedNoFmaSubtotal_verbatim() {
    // Schedule 2 consumes Schedule1CostDerivation's named no-FMA subtotal AS-IS — it must not
    // adjust, re-derive, or second-guess the figure (#252). Before #252 this test pinned the
    // inverse arithmetic that lived here (subtotalCompanyLoggingCost − forestMgmtAdminCost); the
    // "Forest Mgmt Admin is excluded" half of that pin now lives where the sum does, in
    // Schedule1CostDerivationTest.loggingLinesAndItemizedOtherCosts_summed_forestMgmtAdminExcluded.
    // What is still worth pinning HERE is the boundary: the port's figure reaches
    // totalCompanyLogging unmodified, so a stray adjustment on this side can't creep back in.
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.of(new SummaryRow(1002, "c", 0)));
    lenient()
        .when(repository.findDetails(1002))
        .thenReturn(
            List.of(
                new DetailRow(25, null, 500000),
                new DetailRow(26, new BigDecimal("2000"), 100000)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient()
        .when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(
            Optional.of(
                sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000)));
    // The port reports 617250 as the no-FMA subtotal. A regression that re-added a Forest Mgmt
    // Admin of 82750 on this side would inflate the total to 823450.
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.of(617250L));
    lenient()
        .when(repository.findSch1SilvActualSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(20000));
    lenient()
        .when(repository.findSch1SilvAccruedSpentCost(MILL, YEAR))
        .thenReturn(Optional.of(8450));

    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    // 617250 + 100000 crown + ((20000 − 5000) + 8450) = 740700, NOT 823450.
    assertEquals(740700, doc.totalCompanyLogging().cost());
  }

  @Test
  void absentSchedule3_dependentFiguresNull() {
    // Sch2 present, but Schedule 3 absent (404) and no Sch1 144 -> carried/derived dependents null.
    stubNoCrossSchedule(
        "D",
        Optional.of(new SummaryRow(1028, "c", 3)),
        List.of(new DetailRow(25, null, 333000), new DetailRow(26, new BigDecimal("500"), 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
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
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
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
    when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(
            Optional.of(
                sch3Doc(new BigDecimal("10000"), 20000, new BigDecimal("12345"), 100000, 5000)));
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.empty());
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    eq("10000", doc.purchasedWoodOverhead().volume());
    assertEquals(20000, doc.purchasedWoodOverhead().cost());
    assertNull(doc.purchasedLogCost().cost()); // still unsaved item 25
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stubNoCrossSchedule("D", Optional.empty(), List.of());
    assertTrue(service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stubNoCrossSchedule("S", Optional.of(new SummaryRow(1028, "c", 3)), List.of());
    assertFalse(service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stubNoCrossSchedule("D", Optional.empty(), List.of());
    assertFalse(service.getSchedule2(MILL, YEAR, CallerRights.NONE).editable());
  }

  @Test
  void perUnit_nullWhenVolumeZero() {
    stubNoCrossSchedule(
        "D",
        Optional.of(new SummaryRow(1028, "c", 0)),
        List.of(new DetailRow(26, BigDecimal.ZERO, 25000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
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
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    assertEquals("6.6667", doc.lessLogSales().perUnit().toPlainString());
  }

  // -----------------------------------------------------------------------------------------
  // Original values (Story 16.2, BR-04). The gate is the real OriginalValues; what is pinned
  // here is the Schedule 2 side of it — which cost item each field reads, and which fields carry
  // an original at all. A key-to-field slip (item 25's cost landing on lessLogSales, say) leaves
  // every other assertion in this class passing.
  // -----------------------------------------------------------------------------------------

  private static final int SUMMARY_ID = 1002;

  /** Submitted (non-Draft) Schedule 2 over the same stored figures as {@link #stubFullDraft()}. */
  private void stubSubmitted() {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, "current comment", 3)));
    lenient()
        .when(repository.findDetails(SUMMARY_ID))
        .thenReturn(
            List.of(
                new DetailRow(25, null, 500000),
                new DetailRow(26, new BigDecimal("2000"), 100000)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    lenient()
        .when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(Optional.empty());
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.empty());
  }

  /** A submitted cost-detail row carrying only the columns Schedule 2 reads. */
  private static CostDetailSnapshotRepository.Row snapshotRow(
      int costItemCode, String volume, Integer cost) {
    return new CostDetailSnapshotRepository.Row(
        900 + costItemCode,
        (long) SUMMARY_ID,
        costItemCode,
        volume == null ? null : new BigDecimal(volume),
        cost,
        null,
        null);
  }

  private static void assertOriginal(
      Map<String, OriginalValue> originals, String field, String value, String formatted) {
    OriginalValue original = originals.get(field);
    assertNotNull(original, () -> "no original for " + field + " in " + originals.keySet());
    assertEquals(value, original.value());
    assertEquals(OriginalValuesFixture.tooltip(formatted), original.tooltip());
  }

  @Test
  void originalValues_absentAtDraft_andNoSnapshotQueryIssued() {
    stubFullDraft();
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    assertNull(doc.originalValues(), "Draft must serve no original-value map at all");
    assertNull(doc.purchasedLogCost().originalValues());
    assertNull(doc.lessLogSales().originalValues());
    // The gate short-circuits before the read: Draft costs no snapshot query.
    verify(costSnapshots, never()).findBySummary(anyInt());
    verify(summarySnapshots, never()).findBySummaryId(anyInt());
  }

  @Test
  void originalValues_submitted_mappedFromItems25And26AndTheSummary() {
    stubSubmitted();
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(
                snapshotRow(25, null, 480000), // item 25: cost only
                snapshotRow(26, "1800.0", 90000))); // item 26: volume + cost
    when(summarySnapshots.findBySummaryId(SUMMARY_ID))
        .thenReturn(
            Optional.of(new ReportSummarySnapshotRepository.Snapshot(null, null, "as submitted")));

    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);

    assertOriginal(doc.originalValues(), "comments", "as submitted", "as submitted");
    // Item 25 is cost-only: its volume is carried from Schedule 3, never entered here, so a
    // "volume" key appearing on this row would be a mapping regression (Schedule2DAO.java:122-123).
    assertOriginal(doc.purchasedLogCost().originalValues(), "cost", "480000", "480,000");
    assertEquals(Set.of("cost"), doc.purchasedLogCost().originalValues().keySet());
    // Item 26 has both entered (Schedule2DAO.java:118-119). 1800.0 canonicalizes without its
    // trailing zero so the client compares 1800 against 1800, and still renders grouped.
    assertOriginal(doc.lessLogSales().originalValues(), "volume", "1800", "1,800");
    assertOriginal(doc.lessLogSales().originalValues(), "cost", "90000", "90,000");
  }

  @Test
  void originalValues_derivedBlocks_carryNone() {
    stubSubmitted();
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(List.of(snapshotRow(25, null, 480000)));
    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);
    // Nothing on these three rows is entered on Schedule 2, so none of them carries a map at all.
    assertNull(doc.purchasedWoodOverhead().originalValues());
    assertNull(doc.subtotal().originalValues());
    assertNull(doc.netPurchased().originalValues());
  }

  @Test
  void originalValues_submittedButNoSnapshotOnFile_isEmptyMapNotNull() {
    // Half the live rows have no 'S' snapshot. That is NOT Draft: the page must still evaluate
    // "value added since submission" per field, so the maps are empty rather than null.
    stubSubmitted();
    when(costSnapshots.findBySummary(SUMMARY_ID)).thenReturn(List.of());
    when(summarySnapshots.findBySummaryId(SUMMARY_ID)).thenReturn(Optional.empty());

    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);

    assertNotNull(doc.originalValues());
    assertTrue(doc.originalValues().isEmpty());
    assertNotNull(doc.purchasedLogCost().originalValues());
    assertTrue(doc.purchasedLogCost().originalValues().isEmpty());
    assertNotNull(doc.lessLogSales().originalValues());
    assertTrue(doc.lessLogSales().originalValues().isEmpty());
  }

  @Test
  void originalValues_unsavedSchedule_noSummaryId_readsNoSnapshot() {
    // No category-'2' summary beyond Draft: there is no id to read a snapshot against, so the
    // maps are empty and neither query is issued (a null summary id must not reach the repository).
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    lenient()
        .when(schedule3Service.findSchedule3(MILL, YEAR, CallerRights.NONE))
        .thenReturn(Optional.empty());
    lenient()
        .when(schedule1CostDerivation.subtotalLoggingNoFmaCost(MILL, YEAR))
        .thenReturn(Optional.empty());

    Schedule2Response doc = service.getSchedule2(MILL, YEAR, CallerRights.SUBMITTER);

    assertNotNull(doc.originalValues());
    assertTrue(doc.originalValues().isEmpty());
    verify(costSnapshots, never()).findBySummary(anyInt());
    verify(summarySnapshots, never()).findBySummaryId(anyInt());
  }
}
