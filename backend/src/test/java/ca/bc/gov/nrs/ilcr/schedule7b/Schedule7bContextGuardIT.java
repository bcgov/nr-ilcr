package ca.bc.gov.nrs.ilcr.schedule7b;

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
 * Story 13.1 acceptance — the mill/year context guards on {@code GET /api/v1/schedule7b} (AC6,
 * slices S11/S12/S13). Every message is asserted VERBATIM including trailing whitespace (AD-8): the
 * legacy bundle's {@code millYearNotSelectedErrorMsg} genuinely ends in a space, and "fixing" it
 * here would mask a bundle regression. Security is OFF (mock {@code ILCR_SUBMITTER}).
 */
@DisplayName("GET /api/v1/schedule7b — mill/year context guards (S11/S12/S13)")
class Schedule7bContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7b";
  private static final String PROBLEM_JSON = "application/problem+json";

  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_004 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";
  private static final String ERR_002 = "Schedule not found.";

  @Test
  @DisplayName("S11: missing millId -> 400 with verbatim ERR-003")
  void missingMillId_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("S11: missing year -> 400 with verbatim ERR-003")
  void missingYear_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "514"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("S11: non-numeric millId -> 400 with verbatim ERR-003 (not a framework 400)")
  void nonNumericMillId_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "abc").param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("S12: closed mill (516/CLS) -> 409 with verbatim ERR-004")
  void millClosed_returns409() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "516").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_004)));
  }

  @Test
  @DisplayName("S13: no status row (reporting year not opened) -> 404 with verbatim ERR-002")
  void noStatusRow_returns404() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("millId", "999999").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(ERR_002)));
  }

  @Test
  @DisplayName("An empty culvert list for an active Draft mill/year is a valid 200 (list-schedule)")
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
        .andExpect(jsonPath("$.culverts", is(empty())))
        // The Type dropdown must still be served for an empty schedule, or the Add form has no
        // options.
        .andExpect(jsonPath("$.codeLists.culvertTypes", is(org.hamcrest.Matchers.not(empty()))));
  }
}
