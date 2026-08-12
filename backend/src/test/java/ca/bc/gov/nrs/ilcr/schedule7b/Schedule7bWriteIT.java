package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Story 13.2 acceptance — culvert write paths for {@code /api/v1/schedule7b/culverts} (AC1-AC8/AC10,
 * slices S01-S10/S14/S21-S23/S25/S29). All mutations target mill 515 (active, Draft, and initially
 * empty of culverts) so they never disturb the seeded read/check-status fixtures on 514/517. Security
 * OFF; authz is proven in {@link Schedule7bAuthorizationIT}.
 */
@DisplayName("Schedule 7B culvert writes (Story 13.2)")
class Schedule7bWriteIT extends AbstractOracleIT {

  private static final String CULVERTS = "/api/v1/schedule7b/culverts";
  private static final String MILL = "515";
  private static final String YEAR = "2021";

  @Autowired private JdbcTemplate jdbc;

  /**
   * Mill 515/2021 is the shared, initially-empty write fixture; the container is one-per-JVM with no
   * per-test rollback (see {@link AbstractOracleIT}). Remove everything these tests commit so mill 515
   * is empty again for the next test AND for {@code Schedule7bContextGuardIT.emptyList_returns200},
   * which asserts it empty — that guard must not depend on Failsafe class order.
   */
  @AfterEach
  void cleanupMill515() {
    clearMill515();
  }

  /**
   * Assert the precondition rather than inherit it. Emptiness used to be produced solely by this
   * class's own {@code @AfterEach}, which two OTHER classes then depended on
   * ({@code Schedule7bContextGuardIT.emptyList_returns200},
   * {@code Schedule7bCheckStatusIT.emptyScheduleIsAllMet}). One test dying before its cleanup ran, or
   * anything else writing to 515, turned those into order-dependent failures that read like
   * production bugs.
   */
  @BeforeEach
  void mill515StartsEmpty() {
    clearMill515();
    assertThat(culvertCountForMill515()).isZero();
  }

  private void clearMill515() {
    jdbc.update(
        "DELETE FROM THE.ILCR_COST_REPORT_DETAIL WHERE CULVERT_REPORT_ID IN "
            + "(SELECT CULVERT_REPORT_ID FROM THE.CULVERT_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7')");
    jdbc.update(
        "DELETE FROM THE.CULVERT_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7'");
  }

  private Integer culvertCountForMill515() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.CULVERT_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7'",
        Integer.class);
  }

  /** Cost rows for ONE culvert — scoped by id, not mill-wide, so a stray row cannot skew a count. */
  private Integer costRowCount(long culvertId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CULVERT_REPORT_ID = ? AND ILCR_REPORT_COST_ITEM_ID IN (77, 78)",
        Integer.class, culvertId);
  }

  /** A valid Round culvert (total 5500). {@code revisionCount} matters only on a PUT. */
  private static String validBody(Integer revisionCount) {
    return """
        {
          "culvertTypeCode": "R",
          "spanSize": 1200,
          "riseSize": 900,
          "length": 12.5,
          "culvertPieceCount": 3,
          "materialCost": 4000,
          "installCost": 1500,
          "comments": "Write fixture",
          "revisionCount": %s
        }
        """.formatted(revisionCount == null ? "null" : revisionCount.toString());
  }

  private ResultActions postCulvert(String body) throws Exception {
    return mockMvc.perform(post(CULVERTS).param("millId", MILL).param("year", YEAR)
        .contentType(MediaType.APPLICATION_JSON).content(body)
        .accept(MediaType.APPLICATION_JSON));
  }

  /**
   * Create one culvert and return its generated id. Reads the LAST entry of the echoed document, not
   * the first: the document is ordered by {@code CULVERT_REPORT_ID} ascending and the ids come from a
   * sequence, so the culvert this call just created is always the last one — reading index 0 would
   * hand back the earliest culvert and make two successive calls return the same id.
   */
  private long createCulvert() throws Exception {
    String json = postCulvert(validBody(null)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(json, "$.culverts[-1].culvertReportId")).longValue();
  }

  // ===============================================================================================
  // Record (AC1)
  // ===============================================================================================

  @Test
  @DisplayName("AC1/S01: recording a culvert persists it, recomputes the total, echoes SUC-001")
  void recordCulvert() throws Exception {
    postCulvert(validBody(null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("R")))
        .andExpect(jsonPath("$.culverts[0].spanSize", is(1200)))
        .andExpect(jsonPath("$.culverts[0].length", is(12.5)))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(5500)))
        .andExpect(jsonPath("$.culverts[0].revisionCount", is(0)))
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
  }

  @Test
  @DisplayName("AC1/S02: comments persist as entered")
  void commentsPersist() throws Exception {
    postCulvert(validBody(null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].comments", is("Write fixture")));
  }

  @Test
  @DisplayName("AC1: BOTH cost rows are written even when both costs are blank (legacy storage shape)")
  void blankCostsStillWriteBothRows() throws Exception {
    String json = postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": null, "installCost": null,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].totalCost").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    long id = ((Number) JsonPath.read(json, "$.culverts[-1].culvertReportId")).longValue();

    // Two rows with COST NULL — never zero rows. A missing row would be permanently uneditable from
    // the legacy screen, which has no insert path on its update branch. Counted for THIS culvert's id
    // rather than mill-wide, so a stray row from anywhere else cannot make this pass or fail wrongly.
    assertThat(costRowCount(id)).isEqualTo(2);
    Integer nonNull = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CULVERT_REPORT_ID = ? "
            + "AND ILCR_REPORT_COST_ITEM_ID IN (77, 78) AND COST IS NOT NULL",
        Integer.class, id);
    assertThat(nonNull).isZero();
  }

  @Test
  @DisplayName("AC1: span, rise, length and both costs are OPTIONAL at Save (only Type + Pieces)")
  void onlyTypeAndPieceCountAreRequired() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": null, "riseSize": null, "length": null,
          "culvertPieceCount": 1, "materialCost": null, "installCost": null,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertPieceCount", is(1)));
  }

  // ===============================================================================================
  // Correct (AC2)
  // ===============================================================================================

  @Test
  @DisplayName("AC2/S03: correcting a culvert persists, bumps the revision, recomputes the total")
  void correctCulvert() throws Exception {
    long id = createCulvert();

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "O", "spanSize": null, "riseSize": null, "length": 9.5,
                  "culvertPieceCount": 5, "materialCost": 100, "installCost": 200,
                  "comments": "corrected", "revisionCount": 0
                }
                """)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("O")))
        .andExpect(jsonPath("$.culverts[0].length", is(9.5)))
        .andExpect(jsonPath("$.culverts[0].culvertPieceCount", is(5)))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(300)))
        .andExpect(jsonPath("$.culverts[0].comments", is("corrected")))
        .andExpect(jsonPath("$.culverts[0].revisionCount", is(1)))
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")));
  }

  @Test
  @DisplayName("AC2: a stale revisionCount -> 409, and the stored row is unchanged")
  void staleRevisionReturns409() throws Exception {
    long id = createCulvert();

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "O", "spanSize": null, "riseSize": null, "length": 9.5,
                  "culvertPieceCount": 5, "materialCost": 100, "installCost": 200,
                  "comments": "should not land", "revisionCount": 99
                }
                """))
        .andExpect(status().isConflict());

    // The DisplayName promises the row is unchanged, so assert it: a rejected optimistic-lock write
    // must not have applied any part of the body, and must not have bumped the revision.
    var stored = jdbc.queryForMap(
        "SELECT ILCR_CULVERT_TYPE_CODE, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT "
            + "FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = ?", id);
    assertThat(stored).containsEntry("ILCR_CULVERT_TYPE_CODE", "R");
    assertThat(((Number) stored.get("CULVERT_PIECE_COUNT")).intValue()).isEqualTo(3);
    assertThat(stored).containsEntry("COMMENTS", "Write fixture");
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
  }

  @Test
  @DisplayName("AC2: an omitted revisionCount on a PUT is a clean 400, never a coerced 409")
  void omittedRevisionCountReturns400() throws Exception {
    long id = createCulvert();

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
                  "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
                  "comments": null
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("AC2: a PUT against an unknown culvert id -> 404")
  void unknownIdReturns404() throws Exception {
    mockMvc.perform(put(CULVERTS + "/999999").param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody(0)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Culvert report not found.")));
  }

  @Test
  @DisplayName("AC2: a PUT against ANOTHER mill's culvert -> 404 (IDOR guard, not 200)")
  void foreignMillCulvertReturns404() throws Exception {
    // 7801 belongs to mill 514; addressing it as mill 515 must not resolve.
    mockMvc.perform(put(CULVERTS + "/7801").param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody(0)))
        .andExpect(status().isNotFound());
  }

  // ===============================================================================================
  // Save all (AC3)
  // ===============================================================================================

  @Test
  @DisplayName("AC3: the page-level Save persists every culvert in one transaction")
  void saveAllPersistsEveryCulvert() throws Exception {
    long first = createCulvert();
    long second = createCulvert();

    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"culverts": [
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "PA", "spanSize": null,
                    "riseSize": null, "length": 1.0, "culvertPieceCount": 1, "materialCost": 10,
                    "installCost": 20, "comments": null, "revisionCount": 0}},
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "VE", "spanSize": null,
                    "riseSize": null, "length": 2.0, "culvertPieceCount": 2, "materialCost": 30,
                    "installCost": 40, "comments": null, "revisionCount": 0}}
                ]}
                """.formatted(first, second))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(2)))
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("PA")))
        .andExpect(jsonPath("$.culverts[0].totalCost", is(30)))
        .andExpect(jsonPath("$.culverts[1].culvertTypeCode", is("VE")))
        .andExpect(jsonPath("$.culverts[1].totalCost", is(70)));
  }

  @Test
  @DisplayName("AC3: one bad entry rolls the WHOLE batch back — no partial save")
  void batchIsAtomic() throws Exception {
    long first = createCulvert();
    long second = createCulvert();

    // The second entry carries a stale revision, so the batch must fail AFTER the first entry's
    // update has already run in the same transaction.
    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"culverts": [
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "PA", "spanSize": null,
                    "riseSize": null, "length": 1.0, "culvertPieceCount": 1, "materialCost": 10,
                    "installCost": 20, "comments": null, "revisionCount": 0}},
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "VE", "spanSize": null,
                    "riseSize": null, "length": 2.0, "culvertPieceCount": 2, "materialCost": 30,
                    "installCost": 40, "comments": null, "revisionCount": 99}}
                ]}
                """.formatted(first, second))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict());

    // The first entry's type must NOT have been persisted.
    String storedType = jdbc.queryForObject(
        "SELECT ILCR_CULVERT_TYPE_CODE FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = ?",
        String.class, first);
    assertThat(storedType).isEqualTo("R");
  }

  @Test
  @DisplayName("AC3: an empty batch -> 400")
  void emptyBatchReturns400() throws Exception {
    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content("{\"culverts\": []}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("AC3: naming the same culvert twice -> 400, not a misleading 409")
  void duplicateBatchEntryReturns400() throws Exception {
    long id = createCulvert();

    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"culverts": [
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "PA", "spanSize": null,
                    "riseSize": null, "length": 1.0, "culvertPieceCount": 1, "materialCost": 10,
                    "installCost": 20, "comments": null, "revisionCount": 0}},
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "PA", "spanSize": null,
                    "riseSize": null, "length": 1.0, "culvertPieceCount": 1, "materialCost": 10,
                    "installCost": 20, "comments": null, "revisionCount": 0}}
                ]}
                """.formatted(id, id)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            is("The same culvert report was submitted more than once.")));
  }

  // ===============================================================================================
  // Delete (AC4)
  // ===============================================================================================

  @Test
  @DisplayName("AC4/S04: delete removes the culvert AND both cost rows, echoes SUC-002")
  void deleteRemovesCulvertAndCosts() throws Exception {
    long keep = createCulvert();
    long remove = createCulvert();

    mockMvc.perform(delete(CULVERTS + "/" + remove).param("millId", MILL).param("year", YEAR)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", hasSize(1)))
        .andExpect(jsonPath("$.culverts[0].culvertReportId", is((int) keep)))
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));

    Integer orphanCosts = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CULVERT_REPORT_ID = ?",
        Integer.class, remove);
    assertThat(orphanCosts).isZero();
  }

  @Test
  @DisplayName("AC4: deleting the LAST culvert still echoes SUC-002 — legacy 7B has no empty branch")
  void deleteLastCulvertStillReportsDeleted() throws Exception {
    long id = createCulvert();

    mockMvc.perform(delete(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts", is(empty())))
        // anyDataToSaveInfoMsg ("Any data was saved. The Schedule is empty.") appears exactly once in
        // the legacy source — Schedule7aMB.java:374. Legacy 7B's update() always emits the key it was
        // passed, so telling the reporter data "was saved" on a delete would be fabricated here.
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
  }

  @Test
  @DisplayName("AC4: deleting an unknown id -> 404")
  void deleteUnknownIdReturns404() throws Exception {
    mockMvc.perform(delete(CULVERTS + "/999999").param("millId", MILL).param("year", YEAR))
        .andExpect(status().isNotFound());
  }

  // ===============================================================================================
  // Gates and validation (AC5-AC8)
  // ===============================================================================================

  @Test
  @DisplayName("AC5/S14: every write against a non-Draft track (517/S) -> 409")
  void nonDraftWritesReturn409() throws Exception {
    mockMvc.perform(post(CULVERTS).param("millId", "517").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody(null)))
        .andExpect(status().isConflict());

    mockMvc.perform(put(CULVERTS + "/7851").param("millId", "517").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody(0)))
        .andExpect(status().isConflict());

    mockMvc.perform(delete(CULVERTS + "/7851").param("millId", "517").param("year", YEAR))
        .andExpect(status().isConflict());

    // Nothing was removed from the read fixture.
    Integer stillThere = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = 7851", Integer.class);
    assertThat(stillThere).isEqualTo(1);
  }

  @Test
  @DisplayName("AC6/S06: a missing culvert type -> 400 with the verbatim required-field message")
  void missingTypeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": null, "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Value Required")));
  }

  @Test
  @DisplayName("AC6/S07: a missing piece count -> 400 with the verbatim required-field message")
  void missingPieceCountReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": null, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Value Required")));
  }

  @Test
  @DisplayName("AC7/S08: span above 9,999,999 -> 400")
  void spanOutOfRangeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 10000000, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered span must be between 0 and 9,999,999.")));
  }

  @Test
  @DisplayName("AC7/S08: a negative rise -> 400")
  void negativeRiseReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": -1, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("AC7/S08: length above 999,999.9 -> 400")
  void lengthOutOfRangeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 1000000.0,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered culvert length must be between 0.0 and 999,999.9.")));
  }

  @Test
  @DisplayName("AC7/S21: piece count outside 1-9,999 -> 400 (0 is below the floor)")
  void pieceCountOutOfRangeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 0, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered number of pieces must be between 1 and 9,999.")));
  }

  @Test
  @DisplayName("AC7/S09: a cost outside the band -> 400 with the verbatim legacy cost message")
  void costOutOfRangeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 100000000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered cost must be between -99,999,999 and 99,999,999.")));
  }

  @Test
  @DisplayName("AC7/S10: a non-numeric cost -> 400 with the verbatim legacy converter message")
  void nonNumericCostReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": "abc", "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered cost is invalid.")));
  }

  @Test
  @DisplayName("AC7/S22: a non-numeric dimension -> 400")
  void nonNumericDimensionReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": "wide", "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        // Asserting the TEXT, not just the status: this used to answer "Entered cost is invalid."
        // because GlobalExceptionHandler picked the converter message from the target Java TYPE, so a
        // mistyped SPAN told the reporter their COST was invalid. A status-only assertion is exactly
        // where that hid.
        .andExpect(jsonPath("$.detail", is("Entered span is invalid.")));
  }

  @Test
  @DisplayName("AC7/S22: a non-numeric rise and piece count name THEIR OWN field, not the cost")
  void nonNumericRiseAndPieceCountNameTheirOwnField() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": "tall", "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered rise is invalid.")));

    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": "many", "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered number of pieces is invalid.")));
  }

  @Test
  @DisplayName("AC7: a length carrying extra decimals is ACCEPTED and rounded, as legacy did")
  void twoDecimalLengthIsAcceptedAndRounded() throws Exception {
    // 12.50 is the same number as the accepted 12.5 but arrives at scale 2; a @Digits(fraction = 1)
    // constraint rejected it with a RANGE message. 12.55 is what legacy let NUMBER(7,1) round.
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.50,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].length", is(12.5)));

    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.55,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[-1:].length", contains(12.6)));
  }

  @Test
  @DisplayName("AC7: a negative revisionCount is a 400, never a phantom 409")
  void negativeRevisionCountReturns400() throws Exception {
    long id = createCulvert();

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(validBody(-1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Revision count")));
  }

  @Test
  @DisplayName("AC7/S25: several field failures compose into ONE response, not just the first")
  void multipleFailuresComposeIntoOneResponse() throws Exception {
    String detail = postCulvert("""
        {
          "culvertTypeCode": null, "spanSize": 10000000, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 0, "materialCost": 100000000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andReturn().getResponse().getContentAsString();

    assertThat(JsonPath.<String>read(detail, "$.detail"))
        .contains("Value Required")
        .contains("Entered span must be between 0 and 9,999,999.")
        .contains("Entered number of pieces must be between 1 and 9,999.")
        .contains("Entered cost must be between -99,999,999 and 99,999,999.");
  }

  @Test
  @DisplayName("AC7: comments longer than 3,500 characters -> 400")
  void oversizedCommentsReturn400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": "%s", "revisionCount": null
        }
        """.formatted("x".repeat(3501)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Comments must be 3500 characters or fewer.")));
  }

  @Test
  @DisplayName("AC8: a culvert type retired before the reporting year -> 400, and nothing is stored")
  void expiredTypeCodeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "XOLD", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("The selected culvert type is not valid.")));

    Integer stored = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.CULVERT_REPORT "
            + "WHERE ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '7'",
        Integer.class);
    assertThat(stored).isZero();
  }

  @Test
  @DisplayName("AC8: a type that is not in the code table at all -> 400")
  void unknownTypeCodeReturns400() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "ZZZ", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("The selected culvert type is not valid.")));
  }

  @Test
  @DisplayName("AC8: a NULL-bounds code IS accepted (the NVL guard, write side)")
  void nullBoundedTypeCodeIsAccepted() throws Exception {
    postCulvert("""
        {
          "culvertTypeCode": "OPEN", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": null
        }
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("OPEN")));
  }

  // ===============================================================================================
  // Gaps closed by the 2026-08-11 code review
  // ===============================================================================================

  @Test
  @DisplayName("AC5: the page-level Save is Draft-gated too (517/S) -> 409, nothing mutated")
  void saveAllIsDraftGated() throws Exception {
    // The one write verb the Draft gate was never asserted on. Deleting requireDraft() from
    // saveAllCulverts left the whole suite green while a Submitted report could be mutated wholesale
    // — and gate integrity is a named PRD counter-metric.
    mockMvc.perform(put(CULVERTS).param("millId", "517").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"culverts": [
                  {"culvertReportId": 7851, "culvert": {"culvertTypeCode": "PA", "spanSize": null,
                    "riseSize": null, "length": 1.0, "culvertPieceCount": 1, "materialCost": 10,
                    "installCost": 20, "comments": "must not land", "revisionCount": 0}}
                ]}
                """))
        .andExpect(status().isConflict());

    var stored = jdbc.queryForMap(
        "SELECT CULVERT_PIECE_COUNT, REVISION_COUNT FROM THE.CULVERT_REPORT "
            + "WHERE CULVERT_REPORT_ID = 7851");
    assertThat(((Number) stored.get("CULVERT_PIECE_COUNT")).intValue()).isEqualTo(4);
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
  }

  @Test
  @DisplayName("AC8: the type check runs on correct and on save-all, not only on add")
  void typeIsValidatedOnUpdateAndSaveAll() throws Exception {
    long id = createCulvert();
    String withRetiredType = """
        {
          "culvertTypeCode": "XOLD", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": null, "revisionCount": 0
        }
        """;

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).content(withRetiredType))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("The selected culvert type is not valid.")));

    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"culverts\": [{\"culvertReportId\": " + id + ", \"culvert\": "
                + withRetiredType + "}]}"))
        .andExpect(status().isBadRequest());

    // Neither rejection may have changed the row.
    String storedType = jdbc.queryForObject(
        "SELECT ILCR_CULVERT_TYPE_CODE FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = ?",
        String.class, id);
    assertThat(storedType).isEqualTo("R");
  }

  @Test
  @DisplayName("AC8: a culvert already holding a retired type can still be corrected (mill 681)")
  void unchangedRetiredTypeDoesNotBlockTheSave() throws Exception {
    // 7871 is stored with XOLD, retired in 2015. Resubmitting that type unchanged while correcting
    // another field must succeed — otherwise a single legacy row with a since-retired code would 400
    // every page-level Save and no culvert on the page could ever be fixed.
    mockMvc.perform(put(CULVERTS + "/7871").param("millId", "681").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "XOLD", "spanSize": 750, "riseSize": 600, "length": 5.0,
                  "culvertPieceCount": 3, "materialCost": 50, "installCost": 60,
                  "comments": "corrected around a retired code", "revisionCount": 0
                }
                """)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.culverts[0].spanSize", is(750)))
        .andExpect(jsonPath("$.culverts[0].culvertTypeCode", is("XOLD")));

    // But CHANGING a type to a retired code is still rejected.
    mockMvc.perform(put(CULVERTS + "/7871").param("millId", "681").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "EXPJUN", "spanSize": 750, "riseSize": 600, "length": 5.0,
                  "culvertPieceCount": 3, "materialCost": 50, "installCost": 60,
                  "comments": "changing to a different code", "revisionCount": 1
                }
                """))
        .andExpect(status().isOk());
    mockMvc.perform(put(CULVERTS + "/7871").param("millId", "681").param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "MIDYR", "spanSize": 750, "riseSize": 600, "length": 5.0,
                  "culvertPieceCount": 3, "materialCost": 50, "installCost": 60,
                  "comments": "MIDYR is not effective for 2021", "revisionCount": 2
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("The selected culvert type is not valid.")));

    // Restore the fixture for any later class (this mill has no @AfterEach of its own).
    jdbc.update("UPDATE THE.CULVERT_REPORT SET ILCR_CULVERT_TYPE_CODE = 'XOLD', SPAN_SIZE = 700, "
        + "COMMENTS = 'Stored with a since-retired type', REVISION_COUNT = 0 "
        + "WHERE CULVERT_REPORT_ID = 7871");
  }

  @Test
  @DisplayName("A DELETE cannot reach another mill's culvert — 404, and the row plus costs survive")
  void deleteOfForeignMillCulvertReturns404() throws Exception {
    // The IDOR mirror of foreignMillCulvertReturns404, which only covered the PUT. Every other delete
    // test used a nonexistent id (999999), so dropping `AND ILCR_MILL_ID = :millId` from the DELETE
    // would have let mill 515 destroy mill 514's culvert — and, via the unscoped cost cascade, its
    // cost rows — while returning a cheerful 200.
    mockMvc.perform(delete(CULVERTS + "/7801").param("millId", MILL).param("year", YEAR))
        .andExpect(status().isNotFound());

    Integer culvert = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = 7801", Integer.class);
    assertThat(culvert).isEqualTo(1);
    assertThat(costRowCount(7801L)).isEqualTo(2);
  }

  @Test
  @DisplayName("A write cannot reach another REPORT_YEAR's culvert — 404, row untouched")
  void writeAgainstAnotherYearReturns404() throws Exception {
    // 7861 belongs to mill 680 / 2020. Addressing it under year=2021 must not resolve, which is what
    // makes the `AND REPORT_YEAR = :year` predicate on updateCulvert/deleteCulvert falsifiable.
    mockMvc.perform(put(CULVERTS + "/7861").param("millId", "680").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(validBody(0)))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete(CULVERTS + "/7861").param("millId", "680").param("year", "2021"))
        .andExpect(status().isNotFound());

    var stored = jdbc.queryForMap(
        "SELECT CULVERT_PIECE_COUNT, REVISION_COUNT FROM THE.CULVERT_REPORT "
            + "WHERE CULVERT_REPORT_ID = 7861");
    assertThat(((Number) stored.get("CULVERT_PIECE_COUNT")).intValue()).isEqualTo(1);
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
  }

  @Test
  @DisplayName("AC3: a batch entry omitting revisionCount is a 400, never a 500 NPE")
  void batchEntryWithoutRevisionCountReturns400() throws Exception {
    long id = createCulvert();

    // Proves the OnUpdate group propagates through @Valid List<Item> into each CulvertRequest. If it
    // ever stopped, revisionCount() would unbox null inside applyCulvertUpdate and NPE into a 500 —
    // the coerced-token failure mode OnUpdate exists to prevent.
    mockMvc.perform(put(CULVERTS).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"culverts": [
                  {"culvertReportId": %d, "culvert": {"culvertTypeCode": "R", "spanSize": 1200,
                    "riseSize": 900, "length": 12.5, "culvertPieceCount": 3, "materialCost": 4000,
                    "installCost": 1500, "comments": null}}
                ]}
                """.formatted(id)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("AC1: audit columns are stamped on insert and advanced on update")
  void auditColumnsAreStamped() throws Exception {
    long id = createCulvert();

    var afterInsert = jdbc.queryForMap(
        "SELECT ENTRY_USERID, UPDATE_USERID, ENTRY_TIMESTAMP, UPDATE_TIMESTAMP "
            + "FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = ?", id);
    // Security is OFF in this class, so the principal is the configured mock user; assert the columns
    // carry SOMETHING rather than pinning a name the mock config owns.
    assertThat((String) afterInsert.get("ENTRY_USERID")).isNotBlank();
    assertThat((String) afterInsert.get("UPDATE_USERID")).isNotBlank();
    assertThat(afterInsert.get("ENTRY_TIMESTAMP")).isNotNull();
    assertThat(afterInsert.get("UPDATE_TIMESTAMP")).isNotNull();

    // Both cost children carry audit stamps too.
    Integer unstampedCosts = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE CULVERT_REPORT_ID = ? "
            + "AND (ENTRY_USERID IS NULL OR UPDATE_USERID IS NULL OR UPDATE_TIMESTAMP IS NULL)",
        Integer.class, id);
    assertThat(unstampedCosts).isZero();

    mockMvc.perform(put(CULVERTS + "/" + id).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "culvertTypeCode": "R", "spanSize": 1300, "riseSize": 900, "length": 12.5,
                  "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
                  "comments": "audited correction", "revisionCount": 0
                }
                """))
        .andExpect(status().isOk());

    var afterUpdate = jdbc.queryForMap(
        "SELECT ENTRY_TIMESTAMP, UPDATE_TIMESTAMP FROM THE.CULVERT_REPORT "
            + "WHERE CULVERT_REPORT_ID = ?", id);
    // The correction advances UPDATE_TIMESTAMP and leaves ENTRY_TIMESTAMP alone.
    assertThat(afterUpdate).containsEntry("ENTRY_TIMESTAMP", afterInsert.get("ENTRY_TIMESTAMP"));
    assertThat((java.sql.Timestamp) afterUpdate.get("UPDATE_TIMESTAMP"))
        .isAfterOrEqualTo((java.sql.Timestamp) afterInsert.get("UPDATE_TIMESTAMP"));
  }

  @Test
  @DisplayName("AC10/S29: a persistence failure rolls the culvert AND both cost rows back together")
  void persistenceFailureRollsBackBothTables() throws Exception {
    // Force a real database rejection mid-write rather than mocking one: COMMENTS is VARCHAR2(4000
    // BYTE), so temporarily narrowing it makes the INSERT fail after the id has been drawn. What this
    // pins is the transaction boundary — a culvert must never survive without its two cost rows,
    // which per writeCosts' javadoc would be permanently uneditable from the legacy screen.
    // A CHECK constraint on a sentinel value, not a column narrowing: the seeded fixtures already hold
    // long comments, so any MODIFY that would reject the incoming row also rejects existing data
    // (ORA-01441) and the ALTER itself fails before the test can run.
    jdbc.execute("ALTER TABLE THE.CULVERT_REPORT ADD CONSTRAINT TMP_7B_FORCE_FAIL "
        + "CHECK (COMMENTS IS NULL OR COMMENTS <> 'FORCE-ROLLBACK')");
    try {
      postCulvert("""
          {
            "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
            "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
            "comments": "FORCE-ROLLBACK", "revisionCount": null
          }
          """)
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.detail", is("Schedule could not be saved.")));
    } finally {
      jdbc.execute("ALTER TABLE THE.CULVERT_REPORT DROP CONSTRAINT TMP_7B_FORCE_FAIL");
    }

    assertThat(culvertCountForMill515()).isZero();
    Integer orphanCosts = jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL d "
            + "WHERE NOT EXISTS (SELECT 1 FROM THE.CULVERT_REPORT c "
            + "WHERE c.CULVERT_REPORT_ID = d.CULVERT_REPORT_ID) "
            + "AND d.CULVERT_REPORT_ID IS NOT NULL",
        Integer.class);
    assertThat(orphanCosts).isZero();
  }

  @Test
  @DisplayName("A comment inside the character cap but over the 4,000-byte column is a 400, not a 500")
  void oversizedMultibyteCommentReturns400() throws Exception {
    // 3,000 two-byte characters: satisfies the 3,500-CHARACTER cap, overflows VARCHAR2(4000 BYTE).
    // Before the byte cap this reached Oracle, raised ORA-12899 and surfaced as an opaque 500.
    postCulvert("""
        {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": "%s", "revisionCount": null
        }
        """.formatted("é".repeat(3000)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Comments must be 3500 characters or fewer.")));

    assertThat(culvertCountForMill515()).isZero();
  }
}
