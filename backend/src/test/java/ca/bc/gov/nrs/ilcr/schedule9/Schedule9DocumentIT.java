package ca.bc.gov.nrs.ilcr.schedule9;

import static org.hamcrest.Matchers.containsString;
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
 * Acceptance test — Schedule 9 read (AD-5, AD-12), the wire contract PINNED before any Epic 9 write
 * story (AC6). GET /api/v1/schedule9 contractual-work record list.
 *
 * <p>Security is pinned OFF explicitly (Story 8.1's lesson) so the mock {@code ILCR_SUBMITTER}
 * principal applies and these isolate document assembly from authz (covered by
 * {@link Schedule9AuthorizationIT}). {@code editable:true} on the Draft context is therefore the
 * caller-holds-EDIT_SCHEDULE AND Draft branch; the FALSE branch is proven on 517/2021 below and in
 * {@code Schedule9ServiceTest}.
 *
 * <p>Every figure is the EXACT value the V20260813 seed produces. The seed's two DECOY records make
 * the record query's predicates load-bearing: a wrong-year record (9190) and a wrong-category
 * record (9191) sit inside 514/2021 and must not appear. The records are inserted OUT OF ID ORDER
 * (9103, 9101, 9102) so the ascending {@code ORDER BY} is pinned, not insertion order.
 */
@DisplayName("GET /api/v1/schedule9 — contractual-work record list (Schedule 9 read)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule9DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule9";

  @Test
  @DisplayName("514/2021 Draft — three records served in id order (AC1), decoys excluded")
  void draftContext_servesRecordsInIdOrder() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        // The wrong-year (9190) and wrong-category (9191) decoys must NOT appear.
        .andExpect(jsonPath("$.records", hasSize(3)))
        // Addressed BY INDEX so the ascending order is pinned. The seed inserts 9103, 9101, 9102 —
        // insertion order would fail here.
        .andExpect(jsonPath("$.records[0].id", is(9101)))
        .andExpect(jsonPath("$.records[1].id", is(9102)))
        .andExpect(jsonPath("$.records[2].id", is(9103)))
        // Story 9.3: the four dropdown code lists ride on the GET. Contractual Items are the fixed
        // category-'9' catalogue 108–114 (ordered by id); the unit/BEC/source reference options are
        // served whole. The record fields above match a code in these lists.
        .andExpect(jsonPath("$.codeLists.contractualItems", hasSize(7)))
        .andExpect(jsonPath("$.codeLists.contractualItems[0].code", is("108")))
        .andExpect(jsonPath("$.codeLists.contractualItems[0].description", is("Cattleguard")))
        .andExpect(jsonPath("$.codeLists.contractualItems[6].code", is("114")))
        // Assert BOTH halves so a code/description swap in the query is caught (the code lands in the
        // code slot, the label in the description slot) — not just that some code is present.
        .andExpect(jsonPath("$.codeLists.unitTypes[*].code", hasItem("M3")))
        .andExpect(jsonPath("$.codeLists.unitTypes[*].description", hasItem("Cubic Metres")))
        .andExpect(jsonPath("$.codeLists.biogeoclimaticZones[*].code", hasItem("BZ1")))
        .andExpect(jsonPath("$.codeLists.biogeoclimaticZones[*].description", hasItem("BEC Zone One")))
        .andExpect(jsonPath("$.codeLists.sources[*].code", hasItem("A")))
        .andExpect(jsonPath("$.codeLists.sources[*].description", hasItem("Actual Cost")))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("record 9101 — every field served as stored, code descriptions resolved, $/Unit 50.00")
  void fullyPopulatedRecord_servesEveryFigure() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.records[0].contractorId", is("CTR-001")))
        // Contractual Item = the 108–114 code + its ILCR_REPORT_COST_ITEM name (from the cost line).
        .andExpect(jsonPath("$.records[0].contractualItem.code", is("108")))
        .andExpect(jsonPath("$.records[0].contractualItem.description", is("Cattleguard")))
        // Unit Type + BEC + Source codes resolved to their reference-table descriptions in SQL.
        .andExpect(jsonPath("$.records[0].unitType.code", is("M3")))
        .andExpect(jsonPath("$.records[0].unitType.description", is("Cubic Metres")))
        // The fractional value survives the NUMBER(6,1) read (Oracle would canonicalize an integer
        // like 100.0 to 100 and serialize it as an int — this pins the one-decimal precision).
        .andExpect(jsonPath("$.records[0].numberOfUnits", is(12.5)))
        .andExpect(jsonPath("$.records[0].biogeoclimaticZone.code", is("BZ1")))
        .andExpect(jsonPath("$.records[0].biogeoclimaticZone.description", is("BEC Zone One")))
        .andExpect(jsonPath("$.records[0].cost", is(5000)))
        // AD-5 derived server-side: 5000 / 12.5 = 400.00, scale 2.
        .andExpect(jsonPath("$.records[0].costPerUnit", is(400.00)))
        // Pin the scale-2 formatting on the RAW wire: jsonPath is(400.00) parses to a Double and
        // would also pass for a bare 400, so the HALF_UP scale-2 contract needs a string check.
        .andExpect(content().string(containsString("\"costPerUnit\":400.00")))
        .andExpect(jsonPath("$.records[0].sideSlopePct", is(25)))
        .andExpect(jsonPath("$.records[0].source.code", is("A")))
        .andExpect(jsonPath("$.records[0].source.description", is("Actual Cost")))
        .andExpect(jsonPath("$.records[0].comments", is("Cattleguard install.")))
        // The three "Other" free-text descriptions are null here, so NON_NULL omits them entirely.
        .andExpect(jsonPath("$.records[0].itemDescription").doesNotExist())
        .andExpect(jsonPath("$.records[0].unitDescription").doesNotExist())
        .andExpect(jsonPath("$.records[0].sourceDescription").doesNotExist());
  }

  @Test
  @DisplayName("record 9102 — zero units: $/Unit is null (S14) even though a cost is stored (AC2)")
  void zeroUnitsRecord_omitsCostPerUnit() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[1].id", is(9102)))
        .andExpect(jsonPath("$.records[1].contractualItem.code", is("109")))
        .andExpect(jsonPath("$.records[1].cost", is(3000)))
        // Zero Number of Units -> no divide, costPerUnit absent (never 0, never an error).
        .andExpect(jsonPath("$.records[1].costPerUnit").doesNotExist())
        // Null Side Slope + comments are omitted, not defaulted.
        .andExpect(jsonPath("$.records[1].sideSlopePct").doesNotExist())
        .andExpect(jsonPath("$.records[1].comments").doesNotExist());
  }

  @Test
  @DisplayName("record 9103 — the three conditional 'Other' descriptions served verbatim")
  void otherItemRecord_servesConditionalDescriptions() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[2].id", is(9103)))
        // revisionCount is the per-record optimistic-lock token (Story 9.2). 9103's CONTRACTUAL_WORK
        // _REPORT row is rev 2 while its cost-detail line is rev 0, so this pins that the token is
        // read from the record master, not the cost line.
        .andExpect(jsonPath("$.records[2].revisionCount", is(2)))
        // Contractual Item 114 ("Other") + its free-text ITEM_DESCRIPTION on the cost line.
        .andExpect(jsonPath("$.records[2].contractualItem.code", is("114")))
        .andExpect(jsonPath("$.records[2].contractualItem.description", is("Other")))
        .andExpect(jsonPath("$.records[2].itemDescription", is("Custom gate")))
        // Unit "Other" + its UNIT_DESCRIPTION free text.
        .andExpect(jsonPath("$.records[2].unitType.code", is("OT")))
        .andExpect(jsonPath("$.records[2].unitDescription", is("linear metre")))
        // Source "Other" + its SOURCE_DESCRIPTION free text.
        .andExpect(jsonPath("$.records[2].source.code", is("O")))
        .andExpect(jsonPath("$.records[2].sourceDescription", is("Contractor quote")))
        // 2500 / 50.0 = 50.00.
        .andExpect(jsonPath("$.records[2].cost", is(2500)))
        .andExpect(jsonPath("$.records[2].costPerUnit", is(50.00)));
  }

  @Test
  @DisplayName("515/2021 — ACT with a status row but no records -> 200 with records: [] (AC5)")
  void activeMillWithNoRecords_returns200Empty() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "515").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.millId", is(515)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        // Summary-less: absent records are 200-empty, NOT a 404 (the 404 is a missing context row).
        .andExpect(jsonPath("$.records", hasSize(0)));
  }

  @Test
  @DisplayName("517/2021 Submitted — full record list, editable:false (AC4, S30 authority)")
  void nonDraftContext_listsRecordsReadOnly() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "517").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        // The server is the sole authority: EDIT_SCHEDULE alone is not enough on a non-Draft track.
        .andExpect(jsonPath("$.editable", is(false)))
        // Read-only does NOT mean empty — the record is still fully served and fully derived.
        .andExpect(jsonPath("$.records", hasSize(1)))
        .andExpect(jsonPath("$.records[0].id", is(9110)))
        .andExpect(jsonPath("$.records[0].contractualItem.code", is("111")))
        .andExpect(jsonPath("$.records[0].cost", is(8000)))
        // 8000 / 40.0 = 200.00.
        .andExpect(jsonPath("$.records[0].costPerUnit", is(200.00)));
  }
}
