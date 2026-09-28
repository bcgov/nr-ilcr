package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

/**
 * Acceptance test — {@code POST /api/v1/check-status/schedule11/set-to-draft} moves the
 * silviculture track Submitted → Draft in one committed transaction, or refuses and writes nothing
 * (UC-CHK-017).
 *
 * <p>Fixtures ({@code R__62}): 823 (the happy path) and 834 (zero locations) are each written here
 * exactly once; 828 is the rollback arm and can never succeed; 825 is refused and never written;
 * 827, 829 and 833 are refused and never written, and are shared with {@code
 * Schedule11SetToSubmitIT} for that reason.
 *
 * <p>This class carries the full context-guard set (missing, blank and non-numeric params, unknown
 * mill, absent year, closed mill). Both endpoints go through the controller's one {@code
 * reverseOnTrack}, so {@code Schedule11SetToSubmitIT} repeats only a representative subset.
 *
 * <p><strong>Every "unchanged" is asserted by reading the value before and after.</strong>
 */
@DisplayName("POST /api/v1/check-status/schedule11/set-to-draft — Schedule 11 Submitted -> Draft")
class Schedule11SetToDraftIT extends Schedule11ReversalSupport {

  @Test
  @DisplayName(
      "823 silviculture S, 1-10 V -> 200 sch11DraftMsg; D, locations stamped, '11' at D; no"
          + " identity pair written and the 1-10 track byte-unchanged")
  void happyPath_setsSchedule11BackToDraft() throws Exception {
    Map<String, Object> before = statusRow(823);
    Map<String, Object> pairsBefore = identityPairs(823);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(823);
    List<Map<String, Object>> costsBefore = locationCosts(823);
    // Out of scope, each read by key: 823's own Schedules 1-10 row family (R__62 seeds it for
    // exactly this), and a neighbour mill's Schedule 11 location and cost. A reversal that also ran
    // the twenty 1-10 touches, or whose touch lost its mill/year predicate, stamps one of these.
    Map<String, Object> oneToTenSummaryBefore =
        auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1700);
    Map<String, Object> oneToTenCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5950);
    Map<String, Object> neighbourLocationBefore =
        auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9460);
    Map<String, Object> neighbourCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5908);
    assertThat(before.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(before.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("V");
    // Both pairs seeded non-null, so "unchanged" cannot pass on columns that were simply empty.
    assertThat(pairsBefore.get("LICENSEE_USER_GUID")).isEqualTo(LICENSEE_GUID);
    assertThat(pairsBefore.get("AUDITOR_USER_GUID")).isEqualTo(AUDITOR_GUID);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 823, 2021))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    "{\"trackStatus\":\"D\",\"message\":{\"key\":\"sch11DraftMsg\",\"text\":\""
                        + DRAFT_TEXT
                        + "\"}}",
                    JsonCompareMode.STRICT));

    Map<String, Object> after = statusRow(823);
    assertThat(after.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("D");
    assertThat(after.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo(before.get("ILCR_MILL_REPORT_STATUS_CODE"));
    // Legacy skipped the association block for a 'D' target (SubmitReportDAO:403).
    assertThat(identityPairs(823)).isEqualTo(pairsBefore);
    assertThat(after.get("REPORT_COMPLETED_IND")).isEqualTo(before.get("REPORT_COMPLETED_IND"));
    // The 1-10 reversal shape: no REVISION_COUNT bump.
    assertThat(after.get("REVISION_COUNT")).isEqualTo(before.get("REVISION_COUNT"));
    assertThat(after.get("UPDATE_USERID")).isEqualTo(ACTING_USER);
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .isAfter((Timestamp) before.get("UPDATE_TIMESTAMP"));

    // Categories '1'..'10' read back identical, value for value; '11' moved A -> D.
    assertThat(oneToTenCategories(823)).isEqualTo(oneToTenBefore);
    Map<String, Object> eleven = categoryEleven(823);
    assertThat(eleven.get("CATEGORY_STATE_CODE")).isEqualTo("D");
    assertThat(((Number) eleven.get("REVISION_COUNT")).intValue()).isEqualTo(1);
    assertThat(eleven.get("UPDATE_USERID")).isEqualTo(ACTING_USER);

    // Every location and every cost under it stamped, no revision or value moved.
    List<Map<String, Object>> locations = locations(823);
    assertThat(locations).hasSize(2);
    for (Map<String, Object> row : locations) {
      assertThat(row.get("UPDATE_USERID")).as(row.toString()).isEqualTo(ACTING_USER);
      assertThat(row.get("UPDATE_TIMESTAMP")).isNotNull();
      assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
    }
    List<Map<String, Object>> costs = locationCosts(823);
    assertThat(costs).hasSize(4);
    for (int i = 0; i < costs.size(); i++) {
      assertThat(costs.get(i).get("UPDATE_USERID")).isEqualTo(ACTING_USER);
      assertThat(costs.get(i).get("COST")).isEqualTo(costsBefore.get(i).get("COST"));
      assertThat(costs.get(i).get("REVISION_COUNT"))
          .isEqualTo(costsBefore.get(i).get("REVISION_COUNT"));
    }

    assertThat(auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1700))
        .as("823's Schedules 1-10 summary is not the Schedule 11 reversal's to stamp")
        .isEqualTo(oneToTenSummaryBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5950))
        .isEqualTo(oneToTenCostBefore);
    assertThat(auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9460))
        .as("825's Schedule 11 location is outside 823/2021")
        .isEqualTo(neighbourLocationBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5908))
        .isEqualTo(neighbourCostBefore);

    // The page's re-sweep, and editability per the 16.1 matrix: the Licensee edits again at D, the
    // ministry no longer does (BR-05, STA-001).
    mockMvc
        .perform(sweep(823).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("D")))
        .andExpect(jsonPath("$.schedules1To10.statusCode", is("V")));
    mockMvc
        .perform(schedule11Page(823).with(canonicalSubmitter()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)));
    mockMvc
        .perform(schedule11Page(823).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(false)));
  }

  @Test
  @DisplayName(
      "825 a location without a Planned Cost -> 409 reportNotSubmittedErrorMsg, nothing written,"
          + " the flag visible on re-sweep")
  void gateFails_409_writesNothing() throws Exception {
    String before = footprint(825);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 825, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_SUBMITTED)));

    assertThat(footprint(825)).isEqualTo(before);
    mockMvc
        .perform(sweep(825).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("S")))
        .andExpect(jsonPath("$.schedule11.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.schedule11.schedules[0].verdict.errors[0].text",
                is("location  : Unfinished Draft Block - Planned cost: Value Required")));
  }

  @Test
  @DisplayName("827 silviculture D -> the D->D no-op is 409 reportSubmissionErrorMsg")
  void draft_409_genericText() throws Exception {
    String before = footprint(827);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 827, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(827)).isEqualTo(before);
  }

  @Test
  @DisplayName("829 silviculture V -> the V->D jump is 409 reportSubmissionErrorMsg")
  void verified_409_genericText() throws Exception {
    String before = footprint(829);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 829, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(829)).isEqualTo(before);
  }

  @Test
  @DisplayName("833 a NULL silviculture code -> 409 generic text, nothing written")
  void nullSilvicultureCode_409_genericText() throws Exception {
    assertThat(statusRow(833).get("MILL_SILVICULTUR_STATUS_CODE")).isNull();
    String before = footprint(833);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 833, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(833)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "828 no category '11' row -> 500 reportSubmissionErrorMsg, the whole transition rolled back"
          + " — silviculture still S, no location stamped")
  void missingCategoryRow_500_rollsBack() throws Exception {
    Map<String, Object> before = statusRow(828);
    List<Map<String, Object>> categoriesBefore = categoryRows(828);
    List<Map<String, Object>> locationsBefore = locations(828);
    List<Map<String, Object>> costsBefore = locationCosts(828);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 828, 2021))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    // The status UPDATE and both touches ran before the category advance found no row, so this is
    // the transaction boundary under test, not just an early refusal.
    assertThat(statusRow(828)).isEqualTo(before);
    assertThat(statusRow(828).get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(categoryRows(828)).isEqualTo(categoriesBefore);
    assertThat(locations(828)).isEqualTo(locationsBefore);
    assertThat(locationCosts(828)).isEqualTo(costsBefore);
  }

  @Test
  @DisplayName("834 zero locations is vacuously MET -> 200, category '11' at D")
  void zeroLocations_vacuouslyMet() throws Exception {
    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 834, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.message.key", is("sch11DraftMsg")));

    assertThat(statusRow(834).get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("D");
    assertThat(categoryEleven(834).get("CATEGORY_STATE_CODE")).isEqualTo("D");
  }

  // --- the same context guard as /set-to-draft

  @Test
  @DisplayName("missing millId -> 400 ERR-001 verbatim, trailing space included")
  void missingMillId_400() throws Exception {
    mockMvc
        .perform(post(SET_TO_DRAFT_11).param("year", "2021").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("blank millId -> 400 ERR-001")
  void blankMillId_400() throws Exception {
    mockMvc
        .perform(post(SET_TO_DRAFT_11).param("millId", " ").param("year", "2021").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("non-numeric year -> 400 ERR-001, never Spring's own 400")
  void nonNumericYear_400() throws Exception {
    mockMvc
        .perform(
            post(SET_TO_DRAFT_11).param("millId", "825").param("year", "twenty21").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("known mill, year with no status row -> 404 Check Status not-found")
  void absentYear_404() throws Exception {
    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 825, 1999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("unknown mill -> 404 Check Status not-found")
  void unknownMill_404() throws Exception {
    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 999999, 2021))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("mill closed (CLS) for the year -> 409 ERR-002 verbatim, nothing written")
  void closedMill_409() throws Exception {
    String before = footprint(516);

    mockMvc
        .perform(reverse(SET_TO_DRAFT_11, 516, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(ERR_002)));

    assertThat(footprint(516)).isEqualTo(before);
  }
}
