package ca.bc.gov.nrs.ilcr.schedule3;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Acceptance test — Story 4.2 PUT/DELETE + BR-09 Crown Timber push (AD-5/AD-9/AD-14). Security OFF
 * (mock ILCR_SUBMITTER) so this isolates the write behavior from authz ({@link Schedule3WriteAuthorizationIT}).
 * Each test reads the current revision via GET first, so it is order-independent against the shared
 * container. Write fixtures seeded by V9 (mills 540/541/542).
 */
@DisplayName("PUT/DELETE /api/v1/schedule3 — write path + Crown Timber push (Story 4.2)")
class Schedule3WriteIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3";

  /** GET the current document JSON for a mill/year (to read the optimistic-lock revision etc.). */
  private String getDoc(long millId) throws Exception {
    return mockMvc.perform(get(ENDPOINT).param("millId", String.valueOf(millId)).param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private int revisionOf(String doc) {
    return JsonPath.read(doc, "$.revisionCount");
  }

  @Test
  @DisplayName("PUT valid — persists entered values, returns SUC-001 + recomputed document")
  void putValid_persistsAndRecomputes() throws Exception {
    int rev = revisionOf(getDoc(540));
    String body = """
        { "revisionCount": %d, "comments": "updated", "overrideHarvestTotalPop": "N",
          "lineItems": [ { "costItemCode": 27, "harvest": 111, "pop": 44 } ],
          "popTimberVolume": 5000, "crownTimberVolume": 5000 }
        """.formatted(rev);
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.revisionCount", is(rev + 1)))
        .andExpect(jsonPath("$.comments", is("updated")))
        // 27: harvest 111 / PO&P 44 → crown 67 (recomputed server-side).
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].harvest", contains(111)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].pop", contains(44)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 27)].crown", contains(67)));
  }

  @Test
  @DisplayName("PUT with a changed Crown Timber volume propagates into Schedule 1 (WRN-001)")
  void crownVolumeChange_propagatesIntoSchedule1() throws Exception {
    String doc = getDoc(540);
    int rev = revisionOf(doc);
    int newCrown = ((Number) JsonPath.read(doc, "$.crownTimber.volume")).intValue() + 1000;
    String body = """
        { "revisionCount": %d, "overrideHarvestTotalPop": "N", "lineItems": [],
          "popTimberVolume": 5000, "crownTimberVolume": %d }
        """.formatted(rev, newCrown);
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warnings[0].key", is("crownVolumeChangeSchedule1")));
    // Schedule 1 (cat-1 summary 1041) item-12 VOLUME overwritten with the new crown; COST preserved.
    mockMvc.perform(get("/api/v1/schedule1").param("millId", "540").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].volume", contains(newCrown)))
        .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].cost", contains(50000)));
  }

  @Test
  @DisplayName("PUT with a changed Crown volume but no Schedule 1 → WRN-002 (not opened)")
  void crownVolumeChange_noSchedule1_warnsNotSet() throws Exception {
    String doc = getDoc(541);
    int rev = revisionOf(doc);
    int newCrown = ((Number) JsonPath.read(doc, "$.crownTimber.volume")).intValue() + 1000;
    String body = """
        { "revisionCount": %d, "overrideHarvestTotalPop": "N", "lineItems": [],
          "popTimberVolume": 5000, "crownTimberVolume": %d }
        """.formatted(rev, newCrown);
    mockMvc.perform(put(ENDPOINT).param("millId", "541").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warnings[0].key", is("crownVolumeNotSetSchedule1")));
  }

  @Test
  @DisplayName("DELETE removes the whole Schedule 3 family (SUC-002); GET then 404")
  void delete_removesFamily() throws Exception {
    mockMvc.perform(delete(ENDPOINT).param("millId", "542").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")));
    mockMvc.perform(get(ENDPOINT).param("millId", "542").param("year", "2021"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT out-of-range cost → 400 FLD-001 (nothing persisted)")
  void putCostOutOfRange_returns400() throws Exception {
    String body = """
        { "revisionCount": 0, "overrideHarvestTotalPop": "N",
          "lineItems": [ { "costItemCode": 27, "harvest": 100000000, "pop": 0 } ],
          "popTimberVolume": 5000, "crownTimberVolume": 5000 }
        """;
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("PUT out-of-range volume → 400 FLD-002")
  void putVolumeOutOfRange_returns400() throws Exception {
    String body = """
        { "revisionCount": 0, "overrideHarvestTotalPop": "N", "lineItems": [],
          "popTimberVolume": 5000, "crownTimberVolume": 10000000 }
        """;
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("PUT with a stale revisionCount → 409")
  void putStaleRevision_returns409() throws Exception {
    String body = """
        { "revisionCount": 999, "overrideHarvestTotalPop": "N", "lineItems": [],
          "popTimberVolume": 5000, "crownTimberVolume": 5000 }
        """;
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("PUT on a non-Draft schedule → 409")
  void putNonDraft_returns409() throws Exception {
    String body = """
        { "revisionCount": 0, "overrideHarvestTotalPop": "N", "lineItems": [],
          "popTimberVolume": 5000, "crownTimberVolume": 5000 }
        """;
    mockMvc.perform(put(ENDPOINT).param("millId", "517").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }
}
