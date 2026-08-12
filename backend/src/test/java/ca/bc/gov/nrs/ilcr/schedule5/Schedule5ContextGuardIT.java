package ca.bc.gov.nrs.ilcr.schedule5;

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
 * Acceptance test — Schedule 5 read context guards (AD-4, AD-8). All SIX 400 shapes
 * (missing/blank/non-numeric × millId/year) → ERR-003, closed mill (CLS) → 409 ERR-004, no
 * mill/year context row → 404 ERR-005 — each asserted byte-for-byte, including the ERR-003 TRAILING
 * SPACE. Security is pinned OFF explicitly rather than relying on the code default (relying on it
 * cost Story 8.1 a review patch), so these isolate the context guards; 403 is proven in
 * {@link Schedule5AuthorizationIT}.
 *
 * <p>KEY (Story 7.1 Task 1 gate (ii)): there is NO "no camps" 404. Schedule 5 has no category-'5'
 * {@code ILCR_REPORT_SUMMARY} row in delivery, so a valid ACTIVE mill/year with zero
 * {@code CAMP_REPORT} rows returns 200 with {@code camps: []} (deviation (a); mill 515 covers it in
 * {@link Schedule5DocumentIT}). The 404 is reserved for a missing mill/year context row.
 *
 * <p>⚠ <strong>ERR-00n numbers are per use case — always cite the UC alongside them.</strong> The
 * three constants below are UC-SCH5-001's numbering. Schedule 1 numbers the same outcomes
 * differently ({@code Schedule not found.} is ERR-005 here, the reverse of SCH1 —
 * {@code uc-slice-epic-parity-audit:102}), and the shared {@code MillContextService} that actually
 * throws these three exceptions numbers them ERR-001/002/003 in its own javadoc. All three
 * numberings are correct within their own document, so an unqualified "ERR-003" in a support
 * ticket is ambiguous between "select a mill" and "schedule not found".
 */
@DisplayName("GET /api/v1/schedule5 — mill/year context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule5ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule5";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SEEDED_MILL = "514";
  private static final String SEEDED_YEAR = "2021";

  // UC-SCH5-001 ERR-003 — the trailing space is real (messages.properties
  // millYearNotSelectedErrorMsg:9). Thrown by MillContextService, which calls it ERR-001.
  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  // UC-SCH5-001 ERR-004 (MillContextService: ERR-002).
  private static final String ERR_004 = "This Mill is not active for the current Reporting Year. "
      + "Please select another mill from the Home Page.";
  // UC-SCH5-001 ERR-005 (MillContextService: ERR-003).
  private static final String ERR_005 = "Schedule not found.";

  @Test
  @DisplayName("missing millId -> 400 verbatim ERR-003 (trailing space)")
  void missingMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("missing year -> 400 verbatim ERR-003")
  void missingYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", SEEDED_MILL))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("blank millId -> 400 verbatim ERR-003")
  void blankMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "   ").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("blank year -> 400 verbatim ERR-003")
  void blankYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", SEEDED_MILL).param("year", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 verbatim ERR-003")
  void nonNumericMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("non-numeric year -> 400 verbatim ERR-003")
  void nonNumericYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", SEEDED_MILL).param("year", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("closed mill (516, CLS) -> 409 verbatim ERR-004")
  void closedMill_returns409_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", SEEDED_YEAR))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_004)));
  }

  @Test
  @DisplayName("no mill/year context row -> 404 verbatim ERR-005")
  void noContextRow_returns404_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "999").param("year", SEEDED_YEAR))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_005)));
  }

  @Test
  @DisplayName("known mill, year with no context row -> 404 verbatim ERR-005")
  void knownMillUnknownYear_returns404_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", SEEDED_MILL).param("year", "1999"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_005)));
  }
}
