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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Story 12.2 acceptance — bridge write paths for {@code /api/v1/schedule7a/bridges} (AC1-AC6,
 * slices S01/S03/S04/S06-S15/S18/S19). All mutations target mill 515 (an active, Draft,
 * initially-empty mill) so they never disturb the seeded read/check-status fixtures on 514/517.
 * Security OFF.
 */
@DisplayName("Schedule 7A bridge writes (Story 12.2)")
class Schedule7aWriteIT extends AbstractOracleIT {

  private static final String BRIDGES = "/api/v1/schedule7a/bridges";
  private static final String MILL = "515";
  private static final String YEAR = "2021";

  @Autowired private JdbcTemplate jdbc;

  /**
   * Mill 515/2021 is the shared, initially-empty write fixture; the container is one-per-JVM with
   * no per-test rollback (see {@link AbstractOracleIT}). Remove everything these tests commit so
   * mill 515 is empty again for the next test AND for {@code
   * Schedule7aContextGuardIT.emptyList_returns200}, which asserts it empty — that guard must not
   * depend on Failsafe class order.
   */
  @AfterEach
  void cleanupMill515() {
    jdbc.update(
        "DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE BRIDGE_REPORT_ID IN "
            + "(SELECT BRIDGE_REPORT_ID FROM THE.BRIDGE_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7')");
    jdbc.update(
        "DELETE FROM THE.BRIDGE_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7'");
  }

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
        """
        .formatted(location, revisionCount);
  }

  private ResultActions postBridge(String body) throws Exception {
    return mockMvc.perform(
        post(BRIDGES)
            .param("millId", MILL)
            .param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /**
   * POST a valid bridge and return the newly-created id (the max bridge id in the echoed document).
   */
  private long addBridge(String location) throws Exception {
    String content =
        postBridge(validBody(location, 0))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
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
        .andExpect(
            jsonPath(
                "$.bridges[?(@.locationName == 'Add Test Bridge')].grandTotal", hasItem(12000)));
  }

  @Test
  @DisplayName("add rejects an out-of-range length -> 400 verbatim (S10)")
  void add_rejectsOutOfRangeLength() throws Exception {
    String body = validBody("Bad Length", 0).replace("\"length\": 20.0", "\"length\": 12000.0");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.detail",
                containsString("Entered bridge length must be between 0.0 and 9,999.9")));
  }

  @Test
  @DisplayName(
      "add rejects an out-of-range distance -> 400 verbatim legacy text (S12, bug preserved)")
  void add_rejectsOutOfRangeDistance() throws Exception {
    // Legacy parity: enforce 0-9,999 but show the legacy text "0.0 and 999.99" (recorded mismatch).
    String body = validBody("Bad Distance", 0).replace("\"distance\": 12", "\"distance\": 10000");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.detail",
                containsString("Entered bridge distance must be between 0.0 and 999.99")));
  }

  @Test
  @DisplayName("add rejects a malformed yyyy-MM date -> 400 verbatim (S07)")
  void add_rejectsBadDate() throws Exception {
    String body = validBody("Bad Date", 0).replace("\"2020-06\"", "\"2020-13\"");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.detail",
                containsString("The date is not valid. Enter date in format: YYYY-MM.")));
  }

  @Test
  @DisplayName("add rejects an unknown code value -> 400 (S15)")
  void add_rejectsUnknownCode() throws Exception {
    String body =
        validBody("Bad Code", 0)
            .replace("\"constructionTypeCode\": \"N\"", "\"constructionTypeCode\": \"ZZZ\"");
    postBridge(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("The selected code is not valid.")));
  }

  @Test
  @DisplayName("add rejects a missing required code -> 400 (S15)")
  void add_rejectsMissingRequiredCode() throws Exception {
    String body =
        validBody("Missing Code", 0)
            .replace("\"superstructureTypeCode\": \"STL\",", "\"superstructureTypeCode\": null,");
    postBridge(body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("write outside Draft (mill 517 Submitted) -> 409 (S18 write half)")
  void add_rejectedOutsideDraft() throws Exception {
    mockMvc
        .perform(
            post(BRIDGES)
                .param("millId", "517")
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("Nope", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("cannot be edited")));
  }

  @Test
  @DisplayName("correct a bridge -> recomputed totals + bumped revision (S03)")
  void update_correctsBridge() throws Exception {
    long id = addBridge("Correct Me");
    // Double the site-plan cost (1000 -> 2000) via a full-body PUT: grandTotal recomputed 12000 ->
    // 13000.
    String edit =
        validBody("Corrected", 0).replace("\"sitePlanCost\": 1000", "\"sitePlanCost\": 2000");
    mockMvc
        .perform(
            put(BRIDGES + "/" + id)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(
            jsonPath(
                "$.bridges[?(@.bridgeReportId == " + id + ")].locationName", hasItem("Corrected")))
        .andExpect(
            jsonPath(
                "$.bridges[?(@.bridgeReportId == " + id + ")].grandTotal",
                hasItem(13000))) // site plan 2000 (+1000) -> 12000 + 1000
        .andExpect(
            jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")].revisionCount", hasItem(1)));
  }

  @Test
  @DisplayName("stale revisionCount -> 409 (optimistic lock)")
  void update_staleRevision_conflict() throws Exception {
    long id = addBridge("Stale Target");
    mockMvc
        .perform(
            put(BRIDGES + "/" + id)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("Stale Edit", 99)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("unknown bridge id on update -> 404")
  void update_unknownId_notFound() throws Exception {
    mockMvc
        .perform(
            put(BRIDGES + "/888888")
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("Ghost", 0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("delete removes the bridge and its costs (S04)")
  void delete_removesBridge() throws Exception {
    long id = addBridge("Delete Me");
    mockMvc
        .perform(delete(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")]", is(empty())));
  }

  @Test
  @DisplayName("update a bridge id owned by another mill -> 404 (IDOR scope)")
  void update_foreignMillBridgeId_notFound() throws Exception {
    // Bridge 7601 belongs to mill 514; correcting it while scoped to mill 515 must not resolve.
    mockMvc
        .perform(
            put(BRIDGES + "/7601")
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("Cross Mill", 0)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("delete a bridge id owned by another mill -> 404 (IDOR scope)")
  void delete_foreignMillBridgeId_notFound() throws Exception {
    mockMvc
        .perform(delete(BRIDGES + "/7601").param("millId", MILL).param("year", YEAR))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("delete an unknown bridge id -> 404")
  void delete_unknownId_notFound() throws Exception {
    mockMvc
        .perform(delete(BRIDGES + "/888888").param("millId", MILL).param("year", YEAR))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("correct outside Draft (mill 517 Submitted) -> 409 (S18 write half)")
  void update_rejectedOutsideDraft() throws Exception {
    // 7651 is a real bridge on the Submitted mill 517; the Draft gate rejects before any write.
    mockMvc
        .perform(
            put(BRIDGES + "/7651")
                .param("millId", "517")
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("Nope", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("cannot be edited")));
  }

  @Test
  @DisplayName("delete outside Draft (mill 517 Submitted) -> 409")
  void delete_rejectedOutsideDraft() throws Exception {
    mockMvc
        .perform(delete(BRIDGES + "/7651").param("millId", "517").param("year", YEAR))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString("cannot be edited")));
  }

  @Test
  @DisplayName("correcting a cost to null NULLS its row in place and drops the total (S03)")
  void update_clearsCostToNull_dropsTotal() throws Exception {
    long id = addBridge("Clear Cost");
    // Null out otherCost (item 73, 100): grandTotal 12000 -> 11900.
    String edit = validBody("Cleared", 0).replace("\"otherCost\": 100", "\"otherCost\": null");
    mockMvc
        .perform(
            put(BRIDGES + "/" + id)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.bridges[?(@.bridgeReportId == " + id + ")].grandTotal", hasItem(11900)));
    // The row SURVIVES with COST NULL — it is not deleted. The legacy app shares this database and
    // can only update cost rows that already exist (Schedule7aDAO's update branch has no insert),
    // so
    // removing the row would make that cost permanently uneditable from the legacy screen.
    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE BRIDGE_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 73",
            Integer.class,
            id);
    org.junit.jupiter.api.Assertions.assertEquals(1, rows.intValue());
    Integer cost =
        jdbc.queryForObject(
            "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE BRIDGE_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 73",
            Integer.class,
            id);
    org.assertj.core.api.Assertions.assertThat(cost).isNull();
  }

  @Test
  @DisplayName("a bridge added with costs omitted still gets all ten rows (legacy-editable shape)")
  void add_writesAllTenRowsEvenWhenCostsAreOmitted() throws Exception {
    String body =
        validBody("Sparse Costs", 0)
            .replace("\"sitePlanCost\": 1000", "\"sitePlanCost\": null")
            .replace("\"otherCost\": 100", "\"otherCost\": null")
            .replace("\"approachCost\": 700", "\"approachCost\": null");
    String content =
        postBridge(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    List<Integer> ids = JsonPath.read(content, "$.bridges[*].bridgeReportId");
    long id = ids.stream().mapToLong(Integer::longValue).max().orElseThrow();

    // Ten rows regardless of how many costs were entered — the shape legacy's add path produces and
    // its update path depends on.
    Integer rows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE BRIDGE_REPORT_ID = ?",
            Integer.class,
            id);
    org.junit.jupiter.api.Assertions.assertEquals(10, rows.intValue());
    Integer nulls =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE BRIDGE_REPORT_ID = ? AND COST IS NULL",
            Integer.class,
            id);
    org.junit.jupiter.api.Assertions.assertEquals(3, nulls.intValue());
  }

  @Test
  @DisplayName("delete removes the bridge's ten cost children from the table (S04 cascade)")
  void delete_removesCostChildren() throws Exception {
    long id = addBridge("Cascade");
    Integer before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE BRIDGE_REPORT_ID = ?",
            Integer.class,
            id);
    org.junit.jupiter.api.Assertions.assertEquals(10, before.intValue());
    mockMvc
        .perform(delete(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR))
        .andExpect(status().isOk());
    Integer after =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE BRIDGE_REPORT_ID = ?",
            Integer.class,
            id);
    org.junit.jupiter.api.Assertions.assertEquals(0, after.intValue());
  }

  @Test
  @DisplayName("delete the last bridge -> SUC-003 empty-schedule message")
  void delete_lastBridge_emptyScheduleMessage() throws Exception {
    long id = addBridge("Last One");
    mockMvc
        .perform(delete(BRIDGES + "/" + id).param("millId", MILL).param("year", YEAR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bridges", is(empty())))
        .andExpect(jsonPath("$.message.key", is("anyDataToSaveInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Any data was saved. The Schedule is empty.")));
  }

  @Test
  @DisplayName("delete with bridges remaining -> SUC-002 deleted message")
  void delete_withRemaining_deletedMessage() throws Exception {
    long keep = addBridge("Keep");
    long remove = addBridge("Remove");
    mockMvc
        .perform(delete(BRIDGES + "/" + remove).param("millId", MILL).param("year", YEAR))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.bridges[?(@.bridgeReportId == " + keep + ")].locationName", hasItem("Keep")))
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")));
  }

  // ===============================================================================================
  // Page-level Save — the legacy Schedule7aMB.save(), which persisted the WHOLE schedule at once.
  // ===============================================================================================

  private ResultActions saveAll(String body) throws Exception {
    return mockMvc.perform(
        put(BRIDGES)
            .param("millId", MILL)
            .param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  /** A save-all body pairing each id with a renamed copy of the valid body at revision 0. */
  private static String saveAllBody(
      long firstId, String firstName, long secondId, String secondName) {
    return """
        {"bridges": [
          {"bridgeReportId": %d, "bridge": %s},
          {"bridgeReportId": %d, "bridge": %s}
        ]}
        """
        .formatted(firstId, validBody(firstName, 0), secondId, validBody(secondName, 0));
  }

  @Test
  @DisplayName("save-all persists EVERY bridge in one request and echoes SUC-001")
  void saveAll_persistsEveryBridge() throws Exception {
    long first = addBridge("Batch One");
    long second = addBridge("Batch Two");

    saveAll(saveAllBody(first, "Batch One Renamed", second, "Batch Two Renamed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(
            jsonPath(
                "$.bridges[?(@.bridgeReportId == " + first + ")].locationName",
                hasItem("Batch One Renamed")))
        .andExpect(
            jsonPath(
                "$.bridges[?(@.bridgeReportId == " + second + ")].locationName",
                hasItem("Batch Two Renamed")));
  }

  @Test
  @DisplayName("save-all is ATOMIC: a stale entry rolls back the rows that had already applied")
  void saveAll_staleEntryRollsBackTheWholeBatch() throws Exception {
    long first = addBridge("Atomic One");
    long second = addBridge("Atomic Two");

    // Entry 1 is valid; entry 2 carries a stale revision (rows are at 0 after the add).
    String body =
        """
        {"bridges": [
          {"bridgeReportId": %d, "bridge": %s},
          {"bridgeReportId": %d, "bridge": %s}
        ]}
        """
            .formatted(
                first,
                validBody("Atomic One Renamed", 0),
                second,
                validBody("Atomic Two Renamed", 99));

    saveAll(body).andExpect(status().isConflict());

    // The first row must NOT have been renamed — a partial save would leave the reporter unable to
    // tell which rows landed, which is exactly what the single transaction prevents.
    String name =
        jdbc.queryForObject(
            "SELECT LOCATION_NAME FROM THE.BRIDGE_REPORT WHERE BRIDGE_REPORT_ID = ?",
            String.class,
            first);
    org.assertj.core.api.Assertions.assertThat(name).isEqualTo("Atomic One");
  }

  @Test
  @DisplayName("save-all rejects an unknown bridge id -> 404")
  void saveAll_unknownId() throws Exception {
    long real = addBridge("Real");
    String body =
        """
        {"bridges": [
          {"bridgeReportId": %d, "bridge": %s},
          {"bridgeReportId": 999999, "bridge": %s}
        ]}
        """
            .formatted(real, validBody("Real", 0), validBody("Ghost", 0));

    saveAll(body).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("save-all rejects an empty bridge list -> 400")
  void saveAll_emptyList() throws Exception {
    saveAll("{\"bridges\": []}").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("save-all is Draft-gated like every other write -> 409 on a submitted track")
  void saveAll_rejectedOutsideDraft() throws Exception {
    mockMvc
        .perform(
            put(BRIDGES)
                .param("millId", "517")
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"bridges\": [{\"bridgeReportId\": 1, \"bridge\": "
                        + validBody("X", 0)
                        + "}]}"))
        .andExpect(status().isConflict());
  }

  // ===============================================================================================
  // Year-scoped code lists — legacy LookupCache.getCacheList(year) kept only the codes effective on
  // January 1 of the reporting year. 'X' and 'LX' are seeded expired (2015) in V27.
  // ===============================================================================================

  @Test
  @DisplayName("a code with NULL effective/expiry dates is ACCEPTED on write (no bound = always)")
  void write_acceptsCodeWithNullDateBounds() throws Exception {
    // 'OPEN' carries NULL EFFECTIVE_DATE and EXPIRY_DATE. If the filter did not NVL them the row
    // would vanish from the code set and this save would 400 — which is exactly how an existing
    // bridge holding such a code would become unsaveable.
    String body =
        validBody("Null Bounded Code", 0)
            .replace("\"abutmentTypeCode\": \"CONC\"", "\"abutmentTypeCode\": \"OPEN\"");
    postBridge(body).andExpect(status().isOk());
  }

  @Test
  @DisplayName("save-all rejects a duplicate bridge id -> 400 (not a misleading 409)")
  void saveAll_duplicateId() throws Exception {
    long id = addBridge("Duplicated");
    String body =
        """
        {"bridges": [
          {"bridgeReportId": %d, "bridge": %s},
          {"bridgeReportId": %d, "bridge": %s}
        ]}
        """
            .formatted(id, validBody("Duplicated", 0), id, validBody("Duplicated Again", 0));

    saveAll(body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a code that expired before the reporting year is REJECTED on write (400)")
  void write_rejectsCodeExpiredBeforeReportingYear() throws Exception {
    String body =
        validBody("Expired Code", 0)
            .replace("\"constructionTypeCode\": \"N\"", "\"constructionTypeCode\": \"X\"");
    postBridge(body).andExpect(status().isBadRequest());

    String loadBody =
        validBody("Expired Rating", 0)
            .replace("\"loadRatingCode\": \"L100\"", "\"loadRatingCode\": \"LX\"");
    postBridge(loadBody).andExpect(status().isBadRequest());
  }
}
