package ca.bc.gov.nrs.ilcr.schedule11;

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
 * Story 25.1 acceptance — mill/year context guards for {@code GET /api/v1/schedule11}
 * (AC3/AC4/AC5, slices S11/S12/S13; AD-4, AD-8). All errors are RFC 7807
 * {@code application/problem+json} with VERBATIM legacy text — including ERR-001's real trailing
 * space ({@code messages.properties:18} in the legacy bundle). Security is OFF (mock
 * {@code ILCR_SUBMITTER}) so these isolate the guards, not authz.
 *
 * <p>The 404 keys on the {@code ILCR_MILL_REPORT_STATUS} row (legacy {@code Schedule11MB.init()}
 * &rarr; {@code scheduleNotFound}) — NEVER on zero locations, which is a valid 200
 * ({@link Schedule11DocumentIT} AC2). This is the list-schedule divergence from Schedule 1's
 * summary-keyed 404.
 */
@DisplayName("GET /api/v1/schedule11 — mill/year context guards (S11/S12/S13)")
class Schedule11ContextGuardIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule11";
    private static final String PROBLEM_JSON = "application/problem+json";

    // ERR-001 (messages.properties:18) — the trailing space is real and asserted verbatim.
    private static final String ERR_001 =
            "Please Select Mill and Reporting Year in the Home Page. ";
    // ERR-002 (messages.properties:21).
    private static final String ERR_002 =
            "This Mill is not active for the current Reporting Year. "
                    + "Please select another mill from the Home Page.";
    // ERR-003 (messages.properties:59).
    private static final String ERR_003 = "Schedule not found.";

    // ---- S11 / AC3: missing, blank, or malformed context params -> 400 + verbatim ERR-001 -------

    @Test
    @DisplayName("S11: missing millId -> 400 with verbatim ERR-001 (trailing space included)")
    void missingMillId_returns400_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("year", "2021"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_001)));
    }

    @Test
    @DisplayName("S11: missing year -> 400 with verbatim ERR-001")
    void missingYear_returns400_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "610"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_001)));
    }

    @Test
    @DisplayName("S11: both params missing -> ONE combined 400 ERR-001 (legacy shows one message)")
    void bothMissing_returns400_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_001)));
    }

    @Test
    @DisplayName("S11: non-numeric millId -> 400 with verbatim ERR-001")
    void nonNumericMillId_returns400_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", "2021"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_001)));
    }

    @Test
    @DisplayName("S11: blank year -> 400 with verbatim ERR-001")
    void blankYear_returns400_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "610").param("year", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_001)));
    }

    // ---- S12 / AC4: mill not active (ACT) for the year -> 409 + verbatim ERR-002 ----------------

    @Test
    @DisplayName("S12: closed mill (516/CLS) -> 409 with verbatim ERR-002")
    void millClosedForYear_returns409_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", "2021"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_002)));
    }

    // ---- S13 / AC5: no ILCR_MILL_REPORT_STATUS row -> 404 + verbatim ERR-003 --------------------

    @Test
    @DisplayName("S13: no status row for mill/year -> 404 with verbatim 'Schedule not found.'")
    void noStatusRow_returns404_verbatimMessage() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "999999").param("year", "2021"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_003)));
    }

    @Test
    @DisplayName("S13: seeded mill, unseeded year -> 404 (status row is per mill AND year)")
    void seededMillUnseededYear_returns404() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "610").param("year", "1999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is(ERR_003)));
    }

    // ---- Valid pass-through ---------------------------------------------------------------------

    @Test
    @DisplayName("valid seeded context (610/2021) -> passes the guard chain")
    void validContext_passesGuardChain() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "610")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful());
    }
}
