package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Story 2.3 acceptance (AD-5, AD-10, AD-12): GET /api/v1/schedule1 with the BR-03 Crown Timber
 * pre-fill (+ WRN-001) and BR-04 Schedule 3 admin-cost pulls, against a real Oracle dialect.
 *
 * <p>Security is OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * applies, isolating the document behavior from authz (covered by {@link Schedule1AuthorizationIT}).
 *
 * <p>Fixtures (V5 seed): mill 522/2021 has a category-1 summary with NO detail rows (all volumes
 * empty) and a category-3 Crown Timber (item 119) volume of 7777 ⇒ pre-fill fires. Mill 514/2021 is
 * populated (code-12 volume) ⇒ no pre-fill; its category-3 summary carries item 119 = 54321 and the
 * admin-cost source rows (115/135/37) so the BR-04 pulls resolve.
 */
@DisplayName("GET /api/v1/schedule1 — crown pre-fill & Schedule 3 pulls (Story 2.3)")
class Schedule1CrownPrefillIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule1";
  private static final String WRN_001 =
      "The Crown Timber (Sch 3) volume has been set for volume fields. Please check and save schedule.";

  @Test
  @DisplayName("522/2021 first entry — crown volume pre-filled into every savable field + WRN-001")
  void firstEntry_prefillsCrownVolumeAndWarns() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "522")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        // BR-03 source (D1): the category-3 item-119 detail volume, NOT the summary CROWN_VOLUME.
        .andExpect(jsonPath("$.schedule3CrownVolume", is(7777)))
        // The full legacy 13-field copy: line items 12-18 + 143 + 144 (9 rows) all carry the crown value.
        .andExpect(jsonPath("$.lineItems", hasSize(9)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].volume", contains(7777)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 18)].volume", contains(7777)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 143)].volume", contains(7777)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 144)].volume", contains(7777)))
        // Silviculture 1, 2, 139, 140 volumes carry it too.
        .andExpect(jsonPath("$.silviculture.actualSpent.volume", is(7777)))
        .andExpect(jsonPath("$.silviculture.accruedLessActual.volume", is(7777)))
        .andExpect(jsonPath("$.silviculture.lessAdmin.volume", is(7777)))
        .andExpect(jsonPath("$.silviculture.total.volume", is(7777)))
        // WRN-001 verbatim on the advisory warnings channel (AD-8).
        .andExpect(jsonPath("$.warnings", hasSize(1)))
        .andExpect(jsonPath("$.warnings[0].key", is("crownVolumeSetForSchedule1")))
        .andExpect(jsonPath("$.warnings[0].text", is(WRN_001)));
  }

  @Test
  @DisplayName("514/2021 populated — no pre-fill/warning; BR-04 admin costs pulled from Schedule 3")
  void populated_noPrefill_pullsAdminCosts() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "514")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // No pre-fill (already populated) → empty warnings; existing content unchanged (AC4).
        .andExpect(jsonPath("$.warnings", hasSize(0)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].volume", contains(1000)))
        .andExpect(jsonPath("$.crownVolume", is(12345)))
        // BR-03 source is distinct from the cat-1 CROWN_VOLUME (proves D1: item-119 detail).
        .andExpect(jsonPath("$.schedule3CrownVolume", is(54321)))
        // BR-04 pulls: Forest Mgmt Admin = 900000 - 300000; Less Silv Admin = 150000.
        .andExpect(jsonPath("$.forestMgmtAdminCost", is(600000)))
        .andExpect(jsonPath("$.lessSilvAdminCost", is(150000)));
  }
}
