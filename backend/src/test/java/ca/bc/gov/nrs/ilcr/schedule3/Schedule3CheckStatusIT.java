package ca.bc.gov.nrs.ilcr.schedule3;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 4.2 POST /api/v1/schedule3/check-status (BR-11/BR-03, read-only).
 * Security OFF (mock ILCR_SUBMITTER). Fixtures: 572 complete/valid (all met, V18); 517 empty cat-3
 * (missing, V8).
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries the eleven fixed lines, both
 * timber volumes and the Override, while the item-124/38 sub-page rows still come from Oracle. The
 * original cases post a body that MIRRORS the stored fixture, so their verdicts are unchanged; the
 * {@code #359} cases post a body that DISAGREES with Oracle and prove the body wins.
 */
@DisplayName("POST /api/v1/schedule3/check-status — readiness validation (Story 4.2)")
class Schedule3CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3/check-status";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";
  private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";

  /** The eight both-columns lines; 29, 33 and 37 are Harvest-only. */
  private static final List<Integer> HARVEST_POP = List.of(27, 28, 30, 31, 32, 34, 35, 36);

  private static final List<Integer> ALL_LINES =
      List.of(27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37);

  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * The 572 screen as served (V18:38-59): Harvest 1000 on every line, PO&amp;P 500 on the
   * both-columns lines, both timber volumes 1000, Override not set.
   */
  private static List<String> lines572() {
    List<String> lines = new ArrayList<>();
    for (int code : ALL_LINES) {
      lines.add(line(code, 1000, HARVEST_POP.contains(code) ? 500 : null));
    }
    return lines;
  }

  /** One on-screen line as JSON; a null renders as JSON null (blank field), never 0. */
  private static String line(int code, Object harvest, Object pop) {
    return "{\"costItemCode\":" + code + ",\"harvest\":" + harvest + ",\"pop\":" + pop + "}";
  }

  private static String body(String override, List<String> lines, Object pop, Object crown) {
    return "{\"overrideHarvestTotalPop\":"
        + (override == null ? "null" : "\"" + override + "\"")
        + ",\"lineItems\":["
        + String.join(",", lines)
        + "],\"popTimberVolume\":"
        + pop
        + ",\"crownTimberVolume\":"
        + crown
        + "}";
  }

  private static MockHttpServletRequestBuilder check(String millId, String json) {
    return post(ENDPOINT)
        .param("millId", millId)
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Test
  @DisplayName("complete valid document → requirementsMet true + SUC-003")
  void completeDocument_requirementsMet() throws Exception {
    mockMvc
        .perform(check("572", body("N", lines572(), 1000, 1000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.message.key", is("scheduleRequirementsMetMsg")));
  }

  @Test
  @DisplayName("empty document → requirementsMet false + missing-required errors (verbatim labels)")
  void emptyDocument_reportsMissing() throws Exception {
    mockMvc
        .perform(check("517", body("N", List.of(), null, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors.length()", greaterThan(0)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                hasItem("Licenses, Fees, Insurance (Harvest Total $): Value Required")));
  }

  @Test
  @DisplayName("#359 unsaved violation: 572 stores Office Expense, the screen clears it")
  void unsavedViolation_bodyWinsOverStoredValue() throws Exception {
    List<String> lines = lines572();
    lines.set(ALL_LINES.indexOf(32), line(32, null, 500));
    mockMvc
        .perform(check("572", body("N", lines, 1000, 1000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(
            jsonPath("$.errors[0].text", is("Office Expense (Harvest Total $): Value Required")))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("#359 unsaved fix: 517 stores nothing, the screen supplies every line -> MET")
  void unsavedFix_bodyWinsOverMissingRecord() throws Exception {
    mockMvc
        .perform(check("517", body("N", lines572(), 1000, 1000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)));
  }

  @Test
  @DisplayName("#359 the on-screen Override drives Harvest<PO&P; 572 stores no override")
  void onScreenOverride_drivesHarvestLessThanPop() throws Exception {
    List<String> lines = lines572();
    lines.set(ALL_LINES.indexOf(27), line(27, 100, 500));
    String harvestLessThanPop =
        "Licenses, Fees, Insurance (Harvest Total $): "
            + "Value must be greater than or equal to the corresponding PO&P Cost";
    mockMvc
        .perform(check("572", body("N", lines, 1000, 1000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].text", is(harvestLessThanPop)));
    mockMvc
        .perform(check("572", body("Y", lines, 1000, 1000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)));
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "572").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 nothing persisted: a disagreeing body leaves every 572 row and token unmoved")
  void disagreeingBody_persistsNothing() throws Exception {
    List<Map<String, Object>> rowsBefore = rows1044();
    List<Map<String, Object>> summaryBefore = summary1044();

    List<String> lines = new ArrayList<>();
    for (int code : ALL_LINES) {
      lines.add(line(code, 1, 9));
    }
    mockMvc
        .perform(check("572", body("Y", lines, null, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)));

    assertEquals(rowsBefore, rows1044(), "check-status must not rewrite any detail row");
    assertEquals(
        summaryBefore, summary1044(), "check-status must not touch REVISION_COUNT or LOCATION");
  }

  private List<Map<String, Object>> rows1044() {
    return jdbcTemplate.queryForList(
        "SELECT * FROM " + DETAIL + " WHERE ILCR_REPORT_SUMMARY_ID = 1044 ORDER BY 1");
  }

  private List<Map<String, Object>> summary1044() {
    return jdbcTemplate.queryForList(
        "SELECT * FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = 1044");
  }
}
