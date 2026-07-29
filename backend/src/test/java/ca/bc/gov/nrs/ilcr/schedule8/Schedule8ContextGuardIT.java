package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 8 read context guards (AD-4, AD-8). Missing/non-numeric param → 400,
 * unknown mill → 404, closed mill (CLS) → 409 with the verbatim not-active message. Runs with security
 * OFF (mock ILCR_SUBMITTER holds VIEW_SCHEDULE) so these isolate the context guards, not authz (403
 * is proven in {@link Schedule8AuthorizationIT}).
 *
 * <p>KEY: there is NO "no pages" 404 — a valid, active mill/year with no category-{@code '8'}
 * {@code TREE_TO_TRUCK_REPORT} rows returns 200 (proven in {@link Schedule8DocumentIT}).
 */
@DisplayName("GET /api/v1/schedule8 — mill/year context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule8";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final int SEEDED_YEAR = 2021;

  @Test
  @DisplayName("missing millId -> 400 ProblemDetail")
  void missingMillId_returns400() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("missing year -> 400 ProblemDetail")
  void missingYear_returns400() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "570"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 ProblemDetail")
  void nonNumericMillId_returns400() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("unknown mill -> 404 ProblemDetail")
  void unknownMill_returns404() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "999999").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("mill closed (CLS) for year -> 409 with verbatim not-active message")
  void millClosedForYear_returns409_verbatimMessage() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", String.valueOf(SEEDED_YEAR)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail",
            is("This Mill is not active for the current Reporting Year. "
                + "Please select another mill from the Home Page."))); // millNotActiveForCurrentYearMsg
  }

  @Test
  @DisplayName("valid active mill/year with NO pages -> 200 (NOT 404)")
  void validActiveNoPages_returns200() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "572")
            .param("year", String.valueOf(SEEDED_YEAR))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
