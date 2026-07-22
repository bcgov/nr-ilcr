package ca.bc.gov.nrs.ilcr.schedule8;

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
 * Acceptance test — Schedule 8 read (AD-5, AD-10, AD-12). GET /api/v1/schedule8 three-level document.
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}) so the mock {@code ILCR_SUBMITTER} principal
 * applies, isolating document assembly from authz (covered by {@link Schedule8AuthorizationIT}).
 * Asserts the pinned three-level wire contract, the addition/deduction split, the resolved code
 * labels, the server-computed roll-ups, and {@code editable} against the V11 seed.
 */
@DisplayName("GET /api/v1/schedule8 — Tree to Truck document (Schedule 8 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule8";

  @Test
  @DisplayName("570/2021 Draft — page → sample → additions/deductions, labels + computed rates")
  void draftContext_servesThreeLevelHierarchy() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "570")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(570)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.pages.length()", is(1)))
        // Page 8500 — id + revision + the code fields surfaced as BOTH code and resolved label (§3).
        .andExpect(jsonPath("$.pages[0].id", is(8500)))
        .andExpect(jsonPath("$.pages[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.pages[0].division", is("North Div")))
        .andExpect(jsonPath("$.pages[0].license", is("L570")))
        .andExpect(jsonPath("$.pages[0].contact", is("Pat Contact")))
        .andExpect(jsonPath("$.pages[0].supportCentre", is("SC1")))
        .andExpect(jsonPath("$.pages[0].supportCentreLabel", is("Support Centre One")))
        .andExpect(jsonPath("$.pages[0].region", is("R1")))
        .andExpect(jsonPath("$.pages[0].regionLabel", is("Region One")))
        .andExpect(jsonPath("$.pages[0].becZoneLabel", is("BEC Zone One")))
        .andExpect(jsonPath("$.pages[0].tsaNumberLabel", is("Test TSA Five")))
        .andExpect(jsonPath("$.pages[0].supplyBlockLabel", is("Supply Block B")))
        // 570's page has no TFL code — the code and its label are omitted (null).
        .andExpect(jsonPath("$.pages[0].tflNumber").doesNotExist())
        .andExpect(jsonPath("$.pages[0].tflNumberLabel").doesNotExist())
        .andExpect(jsonPath("$.pages[0].sampleCount", is(1)))
        .andExpect(jsonPath("$.pages[0].samples.length()", is(1)))
        // Sample 8600 — computed percentTotal (60+40) and actualHarvested (700+300) server-side.
        .andExpect(jsonPath("$.pages[0].samples[0].id", is(8600)))
        .andExpect(jsonPath("$.pages[0].samples[0].contractId", is("C1")))
        .andExpect(jsonPath("$.pages[0].samples[0].percentTotal", is(100)))
        .andExpect(jsonPath("$.pages[0].samples[0].actualHarvested", is(1000)))
        .andExpect(jsonPath("$.pages[0].samples[0].skidTypeCode", is("ST1")))
        .andExpect(jsonPath("$.pages[0].samples[0].skidTypeDescription", is("Skid Type One")))
        .andExpect(jsonPath("$.pages[0].samples[0].uphillDirection", is(true)))    // "Y"
        .andExpect(jsonPath("$.pages[0].samples[0].waterDumpDestination", is(false))) // "N"
        // originalRate 25.50 + additionsTotal 5.00 − deductionsTotal 2.00 = finalRate 28.50.
        .andExpect(jsonPath("$.pages[0].samples[0].originalRate", is(25.5)))
        .andExpect(jsonPath("$.pages[0].samples[0].additionsTotal", is(5)))
        .andExpect(jsonPath("$.pages[0].samples[0].deductionsTotal", is(2)))
        .andExpect(jsonPath("$.pages[0].samples[0].finalRate", is(28.5)))
        .andExpect(jsonPath("$.pages[0].samples[0].additionCount", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].deductionCount", is(1)))
        // Addition (item 82, subcat '1') and deduction (item 101, subcat '3') split by §Decision 1.
        .andExpect(jsonPath("$.pages[0].samples[0].additions.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].additions[0].costItemCode", is(82)))
        .andExpect(jsonPath("$.pages[0].samples[0].additions[0].itemDescription", is("Add A")))
        .andExpect(jsonPath("$.pages[0].samples[0].additions[0].costingRate", is(5)))
        .andExpect(jsonPath("$.pages[0].samples[0].additions[0].costTypeCode", is("CT1")))
        .andExpect(jsonPath("$.pages[0].samples[0].additions[0].costTypeDescription",
            is("Cost Type One")))
        .andExpect(jsonPath("$.pages[0].samples[0].deductions.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].deductions[0].costItemCode", is(101)))
        .andExpect(jsonPath("$.pages[0].samples[0].deductions[0].costTypeDescription",
            is("Cost Type Two")));
  }

  @Test
  @DisplayName("571/2021 non-Draft — trackStatus S, editable false, page listed, no rate rows")
  void nonDraftContext_notEditable_pageStillListed() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "571")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.pages.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].license", is("L571")))
        .andExpect(jsonPath("$.pages[0].tflNumber", is("48")))
        .andExpect(jsonPath("$.pages[0].tflNumberLabel", is("Tree Farm Licence 48")))
        .andExpect(jsonPath("$.pages[0].sampleCount", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].percentTotal", is(100)))
        .andExpect(jsonPath("$.pages[0].samples[0].actualHarvested", is(500)))
        // No rate rows — finalRate collapses to the original rate; both totals 0.
        .andExpect(jsonPath("$.pages[0].samples[0].additionCount", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[0].deductionCount", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[0].additionsTotal", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[0].originalRate", is(10)))
        .andExpect(jsonPath("$.pages[0].samples[0].finalRate", is(10)));
  }

  @Test
  @DisplayName("572/2021 valid active Draft, no pages — 200 empty list, NOT 404")
  void noPages_returnsEmptyList() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "572")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.pages.length()", is(0)));
  }
}
