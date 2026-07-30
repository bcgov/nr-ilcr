package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Story 12.2 acceptance — bridge write paths for {@code /api/v1/schedule7a/bridges} (AC1-AC6, slices
 * S01/S03/S04/S06-S15/S18/S19). All mutations target mill 515 (an active, Draft, initially-empty
 * mill) so they never disturb the seeded read/check-status fixtures on 514/517. Security OFF.
 */
@DisplayName("Schedule 7A bridge writes (Story 12.2)")
class Schedule7aWriteIT extends AbstractOracleIT {

  private static final String BRIDGES = "/api/v1/schedule7a/bridges";
  private static final String MILL = "515";
  private static final String YEAR = "2021";

  /** A valid bridge body (grandTotal 12000) — {@code {loc}} substituted for a per-test name. */
  private static String validBody(String location, int revisionCount) {
    return """
        {
          "locationName": "%s",
          "builtDate": "2020-06",
          "constructionTypeCode": "N",
          "superstructureTypeCode": "STL",
          "deckTypeCode": "WD",
          "abutmentTypeCode": "CONC",
          "loadRatingCode": "L100",
          "lifeSpan": 50,
          "abutmentHeight": 5.0,
          "length": 20.0,
          "width": 4.0,
          "distance": 12,
          "sitePlanCost": 1000,
          "superstructureMaterialCost": 5000,
          "superstructureDeliverCost": 500,
          "superstructureInstallCost": 800,
          "abutmentMaterialCost": 3000,
          "abutmentDeliverCost": 300,
          "abutmentInstallCost": 400,
          "approachCost": 700,
          "afterInstallCost": 200,
          "otherCost": 100,
          "comments": null,
          "revisionCount": %d
        }
        """.formatted(location, revisionCount);
  }

  private ResultActions postBridge(String body) throws Exception {
    return mockMvc.perform(post(BRIDGES).param("millId", MILL).param("year", YEAR)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  /** POST a valid bridge and return the newly-created id (the max bridge id in the echoed document). */
  private long addBridge(String location) throws Exception {
    String content = postBridge(validBody(location, 0))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    List<Integer> ids = JsonPath.read(content, "$.bridges[*].bridgeReportId");
    return ids.stream().mapToLong(Integer::longValue).max().orElseThrow();
  }

  @Test
  @DisplayName("add persists a bridge, recomputes totals, echoes SUC-001 (S01)")
  void add_persistsAndComputesTotals() throws Exception {
    postBridge(validBody("Add Test Bridge", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.bridges[?(@.locationName == 'Add Test Bridge')].grandTotal",
            hasItem(12000)));
  }

  @Test
  @DisplayName("add rejects an out-of-range length -> 400 verbatim (S10)")
  void add_rejectsOutOfRangeLength() throws Exception {
    String body = validBody("Bad Length", 0).replace("\"length\": 20.0", "\"length\": 12000.0");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Entered bridge length must be between 0.0 and 9,999.9")));
  }

  @Test
  @DisplayName("add rejects an out-of-range distance -> 400 verbatim legacy text (S12, bug preserved)")
  void add_rejectsOutOfRangeDistance() throws Exception {
    // Legacy parity: enforce 0-9,999 but show the legacy text "0.0 and 999.99" (recorded mismatch).
    String body = validBody("Bad Distance", 0).replace("\"distance\": 12", "\"distance\": 10000");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Entered bridge distance must be between 0.0 and 999.99")));
  }

  @Test
  @DisplayName("add rejects a malformed yyyy-MM date -> 400 verbatim (S07)")
  void add_rejectsBadDate() throws Exception {
    String body = validBody("Bad Date", 0).replace("\"2020-06\"", "\"2020-13\"");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("The date is not valid. Enter date in format: YYYY-MM.")));
  }

  @Test
  @DisplayName("add rejects an unknown code value -> 400 (S15)")
  void add_rejectsUnknownCode() throws Exception {
    String body = validBody("Bad Code", 0)
        .replace("\"constructionTypeCode\": \"N\"", "\"constructionTypeCode\": \"ZZZ\"");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("The selected code is not valid.")));
  }

  @Test
  @DisplayName("add rejects a missing required code -> 400 (S15)")
  void add_rejectsMissingRequiredCode() throws Exception {
    String body = validBody("Missing Code", 0)
        .replace("\"superstructureTypeCode\": \"STL\",", "\"superstructureTypeCode\": null,");
    postBridge(body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("write outside Draft (mill 517 Submitted) -> 409 (S18 write half)")
  void add_rejectedOutsideDraft() throws Exception {
    mockMvc.perform(post(BRIDGES).param("millId", "517").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody("Nope", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("cannot be edited")));
  }

  @Test
  @DisplayName("correct a bridge -> recomputed totals + bumped revision (S03)")
  void update_correctsBridge() throws Exception {
    long id = addBridge("Correct Me");
    // Halve every cost by editing the body: new grandTotal computed from the new costs.
    String edit = validBody("Corrected", 0)
        .replace("\"sitePlanCost\": 1000", "\"sitePlanCost\": 2000");
    mockMvc.perform(put(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(edit))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")].locationName",
            hasItem("Corrected")))
        .andExpect(jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")].grandTotal",
            hasItem(13000)))       // site plan 2000 (+1000) -> 12000 + 1000
        .andExpect(jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")].revisionCount",
            hasItem(1)));
  }

  @Test
  @DisplayName("stale revisionCount -> 409 (optimistic lock)")
  void update_staleRevision_conflict() throws Exception {
    long id = addBridge("Stale Target");
    mockMvc.perform(put(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody("Stale Edit", 99)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("unknown bridge id on update -> 404")
  void update_unknownId_notFound() throws Exception {
    mockMvc.perform(put(BRIDGES + "/888888").param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody("Ghost", 0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("delete removes the bridge and its costs (S04)")
  void delete_removesBridge() throws Exception {
    long id = addBridge("Delete Me");
    mockMvc.perform(delete(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")]", is(empty())));
  }
}
