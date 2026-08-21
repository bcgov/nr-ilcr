package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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

    RoadRecord record = service.getSchedule6(MILL, YEAR, true).roadRecords().get(0);

    assertEquals("15", record.rmg());
    assertEquals("01", record.areaType());
    assertEquals("01B", record.supplyBlock());
    assertNull(record.tflNumber());
    assertEquals(0, new BigDecimal("50.00").compareTo(record.costPerVolume()));
    assertEquals("note", record.comments());
    assertEquals(0, record.revisionCount());
  }

  @Test
  @DisplayName("TFL record — RMG from TFL code, areaType TFL, supplyBlock omitted")
  void tflRecord_derivesRmgFromTfl() {
    stub(
        "D",
        List.of(new RoadRecordRow(8002, null, null, "18", "GC", 0)),
        List.of(new CostDetailRow(8002, new BigDecimal("400"), 30000, null)));

    RoadRecord record = service.getSchedule6(MILL, YEAR, true).roadRecords().get(0);

    assertEquals("4", record.rmg());
    assertEquals("TFL", record.areaType());
    assertEquals("18", record.tflNumber());
    assertNull(record.supplyBlock());
    assertEquals(0, new BigDecimal("75.00").compareTo(record.costPerVolume()));
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

    List<RoadRecord> records = service.getSchedule6(MILL, YEAR, true).roadRecords();

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

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);

    assertEquals(4_000_000_000L, response.totalCost());
    assertEquals(0, new BigDecimal("20").compareTo(response.totalVolume()));
  }

  @Test
  @DisplayName(
      "lone-comment placeholder — excluded from records, supplies generalComments, zero totals")
  void placeholder_excludedButGeneralCommentKept() {
    stub("D", List.of(new RoadRecordRow(8004, null, null, null, "Only a comment", 0)), List.of());

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);

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

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);
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

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);
    RoadRecord record = response.roadRecords().get(0);

    // normalizeVolume strips only trailing zeros: 400.50 -> 400.5 (not 400 and not 4.005E+2).
    assertEquals(new BigDecimal("400.5"), record.volume());
    assertEquals(new BigDecimal("400.5"), response.totalVolume());
    // 1000 / 400.5 = 2.4968... -> 2.50 at scale 2 HALF_UP (legacy CoreUtil.bigDecimalDivision).
    assertEquals(new BigDecimal("2.50"), record.costPerVolume());
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
    assertEquals(" last ", service.getSchedule6(MILL, YEAR, true).generalComments());

    stub(
        "D",
        List.of(
            new RoadRecordRow(8001, "01", "01B", null, "first", 0),
            new RoadRecordRow(8002, "03", "03B", null, null, 0)),
        List.of());
    assertNull(service.getSchedule6(MILL, YEAR, true).generalComments());
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

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);

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

    Schedule6Response response = service.getSchedule6(MILL, YEAR, true);

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

    RoadRecord record = service.getSchedule6(MILL, YEAR, true).roadRecords().get(0);

    assertEquals("01", record.areaType());
    assertEquals("01B", record.supplyBlock());
    assertNull(record.tflNumber());
    assertEquals("15", record.rmg());
  }

  @Test
  @DisplayName("editable = callerMayEdit AND trackStatus Draft (server-authoritative)")
  void editableMatrix() {
    assertTrue(editableFor("D", true), "Draft + mayEdit");
    assertFalse(editableFor("D", false), "Draft + !mayEdit");
    assertFalse(editableFor("S", true), "Submitted + mayEdit");
    assertFalse(editableFor(null, true), "no status + mayEdit");
  }

  private boolean editableFor(String trackStatus, boolean callerMayEdit) {
    stub(trackStatus, List.of(), List.of());
    return service.getSchedule6(MILL, YEAR, callerMayEdit).editable();
  }
}
