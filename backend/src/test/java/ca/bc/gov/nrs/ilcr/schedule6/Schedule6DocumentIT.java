package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 6 read (AD-5, AD-10, AD-12). GET /api/v1/schedule6 road-record list.
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}) so the mock {@code ILCR_SUBMITTER}
 * principal applies, isolating document assembly from authz (covered by {@link
 * Schedule6AuthorizationIT}). Asserts the pinned wire contract, the server-derived RMG / $/m3 /
 * running totals, and the S18 lone-comment empty-list state against the V31 seed (whose DECOY rows
 * also make these assertions pin the query's year / category-'6' / item-69 filters).
 */
@DisplayName("GET /api/v1/schedule6 — road-record list (Schedule 6 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule6DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule6";

  @Test
  @DisplayName("514/2021 Draft — two records (TSA+SupplyBlock, TFL), derived RMG/$per-m3/totals")
  void draftContext_listsRecordsWithDerivedFigures() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.generalComments", is("General road maintenance comment for 2021.")))
        .andExpect(jsonPath("$.roadRecords.length()", is(2)))
        // Record 8301 — TSA "01" + Supply Block "01B" -> RMG "15"; vol 1000 / cost 50000 -> $/m3
        // 50.00.
        .andExpect(jsonPath("$.roadRecords[0].recordId", is(8301)))
        .andExpect(jsonPath("$.roadRecords[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.roadRecords[0].areaType", is("01")))
        .andExpect(jsonPath("$.roadRecords[0].supplyBlock", is("01B")))
        .andExpect(jsonPath("$.roadRecords[0].tflNumber").doesNotExist())
        .andExpect(jsonPath("$.roadRecords[0].rmg", is("15")))
        .andExpect(jsonPath("$.roadRecords[0].volume", is(1000)))
        .andExpect(jsonPath("$.roadRecords[0].cost", is(50000)))
        .andExpect(jsonPath("$.roadRecords[0].costPerVolume", is(50.0)))
        .andExpect(jsonPath("$.roadRecords[0].comments", is("Arrow FSR resurfacing")))
        // Record 8302 — TFL "18" -> RMG "4"; vol 400 / cost 30000 -> $/m3 75.00. supplyBlock
        // omitted.
        .andExpect(jsonPath("$.roadRecords[1].recordId", is(8302)))
        .andExpect(jsonPath("$.roadRecords[1].revisionCount", is(0)))
        .andExpect(jsonPath("$.roadRecords[1].areaType", is("TFL")))
        .andExpect(jsonPath("$.roadRecords[1].tflNumber", is("18")))
        .andExpect(jsonPath("$.roadRecords[1].supplyBlock").doesNotExist())
        .andExpect(jsonPath("$.roadRecords[1].rmg", is("4")))
        .andExpect(jsonPath("$.roadRecords[1].volume", is(400)))
        .andExpect(jsonPath("$.roadRecords[1].cost", is(30000)))
        .andExpect(jsonPath("$.roadRecords[1].costPerVolume", is(75.0)))
        .andExpect(jsonPath("$.roadRecords[1].comments", is("TFL 18 spur road")))
        // Running totals (BR-07): 1000+400 = 1400; 50000+30000 = 80000; 80000/1400 = 57.14 (scale
        // 2).
        .andExpect(jsonPath("$.totalVolume", is(1400)))
        .andExpect(jsonPath("$.totalCost", is(80000)))
        .andExpect(jsonPath("$.totalCostPerVolume", is(57.14)))
        // GET carries no success message (Jackson non_null omits it).
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("517/2021 non-Draft — trackStatus S, editable false, record still listed")
  void nonDraftContext_notEditable_recordStillListed() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.roadRecords.length()", is(1)))
        .andExpect(jsonPath("$.roadRecords[0].recordId", is(8303)))
        .andExpect(jsonPath("$.roadRecords[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.roadRecords[0].areaType", is("03")))
        .andExpect(jsonPath("$.roadRecords[0].supplyBlock", is("03B")))
        .andExpect(jsonPath("$.roadRecords[0].tflNumber").doesNotExist())
        .andExpect(jsonPath("$.roadRecords[0].rmg", is("1")))
        .andExpect(jsonPath("$.roadRecords[0].volume", is(2000)))
        .andExpect(jsonPath("$.roadRecords[0].cost", is(40000)))
        .andExpect(jsonPath("$.roadRecords[0].costPerVolume", is(20.0)))
        .andExpect(jsonPath("$.roadRecords[0].comments", is("Bulkley haul road")))
        .andExpect(jsonPath("$.generalComments", is("Submitted road comment.")))
        .andExpect(jsonPath("$.totalVolume", is(2000)))
        .andExpect(jsonPath("$.totalCost", is(40000)))
        .andExpect(jsonPath("$.totalCostPerVolume", is(20.0)));
  }

  @Test
  @DisplayName(
      "660/2021 lone comment (S18) — roadRecords [], zero totals, generalComments populated")
  void loneComment_returnsEmptyListWithGeneralComment() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "660")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.roadRecords.length()", is(0)))
        .andExpect(
            jsonPath("$.generalComments", is("Only a general comment, no road records yet.")))
        .andExpect(jsonPath("$.totalVolume", is(0)))
        .andExpect(jsonPath("$.totalCost", is(0)))
        // $/m3 is undefined (0/0) -> null -> omitted, mirroring the per-record zero-volume rule.
        .andExpect(jsonPath("$.totalCostPerVolume").doesNotExist());
  }
}
