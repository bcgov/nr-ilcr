package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 8.2 acceptance — {@code POST/PUT /api/v1/schedule6/records} (AC1–AC4, AC6; slices
 * S01/S03/S05/S12–S16/S17-write/S19; AD-5, AD-9, AD-10, AD-12). The mock {@code ILCR_SUBMITTER}
 * holds both VIEW and EDIT; authz is proven in {@link Schedule6WriteAuthorizationIT}. Security-off
 * is pinned EXPLICITLY and every mutation carries {@code .with(csrf())} — no-ops today, but they
 * keep this suite green when a fail-closed security default merges in (the recorded
 * merge-regression guard).
 *
 * <p>Mutating tests are ORDER-INDEPENDENT via the V32 context model: a context is (mill, YEAR), so
 * each destructive test method claims its own year on mill 661 — 2017 detail-update-in-place, 2019
 * add-TSA, 2020 add-TFL, 2021 stale-revision, 2022 edit/switch, 2024 TFL alias; 2023 is the
 * never-mutated rejection-fingerprint year and 2018 holds the placeholder the 404 probe rejects.
 * That invariant is load-bearing and was violated: the alias test and the add-TFL test both wrote
 * into 2020, so whichever ran second broke the other's exact-match {@code contains(...)} filter
 * (code review 2026-08-04). Edits read the current {@code revisionCount} before writing (never a
 * hard-coded token — Story 2.1 review lesson), and assertions locate records by id/field (JSONPath
 * filters), not array index.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST/PUT /api/v1/schedule6/records — Schedule 6 writes (Story 8.2)")
class Schedule6WriteIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule6/records";
  private static final String COMMENTS = "/api/v1/schedule6/general-comments";
  private static final String CHECK_STATUS = "/api/v1/schedule6/check-status";
  private static final String ENDPOINT = "/api/v1/schedule6";
  private static final String PROBLEM_JSON = "application/problem+json";

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private DataSource dataSource;

  // ---- AC1: add a TSA+Supply Block record (S01) ------------------------------------------------

  @Test
  @DisplayName(
      "S01: POST a TSA record -> 200 saved, RMG and $/m3 derived, totals recomputed, "
          + "DB row complete; clean follow-up check-status reports all met")
  void addTsaRecord_persistsWithDerivations() throws Exception {
    // Year 2019 carries one seeded record (TSA 01/01B, vol 1000, cost 50000). Adding
    // TSA 03 + TSB 03B (RMG 1), vol 400 / cost 20000 -> $/m3 50.00; totals 1400 / 70000 -> 50.00.
    String body =
        """
            {"areaType":"03","supplyBlock":"03B","volume":400,"cost":20000,"comments":"Bulkley spur"}
            """;
    String json =
        mockMvc
            .perform(
                post(RECORDS)
                    .with(csrf())
                    .param("millId", "661")
                    .param("year", "2019")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
            .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
            .andExpect(jsonPath("$.editable", is(true)))
            .andExpect(jsonPath("$.roadRecords[?(@.areaType=='03')].rmg", contains("1")))
            .andExpect(
                jsonPath("$.roadRecords[?(@.areaType=='03')].costPerVolume", contains(50.00)))
            .andExpect(jsonPath("$.roadRecords[?(@.areaType=='03')].revisionCount", contains(0)))
            .andExpect(jsonPath("$.totalVolume", is(1400)))
            .andExpect(jsonPath("$.totalCost", is(70000)))
            .andExpect(jsonPath("$.totalCostPerVolume", is(50.00)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // DB proof: category-'6' master with ALL five audit/revision columns populated (the
    // recurring audit-column bug this story must not have) + its item-69 detail.
    int recordId = recordIdByAreaType(json, "03");
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            SELECT ILCR_CATEGORY_ID, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP,
                   UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = ?
            """,
            recordId);
    assertEquals("6", row.get("ILCR_CATEGORY_ID"));
    assertEquals(0, ((Number) row.get("REVISION_COUNT")).intValue());
    assertEquals("dev-submitter", row.get("ENTRY_USERID"));
    assertEquals("dev-submitter", row.get("UPDATE_USERID"));
    assertNotNull(row.get("ENTRY_TIMESTAMP"));
    assertNotNull(row.get("UPDATE_TIMESTAMP"));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class,
            recordId));

    // The DETAIL row's own five audit/revision columns. Delivery declares all five NOT NULL on
    // ILCR_COST_REPORT_DETAIL too (verified 2026-08-04), but the LOCAL V1 snapshot leaves them
    // nullable-with-defaults — so unlike the master above, the DDL cannot catch an insert that
    // drops one. Only this assertion can, and it is the recurring audit-column bug's last blind
    // spot in this story (code review 2026-08-04).
    Map<String, Object> detail =
        jdbc.queryForMap(
            """
            SELECT REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            recordId);
    assertEquals(0, ((Number) detail.get("REVISION_COUNT")).intValue());
    assertEquals("dev-submitter", detail.get("ENTRY_USERID"));
    assertEquals("dev-submitter", detail.get("UPDATE_USERID"));
    assertNotNull(detail.get("ENTRY_TIMESTAMP"));
    assertNotNull(detail.get("UPDATE_TIMESTAMP"));

    // Durable on a fresh GET.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "661")
                .param("year", "2019")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==" + recordId + ")]", hasSize(1)));

    // SUC-003 clean follow-up: both 2019 records are complete -> the single MET banner.
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "661").param("year", "2019"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")))
        .andExpect(jsonPath("$.records", hasSize(0)));
  }

  // ---- AC2: TFL semantics (S03) + the leading-zero alias (BR-03/deviation (h)) -----------------

  @Test
  @DisplayName(
      "S03: POST a TFL record -> Supply Block cleared server-side, TSA columns NULL, RMG by TFL")
  void addTflRecord_clearsCounterpart() throws Exception {
    // supplyBlock is deliberately present in the request: BR-02 must clear it server-side.
    String body =
        """
            {"areaType":"TFL","tflNumber":"18","supplyBlock":"01B","volume":200,"cost":10000}
            """;
    String json =
        mockMvc
            .perform(
                post(RECORDS)
                    .with(csrf())
                    .param("millId", "661")
                    .param("year", "2020")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roadRecords[?(@.areaType=='TFL')].tflNumber", contains("18")))
            .andExpect(jsonPath("$.roadRecords[?(@.areaType=='TFL')].rmg", contains("4")))
            // Jackson non_null: the cleared supplyBlock is ABSENT on the TFL record.
            .andExpect(jsonPath("$.roadRecords[?(@.areaType=='TFL')].supplyBlock", hasSize(0)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    int recordId = recordIdByAreaType(json, "TFL");
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = ?
            """,
            recordId);
    assertEquals(null, row.get("TSA_NUMBER"));
    assertEquals(null, row.get("TSB_NUMBER_CODE"));
    assertEquals("18", row.get("TFL_NUMBER_CODE"));
  }

  @Test
  @DisplayName(
      "BR-03: a no-leading-zero TFL alias (\"1\") is normalized onto the stored value (\"01\")")
  void addTflRecord_normalizesLeadingZeroAlias() throws Exception {
    String body =
        """
            {"areaType":"TFL","tflNumber":"1","cost":5000}
            """;
    // Year 2024, NOT 2020: this test and addTflRecord_clearsCounterpart both used to POST a TFL
    // record into 661/2020, so whichever ran second left two TFL records there and the other's
    // exact-match `contains(...)` over an areaType=='TFL' filter saw both values and failed.
    // JUnit's default method order is deterministic but hash-derived, so a rename could flip it
    // (code review 2026-08-04 — the class contract is one destructive test per year).
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        // Served (and stored) as the padded code, resolving RMG 1 — never the raw alias.
        .andExpect(jsonPath("$.roadRecords[?(@.tflNumber=='01')].rmg", contains("1")));
  }

  // ---- AC2: S19 switch TSA -> TFL on an existing record ----------------------------------------

  @Test
  @DisplayName(
      "S19: PUT switches TSA->TFL: TFL stored, Supply Block cleared to NULL, RMG re-derived, "
          + "revision bumped; the detail upsert INSERTS on the delivery-real no-detail row")
  void switchAreaTypeTsaToTfl() throws Exception {
    // 8337 (2022) is seeded TSA 01/01B with NO item-69 detail — the real delivery shape.
    int currentRevision = currentRevision(661, 2022, 8337);
    String body =
        """
            {"areaType":"TFL","tflNumber":"18","volume":300,"cost":15000,"revisionCount":%d}
            """
            .formatted(currentRevision);
    mockMvc
        .perform(
            put(RECORDS + "/8337")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8337)].areaType", contains("TFL")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8337)].tflNumber", contains("18")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8337)].supplyBlock", hasSize(0)))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8337)].rmg", contains("4")))
        .andExpect(
            jsonPath(
                "$.roadRecords[?(@.recordId==8337)].revisionCount", contains(currentRevision + 1)));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8337
            """);
    assertEquals(null, row.get("TSA_NUMBER"));
    assertEquals(null, row.get("TSB_NUMBER_CODE"));
    assertEquals("18", row.get("TFL_NUMBER_CODE"));
    // The UPDATE's audit stamps moved off the seeded 'SEED'. An UPDATE that drops them cannot
    // fail on a NOT NULL column, so only an assertion catches it (code review 2026-08-04).
    assertEquals("dev-submitter", row.get("UPDATE_USERID"));
    assertNotNull(row.get("UPDATE_TIMESTAMP"));
    // The edit CREATED the missing item-69 detail (8.1 Task 1: real cat-6 rows have none).
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8337 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class));
  }

  @Test
  @DisplayName(
      "The detail upsert's UPDATE-IN-PLACE branch: editing a record that ALREADY has an "
          + "item-69 detail updates that row rather than inserting a second one — COUNT stays 1 and "
          + "every written column lands. The suite previously exercised only the INSERT branch, so "
          + "an always-insert regression (duplicate details, money invisible in totals) shipped green")
  void editWithExistingDetail_updatesInPlace() throws Exception {
    // 8361 (2017) is seeded WITH detail 8362 (vol 1000 / cost 50000 / 'Seeded 2017 record') and
    // owns its year, so this edit is the only destructive test in that context.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    int detailId =
        jdbc.queryForObject(
            """
            SELECT ILCR_COST_REPORT_DETAIL_ID FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8361 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class);
    int currentRevision = currentRevision(661, 2017, 8361);
    String body =
        """
            {"areaType":"05","supplyBlock":"05B","volume":250,"cost":7500,
             "comments":"Edited in place","revisionCount":%d}
            """
            .formatted(currentRevision);
    mockMvc
        .perform(
            put(RECORDS + "/8361")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2017")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8361)].volume", contains(250)))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8361)].cost", contains(7500)))
        .andExpect(
            jsonPath("$.roadRecords[?(@.recordId==8361)].comments", contains("Edited in place")));

    // COUNT == 1 AND the same detail id is what pins update-in-place over a second insert.
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8361 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class));
    Map<String, Object> detail =
        jdbc.queryForMap(
            """
            SELECT ILCR_COST_REPORT_DETAIL_ID, VOLUME, COST, COMMENTS, REVISION_COUNT,
                   ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8361 AND ILCR_REPORT_COST_ITEM_ID = 69
            """);
    assertEquals(detailId, ((Number) detail.get("ILCR_COST_REPORT_DETAIL_ID")).intValue());
    assertEquals(250, ((Number) detail.get("VOLUME")).intValue());
    assertEquals(7500, ((Number) detail.get("COST")).intValue());
    assertEquals("Edited in place", detail.get("COMMENTS"));
    // Detail REVISION_COUNT stays 0 (legacy never bumps it) and ENTRY_* survive the update.
    assertEquals(0, ((Number) detail.get("REVISION_COUNT")).intValue());
    assertEquals("SEED", detail.get("ENTRY_USERID"));
    assertEquals("dev-submitter", detail.get("UPDATE_USERID"));
    assertNotNull(detail.get("UPDATE_TIMESTAMP"));
  }

  // ---- AC3: field validation rejects with verbatim texts and persists nothing (S05/S12-S16) ---

  @Test
  @DisplayName("S05: invalid TFL numbers -> 400 verbatim FLD-002; nothing persists (fingerprint)")
  void invalidTfl_returns400_nothingPersists() throws Exception {
    String before = fingerprint(661, 2023);
    for (String tfl : new String[] {"99", "52B", "2"}) {
      mockMvc
          .perform(
              post(RECORDS)
                  .with(csrf())
                  .param("millId", "661")
                  .param("year", "2023")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"areaType\":\"TFL\",\"tflNumber\":\"" + tfl + "\",\"cost\":1}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
          .andExpect(
              jsonPath("$.detail", is("Entered TFL number is not valid for Interior Regions.")));
    }
    // A TFL record with the number missing entirely is equally FLD-002 (required-and-validated).
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"TFL\",\"cost\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Entered TFL number is not valid for Interior Regions.")));
    assertEquals(before, fingerprint(661, 2023));
  }

  @Test
  @DisplayName("S12: missing/blank area type -> 400 FLD-001 (reconstructed text, deviation (e))")
  void missingAreaType_returns400() throws Exception {
    String before = fingerprint(661, 2023);
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplyBlock\":\"01B\",\"cost\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("TSA or TFL: Value is required.")));
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\" \",\"cost\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("TSA or TFL: Value is required.")));
    assertEquals(before, fingerprint(661, 2023));
  }

  @Test
  @DisplayName(
      "S13-S16: out-of-range and non-numeric volume/cost -> 400 verbatim; nothing persists")
  void volumeAndCostValidation_return400() throws Exception {
    String before = fingerprint(661, 2023);
    // S14 volume out of range (and beyond the NUMBER(10,2) fraction — same single message).
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"volume\":10000000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered volume must be between 0 and 9,999,999.")));
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"volume\":100.555}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered volume must be between 0 and 9,999,999.")));
    // S13 non-numeric volume (Jackson BigDecimal mismatch -> verbatim converter text).
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"volume\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered volume entry is invalid.")));
    // S16 cost out of range.
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"cost\":100000000}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Entered cost must be between -99,999,999 and 99,999,999.")));
    // S15 non-numeric cost (Integer mismatch -> verbatim converter text).
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"cost\":\"abc\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered cost is invalid.")));
    assertEquals(before, fingerprint(661, 2023));
  }

  // ---- AC4: writes outside Draft -> 409 (S17 write half, deviation (a)) ------------------------

  @Test
  @DisplayName(
      "S17: POST/PUT records and PUT general-comments on a non-Draft mill -> 409; untouched")
  void nonDraftTrack_returns409() throws Exception {
    String before = fingerprint(662, 2021);
    String post =
        """
            {"areaType":"01","supplyBlock":"01B","cost":100}
            """;
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(post))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    String putBody =
        """
            {"areaType":"01","supplyBlock":"01B","cost":100,"revisionCount":0}
            """;
    mockMvc
        .perform(
            put(RECORDS + "/8321")
                .with(csrf())
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(putBody))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"nope\"}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(before, fingerprint(662, 2021));
  }

  // ---- AC6: optimistic lock per record (AR11) --------------------------------------------------

  @Test
  @DisplayName(
      "AC6: PUT with the current revision succeeds and bumps; re-PUT with the stale token -> "
          + "409 without persisting; omitting revisionCount -> clean 400")
  void optimisticLockPerRecord() throws Exception {
    // 2021's seeded record 8336; this year belongs to this test alone.
    int revision = currentRevision(661, 2021, 8336);
    String edit =
        """
            {"areaType":"05","supplyBlock":"05A","volume":100,"cost":1000,"revisionCount":%d}
            """
            .formatted(revision);
    mockMvc
        .perform(
            put(RECORDS + "/8336")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(edit))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.roadRecords[?(@.recordId==8336)].revisionCount", contains(revision + 1)));

    // The SAME token again is now stale -> 409, and the classification did not change back.
    String stale =
        """
            {"areaType":"01","supplyBlock":"01B","volume":1,"cost":1,"revisionCount":%d}
            """
            .formatted(revision);
    mockMvc
        .perform(
            put(RECORDS + "/8336")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(stale))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath(
                "$.detail",
                is("This schedule was changed by another user. Please reload and try again.")));
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "661")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8336)].areaType", contains("05")));

    // OnUpdate group: a PUT without the token is a clean 400, never a coerced 409.
    mockMvc
        .perform(
            put(RECORDS + "/8336")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"05\",\"supplyBlock\":\"05A\",\"cost\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Revision count is required for an update.")));
  }

  @Test
  @DisplayName("AC6: PUT an unknown id, a foreign mill's id, or the placeholder id -> 404")
  void unknownForeignOrPlaceholderId_returns404() throws Exception {
    String body =
        """
            {"areaType":"01","supplyBlock":"01B","cost":100,"revisionCount":0}
            """;
    // Unknown id under a valid Draft context.
    mockMvc
        .perform(
            put(RECORDS + "/79999")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));
    // 8334 belongs to mill 661/2019 — addressing it via another year is foreign (IDOR guard).
    mockMvc
        .perform(
            put(RECORDS + "/8334")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));
    // 8340 is 661/2018's general-comment placeholder: not a served record -> 404, never a silent
    // conversion into a real record. Deliberately NOT mill 664's placeholder 8324, which this
    // used to target: 664 is declared read-only by V32 and fingerprinted by
    // Schedule6CheckStatusIT, so a regression in the placeholder guard would have corrupted that
    // suite's ordinals instead of failing here (code review 2026-08-04).
    mockMvc
        .perform(
            put(RECORDS + "/8340")
                .with(csrf())
                .param("millId", "661")
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));
    // ...and it is still a placeholder afterwards: the guard rejected, it did not convert.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> placeholder =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, REVISION_COUNT
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8340
            """);
    assertEquals(null, placeholder.get("TSA_NUMBER"));
    assertEquals(null, placeholder.get("TSB_NUMBER_CODE"));
    assertEquals(null, placeholder.get("TFL_NUMBER_CODE"));
    assertEquals(0, ((Number) placeholder.get("REVISION_COUNT")).intValue());
  }

  // ---- Context guard reused (the 8.1 contract) -------------------------------------------------

  @Test
  @DisplayName("POST with a missing millId -> 400 verbatim ERR-001 (trailing space)")
  void missingContext_returns400Err001() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01B\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")));
  }

  /** Read a record's current revisionCount via GET so edits never hard-code a token. */
  private int currentRevision(long millId, int year, int recordId) throws Exception {
    String json =
        mockMvc
            .perform(
                get(ENDPOINT)
                    .param("millId", String.valueOf(millId))
                    .param("year", String.valueOf(year))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (JsonNode record : mapper.readTree(json).get("roadRecords")) {
      if (record.get("recordId").asInt() == recordId) {
        return record.get("revisionCount").asInt();
      }
    }
    throw new IllegalStateException("record " + recordId + " not found");
  }

  /** The new record's DB id, located by its served areaType (sequence ids are not predictable). */
  private int recordIdByAreaType(String json, String areaType) throws Exception {
    for (JsonNode record : mapper.readTree(json).get("roadRecords")) {
      if (areaType.equals(record.path("areaType").asText())) {
        return record.get("recordId").asInt();
      }
    }
    throw new IllegalStateException("no record with areaType " + areaType);
  }

  /**
   * The nothing-persisted proof for a context: a PER-ROW snapshot of every column a Schedule 6
   * write can touch, master and detail, ordered deterministically.
   *
   * <p>This used to be three numbers — master count, detail count, {@code SUM(REVISION_COUNT)} —
   * which was blind to most of what this story writes: {@code updateAllComments}, {@code
   * claimPlaceholder} and {@code updateCostDetail} all deliberately leave master {@code
   * REVISION_COUNT} alone, so a COMMENTS overwrite, a classification claim, or any detail-column
   * write left all three identical (code review 2026-08-04). Note also that the rejections this
   * guards fail in Bean Validation, in {@code classify()} or in {@code requireDraft()} — before any
   * repository call — so {@code @Transactional} is the real protection and this is the belt to its
   * braces.
   */
  private String fingerprint(long millId, int year) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    return jdbc.queryForList(
            """
            SELECT r.ROAD_MAINTENANCE_REPORT_ID, r.TSA_NUMBER, r.TSB_NUMBER_CODE,
                   r.TFL_NUMBER_CODE, r.COMMENTS, r.REVISION_COUNT,
                   r.ENTRY_USERID, r.ENTRY_TIMESTAMP, r.UPDATE_USERID, r.UPDATE_TIMESTAMP,
                   d.ILCR_COST_REPORT_DETAIL_ID, d.VOLUME, d.COST, d.COMMENTS AS DETAIL_COMMENTS,
                   d.REVISION_COUNT AS DETAIL_REVISION, d.ENTRY_USERID AS DETAIL_ENTRY_USERID,
                   d.UPDATE_USERID AS DETAIL_UPDATE_USERID,
                   d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TIMESTAMP
              FROM THE.ROAD_MAINTENANCE_REPORT r
              LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d
                ON d.ROAD_MAINTENANCE_REPORT_ID = r.ROAD_MAINTENANCE_REPORT_ID
               AND d.ILCR_REPORT_COST_ITEM_ID = 69
             WHERE r.ILCR_MILL_ID = ? AND r.REPORT_YEAR = ? AND r.ILCR_CATEGORY_ID = '6'
             ORDER BY r.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
            """,
            millId,
            year)
        .toString();
  }
}
