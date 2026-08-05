package ca.bc.gov.nrs.ilcr.schedule6;

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
 * Acceptance test — Schedule 6 read context guards (AD-4, AD-8). Missing/non-numeric param → 400
 * ERR-001, unknown mill (no context) → 404 ERR-003, closed mill (CLS) → 409 ERR-002 — each with the
 * byte-for-byte legacy message (incl. the ERR-001 trailing space). Runs with security OFF (mock
 * ILCR_SUBMITTER holds VIEW_SCHEDULE) so these isolate the context guards, not authz (403 is proven in
 * {@link Schedule6AuthorizationIT}).
 *
 * <p>KEY (Story 8.1 Task 1): there is NO "no records" 404 — a valid, active mill/year with no
 * {@code ROAD_MAINTENANCE_REPORT} rows returns 200 (legacy-faithful, mirrors Schedule 4). The 404 is
 * reserved for the missing mill/year context.
 */
@DisplayName("GET /api/v1/schedule6 — mill/year context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule6ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule6";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SEEDED_YEAR = "2021";
  // ERR-001 — the trailing space is real (messages.properties millYearNotSelectedErrorMsg).
  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_002 = "This Mill is not active for the current Reporting Year. "
      + "Please select another mill from the Home Page.";
  private static final String ERR_003 = "Schedule not found.";

  @Test
  @DisplayName("missing millId -> 400 verbatim ERR-001 (trailing space)")
  void missingMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("missing year -> 400 verbatim ERR-001")
  void missingYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("non-numeric millId -> 400 verbatim ERR-001")
  void nonNumericMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("non-numeric year -> 400 verbatim ERR-001")
  void nonNumericYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("blank millId -> 400 verbatim ERR-001")
  void blankMillId_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "").param("year", SEEDED_YEAR))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("blank year -> 400 verbatim ERR-001")
  void blankYear_returns400_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", ""))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("unknown mill (no context) -> 404 verbatim ERR-003")
  void unknownMill_returns404_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "999999").param("year", SEEDED_YEAR))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("mill closed (CLS) for year -> 409 verbatim ERR-002")
  void millClosedForYear_returns409_verbatim() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", SEEDED_YEAR))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_002)));
  }

  @Test
  @DisplayName("valid active mill/year with NO road records -> 200 empty (NOT 404)")
  void validActiveNoRecords_returns200Empty() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "515")
            .param("year", SEEDED_YEAR)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.roadRecords.length()", is(0)))
        .andExpect(jsonPath("$.totalVolume", is(0)))
        .andExpect(jsonPath("$.totalCost", is(0)))
        // No records -> no general comment either (distinct from the S18 lone-comment 200).
        .andExpect(jsonPath("$.generalComments").doesNotExist());
  }
}
