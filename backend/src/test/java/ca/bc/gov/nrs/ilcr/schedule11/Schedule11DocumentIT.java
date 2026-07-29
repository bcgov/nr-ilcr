package ca.bc.gov.nrs.ilcr.schedule11;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
 * Story 25.1 acceptance — {@code GET /api/v1/schedule11} 200 aggregate document (AC1/AC2/AC6/AC7,
 * slices S01/S10/S20; AD-5, AD-10, AD-12). Asserts the pinned wire contract via jsonPath against
 * the V20 seed's expected values (per-fixture block comments there). Security is OFF so the mock
 * {@code ILCR_SUBMITTER} principal applies — authz is proven in {@link Schedule11AuthorizationIT},
 * guards in {@link Schedule11ContextGuardIT}.
 *
 * <p>Jackson serializes non-null only, so "absent = null" — nulls are asserted with
 * {@code doesNotExist()} (document {@code revisionCount} is ALWAYS null: no
 * {@code ILCR_REPORT_SUMMARY} row exists for a list schedule; per-row nulls follow BR-08).
 */
@DisplayName("GET /api/v1/schedule11 — silviculture locations document (Story 25.1)")
class Schedule11DocumentIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule11";

    @Test
    @DisplayName("610/2021 Draft — full pinned document, BR-08 derivations, sort, becLabel concat (AC1)")
    void draftContext_returnsPinnedDocument() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "610")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.millId", is(610)))
                .andExpect(jsonPath("$.year", is(2021)))
                .andExpect(jsonPath("$.trackStatus", is("D")))
                .andExpect(jsonPath("$.editable", is(true)))
                // Document-level revisionCount is ALWAYS null for this list schedule (recorded
                // AR11 keying delta: concurrency keys per-row in 25.2).
                .andExpect(jsonPath("$.revisionCount").doesNotExist())
                // Sort: BASIC_SILVICULTURE_REPORT_ID ascending (legacy HQL order by).
                .andExpect(jsonPath("$.locations[*].locationId", contains(9101, 9102, 9103, 9104)))
                // 9101 — both costs present: totalCost 25000+10000; cpna 35000/120.5 scale 4 HALF_UP.
                .andExpect(jsonPath("$.locations[0].location", is("Kootenay Lake East")))
                .andExpect(jsonPath("$.locations[0].enhancedIndicator", is(false)))
                .andExpect(jsonPath("$.locations[0].biogeoclimaticCatalogueId", is(8801)))
                .andExpect(jsonPath("$.locations[0].becLabel", is("ICHdw1"))) // null PHASE -> ""
                .andExpect(jsonPath("$.locations[0].netArea", is(120.5)))
                .andExpect(jsonPath("$.locations[0].actualCost", is(25000)))
                .andExpect(jsonPath("$.locations[0].plannedCost", is(10000)))
                .andExpect(jsonPath("$.locations[0].totalCost", is(35000)))
                .andExpect(jsonPath("$.locations[0].costPerNetArea", is(290.4564)))
                .andExpect(jsonPath("$.locations[0].comments").doesNotExist())
                .andExpect(jsonPath("$.locations[0].revisionCount", is(0)))
                // 9102 — actual only: null+x=x (BR-08 null-tolerant addition); ENHANCED_IND 'Y'.
                .andExpect(jsonPath("$.locations[1].enhancedIndicator", is(true)))
                .andExpect(jsonPath("$.locations[1].becLabel", is("CWHvm"))) // null VARIANT+PHASE
                .andExpect(jsonPath("$.locations[1].actualCost", is(4500)))
                .andExpect(jsonPath("$.locations[1].plannedCost").doesNotExist())
                .andExpect(jsonPath("$.locations[1].totalCost", is(4500)))
                .andExpect(jsonPath("$.locations[1].costPerNetArea", is(135.1351)))
                .andExpect(jsonPath("$.locations[1].comments", is("Enhanced site")))
                .andExpect(jsonPath("$.locations[1].revisionCount", is(3)))
                // 9103 — planned only; 7000/50 = 140 exactly -> min scale 1 serves 140.0 (recorded
                // scale-4 deviation idiom, deferred-work.md).
                .andExpect(jsonPath("$.locations[2].plannedCost", is(7000)))
                .andExpect(jsonPath("$.locations[2].actualCost").doesNotExist())
                .andExpect(jsonPath("$.locations[2].totalCost", is(7000)))
                .andExpect(jsonPath("$.locations[2].costPerNetArea", is(140.0)))
                // 9104 — NO cost rows (the real-data dominant case): null costs, null cpna.
                .andExpect(jsonPath("$.locations[3].becLabel", is("ESSFwc4a"))) // full concat
                .andExpect(jsonPath("$.locations[3].actualCost").doesNotExist())
                .andExpect(jsonPath("$.locations[3].plannedCost").doesNotExist())
                .andExpect(jsonPath("$.locations[3].totalCost").doesNotExist())
                .andExpect(jsonPath("$.locations[3].costPerNetArea").doesNotExist())
                // Footer (BR-08): netArea 214.05 -> scale 1 HALF_UP 214.1 (proves footer rounding);
                // cpna 46500/214.1 -> 217.1882. The out-of-scope item-19 row (99999 on 9101) must
                // appear in NO figure — 46500, not 146499.
                .andExpect(jsonPath("$.totals.netArea", is(214.1)))
                .andExpect(jsonPath("$.totals.actualCost", is(29500)))
                .andExpect(jsonPath("$.totals.plannedCost", is(17000)))
                .andExpect(jsonPath("$.totals.totalCost", is(46500)))
                .andExpect(jsonPath("$.totals.costPerNetArea", is(217.1882)));
    }

    @Test
    @DisplayName("613/2021 zero locations — 200 empty document, NULL (not 0) totals (AC2)")
    void zeroLocations_returns200EmptyDocument() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "613")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations", is(empty())))
                .andExpect(jsonPath("$.trackStatus", is("D")))
                .andExpect(jsonPath("$.editable", is(true)))
                // Null-not-zero footer semantics (legacy sumBigDecimal* return null on no items).
                .andExpect(jsonPath("$.totals.netArea").doesNotExist())
                .andExpect(jsonPath("$.totals.actualCost").doesNotExist())
                .andExpect(jsonPath("$.totals.plannedCost").doesNotExist())
                .andExpect(jsonPath("$.totals.totalCost").doesNotExist())
                .andExpect(jsonPath("$.totals.costPerNetArea").doesNotExist());
    }

    @Test
    @DisplayName("611/2021 silviculture track S — editable:false for SUBMITTER (AC7/S20)")
    void submittedSilvicultureTrack_notEditable() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "611")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackStatus", is("S")))
                .andExpect(jsonPath("$.editable", is(false)));
    }

    @Test
    @DisplayName("612/2021 1-10 track Submitted, silviculture Draft — editable:true (AC6/S10 track independence)")
    void submitted1To10Track_doesNotAffectSchedule11Editability() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "612")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // trackStatus is MILL_SILVICULTUR_STATUS_CODE ('D'), NEVER the 1-10 code ('S').
                .andExpect(jsonPath("$.trackStatus", is("D")))
                .andExpect(jsonPath("$.editable", is(true)))
                .andExpect(jsonPath("$.locations[0].totalCost", is(1500)))
                .andExpect(jsonPath("$.locations[0].costPerNetArea", is(300.0)))
                .andExpect(jsonPath("$.totals.netArea", is(5.0)))
                .andExpect(jsonPath("$.totals.costPerNetArea", is(300.0)));
    }

    @Test
    @DisplayName("514/2021 status row with NULL silviculture code — trackStatus null, editable:false")
    void nullSilvicultureCode_servesNullTrackStatusNotEditable() throws Exception {
        // V2 seeds 514/2021 without MILL_SILVICULTUR_STATUS_CODE (reused READ-ONLY). Legacy shows
        // "Not Initiated" — display text is 25.3's concern; the API serves null + editable:false
        // (cannot be Draft).
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", "514")
                        .param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackStatus").doesNotExist())
                .andExpect(jsonPath("$.editable", is(false)))
                .andExpect(jsonPath("$.locations", is(empty())));
    }
}
