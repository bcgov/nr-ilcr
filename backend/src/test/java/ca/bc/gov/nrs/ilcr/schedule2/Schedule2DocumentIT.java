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
        .andExpect(jsonPath("$.revisionCount", is(0)))
        .andExpect(jsonPath("$.comments", is("Seed Schedule 2 comment for 514/2021")))
        // purchasedLogCost: cost 25 = 500000; volume carried Sch3 118 = 10000; perUnit 50.0
        .andExpect(jsonPath("$.purchasedLogCost.volume", is(10000)))
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(500000)))
        .andExpect(jsonPath("$.purchasedLogCost.perUnit", is(50.0)))
        // purchasedWoodOverhead: volume 118 = 10000; cost 135 = 20000; perUnit 2.0
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
        // totalCompanyLogging: volume Sch3 Crown 119 = 12345; cost Sch1 144 = 617250; perUnit 50.0
        .andExpect(jsonPath("$.totalCompanyLogging.volume", is(12345)))
        .andExpect(jsonPath("$.totalCompanyLogging.cost", is(617250)))
        .andExpect(jsonPath("$.totalCompanyLogging.perUnit", is(50.0)))
        // totalAverage: volume 8000+12345=20345; cost 420000+617250=1037250
        .andExpect(jsonPath("$.totalAverage.volume", is(20345)))
        .andExpect(jsonPath("$.totalAverage.cost", is(1037250)));
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
  @DisplayName("517/2021 no Schedule 3 data — carried/derived dependent figures omitted (null)")
  void missingSchedule3Data_dependentFiguresOmitted() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "517")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // No Sch3 118 -> purchasedLogCost.volume and purchasedWoodOverhead.* omitted.
        .andExpect(jsonPath("$.purchasedLogCost.volume").doesNotExist())
        .andExpect(jsonPath("$.purchasedLogCost.perUnit").doesNotExist())
        .andExpect(jsonPath("$.purchasedWoodOverhead.cost").doesNotExist())
        // No Crown / Sch1 144 -> totalCompanyLogging.volume and .cost omitted.
        .andExpect(jsonPath("$.totalCompanyLogging.volume").doesNotExist())
        .andExpect(jsonPath("$.totalCompanyLogging.cost").doesNotExist())
        // stored line item still present (item 25 cost).
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
