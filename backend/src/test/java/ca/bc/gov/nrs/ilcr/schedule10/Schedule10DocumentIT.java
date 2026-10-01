package ca.bc.gov.nrs.ilcr.schedule10;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
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
 * <p>Asserts the pinned wire contract against the V20260817 seed with EXACT numbers, not shapes.
 * The seed's decoy rows make these assertions also pin the query's mill / year / category-{@code
 * '10'} filters.
 */
@DisplayName("GET /api/v1/schedule10 — construction pages (Schedule 10 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule10";

  @Test
  @DisplayName("710/2021 Draft — two pages, nested details, derived Road Group and counts")
  void draftContext_servesPinnedDocument() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
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
        .andExpect(
            jsonPath(
                "$.pages[0].pageLabel", is("Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-")))
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
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
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

        // Stabilizing: item 22 actual, item 10 TtT (subcategory 2), item 9 other (subcategory 4).
        // The two transfers carry DISTINCT NON-ZERO values on purpose — seeded as 0 they were
        // indistinguishable from absent, so swapping items 9 and 10, or dropping both lookups,
        // left every assertion green (code review 2026-08-17).
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.actualCost", is(40000)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.ttTransfer", is(2500)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.otherTransfer", is(-1500)))
        // 40000 + 2500 - 1500 = 41000
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.total", is(41000.0)))
        // 41000 / 3.000 = 13666.666... -> 13666.67
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.costPerLength", is(13666.67)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.ballastMethodCode", is("C")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.ballastMaterialCode", is("GR")))

        // Each material percentage asserted individually with a DISTINCT value: the entity declares
        // RIPPABLE before SOLID while the DTO takes solid first, so a swap is a plausible slip that
        // the total alone cannot catch (10+20+40+20+10 and 20+10+40+10+20 both make 100).
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.solidRockPct", is(10)))
        .andExpect(
            jsonPath("$.pages[0].roadDetails[0].materialComposition.rippableRockPct", is(20)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.coarsePct", is(30)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.finePct", is(25)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.organicPct", is(15)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].materialComposition.totalPct", is(100)))

        // The passthrough scalars. Adjacent same-typed pairs (endHaul/overland distance and volume,
        // stabilizing depth/distanceToSource, the two surface widths) can be transposed in a
        // positional record constructor and still compile, so each is pinned to a distinct value.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].roadName", is("Mainline A")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].roadLifetimeCode", is("P")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].relSoilMoistRgmClsCode", is("1")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].sideSlopePct", is(25)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].detailedEngineeringCostInd", is("N")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].endHaulDistance", is(2.5)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].endHaulVolume", is(1200)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].overlandDistance", is(1.5)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].overlandVolume", is(800)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].comments", is("Fully populated detail")))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.length", is(12.5)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].subGrade.surfaceWidth", is(6.5)))
        // 5.5, deliberately DIFFERENT from the sub-grade width — both were 6.5, which would have
        // defeated this assertion even once written.
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.surfaceWidth", is(5.5)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.length", is(3.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.depth", is(0.3)))
        .andExpect(jsonPath("$.pages[0].roadDetails[0].stabilizing.distanceToSource", is(12.4)));
  }

  @Test
  @DisplayName("a de-listed BEC classification still renders on its row but is NOT offered")
  void deListedBecRendersOnRowButIsNotOffered() throws Exception {
    // Catalogue row 8803 (ESSFwc4a) exists but is deliberately absent from the xref. Detail 8940
    // stores it. This is the ONLY fixture exercising the referenced-BEC fallback — without it the
    // whole second query and its merge could be deleted with nothing failing.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "711")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // The stored classification resolves and renders in full ...
        .andExpect(
            jsonPath(
                "$.pages[0].roadDetails[?(@.roadDetailId == 8940)].becClassification.label",
                hasItem("ESSFwc4a")))
        // ... while the offerable dropdown still withholds it (the surviving BR-06 gate).
        .andExpect(
            jsonPath(
                "$.codeLists.becClassifications[?(@.biogeoclimaticCatalogueId == 8803)]",
                hasSize(0)));
  }

  @Test
  @DisplayName("year and category filters are falsifiable — decoy rows must not appear")
  void yearAndCategoryFiltersExcludeDecoys() throws Exception {
    // Mill 710 also owns page 8908 (year 2020) and page 8909 (category '99'). Both must be
    // excluded. Previously every seeded page was 2021 + '10', so either predicate could be dropped
    // from findPages/findRoadDetails/findCostLines with the suite still green.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages", hasSize(2)))
        .andExpect(jsonPath("$.pages[?(@.pageId == 8908)]", hasSize(0)))
        .andExpect(jsonPath("$.pages[?(@.pageId == 8909)]", hasSize(0)))
        // The wrong-year page owns a cost line of 999999 — it must not leak into any total.
        .andExpect(jsonPath("$..subGrade[?(@.actualCost == 999999)]", hasSize(0)));
  }

  @Test
  @DisplayName("a detail with no cost lines serves ZERO totals but blank individual costs")
  void noCostLines_servesZeroTotalsWithBlankIndividualCosts() throws Exception {
    // This is the shape ALL 66 real delivery road-detail rows have — zero cost lines. Legacy
    // renders each individual cost blank while counting it as zero in the totals, because
    // getCostValue (:1160-1168) coerces before summing. Serving absent totals here would regress
    // 100% of production data to blanks where the legacy screen shows $0.00.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].roadDetailId", is(8911)))
        .andExpect(jsonPath("$.pages[0].roadDetails[1].rowNumber", is(2)))

        // Individual cost lines: ABSENT, because no row exists for them (legacy renders blank).
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.actualCost").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.ttTransfer").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.lessBridges").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[1].stabilizing.actualCost").doesNotExist())

        // Derived totals: ZERO, not absent.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.totalCosts", is(0.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.totalDeductions", is(0.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.total", is(0.0)))
        .andExpect(jsonPath("$.pages[0].roadDetails[1].stabilizing.total", is(0.0)))

        // costPerLength remains ABSENT here — detail 8911 has a null SUB_GRADE_LENGTH, and
        // bigDecimalDivision returns null on a null denominator. That branch IS reachable.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].subGrade.costPerLength").doesNotExist())

        // RSMR class is nullable in delivery and absent on most real rows.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].relSoilMoistRgmClsCode").doesNotExist())
        // materialComposition.totalPct is int arithmetic: always present, here 0.
        .andExpect(jsonPath("$.pages[0].roadDetails[1].materialComposition.totalPct", is(0)));
  }

  @Test
  @DisplayName("TFL-located page derives from the TFL table (BR-05 mutual exclusion)")
  void tflLocatedPage_derivesFromTflTable() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "711")
                .param("year", "2021")
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
        .andExpect(
            jsonPath(
                "$.pages[0].pageLabel", is("Page 1, Period: 2021-05, TSA: null, SB: -, TFL:08")));
  }

  @Test
  @DisplayName("unmapped combinations serve a blank Road Group without error (S12)")
  void unmappedCombination_servesBlankRoadGroupWithoutError() throws Exception {
    // Mill 712: page 8903 has TSA "99" (absent from the switch -> default -> null) and page 8904
    // has TSA "16" + TSB "16Z" (matches a case, but no inner branch -> "" ). Both must serve blank.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "712")
                .param("year", "2021")
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
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "713")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].tflNumberCode", is("77")))
        .andExpect(jsonPath("$.pages[0].roadGroup").doesNotExist());
  }

  @Test
  @DisplayName("a page with no road details serves count 0 and an empty list (CNT-001)")
  void pageWithNoDetails_servesZeroCount() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "714")
                .param("year", "2021")
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
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "715")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.millId", is(715)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.pages", hasSize(0)))
        // Code lists are still served so the frontend can render an empty add-form.
        .andExpect(jsonPath("$.codeLists.roadLifetimes[?(@.code == 'P')]", hasSize(1)));
  }

  @Test
  @DisplayName("a non-Draft track lists its data with editable:false (S31 / BR-02)")
  void nonDraftTrack_listsDataButIsNotEditable() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "716")
                .param("year", "2021")
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
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Content filters, not hasSize: these are SHARED code tables, so a future story seeding one
        // more ballast material would red a size assertion for a reason unrelated to Schedule 10.
        // Each list now carries an EXPIRED ('XP') and a NOT-YET-EFFECTIVE ('FU') decoy, so BOTH
        // legs of the date predicate are falsifiable rather than merely present.
        .andExpect(jsonPath("$.codeLists.forestRegions[?(@.code == 'ROLD')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.forestRegions[?(@.code == 'RNI')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.roadLifetimes[?(@.code == 'XP')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.roadLifetimes[?(@.code == 'FU')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.roadLifetimes[?(@.code == 'P')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.ballastMethods[?(@.code == 'XP')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.ballastMethods[?(@.code == 'FU')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.ballastMethods[?(@.code == 'C')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.ballastMaterials[?(@.code == 'XP')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.ballastMaterials[?(@.code == 'FU')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.rsmrClasses[?(@.code == 'XP')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.rsmrClasses[?(@.code == 'FU')]", hasSize(0)))
        // A NULL EXPIRY_DATE means "never expires" and MUST appear — NULL >= date is UNKNOWN, so
        // the original predicate silently dropped it.
        .andExpect(jsonPath("$.codeLists.forestRegions[?(@.code == 'RNUL')]", hasSize(1)))
        // LD-1/LD-2 removed both of these lists along with their fields.
        .andExpect(jsonPath("$.codeLists.asmCodes").doesNotExist())
        .andExpect(jsonPath("$.codeLists.soilMoistureCodes").doesNotExist());
  }

  @Test
  @DisplayName("the TSA and supply-block lists are served, year-filtered, and not transposed")
  void tsaAndSupplyBlockLists_areServedAndYearFiltered() throws Exception {
    // Both lists shipped with NO coverage at all: deleting either @Query body, or swapping the two
    // toCodes(...) arguments in the assembler -- they are both List<CodeDescriptionDto>, so it
    // compiles -- left the suite green. Each assertion below fails under one of those mutations.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Present and populated, so deleting a @Query body reds this.
        .andExpect(jsonPath("$.codeLists.tsaNumbers").isArray())
        .andExpect(jsonPath("$.codeLists.supplyBlocks").isArray())
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '01')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '16')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '01A')]", hasSize(1)))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '16G')]", hasSize(1)))
        // The descriptions are what the control DISPLAYS, so they are part of the contract.
        .andExpect(
            jsonPath(
                "$.codeLists.tsaNumbers[?(@.code == '01')].description", contains("Arrow TSA")))
        .andExpect(
            jsonPath(
                "$.codeLists.supplyBlocks[?(@.code == '01A')].description",
                contains("Arrow TSA Block A")))
        // TRANSPOSITION GUARD: a TSA code must never appear in the block list, or the reverse. A
        // swap of the two assembler arguments compiles and is caught only here.
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '01A')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '16G')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '01')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '16')]", hasSize(0)))
        // V20260821 seeds '90','Retired TSA' expiring 2010-12-31 stating it exists "to pin that the
        // year filter drops a code". Nothing pinned it until now: no stored 2021 page references
        // '90', so neither leg of the predicate can rescue it.
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '90')]", hasSize(0)));
  }

  @Test
  @DisplayName("an expired block a stored page references is rescued by the referenced-union leg")
  void supplyBlocks_referencedUnionRescuesAnExpiredCode() throws Exception {
    // The union leg on both queries had NO coverage: it can only rescue a code that HAS a row and
    // fell outside the date window, and no fixture created that shape until V20260821 was corrected
    // to seed '16Z' expired (see that file's CORRECTED note). Page 8904 references it, on mill 712.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "712")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Drop the `OR TSB_NUMBER_CODE IN (...)` leg and this goes to 0.
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '16Z')]", hasSize(1)));

    // Scoped to the MILL and the YEAR: mill 710 references no such block, so the expired code stays
    // dropped there. Remove the date predicate and this goes to 1.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '16Z')]", hasSize(0)));
  }

  @Test
  @DisplayName(
      "a code absent from its table entirely is not served, however many pages reference it")
  void codeLists_cannotServeACodeWithNoRow() throws Exception {
    // Page 8903 (mill 712) stores TSA '99' / TSB '99A', neither of which has a row in its code
    // table.
    // The union leg selects FROM the code table, so it cannot invent one. This is the contract
    // boundary that makes the FRONTEND synthesise a stored code as its own option (review H2) —
    // pinned
    // here so nobody "fixes" the client by pointing at a backend guarantee that does not exist.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "712")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code == '99')]", hasSize(0)))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code == '99A')]", hasSize(0)))
        // The page itself still lists, carrying its stored location verbatim.
        .andExpect(jsonPath("$.pages[?(@.pageId == 8903)].tsaNumber", contains("99")))
        .andExpect(jsonPath("$.pages[?(@.pageId == 8903)].tsbNumberCode", contains("99A")));
  }

  @Test
  @DisplayName("BEC is served structurally, gated by the surviving BR-06 xref (deviation (e))")
  void becIsStructuralAndXrefGated() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
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
        .andExpect(
            jsonPath(
                "$.codeLists.becClassifications[?(@.biogeoclimaticCatalogueId == 8803)]",
                hasSize(0)))
        .andExpect(
            jsonPath(
                "$.codeLists.becClassifications[?(@.biogeoclimaticCatalogueId == 8801)]",
                hasSize(1)));
  }

  @Test
  @DisplayName("the three LD-removed fields never appear in a response")
  void removedFieldsAreAbsent() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "710")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].asmCode").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].soilMoistureCode").doesNotExist())
        .andExpect(jsonPath("$.pages[0].roadDetails[0].boulderAreaPct").doesNotExist())
        .andExpect(
            jsonPath("$.pages[0].roadDetails[0].materialComposition.boulderAreaPct")
                .doesNotExist());
  }

  @Test
  @DisplayName("another mill's pages never leak into this document")
  void doesNotLeakOtherMillsPages() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "714")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Mill 714 owns exactly one page; mills 710-713 and 716 own others in the same year.
        .andExpect(jsonPath("$.pages", hasSize(1)))
        .andExpect(jsonPath("$.pages[0].pageId", is(8906)));
  }
}
