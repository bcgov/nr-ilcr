package ca.bc.gov.nrs.ilcr.millcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * list requests UNAUTHENTICATED — asserting the two paths are in the GET-scoped permit-all set
 * (Task 4) while neighbouring {@code /api/**} traffic still requires authentication. With security
 * on, {@code oauth2ResourceServer().jwt()} needs a {@link JwtDecoder} bean at config time; a mock
 * satisfies construction (never invoked — no request carries a bearer token). Real FAM decoder
 * wiring is the deferred auth story.
 *
 * <p>Assertions avoid positional/exact-count coupling to the shared snapshot (which later stories
 * extend, V5 header rule): ordering is asserted on the sorted projection itself, per-mill fields via
 * {@code millId} filters. Fixtures: {@code V2} (mills 514/515/516/517, year 2021), {@code V4}
 * (mills 518-521), {@code V5} (year 2020 + never-enrolled mill 522).
 *
 * <p>Mills-list semantics are exact legacy {@code getMills()} parity (2026-07-21 review decision):
 * listed iff the mill has its status xref AND any {@code ILCR_MILL_REPORT_STATUS} row — mill 522
 * (xref but never enrolled) proves the exclusion; closed mill 516 (CLS) proves no status filter.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Home option lists — GET /api/v1/mills & /reporting-years (Story 1.1)")
class MillContextListIT extends AbstractOracleIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Security on -> oauth2ResourceServer().jwt() requires a JwtDecoder bean at config time. The
    // requests below are unauthenticated (no bearer), so the decoder is never called; the mock only
    // satisfies bean construction.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("GET /api/v1/mills — unauthenticated 200 JSON; asc by mill number; closed included; never-enrolled excluded")
    void listMills_returnsEnrolledMillsAscendingIncludingClosed() throws Exception {
        String body = mockMvc.perform(get("/api/v1/mills").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Plain JSON success payload, NOT application/problem+json.
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // At least the four V2 core mills (the shared snapshot grows; no exact count).
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(4)))
                // Each V2 core mill carries all four pinned fields, asserted by id (not position).
                .andExpect(jsonPath("$[?(@.millId == 514)].millNumber", contains("514")))
                .andExpect(jsonPath("$[?(@.millId == 514)].millName", contains("AAA Milling")))
                .andExpect(jsonPath("$[?(@.millId == 514)].millStatusCode", contains("ACT")))
                .andExpect(jsonPath("$[?(@.millId == 515)].millStatusCode", contains("ACT")))
                // Closed mill 516 (CLS) is present — no status filter (S06 parity).
                .andExpect(jsonPath("$[?(@.millId == 516)].millStatusCode", contains("CLS")))
                .andExpect(jsonPath("$[?(@.millId == 517)].millStatusCode", contains("ACT")))
                // Never-enrolled mill 522 (xref, but no ILCR_MILL_REPORT_STATUS row) is EXCLUDED
                // (legacy getMills() inner-joins millReportStatuses).
                .andExpect(jsonPath("$[?(@.millId == 522)]").isEmpty())
                .andReturn().getResponse().getContentAsString();

        // Ordering asserted on the payload itself (order by MILL_NUMBER asc, MILL_ID tiebreak) —
        // robust to any future fixture mills, unlike positional $[i] asserts.
        JsonNode mills = MAPPER.readTree(body);
        List<long[]> keys = new ArrayList<>();
        for (JsonNode mill : mills) {
            assertFalse(mill.get("millId").isNull(), "millId must always be present");
            assertFalse(mill.get("millStatusCode").isNull(), "millStatusCode must always be present");
            keys.add(new long[] {
                mill.get("millNumber").asLong(), mill.get("millId").asLong()
            });
        }
        List<long[]> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.<long[]>comparingLong(k -> k[0]).thenComparingLong(k -> k[1]));
        for (int i = 0; i < keys.size(); i++) {
            assertEquals(sorted.get(i)[1], keys.get(i)[1],
                "mills not ordered by mill number ascending at index " + i);
        }
        assertTrue(keys.size() >= 4, "expected at least the four V2 core mills");
    }

    @Test
    @DisplayName("GET /api/v1/reporting-years — unauthenticated 200; opened years desc (2021 before 2020)")
    void listReportingYears_returnsOpenedYearsDescending() throws Exception {
        String body = mockMvc.perform(get("/api/v1/reporting-years").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // Both seeded years present (V2: 2021, V5: 2020) — no exact count, later
                // migrations may open more years.
                .andExpect(jsonPath("$[*].reportYear", hasItems(2021, 2020)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
                .andReturn().getResponse().getContentAsString();

        // Strictly descending by reportYear (BR-03), robust to future year seeds.
        JsonNode years = MAPPER.readTree(body);
        int previous = Integer.MAX_VALUE;
        for (JsonNode year : years) {
            int reportYear = year.get("reportYear").asInt();
            assertTrue(reportYear < previous,
                "reporting years not strictly descending: " + reportYear + " after " + previous);
            previous = reportYear;
        }
    }

    @Test
    @DisplayName("auth boundary — neighbouring /api/** paths still 401 unauthenticated; non-GET on public paths 401")
    void permitAllIsScopedToTheTwoGetEndpoints() throws Exception {
        // A non-permitted /api/** path must still demand authentication with security on — guards
        // against a future matcher-ordering mistake silently widening the permit-all set.
        mockMvc.perform(get("/api/v1/schedule1")
                        .param("millId", "514")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // The permit-all is GET-scoped: an unauthenticated POST to the same public path is
        // rejected by the auth gate (401), not let through to MVC (405).
        mockMvc.perform(post("/api/v1/mills").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
