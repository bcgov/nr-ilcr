package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest.CheckEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
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

  // Unused by every remaining test (Task 8: checkStatus is payload-only and never touches the
  // repository), but @InjectMocks still needs a Schedule6Repository to construct the service.
  @Mock private Schedule6Repository repository;

  @InjectMocks private Schedule6Service service;

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

  // ---- The full check over the submitted payload (Task 6/Task 8: the ONLY source now) -----------
  // These three used to run over stubbed stored rows (RoadRecordRow/CostDetailRow) through the
  // since-retired storedCandidates path; Task 8 ports the aggregation-level behaviour they proved
  // (mixed-results structure, the all-met single banner, the vacuous pass) onto payloadCandidates
  // instead of dropping it — schedulePasses/the MET-vs-ISSUES banner logic is shared by both
  // sources, so it needed no changes, only a different candidate source in the test. The
  // placeholder-exclusion and read-side classification-trimming behaviours these tests used to also
  // cover are retired along with storedCandidates: a payload row carries no placeholder concept and
  // no derived classification to trim — it is exactly what the caller typed.

  @Test
  @DisplayName(
      "Mixed results: 1-based rowCounters/recordIds are the payload ordinal, met banner only "
          + "for clean records, keys-with-null-text emitted for the controller to resolve")
  void checkStatus_mixedResults() {
    Schedule6CheckRequest request =
        new Schedule6CheckRequest(
            null,
            List.of(
                new CheckEntry("01", null, "01B", null, 25000, null), // met
                new CheckEntry("03", null, "03B", null, null, null))); // missing cost

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);

    assertEquals("ISSUES", response.outcome());
    assertTrue(response.messages().isEmpty());
    assertEquals(2, response.records().size());

    RoadRecordCheckResult met = response.records().get(0);
    assertEquals(1, met.recordId());
    assertEquals(1, met.rowCounter());
    assertTrue(met.met());
    assertEquals("roadRequirementsMetMsg", met.metMessage().key());
    assertNull(met.metMessage().text()); // key-only — the controller resolves with the ordinal
    assertTrue(met.issues().isEmpty());

    RoadRecordCheckResult flagged = response.records().get(1);
    assertEquals(2, flagged.recordId());
    assertEquals(2, flagged.rowCounter());
    assertFalse(flagged.met());
    assertNull(flagged.metMessage());
    assertEquals(List.of("cost"), fields(flagged));
  }

  @Test
  @DisplayName("All records pass -> MET, the single schedule banner, NO per-record results at all")
  void checkStatus_allMet() {
    Schedule6CheckRequest request =
        new Schedule6CheckRequest(
            null, List.of(new CheckEntry("01", null, "01B", null, 50000, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);

    assertEquals("MET", response.outcome());
    assertEquals(1, response.messages().size());
    assertEquals("scheduleRequirementsMetMsg", response.messages().get(0).key());
    assertNull(response.messages().get(0).text());
    assertTrue(response.records().isEmpty());
  }

  @Test
  @DisplayName("Zero submitted rows -> the vacuous pass (the legacy loop never runs)")
  void checkStatus_vacuousPasses() {
    Schedule6CheckRequest request = new Schedule6CheckRequest(null, List.of());
    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);
    assertEquals("MET", response.outcome());
    assertTrue(response.records().isEmpty());
  }

  // ---- Task 6: a request evaluates the PAYLOAD, never the repository ----------------------------

  @Test
  @DisplayName(
      "A non-null request never touches the repository at all — the verdict is built entirely "
          + "from request.records(), not the stored rows (the whole point of Task 6)")
  void checkStatus_withRequest_neverReadsTheRepository() {
    Schedule6CheckRequest request =
        new Schedule6CheckRequest(
            null, List.of(new CheckEntry("01", null, "01B", null, 5000, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);

    assertEquals("MET", response.outcome());
    org.mockito.Mockito.verifyNoInteractions(repository);
  }

  @Test
  @DisplayName(
      "The verdict reflects the SUBMITTED values: a payload row with a cleared cost is ISSUES "
          + "even though the (unqueried) stored row would have had one")
  void checkStatus_withRequest_evaluatesSubmittedValues() {
    Schedule6CheckRequest request =
        new Schedule6CheckRequest(
            null, List.of(new CheckEntry("01", null, "01B", null, null, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);

    assertEquals("ISSUES", response.outcome());
    assertEquals(1, response.records().size());
    assertEquals(List.of("cost"), fields(response.records().get(0)));
  }

  @Test
  @DisplayName(
      "Payload rows have no recordId: the 1-based payload ordinal is served in BOTH recordId and "
          + "rowCounter (the Task 6 recordId decision — see Schedule6CheckRequest's javadoc)")
  void checkStatus_withRequest_recordIdIsThePayloadOrdinal() {
    Schedule6CheckRequest request =
        new Schedule6CheckRequest(
            null,
            List.of(
                new CheckEntry("01", null, "01B", null, null, null),
                new CheckEntry("03", null, "03B", null, null, null)));

    Schedule6CheckStatusResponse response = service.checkStatus(MILL, YEAR, request);

    assertEquals(2, response.records().size());
    RoadRecordCheckResult first = response.records().get(0);
    RoadRecordCheckResult second = response.records().get(1);
    assertEquals(1, first.recordId());
    assertEquals(1, first.rowCounter());
    assertEquals(2, second.recordId());
    assertEquals(2, second.rowCounter());
  }
}
