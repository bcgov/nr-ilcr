package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 9.2 acceptance — {@code POST}/{@code PUT}/{@code DELETE /api/v1/schedule9/records}
 * (AC1/AC2/AC5/AC6; slices S01/S02/S07/S10). Security-off is pinned explicitly and every mutation
 * carries {@code .with(csrf())} — the recorded merge-regression guard.
 *
 * <p><strong>Order independence.</strong> A context is (mill, YEAR); mill 690 carries one destructive
 * concern per Draft year (2016 add, 2017 edit, 2018 delete, 2019 lock), and mill 691 is the non-Draft
 * 409. Every edit reads the current {@code revisionCount} from a GET first, never a hard-coded token,
 * and each write is followed by a FRESH {@code GET} plus a JDBC probe — the echoed document comes from
 * the same in-transaction builder and could agree with a write that never committed.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST/PUT/DELETE /api/v1/schedule9/records — Schedule 9 writes (Story 9.2)")
class Schedule9WriteIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule9/records";
  private static final String DOCUMENT = "/api/v1/schedule9";
  private static final long MILL = 690L;

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  /**
   * A complete record body — item 108 (no conditional fields active), unit M3, source A, BEC BZ1.
   * {@code revisionCount} is appended only when non-null (ignored on create, required on update).
   */
  private static String body(
      String contractor, int item, Integer cost, String units, Integer sideSlope,
      Integer revisionCount) {
    return """
        {
          "contractorId": "%s",
          "contractualItemCode": %d,
          "unitCode": "M3",
          "numberOfUnits": %s,
          "biogeoclimaticZone": "BZ1",
          "cost": %s,
          "sideSlopePct": %s,
          "sourceCode": "A"%s
        }
        """.formatted(contractor, item, units, str(cost), str(sideSlope),
        revisionCount == null ? "" : ",\n  \"revisionCount\": " + revisionCount);
  }

  private static String str(Integer value) {
    return value == null ? "null" : value.toString();
  }

  private String documentJson(int year) throws Exception {
    return mockMvc.perform(get(DOCUMENT).param("millId", "690").param("year", String.valueOf(year)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private JsonNode recordByContractor(int year, String contractor) throws Exception {
    for (JsonNode record : mapper.readTree(documentJson(year)).path("records")) {
      if (contractor.equals(record.path("contractorId").asText())) {
        return record;
      }
    }
    throw new AssertionError("record for contractor '" + contractor + "' not served in " + year);
  }

  private Map<String, Object> masterRow(int recordId) {
    return jdbc().queryForMap("""
        SELECT ILCR_CATEGORY_ID, REPORT_YEAR, ILCR_MILL_ID, CONTRACTOR_ID, PERFORMED_UNIT,
               SIDE_SLOPE_PCT, ILCR_UNIT_CODE, BEC_ZONE_CODE, ILCR_CONTRACTUAL_SOURCE_CODE,
               REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
          FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = ?
        """, recordId);
  }

  private List<Map<String, Object>> costLines(int recordId) {
    return jdbc().queryForList("""
        SELECT ILCR_REPORT_COST_ITEM_ID, COST, ITEM_DESCRIPTION, REVISION_COUNT,
               ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
          FROM THE.ILCR_COST_REPORT_DETAIL WHERE CONTRACTUAL_WORK_REPORT_ID = ?
        """, recordId);
  }

  // ---- AC1: create (S01) -----------------------------------------------------------------------

  @Test
  @DisplayName("S01: POST -> 200 saved, one master + one cost line, all audit columns stamped")
  void addRecord_persistsMasterAndCostLine() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "690").param("year", "2016")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("ADD-ONE", 108, 5000, "12.5", null, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", org.hamcrest.Matchers.is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", org.hamcrest.Matchers.is("Data saved successfully")))
        .andExpect(jsonPath("$.editable", org.hamcrest.Matchers.is(true)))
        .andExpect(jsonPath("$.trackStatus", org.hamcrest.Matchers.is("D")));

    JsonNode served = recordByContractor(2016, "ADD-ONE");
    int recordId = served.path("id").asInt();
    // $/Unit derived server-side: 5000 / 12.5 = 400.00 (compared numerically — a plain ObjectMapper
    // reads the JSON number as a double, so asText() would normalise the scale).
    assertEquals(400.00, served.path("costPerUnit").asDouble(), 0.0001);
    assertEquals("108", served.path("contractualItem").path("code").asText());

    Map<String, Object> master = masterRow(recordId);
    assertEquals("9", master.get("ILCR_CATEGORY_ID"));
    assertEquals(0, ((Number) master.get("REVISION_COUNT")).intValue());
    // AC6 audit columns — asserted PER COLUMN on the master, and the app (not the seed) wrote them.
    assertNotNull(master.get("ENTRY_USERID"));
    assertNotEquals("SEED", master.get("ENTRY_USERID"));
    assertNotNull(master.get("ENTRY_TIMESTAMP"));
    assertNotNull(master.get("UPDATE_USERID"));
    assertNotNull(master.get("UPDATE_TIMESTAMP"));

    List<Map<String, Object>> lines = costLines(recordId);
    assertEquals(1, lines.size(), "exactly one keyed cost line");
    Map<String, Object> line = lines.get(0);
    assertEquals(108, ((Number) line.get("ILCR_REPORT_COST_ITEM_ID")).intValue());
    assertEquals(5000, ((Number) line.get("COST")).intValue());
    // AC6 audit columns on the cost line too (the Schedule 1/2/4 bug is systemic).
    assertNotNull(line.get("ENTRY_USERID"));
    assertNotNull(line.get("ENTRY_TIMESTAMP"));
    assertNotNull(line.get("UPDATE_USERID"));
    assertNotNull(line.get("UPDATE_TIMESTAMP"));
  }

  @Test
  @DisplayName("S02: two adds in one session -> both records served")
  void addRecord_multipleInSession() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "690").param("year", "2016")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("ADD-A", 108, 1000, "1.0", null, null)))
        .andExpect(status().isOk());
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "690").param("year", "2016")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("ADD-B", 109, 2000, "2.0", null, null)))
        .andExpect(status().isOk());

    assertNotNull(recordByContractor(2016, "ADD-A"));
    assertNotNull(recordByContractor(2016, "ADD-B"));
  }

  // ---- AC1/AC5: edit in place (S07) ------------------------------------------------------------

  @Test
  @DisplayName("S07: PUT -> 200 saved, values updated, REVISION_COUNT bumped, ENTRY_* preserved")
  void updateRecord_editsInPlaceAndBumpsRevision() throws Exception {
    JsonNode before = recordByContractor(2017, "CTR-EDIT");
    int recordId = before.path("id").asInt();
    int token = before.path("revisionCount").asInt();
    Object entryUser = masterRow(recordId).get("ENTRY_USERID");

    mockMvc.perform(put(RECORDS + "/" + recordId).with(csrf())
            .param("millId", "690").param("year", "2017")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR-EDIT", 108, 7777, "20.0", null, token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", org.hamcrest.Matchers.is("dataSavedSuccesfullyInfoMsg")));

    Map<String, Object> master = masterRow(recordId);
    assertEquals(token + 1, ((Number) master.get("REVISION_COUNT")).intValue());
    assertEquals(entryUser, master.get("ENTRY_USERID"), "ENTRY_USERID survives an edit");
    assertEquals(7777, costLines(recordId).get(0).get("COST") == null ? null
        : ((Number) costLines(recordId).get(0).get("COST")).intValue());
  }

  // ---- AC5: delete (S10) -----------------------------------------------------------------------

  @Test
  @DisplayName("S10: DELETE -> 200 deleted, master and cost line both gone")
  void deleteRecord_removesMasterAndCostLine() throws Exception {
    int recordId = recordByContractor(2018, "CTR-DEL").path("id").asInt();

    mockMvc.perform(delete(RECORDS + "/" + recordId).with(csrf())
            .param("millId", "690").param("year", "2018"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key",
            org.hamcrest.Matchers.is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", org.hamcrest.Matchers.is("Data deleted successfully")));

    assertEquals(0, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = ?",
        Integer.class, recordId));
    assertEquals(0, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CONTRACTUAL_WORK_REPORT_ID = ?",
        Integer.class, recordId));
  }

  // ---- AC5: optimistic concurrency -------------------------------------------------------------

  @Test
  @DisplayName("a stale revisionCount -> 409 and nothing mutates")
  void updateRecord_staleToken_conflicts() throws Exception {
    int recordId = recordByContractor(2019, "CTR-LOCK").path("id").asInt();
    int current = masterRow(recordId).get("REVISION_COUNT") == null ? -1
        : ((Number) masterRow(recordId).get("REVISION_COUNT")).intValue();

    mockMvc.perform(put(RECORDS + "/" + recordId).with(csrf())
            .param("millId", "690").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR-LOCK-STALE", 108, 9999, "1.0", null, current - 1)))
        .andExpect(status().isConflict());

    // Nothing changed: the contractor id and revision are as seeded.
    assertEquals("CTR-LOCK", masterRow(recordId).get("CONTRACTOR_ID"));
    assertEquals(current, ((Number) masterRow(recordId).get("REVISION_COUNT")).intValue());
  }

  @Test
  @DisplayName("an unknown record id -> 404")
  void updateRecord_unknownId_notFound() throws Exception {
    mockMvc.perform(put(RECORDS + "/999999").with(csrf())
            .param("millId", "690").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR-GHOST", 108, 1, "1.0", null, 0)))
        .andExpect(status().isNotFound());
  }

  // ---- AC1: Draft gate -------------------------------------------------------------------------

  @Test
  @DisplayName("a write against a non-Draft (Submitted) track -> 409, nothing persists")
  void write_nonDraftTrack_conflicts() throws Exception {
    // Mill 691 / 2021 is track 'S'. All three verbs must 409.
    long before = jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE ILCR_MILL_ID = 691", Long.class);

    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "691").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("NOPE", 108, 1, "1.0", null, null)))
        .andExpect(status().isConflict());
    mockMvc.perform(put(RECORDS + "/9130").with(csrf()).param("millId", "691").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("NOPE", 108, 1, "1.0", null, 2)))
        .andExpect(status().isConflict());
    mockMvc.perform(delete(RECORDS + "/9130").with(csrf())
            .param("millId", "691").param("year", "2021"))
        .andExpect(status().isConflict());

    assertEquals(before, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE ILCR_MILL_ID = 691", Long.class));
    assertTrue(jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = 9130",
        Integer.class) == 1);
  }
}
