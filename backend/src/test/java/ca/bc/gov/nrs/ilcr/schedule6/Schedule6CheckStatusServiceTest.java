package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest.CheckEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
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
 * Unit tests for the {@link Schedule6Service} Check Status port (Story 8.2, AC7): the verbatim
 * legacy evaluation order and quirks — the cost mislabel travels as {@code field: "cost"} (the
 * composed label is the resolver's), {@code cost == 0} is MET (D2 null-only check), volume is never
 * checked (the evaluation seam takes no volume at all), the area-type flag is emitted but never
 * gates the schedule pass, placeholders are excluded (deviation (d)), and the
 * stored-data-unreachable TFL-missing branch (S10 — legacy view-state-only, story Completion Notes)
 * is pinned at the {@code evaluateRecord} seam. The service emits bundle KEYS with null text;
 * resolution/composition is {@code Schedule6CheckStatusResolver}'s and is byte-proven in {@link
 * Schedule6CheckStatusIT}. Pure JUnit + Mockito — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule6Service — Check Status (verbatim legacy port)")
class Schedule6CheckStatusServiceTest {

  private static final long MILL = 664L;
  private static final int YEAR = 2021;

  // Unused by the PAYLOAD tests — checkStatus(millId, year, request) never touches the repository,
  // which one of them asserts outright. The Story 15.0 stored-path tests at the bottom stub it.
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

  // ---- The full check over the submitted payload (the ENDPOINT's source) -----------------------
  // These three used to run over stubbed stored rows (RoadRecordRow/CostDetailRow) through the
  // storedCandidates path; Task 8 ported the aggregation-level behaviour they proved (mixed-results
  // structure, the all-met single banner, the vacuous pass) onto payloadCandidates instead of
  // dropping it — schedulePasses/the MET-vs-ISSUES banner logic is shared by both sources, so it
  // needed no changes, only a different candidate source in the test.
  //
  // Story 15.0 UPDATE: storedCandidates is back, as a SECOND source feeding checkStatusStored only
  // -- never the endpoint. The placeholder-exclusion and classification-trimming behaviours that
  // Task 8's note called retired are therefore live again, and are covered in the stored-path
  // section at the bottom of this class. A payload row still has no placeholder concept, which is
  // why those cases belong there and not here.

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
    assertNull(met.metMessage().text()); // key-only — the resolver resolves with the ordinal
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

  // ===============================================================================================
  // Story 15.0 Task 6 — the STORED-data path (checkStatusStored).
  //
  // A separate question from the endpoint's: "is what is SAVED complete?" rather than "is what I am
  // LOOKING AT complete?". These tests exist because the stored path has three failure modes the
  // payload path cannot have — placeholder rows, ordinal drift against the served document, and a
  // missing item-69 detail row — and none of them is reachable from a payload.
  // ===============================================================================================

  private static RoadRecordRow row(int id, String tsa, String tsb, String tfl) {
    return new RoadRecordRow(id, tsa, tsb, tfl, "general comment", 0);
  }

  private static CostDetailRow cost(int recordId, Integer cost) {
    return new CostDetailRow(recordId, new BigDecimal("100"), cost, null);
  }

  private void stored(List<RoadRecordRow> rows, List<CostDetailRow> costs) {
    when(repository.findRoadRecords(MILL, YEAR)).thenReturn(rows);
    when(repository.findCostDetails(MILL, YEAR)).thenReturn(costs);
  }

  @Test
  @DisplayName(
      "A mill whose ONLY Schedule 6 content is a general comment is a vacuous MET — the "
          + "placeholder row must NOT be reported as a failing road record")
  void checkStatusStored_excludesPlaceholderRows() {
    // The single most likely bug in this path: a placeholder has no area type, no supply block and
    // no cost, so without the exclusion it would report THREE findings against a row the screen
    // never shows (S18 / deviation (d)).
    stored(List.of(row(8401, null, null, null)), List.of());

    Schedule6CheckStatusResponse response = service.checkStatusStored(MILL, YEAR);

    assertEquals("MET", response.outcome());
    assertTrue(response.records().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", response.messages().get(0).key());
  }

  @Test
  @DisplayName(
      "A whitespace-only classification is a placeholder too — the read side trims, so this "
          + "path must trim identically or it reports a phantom row the document does not serve")
  void checkStatusStored_treatsWhitespaceClassificationAsPlaceholder() {
    stored(List.of(row(8402, "   ", " ", "\t")), List.of());

    assertEquals("MET", service.checkStatusStored(MILL, YEAR).outcome());
  }

  @Test
  @DisplayName(
      "rowCounter is the DISPLAY ordinal: a placeholder between two real rows consumes no "
          + "ordinal, and recordId stays the STORED id (unlike the payload path)")
  void checkStatusStored_ordinalIsTheDisplayPosition() {
    // Ordered by id, as the repository serves them: real, placeholder, real.
    stored(
        List.of(
            row(8410, "01", "01B", null),
            row(8411, null, null, null),
            row(8412, "03", "03B", null)),
        List.of()); // no cost details at all -> both real rows fail on cost

    Schedule6CheckStatusResponse response = service.checkStatusStored(MILL, YEAR);

    assertEquals("ISSUES", response.outcome());
    assertEquals(2, response.records().size());
    // The ordinals are 1 and 2 — NOT 1 and 3. Getting this wrong shifts the user-visible
    // "Road : N" bytes away from what the screen shows.
    assertEquals(1, response.records().get(0).rowCounter());
    assertEquals(2, response.records().get(1).rowCounter());
    assertEquals(8410, response.records().get(0).recordId());
    assertEquals(8412, response.records().get(1).recordId());
  }

  @Test
  @DisplayName("D2 on stored data: a stored cost of 0 PASSES; an absent item-69 row is a null cost")
  void checkStatusStored_zeroCostPassesButAbsentDetailFails() {
    stored(List.of(row(8420, "01", "01B", null)), List.of(cost(8420, 0)));
    assertEquals("MET", service.checkStatusStored(MILL, YEAR).outcome());

    // Same row, no detail row at all: cost is null, so it is a finding. Coercing the missing
    // detail to zero anywhere in the mapping would turn every unfilled cost into a pass.
    stored(List.of(row(8421, "01", "01B", null)), List.of());
    Schedule6CheckStatusResponse absent = service.checkStatusStored(MILL, YEAR);
    assertEquals("ISSUES", absent.outcome());
    assertEquals(List.of("cost"), fields(absent.records().get(0)));
  }

  @Test
  @DisplayName(
      "The TSA/TFL sides are derived exactly as the document serves them: a TFL row is checked "
          + "on its TFL number, a TSA row on its supply block")
  void checkStatusStored_derivesTheClassificationSides() {
    // TFL row: no TSA, a TFL code -> areaType "TFL", so the supply-block rule must NOT apply.
    stored(List.of(row(8430, null, null, "18")), List.of(cost(8430, 500)));
    assertEquals("MET", service.checkStatusStored(MILL, YEAR).outcome());

    // TSA row with no supply block -> the supplyBlock finding, and never a tflNumber one.
    stored(List.of(row(8431, "01", null, null)), List.of(cost(8431, 500)));
    Schedule6CheckStatusResponse tsa = service.checkStatusStored(MILL, YEAR);
    assertEquals(List.of("supplyBlock"), fields(tsa.records().get(0)));
  }

  @Test
  @DisplayName("The stored path emits KEYS with null text, same as the payload path — 15.0 AC 2")
  void checkStatusStored_emitsKeysOnly() {
    stored(List.of(row(8440, "01", "01B", null)), List.of());

    RoadRecordCheckResult verdict = service.checkStatusStored(MILL, YEAR).records().get(0);

    assertEquals("missingRequiredFieldMsg", verdict.issues().get(0).message().key());
    assertNull(verdict.issues().get(0).message().text());
  }

  @Test
  @DisplayName(
      "The four values the stored check judges are exactly what getSchedule6 SERVES for the same "
          + "rows — the anti-drift invariant, since the two derive them independently")
  void storedCandidatesMatchTheServedDocument() {
    // Named in Schedule6Service#storedCandidates' javadoc as the reason its TSA-vs-TFL derivation
    // is asserted equal to buildDocument's rather than extracted into a shared helper: this fails
    // if EITHER side drifts, which a shared helper could not detect.
    List<RoadRecordRow> rows =
        List.of(
            row(8450, "01", "01B", null), // TSA side
            row(8451, null, null, "18"), // TFL side
            row(8452, null, null, null), // placeholder — served by neither
            row(8453, "03", null, null)); // TSA, no supply block
    List<CostDetailRow> costs = List.of(cost(8450, 700), cost(8451, 0));
    stored(rows, costs);
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findTsaNumbers(MILL, YEAR)).thenReturn(List.of());
    when(repository.findSupplyBlocks(MILL, YEAR)).thenReturn(List.of());

    List<RoadRecord> served = service.getSchedule6(MILL, YEAR, true).roadRecords();
    // Force the ISSUES branch so every candidate appears in the response (the MET branch emits no
    // per-record results at all, so it could not carry this comparison).
    List<RoadRecordCheckResult> checked = service.checkStatusStored(MILL, YEAR).records();

    assertEquals(served.size(), checked.size(), "both paths must see the same rows");
    for (int i = 0; i < served.size(); i++) {
      RoadRecord document = served.get(i);
      RoadRecordCheckResult verdict = checked.get(i);
      assertEquals(document.recordId(), verdict.recordId(), "row " + i + " recordId");
      assertEquals(i + 1, verdict.rowCounter(), "row " + i + " display ordinal");
      // The verdict itself is the observable proof that the four judged values agree: reconstruct
      // it from the SERVED values and require the stored path to have reached the same finding set.
      assertEquals(
          Schedule6Service.evaluateRecord(
                  document.areaType(),
                  document.tflNumber(),
                  document.supplyBlock(),
                  document.cost())
              .stream()
              .map(FieldIssue::field)
              .toList(),
          fields(verdict),
          "row " + i + " findings must follow the values the document serves");
    }
  }
}
