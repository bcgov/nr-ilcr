package ca.bc.gov.nrs.ilcr.schedule4;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Acceptance test — Story 4.2. PUT (save create/edit/rename) + DELETE /api/v1/schedule4/locations.
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}, explicit because the merged runtime default
 * is fail-closed) so the mock {@code ILCR_SUBMITTER} principal holds EDIT_SCHEDULE — this isolates
 * the write-gate / family-write / concurrency / validation behaviour from authz (the 403 case lives
 * in {@link Schedule4WriteAuthorizationIT}, security ON).
 *
 * <p>Mutating tests use DEDICATED V8 mills so they never clobber the read-only Schedule 4 fixtures
 * (514/515/517): 540 "Existing Dump" (id 8001, distance child 8002) for edit-in-place / duplicate /
 * range / BR-04 / stale (all non-destructive to the family's name+existence), 541 for create, 542
 * (non-Draft) for the write gate, 544 for delete, 545 for rename. Cases read the revision at runtime
 * so they stay order-independent.
 */
@DisplayName("PUT/DELETE /api/v1/schedule4/locations — location write (Story 4.2)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule4WriteIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule4/locations";
  private static final String REPORT = "THE.TRANSPORTATION_REPORT";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Integer revisionOf(int reportId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + REPORT + " WHERE TRANSPORTATION_REPORT_ID = ?",
        Integer.class, reportId);
  }

  private int reports(String where) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, REPORT, where);
  }

  private int details(String where) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL, where);
  }

  private static String num(Object v) {
    return v == null ? "null" : v.toString();
  }

  /** One category JSON object: {@code {code, volume, cost, distance}} (distance quoted-decimal ok). */
  private static String cat(int code, Object volume, Object cost, Object distance) {
    return """
        {"code": %d, "volume": %s, "cost": %s, "distance": %s}"""
        .formatted(code, num(volume), num(cost), num(distance));
  }

  private static String body(Integer id, Integer revisionCount, String name, String... categories) {
    return """
        {
          "id": %s,
          "revisionCount": %s,
          "name": %s,
          "categories": [%s]
        }
        """.formatted(
            id == null ? "null" : id,
            revisionCount == null ? "null" : revisionCount,
            name == null ? "null" : ("\"" + name + "\""),
            String.join(",", categories));
  }

  // ---- create: primary (fixed) + distance child, revision 0 -> 1. --------------------------------

  @Test
  @DisplayName("create — 541 Draft, id null: primary + distance-child persist, echo recomputed doc")
  void put_create_insertsPrimaryAndDistanceChild() throws Exception {
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "541").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(null, null, "New Dump", cat(40, 1000, 50000, null),
                cat(47, 200, 8000, "60.0")))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        // The recomputed doc lists the created location (mill 541 accumulates create-test locations,
        // so assert by name-filter, not a positional index — the DB counts below are the tight check).
        .andExpect(jsonPath("$.locations[?(@.name=='New Dump')].name", contains("New Dump")));

    // A primary report (distance null) + one distance-child report (distance 60.0).
    assertEquals(2, reports("ILCR_MILL_ID = 541 AND ILCR_CATEGORY_ID = '4' "
        + "AND LOCATION_DESCRIPTION = 'New Dump'"), "primary + distance child persist");
    assertEquals(1, reports("ILCR_MILL_ID = 541 AND LOCATION_DESCRIPTION = 'New Dump' "
        + "AND DISTANCE = 60.0"), "the distance child carries its own distance");
    assertEquals(1, reports("ILCR_MILL_ID = 541 AND LOCATION_DESCRIPTION = 'New Dump' "
        + "AND DISTANCE IS NULL"), "the fixed primary has no distance");
  }

  @Test
  @DisplayName("create — 541 name-only location (empty categories) succeeds, primary only")
  void put_create_nameOnly() throws Exception {
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "541").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(null, null, "Bare Landing"))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals(1, reports("ILCR_MILL_ID = 541 AND LOCATION_DESCRIPTION = 'Bare Landing'"),
        "name-only location = one primary report");
    assertEquals(0, details("TRANSPORTATION_REPORT_ID IN "
        + "(SELECT TRANSPORTATION_REPORT_ID FROM " + REPORT
        + " WHERE ILCR_MILL_ID = 541 AND LOCATION_DESCRIPTION = 'Bare Landing')"),
        "no detail rows for a blank category grid");
  }

  @Test
  @DisplayName("create — 541 30-char name accepted at the boundary (S09)")
  void put_create_thirtyCharNameBoundary() throws Exception {
    String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"; // 30 chars
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "541").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(null, null, name))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    assertEquals(1, reports("ILCR_MILL_ID = 541 AND LOCATION_DESCRIPTION = '" + name + "'"));
  }

  // ---- edit-in-place: update fixed on primary + distance child (no rename). ----------------------

  @Test
  @DisplayName("edit — 540/8001 updates fixed on primary + distance child in place, bumps revision")
  void put_edit_updatesInPlace() throws Exception {
    int before = revisionOf(8001);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(8001, before, "Existing Dump", cat(40, 1500, 60000, null),
                cat(47, 250, 9000, "70.0")))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));

    assertEquals(before + 1, revisionOf(8001), "primary revision advances by one");
    assertEquals(1, details("TRANSPORTATION_REPORT_ID = 8001 AND ILCR_REPORT_COST_ITEM_ID = 40 "
        + "AND VOLUME = 1500 AND COST = 60000"), "fixed detail updated in place");
    assertEquals(1, reports("TRANSPORTATION_REPORT_ID = 8002 AND DISTANCE = 70.0"),
        "distance child distance updated");
    assertEquals(1, details("TRANSPORTATION_REPORT_ID = 8002 AND ILCR_REPORT_COST_ITEM_ID = 47 "
        + "AND VOLUME = 250 AND COST = 9000"), "distance detail updated in place");
  }

  // ---- rename: re-stamps the whole family. -------------------------------------------------------

  @Test
  @DisplayName("rename — 545/8040 re-stamps LOCATION_DESCRIPTION across the family")
  void put_rename_reStampsFamily() throws Exception {
    int before = revisionOf(8040);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "545").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(8040, before, "Renamed Landing", cat(40, 300, 9000, null)))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    assertEquals(1, reports("ILCR_MILL_ID = 545 AND LOCATION_DESCRIPTION = 'Renamed Landing'"),
        "family re-stamped to the new name");
    assertEquals(0, reports("ILCR_MILL_ID = 545 AND LOCATION_DESCRIPTION = 'Renamable Dump'"),
        "the old name is gone");
  }

  // ---- name validation: blank -> 400 ERR-001; duplicate (case-insensitive) -> 409 ERR-002. -------

  @Test
  @DisplayName("blank name -> 400 locationEmptyOrNull, nothing persisted")
  void put_blankName_returns400() throws Exception {
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(8001, revisionOf(8001), "   ", cat(40, 1, 1, null))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail",
            is("Location Name can not be empty. Please enter a description.")));
  }

  @Test
  @DisplayName("duplicate name (case-insensitive) -> 409 locationAlreadyExists, nothing changed")
  void put_duplicateName_returns409() throws Exception {
    int before = revisionOf(8001);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            // "rival dump" collides case-insensitively with the seeded "Rival Dump" (id 8003).
            .content(body(8001, before, "rival dump", cat(40, 1, 1, null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("Location Name already exists.")));
    assertEquals(before, revisionOf(8001), "a rejected save must not bump the revision");
    assertEquals(0, reports("ILCR_MILL_ID = 540 AND LOCATION_DESCRIPTION = 'rival dump'"),
        "nothing renamed on a duplicate-name rejection");
  }

  // ---- field ranges + BR-04 -> 400 verbatim, nothing persisted. ----------------------------------

  @Test
  @DisplayName("fixed volume > 9,999,999 -> 400 volumeValidatorErrorMsg")
  void put_volumeOver_returns400() throws Exception {
    expect400(body(8001, revisionOf(8001), "Existing Dump", cat(40, 10000000, 5000, null)),
        "Entered volume must be between 0 and 9,999,999.");
  }

  @Test
  @DisplayName("fixed cost > 99,999,999 -> 400 costValidatorErrorMsg")
  void put_costOver_returns400() throws Exception {
    expect400(body(8001, revisionOf(8001), "Existing Dump", cat(40, 5000, 100000000, null)),
        "Entered cost must be between -99,999,999 and 99,999,999.");
  }

  @Test
  @DisplayName("distance > 999,999.9 -> 400 distanceValidatorErrorMsg")
  void put_distanceOver_returns400() throws Exception {
    expect400(body(8001, revisionOf(8001), "Existing Dump", cat(47, 200, 8000, "1000000")),
        "Entered distance must be between 0 and 999,999.");
  }

  @Test
  @DisplayName("BR-04 — distance without volume/cost -> 400 Value Required")
  void put_distanceWithoutAmounts_returns400() throws Exception {
    expect400Contains(body(8001, revisionOf(8001), "Existing Dump", cat(47, null, null, "50.0")),
        "Value Required");
  }

  @Test
  @DisplayName("BR-04 — volume/cost without distance -> 400 Value Required")
  void put_amountsWithoutDistance_returns400() throws Exception {
    expect400Contains(body(8001, revisionOf(8001), "Existing Dump", cat(47, 200, 8000, null)),
        "Value Required");
  }

  private void expect400(String requestBody, String verbatimDetail) throws Exception {
    int before = revisionOf(8001);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is(verbatimDetail)));
    assertEquals(before, revisionOf(8001), "a rejected PUT must not persist anything");
  }

  private void expect400Contains(String requestBody, String fragment) throws Exception {
    int before = revisionOf(8001);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString(fragment)));
    assertEquals(before, revisionOf(8001), "a rejected PUT must not persist anything");
  }

  // ---- Draft gate + optimistic lock -> 409, no change. -------------------------------------------

  @Test
  @DisplayName("PUT against non-Draft (mill 542, track S) -> 409, no change")
  void put_nonDraft_returns409() throws Exception {
    int before = revisionOf(8010);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "542").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(8010, before, "Locked Dump", cat(42, 1, 1, null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(before, revisionOf(8010), "a gated PUT must not change data");
  }

  @Test
  @DisplayName("DELETE against non-Draft (mill 542, track S) -> 409, family survives")
  void delete_nonDraft_returns409() throws Exception {
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "542").param("year", "2021")
            .param("id", "8010"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(1, reports("TRANSPORTATION_REPORT_ID = 8010"), "gated DELETE keeps the row");
  }

  @Test
  @DisplayName("stale revisionCount -> 409, no overwrite")
  void put_staleRevision_returns409() throws Exception {
    int before = revisionOf(8001);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(8001, before + 99, "Existing Dump", cat(40, 1, 1, null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail",
            is("This schedule was changed by another user. Please reload and try again.")));
    assertEquals(before, revisionOf(8001), "the stale PUT must not overwrite");
  }

  // ---- DELETE: removes the whole family; idempotent on an unknown id. ----------------------------

  @Test
  @DisplayName("DELETE — 544/8030 removes the whole family + details; second DELETE is idempotent")
  void delete_removesFamily_idempotent() throws Exception {
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "544").param("year", "2021")
            .param("id", "8030"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
    assertEquals(0, reports("ILCR_MILL_ID = 544 AND LOCATION_DESCRIPTION = 'Deletable Dump'"),
        "primary + distance child both removed");
    assertEquals(0, details("TRANSPORTATION_REPORT_ID IN (8030, 8031)"),
        "all cascaded detail rows removed");
    // Idempotent: a second DELETE of the now-absent primary id still returns 200 (never 404).
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "544").param("year", "2021")
            .param("id", "8030"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
  }
}
