package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.contains;
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
 * RED-PHASE ATDD SCAFFOLD — Story 1.2 (AD-5, AD-10, AD-12). GET /api/v1/schedule1 200 aggregate document.
 *
 * <p>Asserts the pinned wire contract via jsonPath only (no unbuilt production type referenced), so
 * this COMPILES today; class-{@code @Disabled} is the red-phase gate. Security is OFF (no
 * {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal applies, isolating the
 * document assembly from authz (covered by {@link Schedule1AuthorizationIT}).
 *
 * <p>RED before implementation: the controller still returns {@code ResponseEntity<Void>}
 * {@code .ok().build()}, so the jsonPath assertions on an empty body fail. GREEN requires: the DTOs,
 * {@code Schedule1Repository}, {@code Schedule1Service}, the controller wiring, and the {@code V3}
 * seed (detail rows + {@code CROWN_VOLUME}/{@code COMMENTS} on summaries 1001/1017).
 *
 * ACTIVATION (dev-story): implement Tasks 1-5, add the V3 seed, then remove {@code @Disabled}.
 */
@DisplayName("GET /api/v1/schedule1 — aggregate document (Story 1.2)")
class Schedule1DocumentIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule1";

    @Test
    @DisplayName("514/2021 Draft — full pinned document with server-computed derived values")
    void draftContext_returnsPinnedDocument() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "514")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.millId", is(514)))
                .andExpect(jsonPath("$.year", is(2021)))
                .andExpect(jsonPath("$.trackStatus", is("D")))
                .andExpect(jsonPath("$.editable", is(true)))
                .andExpect(jsonPath("$.crownVolume", is(12345)))
                .andExpect(jsonPath("$.comments", is("Seed comment for 514/2021")))
                // code-12 line item: seed VOLUME 1000 / COST 50000 -> perUnit 50.0 (server-computed)
                .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].cost", contains(50000)))
                .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].perUnit", contains(50.0)))
                // Other Costs: shared volume 8000, two itemized rows 12000 each -> subtotal 24000, count 2, perUnit 3.0
                .andExpect(jsonPath("$.otherCosts.volume", is(8000)))
                .andExpect(jsonPath("$.otherCosts.count", is(2)))
                .andExpect(jsonPath("$.otherCosts.costSubtotal", is(24000)))
                .andExpect(jsonPath("$.otherCosts.perUnit", is(3.0)));
    }

    @Test
    @DisplayName("517/2021 non-Draft — trackStatus S, editable false")
    void nonDraftContext_notEditable() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "517")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackStatus", is("S")))
                .andExpect(jsonPath("$.editable", is(false)));
    }
}
