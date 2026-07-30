package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 12.2 acceptance — {@code POST /api/v1/schedule7a/check-status} (AC7, slice S29; BR-08).
 * Read-only; mutates nothing; NOT Draft-gated (runs on the Submitted mill 517). Security OFF.
 */
@DisplayName("POST /api/v1/schedule7a/check-status — per-bridge readiness (Story 12.2)")
class Schedule7aCheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7a/check-status";

  @Test
  @DisplayName("514/2021 — bridge 3 missing two costs -> flagged; complete bridges reported met (S29)")
  void incompleteBridge_flagsMissingCosts() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        // 7603 (rowCounter 3) is missing afterInstall(72) + other(73) — two flags, legacy order.
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].key", is("missingRequiredFieldMsg")))
        .andExpect(jsonPath("$.errors[0].text", containsString("Bridge Report Id : 3")))
        .andExpect(jsonPath("$.errors[0].text", containsString(" - Certification After install Cost ")))
        .andExpect(jsonPath("$.errors[0].text", containsString("Value Required")))
        .andExpect(jsonPath("$.errors[1].text", containsString(" - Other Costs ")))
        // 7601 + 7602 pass -> two per-bridge all-met messages; no schedule-wide all-met.
        .andExpect(jsonPath("$.bridgeMessages", hasSize(2)))
        .andExpect(jsonPath("$.bridgeMessages[0].key", is("bridgeRequirementsMetMsg")))
        .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist());
  }

  @Test
  @DisplayName("517/2021 — single complete bridge -> all met (schedule-wide, not Draft-gated)")
  void completeSchedule_allMet() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "517").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.bridgeMessages", hasSize(1)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")))
        .andExpect(jsonPath("$.requirementsMetMessage.text",
            is("All requirements for this schedule have been met")));
  }
}
