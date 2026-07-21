package ca.bc.gov.nrs.ilcr.millcontext;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Acceptance tests — Story 1.1 (AD-10, AD-12). The two Home-page option-list endpoints:
 * {@code GET /api/v1/mills} and {@code GET /api/v1/reporting-years}.
 *
 * <p>Runs with security ON ({@code ilcr.security.enabled=true}, the app default) and issues the
 * requests UNAUTHENTICATED — asserting the paths are in the permit-all set (Task 4): pre-selection
 * option lists carry no auth gate and no {@code @PreAuthorize}. With security on,
 * {@code oauth2ResourceServer().jwt()} needs a {@link JwtDecoder} bean at config time; a mock
 * satisfies construction (it is never invoked, since the requests carry no bearer token). Real
 * FAM decoder wiring is the deferred auth story.
 *
 * <p>Asserts the pinned wire contract ({@code MillSummary}, {@code ReportingYear}) via jsonPath only
 * so it compiles independently of the production types. Written RED-FIRST: before the permit-all
 * entries + controller exist the unauthenticated request is 401/404, so the 200/shape/order
 * assertions fail. GREEN requires the DTOs, repository read methods, service methods, the
 * API/controller pair, the SecurityConfiguration permit-all entries, and the {@code V5}
 * second-opened-year seed. Fixtures: {@code V2} (mills 514/515/516/517, year 2021) + {@code V5}
 * (year 2020).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Home option lists — GET /api/v1/mills & /reporting-years (Story 1.1)")
class MillContextListIT extends AbstractOracleIT {

    // Security on -> oauth2ResourceServer().jwt() requires a JwtDecoder bean at config time. The
    // requests below are unauthenticated (no bearer), so the decoder is never called; the mock only
    // satisfies bean construction.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/mills — unauthenticated 200 JSON, all mills asc by number, closed (516) included")
    void listMills_returnsAllMillsAscendingIncludingClosed() throws Exception {
        mockMvc.perform(get("/api/v1/mills").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Plain JSON success payload, NOT application/problem+json.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Every mill is returned (no filter). The shared snapshot carries the four V2 core
                // mills plus the V4 write-fixture mills (518-521), so assert >= 4 rather than a brittle
                // exact count.
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(4)))
                // Ordered by mill number ascending (legacy order by m.mill_number): the four V2 core
                // mills are the lowest-numbered, so they lead the list in order.
                .andExpect(jsonPath("$[0].millNumber", is("514")))
                .andExpect(jsonPath("$[1].millNumber", is("515")))
                .andExpect(jsonPath("$[2].millNumber", is("516")))
                .andExpect(jsonPath("$[3].millNumber", is("517")))
                // Closed mill 516 (CLS) is present — no status filter (S06 parity).
                .andExpect(jsonPath("$[?(@.millId == 516)].millStatusCode", contains("CLS")))
                .andExpect(jsonPath("$[*].millStatusCode", hasItem("ACT")))
                // The first item carries all four pinned fields.
                .andExpect(jsonPath("$[0].millId", is(514)))
                .andExpect(jsonPath("$[0].millName", is("AAA Milling")))
                .andExpect(jsonPath("$[0].millStatusCode", is("ACT")));
    }

    @Test
    @DisplayName("GET /api/v1/reporting-years — unauthenticated 200, opened years desc (2021 before 2020)")
    void listReportingYears_returnsOpenedYearsDescending() throws Exception {
        mockMvc.perform(get("/api/v1/reporting-years").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // V2 (2021) + V5 (2020).
                .andExpect(jsonPath("$.length()", is(2)))
                // Descending by REPORT_YEAR (BR-03).
                .andExpect(jsonPath("$[*].reportYear", contains(2021, 2020)))
                .andExpect(jsonPath("$[0].reportYear", is(2021)));
    }
}
