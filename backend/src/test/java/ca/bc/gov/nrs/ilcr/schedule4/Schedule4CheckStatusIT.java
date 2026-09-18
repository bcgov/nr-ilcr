package ca.bc.gov.nrs.ilcr.schedule4;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Story 4.4. POST /api/v1/schedule4/check-status — read-only MET/ISSUES
 * evaluation with a per-location breakdown (S28–S31), mutating nothing (AD-5).
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}); the POST sends {@code .with(csrf())}.
 * Fixtures: 560 "All Good Dump" (all Costs present → MET, V10), and the read-only 514 (Harbour Dump
 * has a null-Cost category 52; Empty Landing has no categories). Since issue #465 re-grounded the
 * rule to legacy parity — only a blank location description fails — 514 is MET too, and Harbour
 * Dump is the pin that a Volume-only category is NOT reported. Each case captures report/detail
 * counts before and after to prove no mutation. The ISSUES branch (a blank description) cannot be
 * stored through the app, so {@code Schedule4CheckStatusServiceTest} covers it.
 */
@DisplayName("POST /api/v1/schedule4/check-status — requirement check (Story 4.4)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule4CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule4/check-status";
  private static final String REPORT = "THE.TRANSPORTATION_REPORT";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired private JdbcTemplate jdbcTemplate;

  private long footprint(long mill) {
    Integer reports =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + REPORT + " WHERE ILCR_MILL_ID = ?", Integer.class, mill);
    Integer details =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + DETAIL
                + " d JOIN "
                + REPORT
                + " tr "
                + "ON tr.TRANSPORTATION_REPORT_ID = d.TRANSPORTATION_REPORT_ID WHERE tr.ILCR_MILL_ID = ?",
            Integer.class,
            mill);
    return (long) reports * 100000 + details; // combined fingerprint
  }

  @Test
  @DisplayName(
      "all Costs present (560) -> MET + schedule banner + per-location met; mutates nothing")
  void allPass_met() throws Exception {
    long before = footprint(560);
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(csrf())
                .param("millId", "560")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")))
        .andExpect(jsonPath("$.locations.length()", is(1)))
        .andExpect(jsonPath("$.locations[0].name", is("All Good Dump")))
        .andExpect(jsonPath("$.locations[0].met", is(true)))
        .andExpect(
            jsonPath(
                "$.locations[0].messages[0].text",
                is("All requirements for All Good Dump have been met.")))
        .andExpect(jsonPath("$.locations[0].issues.length()", is(0)));
    assertEquals(before, footprint(560), "check-status must not mutate anything");
  }

  @Test
  @DisplayName(
      "514: Harbour Dump's Volume-only category 52 is NOT a finding (#465) -> MET; no mutation")
  void volumeOnlyCategory_notReported_met() throws Exception {
    long before = footprint(514);
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(csrf())
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")))
        // Harbour Dump: category 52 (Rail Haul) has a Volume and no Cost. Legacy never required a
        // Schedule 4 Cost (its isXxxToCheck gates were never true), so no issue is raised.
        .andExpect(jsonPath("$.locations[0].name", is("Harbour Dump")))
        .andExpect(jsonPath("$.locations[0].met", is(true)))
        .andExpect(jsonPath("$.locations[0].issues.length()", is(0)))
        .andExpect(
            jsonPath(
                "$.locations[0].messages[0].text",
                is("All requirements for Harbour Dump have been met.")))
        // Empty Landing: no categories -> passes, per-location met message.
        .andExpect(jsonPath("$.locations[1].name", is("Empty Landing")))
        .andExpect(jsonPath("$.locations[1].met", is(true)))
        .andExpect(
            jsonPath(
                "$.locations[1].messages[0].text",
                is("All requirements for Empty Landing have been met.")));
    assertEquals(before, footprint(514), "check-status must not mutate anything");
  }

  @Test
  @DisplayName("no location on 514 carries any issue code — cost-item codes are never emitted")
  void noCostItemCodeIsEverEmitted() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(csrf())
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locations[*].issues[*]", empty()));
  }

  @Test
  @DisplayName("check-status returns problem+json content type nothing; 200 JSON body")
  void returnsJson() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(csrf())
                .param("millId", "560")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
}
