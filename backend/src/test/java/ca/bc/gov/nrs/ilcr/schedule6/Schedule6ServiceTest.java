package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link Schedule6Service} derivation (AD-5/AD-6): RMG lookup for TSA and TFL
 * records, the $/m3 cost-per-volume (null when volume is zero/absent), the int-overflow-safe
 * running totals, the placeholder (lone-comment) handling, and the {@code editable} matrix. Pure
 * JUnit + Mockito — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule6Service — server-side derivation")
class Schedule6ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule6Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule6Service service;

  private void stub(String trackStatus, List<RoadRecordRow> rows, List<CostDetailRow> details) {
    lenient()
        .when(repository.findTrackStatus(MILL, YEAR))
        .thenReturn(Optional.ofNullable(trackStatus));
    lenient().when(repository.findRoadRecords(MILL, YEAR)).thenReturn(rows);
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(details);
  }

  @Test
  @DisplayName("TSA record — RMG from TSA+TSB, $/m3 = cost/volume (scale 2), classification split")
  void tsaRecord_derivesRmgAndPerUnit() {
    stub(
        "D",
        List.of(new RoadRecordRow(8001, "01", "01B", null, "GC", 0)),
        List.of(new CostDetailRow(8001, new BigDecimal("1000"), 50000, "note")));

    RoadRecord road = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).roadRecords().get(0);

    assertEquals("15", road.rmg());
    assertEquals("01", road.areaType());
    assertEquals("01B", road.supplyBlock());
    assertNull(road.tflNumber());
    assertEquals(0, new BigDecimal("50.00").compareTo(road.costPerVolume()));
    assertEquals("note", road.comments());
    assertEquals(0, road.revisionCount());
  }

  @Test
  @DisplayName("TFL record — RMG from TFL code, areaType TFL, supplyBlock omitted")
  void tflRecord_derivesRmgFromTfl() {
    stub(
        "D",
        List.of(new RoadRecordRow(8002, null, null, "18", "GC", 0)),
        List.of(new CostDetailRow(8002, new BigDecimal("400"), 30000, null)));

    RoadRecord road = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).roadRecords().get(0);

    assertEquals("4", road.rmg());
    assertEquals("TFL", road.areaType());
    assertEquals("18", road.tflNumber());
    assertNull(road.supplyBlock());
    assertEquals(0, new BigDecimal("75.00").compareTo(road.costPerVolume()));
  }

  @Test
  @DisplayName("zero or absent volume -> costPerVolume null (no divide-by-zero)")
  void zeroOrAbsentVolume_costPerVolumeNull() {
    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, null, 0),
            new RoadRecordRow(8002, "03", "03B", null, null, 0)),
        List.of(
            new CostDetailRow(8001, BigDecimal.ZERO, 5000, null),
            new CostDetailRow(8002, null, 6000, null)));

    List<RoadRecord> records =
        service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).roadRecords();

    assertNull(records.get(0).costPerVolume(), "zero volume");
    assertNull(records.get(1).costPerVolume(), "absent volume");
  }

  @Test
  @DisplayName("running totals are Long-widened (sum exceeds Integer.MAX_VALUE)")
  void totals_areLongOverflowSafe() {
    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, null, 0),
            new RoadRecordRow(8002, "03", "03B", null, null, 0)),
        List.of(
            new CostDetailRow(8001, new BigDecimal("10"), 2_000_000_000, null),
            new CostDetailRow(8002, new BigDecimal("10"), 2_000_000_000, null)));

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertEquals(4_000_000_000L, response.totalCost());
    assertEquals(0, new BigDecimal("20").compareTo(response.totalVolume()));
  }

  @Test
  @DisplayName(
      "lone-comment placeholder — excluded from records, supplies generalComments, zero totals")
  void placeholder_excludedButGeneralCommentKept() {
    stub("D", List.of(new RoadRecordRow(8004, null, null, null, "Only a comment", 0)), List.of());

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertTrue(response.roadRecords().isEmpty());
    assertEquals("Only a comment", response.generalComments());
    assertEquals(0L, response.totalCost());
    assertEquals(0, BigDecimal.ZERO.compareTo(response.totalVolume()));
    assertNull(response.totalCostPerVolume());
  }

  @Test
  @DisplayName(
      "record with NO item-69 detail — listed with null volume/cost/$per-m3, totals skip it")
  void detaillessRecord_listedWithNullFigures() {
    // The dominant REAL-data shape (Task 1: the real cat-6 rows carry ZERO item-69 details).
    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, "GC", 0),
            new RoadRecordRow(8002, "03", "03B", null, "GC", 0)),
        List.of(new CostDetailRow(8002, new BigDecimal("2000"), 40000, null)));

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);
    RoadRecord detailless = response.roadRecords().get(0);

    assertEquals(8001, detailless.recordId());
    assertEquals("15", detailless.rmg(), "classification still derives without a detail");
    assertNull(detailless.volume());
    assertNull(detailless.cost());
    assertNull(detailless.costPerVolume());
    assertNull(detailless.comments());
    assertEquals(40000L, response.totalCost(), "totals sum only the present details");
    assertEquals(0, new BigDecimal("2000").compareTo(response.totalVolume()));
  }

  @Test
  @DisplayName("fractional volume keeps its decimals; $/m3 rounds scale 2 HALF_UP")
  void fractionalVolume_keptAndRounded() {
    stub(
        "D",
        List.of(new RoadRecordRow(8001, "01", "01B", null, null, 0)),
        List.of(new CostDetailRow(8001, new BigDecimal("400.50"), 1000, null)));

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);
    RoadRecord road = response.roadRecords().get(0);

    // normalizeVolume strips only trailing zeros: 400.50 -> 400.5 (not 400 and not 4.005E+2).
    assertEquals(new BigDecimal("400.5"), road.volume());
    assertEquals(new BigDecimal("400.5"), response.totalVolume());
    // 1000 / 400.5 = 2.4968... -> 2.50 at scale 2 HALF_UP (legacy CoreUtil.bigDecimalDivision).
    assertEquals(new BigDecimal("2.50"), road.costPerVolume());
    assertEquals(new BigDecimal("2.50"), response.totalCostPerVolume());
  }

  @Test
  @DisplayName("generalComments = the LAST row's COMMENTS, raw, even when rows diverge or end null")
  void generalComments_lastRowWinsRaw() {
    // Legacy Schedule6DAO.getReport re-assigns per row, so an imperfectly-replicated set resolves
    // to the last row — including a trailing null. Comments are served untrimmed (exactly as
    // saved).
    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, "first ", 0),
            new RoadRecordRow(8002, "03", "03B", null, " last ", 0)),
        List.of());
    assertEquals(
        " last ", service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).generalComments());

    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, "first", 0),
            new RoadRecordRow(8002, "03", "03B", null, null, 0)),
        List.of());
    assertNull(service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).generalComments());
  }

  @Test
  @DisplayName(
      "duplicate item-69 details for one record — first-by-id wins (order-independent totals)")
  void duplicateDetail_firstByIdWins() {
    stub(
        "D",
        List.of(new RoadRecordRow(8001, "01", "01B", null, null, 0)),
        // findCostDetails orders by detail id; the second row is the anomaly and must lose.
        List.of(
            new CostDetailRow(8001, new BigDecimal("1000"), 50000, "kept"),
            new CostDetailRow(8001, new BigDecimal("9999"), 99999, "dropped")));

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertEquals(50000, response.roadRecords().get(0).cost());
    assertEquals("kept", response.roadRecords().get(0).comments());
    assertEquals(50000L, response.totalCost());
    assertEquals(0, new BigDecimal("1000").compareTo(response.totalVolume()));
  }

  @Test
  @DisplayName("cost detail attached to a placeholder row — excluded from records AND totals")
  void placeholderWithDetail_excludedEverywhere() {
    stub(
        "D",
        List.of(new RoadRecordRow(8004, null, null, null, "Only a comment", 0)),
        List.of(new CostDetailRow(8004, new BigDecimal("500"), 12345, "orphaned")));

    Schedule6Response response = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertTrue(response.roadRecords().isEmpty());
    assertEquals(0L, response.totalCost());
    assertEquals(0, BigDecimal.ZERO.compareTo(response.totalVolume()));
    assertEquals("Only a comment", response.generalComments());
  }

  @Test
  @DisplayName("padded or whitespace-only codes — normalized once, classification and RMG agree")
  void paddedCodes_normalizedForClassificationAndRmg() {
    stub(
        "D",
        // Padded TSA/TSB must still resolve RMG 15; a whitespace-only TFL is NO TFL (the record
        // classifies as TSA and derives through the TSA+TSB branch, not the TFL branch).
        List.of(new RoadRecordRow(8001, "01 ", " 01B ", "  ", null, 0)),
        List.of(new CostDetailRow(8001, new BigDecimal("1000"), 50000, null)));

    RoadRecord road = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER).roadRecords().get(0);

    assertEquals("01", road.areaType());
    assertEquals("01B", road.supplyBlock());
    assertNull(road.tflNumber());
    assertEquals("15", road.rmg());
  }

  @Test
  @DisplayName("editable follows the role x status matrix (server-authoritative)")
  void editableMatrix() {
    assertTrue(editableFor("D", CallerRights.SUBMITTER), "Draft + submitter");
    assertFalse(editableFor("D", CallerRights.ADMIN), "Draft + admin");
    assertFalse(editableFor("D", CallerRights.NONE), "Draft + no edit rights");
    assertFalse(editableFor("S", CallerRights.SUBMITTER), "Submitted + submitter");
    assertTrue(editableFor("S", CallerRights.ADMIN), "Submitted + admin");
    assertTrue(editableFor("V", CallerRights.ADMIN), "Verified + admin");
    assertFalse(editableFor("V", CallerRights.SUBMITTER), "Verified + submitter");
    assertFalse(editableFor(null, CallerRights.SUBMITTER), "no status + submitter");
    assertFalse(editableFor(null, CallerRights.ADMIN), "no status + admin");
  }

  private boolean editableFor(String trackStatus, EditableStatuses callerMayEdit) {
    stub(trackStatus, List.of(), List.of());
    return service.getSchedule6(MILL, YEAR, callerMayEdit).editable();
  }

  // -----------------------------------------------------------------------------------------
  // Original values (Story 16.2, BR-04). Schedule 6 reads a road record's submitted figures from
  // TWO snapshots — the report view for its classification, the shared cost view for its volume,
  // cost and comments — joined on the record id. The join is the thing worth pinning: it is the
  // only place the shared finder's parent id has to line up with this schedule's own record id.
  // -----------------------------------------------------------------------------------------

  private static void assertOriginal(
      Map<String, OriginalValue> originals, String field, String value, String formatted) {
    OriginalValue original = originals.get(field);
    assertNotNull(original, () -> "no original for " + field + " in " + originals.keySet());
    assertEquals(value, original.value());
    assertEquals(OriginalValuesFixture.tooltip(formatted), original.tooltip());
  }

  @Test
  @DisplayName("at Draft nothing is exposed and neither snapshot view is read")
  void originalValues_absentAtDraft_andNoSnapshotQueryIssued() {
    stub(
        "D",
        List.of(new RoadRecordRow(8001, "01", "01B", null, "GC", 0)),
        List.of(new CostDetailRow(8001, new BigDecimal("1000"), 50000, "note")));

    Schedule6Response doc = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertNull(doc.originalValues());
    assertNull(doc.roadRecords().get(0).originalValues());
    verify(repository, never()).findRoadRecordSnapshots(anyLong(), anyInt());
    verify(costSnapshots, never()).findByRoadMaintenanceReports(anyList());
  }

  @Test
  @DisplayName("beyond Draft a record joins its report snapshot to its cost snapshot by record id")
  void originalValues_submitted_joinReportAndCostSnapshotsOnTheRecordId() {
    stub(
        "S",
        List.of(new RoadRecordRow(8001, "01", "01B", null, "current note", 0)),
        List.of(new CostDetailRow(8001, new BigDecimal("1000"), 50000, "note")));
    when(repository.findRoadRecordSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(
                new Schedule6Repository.RoadRecordSnapshotRow(
                    8001, "02", "02B", null, "submitted general")));
    when(costSnapshots.findByRoadMaintenanceReports(List.of(8001L)))
        .thenReturn(
            List.of(
                new CostDetailSnapshotRepository.Row(
                    1, 8001L, 71, new BigDecimal("900"), 45000, null, "submitted note")));

    Schedule6Response doc = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);
    Map<String, OriginalValue> originals = doc.roadRecords().get(0).originalValues();

    // Classification off the report snapshot; volume/cost/comments off the cost snapshot. The
    // record id is the only key joining them — a widened-or-narrowed mismatch drops half of these.
    assertOriginal(originals, "areaType", "02", "02");
    assertOriginal(originals, "supplyBlock", "02B", "02B");
    assertOriginal(originals, "volume", "900", "900");
    assertOriginal(originals, "cost", "45000", "45,000");
    assertOriginal(originals, "comments", "submitted note", "submitted note");
    // The document-level general comment is the replicated per-row COMMENTS, not the cost row's.
    assertOriginal(
        doc.originalValues(), "generalComments", "submitted general", "submitted general");
  }

  @Test
  @DisplayName("a record submitted as TFL exposes the TFL number and no supply block")
  void originalValues_submittedAsTfl_swapsTheClassificationKeys() {
    stub(
        "S",
        List.of(new RoadRecordRow(8002, "01", "01B", null, null, 0)),
        List.of(new CostDetailRow(8002, new BigDecimal("10"), 100, null)));
    when(repository.findRoadRecordSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(new Schedule6Repository.RoadRecordSnapshotRow(8002, null, null, "08", null)));
    when(costSnapshots.findByRoadMaintenanceReports(List.of(8002L))).thenReturn(List.of());

    Map<String, OriginalValue> originals =
        service
            .getSchedule6(MILL, YEAR, CallerRights.SUBMITTER)
            .roadRecords()
            .get(0)
            .originalValues();

    // Submitted as a TFL: areaType reads the literal "TFL" and the supply-block key stays absent,
    // mirroring how the current-side record is split.
    assertOriginal(originals, "areaType", "TFL", "TFL");
    assertOriginal(originals, "tflNumber", "08", "08");
    assertEquals(Set.of("areaType", "tflNumber"), originals.keySet());
  }

  @Test
  @DisplayName("beyond Draft with nothing on file the maps are empty, never null")
  void originalValues_submittedButNoSnapshotOnFile_isEmptyMapNotNull() {
    stub(
        "S",
        List.of(new RoadRecordRow(8003, "01", "01B", null, "note", 0)),
        List.of(new CostDetailRow(8003, new BigDecimal("10"), 100, null)));
    when(repository.findRoadRecordSnapshots(MILL, YEAR)).thenReturn(List.of());
    when(costSnapshots.findByRoadMaintenanceReports(List.of(8003L))).thenReturn(List.of());

    Schedule6Response doc = service.getSchedule6(MILL, YEAR, CallerRights.SUBMITTER);

    assertNotNull(doc.originalValues());
    assertTrue(doc.originalValues().isEmpty());
    assertNotNull(doc.roadRecords().get(0).originalValues());
    assertTrue(doc.roadRecords().get(0).originalValues().isEmpty());
  }
}
