package ca.bc.gov.nrs.ilcr.schedule11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 26.2 AC 7: the original-value indicators read the Licensee's submission
 * from the two snapshot views ({@code BASIC_SILVICULTURE_REPORT_S_VW}, {@code
 * ILCR_COST_REPORT_DETAIL_S_VW}) against Oracle — the first test in the repo to do so; until now
 * the snapshot path was proven only with a mocked repository.
 *
 * <p><strong>What this can and cannot prove.</strong> The test schema reproduces no audit trigger,
 * so the {@code 'S'} rows are seeded ({@code R__58}) exactly as the delivery trigger would have
 * left them at submission. This class proves the READ: the view SQL, the per-detail-id cost key,
 * the BEC label, and the empty entry for a location with no snapshot. That the baseline survives
 * later corrections rests on the trigger mapping plus the submit order (26.1) and on the
 * application never writing an audit row — the last of which {@code Schedule11CorrectionIT} pins.
 * Nothing here writes; mill 801 is read-only and 802's asserted rows are ones no other test changes
 * the originals of.
 *
 * <p><strong>At Verified</strong> ({@code R__61}'s 816, never written) the same views serve the
 * same baseline: the Licensee's {@code 'S'} submission, not the value in effect when the report was
 * verified. The delivery trigger stamps a save at {@code V} as {@code 'V'} and the verify touch as
 * {@code 'V'}, so nothing at Verified can re-baseline; this class proves the read ranks only {@code
 * 'S'} rows, for either role. 817's baseline after two late corrections is read inside {@code
 * Schedule11LateCorrectionIT}'s happy path, the one test that writes it.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("Schedule 11 — original values from the submitted snapshot, against Oracle (26.2)")
class Schedule11OriginalValuesIT extends AbstractOracleIT {

  private static final String LABEL = "Original Submission Value: ";
  private final ObjectMapper mapper = new ObjectMapper();

  private JsonNode location(long mill, long id) throws Exception {
    return location(mill, id, null);
  }

  private JsonNode location(long mill, long id, String groups) throws Exception {
    MockHttpServletRequestBuilder request =
        get("/api/v1/schedule11")
            .param("millId", String.valueOf(mill))
            .param("year", "2021")
            .accept(MediaType.APPLICATION_JSON);
    if (groups != null) {
      request.header("X-Mock-Groups", groups);
    }
    JsonNode doc =
        mapper.readTree(
            mockMvc
                .perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode loc : doc.get("locations")) {
      if (loc.get("locationId").asLong() == id) {
        return loc;
      }
    }
    throw new IllegalStateException("location " + id + " not served");
  }

  private static void assertOriginal(JsonNode loc, String key, String value, String shown) {
    assertThat(loc.at("/originalValues/" + key + "/value").asText()).as(key).isEqualTo(value);
    assertThat(loc.at("/originalValues/" + key + "/tooltip").asText())
        .as(key)
        .isEqualTo(LABEL + shown);
  }

  @Test
  @DisplayName(
      "every tracked field is read from the views: the Licensee's values, formatted as legacy did")
  void submittedValuesAreReadFromTheViews() throws Exception {
    JsonNode loc = location(801, 9421);

    // Current values differ on all five, so every indicator has something to show.
    assertThat(loc.get("location").asText()).isEqualTo("Corrected North");
    assertOriginal(loc, "location", "Submitted North", "Submitted North");
    // Compared by id, SHOWN by label (legacy schedule11.xhtml:251) — never the catalogue number.
    assertOriginal(loc, "biogeoclimaticCatalogueId", "8802", "CWHvm");
    assertOriginal(loc, "netArea", "10", "10.0");
    assertOriginal(loc, "actualCost", "20000", "20,000");
    assertOriginal(loc, "plannedCost", "14000", "14,000");
    // Enhanced and Comments carry no indicator (16.2 D5; 26.2 D3).
    assertThat(loc.get("originalValues").has("enhancedIndicator")).isFalse();
    assertThat(loc.get("originalValues").has("comments")).isFalse();
  }

  @Test
  @DisplayName("a location with no snapshot (added since submission) has every field on file empty")
  void noSnapshot_everyTrackedFieldIsEmpty() throws Exception {
    JsonNode loc = location(801, 9422);

    for (String key :
        List.of("location", "biogeoclimaticCatalogueId", "netArea", "actualCost", "plannedCost")) {
      assertOriginal(loc, key, "", "");
    }
  }

  @Test
  @DisplayName(
      "a submitted BEC with no catalogue row still serves the snapshot, the BEC shown by its id")
  void danglingSubmittedBec_keepsTheSnapshot_andShowsTheId() throws Exception {
    // The snapshot query LEFT JOINs the catalogue. Under an inner join 9430's snapshot row would
    // vanish and every field would read "added since submission" on a row the Licensee filed.
    JsonNode loc = location(801, 9430);

    assertOriginal(loc, "location", "Dangling Submitted", "Dangling Submitted");
    assertOriginal(loc, "biogeoclimaticCatalogueId", "8899", "8899");
    assertOriginal(loc, "netArea", "6", "6.0");
  }

  @Test
  @DisplayName(
      "a cost's original is the snapshot of its OWN detail id — not an older row's for the same item")
  void costOriginalIsKeyedByTheCurrentDetailId() throws Exception {
    // 9423's Actual cost is detail 5833 ('S' snapshot 900). An OLDER detail 5899 for the same
    // location and item also has an 'S' row, with a HIGHER audit id, and 777.
    assertOriginal(location(802, 9423), "actualCost", "900", "900");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"ILCR_ADMIN", "ILCR_SUBMITTER"})
  @DisplayName("at Verified, every tracked field is still read from the Licensee's submission")
  void atVerified_theBaselineIsTheSubmission(String groups) throws Exception {
    JsonNode loc = location(816, 9441, groups);

    assertThat(loc.get("location").asText()).isEqualTo("Late Corrected North");
    assertOriginal(loc, "location", "Late Submitted North", "Late Submitted North");
    assertOriginal(loc, "biogeoclimaticCatalogueId", "8802", "CWHvm");
    assertOriginal(loc, "netArea", "10", "10.0");
    assertOriginal(loc, "actualCost", "20000", "20,000");
    assertOriginal(loc, "plannedCost", "14000", "14,000");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"ILCR_ADMIN", "ILCR_SUBMITTER"})
  @DisplayName(
      "at Verified, a row the ministry corrected at S still flags against the Licensee's value —"
          + " its later 'A' and 'V' audit rows never become the baseline")
  void atVerified_aCorrectionMadeAtSubmittedStillFlags(String groups) throws Exception {
    // 9442's 'A' (the admin save at S) and 'V' (the verify touch) rows carry the ministry's
    // values under HIGHER audit ids; ranking before filtering on 'S' would serve them and every
    // indicator would fall silent.
    JsonNode loc = location(816, 9442, groups);

    assertThat(loc.get("location").asText()).isEqualTo("Ministry Fixed At S");
    assertOriginal(loc, "location", "Licensee Filed", "Licensee Filed");
    assertOriginal(loc, "biogeoclimaticCatalogueId", "8801", "ICHdw1");
    assertOriginal(loc, "netArea", "6", "6.0");
    assertOriginal(loc, "actualCost", "650", "650");
    assertOriginal(loc, "plannedCost", "800", "800");
  }
}
