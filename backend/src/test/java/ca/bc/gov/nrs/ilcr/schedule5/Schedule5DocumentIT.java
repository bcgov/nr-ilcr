package ca.bc.gov.nrs.ilcr.schedule5;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
 * Acceptance test — Schedule 5 read (AD-5, AD-10, AD-12). GET /api/v1/schedule5 camp list.
 *
 * <p>Security is pinned OFF explicitly (not relied on by default — that cost Story 8.1 a review
 * patch) so the mock {@code ILCR_SUBMITTER} principal applies and these isolate document assembly
 * from authz (covered by {@link Schedule5AuthorizationIT}).
 *
 * <p>Every number below is the EXACT figure the V34 seed produces, hand-derived from the legacy
 * formulas rather than copied from a run. The seed's DECOY rows make the CAMP query's predicates
 * load-bearing: a wrong-year camp (8406) and a wrong-category camp (8407) sit inside 514/2021 and
 * must not appear, and a duplicate item-56 row plus a registered-but-undispatched item-57 row must
 * be ignored without reaching a total.
 *
 * <p><strong>What this class cannot see, and where it lives instead.</strong> The DETAIL query's
 * own year/category predicates are invisible from here — its decoy rows hang off camps the service
 * never looks up, so deleting those predicates changes no response — and so is its {@code ORDER BY},
 * which Oracle's insertion-order return would mask. Both are pinned in {@link Schedule5RepositoryIT}.
 * The {@code editable} permission lookup is invisible too (this class runs with security off, and
 * both shipped roles hold {@code EDIT_SCHEDULE}); {@code Schedule5ControllerTest} covers it.
 */
@DisplayName("GET /api/v1/schedule5 — camp list (Schedule 5 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule5DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule5";

  @Test
  @DisplayName("514/2021 Draft — five camps served in CAMP_REPORT_ID order (AC7), decoys excluded")
  void draftContext_servesCampsInIdOrder() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        // The wrong-year (8406) and wrong-category (8407) decoys must NOT appear.
        .andExpect(jsonPath("$.camps", hasSize(5)))
        // Addressed BY INDEX so the ascending order is pinned, not merely membership. The seed
        // inserts these as 8403, 8401, 8405, 8402, 8404 — insertion order would fail here.
        .andExpect(jsonPath("$.camps[0].campId", is(8401)))
        .andExpect(jsonPath("$.camps[1].campId", is(8402)))
        .andExpect(jsonPath("$.camps[2].campId", is(8403)))
        .andExpect(jsonPath("$.camps[3].campId", is(8404)))
        .andExpect(jsonPath("$.camps[4].campId", is(8405)))
        // There is no document-level total and no top-level revisionCount (deviation (b)).
        .andExpect(jsonPath("$.totalCost").doesNotExist())
        .andExpect(jsonPath("$.revisionCount").doesNotExist())
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("camp 8401 — descriptors, all twelve categories, and the four derived totals")
  void fullyPopulatedCamp_servesEveryFigure() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // ---- descriptors, served exactly as stored ----
        .andExpect(jsonPath("$.camps[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.camps[0].campName", is("Cedar Flats Camp")))
        .andExpect(jsonPath("$.camps[0].roadDistanceToOperatingArea", is(42.50)))
        .andExpect(jsonPath("$.camps[0].sizeOfCamp", is(60)))
        .andExpect(jsonPath("$.camps[0].associatedCampVolume", is(120000)))
        .andExpect(jsonPath("$.camps[0].isolatedCamp", is(true)))
        .andExpect(jsonPath("$.camps[0].comments", is("Seasonal camp, spring only.")))
        // ---- the fixed grid: stored volume/cost, derived $/m3 ----
        // Every category's VOLUME is asserted, not just its cost and $/m3. Without these the
        // service could return null for the volume slot, or substitute ASSOCIATED_CAMP_VOLUME for
        // the stored one, and the suite would stay green — the $/m3 assertions cannot tell,
        // because they divide by that same volume. Catering (96000) and Crew (90000) deliberately
        // differ from the camp volume (120000) so "serve what is stored, never re-derive from the
        // camp volume" is pinned to a number only the stored row can produce.
        .andExpect(jsonPath("$.camps[0].cateringAndFood.volume", is(96000)))
        .andExpect(jsonPath("$.camps[0].cateringAndFood.cost", is(480000)))
        .andExpect(jsonPath("$.camps[0].cateringAndFood.costPerVolume", is(5.00)))
        .andExpect(jsonPath("$.camps[0].wagesAndBenefits.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].wagesAndBenefits.cost", is(960000)))
        .andExpect(jsonPath("$.camps[0].wagesAndBenefits.costPerVolume", is(8.00)))
        .andExpect(jsonPath("$.camps[0].depreciationLease.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].depreciationLease.cost", is(120000)))
        .andExpect(jsonPath("$.camps[0].depreciationLease.costPerVolume", is(1.00)))
        .andExpect(jsonPath("$.camps[0].generalCampExpenses.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].generalCampExpenses.cost", is(60000)))
        .andExpect(jsonPath("$.camps[0].generalCampExpenses.costPerVolume", is(0.50)))
        .andExpect(jsonPath("$.camps[0].crewTransportation.volume", is(90000)))
        .andExpect(jsonPath("$.camps[0].crewTransportation.cost", is(180000)))
        .andExpect(jsonPath("$.camps[0].crewTransportation.costPerVolume", is(2.00)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesLand.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesLand.cost", is(90000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesLand.costPerVolume", is(0.75)))
        // All six Access components are non-zero and distinct, so dropping any one of them from
        // the total below changes the answer. Rail used to be seeded 0, which made the
        // "sums exactly six" test verify five. The stored-zero case moved to camp 8404.
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesRail.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesRail.cost", is(15000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesRail.costPerVolume", is(0.13)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesAir.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesAir.cost", is(12000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesAir.costPerVolume", is(0.10)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesWater.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesWater.cost", is(6000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesWater.costPerVolume", is(0.05)))
        // ---- Recoveries: the volume-less twelfth category ----
        .andExpect(jsonPath("$.camps[0].recoveries.cost", is(44000)))
        .andExpect(jsonPath("$.camps[0].recoveries.volume").doesNotExist())
        .andExpect(jsonPath("$.camps[0].recoveries.costPerVolume").doesNotExist())
        // ---- sub-page aggregates: cost is the row SUM, volume is the stored item-141/142 ----
        // The item-141 volume (80000) and item-142 volume (60000) differ from each other AND from
        // the camp volume, so each aggregate is pinned to its own stored source.
        .andExpect(jsonPath("$.camps[0].otherCampExpenses.volume", is(80000)))
        .andExpect(jsonPath("$.camps[0].otherCampExpenses.cost", is(24000)))
        // Per-term against 80000: 0.13 + 0.13 + 0.05 = 0.31. The ratio-of-sums shortcut would give
        // 24000/80000 = 0.30, so this single assertion is what pins calculateCostVolume's formula.
        .andExpect(jsonPath("$.camps[0].otherCampExpenses.costPerVolume", is(0.31)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenses.volume", is(60000)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenses.cost", is(3000)))
        // 3000/60000 = 0.05 — divided by the item-142 volume, not the camp volume (which would
        // give 0.03) and not the item-141 volume (which would give 0.04).
        .andExpect(jsonPath("$.camps[0].otherAccessExpenses.costPerVolume", is(0.05)))
        // ---- counts: raw row counts, deliberately UNEQUAL so a swapped pair is caught ----
        .andExpect(jsonPath("$.camps[0].otherCampExpenseCount", is(3)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenseCount", is(1)))
        // ---- derived totals: every one divides by the CAMP volume, never a category volume ----
        // 480000+960000+120000+60000+24000 = 1644000 -> /120000 = 13.70
        .andExpect(jsonPath("$.camps[0].campSubTotal.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].campSubTotal.cost", is(1644000)))
        .andExpect(jsonPath("$.camps[0].campSubTotal.costPerVolume", is(13.70)))
        // 1644000 - 44000 = 1600000 -> 13.3333.. -> 13.33
        .andExpect(jsonPath("$.camps[0].campTotal.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].campTotal.cost", is(1600000)))
        .andExpect(jsonPath("$.camps[0].campTotal.costPerVolume", is(13.33)))
        // 180000+90000+15000+12000+6000+3000 = 306000 -> /120000 = 2.55
        .andExpect(jsonPath("$.camps[0].accessExpenseTotal.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].accessExpenseTotal.cost", is(306000)))
        .andExpect(jsonPath("$.camps[0].accessExpenseTotal.costPerVolume", is(2.55)))
        // 1600000 + 306000 = 1906000 -> 15.8833.. -> 15.88
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.volume", is(120000)))
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.cost", is(1906000)))
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.costPerVolume", is(15.88)));
  }

  @Test
  @DisplayName("distance serializes as the exact decimal — no new BigDecimal(double) expansion")
  void distanceSerializesWithoutBinaryExpansion() throws Exception {
    // jsonPath cannot see this. Jayway parses a JSON decimal literal into a Double before Hamcrest
    // compares, so is(999999.90) passes whether the body says 999999.9 or
    // 999999.899999999994179233908653259277343750 — and the second is exactly what legacy produced:
    // it mapped DISTANCE_TO_OPERATING_AREA to a Double and then called new BigDecimal(double),
    // leaking the binary expansion of a value the NUMBER(8,2) column had already fixed. A JSF
    // DecimalFormat hid that; a JSON API would ship it. Reading the column as BigDecimal is the
    // headline reason CampReportEntity exists in the shape it does, and asserting the raw bytes is
    // the only way a test can observe a regression to the legacy idiom.
    //
    // 999999.9 is chosen deliberately: it is NOT exactly representable as a double, so the two
    // idioms diverge here. 42.5 and 8.25 are exact binary fractions and would pass either way.
    // Note the column does NOT preserve a trailing zero — Oracle NUMBER stores 999999.90 as
    // 999999.9 — so this pins the value, not a scale.
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("\"roadDistanceToOperatingArea\":999999.9,")))
        .andExpect(content().string(not(containsString("999999.89"))));
  }

  @Test
  @DisplayName("camp 8401 — duplicate item-56 row loses to the lower detail id; item 57 is dropped")
  void duplicateAndUnknownItems_doNotReachTotals() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Detail 8428 carries 777777 for item 56 but sits ABOVE 8411; first-by-detail-id-wins
        // (deviation (f)) keeps 8411's 480000. A last-wins port fails right here.
        .andExpect(jsonPath("$.camps[0].cateringAndFood.cost", is(480000)))
        // Detail 8427 is item 57 — registered in delivery but undispatched by legacy, so it is
        // dropped with a warning. Its 999999 must not have reached Sub-Total (still 1644000).
        .andExpect(jsonPath("$.camps[0].campSubTotal.cost", is(1644000)));
  }

  @Test
  @DisplayName("camp 8402 — zero detail rows: every category empty, every total NULL not 0")
  void zeroDetailCamp_servesNullTotals() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[1].campName", is("Bare Ridge Camp")))
        .andExpect(jsonPath("$.camps[1].revisionCount", is(1)))
        .andExpect(jsonPath("$.camps[1].isolatedCamp", is(false)))
        // Null descriptors are omitted, not defaulted.
        .andExpect(jsonPath("$.camps[1].roadDistanceToOperatingArea").doesNotExist())
        .andExpect(jsonPath("$.camps[1].sizeOfCamp").doesNotExist())
        .andExpect(jsonPath("$.camps[1].associatedCampVolume").doesNotExist())
        .andExpect(jsonPath("$.camps[1].comments").doesNotExist())
        // The category still EXISTS as an object; its cost/volume are simply absent. Asserting the
        // omission is what stops a later `0` default from shipping green.
        .andExpect(jsonPath("$.camps[1].cateringAndFood").exists())
        .andExpect(jsonPath("$.camps[1].cateringAndFood.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[1].cateringAndFood.volume").doesNotExist())
        .andExpect(jsonPath("$.camps[1].cateringAndFood.costPerVolume").doesNotExist())
        // Totals over all-null components are NULL, never 0 (AC2).
        .andExpect(jsonPath("$.camps[1].campSubTotal.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[1].campTotal.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[1].accessExpenseTotal.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[1].campAndAccessTotal.cost").doesNotExist())
        // Counts are 0, never null.
        .andExpect(jsonPath("$.camps[1].otherCampExpenseCount", is(0)))
        .andExpect(jsonPath("$.camps[1].otherAccessExpenseCount", is(0)));
  }

  @Test
  @DisplayName("camp 8403 — Recoveries only: null Sub-Total makes Camp Total null, not a negative")
  void recoveriesOnlyCamp_servesNullCampTotal() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[2].campName", is("Salvage Camp")))
        .andExpect(jsonPath("$.camps[2].revisionCount", is(2)))
        .andExpect(jsonPath("$.camps[2].recoveries.cost", is(5000)))
        // This camp's item-61 row STORES a volume (50000) and the served Recoveries must still
        // carry neither volume nor $/m3: it is the volume-less twelfth category and the service
        // hard-codes both to null. Every other Recoveries fixture stores a NULL volume, where the
        // fields would be absent anyway — this is the one that makes the suppression load-bearing.
        .andExpect(jsonPath("$.camps[2].recoveries.volume").doesNotExist())
        .andExpect(jsonPath("$.camps[2].recoveries.costPerVolume").doesNotExist())
        .andExpect(jsonPath("$.camps[2].campSubTotal.cost").doesNotExist())
        // bigDecimalCostSubtraction: a null total yields null REGARDLESS of the subtrahend — so
        // this must be absent, not -5000. This is also the §T1 canary: a port that derived
        // campTotal without computing Sub-Total first would land here too, so the negative-total
        // case (camp 8404) is what distinguishes a correct implementation from a silently null one.
        .andExpect(jsonPath("$.camps[2].campTotal.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[2].accessExpenseTotal.cost").doesNotExist())
        .andExpect(jsonPath("$.camps[2].campAndAccessTotal.cost").doesNotExist());
  }

  @Test
  @DisplayName("camp 8404 — Recoveries exceeds Sub-Total: NEGATIVE Camp Total, never clamped")
  void recoveriesExceedingSubTotal_servesNegativeTotal() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[3].campName", is("Overrun Camp")))
        // Real data sits exactly on this bound; the legacy message says 999,999 while the validator
        // enforces 999999.9 — a recorded Open Question, preserved rather than resolved.
        .andExpect(jsonPath("$.camps[3].roadDistanceToOperatingArea", is(999999.90)))
        .andExpect(jsonPath("$.camps[3].sizeOfCamp", is(999)))
        .andExpect(jsonPath("$.camps[3].campSubTotal.cost", is(30000)))
        .andExpect(jsonPath("$.camps[3].campSubTotal.costPerVolume", is(3.00)))
        .andExpect(jsonPath("$.camps[3].recoveries.cost", is(50000)))
        // 30000 - 50000 = -20000. BR-04/S09: subtracted as stored, never abs()'d or floored at 0.
        .andExpect(jsonPath("$.camps[3].campTotal.cost", is(-20000)))
        .andExpect(jsonPath("$.camps[3].campTotal.costPerVolume", is(-2.00)))
        // A stored ZERO cost is a real 0, never omitted — it is null that disappears. This camp's
        // land row is its ONLY access component, so 0-versus-absent is exactly the distinction
        // under test: a total of 0 is NOT the same as a total over all-null components (camp
        // 8405 covers that). Both must be distinguishable in the served JSON.
        .andExpect(jsonPath("$.camps[3].equipAndSuppliesLand.volume", is(10000)))
        .andExpect(jsonPath("$.camps[3].equipAndSuppliesLand.cost", is(0)))
        .andExpect(jsonPath("$.camps[3].equipAndSuppliesLand.costPerVolume", is(0.00)))
        .andExpect(jsonPath("$.camps[3].accessExpenseTotal.cost", is(0)))
        .andExpect(jsonPath("$.camps[3].accessExpenseTotal.costPerVolume", is(0.00)))
        // addCost(-20000, 0) = -20000 — a zero access side adds nothing but does not null it.
        .andExpect(jsonPath("$.camps[3].campAndAccessTotal.cost", is(-20000)))
        .andExpect(jsonPath("$.camps[3].campAndAccessTotal.costPerVolume", is(-2.00)));
  }

  @Test
  @DisplayName("camp 8405 — zero associated volume: costs still total, every $/m3 is null")
  void zeroVolumeCamp_omitsEveryCostPerVolume() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[4].campName", is("Zero Volume Camp")))
        .andExpect(jsonPath("$.camps[4].associatedCampVolume", is(0)))
        .andExpect(jsonPath("$.camps[4].cateringAndFood.cost", is(25000)))
        .andExpect(jsonPath("$.camps[4].cateringAndFood.costPerVolume").doesNotExist())
        .andExpect(jsonPath("$.camps[4].wagesAndBenefits.cost", is(15000)))
        .andExpect(jsonPath("$.camps[4].wagesAndBenefits.costPerVolume").doesNotExist())
        // The cost total is real; only the division is suppressed (no divide-by-zero).
        .andExpect(jsonPath("$.camps[4].campSubTotal.cost", is(40000)))
        .andExpect(jsonPath("$.camps[4].campSubTotal.costPerVolume").doesNotExist())
        // No Recoveries row -> subtrahend null -> total passes through unchanged.
        .andExpect(jsonPath("$.camps[4].campTotal.cost", is(40000)))
        .andExpect(jsonPath("$.camps[4].campTotal.costPerVolume").doesNotExist())
        // No access rows at all -> sumCosts over six nulls is NULL, never 0. Camp 8404 is the
        // mirror case (one stored zero -> a real 0); the two together pin the distinction.
        .andExpect(jsonPath("$.camps[4].accessExpenseTotal.cost").doesNotExist())
        // addCost(40000, null) = 40000 — a null access side does not null the combined total.
        .andExpect(jsonPath("$.camps[4].campAndAccessTotal.cost", is(40000)))
        .andExpect(jsonPath("$.camps[4].campAndAccessTotal.costPerVolume").doesNotExist());
  }

  @Test
  @DisplayName("515/2021 — ACT with a status row but no camps -> 200 with camps: [] (AC6)")
  void activeMillWithNoCamps_returns200Empty() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "515").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.millId", is(515)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        // Deviation (a): absent camps are 200-empty, NOT the ERR-005 404 — Schedule 5 is
        // summary-less, so there is no summary row whose absence could mean "not found".
        .andExpect(jsonPath("$.camps", hasSize(0)));
  }

  @Test
  @DisplayName("517/2021 Submitted — full camp list, editable:false (AC5, S19 authority)")
  void nonDraftContext_listsCampsReadOnly() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "517").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        // The server is the sole authority for this flag: EDIT_SCHEDULE alone is not enough.
        .andExpect(jsonPath("$.editable", is(false)))
        // Read-only does NOT mean empty — the data is still fully served and fully derived.
        .andExpect(jsonPath("$.camps", hasSize(1)))
        .andExpect(jsonPath("$.camps[0].campId", is(8408)))
        .andExpect(jsonPath("$.camps[0].campName", is("Submitted Camp")))
        .andExpect(jsonPath("$.camps[0].revisionCount", is(3)))
        .andExpect(jsonPath("$.camps[0].cateringAndFood.cost", is(30000)))
        .andExpect(jsonPath("$.camps[0].campSubTotal.cost", is(30000)))
        .andExpect(jsonPath("$.camps[0].campSubTotal.costPerVolume", is(0.50)))
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.cost", is(30000)));
  }
}
