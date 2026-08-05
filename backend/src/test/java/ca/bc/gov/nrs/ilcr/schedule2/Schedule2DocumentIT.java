package ca.bc.gov.nrs.ilcr.schedule2;

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
 * Acceptance test — Story 3.1 (AD-5, AD-10, AD-12). GET /api/v1/schedule2 aggregate document.
 *
 * <p>Security OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * applies, isolating document assembly from authz (covered by {@link Schedule2AuthorizationIT}).
 * Asserts the pinned wire contract and the exact server-computed derived figures against the V5 seed.
 */
@DisplayName("GET /api/v1/schedule2 — aggregate document (Story 3.1)")
class Schedule2DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2";

  @Test
  @DisplayName("621/2021 Draft — full pinned document with server-computed derived values")
  void draftContext_returnsPinnedDocument() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "621")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(621)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.revisionCount", is(0)))
        .andExpect(jsonPath("$.comments", is("Seed Schedule 2 comment for 621/2021")))
        // purchasedLogCost: cost 25 = 500000; volume carried Sch3 118 = 10000; perUnit 50.0
        .andExpect(jsonPath("$.purchasedLogCost.volume", is(10000)))
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(500000)))
        .andExpect(jsonPath("$.purchasedLogCost.perUnit", is(50.0)))
        // purchasedWoodOverhead: volume Sch3 PO&P timber = 10000; cost = Sch3 Subtotal Actual Costs
        // PO&P column = 20000 (items 27/125); perUnit 2.0
        .andExpect(jsonPath("$.purchasedWoodOverhead.volume", is(10000)))
        .andExpect(jsonPath("$.purchasedWoodOverhead.cost", is(20000)))
        .andExpect(jsonPath("$.purchasedWoodOverhead.perUnit", is(2.0)))
        // subtotal: cost 500000+20000=520000; volume 10000; perUnit 52.0
        .andExpect(jsonPath("$.subtotal.volume", is(10000)))
        .andExpect(jsonPath("$.subtotal.cost", is(520000)))
        .andExpect(jsonPath("$.subtotal.perUnit", is(52.0)))
        // lessLogSales: item 26 volume 2000 / cost 100000; perUnit 50.0
        .andExpect(jsonPath("$.lessLogSales.volume", is(2000)))
        .andExpect(jsonPath("$.lessLogSales.cost", is(100000)))
        .andExpect(jsonPath("$.lessLogSales.perUnit", is(50.0)))
        // netPurchased: volume 10000-2000=8000; cost 520000-100000=420000; perUnit 52.5
        .andExpect(jsonPath("$.netPurchased.volume", is(8000)))
        .andExpect(jsonPath("$.netPurchased.cost", is(420000)))
        .andExpect(jsonPath("$.netPurchased.perUnit", is(52.5)))
        // totalCompanyLogging: volume Sch3 Crown = 12345; cost = 617250 (Sch1 144)
        //   + 100000 (Sch3 actual-costs crown) + ((20000 Sch1 silvActual − 5000 Sch3 silvAdmin crown)
        //   + 8450 Sch1 silvAccrued) = 740700; perUnit 740700/12345 = 60.0
        .andExpect(jsonPath("$.totalCompanyLogging.volume", is(12345)))
        .andExpect(jsonPath("$.totalCompanyLogging.cost", is(740700)))
        .andExpect(jsonPath("$.totalCompanyLogging.perUnit", is(60.0)))
        // totalAverage: volume 8000+12345=20345; cost 420000+740700=1160700
        .andExpect(jsonPath("$.totalAverage.volume", is(20345)))
        .andExpect(jsonPath("$.totalAverage.cost", is(1160700)));
  }

  @Test
  @DisplayName("517/2021 non-Draft — trackStatus S, editable false, stored values still shown")
  void nonDraftContext_notEditable_storedValuesShown() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "517")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(333000)))
        .andExpect(jsonPath("$.lessLogSales.volume", is(500)))
        .andExpect(jsonPath("$.lessLogSales.cost", is(25000)));
  }

  @Test
  @DisplayName("517/2021 empty Schedule 3 — computed-from-zero subtotals (legacy CoreUtil seeds at 0)")
  void emptySchedule3_computedFromZeroSubtotals() throws Exception {
    // 517 has an EMPTY category-'3' summary (no PO&P timber / Crown timber / actual-cost lines) and a
    // minimal Schedule 1 (one logging line, item 12 = 40000). Legacy CoreUtil subtotals seed at 0, so
    // the Schedule-3 PO&P actual cost is 0 (not null) — the "absent Schedule 3 → null" case (no
    // category-'3' summary at all) is covered by the Schedule2Service unit test.
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "517")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // No PO&P timber volume (no item 118) -> purchasedLogCost.volume + perUnit omitted.
        .andExpect(jsonPath("$.purchasedLogCost.volume").doesNotExist())
        .andExpect(jsonPath("$.purchasedLogCost.perUnit").doesNotExist())
        // Subtotal Actual Costs PO&P column of the empty Schedule 3 = 0 (seeds at zero).
        .andExpect(jsonPath("$.purchasedWoodOverhead.cost", is(0)))
        // No Crown timber volume (no item 119) -> totalCompanyLogging.volume omitted.
        .andExpect(jsonPath("$.totalCompanyLogging.volume").doesNotExist())
        // totalCompanyLogging.cost = Sch1 subtotal logging (item 12 = 40000) + 0 crown + null silv.
        .andExpect(jsonPath("$.totalCompanyLogging.cost", is(40000)))
        // subtotal cost = item 25 (333000) + 0 PO&P actual cost.
        .andExpect(jsonPath("$.subtotal.cost", is(333000)));
  }

  @Test
  @DisplayName("515/2021 valid active Draft, no Schedule 2 summary — 200 empty editable doc, NOT 404")
  void unsavedSchedule_returnsEmptyEditableDocument() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "515")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.revisionCount").doesNotExist())
        .andExpect(jsonPath("$.comments").doesNotExist())
        .andExpect(jsonPath("$.purchasedLogCost.cost").doesNotExist())
        .andExpect(jsonPath("$.lessLogSales.volume").doesNotExist())
        .andExpect(jsonPath("$.subtotal.cost").doesNotExist())
        // Block objects still serialize (as empty {}), but with no populated fields.
        .andExpect(jsonPath("$.totalAverage.volume").doesNotExist());
  }
}
