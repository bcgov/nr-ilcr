package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.hamcrest.Matchers.empty;
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
 * Story 12.1 acceptance — mill/year context guards for {@code GET /api/v1/schedule7a} (AC6, slices
 * S16/S17/S20; AD-4, AD-8). All errors are RFC 7807 {@code application/problem+json} with VERBATIM
 * legacy text (ERR-001's trailing space included). An empty bridge list for an active Draft
 * mill/year is a valid 200 (the list-schedule divergence). Security is OFF (mock {@code
 * ILCR_SUBMITTER}).
 */
@DisplayName("GET /api/v1/schedule7a — mill/year context guards (S16/S17/S20)")
class Schedule7aContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7a";
  private static final String PROBLEM_JSON = "application/problem+json";

  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_002 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";
  private static final String ERR_003 = "Schedule not found.";

  @Test
  @DisplayName("S16: missing millId -> 400 with verbatim ERR-001")
  void missingMillId_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("S16: non-numeric millId -> 400 with verbatim ERR-001")
  void nonNumericMillId_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "abc").param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("S17: closed mill (516/CLS) -> 409 with verbatim ERR-002")
  void millClosed_returns409() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "516").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_002)));
  }

  @Test
  @DisplayName("S20: no status row (reporting year not opened) -> 404 with verbatim ERR-003")
  void noStatusRow_returns404() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "999999").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("empty bridge list for an active Draft mill/year -> valid 200 (list-schedule)")
  void emptyList_returns200() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "515")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.bridges", is(empty())));
  }
}
