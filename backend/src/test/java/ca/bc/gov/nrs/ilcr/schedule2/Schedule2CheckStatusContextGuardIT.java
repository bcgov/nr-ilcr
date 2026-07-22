package ca.bc.gov.nrs.ilcr.schedule2;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Acceptance test — check-status mill/year context guards (AD-4). Security OFF (mock ILCR_SUBMITTER
 * holds VIEW_SCHEDULE) so these isolate the context guards, not authz (403 is proven in
 * {@link Schedule2CheckStatusAuthorizationIT}). Same guard contract as read/write: missing/non-numeric
 * param -> 400; unknown mill -> 404; closed (CLS) mill -> 409. There is NO no-summary 404.
 */
@DisplayName("POST /api/v1/schedule2/check-status — mill/year context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule2CheckStatusContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2/check-status";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final int SEEDED_YEAR = 2021;

  @Test
  @DisplayName("missing millId -> 400 ProblemDetail")
  void missingMillId_returns400() throws Exception {
    mockMvc.perform(post(ENDPOINT).with(csrf()).param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 ProblemDetail")
  void nonNumericMillId_returns400() throws Exception {
    mockMvc.perform(post(ENDPOINT).with(csrf()).param("millId", "abc").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("unknown mill -> 404 ProblemDetail")
  void unknownMill_returns404() throws Exception {
    mockMvc.perform(post(ENDPOINT).with(csrf()).param("millId", "999999").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("mill closed (CLS) for year -> 409 verbatim not-active message")
  void millClosedForYear_returns409() throws Exception {
    mockMvc.perform(post(ENDPOINT).with(csrf()).param("millId", "516").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail",
            is("This Mill is not active for the current Reporting Year. "
                + "Please select another mill from the Home Page.")));
  }
}
