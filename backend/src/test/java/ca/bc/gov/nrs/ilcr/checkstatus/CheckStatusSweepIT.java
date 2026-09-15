package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 15.1. {@code GET /api/v1/check-status} sweeps all twelve schedules for a
 * mill/year on both tracks, read-only (AC 1/2/4, UC-CHK-001 S01/S02/S09/S10, UC-CHK-009 S17).
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}). Anchors are the read-only seeded fixtures
 * the per-schedule check-status ITs and {@code CheckStatusWireContractIT} already pin, so every
 * text asserted here is a byte the schedule's own golden already guarantees — this class asserts
 * that the sweep CARRIES those bytes, in the right slot, and changes nothing. Each mutation check
 * fingerprints row counts AND the status row before and after (FR5: "not merely by inspection").
 */
@DisplayName("GET /api/v1/check-status — the twelve-schedule sweep (Story 15.1)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class CheckStatusSweepIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status";
  private static final String TRACK_1_TO_10 = "$.schedules1To10";
  private static final String TRACK_11 = "$.schedule11";
  private static final List<String> SCHEDULE_DATA_TABLES =
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
          "ILCR_MILL_REPORT_STATUS");

  @Autowired private JdbcTemplate jdbcTemplate;

  private MockHttpServletRequestBuilder sweep(long mill, int year) {
    return get(ENDPOINT)
        .param("millId", String.valueOf(mill))
        .param("year", String.valueOf(year))
        .accept(MediaType.APPLICATION_JSON);
  }

  /**
   * Global row counts across every schedule-owned table reached by the twelve validations, plus
   * this mill/year's two track-status codes, folded into one comparable fingerprint. Global counts
   * deliberately include child tables whose rows reach mill/year only through different parent
   * shapes; the integration suite is not parallelized, so any insert/delete by the sweep fails the
   * equality without an incomplete chain of parent joins. The explicit status values also catch a
   * transition, whose row count would otherwise stay unchanged.
   */
  private String footprint(long mill, int year) {
    StringBuilder fingerprint = new StringBuilder();
    for (String table : SCHEDULE_DATA_TABLES) {
      Integer count =
          jdbcTemplate.queryForObject("SELECT COUNT(*) FROM THE." + table, Integer.class);
      fingerprint.append(table).append('=').append(count).append(';');
    }
    String trackCodes =
        jdbcTemplate.queryForObject(
            "SELECT NVL(ILCR_MILL_REPORT_STATUS_CODE, '-') || '/'"
                + " || NVL(MILL_SILVICULTUR_STATUS_CODE, '-')"
                + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ?",
            String.class,
            mill,
            year);
    return fingerprint.append("TRACK_CODES=").append(trackCodes).toString();
  }

  @Test
  @DisplayName("514/2021: all twelve, partitioned 11 + 1 in legacy order, both track statuses")
  void sweep_allTwelvePartitionedByTrack() throws Exception {
    mockMvc
        .perform(sweep(514, 2021))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        // 7A and 7B are two of the "ten"; Schedule 11 sits alone on its own track.
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[*].schedule",
                contains("1", "2", "3", "4", "5", "6", "7A", "7B", "8", "9", "10")))
        .andExpect(jsonPath(TRACK_11 + ".schedules[*].schedule", contains("11")))
        // The seeded status row is Draft on 1-10 with a NULL silviculture code, which the global
        // non_null Jackson setting omits — so the 11 track has NO statusCode property at all.
        .andExpect(jsonPath(TRACK_1_TO_10 + ".statusCode", is("D")))
        .andExpect(jsonPath(TRACK_11 + ".statusCode").doesNotExist())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".requirementsMet", is(false)));
  }

  @Test
  @DisplayName("514/2021: Schedule 4's ISSUES verdict rides through verbatim; nothing is mutated")
  void sweep_carriesSchedule4Verdict_mutatesNothing() throws Exception {
    String before = footprint(514, 2021);

    mockMvc
        .perform(sweep(514, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[3].schedule", is("4")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[3].requirementsMet", is(false)))
        // The schedule's own DTO, untouched: outcome/messages/locations exactly as
        // Schedule4CheckStatusIT.mixed_issues pins them.
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[3].verdict.outcome", is("ISSUES")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[3].verdict.messages.length()", is(0)))
        .andExpect(
            jsonPath(TRACK_1_TO_10 + ".schedules[3].verdict.locations[0].name", is("Harbour Dump")))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[3].verdict.locations[0].issues[0].message.text",
                is("Value Required")));

    assertEquals(before, footprint(514, 2021), "the sweep must not change any data or status");
  }

  @Test
  @DisplayName("S17: 7A and 7B each carry their OWN verdict (legacy bound the 7B tab to 7A's)")
  void sweep_schedule7bCarriesItsOwnVerdict() throws Exception {
    mockMvc
        .perform(sweep(514, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[6].schedule", is("7A")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[6].requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[6].verdict.errors[0].text",
                is("Bridge Report Id : 3 - Certification After install Cost : Value Required")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[7].schedule", is("7B")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[7].requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[7].verdict.errors[0].text",
                is("Culvert Report Id : 3 - Culvert Type Round - Span size: Value Required")));
  }

  @Test
  @DisplayName("S09 (530/2021): Schedule 1's Other-Costs cross-check surfaces in the sweep")
  void sweep_schedule1OtherCostsCrossCheck() throws Exception {
    String before = footprint(530, 2021);

    mockMvc
        .perform(sweep(530, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[0].schedule", is("1")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[0].requirementsMet", is(false)))
        // The bean-level rule legacy kept OUTSIDE the validator (CheckStatusMB.init():88-102) —
        // present in the modern service, and therefore in the sweep. Misspelling is
        // legacy-verbatim.
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[0].verdict.errors[*].key",
                hasItem("sch1.subtotal.other.costs.costs.grearter.than.zero")))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[0].verdict.errors[*].text",
                hasItem(
                    "Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater"
                        + " than 0")));

    assertEquals(before, footprint(530, 2021), "the sweep must not change any data or status");
  }

  @Test
  @DisplayName("S10 (601/2021): Schedule 8's dynamic per-page/per-sample list rides through")
  void sweep_schedule8DynamicMessageList() throws Exception {
    mockMvc
        .perform(sweep(601, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[8].schedule", is("8")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[8].requirementsMet", is(false)))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[8].verdict.outcome", is("ISSUES")))
        .andExpect(
            jsonPath(TRACK_1_TO_10 + ".schedules[8].verdict.pages[0].issues.length()", is(4)))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[8].verdict.pages[0].samples[0].issues[*].message.text",
                hasItem("The total percent value for skidding/yarding must be equal to 100%")));
  }

  @Test
  @DisplayName(
      "673/2021: Schedule 5's bespoke per-camp findings (not MessageInfo) arrive with their text")
  void sweep_schedule5CampFindingsSurvive() throws Exception {
    mockMvc
        .perform(sweep(673, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[4].schedule", is("5")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules[4].requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[4].verdict.camps[1].campName",
                is("Bare Descriptor Camp")))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[4].verdict.camps[1].messages[0].field",
                is("roadDistanceToOperatingArea")))
        .andExpect(
            jsonPath(
                TRACK_1_TO_10 + ".schedules[4].verdict.camps[1].messages[0].text",
                is(
                    "Camp Report Name : Bare Descriptor Camp - Road Distance to Operating Area:"
                        + " Value Required")));
  }

  @Test
  @DisplayName("617/2021: Schedule 11 sits on its own track with its always-present SUC-004")
  void sweep_schedule11OnItsOwnTrack() throws Exception {
    String before = footprint(617, 2021);

    mockMvc
        .perform(sweep(617, 2021))
        .andExpect(status().isOk())
        .andExpect(jsonPath(TRACK_11 + ".schedules.length()", is(1)))
        .andExpect(jsonPath(TRACK_11 + ".schedules[0].schedule", is("11")))
        .andExpect(
            jsonPath(TRACK_11 + ".schedules[0].verdict.message.key", is("checkStatusMessage")))
        .andExpect(jsonPath(TRACK_1_TO_10 + ".schedules.length()", is(11)));

    assertEquals(before, footprint(617, 2021), "the sweep must not change any data or status");
  }
}
