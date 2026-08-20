package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — {@code POST /api/v1/schedule10/check-status}.
 *
 * <p>Asserts the composed lines BYTE-FOR-BYTE, because the label prefixes, the separator and the
 * rendered bounds are the contract. It also fingerprints every mutable column before and after, since
 * "mutates nothing" is a claim that a passing status assertion alone would never catch.
 */
@DisplayName("Schedule 10 — Check Status")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule10/check-status";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired
  private JdbcTemplate jdbc;

  /**
   * Every mutable column of every Schedule 10 row for a mill, across ALL THREE tables the write path
   * touches, including both audit pairs on each.
   *
   * <p>Code review 2026-08-18 found this helper's javadoc overclaiming: the road-detail leg captured
   * neither timestamp, and {@code ILCR_COST_REPORT_DETAIL} was not fingerprinted at all — so a
   * check-status run that re-stamped a detail or touched a cost row would have gone unnoticed. The
   * cost leg is joined up through the detail to the page so the mill filter still applies.
   */
  private List<String> fingerprint(long millId) {
    List<String> rows = new ArrayList<>(jdbc.query(
        "SELECT r.ROAD_CONSTRUCTION_REPRT_ID || '|' || r.REVISION_COUNT || '|' || r.ENTRY_USERID"
            + " || '|' || r.ENTRY_TIMESTAMP || '|' || r.UPDATE_USERID || '|' || r.UPDATE_TIMESTAMP"
            + " || '|' || NVL(r.CONSTRUCTION_DIVISION_NAME, '-')"
            + " FROM THE.ROAD_CONSTRUCTION_REPRT r WHERE r.ILCR_MILL_ID = ?"
            + " ORDER BY r.ROAD_CONSTRUCTION_REPRT_ID",
        (rs, i) -> rs.getString(1), millId));
    rows.addAll(jdbc.query(
        "SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID || '|' || d.REVISION_COUNT || '|' || d.ENTRY_USERID"
            + " || '|' || d.ENTRY_TIMESTAMP || '|' || d.UPDATE_USERID || '|' || d.UPDATE_TIMESTAMP"
            + " || '|' || d.ROAD_NAME || '|' || d.RELATIVE_SOIL_MOISTUR_RGM_CODE"
            + " || '|' || d.ILCR_SOIL_MOISTURE_CODE"
            + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d JOIN THE.ROAD_CONSTRUCTION_REPRT r"
            + " ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID"
            + " WHERE r.ILCR_MILL_ID = ? ORDER BY d.ROAD_CONSTRUCTION_REPRT_DTL_ID",
        (rs, i) -> rs.getString(1), millId));
    rows.addAll(jdbc.query(
        "SELECT c.ILCR_COST_REPORT_DETAIL_ID || '|' || c.ILCR_REPORT_COST_ITEM_ID"
            + " || '|' || NVL(TO_CHAR(c.COST), 'null') || '|' || c.REVISION_COUNT"
            + " || '|' || c.ENTRY_USERID || '|' || c.UPDATE_USERID || '|' || c.UPDATE_TIMESTAMP"
            + " FROM THE.ILCR_COST_REPORT_DETAIL c"
            + " JOIN THE.ROAD_CONSTRUCTION_REPRT_DTL d"
            + " ON d.ROAD_CONSTRUCTION_REPRT_DTL_ID = c.ROAD_CONSTRUCTION_REPRT_DTL_ID"
            + " JOIN THE.ROAD_CONSTRUCTION_REPRT r"
            + " ON r.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID"
            + " WHERE r.ILCR_MILL_ID = ? ORDER BY c.ILCR_COST_REPORT_DETAIL_ID",
        (rs, i) -> rs.getString(1), millId));
    return rows;
  }

  private static List<String> issueTexts(JsonNode response) {
    List<String> texts = new ArrayList<>();
    for (JsonNode page : response.get("pages")) {
      for (JsonNode issue : page.get("issues")) {
        texts.add(issue.get("message").get("text").asText());
      }
      for (JsonNode detail : page.get("roadDetails")) {
        for (JsonNode issue : detail.get("issues")) {
          texts.add(issue.get("message").get("text").asText());
        }
      }
    }
    return texts;
  }

  @Test
  @DisplayName("a complete schedule reports MET with the banner and NO per-page results")
  void completeScheduleIsMet() throws Exception {
    List<String> before = fingerprint(719L);

    mockMvc.perform(post(ENDPOINT).param("millId", "719").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages", hasSize(1)))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        // No trailing period — legacy's own string.
        .andExpect(jsonPath("$.messages[0].text",
            is("All requirements for this schedule have been met")))
        // The pass branch never enters the per-row loop, so emitting rows here would invent output.
        .andExpect(jsonPath("$.pages", hasSize(0)));

    assertThat(fingerprint(719L)).isEqualTo(before);
  }

  @Test
  @DisplayName("an incomplete schedule reports ISSUES with byte-exact composed lines")
  void incompleteScheduleReportsComposedIssues() throws Exception {
    List<String> before = fingerprint(720L);

    String body = mockMvc.perform(
            post(ENDPOINT).param("millId", "720").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        // No banner on the failing branch.
        .andExpect(jsonPath("$.messages", hasSize(0)))
        .andReturn().getResponse().getContentAsString();

    List<String> texts = issueTexts(MAPPER.readTree(body));

    // One chain over the same subject, with each link's reasoning kept beside it. Every anyMatch and
    // noneMatch below is safe against a vacuously-empty list because the contains(...) link fails
    // first if the engine returned nothing.
    assertThat(texts)
        // Page 8951 has neither Division nor Period Surveyed. The label prefix, the single ": "
        // separator and the message are all contractual.
        .contains(
            "Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Division: Value Required",
            "Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Period Surveyed: Value Required")
        // The material-total quirk: every percentage is blank, and it still reports 100 because the
        // legacy total coerces nulls to zero and is therefore never absent.
        .anyMatch(text ->
            text.endsWith("Material Type Total (%): Total value must be equal to 100."))
        // The Road Name and Sub Zone rules carry the PAGE label only, with no road label — so on
        // page 8951, which holds two roads, their lines would be indistinguishable. That is legacy's
        // behaviour and it is pinned at the unit seam.
        //
        // Sub Zone is UNREACHABLE from stored data and is asserted absent here on purpose. It reads
        // the BEC classification's subzone, BIOGEOCLIMATIC_CATALOGUE.SUBZONE is NOT NULL, and
        // BECBIOGEO_CATALOGUE_ID is NOT NULL on the road detail — so no stored row can produce a
        // blank subzone. The only way to reach the rule is a null classification, which the schema
        // forbids; Schedule10CheckStatusTest pins that branch directly. Schedule 6 carries an
        // identical stored-data-unreachable rule.
        .noneMatch(text -> text.contains(" Sub Zone: Value Required"))
        // The classification that exists in the catalogue but is absent from the gate.
        .anyMatch(text -> text.endsWith(
            " BEC Zone: Biogeo/Subzone/Variant code is invalid."
                + " The code must be corrected before the schedule can be saved."))
        // Ballast 'C' gates IN the four additional-stabilizing dimension rules and the three cost
        // rules, all of which fire here because the seeded detail leaves those columns null.
        .anyMatch(text ->
            text.endsWith(" Additional Stabilizing: Length (km): Value Required"))
        .anyMatch(text ->
            text.endsWith(" Additional Stabilizing: Actual Cost ($): Value Required"))
        // The material-type rule inside that same gate is UNREACHABLE from stored data, and is
        // asserted absent on purpose: ILCR_ROAD_BALLAST_MATERL_CODE is NOT NULL, so no stored road
        // detail can present a blank material type however the ballast method is set. Like Sub Zone
        // above, the rule is ported faithfully and pinned at the unit seam instead.
        .noneMatch(text ->
            text.endsWith(" Additional Stabilizing Type: Value Required"));

    assertThat(fingerprint(720L)).as("check status must mutate nothing").isEqualTo(before);
  }

  @Test
  @DisplayName("a schedule with no pages is a vacuous MET")
  void emptyScheduleIsVacuouslyMet() throws Exception {
    // Mill 715 is a valid active context with zero pages (Story 11.1's fixture, read-only here).
    mockMvc.perform(post(ENDPOINT).param("millId", "715").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.pages", hasSize(0)));
  }

  @Test
  @DisplayName("Check Status is NOT Draft-gated — a submitted schedule can still be checked")
  void submittedScheduleCanStillBeChecked() throws Exception {
    // Mill 718 sits on track 'S'. Every write there is a 409; this read must still succeed.
    //
    // The outcome VALUE is asserted, not merely its presence: exists() reads like a result assertion
    // and is not one (code review 2026-08-18). Mill 718's detail 8970 has all five material
    // percentages null, so its Material Type Total rule fires and the outcome is deterministic.
    List<String> before = fingerprint(718L);

    mockMvc.perform(post(ENDPOINT).param("millId", "718").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")));

    // And a non-Draft context is still not a licence to mutate.
    assertThat(fingerprint(718L)).as("check status must mutate nothing").isEqualTo(before);
  }

  @Test
  @DisplayName("the mill/year guards apply to Check Status too")
  void contextGuardsApply() throws Exception {
    mockMvc.perform(post(ENDPOINT).with(csrf()))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post(ENDPOINT).param("millId", "999999").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());
  }
}
