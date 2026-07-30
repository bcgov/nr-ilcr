package ca.bc.gov.nrs.ilcr.schedule3;

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
 * Acceptance test — Story 4.1 (AD-5, AD-10, AD-12). GET /api/v1/schedule3 200 aggregate document with
 * the three-column model and all server-computed derived values. Security OFF (mock ILCR_SUBMITTER),
 * so this isolates document assembly from authz (covered by {@link Schedule3AuthorizationIT}). Seeded
 * by V8 on summary 1003 (514/2021).
 */
@DisplayName("GET /api/v1/schedule3 — aggregate document (Story 4.1)")
class Schedule3DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3";

  @Test
  @DisplayName("514/2021 Draft — full pinned three-column document with server-computed derived values")
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
        .andExpect(jsonPath("$.revisionCount", is(0)))
        .andExpect(jsonPath("$.overrideHarvestTotalPop", is("Y")))   // read from summary LOCATION
        .andExpect(jsonPath("$.comments", is("Seed Schedule 3 comment for 514/2021")))
        // Licenses (27): harvest 100000 / PO&P 40000 -> crown 60000.
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].harvest", contains(100000)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].pop", contains(40000)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].crown", contains(60000)))
        // Annual Rents (29): Harvest-only -> PO&P 0, crown = harvest.
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 29)].pop", contains(0)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 29)].crown", contains(30000)))
        // Scaling (33): PO&P derived = 0.5 * 60000 = 30000.
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 33)].pop", contains(30000)))
        // Silviculture Admin (37): Harvest-only -> crown = harvest.
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 37)].crown", contains(150000)))
        // Derived totals.
        .andExpect(jsonPath("$.subtotalActualCosts.harvest", is(900000)))
        .andExpect(jsonPath("$.subtotalActualCosts.pop", is(300000)))
        .andExpect(jsonPath("$.subtotalActualCosts.crown", is(600000)))
        .andExpect(jsonPath("$.includedUnacceptableCosts.harvest", is(30000)))
        .andExpect(jsonPath("$.includedUnacceptableCosts.crown", is(30000)))
        .andExpect(jsonPath("$.totalCosts.harvest", is(870000)))
        .andExpect(jsonPath("$.totalCosts.pop", is(300000)))
        .andExpect(jsonPath("$.totalCosts.crown", is(570000)))
        // Timber blocks: volume entered, cost/perUnit derived (perUnit scale-2).
        .andExpect(jsonPath("$.popTimber.volume", is(54321)))
        .andExpect(jsonPath("$.popTimber.cost", is(300000)))
        .andExpect(jsonPath("$.popTimber.perUnit", is(5.52)))
        .andExpect(jsonPath("$.crownTimber.cost", is(570000)))
        .andExpect(jsonPath("$.crownTimber.perUnit", is(10.49)))
        .andExpect(jsonPath("$.totalOverhead.volume", is(108642)))
        .andExpect(jsonPath("$.totalOverhead.cost", is(870000)))
        .andExpect(jsonPath("$.totalOverhead.perUnit", is(8.01)))
        // Sub-page counts (CNT-001): no acceptable rows; unacceptable = 0 rows + 1 for Annual Rents.
        .andExpect(jsonPath("$.otherAcceptableCount", is(0)))
        .andExpect(jsonPath("$.unacceptableCount", is(1)))
        .andExpect(jsonPath("$.message").doesNotExist());
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
