package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Story 8.2/Task 8 acceptance — {@code POST /api/v1/schedule6/records} (AC1-AC4; slices
 * S01/S03/S05/S12-S16/S17-write-POST-half; AD-5, AD-9, AD-10, AD-12). The mock {@code
 * ILCR_SUBMITTER} holds both VIEW and EDIT; authz is proven in {@link
 * Schedule6WriteAuthorizationIT}. Security-off is pinned EXPLICITLY and every mutation carries
 * {@code .with(csrf())} — no-ops today, but they keep this suite green when a fail-closed security
 * default merges in (the recorded merge-regression guard).
 *
 * <p>{@code PUT /records/{recordId}} and {@code PUT /general-comments} — the two
 * per-row/independent write endpoints this class used to cover alongside POST — are retired (Task
 * 8; the frontend has called only the whole-document {@code PUT /api/v1/schedule6} since Task 7).
 * Their behaviour-asserting cases live on: the item-69 detail upsert's two branches and the AC6
 * optimistic lock / 404-vs-409 disambiguation moved to {@link Schedule6SaveDocumentIT}, which
 * already covers the shape-level cases (unknown/foreign/placeholder id, stale token, missing token)
 * from Task 5. This class also absorbs four {@code POST /records} tests that used to live in the
 * now-deleted {@code Schedule6GeneralCommentsIT} (that class existed only to cover the retired
 * {@code PUT /general-comments}; its few POST-only cases belong here instead) — mill 665's V32
 * fixtures moved with them.
 *
 * <p>Mutating tests are ORDER-INDEPENDENT via the V32 context model: a context is (mill, YEAR), so
 * each destructive test method claims its own year — mill 661: 2019 add-TSA, 2020 add-TFL, 2024 TFL
 * alias (2023 is the never-mutated rejection-fingerprint year); mill 665: 2018 add-carries-comment,
 * 2020 over-wide classification, 2022 placeholder-reuse, 2024 overlong per-record comment. This
 * class is POST-only now (Task 8 retired the per-record {@code PUT} it used to also cover — see
 * {@link Schedule6SaveDocumentIT} for the edit/optimistic-lock cases that replaced it), so every
 * record here is freshly assigned; assertions locate records by id/field (JSONPath filters), not
 * array index, because sequence-assigned ids are not predictable.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule6/records — Schedule 6 writes (Story 8.2/Task 8)")
class Schedule6WriteIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule6/records";
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

    // SUC-003 clean follow-up: check-status is payload-driven (Task 6/Task 8), so the clean
    // verdict is proven by submitting the two now-persisted, now-complete 2019 records exactly as
    // the GET above just served them -> the single MET banner.
    String checkBody =
        """
        {"generalComments":null,
         "records":[{"areaType":"01","supplyBlock":"01B","volume":1000,"cost":50000},
                    {"areaType":"03","supplyBlock":"03B","volume":400,"cost":20000}]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "661")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkBody))
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
  @DisplayName("S17: POST records on a non-Draft mill -> 409; untouched")
  void nonDraftTrack_returns409() throws Exception {
    // The PUT /records/{id} and PUT /general-comments legs this test used to also exercise are
    // gone (Task 8); the identical Draft-gate behaviour for the whole-document PUT that replaced
    // them is proven by Schedule6SaveDocumentIT#nonDraftTrackReturns409.
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
    assertEquals(before, fingerprint(662, 2021));
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

  // ---- Ported from the retired Schedule6GeneralCommentsIT (POST /records only; that class existed
  // only to cover the now-retired PUT /general-comments) -----------------------------------------

  @Test
  @DisplayName(
      "BR-09: POST a record into a context that already has a general comment -> the new "
          + "row carries that comment, so the served generalComments is unchanged (the insert-side "
          + "replication invariant; previously only mock-verified)")
  void addRecord_carriesCurrentGeneralComment() throws Exception {
    // 665/2018 holds record 8357 with COMMENTS 'Carried general comment.'.
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"areaType\":\"03\",\"supplyBlock\":\"03B\",\"volume\":50,\"cost\":500}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords", hasSize(2)))
        .andExpect(jsonPath("$.generalComments", is("Carried general comment.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // The NEW row (highest id) must carry it: the read side takes the last row's COMMENTS, so a
    // NULL here would silently blank the schedule's General Comment on every later GET.
    assertEquals(
        "Carried general comment.",
        jdbc.queryForObject(
            """
            SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ROAD_MAINTENANCE_REPORT_ID =
                   (SELECT MAX(ROAD_MAINTENANCE_REPORT_ID) FROM THE.ROAD_MAINTENANCE_REPORT
                     WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2018 AND ILCR_CATEGORY_ID = '6')
            """,
            String.class));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "665")
                .param("year", "2018")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("Carried general comment.")));
  }

  @Test
  @DisplayName(
      "BR-09: POST a record when only the placeholder exists -> the placeholder row is "
          + "REUSED (same id, ENTRY_* survive, comment retained) and its detail is created")
  void addRecord_reusesLonePlaceholder() throws Exception {
    // 8331 is 2022's seeded placeholder ('Lone comment to reuse', ENTRY_USERID 'SEED').
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"volume\":100,\"cost\":5000}"))
        .andExpect(status().isOk())
        // The served record carries the PLACEHOLDER's id — the row was claimed, not added.
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8331)].areaType", contains("01")))
        .andExpect(jsonPath("$.roadRecords", hasSize(1)))
        .andExpect(jsonPath("$.generalComments", is("Lone comment to reuse.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // Still exactly ONE row for the context; its identity and ENTRY_* survived the claim.
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2022 AND ILCR_CATEGORY_ID = '6'
            """,
            Integer.class));
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, COMMENTS, ENTRY_USERID, UPDATE_USERID
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8331
            """);
    assertEquals("01", row.get("TSA_NUMBER"));
    assertEquals("01B", row.get("TSB_NUMBER_CODE"));
    assertEquals("Lone comment to reuse.", row.get("COMMENTS"));
    assertEquals("SEED", row.get("ENTRY_USERID"));
    assertEquals("dev-submitter", row.get("UPDATE_USERID"));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8331 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class));
  }

  @Test
  @DisplayName(
      "A PER-RECORD comment beyond 400 -> 400, NOT the 500 that ORA-12899 would produce: "
          + "it lands in ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE), a different and much "
          + "narrower column than the general comment's (code review 2026-08-04). 400 itself passes.")
  void addRecord_overlongPerRecordComment_returns400() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    int before =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2024 AND ILCR_CATEGORY_ID = '6'
            """,
            Integer.class);

    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"comments\":\""
                        + "x".repeat(401)
                        + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        // Its own key/text: the shared 3500 message would misstate the limit by 8.75x.
        .andExpect(jsonPath("$.detail", is("Comments must be 400 characters or fewer.")));

    assertEquals(
        before,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2024 AND ILCR_CATEGORY_ID = '6'
            """,
            Integer.class));

    // Exactly at the cap the write succeeds — proving the boundary is the column width, not a
    // value Oracle would still reject.
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"areaType\":\"01\",\"supplyBlock\":\"01B\",\"comments\":\""
                        + "y".repeat(400)
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords[0].comments", is("y".repeat(400))));
  }

  @Test
  @DisplayName(
      "An over-wide TSA area type / supply block -> 400, not the ORA-12899 500 it used to "
          + "produce (TSA_NUMBER VARCHAR2(2), TSB_NUMBER_CODE VARCHAR2(3))")
  void addRecord_overWideClassification_returns400() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"999\",\"supplyBlock\":\"01B\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is("A valid value must be selected from the list.")));

    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"9999\",\"supplyBlock\":\"01B\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areaType\":\"01\",\"supplyBlock\":\"01BX\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2020 AND ILCR_CATEGORY_ID = '6'
            """,
            Integer.class));
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
