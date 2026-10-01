package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Story 2.6 acceptance (BR-07, AD-5/8/10): POST /api/v1/schedule1/check-status against a real
 * Oracle dialect. Security OFF (mock ILCR_SUBMITTER). Fixtures (V7): 528 fully populated (S14), 529
 * with a null-cost itemized row (S18 WRN-002); 514 partially populated (S15 missing fields).
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries the page's lines and shared Other
 * Costs volume, while the itemized Other Costs rows (count, subtotal, WRN-002) still come from
 * Oracle. The original cases post a body that MIRRORS the stored fixture, so their verdicts are
 * unchanged; the {@code #359} cases post a body that DISAGREES with Oracle and prove the body wins.
 */
@DisplayName("POST /api/v1/schedule1/check-status — BR-07 (Story 2.6)")
class Schedule1CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule1/check-status";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";
  private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";

  /** The nine volume+cost codes and the four volume-only codes (Schedule1Service.CHECK_FIELDS). */
  private static final List<Integer> VOL_COST = List.of(12, 13, 14, 15, 16, 17, 18, 1, 2);

  private static final List<Integer> VOL_ONLY = List.of(143, 144, 139, 140);

  @Autowired private JdbcTemplate jdbcTemplate;

  /** One on-screen line as JSON; a null renders as JSON null (blank field), never 0. */
  private static String line(int code, Object volume, Object cost) {
    return "{\"costItemCode\":" + code + ",\"volume\":" + volume + ",\"cost\":" + cost + "}";
  }

  private static String body(List<String> lines, Object otherCostsVolume) {
    return "{\"lineItems\":["
        + String.join(",", lines)
        + "],\"otherCostsVolume\":"
        + otherCostsVolume
        + "}";
  }

  /** The 528 screen as served (V7): every line 100 / 500, volume-only lines 100; shared 0. */
  private static List<String> lines528() {
    return Stream.concat(
            VOL_COST.stream().map(code -> line(code, 100, 500)),
            VOL_ONLY.stream().map(code -> line(code, 100, null)))
        .collect(Collectors.toList());
  }

  /** The 514 screen as served (V3): code 12 1000/50000, code 1 500/20000, shared 8000. */
  private static final String SCREEN_514 =
      body(List.of(line(12, 1000, 50000), line(1, 500, 20000)), 8000);

  private static MockHttpServletRequestBuilder check(String millId, String json) {
    return post(ENDPOINT)
        .param("millId", millId)
        .param("year", "2021")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Test
  @DisplayName("528 fully populated -> requirementsMet, verbatim SUC-003, no data change")
  void allRequirementsMet() throws Exception {
    int before =
        JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL, "ILCR_REPORT_SUMMARY_ID = 1030");
    mockMvc
        .perform(check("528", body(lines528(), 0)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors.length()", is(0)))
        .andExpect(
            jsonPath("$.message.text", is("All requirements for this schedule have been met")));
    // Read-only: no data changed.
    assertEquals(
        before,
        JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL, "ILCR_REPORT_SUMMARY_ID = 1030"),
        "check-status must not mutate data");
  }

  @Test
  @DisplayName("514 partially populated -> missing-field errors (verbatim), requirementsMet false")
  void missingFields() throws Exception {
    mockMvc
        .perform(check("514", SCREEN_514))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath("$.errors[*].text", hasItem("Log Transportation - Volume: Value Required")))
        .andExpect(
            jsonPath("$.errors[*].text", hasItem("Log Transportation - Cost: Value Required")))
        .andExpect(
            jsonPath("$.errors[*].text", hasItem("Total Silviculture - Volume: Value Required")))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("530 Other-Costs volume>0 but no cost -> verbatim FLD-008")
  void otherCostsCostConsistency() throws Exception {
    mockMvc
        .perform(check("530", body(List.of(), 100)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                hasItem(
                    "Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater than 0")));
  }

  @Test
  @DisplayName("531 Other-Costs cost>0 but volume 0 -> verbatim FLD-009")
  void otherCostsVolumeConsistency() throws Exception {
    mockMvc
        .perform(check("531", body(List.of(), 0)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                hasItem(
                    "Subtotal Other Costs (1): Volume: must be greater than 0 when Cost is greater than 0")));
  }

  @Test
  @DisplayName("532 no shared-volume row -> verbatim FLD-010 (Subtotal Other Costs (0) - Volume)")
  void otherCostsVolumeRequired() throws Exception {
    mockMvc
        .perform(check("532", body(List.of(), null)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.errors[*].text", hasItem("Subtotal Other Costs (0) - Volume: Value Required")));
  }

  @Test
  @DisplayName("529 empty-cost row -> verbatim WRN-002 warning (N=2)")
  void emptyCostWarning() throws Exception {
    mockMvc
        .perform(check("529", body(List.of(), 100)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.warnings[*].text",
                hasItem(
                    "Subtotal Other Costs (2) - Cost: One or more entries contain an empty Cost value. "
                        + "Please verify there are no Other Costs to be entered.")));
  }

  @Test
  @DisplayName("#359 unsaved violation: 528 stores every line, the screen clears Standing Tree")
  void unsavedViolation_bodyWinsOverStoredValue() throws Exception {
    List<String> lines = lines528();
    lines.set(0, line(12, null, 500));
    mockMvc
        .perform(check("528", body(lines, 0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors.length()", is(1)))
        .andExpect(
            jsonPath(
                "$.errors[0].text", is("Standing Tree to Loaded Truck - Volume: Value Required")))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("#359 unsaved fix: 514 stores no Log Transportation, the screen supplies it")
  void unsavedFix_findingGone_othersUnchanged() throws Exception {
    String stored =
        mockMvc.perform(check("514", SCREEN_514)).andReturn().getResponse().getContentAsString();
    String fixed =
        body(List.of(line(12, 1000, 50000), line(1, 500, 20000), line(13, 250, 0)), 8000);
    String onScreen =
        mockMvc
            .perform(check("514", fixed))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                    "$.errors[*].text",
                    not(hasItem("Log Transportation - Volume: Value Required"))))
            .andExpect(
                jsonPath(
                    "$.errors[*].text", not(hasItem("Log Transportation - Cost: Value Required"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Exactly the two Log Transportation lines went; every other finding is byte-identical.
    assertEquals(
        stored
            .replace(
                "{\"key\":\"missingRequiredFieldMsg\",\"text\":\"Log Transportation - Volume: Value Required\"},",
                "")
            .replace(
                "{\"key\":\"missingRequiredFieldMsg\",\"text\":\"Log Transportation - Cost: Value Required\"},",
                ""),
        onScreen);
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "528").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 nothing persisted: a disagreeing body leaves every 528 row and token unmoved")
  void disagreeingBody_persistsNothing() throws Exception {
    List<Map<String, Object>> rowsBefore = rows1030();
    Integer revisionBefore = revision1030();

    // Every line 528 stores, cleared on screen: null volume and cost.
    List<String> cleared =
        Stream.concat(VOL_COST.stream(), VOL_ONLY.stream())
            .map(code -> line(code, null, null))
            .collect(Collectors.toList());
    mockMvc
        .perform(check("528", body(cleared, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)));

    assertEquals(rowsBefore, rows1030(), "check-status must not rewrite any detail row");
    assertEquals(revisionBefore, revision1030(), "check-status must not bump REVISION_COUNT");
  }

  private List<Map<String, Object>> rows1030() {
    return jdbcTemplate.queryForList(
        "SELECT * FROM " + DETAIL + " WHERE ILCR_REPORT_SUMMARY_ID = 1030 ORDER BY 1");
  }

  private Integer revision1030() {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = 1030",
        Integer.class);
  }
}
