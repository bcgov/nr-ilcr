package ca.bc.gov.nrs.ilcr.schedule3;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Acceptance test — Story 4.1 mill/year CONTEXT GUARD contract for GET /api/v1/schedule3 (AD-4, AD-8).
 * Reuses {@code MillContextService.validateScheduleViewable(..., "3")}, so the guard order + verbatim
 * messages match Schedule 1. Security OFF (mock ILCR_SUBMITTER) to isolate the guards from authz.
 * S13 (no-context param → 400), S14 (mill closed → 409), S16 (schedule not found → 404).
 */
@DisplayName("GET /api/v1/schedule3 — mill/year context guards (S13/S14/S16)")
class Schedule3ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3";
  private static final String PROBLEM_JSON = "application/problem+json";

  @Test
  @DisplayName("S13: missing millId -> 400 ProblemDetail")
  void missingMillId_returns400() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("S13: non-numeric millId -> 400 ProblemDetail")
  void nonNumericMillId_returns400() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("S16: unknown mill -> 404 ProblemDetail")
  void unknownMill_returns404() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "999999").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("S16: active mill but no Schedule 3 summary -> 404 'Schedule not found.'")
  void noScheduleSummary_returns404_verbatimMessage() throws Exception {
    // Mill 515 is ACT with a report-status row but has NO category-3 summary (guard 2 → 404).
    mockMvc.perform(get(ENDPOINT).param("millId", "515").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is("Schedule not found.")));
  }

  @Test
  @DisplayName("S14: mill closed (CLS) for year -> 409 with verbatim not-active message")
  void millClosedForYear_returns409_verbatimMessage() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail",
            is("This Mill is not active for the current Reporting Year. "
                + "Please select another mill from the Home Page.")));
  }

  @Test
  @DisplayName("valid seeded context -> passes the guard chain (200)")
  void validContext_passesGuardChain() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "514")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
