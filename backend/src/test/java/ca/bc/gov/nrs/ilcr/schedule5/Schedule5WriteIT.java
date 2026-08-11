package ca.bc.gov.nrs.ilcr.schedule5;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Story 7.2 acceptance — {@code POST}/{@code PUT}/{@code DELETE /api/v1/schedule5/camps}
 * (AC1–AC5, AC7, AC8; slices S01/S02/S03/S07/S08/S13/S14).
 *
 * <p>Security-off is pinned EXPLICITLY and every mutation carries {@code .with(csrf())} — both are
 * no-ops today but keep this suite green when a fail-closed security default merges (the recorded
 * merge-regression guard, {@code Schedule6WriteIT.java:32-34}).
 *
 * <p><strong>Order independence.</strong> A context is (mill, YEAR), so each destructive method claims
 * its own year on mill 670: 2017 edit-in-place, 2018 zero-detail edit, 2019 add, 2020 delete, 2021 the
 * lock target, 2022 the renamed copy. 2023 and 2024 are never mutated — they hold only rejection and
 * 404 probes — which is what lets their fingerprints serve as nothing-persisted proofs.
 *
 * <p><strong>Every edit reads the current {@code revisionCount} from a GET first</strong>, never a
 * hard-coded token, and assertions locate rows by id or field via JSONPath filters rather than by array
 * index wherever the index is not itself the thing under test. Each write branch is followed by a FRESH
 * {@code GET}: the echoed document comes from the same in-transaction builder, so it can agree with a
 * write that never committed (8-2-…md:115).
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST/PUT/DELETE /api/v1/schedule5/camps — Schedule 5 writes (Story 7.2)")
class Schedule5WriteIT extends AbstractOracleIT {

  private static final String CAMPS = "/api/v1/schedule5/camps";
  private static final String DOCUMENT = "/api/v1/schedule5";
  private static final long MILL = 670L;

  /** The twelve item ids this endpoint writes, in § ITEM WRITE MAP order. */
  private static final List<Integer> WRITE_MAP_ITEMS =
      List.of(56, 58, 59, 60, 141, 61, 63, 64, 65, 66, 67, 142);

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  /** A complete twelve-category body. {@code revisionCount} is ignored on a create. */
  private static String body(String campName, Integer revisionCount) {
    return """
        {
          "campName": "%s",
          "roadDistanceToOperatingArea": 42.50,
          "sizeOfCamp": 60,
          "associatedCampVolume": 120000,
          "isolatedCamp": true,
          "comments": "Seasonal camp, spring only.",
          %s
          "cateringAndFood":       { "volume": 96000,  "cost": 480000 },
          "wagesAndBenefits":      { "volume": 120000, "cost": 960000 },
          "depreciationLease":     { "volume": 120000, "cost": 120000 },
          "generalCampExpenses":   { "volume": 120000, "cost":  60000 },
          "otherCampExpenses":     { "volume": 80000 },
          "recoveries":            { "cost": 44000 },
          "crewTransportation":    { "volume": 90000,  "cost": 180000 },
          "equipAndSuppliesLand":  { "volume": 120000, "cost":  90000 },
          "equipAndSuppliesRail":  { "volume": 120000, "cost":  15000 },
          "equipAndSuppliesAir":   { "volume": 120000, "cost":  12000 },
          "equipAndSuppliesWater": { "volume": 120000, "cost":   6000 },
          "otherAccessExpenses":   { "volume": 60000 }
        }
        """.formatted(campName,
        revisionCount == null ? "" : "\"revisionCount\": " + revisionCount + ",");
  }

  private String documentJson(int year) throws Exception {
    return mockMvc.perform(get(DOCUMENT).param("millId", "670").param("year", String.valueOf(year)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  /** The camp's CURRENT lock token, read from the served document — never hard-coded. */
  private int currentRevision(int year, int campId) throws Exception {
    for (JsonNode camp : mapper.readTree(documentJson(year)).path("camps")) {
      if (camp.path("campId").asInt() == campId) {
        return camp.path("revisionCount").asInt();
      }
    }
    throw new AssertionError("camp " + campId + " not served for year " + year);
  }

  private int campIdByName(int year, String campName) throws Exception {
    for (JsonNode camp : mapper.readTree(documentJson(year)).path("camps")) {
      if (campName.equals(camp.path("campName").asText())) {
        return camp.path("campId").asInt();
      }
    }
    throw new AssertionError("camp '" + campName + "' not served for year " + year);
  }

  private List<Map<String, Object>> detailRows(int campId) {
    return jdbc().queryForList("""
        SELECT ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
               ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
          FROM THE.ILCR_COST_REPORT_DETAIL
         WHERE CAMP_REPORT_ID = ?
         ORDER BY ILCR_REPORT_COST_ITEM_ID
        """, campId);
  }

  // ---- AC1: create (S01) -----------------------------------------------------------------------

  @Test
  @DisplayName("S01: POST -> 200 saved, one CAMP_REPORT row and EXACTLY twelve detail rows")
  void addCamp_persistsMasterAndTwelveDetailRows() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Cedar Flats Camp", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.trackStatus", is("D")));

    // A FRESH GET, not the echo: the echo is built by the same in-transaction builder and would agree
    // with a write that never committed.
    int campId = campIdByName(2019, "Cedar Flats Camp");

    Map<String, Object> master = jdbc().queryForMap("""
        SELECT ILCR_CATEGORY_ID, REPORT_YEAR, ILCR_MILL_ID, CAMP_NAME,
               DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME,
               ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT,
               ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
          FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = ?
        """, campId);
    assertEquals("5", master.get("ILCR_CATEGORY_ID"));
    assertEquals("Y", master.get("ISOLATED_CAMP_IND"));
    assertEquals(0, ((Number) master.get("REVISION_COUNT")).intValue());
    // All five audit/revision columns asserted PER COLUMN, on the master. The local snapshot declares
    // them NOT NULL with no defaults, but a dropped column in an INSERT list is exactly the bug that
    // has shipped on Schedules 1, 2, 4, 8 and 11 (deferred-work.md:15, 204, 211, 217).
    assertNotNull(master.get("ENTRY_USERID"));
    assertNotNull(master.get("ENTRY_TIMESTAMP"));
    assertNotNull(master.get("UPDATE_USERID"));
    assertNotNull(master.get("UPDATE_TIMESTAMP"));

    List<Map<String, Object>> details = detailRows(campId);
    assertEquals(12, details.size(), "exactly twelve rows — no more, no fewer");
    assertEquals(WRITE_MAP_ITEMS.stream().sorted().toList(),
        details.stream().map(r -> ((Number) r.get("ILCR_REPORT_COST_ITEM_ID")).intValue()).toList());
    // And the same five columns on EVERY detail row.
    for (Map<String, Object> row : details) {
      assertEquals(0, ((Number) row.get("REVISION_COUNT")).intValue());
      assertNotNull(row.get("ENTRY_USERID"), "detail ENTRY_USERID");
      assertNotNull(row.get("ENTRY_TIMESTAMP"), "detail ENTRY_TIMESTAMP");
      assertNotNull(row.get("UPDATE_USERID"), "detail UPDATE_USERID");
      assertNotNull(row.get("UPDATE_TIMESTAMP"), "detail UPDATE_TIMESTAMP");
      // ITEM_DESCRIPTION belongs to the item-62/68 sub-page rows only; legacy never sets it here.
      assertNull(row.get("ITEM_DESCRIPTION"), "fixed-grid rows carry no description");
    }
  }

  @Test
  @DisplayName("items 57, 62 and 68 are NEVER written by this endpoint")
  void addCamp_neverWritesTheDeadOrSubPageItems() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("No Sub Page Camp", null)))
        .andExpect(status().isOk());

    int campId = campIdByName(2019, "No Sub Page Camp");
    List<Integer> items = detailRows(campId).stream()
        .map(r -> ((Number) r.get("ILCR_REPORT_COST_ITEM_ID")).intValue())
        .toList();

    // 57 is registered in delivery but has no legacy dispatch branch and zero rows anywhere; 62 and 68
    // are Story 7.4's sub-page rows. A new camp starts with no sub-page rows, which is also what a
    // legacy copy produces (CampReportType.java:150-153).
    assertTrue(items.stream().noneMatch(id -> id == 57 || id == 62 || id == 68),
        "items 57/62/68 must not be written, got " + items);
    assertEquals(0, campSubPageRowCount(campId));
  }

  @Test
  @DisplayName("the 141/61/142 asymmetry persists: volume-only, cost-only, volume-only")
  void addCamp_writesTheVolumeCostAsymmetry() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Asymmetry Camp", null)))
        .andExpect(status().isOk());

    int campId = campIdByName(2019, "Asymmetry Camp");
    Map<Integer, Map<String, Object>> byItem = detailRows(campId).stream()
        .collect(java.util.stream.Collectors.toMap(
            r -> ((Number) r.get("ILCR_REPORT_COST_ITEM_ID")).intValue(), r -> r));

    // Item 141 takes its volume and a hard-coded NULL cost (Schedule5DAO.java:391) — the request's
    // cost half for it is discarded, not rejected.
    assertEquals(80000, ((Number) byItem.get(141).get("VOLUME")).intValue());
    assertNull(byItem.get(141).get("COST"));
    // Item 61 (Recoveries) is the volume-less category: cost only (:392).
    assertNull(byItem.get(61).get("VOLUME"));
    assertEquals(44000, ((Number) byItem.get(61).get("COST")).intValue());
    // Item 142 mirrors 141 (:398).
    assertEquals(60000, ((Number) byItem.get(142).get("VOLUME")).intValue());
    assertNull(byItem.get(142).get("COST"));
  }

  @Test
  @DisplayName("per-category volumes persist VERBATIM — the server never re-derives them (dev. (A))")
  void addCamp_storesSubmittedCategoryVolumesVerbatim() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Divergent Volume Camp", null)))
        .andExpect(status().isOk());

    int campId = campIdByName(2019, "Divergent Volume Camp");
    Map<Integer, Map<String, Object>> byItem = detailRows(campId).stream()
        .collect(java.util.stream.Collectors.toMap(
            r -> ((Number) r.get("ILCR_REPORT_COST_ITEM_ID")).intValue(), r -> r));

    // The body's camp volume is 120000 while catering carries 96000 and crew 90000. BR-03's
    // propagation is a CLIENT-side ajax listener that never runs at save
    // (Schedule5MB.updateCampVolumes is invoked only from the two <p:ajax> handlers), so a server
    // that stamped the camp volume across the eleven categories would silently overwrite a
    // deliberate per-category edit.
    assertEquals(96000, ((Number) byItem.get(56).get("VOLUME")).intValue());
    assertEquals(90000, ((Number) byItem.get(63).get("VOLUME")).intValue());
    assertEquals(120000, ((Number) byItem.get(58).get("VOLUME")).intValue());
  }

  @Test
  @DisplayName("the echo recomputes every derived total server-side (BR-04)")
  void addCamp_echoCarriesDerivedTotals() throws Exception {
    String json = mockMvc.perform(post(CAMPS).with(csrf())
            .param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Derived Totals Camp", null)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    JsonNode camp = null;
    for (JsonNode node : mapper.readTree(json).path("camps")) {
      if ("Derived Totals Camp".equals(node.path("campName").asText())) {
        camp = node;
      }
    }
    assertNotNull(camp);
    // Sub-Total = catering + wages + depreciation + general + otherCamp(null -> the new camp has no
    // item-62 rows, so null) = 480000+960000+120000+60000 = 1620000. Camp Total subtracts Recoveries
    // 44000 -> 1576000. Access = crew+land+rail+air+water+otherAccess(null) = 303000.
    // Camp-and-Access = 1576000 + 303000 = 1879000. Every $/m3 divides by the CAMP volume 120000.
    assertEquals(1_620_000, camp.path("campSubTotal").path("cost").asLong());
    assertEquals(1_576_000, camp.path("campTotal").path("cost").asLong());
    assertEquals(303_000, camp.path("accessExpenseTotal").path("cost").asLong());
    assertEquals(1_879_000, camp.path("campAndAccessTotal").path("cost").asLong());
    // Not accepted on the way IN, but derived on the way out: the request carried no costPerVolume.
    assertEquals(13.50, camp.path("campSubTotal").path("costPerVolume").asDouble(), 0.001);
  }

  @Test
  @DisplayName("unknown properties are IGNORED — the served document can be PUT straight back")
  void servedDocumentRoundTripsWithoutRejection() throws Exception {
    // Legacy's JSF postback silently discarded the disabled="true" computed inputs, so *ignore* is the
    // legacy-faithful behaviour, and it is Spring Boot's Jackson default (FAIL_ON_UNKNOWN_PROPERTIES
    // off — verified, not assumed). A client that echoes back campSubTotal, costPerVolume and the two
    // counts must not be punished for it.
    String withDerived = """
        {
          "campId": 9999, "revisionCount": 0,
          "campName": "Echo Back Camp", "isolatedCamp": false,
          "cateringAndFood": { "volume": 100, "cost": 200, "costPerVolume": 2.00 },
          "campSubTotal": { "volume": 100, "cost": 200, "costPerVolume": 2.00 },
          "campTotal": { "volume": 100, "cost": 200, "costPerVolume": 2.00 },
          "accessExpenseTotal": { "volume": 100, "cost": 0, "costPerVolume": 0.00 },
          "campAndAccessTotal": { "volume": 100, "cost": 200, "costPerVolume": 2.00 },
          "otherCampExpenseCount": 3, "otherAccessExpenseCount": 1
        }
        """;

    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(withDerived))
        .andExpect(status().isOk());

    // And the derived values it sent were NOT taken as input: the new camp has no item-62 rows, so its
    // sub-page count is 0 and not the 3 the body claimed.
    int campId = campIdByName(2019, "Echo Back Camp");
    assertEquals(0, campSubPageRowCount(campId));
  }

  // ---- AC2: edit in place (S02) ----------------------------------------------------------------

  @Test
  @DisplayName("S02: PUT upserts IN PLACE — detail ids and ENTRY_* survive, UPDATE_* moves")
  void updateCamp_upsertsDetailRowsInPlace() throws Exception {
    List<Map<String, Object>> before = jdbc().queryForList("""
        SELECT ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, ENTRY_USERID, ENTRY_TIMESTAMP,
               REVISION_COUNT
          FROM THE.ILCR_COST_REPORT_DETAIL WHERE CAMP_REPORT_ID = 8201
         ORDER BY ILCR_REPORT_COST_ITEM_ID
        """);
    assertEquals(12, before.size());

    int revision = currentRevision(2017, 8201);
    mockMvc.perform(put(CAMPS + "/8201").with(csrf()).param("millId", "670").param("year", "2017")
            .contentType(MediaType.APPLICATION_JSON).content(body("Edit Target Camp", revision)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")));

    List<Map<String, Object>> after = jdbc().queryForList("""
        SELECT ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, ENTRY_USERID, ENTRY_TIMESTAMP,
               REVISION_COUNT, UPDATE_USERID
          FROM THE.ILCR_COST_REPORT_DETAIL WHERE CAMP_REPORT_ID = 8201
         ORDER BY ILCR_REPORT_COST_ITEM_ID
        """);

    // STILL twelve rows with the SAME detail ids — a delete-and-reinsert would rotate every id,
    // destroy ENTRY_*, and make an ordinary edit look like a create in the _AUD shadow (deviation (N)).
    assertEquals(12, after.size());
    assertEquals(
        before.stream().map(r -> r.get("ILCR_COST_REPORT_DETAIL_ID")).toList(),
        after.stream().map(r -> r.get("ILCR_COST_REPORT_DETAIL_ID")).toList());
    for (int i = 0; i < before.size(); i++) {
      assertEquals(before.get(i).get("ENTRY_USERID"), after.get(i).get("ENTRY_USERID"),
          "detail ENTRY_USERID must survive an edit");
      assertEquals(before.get(i).get("ENTRY_TIMESTAMP"), after.get(i).get("ENTRY_TIMESTAMP"),
          "detail ENTRY_TIMESTAMP must survive an edit");
      // Detail REVISION_COUNT is NOT bumped — legacy moves only UPDATE_* here (:641-642).
      assertEquals(before.get(i).get("REVISION_COUNT"), after.get(i).get("REVISION_COUNT"),
          "detail REVISION_COUNT is not a lock and must not move");
      assertNotNull(after.get(i).get("UPDATE_USERID"));
    }
  }

  @Test
  @DisplayName("the master REVISION_COUNT increments by exactly one and ENTRY_* is preserved")
  void updateCamp_bumpsMasterRevisionOnly() throws Exception {
    // Backdated FIRST so the restamp is observable: the column holds second-granularity values and
    // a sibling test may have PUT this camp within the same second, which would make a "moved"
    // assertion against the live value flaky in exactly the way it is meant to be deterministic.
    jdbc().update("UPDATE THE.CAMP_REPORT SET UPDATE_TIMESTAMP = TIMESTAMP '2000-01-01 00:00:00' "
        + "WHERE CAMP_REPORT_ID = 8201");
    Map<String, Object> before = jdbc().queryForMap(
        "SELECT REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP "
            + "FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8201");
    int revision = currentRevision(2017, 8201);

    mockMvc.perform(put(CAMPS + "/8201").with(csrf()).param("millId", "670").param("year", "2017")
            .contentType(MediaType.APPLICATION_JSON).content(body("Edit Target Camp", revision)))
        .andExpect(status().isOk());

    Map<String, Object> after = jdbc().queryForMap(
        "SELECT REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP "
            + "FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8201");

    assertEquals(((Number) before.get("REVISION_COUNT")).intValue() + 1,
        ((Number) after.get("REVISION_COUNT")).intValue());
    assertEquals(before.get("ENTRY_USERID"), after.get("ENTRY_USERID"));
    assertEquals(before.get("ENTRY_TIMESTAMP"), after.get("ENTRY_TIMESTAMP"));
    // RESTAMPED, not merely non-null: the seed populates 'SEED'/SYSDATE, so a repository that
    // dropped UPDATE_USERID = :user / UPDATE_TIMESTAMP = SYSTIMESTAMP would pass a null check on
    // the stale seed values (the review's regression gap — the detail-side test already asserts
    // equality with the acting user).
    assertNotNull(after.get("UPDATE_USERID"));
    assertNotEquals("SEED", after.get("UPDATE_USERID"),
        "UPDATE_USERID must be restamped with the acting user, not left at the seed value");
    assertNotEquals(before.get("UPDATE_TIMESTAMP"), after.get("UPDATE_TIMESTAMP"),
        "UPDATE_TIMESTAMP must move on an edit");
  }

  @Test
  @DisplayName("editing a ZERO-DETAIL camp takes the upsert's INSERT branch — COUNT(*) = 1, not 2")
  void updateZeroDetailCamp_takesTheInsertBranch() throws Exception {
    // This is the DELIVERY-REAL shape: all 61 real camps carry no detail rows at all (Task 1 gate
    // (vii)), so the first edit of a real camp takes this path twelve times over.
    assertEquals(0, detailRows(8202).size(), "camp 8202 starts with no detail rows");

    int revision = currentRevision(2018, 8202);
    mockMvc.perform(put(CAMPS + "/8202").with(csrf()).param("millId", "670").param("year", "2018")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("Bare Edit Target Camp", revision)))
        .andExpect(status().isOk());

    List<Map<String, Object>> after = detailRows(8202);
    assertEquals(12, after.size());
    // Asserting the COUNT is the point, not merely that the rows exist: an upsert that inserted
    // without first attempting the update would produce TWO rows per item on the SECOND edit.
    for (Integer item : WRITE_MAP_ITEMS) {
      assertEquals(1, jdbc().queryForObject(
          "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
              + "WHERE CAMP_REPORT_ID = 8202 AND ILCR_REPORT_COST_ITEM_ID = ?",
          Integer.class, item), "exactly one row for item " + item);
    }

    // A second edit must still leave one row per item — the update branch now applies.
    int nextRevision = currentRevision(2018, 8202);
    mockMvc.perform(put(CAMPS + "/8202").with(csrf()).param("millId", "670").param("year", "2018")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("Bare Edit Target Camp", nextRevision)))
        .andExpect(status().isOk());
    assertEquals(12, detailRows(8202).size(), "a second edit must not duplicate the rows");
  }

  @Test
  @DisplayName("a cleared value writes NULL and the row SURVIVES (deviation (N))")
  void updateCamp_clearedValueWritesNullAndKeepsTheRow() throws Exception {
    // Camp 8218 in year 2016 is this test's own context: it is destructive in a way no other edit test
    // is (it nulls every descriptor and every category), so sharing 2017 with the three tests above
    // would make their expectations order-dependent.
    int revision = currentRevision(2016, 8218);
    // Every category omitted -> both halves null. The rows must remain, holding NULL.
    String cleared = """
        {"campName":"Clear Target Camp","isolatedCamp":true,"revisionCount":%d}
        """.formatted(revision);

    mockMvc.perform(put(CAMPS + "/8218").with(csrf()).param("millId", "670").param("year", "2016")
            .contentType(MediaType.APPLICATION_JSON).content(cleared))
        .andExpect(status().isOk());

    List<Map<String, Object>> after = detailRows(8218);
    assertEquals(12, after.size(), "clearing a value must not delete its row");
    for (Map<String, Object> row : after) {
      assertNull(row.get("VOLUME"), "a cleared volume is NULL, not 0");
      assertNull(row.get("COST"), "a cleared cost is NULL, not 0");
    }
    // And a cleared descriptor is null on the master too, not zeroed.
    Map<String, Object> master = jdbc().queryForMap(
        "SELECT DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, COMMENTS "
            + "FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8218");
    assertNull(master.get("DISTANCE_TO_OPERATING_AREA"));
    assertNull(master.get("CAMP_SIZE_CAPACITY"));
    assertNull(master.get("ASSOCIATED_CAMP_VOLUME"));
    assertNull(master.get("COMMENTS"));
  }

  // ---- AC3: BR-02 uniqueness (S08, S13, S14) ---------------------------------------------------

  @Test
  @DisplayName("S13: a duplicate name -> 409 verbatim, and NOTHING is persisted")
  void duplicateName_conflictsAndPersistsNothing() throws Exception {
    List<Map<String, Object>> before = fingerprint(2023);

    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("Duplicate Name Camp", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("Camp name already exists.")));

    assertEquals(before, fingerprint(2023), "a rejected create must write nothing at all");
  }

  @Test
  @DisplayName("S13: uniqueness is CASE-INSENSITIVE and trim-aware")
  void duplicateName_isCaseInsensitiveAndTrimmed() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("DUPLICATE NAME CAMP", null)))
        .andExpect(status().isConflict());
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("  Duplicate Name Camp  ", null)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("S14: an UNRENAMED copy is exactly this 409 — there is no separate copy endpoint")
  void unrenamedCopy_isTheDuplicateNameConflict() throws Exception {
    // Legacy's copyCamp() makes no DB call (Schedule5MB.java:270-275): it clones in memory, blanks the
    // name, and warns. So "save an unrenamed copy" and "save a duplicate name" are the same request.
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("Duplicate Name Camp", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("Camp name already exists.")));
  }

  @Test
  @DisplayName("S08: the same name passes in another MILL and in another YEAR")
  void sameNameOtherMillYear_succeeds() throws Exception {
    // Mill 675/2023 is a different mill; 670/2019 is a different year of the same mill. Both must
    // accept the name mill 670/2023 already holds — the scope is (mill, year, category '5').
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "675").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("Duplicate Name Camp", null)))
        .andExpect(status().isOk());
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Duplicate Name Camp", null)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("S13: a RENAME onto another camp's name -> 409 verbatim, and the rename is not stored")
  void renameOntoAnotherCampsName_conflicts() throws Exception {
    // Both camps are created HERE, in the add year, so this test owns its fixtures and no other
    // test's expectations depend on them. This is the branch legacy disarmed after a new camp's
    // first save (deviation (I)) — the edit-path conflict, not the create-path one.
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Rename Source Camp", null)))
        .andExpect(status().isOk());
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).content(body("Rename Incumbent Camp", null)))
        .andExpect(status().isOk());
    int sourceId = campIdByName(2019, "Rename Source Camp");
    int revision = currentRevision(2019, sourceId);

    // Case-varied on purpose: the edit path must be as case-insensitive as the create path.
    mockMvc.perform(put(CAMPS + "/" + sourceId).with(csrf())
            .param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("RENAME INCUMBENT CAMP", revision)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("Camp name already exists.")));

    // A fresh GET, not the echo: the source camp still carries its own name.
    assertEquals(sourceId, campIdByName(2019, "Rename Source Camp"));
  }

  @Test
  @DisplayName("an UNRENAMED edit of an existing camp is NOT a self-conflict")
  void unrenamedEdit_isNotASelfConflict() throws Exception {
    // The excluding query keys on CAMP_REPORT_ID, so saving a camp under its own name is fine. Legacy
    // reached the same outcome by DISARMING the check after a new camp's first save (:315), which also
    // let a rename bypass BR-02 entirely — deviation (I), not ported.
    int revision = currentRevision(2017, 8201);
    mockMvc.perform(put(CAMPS + "/8201").with(csrf()).param("millId", "670").param("year", "2017")
            .contentType(MediaType.APPLICATION_JSON).content(body("Edit Target Camp", revision)))
        .andExpect(status().isOk());
  }

  // ---- AC4: BR-10 renamed copy (S03) -----------------------------------------------------------

  @Test
  @DisplayName("S03: a renamed copy round-trips the values through an ORDINARY POST")
  void renamedCopy_roundTripsThroughAnOrdinaryPost() throws Exception {
    // Read the source camp, rename it, POST it back — that is the whole of BR-10 on the server side.
    JsonNode source = null;
    for (JsonNode camp : mapper.readTree(documentJson(2022)).path("camps")) {
      if (camp.path("campId").asInt() == 8205) {
        source = camp;
      }
    }
    assertNotNull(source);

    String copy = """
        {
          "campName": "Copied Camp",
          "roadDistanceToOperatingArea": %s,
          "sizeOfCamp": %d,
          "associatedCampVolume": %d,
          "isolatedCamp": %b,
          "comments": "%s",
          "cateringAndFood":       { "volume": %s, "cost": %d },
          "wagesAndBenefits":      { "volume": %s, "cost": %d },
          "depreciationLease":     { "volume": %s, "cost": %d },
          "generalCampExpenses":   { "volume": %s, "cost": %d },
          "otherCampExpenses":     { "volume": %s },
          "recoveries":            { "cost": %d },
          "crewTransportation":    { "volume": %s, "cost": %d },
          "equipAndSuppliesLand":  { "volume": %s, "cost": %d },
          "equipAndSuppliesRail":  { "volume": %s, "cost": %d },
          "equipAndSuppliesAir":   { "volume": %s, "cost": %d },
          "equipAndSuppliesWater": { "volume": %s, "cost": %d },
          "otherAccessExpenses":   { "volume": %s }
        }
        """.formatted(
        source.path("roadDistanceToOperatingArea").asText(),
        source.path("sizeOfCamp").asInt(),
        source.path("associatedCampVolume").asInt(),
        source.path("isolatedCamp").asBoolean(),
        source.path("comments").asText(),
        source.path("cateringAndFood").path("volume").asText(),
        source.path("cateringAndFood").path("cost").asInt(),
        source.path("wagesAndBenefits").path("volume").asText(),
        source.path("wagesAndBenefits").path("cost").asInt(),
        source.path("depreciationLease").path("volume").asText(),
        source.path("depreciationLease").path("cost").asInt(),
        source.path("generalCampExpenses").path("volume").asText(),
        source.path("generalCampExpenses").path("cost").asInt(),
        source.path("otherCampExpenses").path("volume").asText(),
        source.path("recoveries").path("cost").asInt(),
        source.path("crewTransportation").path("volume").asText(),
        source.path("crewTransportation").path("cost").asInt(),
        source.path("equipAndSuppliesLand").path("volume").asText(),
        source.path("equipAndSuppliesLand").path("cost").asInt(),
        source.path("equipAndSuppliesRail").path("volume").asText(),
        source.path("equipAndSuppliesRail").path("cost").asInt(),
        source.path("equipAndSuppliesAir").path("volume").asText(),
        source.path("equipAndSuppliesAir").path("cost").asInt(),
        source.path("equipAndSuppliesWater").path("volume").asText(),
        source.path("equipAndSuppliesWater").path("cost").asInt(),
        source.path("otherAccessExpenses").path("volume").asText());

    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2022")
            .contentType(MediaType.APPLICATION_JSON).content(copy))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[?(@.campName=='Copied Camp')].cateringAndFood.cost",
            contains(11000)))
        .andExpect(jsonPath("$.camps[?(@.campName=='Copied Camp')].recoveries.cost",
            contains(15000)));

    int copyId = campIdByName(2022, "Copied Camp");
    assertNotEquals(8205, copyId);
    // The source's item-62 row is NOT duplicated onto the copy: legacy's copy constructor skips the
    // sub-page lists (CampReportType.java:150-153), and this path writes only the twelve fixed rows.
    assertEquals(1, campSubPageRowCount(8205), "the source keeps its own sub-page row");
    assertEquals(0, campSubPageRowCount(copyId), "the copy starts with none");
  }

  // ---- AC5: delete the camp family (S07) -------------------------------------------------------

  @Test
  @DisplayName("S07: DELETE removes the camp AND every detail row, incl. the item-62/68 sub-page rows")
  void deleteCamp_removesTheWholeFamily() throws Exception {
    assertEquals(14, detailRows(8203).size(), "12 fixed + one item-62 + one item-68");
    List<Map<String, Object>> neighbourBefore = neighbourFingerprint();

    mockMvc.perform(delete(CAMPS + "/8203").with(csrf())
            .param("millId", "670").param("year", "2020"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")))
        .andExpect(jsonPath("$.camps[?(@.campId==8203)]", hasSize(0)));

    assertEquals(0, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8203", Integer.class));
    // Orphaned children are the failure this asserts against: a delete that only removed the twelve
    // fixed rows would leave the item-62/68 rows behind, pointing at a camp that no longer exists.
    assertEquals(0, detailRows(8203).size());

    // Scoping proven by the NEIGHBOUR SURVIVING, not by the target vanishing — the latter cannot tell
    // a correctly scoped delete from one that deleted more than it should.
    assertEquals(neighbourBefore, neighbourFingerprint());
  }

  @Test
  @DisplayName("DELETE carries no revision token — it cannot be rejected as stale (deviation (L))")
  void deleteCamp_needsNoRevisionToken() throws Exception {
    // Camp 8207's REVISION_COUNT is 0, but nothing in the request references it. This is the systemic
    // AR11 deviation shared with Schedules 4, 7A and 11 (deferred-work.md:187, 196) — restated, not
    // re-litigated.
    mockMvc.perform(delete(CAMPS + "/8207").with(csrf())
            .param("millId", "670").param("year", "2024"))
        .andExpect(status().isOk());
    assertEquals(0, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CAMP_REPORT WHERE CAMP_REPORT_ID = 8207", Integer.class));
  }

  // ---- AC7: the Draft gate ---------------------------------------------------------------------

  @Test
  @DisplayName("a non-Draft mill 409s on all three mutations, and persists nothing")
  void nonDraft_rejectsEveryMutation() throws Exception {
    List<Map<String, Object>> before = lockedMillFingerprint();

    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "671").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Should Not Persist", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail",
            is("This schedule cannot be edited in its current status.")));
    mockMvc.perform(put(CAMPS + "/8208").with(csrf()).param("millId", "671").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Locked Camp", 4)))
        .andExpect(status().isConflict());
    mockMvc.perform(delete(CAMPS + "/8208").with(csrf())
            .param("millId", "671").param("year", "2021"))
        .andExpect(status().isConflict());

    assertEquals(before, lockedMillFingerprint());
    // The camp count is unchanged too — the rejected POST must not have created one.
    assertEquals(1, jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CAMP_REPORT WHERE ILCR_MILL_ID = 671", Integer.class));
  }

  // ---- AC8: AR11 optimistic locking ------------------------------------------------------------

  @Test
  @DisplayName("a STALE revisionCount -> 409, and the camp is untouched")
  void staleRevision_409() throws Exception {
    // Camp 8204's stored REVISION_COUNT is 3, so 0 is stale. Seeding it non-zero is deliberate: a test
    // that hard-coded 0 as "the current token" would otherwise pass by accident.
    Map<String, Object> before = jdbc().queryForMap(
        "SELECT CAMP_NAME, REVISION_COUNT, UPDATE_TIMESTAMP FROM THE.CAMP_REPORT "
            + "WHERE CAMP_REPORT_ID = 8204");

    mockMvc.perform(put(CAMPS + "/8204").with(csrf()).param("millId", "670").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Renamed By Stale Write", 0)))
        .andExpect(status().isConflict());

    assertEquals(before, jdbc().queryForMap(
        "SELECT CAMP_NAME, REVISION_COUNT, UPDATE_TIMESTAMP FROM THE.CAMP_REPORT "
            + "WHERE CAMP_REPORT_ID = 8204"));
  }

  @Test
  @DisplayName("the CURRENT revisionCount succeeds, so the 409 above is about staleness alone")
  void currentRevision_succeeds() throws Exception {
    int revision = currentRevision(2021, 8204);
    assertEquals(3, revision, "the seed's token, read not assumed");

    mockMvc.perform(put(CAMPS + "/8204").with(csrf()).param("millId", "670").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Lock Target Camp", revision)))
        .andExpect(status().isOk());
    assertEquals(4, currentRevision(2021, 8204));
  }

  @Test
  @DisplayName("an UNKNOWN camp id -> 404, not 409")
  void unknownCamp_404() throws Exception {
    // 7999 sits BELOW the 8200 fixture block, so it is categorically unknown. An id at or above the
    // sequence start (ILCR_REPORT_COMMON_SEQ begins at 9500) could eventually be MINTED by the
    // create tests on a long-lived container, turning this probe into a mutation of live state.
    mockMvc.perform(put(CAMPS + "/7999").with(csrf()).param("millId", "670").param("year", "2024")
            .contentType(MediaType.APPLICATION_JSON).content(body("Nowhere Camp", 0)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Camp not found.")));
    mockMvc.perform(delete(CAMPS + "/7999").with(csrf())
            .param("millId", "670").param("year", "2024"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("an unknown id whose SUBMITTED NAME collides is still the 404 — never a leaked 409")
  void unknownCampWithConflictingName_404() throws Exception {
    List<Map<String, Object>> before = fingerprint(2023);

    // "Duplicate Name Camp" is held by camp 8206 in 670/2023. Running the name-conflict check
    // before the existence probe would answer 409 "Camp name already exists." about a camp id the
    // caller cannot see — contradicting the documented 404 and confirming the name exists.
    mockMvc.perform(put(CAMPS + "/7999").with(csrf()).param("millId", "670").param("year", "2023")
            .contentType(MediaType.APPLICATION_JSON).content(body("Duplicate Name Camp", 0)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Camp not found.")));

    assertEquals(before, fingerprint(2023), "the rejected write must not touch a single column");
  }

  @Test
  @DisplayName("a FOREIGN camp id -> 404, and the other mill's camp is untouched (deviation (M))")
  void foreignMillCamp_404() throws Exception {
    // Camp 8216 belongs to mill 675. Legacy's deleteCampFromReport loaded by primary key alone with no
    // tenancy check (Schedule5DAO.java:550) — an IDOR this port must not reproduce. 404 rather than
    // 403 deliberately: a 403 would confirm the camp exists.
    List<Map<String, Object>> before = neighbourFingerprint();

    mockMvc.perform(put(CAMPS + "/8216").with(csrf()).param("millId", "670").param("year", "2024")
            .contentType(MediaType.APPLICATION_JSON).content(body("Stolen Camp", 0)))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete(CAMPS + "/8216").with(csrf())
            .param("millId", "670").param("year", "2024"))
        .andExpect(status().isNotFound());

    assertEquals(before, neighbourFingerprint());
  }

  @Test
  @DisplayName("a MISSING revisionCount on PUT -> 400, never a coerced 409")
  void missingRevisionCount_400() throws Exception {
    // The OnUpdate validation group. A coerced 409 would tell the licensee to reload when the real fix
    // is to send the token (the Story 2.1 review lesson).
    mockMvc.perform(put(CAMPS + "/8207").with(csrf()).param("millId", "670").param("year", "2024")
            .contentType(MediaType.APPLICATION_JSON).content(body("Probe Camp", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Revision count is required for an update.")));
  }

  @Test
  @DisplayName("POST does NOT require revisionCount — it is meaningless on a create")
  void postIgnoresRevisionCount() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "670").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"campName\":\"No Token Camp\",\"isolatedCamp\":false}"))
        .andExpect(status().isOk());
  }

  // ---- context guards --------------------------------------------------------------------------

  @Test
  @DisplayName("the mill/year context guards apply to every mutation (ERR-003/004/005)")
  void contextGuardsApplyToWrites() throws Exception {
    mockMvc.perform(post(CAMPS).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body("No Context Camp", null)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "516").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Closed Mill Camp", null)))
        .andExpect(status().isConflict());
    mockMvc.perform(post(CAMPS).with(csrf()).param("millId", "999").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body("Unknown Mill Camp", null)))
        .andExpect(status().isNotFound());
  }

  // ---- fingerprints ----------------------------------------------------------------------------

  private int campSubPageRowCount(int campId) {
    return jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID IN (62, 68)",
        Integer.class, campId);
  }

  /**
   * A per-row snapshot of one mill/year including BOTH audit pairs and both REVISION_COUNTs. Count-
   * and sum-based fingerprints have passed while real writes slipped through (8-2-…md:112).
   */
  private List<Map<String, Object>> fingerprint(int year) {
    return jdbc().queryForList("""
        SELECT c.CAMP_REPORT_ID, c.CAMP_NAME, c.DISTANCE_TO_OPERATING_AREA, c.CAMP_SIZE_CAPACITY,
               c.ASSOCIATED_CAMP_VOLUME, c.ISOLATED_CAMP_IND, c.COMMENTS, c.REVISION_COUNT,
               c.ENTRY_USERID, c.ENTRY_TIMESTAMP, c.UPDATE_USERID, c.UPDATE_TIMESTAMP,
               d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST,
               d.REVISION_COUNT AS DETAIL_REVISION, d.ENTRY_USERID AS DETAIL_ENTRY_USER,
               d.ENTRY_TIMESTAMP AS DETAIL_ENTRY_TS, d.UPDATE_USERID AS DETAIL_UPDATE_USER,
               d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TS
          FROM THE.CAMP_REPORT c
          LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.CAMP_REPORT_ID = c.CAMP_REPORT_ID
         WHERE c.ILCR_MILL_ID = ? AND c.REPORT_YEAR = ?
         ORDER BY c.CAMP_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
        """, MILL, year);
  }

  private List<Map<String, Object>> neighbourFingerprint() {
    return jdbc().queryForList("""
        SELECT c.CAMP_REPORT_ID, c.CAMP_NAME, c.REVISION_COUNT, c.ENTRY_USERID, c.ENTRY_TIMESTAMP,
               c.UPDATE_USERID, c.UPDATE_TIMESTAMP,
               d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.COST, d.ITEM_DESCRIPTION
          FROM THE.CAMP_REPORT c
          LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.CAMP_REPORT_ID = c.CAMP_REPORT_ID
         WHERE c.CAMP_REPORT_ID = 8216
         ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
        """);
  }

  private List<Map<String, Object>> lockedMillFingerprint() {
    return jdbc().queryForList("""
        SELECT c.CAMP_REPORT_ID, c.CAMP_NAME, c.REVISION_COUNT, c.ENTRY_USERID, c.ENTRY_TIMESTAMP,
               c.UPDATE_USERID, c.UPDATE_TIMESTAMP,
               d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST,
               d.UPDATE_USERID AS DETAIL_UPDATE_USER, d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TS
          FROM THE.CAMP_REPORT c
          LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.CAMP_REPORT_ID = c.CAMP_REPORT_ID
         WHERE c.ILCR_MILL_ID = 671
         ORDER BY c.CAMP_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
        """);
  }
}
