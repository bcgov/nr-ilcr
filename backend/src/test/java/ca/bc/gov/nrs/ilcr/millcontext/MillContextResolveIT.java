package ca.bc.gov.nrs.ilcr.millcontext;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Acceptance tests — Story 1.2 (AD-10, AD-12 amended contract). {@code GET /api/v1/mill-context}:
 * resolve a chosen mill/year into the pinned {@code WorkingContext} with both independent track
 * statuses (AR6), the S06 closed-mill flag, S07 null-status tolerance, the S04/S05/S08 verbatim
 * required-field 400s, and 404 for unknown mill/year.
 *
 * <p>Runs with security ON ({@code ilcr.security.enabled=true}, app default) and issues requests
 * UNAUTHENTICATED — the path must be in the GET-scoped {@code HOME_PUBLIC_PATHS} permit set.
 * {@code @MockitoBean JwtDecoder} satisfies {@code oauth2ResourceServer().jwt()} construction
 * (never invoked — no bearer tokens here). Written RED-FIRST: before the endpoint + permit entry
 * exist, requests are 401/404 and every assertion fails.
 *
 * <p>Fixtures (V2/V4/V5/V6, fixture-robust assertions only — no positional/exact-count coupling):
 * 514/2020 both tracks (S + silvi D, dates); 514/2021 1-10 D only (silvi NULL, draft date);
 * 516/2021 closed (CLS, no view row → date null); (515, 2020) selectable mill + opened year with
 * NO status row (S07); 522 never-enrolled (not selectable → 404); 2019 not opened.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Home working context — GET /api/v1/mill-context (Story 1.2)")
class MillContextResolveIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/mill-context";
    private static final String MILL_REQUIRED = "Mill: Value is required.";
    private static final String YEAR_REQUIRED = "Reporting Year: Value is required.";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("514/2020 — 200 with BOTH independent track statuses and their dates")
    void bothTracks_returnsFullContext() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2020")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.millId", is(514)))
                .andExpect(jsonPath("$.millNumber", is("514")))
                .andExpect(jsonPath("$.millName", is("AAA Milling")))
                .andExpect(jsonPath("$.reportYear", is(2020)))
                .andExpect(jsonPath("$.millViewable", is(true)))
                .andExpect(jsonPath("$.schedules1To10Status.code", is("S")))
                .andExpect(jsonPath("$.schedules1To10Status.description", is("Submitted")))
                // Submitted -> MILL_STATUS_SUBMIT_DATE '02 2020-11-30' with 3-char prefix stripped.
                .andExpect(jsonPath("$.schedules1To10Status.date", is("2020-11-30")))
                .andExpect(jsonPath("$.schedule11Status.code", is("D")))
                .andExpect(jsonPath("$.schedule11Status.description", is("Draft")))
                // Silvi Draft -> SILVI_STATUS_DRAFT_DATE (each track uses its OWN code — the legacy
                // cross-track bug is deliberately not reproduced).
                .andExpect(jsonPath("$.schedule11Status.date", is("2020-08-01")));
    }

    @Test
    @DisplayName("514/2021 — 200, 1-10 Draft with date; NULL silvi code -> schedule11Status absent")
    void nullSilviCode_omitsSchedule11Status() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules1To10Status.code", is("D")))
                .andExpect(jsonPath("$.schedules1To10Status.description", is("Draft")))
                .andExpect(jsonPath("$.schedules1To10Status.date", is("2021-03-15")))
                .andExpect(jsonPath("$.schedule11Status").doesNotExist())
                .andExpect(jsonPath("$.millViewable", is(true)));
    }

    @Test
    @DisplayName("516/2021 closed mill — 200 with millViewable:false, date absent (S06)")
    void closedMill_isFlagNotError() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "516").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.millId", is(516)))
                .andExpect(jsonPath("$.millViewable", is(false)))
                .andExpect(jsonPath("$.schedules1To10Status.code", is("D")))
                // No ILCR_MILL_REPORT_STATUS_RPT_VW row for 516/2021 -> date null -> omitted
                // (frontend renders "Not Initiated", Story 1.4).
                .andExpect(jsonPath("$.schedules1To10Status.date").doesNotExist());
    }

    @Test
    @DisplayName("(515, 2020) no status row — 200 with both statuses absent (S07)")
    void noStatusRow_returnsContextWithNullStatuses() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "515").param("year", "2020")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.millId", is(515)))
                .andExpect(jsonPath("$.reportYear", is(2020)))
                .andExpect(jsonPath("$.millViewable", is(true)))
                .andExpect(jsonPath("$.schedules1To10Status").doesNotExist())
                .andExpect(jsonPath("$.schedule11Status").doesNotExist());
    }

    @Test
    @DisplayName("missing millId — 400 problem+json with verbatim FLD-001 text (S04)")
    void missingMill_returns400WithVerbatimText() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(MILL_REQUIRED)))
                .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED)));
    }

    @Test
    @DisplayName("missing year — 400 with verbatim FLD-002 text (S05)")
    void missingYear_returns400WithVerbatimText() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "514").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(YEAR_REQUIRED)))
                .andExpect(jsonPath("$.messages[*].text", contains(YEAR_REQUIRED)));
    }

    @Test
    @DisplayName("both missing — 400 carries BOTH verbatim texts together, Mill first (S08)")
    void bothMissing_returns400WithBothMessages() throws Exception {
        mockMvc.perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString(MILL_REQUIRED)))
                .andExpect(jsonPath("$.detail", containsString(YEAR_REQUIRED)))
                // Field order mirrors home.xhtml: Mill, then Reporting Year.
                .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED, YEAR_REQUIRED)));
    }

    @Test
    @DisplayName("non-numeric millId — 400 with the Mill required text (legacy: only valid options exist)")
    void nonNumericMill_returns400WithVerbatimText() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "abc").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED)));
    }

    @Test
    @DisplayName("unknown mill 999 / never-enrolled 522 / unopened year 2019 — 404 problem+json")
    void unknownMillOrYear_returns404() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("millId", "999").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        // Mill 522 has an xref but NO report-status row for any year -> not selectable (legacy
        // getMills() parity, Story 1.1 review decision) -> 404, same as an unknown id.
        mockMvc.perform(get(ENDPOINT).param("millId", "522").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2019")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("auth boundary — GET permitted unauthenticated; POST on the path still 401")
    void permitAllIsGetScopedForMillContext() throws Exception {
        // GET is reachable unauthenticated (asserted implicitly by every case above); the non-GET
        // side of the permit must still hit the auth gate.
        mockMvc.perform(post(ENDPOINT).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
