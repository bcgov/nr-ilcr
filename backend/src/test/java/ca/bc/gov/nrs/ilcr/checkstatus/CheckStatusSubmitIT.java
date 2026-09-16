package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 15.3 AC 1/2/3/4/7. {@code POST /api/v1/check-status/submit} moves the
 * Schedules 1–10 track Draft → Submitted in one committed transaction, or refuses and writes
 * nothing. Security OFF: the mock {@code ILCR_SUBMITTER} holds {@code SUBMIT_REPORT} and carries
 * the canonical submitter's GUID ({@code ilcr.security.mock-user-guid}), which {@code R__70}
 * associates to every seeded mill — so the licensee lookup finds a row.
 *
 * <p>Fixtures ({@code R__55}): mill 780 is submitted here exactly once (AC 1) and mill 782 is the
 * rollback case (AC 7, its category row {@code '7'} removed first); both are owned solely by this
 * class. The refusal cases use read-only anchors — 515/2021 (Draft, empty → Schedules 1/2/3 fail),
 * 517/2021 ({@code S}) and 737/2021 ({@code V}) — and prove "nothing written" by fingerprinting row
 * counts, revision sums and the latest audit timestamp of every schedule table plus the mill's own
 * status and category rows.
 *
 * <p>The test schema carries no audit triggers, so the statement ORDER that earns Story 16.2's
 * {@code S} snapshot cannot be observed here — {@code ReportTrackTransitionServiceTest} pins it;
 * this class proves the EFFECTS: every touched row's audit columns moved and every category reads
 * {@code A}.
 */
@DisplayName("POST /api/v1/check-status/submit — the Draft -> Submitted transition (Story 15.3)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class CheckStatusSubmitIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/submit";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SUBMITTED = "Schedules 1-10 are successfully submitted.";
  private static final String NOT_SUBMITTED =
      "The report cannot be submitted. One or more of the Schedules have not passed validation."
          + " Please review and correct any errors.";
  private static final String SUBMISSION_ERROR =
      "An error has been found submitting schedules. The error details have been logged. Please"
          + " contact ILCR application support.";

  /** The mock principal's audit name for the default SUBMITTER role (MockPrincipalFilter). */
  private static final String MOCK_USER = "dev-submitter";

  private static final List<String> SCHEDULE_TABLES =
      List.of(
          "ILCR_REPORT_SUMMARY",
          "ILCR_COST_REPORT_DETAIL",
          "TRANSPORTATION_REPORT",
          "CAMP_REPORT",
          "ROAD_MAINTENANCE_REPORT",
          "BRIDGE_REPORT",
          "CULVERT_REPORT",
          "TREE_TO_TRUCK_REPORT",
          "TREE_TO_TRUCK_DETAIL_REPORT",
          "TREE_TO_TRUCK_RATE_DETAIL",
          "CONTRACTUAL_WORK_REPORT",
          "ROAD_CONSTRUCTION_REPRT",
          "ROAD_CONSTRUCTION_REPRT_DTL",
          "BASIC_SILVICULTURE_REPORT",
          "ILCR_MILL_REPORT_STATUS",
          "ILCR_REPORT_CATEGORY");

  @Autowired private JdbcTemplate jdbc;

  private static MockHttpServletRequestBuilder submit(long mill, int year) {
    return post(ENDPOINT)
        .param("millId", String.valueOf(mill))
        .param("year", String.valueOf(year))
        .accept(MediaType.APPLICATION_JSON);
  }

  /**
   * Everything a refused submit must leave alone, folded into one comparable string: per table the
   * row count, the revision sum and the latest UPDATE_TIMESTAMP (a touch moves the last two without
   * changing the first), plus the mill/year's whole status row and every category row.
   */
  private String footprint(long mill, int year) {
    StringBuilder f = new StringBuilder();
    for (String table : SCHEDULE_TABLES) {
      f.append(table)
          .append('=')
          .append(
              jdbc.queryForObject(
                  "SELECT COUNT(*) || '/' || NVL(SUM(REVISION_COUNT), 0) || '/'"
                      + " || NVL(TO_CHAR(CAST(MAX(UPDATE_TIMESTAMP) AS TIMESTAMP),"
                      + " 'YYYY-MM-DD HH24:MI:SS.FF6'), '-')"
                      + " FROM THE."
                      + table,
                  String.class))
          .append(';');
    }
    f.append("STATUS=").append(statusRow(mill, year)).append(';');
    f.append("CATEGORIES=").append(categoryRows(mill, year));
    return f.toString();
  }

  private Map<String, Object> statusRow(long mill, int year) {
    return jdbc.queryForMap(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, REPORT_COMPLETED_IND,"
            + " LICENSEE_MILL_ID, LICENSEE_USER_GUID, AUDITOR_MILL_ID, AUDITOR_USER_GUID,"
            + " REVISION_COUNT, UPDATE_USERID, UPDATE_TIMESTAMP, ENTRY_TIMESTAMP"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ?",
        mill,
        year);
  }

  private List<Map<String, Object>> categoryRows(long mill, int year) {
    return jdbc.queryForList(
        "SELECT ILCR_CATEGORY_ID, CATEGORY_STATE_CODE, REVISION_COUNT, UPDATE_USERID,"
            + " UPDATE_TIMESTAMP FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ? ORDER BY TO_NUMBER(ILCR_CATEGORY_ID)",
        mill,
        year);
  }

  @Test
  @DisplayName(
      "AC 1/4: 780/2021 Draft + all ten MET -> 200 sch1-10SubmittedMsg; status S, licensee recorded,"
          + " every row touched, categories '1'..'10' at A, '11' untouched")
  void happyPath_submitsInOneTransaction() throws Exception {
    Map<String, Object> before = statusRow(780, 2021);
    assertThat(before.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("D");

    mockMvc
        .perform(submit(780, 2021))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message.key", is("sch1-10SubmittedMsg")))
        .andExpect(jsonPath("$.message.text", is(SUBMITTED)));

    // The status row (BR-05, BR-08, D13).
    Map<String, Object> after = statusRow(780, 2021);
    assertThat(after.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("S");
    assertThat(((Number) after.get("LICENSEE_MILL_ID")).longValue()).isEqualTo(780L);
    assertThat(after.get("LICENSEE_USER_GUID")).isEqualTo(CANONICAL_SUBMITTER_GUID);
    assertThat(after.get("AUDITOR_MILL_ID")).isNull();
    assertThat(after.get("AUDITOR_USER_GUID")).isNull();
    assertThat(after.get("UPDATE_USERID")).isEqualTo(MOCK_USER);
    assertThat(((Number) after.get("REVISION_COUNT")).intValue()).isEqualTo(1);
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .isAfter((Timestamp) before.get("UPDATE_TIMESTAMP"));
    assertThat(after.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("D");
    assertThat(after.get("REPORT_COMPLETED_IND")).isEqualTo("N");

    // The ten category rows advanced (BR-04, D12: @Version -> revision moves); '11' untouched.
    List<Map<String, Object>> categories = categoryRows(780, 2021);
    assertThat(categories).hasSize(11);
    for (Map<String, Object> row : categories.subList(0, 10)) {
      assertThat(row.get("CATEGORY_STATE_CODE")).as(row.toString()).isEqualTo("A");
      assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isEqualTo(1);
      assertThat(row.get("UPDATE_USERID")).isEqualTo(MOCK_USER);
    }
    Map<String, Object> eleven = categories.get(10);
    assertThat(eleven.get("ILCR_CATEGORY_ID")).isEqualTo("11");
    assertThat(eleven.get("CATEGORY_STATE_CODE")).isEqualTo("D");
    assertThat(((Number) eleven.get("REVISION_COUNT")).intValue()).isZero();
    assertThat(eleven.get("UPDATE_USERID")).isEqualTo("SEED");

    // Every Schedule 1/2/3 row touched: the summaries bump the revision (@Version in legacy), the
    // cost details move audit columns only (AC 4 effects, D12).
    List<Map<String, Object>> summaries =
        jdbc.queryForList(
            "SELECT ILCR_REPORT_SUMMARY_ID, REVISION_COUNT, UPDATE_USERID, UPDATE_TIMESTAMP"
                + " FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_MILL_ID = 780 AND REPORT_YEAR = 2021");
    assertThat(summaries).hasSize(3);
    for (Map<String, Object> s : summaries) {
      assertThat(((Number) s.get("REVISION_COUNT")).intValue()).isEqualTo(1);
      assertThat(s.get("UPDATE_USERID")).isEqualTo(MOCK_USER);
      assertThat(s.get("UPDATE_TIMESTAMP")).isNotNull();
    }
    Map<String, Object> details =
        jdbc.queryForMap(
            "SELECT COUNT(*) TOTAL, SUM(CASE WHEN UPDATE_USERID = ? THEN 1 ELSE 0 END) TOUCHED,"
                + " SUM(REVISION_COUNT) REVISIONS"
                + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_REPORT_SUMMARY_ID IN"
                + " (SELECT ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_REPORT_SUMMARY"
                + "   WHERE ILCR_MILL_ID = 780 AND REPORT_YEAR = 2021)",
            MOCK_USER);
    assertThat(((Number) details.get("TOTAL")).intValue()).isEqualTo(36);
    assertThat(((Number) details.get("TOUCHED")).intValue()).isEqualTo(36);
    assertThat(((Number) details.get("REVISIONS")).intValue()).isZero();

    // The sweep now reports Submitted and withdraws the offer (AC 1 last clause, AC 9).
    mockMvc
        .perform(
            get("/api/v1/check-status")
                .param("millId", "780")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules1To10.statusCode", is("S")))
        .andExpect(jsonPath("$.schedules1To10.canSubmit", is(false)))
        .andExpect(jsonPath("$.schedules1To10.requirementsMet", is(true)))
        .andExpect(jsonPath("$.schedule11.statusCode", is("D")));
  }

  @Test
  @DisplayName(
      "AC 2: 515/2021 Draft with failing checks -> 409 reportNotSubmittedErrorMsg, nothing written")
  void gateFails_409_writesNothing() throws Exception {
    String before = footprint(515, 2021);

    mockMvc
        .perform(submit(515, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_SUBMITTED)));

    assertThat(footprint(515, 2021)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC 3: 517/2021 already Submitted -> 409 reportSubmissionErrorMsg, nothing written")
  void alreadySubmitted_409_writesNothing() throws Exception {
    String before = footprint(517, 2021);

    mockMvc
        .perform(submit(517, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(517, 2021)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 3: 737/2021 Verified -> 409 reportSubmissionErrorMsg (no D<->V jump), nothing written")
  void verified_409_writesNothing() throws Exception {
    String before = footprint(737, 2021);

    mockMvc
        .perform(submit(737, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(737, 2021)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 7/D6: 782/2021 with its category row '7' missing -> 500 reportSubmissionErrorMsg, rolled back")
  void missingCategoryRow_500_rollsBack() throws Exception {
    jdbc.update(
        "DELETE FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = 782 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7'");
    try {
      mockMvc
          .perform(submit(782, 2021))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
          .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

      // Rolled back: status still Draft and unbumped, no licensee, the categories before '7' still
      // Draft, the summaries untouched.
      Map<String, Object> row = statusRow(782, 2021);
      assertThat(row.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("D");
      assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
      assertThat(row.get("LICENSEE_USER_GUID")).isNull();
      assertThat(row.get("UPDATE_USERID")).isEqualTo("SEED");
      for (Map<String, Object> category : categoryRows(782, 2021)) {
        assertThat(category.get("CATEGORY_STATE_CODE")).as(category.toString()).isEqualTo("D");
        assertThat(((Number) category.get("REVISION_COUNT")).intValue()).isZero();
      }
      Integer touchedSummaries =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_MILL_ID = 782"
                  + " AND REPORT_YEAR = 2021 AND (REVISION_COUNT <> 0 OR UPDATE_USERID <> 'SEED')",
              Integer.class);
      assertThat(touchedSummaries).isZero();
    } finally {
      jdbc.update(
          "INSERT INTO THE.ILCR_REPORT_CATEGORY (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,"
              + " CATEGORY_STATE_CODE, REPORTABLE_DETAIL_IND, REVISION_COUNT, ENTRY_USERID,"
              + " ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)"
              + " VALUES (2021, 782, '7', 'D', 'Y', 0, 'SEED', SYSDATE, 'SEED', SYSDATE)");
    }
  }
}
