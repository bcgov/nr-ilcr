package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * Story 8.2 acceptance — {@code PUT /api/v1/schedule6/general-comments} (AC5; slice S04 + the BR-09
 * lone-comment mechanics). Mill 665 carries one Draft year per destructive concern (the V32 context
 * model): 2019 empty (insert-placeholder), 2021 placeholder-only (delete-on-blank), 2022
 * placeholder-only (reuse-on-add), 2023 two real records (replication invariant), 2020/2024 spare
 * empties. Security-off pinned; every mutation carries {@code .with(csrf())}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("PUT /api/v1/schedule6/general-comments — BR-09 comment mechanics (Story 8.2)")
class Schedule6GeneralCommentsIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule6";
  private static final String COMMENTS = "/api/v1/schedule6/general-comments";
  private static final String RECORDS = "/api/v1/schedule6/records";
  private static final String PROBLEM_JSON = "application/problem+json";

  @Autowired private DataSource dataSource;

  // ---- Branch 1: rows exist -> the comment is replicated onto EVERY cat-6 row ------------------

  @Test
  @DisplayName(
      "S04: PUT with records present -> 200 saved; COMMENTS equal on every row (replication)")
  void saveWithRecords_replicatesOntoEveryRow() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"Replicated 2023 update\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.generalComments", is("Replicated 2023 update")));

    // The replication invariant, proven on the rows themselves: every cat-6 row equal, and the
    // UPDATE_* stamps moved (an UPDATE that drops its audit stamps cannot fail on a NOT NULL
    // column, so only an assertion catches it — code review 2026-08-04).
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
      assertEquals("dev-submitter", row.get("UPDATE_USERID"));
      assertNotNull(row.get("UPDATE_TIMESTAMP"));
    }

    // The mill/year IDOR scoping of updateAllComments: a neighbouring context of the SAME mill
    // keeps its own comment. Without this an UPDATE missing "AND REPORT_YEAR = :year" passes.
    assertEquals(
        "Carried general comment.",
        jdbc.queryForObject(
            """
            SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ROAD_MAINTENANCE_REPORT_ID = 8357
            """,
            String.class));

    // AC5: a FRESH GET reflects it too — the write response is built in-transaction from the
    // same buildDocument call, so it alone cannot prove the mutation committed and re-reads.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "665")
                .param("year", "2023")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("Replicated 2023 update")));
  }

  // ---- BR-09 replication on ADD: a new record carries the CURRENT general comment --------------

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

  // ---- Branch 2: zero rows + non-blank -> the placeholder row is inserted ----------------------

  @Test
  @DisplayName(
      "S04/BR-09: PUT on a mill/year with no rows -> placeholder row inserted "
          + "(classification NULL, no item-69 detail), document still lists no records")
  void saveOnEmpty_insertsPlaceholder() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"First ever comment\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("First ever comment")))
        .andExpect(jsonPath("$.roadRecords", hasSize(0)));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            SELECT ROAD_MAINTENANCE_REPORT_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE,
                   COMMENTS, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID
              FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2019 AND ILCR_CATEGORY_ID = '6'
            """);
    assertNull(row.get("TSA_NUMBER"));
    assertNull(row.get("TSB_NUMBER_CODE"));
    assertNull(row.get("TFL_NUMBER_CODE"));
    assertEquals("First ever comment", row.get("COMMENTS"));
    assertEquals(0, ((Number) row.get("REVISION_COUNT")).intValue());
    assertEquals("dev-submitter", row.get("ENTRY_USERID"));
    // The placeholder is BARE: no item-69 detail (Schedule6DAO.java:263-267).
    int placeholderId = ((Number) row.get("ROAD_MAINTENANCE_REPORT_ID")).intValue();
    assertEquals(
        0,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_MAINTENANCE_REPORT_ID = ?
            """,
            Integer.class,
            placeholderId));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "665")
                .param("year", "2019")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments", is("First ever comment")))
        .andExpect(jsonPath("$.roadRecords", hasSize(0)));
  }

  // ---- Branch 3: placeholder-only + blank -> the placeholder row is deleted --------------------

  @Test
  @DisplayName("S04/BR-09: blank PUT when only the placeholder exists -> the row is deleted")
  void clearOnPlaceholderOnly_deletesPlaceholder() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments").doesNotExist())
        .andExpect(jsonPath("$.roadRecords", hasSize(0)));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 665 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '6'
            """,
            Integer.class));

    // The DELETE's mill/year scoping: every OTHER placeholder survives. Without this an unscoped
    // DELETE — which satisfies the COUNT(*)=0 above — would wipe every placeholder in the schema
    // and nothing would notice. Order-independent: 8331 survives whether or not the reuse test
    // has claimed it (a claim converts the row, it does not remove it).
    assertEquals(
        3,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ROAD_MAINTENANCE_REPORT_ID IN (8331, 8340, 8324)
            """,
            Integer.class));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "665")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments").doesNotExist())
        .andExpect(jsonPath("$.roadRecords", hasSize(0)));
  }

  // ---- Placeholder REUSE on add (the BR-09 fourth mechanic, Schedule6DAO.java:268-278) ---------

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

  // ---- Blank on empty is a no-op; the length cap holds -----------------------------------------

  @Test
  @DisplayName("Blank PUT on an empty mill/year -> 200 no-op (nothing inserted)")
  void clearOnEmpty_isNoOp() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generalComments").doesNotExist());
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

  @Test
  @DisplayName(
      "General comments beyond 3500 -> 400 verbatim cap (deviation (g); non-mutating). "
          + "3500 is correct HERE: the general comment lands in ROAD_MAINTENANCE_REPORT.COMMENTS "
          + "VARCHAR2(4000) — see addRecord_overlongPerRecordComment for the narrower detail column")
  void overlongComment_returns400() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "665")
                .param("year", "2024")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"" + "x".repeat(3501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is("Comments must be 3500 characters or fewer.")));
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
}
