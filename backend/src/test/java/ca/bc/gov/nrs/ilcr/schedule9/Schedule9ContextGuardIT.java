package ca.bc.gov.nrs.ilcr.schedule9;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 9 read context guards (AD-4, AD-8), UC-SCH9-001 EF1-3. The 400 shapes
 * (missing/blank/non-numeric × millId/year) → ERR-003, closed mill (CLS) → 409 ERR-004, no
 * mill/year context row → 404 ERR-005 — each asserted byte-for-byte, including the ERR-003 TRAILING
 * SPACE. Security is pinned OFF explicitly (Story 8.1's lesson) so these isolate the context
 * guards; 403 is proven in {@link Schedule9AuthorizationIT}.
 *
 * <p>Like Schedules 4/5/6, Schedule 9 is summary-less: a valid ACTIVE mill/year with zero {@code
 * CONTRACTUAL_WORK_REPORT} rows returns 200 with {@code records: []} (mill 515 in {@link
 * Schedule9DocumentIT}), NOT a 404. The 404 is reserved for a missing mill/year context row. These
 * guards are delegated to the shared {@code MillContextService.validateMillYearActive}, so the
 * verbatim strings match Schedules 5/6 exactly.
 */
@DisplayName("GET /api/v1/schedule9 — mill/year context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule9ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule9";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SEEDED_MILL = "514";
  private static final String SEEDED_YEAR = "2021";

  // UC-SCH9-001 ERR-003 — the trailing space is real (messages.properties millYearNotSelected).
  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_004 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";
  private static final String ERR_005 = "Schedule not found.";

  @Test
  @DisplayName("missing millId -> 400 verbatim ERR-003 (trailing space)")
  void missingMillId_returns400_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("missing year -> 400 verbatim ERR-003")
  void missingYear_returns400_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", SEEDED_MILL))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("blank millId -> 400 verbatim ERR-003")
  void blankMillId_returns400_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "   ").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 verbatim ERR-003")
  void nonNumericMillId_returns400_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "abc").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("non-numeric year -> 400 verbatim ERR-003")
  void nonNumericYear_returns400_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", SEEDED_MILL).param("year", "xyz"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("closed mill (516, CLS) -> 409 verbatim ERR-004 (BR-10 blocks viewing)")
  void closedMill_returns409_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "516").param("year", SEEDED_YEAR))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_004)));
  }

  @Test
  @DisplayName("no mill/year context row -> 404 verbatim ERR-005")
  void noContextRow_returns404_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "999").param("year", SEEDED_YEAR))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_005)));
  }

  @Test
  @DisplayName("known mill, year with no context row -> 404 verbatim ERR-005")
  void knownMillUnknownYear_returns404_verbatim() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", SEEDED_MILL).param("year", "1999"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_005)));
  }
}
