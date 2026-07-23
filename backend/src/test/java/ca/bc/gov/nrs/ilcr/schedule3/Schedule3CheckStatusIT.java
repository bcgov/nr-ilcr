package ca.bc.gov.nrs.ilcr.schedule3;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test — Story 4.2 POST /api/v1/schedule3/check-status (BR-11/BR-03, read-only). Security
 * OFF (mock ILCR_SUBMITTER). Fixtures: 543 complete/valid (all met, V9); 517 empty cat-3 (missing, V8).
 */
@DisplayName("POST /api/v1/schedule3/check-status — readiness validation (Story 4.2)")
class Schedule3CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3/check-status";

  @Test
  @DisplayName("complete valid document → requirementsMet true + SUC-003")
  void completeDocument_requirementsMet() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "543").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.message.key", is("scheduleRequirementsMetMsg")));
  }

  @Test
  @DisplayName("empty document → requirementsMet false + missing-required errors (verbatim labels)")
  void emptyDocument_reportsMissing() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "517").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors.length()", greaterThan(0)))
        .andExpect(jsonPath("$.errors[*].text",
            hasItem("Licence, Fees, Insurance (Harvest Total $): Value Required")));
  }
}
