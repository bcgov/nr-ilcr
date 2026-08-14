package ca.bc.gov.nrs.ilcr.schedule10;

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
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 10 read (AD-5, AD-10, AD-12). {@code GET /api/v1/schedule10}.
 *
 * <p>Security OFF so the mock {@code ILCR_SUBMITTER} principal applies, isolating document assembly
 * from authorization (covered by {@link Schedule10AuthorizationIT}).
 *
 * <p>Asserts the pinned wire contract against the V20260817 seed with EXACT numbers, not shapes. The
 * seed's decoy rows make these assertions also pin the query's mill / year / category-{@code '10'}
 * filters.
 */
@DisplayName("GET /api/v1/schedule10 — construction pages (Schedule 10 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule10";

  @Test
  @DisplayName("710/2021 Draft — two pages, nested details, derived Road Group and counts")
  void draftContext_servesPinnedDocument() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(710)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.pages", hasSize(2)))

        // --- Page 8900: TSA "01" + TSB "01A" -> startsWith("01") -> Road Group "11" ---
        .andExpect(jsonPath("$.pages[0].pageId", is(8900)))
        .andExpect(jsonPath("$.pages[0].pageNumber", is(1)))
        .andExpect(jsonPath("$.pages[0].roadGroup", is("11")))
        .andExpect(jsonPath("$.pages[0].tsaNumber", is("01")))
        .andExpect(jsonPath("$.pages[0].tsbNumberCode", is("01A")))
        .andExpect(jsonPath("$.pages[0].tflNumberCode").doesNotExist())
        .andExpect(jsonPath("$.pages[0].forestRegionCode", is("RNI")))
        .andExpect(jsonPath("$.pages[0].divisionName", is("North Division")))
        .andExpect(jsonPath("$.pages[0].constructionPeriod", is("2021-06")))
        .andExpect(jsonPath("$.pages[0].roadDetailCount", is(2)))
        // The legacy label, byte-for-byte — note NO space after "TFL:" and "-" for the absent TFL.
        .andExpect(jsonPath("$.pages[0].pageLabel",
            is("Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-")))
        .andExpect(jsonPath("$.pages[0].roadDetails", hasSize(2)))

        // --- Page 8901: TSA "16" + TSB "16G" -> regex [G-Pg-p] -> Road Group "6" ---
        .andExpect(jsonPath("$.pages[1].pageId", is(8901)))
        .andExpect(jsonPath("$.pages[1].pageNumber", is(2)))
        .andExpect(jsonPath("$.pages[1].roadGroup", is("6")))
        .andExpect(jsonPath("$.pages[1].roadDetailCount", is(1)));
  }

  @Test
  @DisplayName("cost lines reassemble into the sub-grade and stabilizing substructures (BR-08)")
  void reassemblesCostSubstructures() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Detail 8910 is the only row in the whole delivery-shaped fixture set WITH cost lines.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].roadDetailId", is(8910)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].rowNumber", is(1)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].roadDetailLabel", is("Road #1, Mainline A")))

        // Sub-grade: item 20 actual, item 3 TtT, item 5 other transfer (subcategory 3).
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.actualCost", is(150000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.ttTransfer", is(-5000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.otherTransfer", is(2000)))
        // The six deductions, which span THREE cost subcategories.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessBridges", is(1000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessCulverts", is(2000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessLandings", is(3000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessOverland", is(4000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessOtherEng", is(5000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.lessEndHaul", is(6000)))
        // Derived, server-side only. NOTE the scale difference, which is legacy-faithful and
        // deliberate: RAW costs come straight from COST NUMBER(15) as whole dollars (150000), while
        // every DERIVED total passes through CoreUtil.roundBigDecimal (:457-459) = setScale(2,
        // HALF_UP) and therefore carries two decimal places (147000.0). Asserting these as integers
        // would be asserting a behaviour legacy does not have.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.totalCosts", is(147000.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.totalDeductions", is(21000.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.total", is(126000.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.costPerLength", is(10080.0)))

        // Stabilizing: item 22 actual, item 10 TtT, item 9 other transfer (subcategory 4).
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.actualCost", is(40000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.total", is(40000.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.costPerLength", is(13333.33)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.ballastMethodCode", is("C")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.ballastMaterialCode", is("GR")))

        // Material composition total is derived and, uniquely, never null.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.totalPct", is(100)));
  }

  @Test
  @DisplayName("a detail with no cost lines omits the totals entirely — null is not zero")
  void noCostLines_omitsTotalsRatherThanServingZero() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Detail 8911 is the shape real delivery data actually has: no cost lines at all.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].roadDetailId", is(8911)))
        .andExpect(jsonPath("$.pages[0].roadDetails[1].rowNumber", is(2)))
        // Every cost and every derived total must be ABSENT, not 0.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.actualCost").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.totalCosts").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.totalDeductions").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.total").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.costPerLength").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].stabilizing.total").doesNotExist())
        // RSMR class is nullable in delivery and absent on most real rows.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].relSoilMoistRgmClsCode").doesNotExist())
        // But materialComposition.totalPct is the documented exception: always present, here 0.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].materialComposition.totalPct", is(0)));
  }

  @Test
  @DisplayName("TFL-located page derives from the TFL table (BR-05 mutual exclusion)")
  void tflLocatedPage_derivesFromTflTable() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "711").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages", hasSize(1)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8902)))
        .andExpect(jsonPath("$.pages[0].tflNumberCode", is("08")))
        // TFL "08" -> "10" in the Schedule 10 table. Schedule 6 maps it to "7" — a wrong reuse
        // would surface here.
        .andExpect(jsonPath("$.pages[0].roadGroup", is("10")))
        .andExpect(jsonPath("$.pages[0].tsaNumber").doesNotExist())
        .andExpect(jsonPath("$.pages[0].tsbNumberCode").doesNotExist())
        // Legacy does NOT null-guard TSA in the label, so a TFL page renders the literal "null".
        // Reproduced verbatim (deviation (l)) — this asserts the defect on purpose.
        .andExpect(jsonPath("$.pages[0].pageLabel",
            is("Page 1, Period: 2021-05, TSA: null, SB: -, TFL:08")));
  }

  @Test
  @DisplayName("unmapped combinations serve a blank Road Group without error (S12)")
  void unmappedCombination_servesBlankRoadGroupWithoutError() throws Exception {
    // Mill 712: page 8903 has TSA "99" (absent from the switch -> default -> null) and page 8904
    // has TSA "16" + TSB "16Z" (matches a case, but no inner branch -> "" ). Both must serve blank.
    mockMvc.perform(get(ENDPOINT).param("millId", "712").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages", hasSize(2)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8903)))
        .andExpect(jsonPath("$.pages[0].roadGroup").doesNotExist())
        .andExpect(jsonPath("$.pages[1].pageId", is(8904)))
        .andExpect(jsonPath("$.pages[1].roadGroup").doesNotExist())
        // The pages still list normally — a blank Road Group is not an error state.
        .andExpect(jsonPath("$.pages[0].roadDetailCount", is(1)));

    // Mill 713: an unmapped TFL takes the third legacy path (default -> null).
    mockMvc.perform(get(ENDPOINT).param("millId", "713").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].tflNumberCode", is("77")))
        .andExpect(jsonPath("$.pages[0].roadGroup").doesNotExist());
  }

  @Test
  @DisplayName("a page with no road details serves count 0 and an empty list (CNT-001)")
  void pageWithNoDetails_servesZeroCount() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "714").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages", hasSize(1)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8906)))
        // 0 is SERVED, not omitted — the legacy link reads "Enter Road Data (0)".
        .andExpect(jsonPath("$.pages[0].roadDetailCount", is(0)))
        .andExpect(jsonPath("$.pages[0].roadDetails", hasSize(0)));
  }

  @Test
  @DisplayName("a valid active context with zero pages is 200 empty, not 404 (deviation (a))")
  void noPages_isTwoHundredEmpty() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "715").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.millId", is(715)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.pages", hasSize(0)))
        // Code lists are still served so the frontend can render an empty add-form.
        .andExpect(jsonPath("$.codeLists.roadLifetimes", hasSize(2)));
  }

  @Test
  @DisplayName("a non-Draft track lists its data with editable:false (S31 / BR-02)")
  void nonDraftTrack_listsDataButIsNotEditable() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "716").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        // The caller holds EDIT_SCHEDULE, but the track is not Draft — the server decides.
        .andExpect(jsonPath("$.editable", is(false)))
        // Data is still fully served; only the write authority differs.
        .andExpect(jsonPath("$.pages", hasSize(1)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8907)))
        .andExpect(jsonPath("$.pages[0].roadDetailCount", is(1)));
  }

  @Test
  @DisplayName("code lists are year-filtered and exclude the three LD-removed lists")
  void codeLists_areYearFilteredAndOmitRemovedFields() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // 'ROLD' expired in 2010, so the 2021 filter must exclude it; RNI and RNO remain, and V22's
        // pre-existing 'R1' row was widened to 2000-2099 by this migration.
        .andExpect(jsonPath("$.codeLists.forestRegions[?(@.code == 'ROLD')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.forestRegions[?(@.code == 'RNI')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.ballastMethods", hasSize(3)))
        .andExpect(jsonPath("$.codeLists.ballastMaterials", hasSize(2)))
        .andExpect(jsonPath("$.codeLists.rsmrClasses", hasSize(2)))
        // LD-1/LD-2 removed both of these lists along with their fields.
        .andExpect(jsonPath("$.codeLists.asmCodes").doesNotExist())
        .andExpect(jsonPath("$.codeLists.soilMoistureCodes").doesNotExist());
  }

  @Test
  @DisplayName("BEC is served structurally, gated by the surviving BR-06 xref (deviation (e))")
  void becIsStructuralAndXrefGated() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Structural components plus the legacy concatenated label.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].becClassification.becZoneCode", is("ICH")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].becClassification.subzone", is("dw")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].becClassification.variant", is("1")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].becClassification.phase").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].becClassification.label", is("ICHdw1")))
        // 8803 (ESSFwc4a) exists in BIOGEOCLIMATIC_CATALOGUE but is NOT in the xref, so the gate
        // must exclude it. Serving the unfiltered catalogue would be an unflagged behaviour change.
        .andExpect(jsonPath(
            "$.codeLists.becClassifications[?(@.biogeoclimaticCatalogueId == 8803)]", hasSize(0)))
        .andExpect(jsonPath(
            "$.codeLists.becClassifications[?(@.biogeoclimaticCatalogueId == 8801)]", hasSize(1)));
  }

  @Test
  @DisplayName("the three LD-removed fields never appear in a response")
  void removedFieldsAreAbsent() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "710").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].asmCode").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].soilMoistureCode").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].boulderAreaPct").doesNotExist())
        .andExpect(jsonPath(
            "$.pages[0].roadDetails[0].materialComposition.boulderAreaPct").doesNotExist());
  }

  @Test
  @DisplayName("another mill's pages never leak into this document")
  void doesNotLeakOtherMillsPages() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "714").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Mill 714 owns exactly one page; mills 710-713 and 716 own others in the same year.
        .andExpect(jsonPath("$.pages", hasSize(1)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8906)));
  }
}
