package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
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
 * Task 5/Task 8 acceptance — {@code PUT /api/v1/schedule6}: the whole-document save, restoring
 * legacy's single atomic {@code saveSchedule} ({@code Schedule6DAO.saveSchedule} :236-346) — every
 * road record plus the general comment in one transaction. This is now the ONLY road-record write
 * path: Task 8 retired the per-record {@code PUT /records/{recordId}} and the independent {@code
 * PUT /general-comments} once every row became always-editable (Task 7), and this class absorbed
 * the behaviour-asserting cases from their retired {@code Schedule6WriteIT}/{@code
 * Schedule6GeneralCommentsIT} suites (the item-69 detail upsert's two branches; the BR-09
 * comment-branch selection; the raw-untrimmed comment; 404-vs-409 disambiguation). Security-off is
 * pinned EXPLICITLY.
 *
 * <p>Fixture mills 724/725 (V20260822 migration, Task 5/Task 8 blocks) follow the same "one
 * destructive test per (mill, year) context" contract {@link Schedule6DeleteIT} established: every
 * MUTATING save owns a dedicated year, so JUnit's method-execution order can never let one test's
 * write corrupt another's fixture. A REJECTED save (400/404/409) never mutates anything, so several
 * read-only probes deliberately share one context (mill 724/2023, mill 724/2024) — see the fixture
 * file's comments for exactly which id belongs to which test.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("PUT /api/v1/schedule6 — Schedule 6 whole-document save (Task 5)")
class Schedule6SaveDocumentIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule6";

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("one PUT saves every row and the general comment in one transaction")
  void savesRowsAndCommentTogether() throws Exception {
    String body =
        """
        {"generalComments":"Saved together",
         "records":[{"recordId":8390,"revisionCount":0,"areaType":"03","supplyBlock":"03B",
                     "volume":1500,"cost":60000,"comments":"row one"}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.generalComments", is("Saved together")))
        .andExpect(jsonPath("$.roadRecords[0].recordId", is(8390)))
        .andExpect(jsonPath("$.roadRecords[0].areaType", is("03")))
        .andExpect(jsonPath("$.roadRecords[0].volume", is(1500)))
        .andExpect(jsonPath("$.roadRecords[0].cost", is(60000)))
        .andExpect(jsonPath("$.roadRecords[0].revisionCount", is(1)));

    // Durable on a fresh read, including the classification change and the bumped revision.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    var row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, COMMENTS, REVISION_COUNT
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8390
            """);
    assertEquals("03", row.get("TSA_NUMBER"));
    assertEquals("03B", row.get("TSB_NUMBER_CODE"));
    assertEquals("Saved together", row.get("COMMENTS"));
    assertEquals(1, ((Number) row.get("REVISION_COUNT")).intValue());
    assertEquals(
        1500,
        jdbc.queryForObject(
                "SELECT VOLUME FROM THE.ILCR_COST_REPORT_DETAIL "
                    + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8390 AND ILCR_REPORT_COST_ITEM_ID = 69",
                Number.class)
            .intValue());
  }

  @Test
  @DisplayName("omitting a served row is a 400, never a silent skip")
  void omittingAServedRowIsRejected() throws Exception {
    // Mill 724/2019 has TWO cat-6 rows (8391, 8392); send only the first. Legacy posted its whole
    // in-memory list so this could not arise; silently skipping the absent row would discard the
    // user's data behind a success message. Schedules 5 and 7.4 pinned the same 400.
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8391,"revisionCount":0,"areaType":"01","supplyBlock":"01B",
                     "volume":1,"cost":1,"comments":null}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("All existing road records must be included when saving.")));

    // Nothing persisted: both rows are untouched (revision still 0).
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2019 AND REVISION_COUNT = 0",
            Integer.class));
  }

  @Test
  @DisplayName("a stale revision token on any row is a 409 and nothing is written")
  void staleTokenRollsBackTheWholeSave() throws Exception {
    String body =
        """
        {"generalComments":"must not land",
         "records":[{"recordId":8393,"revisionCount":999,"areaType":"01","supplyBlock":"01B",
                     "volume":1,"cost":1,"comments":null}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());

    // The whole point of one transaction: the comment must NOT have landed even though it is
    // written after the rows.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2020 "
                + "AND ILCR_CATEGORY_ID = '6' AND COMMENTS = 'must not land'",
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8393",
            Integer.class));
  }

  @Test
  @DisplayName("an empty record list with a comment inserts the placeholder")
  void emptyListWithCommentInsertsPlaceholder() throws Exception {
    String body =
        """
        {"generalComments":"comment only","records":[]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords").isEmpty())
        .andExpect(jsonPath("$.generalComments", is("comment only")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '6' "
                + "AND COMMENTS = 'comment only' AND TSA_NUMBER IS NULL AND TFL_NUMBER_CODE IS NULL",
            Integer.class));
  }

  @Test
  @DisplayName("an empty record list with a blank comment deletes the lone placeholder")
  void emptyListWithBlankCommentDeletesPlaceholder() throws Exception {
    String body =
        """
        {"generalComments":null,"records":[]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords").isEmpty())
        .andExpect(jsonPath("$.generalComments").doesNotExist());

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2022 AND ILCR_CATEGORY_ID = '6'",
            Integer.class));
  }

  @Test
  @DisplayName(
      "code review 2026-08-21 (C1): an empty list plus a NEW comment writes onto the existing "
          + "lone placeholder, never inserts a second one")
  void emptyListWithNewCommentUpdatesExistingPlaceholderInPlace() throws Exception {
    // Mill 724/2025's ONLY row is placeholder 8398, carrying 'original comment'. The BR-09 branch
    // must key on the STORED rows (non-empty: one placeholder), not the submitted records list
    // (always empty here) -- branching on the submitted list collapsed this into the zero-rows
    // branch and inserted a SECOND placeholder, stranding 'original comment' on an orphan row
    // that `addRecord`'s lonePlaceholderId (rows.size() == 1) and `deleteRecord`'s wasOnlyRow could
    // no longer see (legacy Schedule6DAO.java:263,286's `onlyGeneralCommentsExist` guard).
    String body =
        """
        {"generalComments":"changed","records":[]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2025")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords").isEmpty())
        .andExpect(jsonPath("$.generalComments", is("changed")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2025 AND ILCR_CATEGORY_ID = '6'",
            Integer.class));
    assertEquals(
        "changed",
        jdbc.queryForObject(
            "SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8398",
            String.class));
  }

  @Test
  @DisplayName("a null revisionCount on an entry is a clean 400, never a coerced 409")
  void nullRevisionCountIsRejected() throws Exception {
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8395,"areaType":"01","supplyBlock":"01B","cost":100}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Revision count is required for an update.")));
  }

  @Test
  @DisplayName("a null entry in records[] is a clean 400, never an NPE-to-500 (final-review I1)")
  void nullEntryInRecords_returns400() throws Exception {
    // {"records":[null]} used to reach requireEveryServedRow (Schedule6Service.java) and NPE past
    // the catch (DataAccessException) into the catch-all 500 handler -- @NotNull carried no
    // element-type guard, unlike its Schedule6CheckRequest sibling. Mirrors
    // Schedule6CheckStatusIT#nullEntryInRecords_returns400.
    String body =
        """
        {"generalComments":null,"records":[null]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Value Required")));
  }

  @Test
  @DisplayName("field validation rejects with the verbatim FLD text; nothing persists")
  void fieldValidation_returns400() throws Exception {
    // S12: blank area type.
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"records":[{"recordId":8395,"revisionCount":0,"supplyBlock":"01B","cost":100}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("TSA or TFL: Value is required.")));

    // S05: invalid TFL number.
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"records":[{"recordId":8395,"revisionCount":0,"areaType":"TFL",
                                 "tflNumber":"99","cost":1}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Entered TFL number is not valid for Interior Regions.")));

    // S16: cost out of range.
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"records":[{"recordId":8395,"revisionCount":0,"areaType":"01",
                                 "supplyBlock":"01B","cost":100000000}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Entered cost must be between -99,999,999 and 99,999,999.")));

    // Nothing persisted across any of the above.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8395",
            Integer.class));
  }

  @Test
  @DisplayName("an unknown record id in the payload is a 404")
  void unknownIdIsRejected() throws Exception {
    // 8395 (mill 724/2023's only real row) must still be present to satisfy the omitted-rows
    // guard; the bogus 79999 entry is what triggers the 404.
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8395,"revisionCount":0,"areaType":"01","supplyBlock":"01B","cost":1},
                    {"recordId":79999,"revisionCount":0,"areaType":"01","supplyBlock":"01B","cost":1}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));

    // The transaction rolled back: 8395's real update never landed either.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8395",
            Integer.class));
  }

  @Test
  @DisplayName("a foreign mill/year's record id in the payload is a 404 -- the IDOR scope guard")
  void foreignRecordIdIsRejected() throws Exception {
    // 8395 belongs to mill 724/2023; addressing it via 724/2024 (whose only row is the placeholder
    // 8396, excluded from the omitted-rows check) is foreign.
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8395,"revisionCount":0,"areaType":"01","supplyBlock":"01B","cost":1}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));
  }

  @Test
  @DisplayName("a placeholder id in the payload is a 404 -- it is not a served record")
  void placeholderIdIsRejected() throws Exception {
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8396,"revisionCount":0,"areaType":"01","supplyBlock":"01B","cost":1}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));

    // The guard rejected, it did not convert: still a placeholder afterwards.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    var row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8396
            """);
    assertEquals(null, row.get("TSA_NUMBER"));
    assertEquals(null, row.get("TSB_NUMBER_CODE"));
    assertEquals(null, row.get("TFL_NUMBER_CODE"));
  }

  @Test
  @DisplayName("a non-Draft (status 'S') mill/year rejects the whole-document PUT with 409")
  void nonDraftTrackReturns409() throws Exception {
    String body =
        """
        {"generalComments":"nope",
         "records":[{"recordId":8397,"revisionCount":0,"areaType":"05","supplyBlock":"05A","cost":1}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "725")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    var row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, COMMENTS, REVISION_COUNT
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8397
            """);
    assertEquals("01", row.get("TSA_NUMBER"));
    assertEquals("01B", row.get("TSB_NUMBER_CODE"));
    assertEquals("Locked comment.", row.get("COMMENTS"));
    assertEquals(0, ((Number) row.get("REVISION_COUNT")).intValue());
  }

  // ---- Task 8: ported from the retired Schedule6WriteIT (PUT /records/{id})
  // -----------------------
  // These two prove the item-69 detail upsert's two branches, which none of the tests above happen
  // to exercise beyond a single INSERT: the S19 switch's no-detail-row INSERT, and the far less
  // obvious UPDATE-in-place branch on a row that already carries one. The suite that ported these
  // used to exercise only the INSERT branch, so an always-insert regression (duplicate details,
  // money invisible in totals) would have shipped green (code review 2026-08-04, on the original).

  @Test
  @DisplayName(
      "S19 (ported): switching TSA->TFL via the whole-document PUT clears the Supply Block, "
          + "re-derives the RMG, bumps the revision, and the detail upsert INSERTS on the "
          + "delivery-real no-detail row")
  void switchAreaTypeTsaToTfl() throws Exception {
    // 8374 (mill 724/2026) is seeded TSA 01/01B with NO item-69 detail -- the real delivery shape.
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8374,"revisionCount":0,"areaType":"TFL","tflNumber":"18",
                     "volume":300,"cost":15000}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2026")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8374)].areaType", contains("TFL")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8374)].tflNumber", contains("18")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8374)].supplyBlock", hasSize(0)))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8374)].rmg", contains("4")))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8374)].revisionCount", contains(1)));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    var row =
        jdbc.queryForMap(
            """
            SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8374
            """);
    assertEquals(null, row.get("TSA_NUMBER"));
    assertEquals(null, row.get("TSB_NUMBER_CODE"));
    assertEquals("18", row.get("TFL_NUMBER_CODE"));
    // The UPDATE's audit stamps moved off the seeded 'SEED'. An UPDATE that drops them cannot fail
    // on a NOT NULL column, so only an assertion catches it (code review 2026-08-04, carried across
    // from the original updateRecord test this one replaced).
    assertEquals("dev-submitter", row.get("UPDATE_USERID"));
    assertNotNull(row.get("UPDATE_TIMESTAMP"));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8374 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class));
  }

  @Test
  @DisplayName(
      "(ported): editing a record that ALREADY has an item-69 detail via the whole-document PUT "
          + "UPDATES that row rather than inserting a second one -- COUNT stays 1 and every written "
          + "column lands")
  void editWithExistingDetail_updatesInPlace() throws Exception {
    // 8375 (mill 724/2027) is seeded WITH detail 8383 (vol 1000 / cost 50000 / 'Seeded 2027
    // record').
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    int detailId =
        jdbc.queryForObject(
            """
            SELECT ILCR_COST_REPORT_DETAIL_ID FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8375 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class);
    String body =
        """
        {"generalComments":null,
         "records":[{"recordId":8375,"revisionCount":0,"areaType":"05","supplyBlock":"05B",
                     "volume":250,"cost":7500,"comments":"Edited in place"}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2027")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8375)].volume", contains(250)))
        .andExpect(jsonPath("$.roadRecords[?(@.recordId==8375)].cost", contains(7500)))
        .andExpect(
            jsonPath("$.roadRecords[?(@.recordId==8375)].comments", contains("Edited in place")));

    // COUNT == 1 AND the same detail id is what pins update-in-place over a second insert.
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8375 AND ILCR_REPORT_COST_ITEM_ID = 69
            """,
            Integer.class));
    var detail =
        jdbc.queryForMap(
            """
            SELECT ILCR_COST_REPORT_DETAIL_ID, VOLUME, COST, COMMENTS, REVISION_COUNT,
                   ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP
              FROM THE.ILCR_COST_REPORT_DETAIL
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8375 AND ILCR_REPORT_COST_ITEM_ID = 69
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

  @Test
  @DisplayName(
      "BR-09 (ported): rows exist -> the comment is replicated onto EVERY cat-6 row, "
          + "scoped to this mill/year only")
  void savingWithMultipleRows_replicatesCommentOntoEveryRow() throws Exception {
    // Mill 665/2023 (retired Schedule6GeneralCommentsIT fixture, V32) carries two real records
    // (8332 TSA, 8333 TFL) sharing the replicated comment.
    String body =
        """
        {"generalComments":"Replicated 2023 update",
         "records":[{"recordId":8332,"revisionCount":0,"areaType":"01","supplyBlock":"01B",
                     "cost":1},
                    {"recordId":8333,"revisionCount":0,"areaType":"TFL","tflNumber":"18",
                     "cost":1}]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("Replicated 2023 update")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT COMMENTS, UPDATE_USERID, UPDATE_TIMESTAMP FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2023 AND ILCR_CATEGORY_ID = '6'
            """);
    assertEquals(2, rows.size());
    for (Map<String, Object> row : rows) {
      assertEquals("Replicated 2023 update", row.get("COMMENTS"));
      // The replication invariant, proven on the rows themselves: the UPDATE_* stamps moved (an
      // UPDATE that drops its audit stamps cannot fail on a NOT NULL column, so only an assertion
      // catches it — code review 2026-08-04, carried across from the original this test replaced).
      assertEquals("dev-submitter", row.get("UPDATE_USERID"));
      assertNotNull(row.get("UPDATE_TIMESTAMP"));
    }

    // The mill/year IDOR scoping of updateAllComments: a neighbouring context of the SAME mill
    // (665/2018, record 8357) keeps its own comment. Without "AND REPORT_YEAR = :year" this would
    // land here too.
    assertEquals(
        "Carried general comment.",
        jdbc.queryForObject(
            "SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8357",
            String.class));
  }

  @Test
  @DisplayName(
      "(ported): a non-blank general comment is stored RAW, untrimmed (the 8.1 legacy-faithful "
          + "decision applies to the whole-document save too)")
  void generalComment_isStoredRawUntrimmed() throws Exception {
    String body =
        """
        {"generalComments":"  raw untrimmed  ","records":[]}
        """;
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2028")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("  raw untrimmed  ")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        "  raw untrimmed  ",
        jdbc.queryForObject(
            """
            SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 724 AND REPORT_YEAR = 2028 AND ILCR_CATEGORY_ID = '6'
            """,
            String.class));
  }

  @Test
  @DisplayName(
      "(ported): general comments beyond 3500 -> 400 verbatim cap (retired "
          + "Schedule6GeneralCommentsIT#overlongComment_returns400's only verbatim-message proof; "
          + "non-mutating, so it safely shares 724/2028 with the raw-untrimmed test above)")
  void generalComment_beyond3500_returns400Verbatim() throws Exception {
    String body =
        """
        {"generalComments":"%s","records":[]}
        """
            .formatted("x".repeat(3501));
    mockMvc
        .perform(
            put(ENDPOINT)
                .with(csrf())
                .param("millId", "724")
                .param("year", "2028")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Comments must be 3500 characters or fewer.")));
  }
}
