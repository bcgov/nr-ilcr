package ca.bc.gov.nrs.ilcr.schedule5;

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
 * Acceptance test — Schedule 5 read (AD-5, AD-10, AD-12). GET /api/v1/schedule5 camp list.
 *
 * <p>Security is pinned OFF explicitly (not relied on by default — that cost Story 8.1 a review
 * patch) so the mock {@code ILCR_SUBMITTER} principal applies and these isolate document assembly
 * from authz (covered by {@link Schedule5AuthorizationIT}).
 *
 * <p>Every number below is the EXACT figure the V34 seed produces, hand-derived from the legacy
 * formulas rather than copied from a run. The seed's DECOY rows make the query's predicates
 * load-bearing: a wrong-year camp, a wrong-category camp, a duplicate item-56 row with a higher
 * detail id, and a registered-but-undispatched item-57 row all sit inside 514/2021 and must be
 * invisible or ignored.
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
        .andExpect(jsonPath("$.camps[0].cateringAndFood.cost", is(480000)))
        .andExpect(jsonPath("$.camps[0].cateringAndFood.costPerVolume", is(4.00)))
        .andExpect(jsonPath("$.camps[0].wagesAndBenefits.cost", is(960000)))
        .andExpect(jsonPath("$.camps[0].wagesAndBenefits.costPerVolume", is(8.00)))
        .andExpect(jsonPath("$.camps[0].depreciationLease.cost", is(120000)))
        .andExpect(jsonPath("$.camps[0].depreciationLease.costPerVolume", is(1.00)))
        .andExpect(jsonPath("$.camps[0].generalCampExpenses.cost", is(60000)))
        .andExpect(jsonPath("$.camps[0].generalCampExpenses.costPerVolume", is(0.50)))
        .andExpect(jsonPath("$.camps[0].crewTransportation.cost", is(180000)))
        .andExpect(jsonPath("$.camps[0].crewTransportation.costPerVolume", is(1.50)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesLand.cost", is(90000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesLand.costPerVolume", is(0.75)))
        // A stored ZERO is a real 0, never omitted — it is null that disappears.
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesRail.cost", is(0)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesRail.costPerVolume", is(0.00)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesAir.cost", is(12000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesAir.costPerVolume", is(0.10)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesWater.cost", is(6000)))
        .andExpect(jsonPath("$.camps[0].equipAndSuppliesWater.costPerVolume", is(0.05)))
        // ---- Recoveries: the volume-less twelfth category ----
        .andExpect(jsonPath("$.camps[0].recoveries.cost", is(44000)))
        .andExpect(jsonPath("$.camps[0].recoveries.volume").doesNotExist())
        .andExpect(jsonPath("$.camps[0].recoveries.costPerVolume").doesNotExist())
        // ---- sub-page aggregates: cost is the row SUM, volume is the stored item-141/142 ----
        .andExpect(jsonPath("$.camps[0].otherCampExpenses.cost", is(24000)))
        // 0.08 + 0.08 + 0.03 = 0.19 PER-TERM. The ratio-of-sums shortcut would give 24000/120000 =
        // 0.20, so this single assertion is what pins legacy's calculateCostVolume formula.
        .andExpect(jsonPath("$.camps[0].otherCampExpenses.costPerVolume", is(0.19)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenses.cost", is(3000)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenses.costPerVolume", is(0.03)))
        // ---- counts: raw row counts, deliberately UNEQUAL so a swapped pair is caught ----
        .andExpect(jsonPath("$.camps[0].otherCampExpenseCount", is(3)))
        .andExpect(jsonPath("$.camps[0].otherAccessExpenseCount", is(1)))
        // ---- derived totals ----
        // 480000+960000+120000+60000+24000 = 1644000 -> /120000 = 13.70
        .andExpect(jsonPath("$.camps[0].campSubTotal.cost", is(1644000)))
        .andExpect(jsonPath("$.camps[0].campSubTotal.costPerVolume", is(13.70)))
        // 1644000 - 44000 = 1600000 -> 13.3333.. -> 13.33
        .andExpect(jsonPath("$.camps[0].campTotal.cost", is(1600000)))
        .andExpect(jsonPath("$.camps[0].campTotal.costPerVolume", is(13.33)))
        // 180000+90000+0+12000+6000+3000 = 291000 -> 2.425 -> 2.43 (HALF_UP)
        .andExpect(jsonPath("$.camps[0].accessExpenseTotal.cost", is(291000)))
        .andExpect(jsonPath("$.camps[0].accessExpenseTotal.costPerVolume", is(2.43)))
        // 1600000 + 291000 = 1891000 -> 15.7583.. -> 15.76
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.cost", is(1891000)))
        .andExpect(jsonPath("$.camps[0].campAndAccessTotal.costPerVolume", is(15.76)));
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
        // addCost(-20000, null) = -20000 — the access side adds nothing but does not null it.
        .andExpect(jsonPath("$.camps[3].accessExpenseTotal.cost").doesNotExist())
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
