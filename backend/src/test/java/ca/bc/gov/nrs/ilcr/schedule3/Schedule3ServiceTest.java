package ca.bc.gov.nrs.ilcr.schedule3;

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
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * Unit test for the Schedule 3 document assembly + server-side derivation cascade (AD-5/AD-6),
 * reproducing the legacy {@code Schedule3DO}/{@code CostType} getter arithmetic. Mocked repository
 * — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule3Repository repository;

  @Mock private ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service schedule1Service;

  @Mock private org.springframework.context.MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule3Service service;

  /** A cost detail row (no comments). */
  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null, null);
  }

  /** A volume detail row. */
  private static DetailRow volume(int code, String vol) {
    return new DetailRow(code, new BigDecimal(vol), null, null, null);
  }

  /**
   * Defect #296: a mill/year with NO category-3 summary is the unsaved state, not a 404. Note this
   * is a DIFFERENT case from the existing empty-details test — that one has a summary present with
   * no rows; this one has no summary at all, which is the branch the fix added.
   *
   * <p>The null {@code revisionCount} is load-bearing: it is omitted from the body ({@code
   * default-property-inclusion: non_null}) and the client's {@code isScheduleSaved} reads that
   * omission to keep Delete closed on a never-saved schedule.
   */
  @Test
  void getSchedule3_noSummary_servesEmptyEditableDocument() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));

    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    assertNull(doc.revisionCount(), "an unsaved schedule must carry NO optimistic-lock token");
    assertNull(doc.comments());
    assertEquals("N", doc.overrideHarvestTotalPop(), "absent summary falls back to the N default");
    assertTrue(doc.editable());
    verify(repository, never()).findDetails(anyInt());
  }

  /**
   * The cross-schedule counterpart. This one matters more than Schedule 1's: Schedule 3's subtotals
   * seed at ZERO, so handing Schedule 2 the empty document instead of an empty Optional would turn
   * its blank carried figures into $0 (#296 code review).
   */
  @Test
  void findSchedule3_noSummary_isEmpty_whileGetServesADocument() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));

    assertTrue(service.findSchedule3(MILL, YEAR, CallerRights.SUBMITTER).isEmpty());
    assertNotNull(
        service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER),
        "get must still serve a document");
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
    rows.add(cost(29, 30000)); // Annual Rents — Harvest-only
    rows.add(cost(30, 285000));
    rows.add(cost(128, 155000));
    rows.add(cost(31, 40000));
    rows.add(cost(129, 10000));
    rows.add(cost(32, 25000));
    rows.add(cost(130, 5000));
    rows.add(cost(33, 60000)); // Scaling — PO&P derived
    rows.add(cost(34, 35000));
    rows.add(cost(132, 15000));
    rows.add(cost(35, 45000));
    rows.add(cost(133, 5000));
    rows.add(cost(36, 80000));
    rows.add(cost(134, 20000));
    rows.add(cost(37, 150000)); // Silviculture Admin — Harvest-only (V5)
    rows.add(volume(118, "54321")); // PO&P Timber volume
    rows.add(volume(119, "54321")); // Crown Timber volume
    return rows;
  }

  @Test
  void normalLine_crownIsHarvestMinusPop() {
    stub("D", "N", List.of(cost(27, 100000), cost(125, 40000)));
    CostLine licenses = line(service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER), 27);
    assertEquals(100000, licenses.harvest());
    assertEquals(40000, licenses.pop());
    assertEquals(60000, licenses.crown());
  }

  @Test
  void harvestOnlyLines_popForcedZero_crownEqualsHarvest() {
    stub("D", "N", List.of(cost(29, 30000), cost(37, 150000)));
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);
    assertEquals(0, line(doc, 29).pop());
    assertEquals(30000, line(doc, 29).crown());
    assertEquals(0, line(doc, 37).pop());
    assertEquals(150000, line(doc, 37).crown());
  }

  @Test
  void scalingPop_derivedFromTimberVolumeRatio() {
    // ratio = popTimberVol / (popTimberVol + crownTimberVol) = 54321/108642 = 0.5; pop = 0.5 *
    // 60000.
    stub("D", "N", List.of(cost(33, 60000), volume(118, "54321"), volume(119, "54321")));
    CostLine scaling = line(service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER), 33);
    assertEquals(60000, scaling.harvest());
    assertEquals(30000, scaling.pop());
    assertEquals(30000, scaling.crown());
  }

  @Test
  void fullDocument_derivedCascadeMatchesLegacy() {
    stub("D", "Y", fullDocument());
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    // Subtotal Actual = 900000 / 300000 (== the V5-stored 115/135). Totals are Long
    // (overflow-safe).
    assertEquals(900000L, doc.subtotalActualCosts().harvest().longValue());
    assertEquals(300000L, doc.subtotalActualCosts().pop().longValue());
    assertEquals(600000L, doc.subtotalActualCosts().crown().longValue());

    // Included Unacceptable = Annual Rents harvest (no item-38 rows); PO&P forced 0, crown =
    // harvest.
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
    assertEquals(1, doc.unacceptableCount()); // +1 for the non-zero Annual Rents harvest
  }

  @Test
  void unacceptableCount_addsItem38RowsPlusAnnualRents() {
    List<DetailRow> rows = new ArrayList<>();
    rows.add(cost(29, 30000)); // Annual Rents present → +1
    rows.add(new DetailRow(38, null, 1000, "Fine A", null)); // two item-38 rows
    rows.add(new DetailRow(38, null, 2000, "Fine B", null));
    stub("D", "N", rows);
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);
    assertEquals(3, doc.unacceptableCount());
    // Included Unacceptable harvest = 1000 + 2000 + 30000 (Annual Rents).
    assertEquals(33000, doc.includedUnacceptableCosts().harvest());
  }

  @Test
  void unacceptableCount_noAnnualRents_noPlusOne() {
    stub("D", "N", List.of(new DetailRow(38, null, 1000, "Fine A", null)));
    assertEquals(1, service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER).unacceptableCount());
  }

  @Test
  void otherAcceptableCosts_groupedByCommentsKey() {
    // Two groups (GRP1/GRP2), each a TOT row (harvest) + a POP row.
    List<DetailRow> rows =
        List.of(
            new DetailRow(124, null, 10000, "Fuel", "SCH3_2_TOT_GRP1"),
            new DetailRow(124, null, 4000, "Fuel", "SCH3_2_POP_GRP1"),
            new DetailRow(124, null, 6000, "Tools", "SCH3_2_TOT_GRP2"),
            new DetailRow(124, null, 1000, "Tools", "SCH3_2_POP_GRP2"));
    stub("D", "N", rows);
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);
    assertEquals(2, doc.otherAcceptableCount());
    assertEquals(16000L, doc.subtotalOtherCosts().harvest().longValue()); // 10000 + 6000
    assertEquals(5000L, doc.subtotalOtherCosts().pop().longValue()); // 4000 + 1000
    assertEquals(11000L, doc.subtotalOtherCosts().crown().longValue());
  }

  @Test
  void overrideDefaultsToN_whenLocationNull() {
    stub("D", null, List.of());
    assertEquals(
        "N", service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER).overrideHarvestTotalPop());
  }

  @Test
  void emptySchedule_subtotalsAreZero() {
    stub("D", "N", List.of());
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);
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
    assertTrue(service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stub("S", "N", List.of());
    assertFalse(service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stub("D", "N", List.of());
    assertFalse(service.getSchedule3(MILL, YEAR, CallerRights.NONE).editable());
  }

  // -----------------------------------------------------------------------------------------
  // Original values (Story 16.2, BR-04). The gate itself is OriginalValuesTest's; what is pinned
  // here is Schedule 3's own mapping — which cost item each field reads its snapshot from, and
  // which fields carry one. A fixed line reads two DIFFERENT items (its Harvest code and its
  // PO&P code) and the sub-page rows key by detail id rather than by item, so a key slip is
  // invisible to every other assertion in this class.
  // -----------------------------------------------------------------------------------------

  private static final int SUMMARY_ID = 1003;

  /** A submitted cost-detail row keyed by cost item — the fixed lines and timber volumes. */
  private static CostDetailSnapshotRepository.Row byItem(
      int costItemCode, String vol, Integer cost) {
    return new CostDetailSnapshotRepository.Row(
        900 + costItemCode,
        (long) SUMMARY_ID,
        costItemCode,
        vol == null ? null : new BigDecimal(vol),
        cost,
        null,
        null);
  }

  /** A submitted sub-page row, addressed by its own detail id (item 19 repeats within a parent). */
  private static CostDetailSnapshotRepository.Row byDetail(
      int detailId, int costItemCode, Integer cost, String description) {
    return new CostDetailSnapshotRepository.Row(
        detailId, (long) SUMMARY_ID, costItemCode, null, cost, description, null);
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
    stub("D", "N", List.of(cost(27, 100000), cost(125, 40000), volume(118, "54321")));
    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);
    assertNull(doc.originalValues(), "Draft must serve no original-value map at all");
    assertNull(line(doc, 27).originalValues());
    assertNull(doc.popTimber().originalValues());
    verify(costSnapshots, never()).findBySummary(anyInt());
    verify(summarySnapshots, never()).findBySummaryId(anyInt());
  }

  @Test
  void originalValues_fixedLine_harvestAndPopReadTheirOwnCostItems() {
    stub("S", "N", List.of(cost(27, 100000), cost(125, 40000)));
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(List.of(byItem(27, null, 95000), byItem(125, null, 38000)));

    CostLine licenses = line(service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER), 27);

    // Harvest comes from item 27, PO&P from item 125 — swapping the two would still populate both
    // keys, so both values are asserted, not merely their presence.
    assertOriginal(licenses.originalValues(), "harvest", "95000", "95,000");
    assertOriginal(licenses.originalValues(), "pop", "38000", "38,000");
  }

  @Test
  void originalValues_harvestOnlyLines_carryNoPopKey() {
    // Annual Rents (29) and Silviculture Admin (37) force PO&P to zero and Scaling (33) derives it,
    // so none of the three has a PO&P item to have submitted — the key must stay absent, which is
    // what tells the client "no original on file" rather than "submitted as blank".
    stub("S", "N", List.of(cost(29, 30000), cost(33, 60000), cost(37, 150000)));
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(byItem(29, null, 28000), byItem(33, null, 55000), byItem(37, null, 140000)));

    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    assertEquals(Set.of("harvest"), line(doc, 29).originalValues().keySet());
    assertEquals(Set.of("harvest"), line(doc, 33).originalValues().keySet());
    assertEquals(Set.of("harvest"), line(doc, 37).originalValues().keySet());
    assertOriginal(line(doc, 29).originalValues(), "harvest", "28000", "28,000");
  }

  @Test
  void originalValues_timberVolumes_readItems118And119() {
    stub("S", "N", List.of(volume(118, "54321"), volume(119, "12000")));
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(List.of(byItem(118, "50000.0", null), byItem(119, "11000", null)));

    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    // The two ENTERED volumes carry an original; their costs are derived totals and carry none.
    assertOriginal(doc.popTimber().originalValues(), "volume", "50000", "50,000");
    assertEquals(Set.of("volume"), doc.popTimber().originalValues().keySet());
    assertOriginal(doc.crownTimber().originalValues(), "volume", "11000", "11,000");
    // Total Overhead is derived outright — no map at all.
    assertNull(doc.totalOverhead().originalValues());
  }

  @Test
  void originalValues_summary_carriesCommentsAndTheOverrideFlag() {
    stub("S", "Y", List.of());
    when(summarySnapshots.findBySummaryId(SUMMARY_ID))
        .thenReturn(
            Optional.of(
                new ReportSummarySnapshotRepository.Snapshot(null, "N", "submitted comment")));

    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    assertOriginal(doc.originalValues(), "comments", "submitted comment", "submitted comment");
    // Not a location: Schedule 3 stores the override-total-PO&P flag in the summary LOCATION
    // column, and the submitted "N" here differs from the current "Y" the stub carries.
    assertOriginal(doc.originalValues(), "overrideHarvestTotalPop", "N", "N");
  }

  @Test
  void originalValues_submittedButNoSnapshotOnFile_isEmptyMapNotNull() {
    stub("S", "N", List.of(cost(27, 100000)));
    when(costSnapshots.findBySummary(SUMMARY_ID)).thenReturn(List.of());
    when(summarySnapshots.findBySummaryId(SUMMARY_ID)).thenReturn(Optional.empty());

    Schedule3Response doc = service.getSchedule3(MILL, YEAR, CallerRights.SUBMITTER);

    assertNotNull(doc.originalValues());
    assertTrue(doc.originalValues().isEmpty());
    assertNotNull(line(doc, 27).originalValues());
    assertTrue(line(doc, 27).originalValues().isEmpty());
  }

  @Test
  void originalValues_otherAcceptableRow_keyedByDetailId_popFromThePopRow() {
    // The TOT row's description + harvest total, and the PO&P PEER row's cost, all addressed by
    // detail id — item 124 repeats within the summary, so a code-keyed lookup would collide.
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, "N", "comment", 0)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findSubPageRows(SUMMARY_ID, 124))
        .thenReturn(
            List.of(
                new Schedule3Repository.SubPageRow(501, 10000, "Fuel", "SCH3_2_TOT_GRP1"),
                new Schedule3Repository.SubPageRow(502, 4000, "Fuel", "SCH3_2_POP_GRP1")));
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(byDetail(501, 124, 9000, "Diesel"), byDetail(502, 124, 3500, "Diesel")));

    OtherAcceptableDocument doc =
        service.getOtherAcceptableDocument(MILL, YEAR, CallerRights.SUBMITTER);

    Map<String, OriginalValue> originals = doc.rows().get(0).originalValues();
    assertOriginal(originals, "description", "Diesel", "Diesel");
    assertOriginal(originals, "total", "9000", "9,000"); // detail 501, the TOT row
    assertOriginal(originals, "pop", "3500", "3,500"); // detail 502, the PO&P peer
    // Crown is derived from the other two and never carries one.
    assertEquals(Set.of("description", "total", "pop"), originals.keySet());
  }

  @Test
  void originalValues_unacceptableRow_keyedByDetailId() {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, "N", "comment", 0)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findSubPageRows(SUMMARY_ID, 38))
        .thenReturn(List.of(new Schedule3Repository.SubPageRow(601, 1000, "Fine A", null)));
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(List.of(byDetail(601, 38, 800, "Penalty A")));
    lenient().when(repository.findDetails(SUMMARY_ID)).thenReturn(List.of());

    UnacceptableDocument doc = service.getUnacceptableDocument(MILL, YEAR, CallerRights.SUBMITTER);

    Map<String, OriginalValue> originals = doc.rows().get(0).originalValues();
    assertOriginal(originals, "description", "Penalty A", "Penalty A");
    assertOriginal(originals, "total", "800", "800");
    assertEquals(Set.of("description", "total"), originals.keySet());
  }
}
