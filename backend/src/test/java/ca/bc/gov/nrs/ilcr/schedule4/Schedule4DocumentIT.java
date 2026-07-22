package ca.bc.gov.nrs.ilcr.schedule4;

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
 * Acceptance test — Schedule 4 read (AD-5, AD-10, AD-12). GET /api/v1/schedule4 location list.
 *
 * <p>Security OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * applies, isolating document assembly from authz (covered by {@link Schedule4AuthorizationIT}).
 * Asserts the pinned wire contract and the server-computed perUnit against the V7 seed.
 */
@DisplayName("GET /api/v1/schedule4 — location list (Schedule 4 read)")
class Schedule4DocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule4";

  @Test
  @DisplayName("514/2021 Draft — two locations, category grid with server-computed perUnit, ordered")
  void draftContext_listsLocationsWithCategories() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "514")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.year", is(2021)))
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.locations.length()", is(2)))
        // Location "Harbour Dump" — a family of reports (7001 primary + 7011/7012) collapsed to one.
        .andExpect(jsonPath("$.locations[0].name", is("Harbour Dump")))
        .andExpect(jsonPath("$.locations[0].categories.length()", is(4)))
        // 40 Lakeside Dry Dump (FIXED, primary report): vol 2000 / cost 100000 / perUnit 50.0, no distance.
        .andExpect(jsonPath("$.locations[0].categories[0].code", is(40)))
        .andExpect(jsonPath("$.locations[0].categories[0].kind", is("FIXED")))
        .andExpect(jsonPath("$.locations[0].categories[0].volume", is(2000)))
        .andExpect(jsonPath("$.locations[0].categories[0].cost", is(100000)))
        .andExpect(jsonPath("$.locations[0].categories[0].perUnit", is(50.0)))
        .andExpect(jsonPath("$.locations[0].categories[0].distance").doesNotExist())
        // 47 Truck Barge/Ferry (DISTANCE): its OWN report's distance 120.5; vol 500 / cost 25000 / 50.0.
        .andExpect(jsonPath("$.locations[0].categories[2].code", is(47)))
        .andExpect(jsonPath("$.locations[0].categories[2].kind", is("DISTANCE")))
        .andExpect(jsonPath("$.locations[0].categories[2].distance", is(120.5)))
        .andExpect(jsonPath("$.locations[0].categories[2].perUnit", is(50.0)))
        // 52 Rail Haul (DISTANCE): its OWN report's distance 88.0 — DIFFERENT from 47's 120.5, proving
        // per-category distance. vol 300 / cost null (missing) -> perUnit omitted, volume shown.
        .andExpect(jsonPath("$.locations[0].categories[3].code", is(52)))
        .andExpect(jsonPath("$.locations[0].categories[3].kind", is("DISTANCE")))
        .andExpect(jsonPath("$.locations[0].categories[3].distance", is(88.0)))
        .andExpect(jsonPath("$.locations[0].categories[3].volume", is(300)))
        .andExpect(jsonPath("$.locations[0].categories[3].cost").doesNotExist())
        .andExpect(jsonPath("$.locations[0].categories[3].perUnit").doesNotExist())
        // Location "Empty Landing": name-only, empty category list.
        .andExpect(jsonPath("$.locations[1].name", is("Empty Landing")))
        .andExpect(jsonPath("$.locations[1].categories.length()", is(0)));
  }

  @Test
  @DisplayName("514/2021 — deferred sub-page code (43 Towing) is filtered out of the grid")
  void deferredCodesFilteredOut() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "514")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // Location A has exactly the 4 in-scope codes seeded (40,41,47,52) — never 43.
        .andExpect(jsonPath("$.locations[0].categories[?(@.code == 43)]").isEmpty());
  }

  @Test
  @DisplayName("517/2021 non-Draft — trackStatus S, editable false, location still listed")
  void nonDraftContext_notEditable_locationStillListed() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "517")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(false)))
        .andExpect(jsonPath("$.locations.length()", is(1)))
        .andExpect(jsonPath("$.locations[0].name", is("Submitted Dump")))
        .andExpect(jsonPath("$.locations[0].categories[0].code", is(42)))
        .andExpect(jsonPath("$.locations[0].categories[0].perUnit", is(20.0)));
  }

  @Test
  @DisplayName("515/2021 valid active Draft, no locations — 200 empty list, NOT 404")
  void noLocations_returnsEmptyList() throws Exception {
    mockMvc.perform(get(ENDPOINT)
            .param("millId", "515")
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("D")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.locations.length()", is(0)));
  }
}
