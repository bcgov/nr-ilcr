package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
 * Story 12.1 acceptance — {@code GET /api/v1/schedule7a} 200 aggregate document (AC1-AC5/AC7,
 * slices S01/S18; AD-5, AD-10, AD-12). Asserts the pinned wire contract via jsonPath against the
 * V27 seed's hand-computed values. Security is OFF (mock {@code ILCR_SUBMITTER}); authz is proven
 * in {@link Schedule7aAuthorizationIT}, guards in {@link Schedule7aContextGuardIT}.
 */
@DisplayName("GET /api/v1/schedule7a — bridge document (Story 12.1)")
class Schedule7aDocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7a";

  @Test
  @DisplayName("514/2021 Draft — pinned document, server-computed totals, code lists (AC1-AC4)")
  void draftContext_returnsPinnedDocument() throws Exception {
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
        .andExpect(jsonPath("$.bridges", hasSize(3)))
        .andExpect(jsonPath("$.bridges[*].bridgeReportId", contains(7601, 7602, 7603)))
        .andExpect(jsonPath("$.bridges[*].rowCounter", contains(1, 2, 3)))
        // 7601 — complete: totals hand-computed, builtDate yyyy-MM, all attributes.
        .andExpect(jsonPath("$.bridges[0].locationName", is("North Fork Bridge")))
        .andExpect(jsonPath("$.bridges[0].builtDate", is("2020-06")))
        .andExpect(jsonPath("$.bridges[0].constructionTypeCode", is("N")))
        .andExpect(jsonPath("$.bridges[0].superstructureTypeCode", is("STL")))
        .andExpect(jsonPath("$.bridges[0].deckTypeCode", is("WD")))
        .andExpect(jsonPath("$.bridges[0].abutmentTypeCode", is("CONC")))
        .andExpect(jsonPath("$.bridges[0].loadRatingCode", is("L100")))
        .andExpect(jsonPath("$.bridges[0].lifeSpan", is(50)))
        .andExpect(jsonPath("$.bridges[0].abutmentHeight", is(5.0)))
        .andExpect(jsonPath("$.bridges[0].length", is(20.0)))
        .andExpect(jsonPath("$.bridges[0].width", is(4.0)))
        .andExpect(jsonPath("$.bridges[0].distance", is(12)))
        .andExpect(jsonPath("$.bridges[0].sitePlanCost", is(1000)))
        .andExpect(jsonPath("$.bridges[0].totalMaterial", is(8000))) // 5000 + 3000
        .andExpect(jsonPath("$.bridges[0].totalDeliver", is(800))) // 500 + 300
        .andExpect(jsonPath("$.bridges[0].totalInstall", is(1200))) // 800 + 400
        .andExpect(jsonPath("$.bridges[0].grandTotal", is(12000))) // 1000+8000+800+1200+700+200+100
        .andExpect(jsonPath("$.bridges[0].comments", is("First span")))
        .andExpect(jsonPath("$.bridges[0].revisionCount", is(0)))
        // 7602 — complete, used-construction; grandTotal 8800; null comments omitted.
        .andExpect(jsonPath("$.bridges[1].locationName", is("South Creek Bridge")))
        .andExpect(jsonPath("$.bridges[1].builtDate", is("2019-11")))
        .andExpect(jsonPath("$.bridges[1].constructionTypeCode", is("U")))
        .andExpect(jsonPath("$.bridges[1].grandTotal", is(8800)))
        .andExpect(jsonPath("$.bridges[1].comments").doesNotExist())
        // 7603 — incomplete (afterInstall + other absent → omitted); grandTotal 5400.
        .andExpect(jsonPath("$.bridges[2].afterInstallCost").doesNotExist())
        .andExpect(jsonPath("$.bridges[2].otherCost").doesNotExist())
        .andExpect(jsonPath("$.bridges[2].totalMaterial", is(3800)))
        .andExpect(jsonPath("$.bridges[2].grandTotal", is(5400)))
        // Code lists (ordered by code): dropdown options served with the document (AC4). The counts
        // are also the year-scoping assertion — V27 seeds an EXPIRED construction type ('X') and
        // load rating ('LX'), so an unfiltered query would serve 3 of each here, not 2.
        .andExpect(jsonPath("$.codeLists.constructionTypes", hasSize(2)))
        .andExpect(jsonPath("$.codeLists.constructionTypes[0].code", is("N")))
        .andExpect(jsonPath("$.codeLists.constructionTypes[0].description", is("New")))
        .andExpect(jsonPath("$.codeLists.superstructureTypes", hasSize(3)))
        .andExpect(jsonPath("$.codeLists.deckTypes", hasSize(2)))
        .andExpect(jsonPath("$.codeLists.abutmentTypes", hasSize(3)))
        .andExpect(jsonPath("$.codeLists.loadRatings", hasSize(2)))
        // GET never carries a success message (Jackson non_null omits it).
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("517/2021 Submitted — editable:false, bridges still served (AC7/S18 read side)")
  void submittedContext_notEditable() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.bridges", hasSize(1)))
        .andExpect(jsonPath("$.bridges[0].locationName", is("Harbour Overpass")))
        .andExpect(jsonPath("$.bridges[0].grandTotal", is(6125)));
  }

  @Test
  @DisplayName(
      "code lists exclude a code that expired before the reporting year (legacy year scope)")
  void codeLists_excludeCodesExpiredBeforeTheReportingYear() throws Exception {
    // Legacy filtered every list through LookupCache.getCacheList(year) — effective_date <= Jan 1
    // of
    // the year <= expiry_date — so a code retired in 2015 was never offered on a 2021 form.
    // Asserting
    // the specific codes (not just a count) is what pins WHICH rows the filter removed.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.codeLists.constructionTypes[*].code",
                org.hamcrest.Matchers.containsInAnyOrder("N", "U")))
        .andExpect(
            jsonPath(
                "$.codeLists.constructionTypes[*].code",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("X"))))
        .andExpect(
            jsonPath(
                "$.codeLists.loadRatings[*].code",
                org.hamcrest.Matchers.containsInAnyOrder("L100", "L75")))
        .andExpect(
            jsonPath(
                "$.codeLists.loadRatings[*].code",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("LX"))))
        // A row with NULL bounds means "no bound" and must SURVIVE the filter. An unguarded
        // comparison against NULL is false in SQL, which would silently drop it.
        .andExpect(
            jsonPath("$.codeLists.abutmentTypes[*].code", org.hamcrest.Matchers.hasItem("OPEN")));
  }
}
