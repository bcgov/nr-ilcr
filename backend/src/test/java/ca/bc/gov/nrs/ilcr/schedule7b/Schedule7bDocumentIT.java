package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Story 13.1 acceptance — {@code GET /api/v1/schedule7b} 200 aggregate document (AC1-AC5/AC8; AD-5,
 * AD-10, AD-12). Asserts the pinned wire contract via jsonPath against the V35 seed's hand-computed
 * values. Security is OFF (mock {@code ILCR_SUBMITTER}); authz is proven in
 * {@link Schedule7bAuthorizationIT}, guards in {@link Schedule7bContextGuardIT}.
 */
@DisplayName("GET /api/v1/schedule7b — culvert document (Story 13.1)")
class Schedule7bDocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7b";

  @Test
  @DisplayName("514/2021 Draft — pinned document with server-computed totals (AC1-AC3)")
  void draftContext_returnsPinnedDocument() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.culverts", hasSize(3)))
        .andExpect(jsonPath("$.culverts[*].culvertReportId", contains(7801, 7802, 7803)))
        .andExpect(jsonPath("$.culverts[*].rowCounter", contains(1, 2, 3)))
        // 7801 — complete Round: total 4000 + 1500 = 5500.
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("R")))
        .andExpect(jsonPath("$.culverts[0].spanSize", is(1200)))
        .andExpect(jsonPath("$.culverts[0].riseSize", is(900)))
        .andExpect(jsonPath("$.culverts[0].length", is(12.5)))
        .andExpect(jsonPath("$.culverts[0].culvertPieceCount", is(3)))
        .andExpect(jsonPath("$.culverts[0].materialCost", is(4000)))
        .andExpect(jsonPath("$.culverts[0].installCost", is(1500)))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(5500)))
        .andExpect(jsonPath("$.culverts[0].comments", is("Main haul road")))
        .andExpect(jsonPath("$.culverts[0].revisionCount", is(0)))
        // 7802 — complete Others: total 2500 + 700 = 3200; no span, which is fine for a non-Round type.
        .andExpect(jsonPath("$.culverts[1].culvertTypeCode", is("O")))
        .andExpect(jsonPath("$.culverts[1].spanSize").doesNotExist())
        .andExpect(jsonPath("$.culverts[1].totalCost", is(3200)))
        // 7803 — install cost row present but NULL: total is the material cost alone, never 900 + 0.
        .andExpect(jsonPath("$.culverts[2].materialCost", is(900)))
        .andExpect(jsonPath("$.culverts[2].installCost").doesNotExist())
        .andExpect(jsonPath("$.culverts[2].totalCost", is(900)))
        // Optional values absent on 7803 are omitted, not zeroed (Jackson non_null).
        .andExpect(jsonPath("$.culverts[2].spanSize").doesNotExist())
        .andExpect(jsonPath("$.culverts[2].riseSize").doesNotExist())
        .andExpect(jsonPath("$.culverts[2].length").doesNotExist())
        // A GET never carries a mutation message.
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("AC4: the Type list is filtered to the codes effective for the reporting year")
  void codeList_isFilteredToTheReportingYear() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // The eight legacy codes, plus the NULL-bounds row and the mid-2021-expiring row, in code
        // order. XOLD (retired 2015) and MIDYR (not effective until June 2021) are both excluded.
        .andExpect(jsonPath("$.codeLists.culvertTypes[*].code",
            contains("A", "ABL", "EXPJUN", "HE", "O", "OPEN", "PA", "R", "VE", "WBL")))
        .andExpect(jsonPath("$.codeLists.culvertTypes[*].code", not(Matchers.hasItem("XOLD"))))
        .andExpect(jsonPath("$.codeLists.culvertTypes[7].code", is("R")))
        .andExpect(jsonPath("$.codeLists.culvertTypes[7].description", is("Round")));
  }

  @Test
  @DisplayName("AC4: the as-of instant is JANUARY 1 — a code effective mid-year is not yet offered")
  void codeList_excludesCodeEffectiveLaterInTheYear() throws Exception {
    // MIDYR is effective 2021-06-01. Legacy evaluated the list at January 1 of the reporting year
    // (CoreUtil.getDate(int) -> LookupCache.getCacheList(year)), so it is NOT offered for RY2021 —
    // it first appears in RY2022. This is the fixture that makes effectiveOn() falsifiable: move the
    // as-of instant to any later day in 2021 and this assertion fails.
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeLists.culvertTypes[*].code", not(Matchers.hasItem("MIDYR"))));
  }

  @Test
  @DisplayName("AC4: the as-of instant is JANUARY 1 — a code retired mid-year IS still offered")
  void codeList_includesCodeRetiredLaterInTheYear() throws Exception {
    // EXPJUN was in force on 2021-01-01 and retired that June. Because legacy evaluated at Jan 1 it
    // remains on the RY2021 form; this pins that the filter uses Jan 1 rather than "today" or
    // year-end, from the opposite direction to the MIDYR case above.
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeLists.culvertTypes[*].code", Matchers.hasItem("EXPJUN")));
  }

  @Test
  @DisplayName("The served list is ordered by CULVERT_REPORT_ID, not by insertion order")
  void culvertsAreOrderedById() throws Exception {
    // The V20260811 seed inserts 514/2021's culverts as 7803, 7801, 7802 on purpose, so an unordered
    // heap read would surface that sequence. The 1-based rowCounter that Check Status quotes back to
    // the reporter rides on this ordering.
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[*].culvertReportId", contains(7801, 7802, 7803)))
        .andExpect(jsonPath("$.culverts[*].rowCounter", contains(1, 2, 3)));
  }

  @Test
  @DisplayName("A read is scoped to the requested REPORT_YEAR — mill 680 has 2020 and 2021 culverts")
  void readIsScopedToTheReportingYear() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "680").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertReportId", is(7862)))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(700)));

    mockMvc.perform(get(ENDPOINT).param("millId", "680").param("year", "2020")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertReportId", is(7861)))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(300)));
  }

  @Test
  @DisplayName("AC4: a code with NULL effective/expiry dates is still offered (the NVL guard)")
  void codeList_includesNullBoundedCode() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeLists.culvertTypes[*].code", Matchers.hasItem("OPEN")));
  }

  @Test
  @DisplayName("AC8: 517/2021 Submitted — editable:false with the culverts still displayed")
  void nonDraftContext_servesReadOnly() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "517").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertReportId", is(7851)))
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("PA")))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(2100)))
        // Jackson non_null omits the key entirely on a GET rather than serializing it as null.
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("AC3: a whole length serializes at scale 1 rather than as an integer")
  void wholeLengthKeepsOneDecimal() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // 7802's seeded LENGTH is 8.0 — Oracle reads it back at scale 0, so this pins the
        // service-side normalization rather than the column definition.
        .andExpect(jsonPath("$.culverts[1].length", is(8.0)));
  }
}
