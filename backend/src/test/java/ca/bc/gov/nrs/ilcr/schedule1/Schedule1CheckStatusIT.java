package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Story 2.6 acceptance (BR-07, AD-5/8/10): POST /api/v1/schedule1/check-status against a real Oracle
 * dialect. Security OFF (mock ILCR_SUBMITTER). Fixtures (V7): 528 fully populated (S14), 529 with a
 * null-cost itemized row (S18 WRN-002); 514 partially populated (S15 missing fields).
 */
@DisplayName("POST /api/v1/schedule1/check-status — BR-07 (Story 2.6)")
class Schedule1CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule1/check-status";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("528 fully populated -> requirementsMet, verbatim SUC-003, no data change")
  void allRequirementsMet() throws Exception {
    int before = JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1030");
    mockMvc.perform(post(ENDPOINT).param("millId", "528").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors.length()", is(0)))
        .andExpect(jsonPath("$.message.text", is("All requirements for this schedule have been met")));
    // Read-only: no data changed.
    assertEquals(before, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1030"), "check-status must not mutate data");
  }

  @Test
  @DisplayName("514 partially populated -> missing-field errors (verbatim), requirementsMet false")
  void missingFields() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors[*].text", hasItem("Log Transportation - Volume: Value Required")))
        .andExpect(jsonPath("$.errors[*].text", hasItem("Log Transportation - Cost: Value Required")))
        .andExpect(jsonPath("$.errors[*].text", hasItem("Total Silviculture - Volume: Value Required")))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("530 Other-Costs volume>0 but no cost -> verbatim FLD-008")
  void otherCostsCostConsistency() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "530").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors[*].text", hasItem(
            "Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater than 0")));
  }

  @Test
  @DisplayName("531 Other-Costs cost>0 but volume 0 -> verbatim FLD-009")
  void otherCostsVolumeConsistency() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "531").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[*].text", hasItem(
            "Subtotal Other Costs (1): Volume: must be greater than 0 when Cost is greater than 0")));
  }

  @Test
  @DisplayName("532 no shared-volume row -> verbatim FLD-010 (Subtotal Other Costs (0) - Volume)")
  void otherCostsVolumeRequired() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "532").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[*].text",
            hasItem("Subtotal Other Costs (0) - Volume: Value Required")));
  }

  @Test
  @DisplayName("529 empty-cost row -> verbatim WRN-002 warning (N=2)")
  void emptyCostWarning() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "529").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warnings[*].text", hasItem(
            "Subtotal Other Costs (2) - Cost: One or more entries contain an empty Cost value. "
                + "Please verify there are no Other Costs to be entered.")));
  }
}
