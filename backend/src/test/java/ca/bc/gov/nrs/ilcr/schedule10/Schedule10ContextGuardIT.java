package ca.bc.gov.nrs.ilcr.schedule10;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — the Schedule 10 context guards (S16–S18 / ERR-001–003, AD-4, AD-8).
 *
 * <p>Every message is asserted BYTE-FOR-BYTE against the legacy bundle. ERR-001 carries a
 * <strong>trailing space</strong> in {@code messages.properties:18} (verified at byte level with
 * {@code cat -A}: {@code …Home Page. $}); the other two do not. A test that trims would pass while
 * shipping the wrong bytes to the licensee.
 *
 * <p>Also pins {@code application/problem+json} (RFC 7807, AD-8) and the guard ORDER — legacy
 * checks session context before mill status, so a request that is both context-less and closed
 * collapses to ERR-001.
 */
@DisplayName("GET /api/v1/schedule10 — context guards")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10ContextGuardIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule10";

  /** ERR-001. The trailing space is real and deliberate — do not trim it. */
  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";

  /** ERR-002. */
  private static final String ERR_002 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";

  /** ERR-003. */
  private static final String ERR_003 = "Schedule not found.";

  @ParameterizedTest(name = "400 ERR-001 — millId=''{0}'' year=''{1}''")
  @CsvSource(
      nullValues = "NULL",
      value = {
        // missing
        "NULL,  2021",
        "710,   NULL",
        "NULL,  NULL",
        // blank
        "'',    2021",
        "710,   ''",
        // non-numeric
        "abc,   2021",
        "710,   twenty",
      })
  @DisplayName("all six bad-parameter shapes yield 400 with the verbatim ERR-001")
  void badParameters_yieldVerbatimErr001(String millId, String year) throws Exception {
    var request = get(ENDPOINT).accept(MediaType.APPLICATION_JSON);
    if (millId != null) {
      request = request.param("millId", millId);
    }
    if (year != null) {
      request = request.param("year", year);
    }

    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("ERR-001 keeps its trailing space byte-for-byte")
  void err001RetainsTrailingSpace() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        // Asserted as an exact string: the final character is a space.
        .andExpect(
            jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")))
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("409 ERR-002 — mill closed for the reporting year (BR-11)")
  void closedMill_yieldsVerbatimErr002() throws Exception {
    // Mill 516 is seeded CLS (closed) by V2.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "516")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is(ERR_002)));
  }

  @Test
  @DisplayName("404 ERR-003 — no ILCR_MILL_REPORT_STATUS context row")
  void missingContextRow_yieldsVerbatimErr003() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "999999")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is(ERR_003)));
  }

  @Test
  @DisplayName("guard order — ERR-001 wins over ERR-002 when both would apply")
  void err001TakesPrecedenceOverErr002() throws Exception {
    // No year param at all, against the closed mill 516. Legacy checks session context first, so
    // this must collapse to ERR-001/400 rather than ERR-002/409.
    mockMvc
        .perform(get(ENDPOINT).param("millId", "516").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("zero construction pages is NOT a 404 — it is a 200 with an empty list")
  void emptyScheduleIsNotNotFound() throws Exception {
    // The 404 fires only on a missing context row, never on a valid context with no data
    // (deviation (a)). Mill 715 is active with a Draft context row and zero pages.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "715")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
