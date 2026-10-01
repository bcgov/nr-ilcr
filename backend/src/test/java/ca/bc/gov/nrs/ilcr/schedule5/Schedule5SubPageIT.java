package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
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
 * Story 7.4 acceptance — the Other Camp (62) / Other Access (68) expense sub-resources (AC1–AC4,
 * AC7, AC8; slices S04/S07/S20).
 *
 * <p>Security-off is pinned EXPLICITLY and every mutation carries {@code .with(csrf())} — both are
 * no-ops today but keep this suite green when a fail-closed security default merges (the recorded
 * merge-regression guard).
 *
 * <p><strong>Order independence.</strong> A context is (mill, YEAR), so each destructive method
 * claims its own year on mill 690 and nothing here touches Story 7.2's mills 670–676: 2016 the
 * reconcile round trip, 2017 the immediate delete, 2018 insert-from-empty and empty-clears, 2019
 * the deviation-(L) probe, 2020 the foreign-row 404 pair, 2021 the cross-item 404, 2022 the
 * audit-column proof, 2028 the update-to-null proof. 2023 is never mutated — it holds rejection
 * probes whose fingerprint is the nothing-persisted proof.
 *
 * <p><strong>Every write branch is followed by a FRESH {@code GET}</strong>, never only the echo:
 * the echoed document comes from the same in-transaction builder, so it can agree with a write that
 * never committed.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("Schedule 5 sub-pages — /camps/{campId}/other-{camp,access}-expenses (Story 7.4)")
class Schedule5SubPageIT extends AbstractOracleIT {

  private static final String BASE = "/api/v1/schedule5/camps";
  private static final long MILL = 690L;

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private static String campPath(int campId) {
    return BASE + "/" + campId + "/other-camp-expenses";
  }

  private static String accessPath(int campId) {
    return BASE + "/" + campId + "/other-access-expenses";
  }

  private JsonNode getDoc(String path, int year) throws Exception {
    String body =
        mockMvc
            .perform(
                get(path).param("millId", String.valueOf(MILL)).param("year", String.valueOf(year)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private static String rowsBody(String... rows) {
    return "{\"rows\":[" + String.join(",", rows) + "]}";
  }

  private static String newRow(String description, Integer cost) {
    return "{\"rowId\":null,\"description\":" + quote(description) + ",\"cost\":" + cost + "}";
  }

  private static String existingRow(int rowId, String description, Integer cost) {
    return "{\"rowId\":"
        + rowId
        + ",\"description\":"
        + quote(description)
        + ",\"cost\":"
        + cost
        + "}";
  }

  private static String quote(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  // -----------------------------------------------------------------------------------------
  // AC1 — the read
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC1 — GET serves rows in detail-id order with the camp context and both totals")
  void servesCampSubPage() throws Exception {
    JsonNode doc = getDoc(campPath(8700), 2016);

    assertThat(doc.get("campId").asInt()).isEqualTo(8700);
    assertThat(doc.get("campName").asText()).isEqualTo("Reconcile Camp");
    assertThat(doc.get("associatedCampVolume").asInt()).isEqualTo(120000);
    assertThat(doc.get("editable").asBoolean()).isTrue();

    // Deviation (G): ILCR_COST_REPORT_DETAIL_ID ASC, not HashSet order.
    assertThat(doc.get("rows")).hasSize(3);
    assertThat(doc.get("rows").get(0).get("rowId").asInt()).isEqualTo(8722);
    assertThat(doc.get("rows").get(1).get("rowId").asInt()).isEqualTo(8723);
    assertThat(doc.get("rows").get(2).get("rowId").asInt()).isEqualTo(8724);

    // Every row's volume is STAMPED from item 141 — the stored column is null on all three.
    doc.get("rows").forEach(row -> assertThat(row.get("volume").asInt()).isEqualTo(120000));
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                        + "WHERE CAMP_REPORT_ID = 8700 AND ILCR_REPORT_COST_ITEM_ID = 62 AND VOLUME IS NOT NULL",
                    Integer.class))
        .isZero();

    // The third row's description is genuinely null and is OMITTED rather than sent as "".
    assertThat(doc.get("rows").get(2).has("description")).isFalse();

    // AC8 camp footer: 3 x 120000 summed volume.
    assertThat(doc.get("totals").get("volume").asInt()).isEqualTo(360000);
    assertThat(doc.get("totals").get("cost").asInt()).isEqualTo(13000);
  }

  @Test
  @DisplayName("AC8 — the ACCESS footer uses the single camp volume, not the sum (deviation (C))")
  void accessFooterUsesSingleCampVolume() throws Exception {
    JsonNode doc = getDoc(accessPath(8700), 2016);

    assertThat(doc.get("rows")).hasSize(2);
    assertThat(doc.get("totals").get("cost").asInt()).isEqualTo(10000);
    // Two rows, but the volume is 120000 — NOT 240000, which is what the camp side would report.
    assertThat(doc.get("totals").get("volume").asInt()).isEqualTo(120000);
  }

  @Test
  @DisplayName("AC7 — deviation (L): the camp side serves 0 where the access side serves null")
  void deviationLiveOnBothSides() throws Exception {
    JsonNode camp = getDoc(campPath(8703), 2019);
    JsonNode access = getDoc(accessPath(8703), 2019);

    // Identical fixtures on both sides: one row, cost NULL, a non-null item-141/142 volume.
    assertThat(camp.get("rows")).hasSize(1);
    assertThat(access.get("rows")).hasSize(1);

    // The camp helper flags on the STAMPED VOLUME as well as the cost, so its accumulator survives.
    assertThat(camp.get("totals").get("cost").asInt()).isZero();
    // The access helper checks cost alone, so the field is omitted entirely.
    assertThat(access.get("totals").has("cost")).isFalse();
  }

  @Test
  @DisplayName("AC7/deviation (L) — the zero-not-null propagates into the camp document's roll-ups")
  void deviationPropagatesIntoCampDocument() throws Exception {
    // Camp 8703/2019: one item-62 row with cost NULL plus a non-null item-141 volume, mirrored on
    // the access side. The sub-page footers are pinned by deviationLiveOnBothSides; THIS pins the
    // trap-5 propagation — the camp-side 0 flows into Camp Sub-Total, Camp Total and Camp and
    // Access, while the access side's roll-up stays absent (review patch, 2026-08-12).
    String body =
        mockMvc
            .perform(
                get("/api/v1/schedule5")
                    .param("millId", String.valueOf(MILL))
                    .param("year", "2019"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode camp = findCamp(mapper.readTree(body), 8703);

    assertThat(camp.get("otherCampExpenses").get("cost").asInt()).isZero();
    assertThat(camp.get("otherAccessExpenses").has("cost")).isFalse();
    assertThat(camp.get("campSubTotal").get("cost").asInt()).isZero();
    assertThat(camp.get("campTotal").get("cost").asInt()).isZero();
    assertThat(camp.get("campAndAccessTotal").get("cost").asInt()).isZero();
  }

  @Test
  @DisplayName("AC7 — the camp document's counts and roll-up costs agree with the sub-pages")
  void campDocumentAgreesWithSubPages() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/api/v1/schedule5")
                    .param("millId", String.valueOf(MILL))
                    .param("year", "2016"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode camp = findCamp(mapper.readTree(body), 8700);

    assertThat(camp.get("otherCampExpenseCount").asInt()).isEqualTo(3);
    assertThat(camp.get("otherAccessExpenseCount").asInt()).isEqualTo(2);
    assertThat(camp.get("otherCampExpenses").get("cost").asInt()).isEqualTo(13000);
    assertThat(camp.get("otherAccessExpenses").get("cost").asInt()).isEqualTo(10000);
  }

  private static JsonNode findCamp(JsonNode document, int campId) {
    for (JsonNode camp : document.get("camps")) {
      if (camp.get("campId").asInt() == campId) {
        return camp;
      }
    }
    throw new AssertionError("camp " + campId + " not served");
  }

  // -----------------------------------------------------------------------------------------
  // AC2 — the batch reconcile
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC2 — insert from empty, then a fresh GET confirms it committed")
  void insertsFromEmpty() throws Exception {
    mockMvc
        .perform(
            put(campPath(8702))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow("First Row", 1500), newRow("Second Row", 2500))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text").value("Data saved successfully"));

    JsonNode fresh = getDoc(campPath(8702), 2018);
    assertThat(fresh.get("rows")).hasSize(2);
    assertThat(fresh.get("rows").get(0).get("description").asText()).isEqualTo("First Row");
    assertThat(fresh.get("rows").get(1).get("cost").asInt()).isEqualTo(2500);
    assertThat(fresh.get("totals").get("cost").asInt()).isEqualTo(4000);

    // The camp path must NOT have been touched: item 141 still carries its seeded volume.
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT VOLUME FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_COST_REPORT_DETAIL_ID = 8730",
                    Integer.class))
        .isEqualTo(50000);
  }

  @Test
  @DisplayName("AC2 — one call inserts, updates and deletes together")
  void reconcilesInOneCall() throws Exception {
    // Camp 8716/2026 mirrors 8700 but is this test's alone — 2016 is read-asserted elsewhere.
    // Start: 8756 'Generator Fuel' 10000, 8757 'Propane' 2500, 8758 null 500.
    // Keep 8756 edited, drop 8757 and 8758, add one.
    mockMvc
        .perform(
            put(campPath(8716))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    rowsBody(
                        existingRow(8756, "Generator Diesel", 11000),
                        newRow("Chainsaw Fuel", 750))))
        .andExpect(status().isOk());

    JsonNode fresh = getDoc(campPath(8716), 2026);
    assertThat(fresh.get("rows")).hasSize(2);
    // UPDATED IN PLACE — same id, so the row was not churned and reinserted.
    assertThat(fresh.get("rows").get(0).get("rowId").asInt()).isEqualTo(8756);
    assertThat(fresh.get("rows").get(0).get("description").asText()).isEqualTo("Generator Diesel");
    assertThat(fresh.get("rows").get(0).get("cost").asInt()).isEqualTo(11000);
    assertThat(fresh.get("rows").get(1).get("description").asText()).isEqualTo("Chainsaw Fuel");
    assertThat(fresh.get("totals").get("cost").asInt()).isEqualTo(11750);

    // The ACCESS side is untouched — this endpoint writes item 62 only.
    assertThat(getDoc(accessPath(8716), 2026).get("rows")).hasSize(2);

    // AC7 — the camp DOCUMENT reflects the write, in the same test as the write (the guardrail's
    // "in the same test": the static-fixture agreement check below cannot catch a count derivation
    // that breaks only after a mutation).
    String campListBody =
        mockMvc
            .perform(
                get("/api/v1/schedule5")
                    .param("millId", String.valueOf(MILL))
                    .param("year", "2026"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode campAfter = findCamp(mapper.readTree(campListBody), 8716);
    assertThat(campAfter.get("otherCampExpenseCount").asInt()).isEqualTo(2);
    assertThat(campAfter.get("otherCampExpenses").get("cost").asInt()).isEqualTo(11750);
  }

  @Test
  @DisplayName("AC2 — an empty list clears every row for that camp and item")
  void emptyListClears() throws Exception {
    mockMvc
        .perform(
            put(accessPath(8702))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow("Transient", 100))))
        .andExpect(status().isOk());
    assertThat(getDoc(accessPath(8702), 2018).get("rows")).hasSize(1);

    mockMvc
        .perform(
            put(accessPath(8702))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rows\":[]}"))
        .andExpect(status().isOk());

    JsonNode cleared = getDoc(accessPath(8702), 2018);
    assertThat(cleared.get("rows")).isEmpty();
    // An empty list yields a NULL cost, never 0.
    assertThat(cleared.get("totals").has("cost")).isFalse();
  }

  @Test
  @DisplayName("AC2 — an unknown rowId is 404 and NOTHING in the batch persists")
  void unknownRowIdPersistsNothing() throws Exception {
    mockMvc
        .perform(
            put(campPath(8708))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                // A valid new row FIRST, so a naive implementation that wrote as it went would have
                // committed it before reaching the bad id.
                .content(
                    rowsBody(newRow("Should Not Persist", 111), existingRow(999999, "Ghost", 1))))
        .andExpect(status().isNotFound());

    JsonNode unchanged = getDoc(campPath(8708), 2023);
    assertThat(unchanged.get("rows")).hasSize(1);
    assertThat(unchanged.get("rows").get(0).get("description").asText()).isEqualTo("Boundary Row");
    assertThat(unchanged.get("rows").get(0).get("cost").asInt()).isEqualTo(9999999);
  }

  @Test
  @DisplayName("AC2 — a row belonging to ANOTHER camp is 404, never a cross-camp write")
  void foreignRowIdRejected() throws Exception {
    // 8737 belongs to camp 8705; offer it to camp 8704.
    mockMvc
        .perform(
            put(campPath(8704))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(existingRow(8737, "Stolen Row", 999))))
        .andExpect(status().isNotFound());

    // Both camps intact, and the donor row still says what it said.
    assertThat(getDoc(campPath(8704), 2020).get("rows")).hasSize(1);
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT ITEM_DESCRIPTION FROM THE.ILCR_COST_REPORT_DETAIL "
                        + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8737",
                    String.class))
        .isEqualTo("Donor Row");
  }

  @Test
  @DisplayName("AC2/deviation (O) — an item-68 row id offered to the item-62 page is 404")
  void crossItemRowIdRejected() throws Exception {
    // 8739 is camp 8706's ACCESS row. Legacy would have overwritten it: its update loop matched a
    // detail id against the camp's ENTIRE detail collection with no item check
    // (Schedule5DAO.java:585-595). Here it must 404.
    mockMvc
        .perform(
            put(campPath(8706))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(existingRow(8739, "Cross Written", 555))))
        .andExpect(status().isNotFound());

    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT ITEM_DESCRIPTION FROM THE.ILCR_COST_REPORT_DETAIL "
                        + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8739",
                    String.class))
        .isEqualTo("Access Side Row");
  }

  @Test
  @DisplayName("AC2 — a rowId repeated within one body is 404 and NOTHING persists")
  void duplicateRowIdRejected() throws Exception {
    // The classification pass's `!kept.add(rowId)` clause. Without it a duplicated id would apply
    // last-write-wins silently — this pins the loud rejection (review patch, 2026-08-12).
    mockMvc
        .perform(
            put(campPath(8708))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    rowsBody(
                        existingRow(8742, "First Copy", 1), existingRow(8742, "Second Copy", 2))))
        .andExpect(status().isNotFound());

    JsonNode unchanged = getDoc(campPath(8708), 2023);
    assertThat(unchanged.get("rows")).hasSize(1);
    assertThat(unchanged.get("rows").get(0).get("description").asText()).isEqualTo("Boundary Row");
  }

  @Test
  @DisplayName("AC2 — an UPDATE clears a nulled description and cost, never keeps the old values")
  void updateClearsFieldsToNull() throws Exception {
    // Row 8764 is seeded with BOTH fields populated ('Clear Me', 4000). Every other update fixture
    // carries non-null values, so an NVL-style regression (COST = NVL(:cost, COST)) would pass all
    // of them — this is the one test that catches it (review patch, 2026-08-12).
    mockMvc
        .perform(
            put(campPath(8718))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2028")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(existingRow(8764, null, null))))
        .andExpect(status().isOk());

    JsonNode fresh = getDoc(campPath(8718), 2028);
    assertThat(fresh.get("rows")).hasSize(1);
    assertThat(fresh.get("rows").get(0).get("rowId").asInt()).isEqualTo(8764);
    assertThat(fresh.get("rows").get(0).has("description")).isFalse();
    assertThat(fresh.get("rows").get(0).has("cost")).isFalse();

    Map<String, Object> stored =
        jdbc()
            .queryForMap(
                "SELECT ITEM_DESCRIPTION, COST FROM THE.ILCR_COST_REPORT_DETAIL "
                    + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8764");
    assertThat(stored.get("ITEM_DESCRIPTION")).isNull();
    assertThat(stored.get("COST")).isNull();
  }

  @Test
  @DisplayName("AC2 — an unknown CAMP id is 404")
  void unknownCampRejected() throws Exception {
    mockMvc
        .perform(
            put(campPath(999999))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow("Nowhere", 1))))
        .andExpect(status().isNotFound());
  }

  // -----------------------------------------------------------------------------------------
  // AC3 — immediate row delete
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC3 — DELETE removes exactly that row and echoes the delete message")
  void deletesOneRow() throws Exception {
    mockMvc
        .perform(
            delete(campPath(8701) + "/8729")
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2017"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text").value("Data deleted successfully"));

    JsonNode fresh = getDoc(campPath(8701), 2017);
    assertThat(fresh.get("rows")).hasSize(1);
    assertThat(fresh.get("rows").get(0).get("rowId").asInt()).isEqualTo(8728);
    assertThat(fresh.get("rows").get(0).get("description").asText()).isEqualTo("Keep Me");
  }

  @Test
  @DisplayName("AC3 — deleting a foreign row is 404, never a cross-camp delete")
  void deleteForeignRowRejected() throws Exception {
    mockMvc
        .perform(
            delete(campPath(8704) + "/8737")
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2020"))
        .andExpect(status().isNotFound());

    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_COST_REPORT_DETAIL_ID = 8737",
                    Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("AC3 — deleting an unknown row is 404")
  void deleteUnknownRowRejected() throws Exception {
    mockMvc
        .perform(
            delete(campPath(8708) + "/999999")
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2023"))
        .andExpect(status().isNotFound());
  }

  // -----------------------------------------------------------------------------------------
  // AC4 — a blank description is STORABLE (deviation (F))
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC4 — null and empty-string descriptions both persist rather than 400")
  void blankDescriptionsPersist() throws Exception {
    mockMvc
        .perform(
            put(accessPath(8703))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow(null, 100), newRow("", 200))))
        .andExpect(status().isOk());

    JsonNode fresh = getDoc(accessPath(8703), 2019);
    assertThat(fresh.get("rows")).hasSize(2);
    // Jackson NON_NULL omits a null description; an empty string is stored as NULL by Oracle.
    assertThat(fresh.get("rows").get(0).has("description")).isFalse();
    assertThat(fresh.get("rows").get(1).has("description")).isFalse();
    assertThat(fresh.get("totals").get("cost").asInt()).isEqualTo(300);
  }

  // -----------------------------------------------------------------------------------------
  // AC15 — the Draft gate
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("AC15 — every write verb 409s on a non-Draft track, and the read still works")
  void nonDraftBlocksWrites() throws Exception {
    mockMvc
        .perform(
            put(campPath(8709))
                .with(csrf())
                .param("millId", "691")
                .param("year", "2016")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow("Blocked", 1))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            delete(campPath(8709) + "/8743")
                .with(csrf())
                .param("millId", "691")
                .param("year", "2016"))
        .andExpect(status().isConflict());

    // The READ is not gated, and it reports the camp as read-only.
    String body =
        mockMvc
            .perform(get(campPath(8709)).param("millId", "691").param("year", "2016"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode doc = mapper.readTree(body);
    assertThat(doc.get("editable").asBoolean()).isFalse();
    assertThat(doc.get("rows")).hasSize(1);
  }

  // -----------------------------------------------------------------------------------------
  // Audit columns — per column, on INSERT and on UPDATE
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("audit — an INSERT populates all four columns and REVISION_COUNT")
  void insertStampsAllAuditColumns() throws Exception {
    mockMvc
        .perform(
            put(campPath(8715))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2025")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(newRow("Audited Insert", 42))))
        .andExpect(status().isOk());

    Map<String, Object> stored =
        jdbc()
            .queryForMap(
                "SELECT ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP, REVISION_COUNT, "
                    + "VOLUME FROM THE.ILCR_COST_REPORT_DETAIL WHERE CAMP_REPORT_ID = 8715 "
                    + "AND ILCR_REPORT_COST_ITEM_ID = 62 AND ITEM_DESCRIPTION = 'Audited Insert'");

    // Asserted PER COLUMN: the local snapshot's defaults would hide a dropped INSERT column, and
    // this exact bug has shipped on five schedules.
    assertThat(stored.get("ENTRY_USERID")).isNotNull();
    assertThat(stored.get("ENTRY_TIMESTAMP")).isNotNull();
    assertThat(stored.get("UPDATE_USERID")).isNotNull();
    assertThat(stored.get("UPDATE_TIMESTAMP")).isNotNull();
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
    // Deviation (B): a sub-page row's VOLUME is never persisted.
    assertThat(stored.get("VOLUME")).isNull();
  }

  @Test
  @DisplayName("audit — an UPDATE moves UPDATE_* and leaves ENTRY_* untouched")
  void updateLeavesEntryColumnsAlone() throws Exception {
    // Row 8740 is seeded with ENTRY_* backdated to 2020-01-01 by a user the app can never write.
    // The audit columns are DATE (second granularity), so only a backdated baseline can falsify a
    // re-stamp.
    Map<String, Object> before =
        jdbc()
            .queryForMap(
                "SELECT ENTRY_USERID, ENTRY_TIMESTAMP FROM THE.ILCR_COST_REPORT_DETAIL "
                    + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8740");

    mockMvc
        .perform(
            put(campPath(8707))
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rowsBody(existingRow(8740, "Audit Row Edited", 6000))))
        .andExpect(status().isOk());

    Map<String, Object> after =
        jdbc()
            .queryForMap(
                "SELECT ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP, REVISION_COUNT, "
                    + "ITEM_DESCRIPTION FROM THE.ILCR_COST_REPORT_DETAIL "
                    + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8740");

    assertThat(after.get("ITEM_DESCRIPTION")).isEqualTo("Audit Row Edited");
    // ENTRY_* preserved — this is LEGACY PARITY, not a deviation: legacy's update branch mutates
    // the
    // persistent row and copies only cost/description/volume and UPDATE_*
    // (Schedule5DAO.java:585-595).
    assertThat(after.get("ENTRY_USERID"))
        .isEqualTo(before.get("ENTRY_USERID"))
        .isEqualTo("LEGACYUSER");
    assertThat(after.get("ENTRY_TIMESTAMP")).isEqualTo(before.get("ENTRY_TIMESTAMP"));
    // UPDATE_* moved off the backdated baseline.
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .isAfter((Timestamp) before.get("ENTRY_TIMESTAMP"));
    // Detail REVISION_COUNT is deliberately NOT bumped — the camp path and Schedule 6 agree.
    assertThat(((Number) after.get("REVISION_COUNT")).intValue()).isZero();
  }

  @Test
  @DisplayName("the camp family delete still removes the sub-page rows with the rest (BR-09)")
  void campDeleteRemovesSubPageRows() throws Exception {
    // Camp 8714/year 2024 is this test's alone. It must NOT reuse 2020's donor/target pair: a
    // family delete destroys the rows those tests assert are intact, which is precisely how an
    // order-dependent suite is created.
    List<Integer> before =
        jdbc()
            .queryForList(
                "SELECT ILCR_COST_REPORT_DETAIL_ID FROM THE.ILCR_COST_REPORT_DETAIL "
                    + "WHERE CAMP_REPORT_ID = 8714 AND ILCR_REPORT_COST_ITEM_ID IN (62, 68)",
                Integer.class);
    assertThat(before).hasSize(2);

    mockMvc
        .perform(
            delete(BASE + "/8714")
                .with(csrf())
                .param("millId", String.valueOf(MILL))
                .param("year", "2024"))
        .andExpect(status().isOk());

    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CAMP_REPORT_ID = 8714",
                    Integer.class))
        .isZero();
  }
}
