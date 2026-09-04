package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test — the sweep's mill/year context guards (Story 15.1 AC 5/6/9; UC-CHK-001 S05/S06).
 * Security OFF (the mock ILCR_SUBMITTER holds VIEW_SCHEDULE) so these isolate the guard, not authz
 * (403 is proven in {@link CheckStatusAuthorizationIT}).
 *
 * <p>Every case asserts the verbatim {@code detail} text, never merely "not 200": Schedules 4, 5,
 * 6, 8, 10 and 11 all report zero rows as a vacuous MET, so a sweep that skipped the guard would
 * answer an absent or closed mill-year with a 200 COMPLETE — the one failure direction these tests
 * exist to rule out (AC 9).
 */
@DisplayName("GET /api/v1/check-status — mill/year context guards (Story 15.1)")
class CheckStatusContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status";
  private static final String PROBLEM_JSON = "application/problem+json";

  /**
   * ERR-001, byte-for-byte from legacy {@code messages.properties:18} — the trailing space is real.
   */
  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";

  /** Legacy {@code checkStatus.xhtml:20} renders THIS when the context has no status row (S06). */
  private static final String CHECK_STATUS_NOT_FOUND =
      "One or more of the schedules for the report have not been found.";

  private static final String ERR_002 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";

  @Test
  @DisplayName("S05: missing millId -> 400 ERR-001 verbatim, trailing space included")
  void missingMillId_returns400ErrOne() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("S05: neither param -> the ONE combined ERR-001, not per-field texts")
  void noParams_returns400ErrOne() throws Exception {
    mockMvc
        .perform(get(ENDPOINT))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("S05: non-numeric millId -> 400 ERR-001 (raw-String params, never Spring's 400)")
  void nonNumericMillId_returns400ErrOne() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "abc").param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("S06: unknown mill -> 404 with the CHECK STATUS not-found text, not a vacuous MET")
  void unknownMill_returns404CheckStatusNotFound() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "999999").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("S06: known mill, year with no status row -> 404 check-status not-found")
  void knownMillAbsentYear_returns404CheckStatusNotFound() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "514").param("year", "1999"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(CHECK_STATUS_NOT_FOUND)));
  }

  @Test
  @DisplayName("AC 6: mill closed (CLS) for the year -> 409 ERR-002 verbatim, not a vacuous MET")
  void millClosedForYear_returns409() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "516").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_002)));
  }
}
