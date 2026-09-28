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
 * Acceptance test — {@code POST /api/v1/check-status/schedule11/set-to-submit} moves the
 * silviculture track Verified → Submitted in one committed transaction, or refuses and writes
 * nothing (UC-CHK-019).
 *
 * <p>Fixtures ({@code R__62}): 824 (the happy path) is written here exactly once; 829 is the
 * rollback arm and can never succeed; 826, 827, 828 and 833 are refused and never written, and are
 * shared with {@code Schedule11SetToDraftIT} for that reason.
 *
 * <p><strong>Every "unchanged" is asserted by reading the value before and after.</strong>
 */
@DisplayName(
    "POST /api/v1/check-status/schedule11/set-to-submit — Schedule 11 Verified -> Submitted")
class Schedule11SetToSubmitIT extends Schedule11ReversalSupport {

  @Test
  @DisplayName(
      "824 silviculture V, 1-10 D -> 200 sch11SubmittedMsg; S, locations stamped, '11' at A; the"
          + " LICENSEE pair is NOT rewritten from the acting admin (deviation (S))")
  void happyPath_setsSchedule11BackToSubmitted() throws Exception {
    Map<String, Object> before = statusRow(824);
    Map<String, Object> pairsBefore = identityPairs(824);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(824);
    List<Map<String, Object>> costsBefore = locationCosts(824);
    Map<String, Object> oneToTenSummaryBefore =
        auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1701);
    Map<String, Object> oneToTenCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5951);
    Map<String, Object> neighbourLocationBefore =
        auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9461);
    Map<String, Object> neighbourCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5910);
    assertThat(before.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("V");
    assertThat(before.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("D");
    // The positive arm that could pass by accident: the recorded licensee must DIFFER from the
    // acting admin, who is xref'd to 824, so a legacy-style write of the admin would be visible.
    assertThat(pairsBefore.get("LICENSEE_USER_GUID"))
        .isEqualTo(LICENSEE_GUID)
        .isNotEqualTo(ADMIN_GUID);
    assertThat(pairsBefore.get("AUDITOR_USER_GUID")).isEqualTo(AUDITOR_GUID);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_MILL_USER_XREF"
                    + " WHERE ILCR_MILL_ID = 824 AND USER_GUID = ?",
                Integer.class,
                ADMIN_GUID))
        .isOne();

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 824, 2021))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    "{\"trackStatus\":\"S\",\"message\":{\"key\":\"sch11SubmittedMsg\",\"text\":\""
                        + SUBMITTED_TEXT
                        + "\"}}",
                    JsonCompareMode.STRICT));

    Map<String, Object> after = statusRow(824);
    assertThat(after.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(after.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo(before.get("ILCR_MILL_REPORT_STATUS_CODE"));
    // Deviation (S), extended: legacy would have written (824, ADMIN_GUID) into LICENSEE_*, the
    // pair both tracks share (SubmitReportDAO:405-409). Neither pair moves.
    assertThat(identityPairs(824)).isEqualTo(pairsBefore);
    assertThat(after.get("REPORT_COMPLETED_IND")).isEqualTo(before.get("REPORT_COMPLETED_IND"));
    assertThat(after.get("REVISION_COUNT")).isEqualTo(before.get("REVISION_COUNT"));
    assertThat(after.get("UPDATE_USERID")).isEqualTo(ACTING_USER);
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .isAfter((Timestamp) before.get("UPDATE_TIMESTAMP"));

    assertThat(oneToTenCategories(824)).isEqualTo(oneToTenBefore);
    Map<String, Object> eleven = categoryEleven(824);
    // Legacy's "VS" -> A (SubmitReportDAO:465-487): a withdrawn verification is indistinguishable
    // from a fresh submission at the category level.
    assertThat(eleven.get("CATEGORY_STATE_CODE")).isEqualTo("A");
    assertThat(((Number) eleven.get("REVISION_COUNT")).intValue()).isEqualTo(1);
    assertThat(eleven.get("UPDATE_USERID")).isEqualTo(ACTING_USER);

    List<Map<String, Object>> locations = locations(824);
    assertThat(locations).hasSize(2);
    for (Map<String, Object> row : locations) {
      assertThat(row.get("UPDATE_USERID")).as(row.toString()).isEqualTo(ACTING_USER);
      assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
    }
    List<Map<String, Object>> costs = locationCosts(824);
    assertThat(costs).hasSize(4);
    for (int i = 0; i < costs.size(); i++) {
      assertThat(costs.get(i).get("UPDATE_USERID")).isEqualTo(ACTING_USER);
      assertThat(costs.get(i).get("COST")).isEqualTo(costsBefore.get(i).get("COST"));
      assertThat(costs.get(i).get("REVISION_COUNT"))
          .isEqualTo(costsBefore.get(i).get("REVISION_COUNT"));
    }

    assertThat(auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1701))
        .as("824's Schedules 1-10 summary is not the Schedule 11 reversal's to stamp")
        .isEqualTo(oneToTenSummaryBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5951))
        .isEqualTo(oneToTenCostBefore);
    assertThat(auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9461))
        .as("826's Schedule 11 location is outside 824/2021")
        .isEqualTo(neighbourLocationBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5910))
        .isEqualTo(neighbourCostBefore);

    // Editability is unchanged by the transition: ADMIN edits at S as at V (16.1, DL-23); the
    // Licensee does not.
    mockMvc
        .perform(sweep(824).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("S")))
        .andExpect(jsonPath("$.schedules1To10.statusCode", is("D")));
    mockMvc
        .perform(schedule11Page(824).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(true)));
    mockMvc
        .perform(schedule11Page(824).with(canonicalSubmitter()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)));
  }

  @Test
  @DisplayName(
      "826 a location without a Planned Cost -> 409 reportNotSubmittedErrorMsg, nothing written")
  void gateFails_409_writesNothing() throws Exception {
    String before = footprint(826);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 826, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_SUBMITTED)));

    assertThat(footprint(826)).isEqualTo(before);
    mockMvc
        .perform(sweep(826).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("V")))
        .andExpect(jsonPath("$.schedule11.requirementsMet", is(false)));
  }

  @Test
  @DisplayName(
      "827 silviculture D -> 409 reportSubmissionErrorMsg: D->S is a legal pair, but it is the"
          + " Licensee's SUBMIT, not this endpoint's")
  void draft_409_genericText() throws Exception {
    String before = footprint(827);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 827, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(827)).isEqualTo(before);
  }

  @Test
  @DisplayName("828 silviculture S -> the S->S no-op is 409 reportSubmissionErrorMsg")
  void submitted_409_genericText() throws Exception {
    String before = footprint(828);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 828, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(828)).isEqualTo(before);
  }

  @Test
  @DisplayName("833 a NULL silviculture code -> 409 generic text, nothing written")
  void nullSilvicultureCode_409_genericText() throws Exception {
    String before = footprint(833);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 833, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(833)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "829 no category '11' row -> 500 reportSubmissionErrorMsg, the whole transition rolled back"
          + " — silviculture still V, no location stamped")
  void missingCategoryRow_500_rollsBack() throws Exception {
    Map<String, Object> before = statusRow(829);
    List<Map<String, Object>> categoriesBefore = categoryRows(829);
    List<Map<String, Object>> locationsBefore = locations(829);
    List<Map<String, Object>> costsBefore = locationCosts(829);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 829, 2021))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(statusRow(829)).isEqualTo(before);
    assertThat(statusRow(829).get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("V");
    assertThat(categoryRows(829)).isEqualTo(categoriesBefore);
    assertThat(locations(829)).isEqualTo(locationsBefore);
    assertThat(locationCosts(829)).isEqualTo(costsBefore);
  }

  // --- the same context guard as /set-to-submit

  @Test
  @DisplayName("missing millId -> 400 ERR-001 verbatim")
  void missingMillId_400() throws Exception {
    mockMvc
        .perform(post(SET_TO_SUBMIT_11).param("year", "2021").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 ERR-001")
  void nonNumericMillId_400() throws Exception {
    mockMvc
        .perform(post(SET_TO_SUBMIT_11).param("millId", "abc").param("year", "2021").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("known mill, year with no status row -> 404 Check Status not-found")
  void absentYear_404() throws Exception {
    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 826, 1999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("mill closed (CLS) for the year -> 409 ERR-002 verbatim, nothing written")
  void closedMill_409() throws Exception {
    String before = footprint(516);

    mockMvc
        .perform(reverse(SET_TO_SUBMIT_11, 516, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(ERR_002)));

    assertThat(footprint(516)).isEqualTo(before);
  }
}
