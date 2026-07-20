package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RED-PHASE ATDD SCAFFOLD — Story 1.1 (AD-10, AD-4, AD-8). Slices S19/S20/S21 + valid pass-through.
 *
 * These acceptance tests assert the mill/year CONTEXT GUARD contract for
 * {@code GET /api/v1/schedule1}. They reference no unbuilt production type (URL +
 * ProblemDetail only), so this file COMPILES today; it is class-{@code @Disabled}
 * as the red-phase gate. Runs with security OFF (mock principal = ILCR_SUBMITTER,
 * which holds VIEW_SCHEDULE) so these isolate the context guards, not authz —
 * 403 is proven separately in {@link Schedule1AuthorizationIT}.
 *
 * RED expectation before implementation: with no {@code Schedule1Controller} the
 * calls 404/500 instead of the asserted statuses — that IS red. The S19 param
 * cases may specifically surface as 500 (today's catch-all) until
 * {@code GlobalExceptionHandler} adds the 400 handlers — also valid red.
 *
 * GREEN = correct status + {@code application/problem+json} + verbatim legacy
 * message text (from the story's MESSAGE BUNDLE; keys preserved incl. trailing
 * space on ERR-002/003 values).
 *
 * ACTIVATION (dev-story Task 9): create the test schema/seed (Task 8), the
 * millcontext guard chain (Task 6), and the controller skeleton (Task 7); set
 * {@code ilcr.security.enabled=false} for this class; then remove {@code @Disabled}.
 */
@DisplayName("GET /api/v1/schedule1 — mill/year context guards (S19/S20/S21)")
class Schedule1ContextGuardIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule1";
    private static final String PROBLEM_JSON = "application/problem+json";

    // Seeded by the shared test snapshot (Task 8): mill 514 – AAA Milling, year 2021, Sch1 track = D (Draft).
    private static final long SEEDED_MILL = 514L;
    private static final int SEEDED_YEAR = 2021;

    // mockMvc is inherited from AbstractOracleIT (built with the security filter chain applied).

    // ---- S19: missing / malformed context param -> 400 ProblemDetail -----------------------------

    @Test
    @DisplayName("S19: missing millId -> 400 ProblemDetail")
    void missingMillId_returns400() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("year", String.valueOf(SEEDED_YEAR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("S19: missing year -> 400 ProblemDetail")
    void missingYear_returns400() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", String.valueOf(SEEDED_MILL)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("S19: non-numeric millId -> 400 ProblemDetail")
    void nonNumericMillId_returns400() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", String.valueOf(SEEDED_YEAR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // ---- S21: unknown mill/year -> 404 ProblemDetail ---------------------------------------------
    // NOTE (2026-07-20 product change): a valid ACTIVE mill/year with NO Schedule 1 summary is NO
    // LONGER a 404 — it now returns a 200 "not initiated" locked empty document (see
    // Schedule1DocumentIT#notInitiatedContext_returnsLockedEmptyDocument). Only genuine context
    // errors (unknown mill, missing/malformed params, closed mill) remain 4xx here.

    @Test
    @DisplayName("S21: unknown mill -> 404 ProblemDetail")
    void unknownMill_returns404() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "999999").param("year", String.valueOf(SEEDED_YEAR)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // ---- S20: mill closed for the year -> 409 ProblemDetail --------------------------------------

    @Test
    @DisplayName("S20: mill closed (CLS) for year -> 409 with verbatim not-active message")
    void millClosedForYear_returns409_verbatimMessage() throws Exception {
        // Seed (Task 8) MUST include a mill whose ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_CODE = 'CLS'.
        mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", String.valueOf(SEEDED_YEAR)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail",
                        is("This Mill is not active for the current Reporting Year. "
                                + "Please select another mill from the Home Page."))); // ERR-002 / millNotActiveForCurrentYearMsg
    }

    // ---- Valid pass-through: guard chain lets a valid principal + valid context through ----------
    // Body is Story 1.2's job; here we only assert it is NOT a guard error (not 4xx).

    @Test
    @DisplayName("valid seeded context -> passes the guard chain (not a 4xx guard error)")
    void validContext_passesGuardChain() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", String.valueOf(SEEDED_MILL))
                        .param("year", String.valueOf(SEEDED_YEAR))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful());
        // NOTE: full document assertions (lineItems/trackStatus/editable/otherCosts/perUnit) belong to Story 1.2.
    }
}
