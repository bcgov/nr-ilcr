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
 * Acceptance test — Story 26.1 AC 1/2/4/5/8. {@code POST /api/v1/check-status/schedule11/submit}
 * moves the silviculture track Draft → Submitted in one committed transaction, or refuses and
 * writes nothing. Security OFF: the mock {@code ILCR_SUBMITTER} carries the canonical submitter's
 * GUID, which {@code R__70} associates to every seeded mill, so the licensee lookup finds a row.
 *
 * <p>Fixtures ({@code R__57}): 784 (happy path, 1–10 at {@code S}) and 789 (zero locations) are
 * each submitted here exactly once; 787 is the rollback arm and is seeded without its category
 * {@code '11'} row, so it can never succeed; 785 and 786 are refused and never written. All six are
 * owned by this class, except 788 ({@code Schedule11SubmitConcurrencyIT}).
 *
 * <p><strong>Every "unchanged" is asserted by reading the value before and after</strong>, not by
 * asserting what it ought to be: a Schedule 11 statement that also wrote the 1–10 column or its
 * categories would pass an "is still S" check on a fixture that started at {@code S}.
 *
 * <p>The test schema has no audit triggers, so the statement ORDER that earns the {@code S}
 * snapshot is pinned in {@code ReportTrackTransitionServiceTest}; this class proves the effects.
 */
@DisplayName("POST /api/v1/check-status/schedule11/submit — Schedule 11 Draft -> Submitted (26.1)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule11SubmitIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/schedule11/submit";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SUBMITTED = "Schedule 11 has been successfully submitted.";
  private static final String NOT_SUBMITTED =
      "The report cannot be submitted. One or more of the Schedules have not passed validation."
          + " Please review and correct any errors.";
  private static final String NOT_DRAFT =
      "Schedule 11 is no longer in Draft and cannot be submitted.";
  private static final String SUBMISSION_ERROR =
      "An error has been found submitting schedules. The error details have been logged. Please"
          + " contact ILCR application support.";
  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String CHECK_STATUS_NOT_FOUND =
      "One or more of the schedules for the report have not been found.";
  private static final String ERR_002 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";

  /** The mock principal's audit name for the default SUBMITTER role (MockPrincipalFilter). */
  private static final String MOCK_USER = "dev-submitter";

  private static final List<String> TABLES =
      List.of(
          "ILCR_REPORT_SUMMARY",
          "ILCR_COST_REPORT_DETAIL",
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

  private static MockHttpServletRequestBuilder sweep(long mill) {
    return get("/api/v1/check-status")
        .param("millId", String.valueOf(mill))
        .param("year", "2021")
        .accept(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder schedule11Page(long mill) {
    return get("/api/v1/schedule11")
        .param("millId", String.valueOf(mill))
        .param("year", "2021")
        .accept(MediaType.APPLICATION_JSON);
  }

  /** Per table the row count, revision sum and latest touch, plus the mill's own rows. */
  private String footprint(long mill) {
    StringBuilder f = new StringBuilder();
    for (String table : TABLES) {
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
    f.append("STATUS=").append(statusRow(mill)).append(';');
    f.append("CATEGORIES=").append(categoryRows(mill));
    return f.toString();
  }

  private Map<String, Object> statusRow(long mill) {
    return jdbc.queryForMap(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, REPORT_COMPLETED_IND,"
            + " LICENSEE_MILL_ID, LICENSEE_USER_GUID, AUDITOR_MILL_ID, AUDITOR_USER_GUID,"
            + " REVISION_COUNT, UPDATE_USERID, UPDATE_TIMESTAMP, ENTRY_TIMESTAMP"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        mill);
  }

  private List<Map<String, Object>> categoryRows(long mill) {
    return jdbc.queryForList(
        "SELECT ILCR_CATEGORY_ID, CATEGORY_STATE_CODE, REVISION_COUNT, UPDATE_USERID,"
            + " UPDATE_TIMESTAMP FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021 ORDER BY TO_NUMBER(ILCR_CATEGORY_ID)",
        mill);
  }

  /** Categories '1'..'10' only — everything a Schedule 11 transition must never write. */
  private List<Map<String, Object>> oneToTenCategories(long mill) {
    return categoryRows(mill).stream()
        .filter(r -> !"11".equals(r.get("ILCR_CATEGORY_ID")))
        .toList();
  }

  private List<Map<String, Object>> locations(long mill) {
    return jdbc.queryForList(
        "SELECT BASIC_SILVICULTURE_REPORT_ID, REVISION_COUNT, UPDATE_USERID, UPDATE_TIMESTAMP"
            + " FROM THE.BASIC_SILVICULTURE_REPORT WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
            + " ORDER BY BASIC_SILVICULTURE_REPORT_ID",
        mill);
  }

  private List<Map<String, Object>> locationCosts(long mill) {
    return jdbc.queryForList(
        "SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.COST, d.REVISION_COUNT, d.UPDATE_USERID"
            + " FROM THE.ILCR_COST_REPORT_DETAIL d JOIN THE.BASIC_SILVICULTURE_REPORT b"
            + " ON b.BASIC_SILVICULTURE_REPORT_ID = d.BASIC_SILVICULTURE_REPORT_ID"
            + " WHERE b.ILCR_MILL_ID = ? AND b.REPORT_YEAR = 2021"
            + " ORDER BY d.ILCR_COST_REPORT_DETAIL_ID",
        mill);
  }

  /**
   * One row by primary key — its audit and revision columns — for the "must not be stamped" reads:
   * a row outside the submit's scope compares equal before and after, value for value.
   */
  private Map<String, Object> auditColumns(String table, String idColumn, long id) {
    return jdbc.queryForMap(
        "SELECT REVISION_COUNT, UPDATE_USERID, UPDATE_TIMESTAMP FROM THE."
            + table
            + " WHERE "
            + idColumn
            + " = ?",
        id);
  }

  @Test
  @DisplayName(
      "AC 1/8a: 784 silviculture D, 1-10 S -> 200 sch11SubmittedMsg; silviculture S, licensee"
          + " recorded, every location and cost stamped, '11' at A — and 1-10 byte-unchanged")
  void happyPath_submitsSchedule11Alone() throws Exception {
    Map<String, Object> before = statusRow(784);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(784);
    List<Map<String, Object>> costsBefore = locationCosts(784);
    // Out of scope, and each read by key: 784's own Schedules 1-10 row family (R__57 seeds it for
    // exactly this), and another mill's Schedule 11 location and cost. A Schedule 11 submit that
    // also
    // ran the ten 1-10 touches, or whose touch lost its mill/year predicate, stamps one of these.
    Map<String, Object> oneToTenSummaryBefore =
        auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1680);
    Map<String, Object> oneToTenCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5811);
    Map<String, Object> neighbourLocationBefore =
        auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9413);
    Map<String, Object> neighbourCostBefore =
        auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5804);
    assertThat(before.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("D");
    assertThat(before.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("S");

    mockMvc
        .perform(submit(784, 2021))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message.key", is("sch11SubmittedMsg")))
        .andExpect(jsonPath("$.message.text", is(SUBMITTED)));

    // The status row: silviculture S (BR-03), the shared LICENSEE pair written (D1, BR-05), the
    // revision bumped (deviation (B) extended), and the 1-10 code EXACTLY as it was (BR-06).
    Map<String, Object> after = statusRow(784);
    assertThat(after.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(after.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo(before.get("ILCR_MILL_REPORT_STATUS_CODE"));
    assertThat(((Number) after.get("LICENSEE_MILL_ID")).longValue()).isEqualTo(784L);
    assertThat(after.get("LICENSEE_USER_GUID")).isEqualTo(CANONICAL_SUBMITTER_GUID);
    assertThat(after.get("AUDITOR_MILL_ID")).isEqualTo(before.get("AUDITOR_MILL_ID"));
    assertThat(after.get("AUDITOR_USER_GUID")).isEqualTo(before.get("AUDITOR_USER_GUID"));
    assertThat(after.get("REPORT_COMPLETED_IND")).isEqualTo(before.get("REPORT_COMPLETED_IND"));
    assertThat(after.get("UPDATE_USERID")).isEqualTo(MOCK_USER);
    assertThat(((Number) after.get("REVISION_COUNT")).intValue())
        .isEqualTo(((Number) before.get("REVISION_COUNT")).intValue() + 1);
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .isAfter((Timestamp) before.get("UPDATE_TIMESTAMP"));

    // Categories '1'..'10' read back identical, value for value; '11' advanced (BR-04).
    assertThat(oneToTenCategories(784)).isEqualTo(oneToTenBefore);
    Map<String, Object> eleven = categoryRows(784).get(10);
    assertThat(eleven.get("ILCR_CATEGORY_ID")).isEqualTo("11");
    assertThat(eleven.get("CATEGORY_STATE_CODE")).isEqualTo("A");
    assertThat(((Number) eleven.get("REVISION_COUNT")).intValue()).isEqualTo(1);
    assertThat(eleven.get("UPDATE_USERID")).isEqualTo(MOCK_USER);

    // Every location and every cost under it stamped; neither legacy entity was @Version, so no
    // revision moves, and no cost value changes.
    List<Map<String, Object>> locations = locations(784);
    assertThat(locations).hasSize(2);
    for (Map<String, Object> row : locations) {
      assertThat(row.get("UPDATE_USERID")).as(row.toString()).isEqualTo(MOCK_USER);
      assertThat(row.get("UPDATE_TIMESTAMP")).isNotNull();
      assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isZero();
    }
    List<Map<String, Object>> costs = locationCosts(784);
    assertThat(costs).hasSize(4);
    for (int i = 0; i < costs.size(); i++) {
      assertThat(costs.get(i).get("UPDATE_USERID")).isEqualTo(MOCK_USER);
      assertThat(costs.get(i).get("COST")).isEqualTo(costsBefore.get(i).get("COST"));
      assertThat(costs.get(i).get("REVISION_COUNT"))
          .isEqualTo(costsBefore.get(i).get("REVISION_COUNT"));
    }

    assertThat(auditColumns("ILCR_REPORT_SUMMARY", "ILCR_REPORT_SUMMARY_ID", 1680))
        .as("784's Schedules 1-10 summary is not the Schedule 11 submit's to stamp")
        .isEqualTo(oneToTenSummaryBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5811))
        .isEqualTo(oneToTenCostBefore);
    assertThat(auditColumns("BASIC_SILVICULTURE_REPORT", "BASIC_SILVICULTURE_REPORT_ID", 9413))
        .as("785's Schedule 11 location is outside 784/2021")
        .isEqualTo(neighbourLocationBefore);
    assertThat(auditColumns("ILCR_COST_REPORT_DETAIL", "ILCR_COST_REPORT_DETAIL_ID", 5804))
        .isEqualTo(neighbourCostBefore);

    // The page re-sweeps: Submitted, no longer offered; and the Licensee's page is read-only now
    // with no page change of its own (BR-08).
    mockMvc
        .perform(sweep(784))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("S")))
        .andExpect(jsonPath("$.schedule11.canSubmit", is(false)))
        .andExpect(jsonPath("$.schedules1To10.statusCode", is("S")));
    mockMvc
        .perform(schedule11Page(784))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)));
  }

  @Test
  @DisplayName(
      "AC 2: 785 one location without a Planned Cost -> 409 reportNotSubmittedErrorMsg, nothing"
          + " written, the flag visible on re-sweep and the page still editable")
  void gateFails_409_writesNothing() throws Exception {
    String before = footprint(785);

    mockMvc
        .perform(submit(785, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_SUBMITTED)));

    assertThat(footprint(785)).isEqualTo(before);
    mockMvc
        .perform(sweep(785))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedule11.statusCode", is("D")))
        .andExpect(jsonPath("$.schedule11.requirementsMet", is(false)))
        .andExpect(jsonPath("$.schedule11.canSubmit", is(true)))
        .andExpect(
            jsonPath(
                "$.schedule11.schedules[0].verdict.errors[0].text",
                is("location  : Unplanned Block - Planned cost: Value Required")));
    mockMvc
        .perform(schedule11Page(785))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)));
  }

  @Test
  @DisplayName(
      "AC 5/(AB): 786 silviculture already S (1-10 at D) -> 409 in Schedule 11's words, nothing"
          + " written")
  void alreadySubmitted_409_writesNothing() throws Exception {
    String before = footprint(786);

    mockMvc
        .perform(submit(786, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_DRAFT)));

    assertThat(footprint(786)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 5: 787 with no category '11' row -> 500 reportSubmissionErrorMsg, the whole transition"
          + " rolled back — silviculture NOT left at S")
  void missingCategoryRow_500_rollsBack() throws Exception {
    Map<String, Object> before = statusRow(787);
    List<Map<String, Object>> categoriesBefore = categoryRows(787);
    List<Map<String, Object>> locationsBefore = locations(787);
    List<Map<String, Object>> costsBefore = locationCosts(787);

    mockMvc
        .perform(submit(787, 2021))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    // The status UPDATE and both stamps ran before the category advance found no row; all three
    // must have been rolled back with it.
    assertThat(statusRow(787)).isEqualTo(before);
    assertThat(statusRow(787).get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("D");
    assertThat(categoryRows(787)).isEqualTo(categoriesBefore);
    assertThat(locations(787)).isEqualTo(locationsBefore);
    assertThat(locationCosts(787)).isEqualTo(costsBefore);
  }

  @Test
  @DisplayName(
      "AC 2/8c: 789 zero locations is vacuously MET -> 200; 1-10 stays D and its Submit stays"
          + " offered")
  void zeroLocations_vacuouslyMet_leavesOneToTenOffered() throws Exception {
    Map<String, Object> before = statusRow(789);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(789);

    mockMvc
        .perform(submit(789, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("sch11SubmittedMsg")));

    Map<String, Object> after = statusRow(789);
    assertThat(after.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(after.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo(before.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo("D");
    assertThat(oneToTenCategories(789)).isEqualTo(oneToTenBefore);
    assertThat(categoryRows(789).get(10).get("CATEGORY_STATE_CODE")).isEqualTo("A");

    mockMvc
        .perform(sweep(789))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules1To10.statusCode", is("D")))
        .andExpect(jsonPath("$.schedules1To10.canSubmit", is(true)))
        .andExpect(jsonPath("$.schedule11.canSubmit", is(false)));
  }

  @Test
  @DisplayName(
      "review 1a: 514/2021 has a NULL silviculture code -> 409 with legacy's generic text, not"
          + " \"no longer in Draft\" — the track was never in Draft; nothing written")
  void nullSilvicultureCode_409_genericText() throws Exception {
    assertThat(statusRow(514).get("MILL_SILVICULTUR_STATUS_CODE")).isNull();
    String before = footprint(514);

    mockMvc
        .perform(submit(514, 2021))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));

    assertThat(footprint(514)).isEqualTo(before);
  }

  // --- AC 4: the same context guard as /submit ---------------------------------------------------

  @Test
  @DisplayName("AC 4: missing millId -> 400 ERR-001 verbatim, trailing space included")
  void missingMillId_400() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC 4: non-numeric year -> 400 ERR-001, never Spring's own 400")
  void nonNumericYear_400() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "784").param("year", "twenty21"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC 4: unknown mill -> 404 with the Check Status not-found text")
  void unknownMill_404() throws Exception {
    mockMvc
        .perform(submit(999999, 2021))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("AC 4: known mill, year with no status row -> 404 Check Status not-found")
  void absentYear_404() throws Exception {
    mockMvc
        .perform(submit(784, 1999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("AC 4: mill closed (CLS) for the year -> 409 ERR-002 verbatim, nothing written")
  void closedMill_409() throws Exception {
    String before = footprint(516);

    mockMvc
        .perform(submit(516, 2021))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(ERR_002)));

    assertThat(footprint(516)).isEqualTo(before);
  }
}
