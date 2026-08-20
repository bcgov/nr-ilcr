package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link Schedule6Service} Check Status port (Story 8.2, AC7): the verbatim
 * legacy evaluation order and quirks — the cost mislabel travels as {@code field: "cost"} (the
 * composed label is the controller's), {@code cost == 0} is MET (D2 null-only check), volume is
 * never checked (the evaluation seam takes no volume at all), the area-type flag is emitted but
 * never gates the schedule pass, placeholders are excluded (deviation (d)), and the
 * stored-data-unreachable TFL-missing branch (S10 — legacy view-state-only, story Completion Notes)
 * is pinned at the {@code evaluateRecord} seam. The service emits bundle KEYS with null text;
 * resolution/composition is {@code Schedule6Controller}'s and is byte-proven in {@link
 * Schedule6CheckStatusIT}. Pure JUnit + Mockito — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule6Service — Check Status (verbatim legacy port)")
class Schedule6CheckStatusServiceTest {

  private static final long MILL = 664L;
  private static final int YEAR = 2021;

  @Mock private Schedule6Repository repository;

  @InjectMocks private Schedule6Service service;

  private void stub(List<RoadRecordRow> rows, List<CostDetailRow> details) {
    when(repository.findRoadRecords(MILL, YEAR)).thenReturn(rows);
    lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(details);
  }

  private static List<String> fields(RoadRecordCheckResult record) {
    return record.issues().stream().map(FieldIssue::field).toList();
  }

  // ---- The evaluation seam (legacy Schedule6MB.checkStatus() order + branches) -----------------

  @Test
  @DisplayName(
      "S10 seam: a TFL-classified record with a blank TFL number emits the tflNumber issue "
          + "(the branch is unreachable from stored rows — ported verbatim, pinned here)")
  void evaluateRecord_tflMissingNumber() {
    List<FieldIssue> issues = Schedule6Service.evaluateRecord("TFL", null, null, 5000);
    assertEquals(List.of("tflNumber"), issues.stream().map(FieldIssue::field).toList());
    assertEquals("missingRequiredFieldMsg", issues.get(0).message().key());
    assertNull(issues.get(0).message().text());
  }

  @Test
  @DisplayName("Issue order is the legacy order: type, then TFL/Supply Block, then cost")
  void evaluateRecord_legacyOrder() {
    List<FieldIssue> issues = Schedule6Service.evaluateRecord(null, null, null, null);
    assertEquals(
        List.of("areaType", "supplyBlock", "cost"),
        issues.stream().map(FieldIssue::field).toList());
  }

  @Test
  @DisplayName("The TSA side checks Supply Block, the TFL side never does (and vice versa)")
  void evaluateRecord_sidesAreExclusive() {
    assertEquals(
        List.of("supplyBlock"),
        Schedule6Service.evaluateRecord("01", null, null, 100).stream()
            .map(FieldIssue::field)
            .toList());
    assertEquals(
        List.of(),
        Schedule6Service.evaluateRecord("TFL", "18", null, 100).stream()
            .map(FieldIssue::field)
            .toList());
  }

  @Test
  @DisplayName("D2 quirk: cost == 0 is MET (null-only check); a null cost is the only cost finding")
  void evaluateRecord_zeroCostIsMet() {
    assertTrue(Schedule6Service.evaluateRecord("01", null, "01B", 0).isEmpty());
    assertEquals(
        List.of("cost"),
        Schedule6Service.evaluateRecord("01", null, "01B", null).stream()
            .map(FieldIssue::field)
            .toList());
  }

  @Test
  @DisplayName("isScheduleValid quirk: the area-type flag is emitted but never gates the pass")
  void areaTypeFlag_doesNotGateThePass() {
    // A record with no area type but a supply block and a cost: flagged by the evaluation...
    assertEquals(
        List.of("areaType"),
        Schedule6Service.evaluateRecord(null, null, "01B", 100).stream()
            .map(FieldIssue::field)
            .toList());
    // ...yet it PASSES the schedule gate (legacy isScheduleValid ignores missingTsaNumberOnCheck).
    assertTrue(Schedule6Service.recordPasses(null, null, "01B", 100));
    // The gate's real inputs: TFL needs a number, TSA needs a supply block, both need cost.
    assertFalse(Schedule6Service.recordPasses("TFL", null, "01B", 100));
    assertFalse(Schedule6Service.recordPasses("01", null, null, 100));
    assertFalse(Schedule6Service.recordPasses("01", null, "01B", null));
    assertTrue(Schedule6Service.recordPasses("TFL", "18", null, 0));
  }

  // ---- The full check over stored rows ---------------------------------------------------------

  @Test
  @DisplayName(
      "Mixed results: placeholder excluded, 1-based rowCounters, met banner only for clean "
          + "records, keys-with-null-text emitted for the controller to resolve")
  void checkStatus_mixedResults() {
    stub(
        List.of(
            new RoadRecordRow(8324, null, null, null, "comment", 0), // placeholder
            new RoadRecordRow(8325, "01", "01B", null, "comment", 0), // met
            new RoadRecordRow(8326, "03", "03B", null, "comment", 0)), // missing cost
        List.of(new CostDetailRow(8325, new BigDecimal("800"), 25000, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", response.outcome());
    assertTrue(response.messages().isEmpty());
    assertEquals(2, response.records().size());

    RoadRecordCheckResult met = response.records().get(0);
    assertEquals(8325, met.recordId());
    assertEquals(1, met.rowCounter()); // the placeholder shifted nothing — counters are 1-based
    assertTrue(met.met());
    assertEquals("roadRequirementsMetMsg", met.metMessage().key());
    assertNull(met.metMessage().text()); // key-only — the controller resolves with the ordinal
    assertTrue(met.issues().isEmpty());

    RoadRecordCheckResult flagged = response.records().get(1);
    assertEquals(8326, flagged.recordId());
    assertEquals(2, flagged.rowCounter());
    assertFalse(flagged.met());
    assertNull(flagged.metMessage());
    assertEquals(List.of("cost"), fields(flagged));
  }

  @Test
  @DisplayName("All records pass -> MET, the single schedule banner, NO per-record results at all")
  void checkStatus_allMet() {
    stub(
        List.of(new RoadRecordRow(8322, "01", "01B", null, null, 0)),
        List.of(new CostDetailRow(8322, null, 50000, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR);

    assertEquals("MET", response.outcome());
    assertEquals(1, response.messages().size());
    assertEquals("scheduleRequirementsMetMsg", response.messages().get(0).key());
    assertNull(response.messages().get(0).text());
    assertTrue(response.records().isEmpty());
  }

  @Test
  @DisplayName("Zero records and lone-comment (deviation (d)) are both the vacuous pass")
  void checkStatus_vacuousPasses() {
    stub(List.of(), List.of());
    assertEquals("MET", service.checkStatus(MILL, YEAR).outcome());

    stub(List.of(new RoadRecordRow(8324, null, null, null, "only a comment", 0)), List.of());
    Schedule6CheckStatusResponse loneComment = service.checkStatus(MILL, YEAR);
    assertEquals("MET", loneComment.outcome());
    assertTrue(loneComment.records().isEmpty());
  }

  @Test
  @DisplayName(
      "Blank-but-not-null classification codes are trimmed like the read side "
          + "(a whitespace TSA row with a TFL code is a TFL record)")
  void checkStatus_trimsLikeTheReadSide() {
    stub(
        List.of(new RoadRecordRow(8329, " ", " ", "18", null, 0)),
        List.of(new CostDetailRow(8329, null, 100, null)));

    assertEquals("MET", service.checkStatus(MILL, YEAR).outcome());
  }

  @Test
  @DisplayName("Read-only: the check never touches a write repository method (mutates nothing)")
  void checkStatus_onlyReads() {
    stub(List.of(), List.of());
    service.checkStatus(MILL, YEAR);
    verify(repository).findRoadRecords(MILL, YEAR);
    verify(repository).findCostDetails(MILL, YEAR);
    // Mockito verifies no OTHER interactions happened — any write call would fail here.
    org.mockito.Mockito.verifyNoMoreInteractions(repository);
  }
}
