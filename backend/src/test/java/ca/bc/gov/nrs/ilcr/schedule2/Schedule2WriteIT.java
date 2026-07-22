package ca.bc.gov.nrs.ilcr.schedule2;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Acceptance test — Story 3.2. PUT (save) + DELETE /api/v1/schedule2.
 *
 * <p>Security is OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * holds EDIT_SCHEDULE — this isolates the write-gate / create-on-absent / concurrency / validation
 * behaviour from authz (the 403 case lives in {@link Schedule2WriteAuthorizationIT}, security ON).
 *
 * <p>Mutating tests use DEDICATED mills seeded by {@code V6} so they never clobber the read-only
 * Schedule 2 fixtures (514/517) the read *IT classes assert against (the container + data are shared
 * across the whole run): mill 522 / summary 1022 (update + stale + clear-null + delete), mill 523
 * (Submitted — non-Draft write gate), mill 515 (create-on-save, no summary). Cases read the revision
 * at runtime so they stay order-independent.
 */
@DisplayName("PUT/DELETE /api/v1/schedule2 — write path (Story 3.2)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule2WriteIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2";
  private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Integer revisionOf(long summaryId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = ?",
        Integer.class, summaryId);
  }

  private static String body(Integer revisionCount, Integer cost25, Object vol26, Object cost26) {
    return """
        {
          "revisionCount": %s,
          "comments": "PUT by IT",
          "purchasedLogCostCost": %s,
          "lessLogSalesVolume": %s,
          "lessLogSalesCost": %s
        }
        """.formatted(
            revisionCount == null ? "null" : revisionCount,
            cost25 == null ? "null" : cost25,
            vol26 == null ? "null" : vol26,
            cost26 == null ? "null" : cost26);
  }

  // ---- S12 / update: persist + recompute echo + bump revision. -----------------------------------

  @Test
  @DisplayName("update — 522 Draft PUT persists 25/26, bumps revision, echoes recomputed doc + save msg")
  void put_update_persistsRecomputesBumps() throws Exception {
    int before = revisionOf(1022);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(before, 500000, 2000, 100000))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(522)))
        .andExpect(jsonPath("$.revisionCount", is(before + 1)))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(500000)))
        .andExpect(jsonPath("$.lessLogSales.volume", is(2000)))
        .andExpect(jsonPath("$.lessLogSales.cost", is(100000)))
        // lessLogSales.perUnit computed server-side = 100000/2000 = 50.0
        .andExpect(jsonPath("$.lessLogSales.perUnit", is(50.0)));

    assertEquals(before + 1, revisionOf(1022), "REVISION_COUNT must advance by exactly one");
  }

  // ---- create-on-absent: no summary -> insert (rev 0 -> 1), never 404. ---------------------------

  @Test
  @DisplayName("create — 515 Draft, no summary, revisionCount 0 -> new summary created, rev becomes 1")
  void put_createOnAbsent_insertsSummaryRevBecomesOne() throws Exception {
    // Precondition: no category-"2" summary for 515/2021.
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
        "ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '2'"),
        "precondition: 515 has no Schedule 2 summary");

    // An unsaved GET omits revisionCount; the client sends 0 (the freshly-inserted summary's rev).
    // revisionCount is @NotNull, so a literal null is a 400 — the create token is 0, not null.
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "515").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(0, 250000, 1000, 40000))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionCount", is(1)))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(250000)))
        .andExpect(jsonPath("$.lessLogSales.volume", is(1000)))
        .andExpect(jsonPath("$.lessLogSales.cost", is(40000)));

    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
        "ILCR_MILL_ID = 515 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '2'"),
        "a new category-2 summary must be created on save");
  }

  // ---- clear-to-null: item 25 cost null persists as null. ----------------------------------------

  @Test
  @DisplayName("clear — 522 PUT with item 25 cost null clears the stored value to null")
  void put_clearItem25_persistsNull() throws Exception {
    int before = revisionOf(1022);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(before, null, 2000, 100000))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchasedLogCost.cost").doesNotExist()); // null omitted

    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1022 AND ILCR_REPORT_COST_ITEM_ID = 25 AND COST IS NULL"),
        "item 25 cost must be cleared to null");
  }

  // ---- validation ranges -> 400 verbatim, nothing persisted. -------------------------------------

  @Test
  @DisplayName("item 25 cost > 99,999,999 -> 400 costValidatorErrorMsg, nothing persisted")
  void put_item25Over_returns400() throws Exception {
    expect400NothingPersisted(body(revisionOf(1022), 100000000, 1000, 5000),
        "Entered cost must be between -99,999,999 and 99,999,999.");
  }

  @Test
  @DisplayName("item 26 volume > 9,999,999 -> 400 volumeValidatorErrorMsg, nothing persisted")
  void put_item26VolumeOver_returns400() throws Exception {
    expect400NothingPersisted(body(revisionOf(1022), 5000, 10000000, 5000),
        "Entered volume must be between 0 and 9,999,999.");
  }

  @Test
  @DisplayName("item 26 volume < 0 -> 400 volumeValidatorErrorMsg (min is 0), nothing persisted")
  void put_item26VolumeNegative_returns400() throws Exception {
    expect400NothingPersisted(body(revisionOf(1022), 5000, -1, 5000),
        "Entered volume must be between 0 and 9,999,999.");
  }

  @Test
  @DisplayName("item 26 cost > 999,999,999 -> 400 costSize9ValidatorErrorMsg, nothing persisted")
  void put_item26CostOver_returns400() throws Exception {
    expect400NothingPersisted(body(revisionOf(1022), 5000, 1000, 1000000000),
        "Entered cost must be between -999,999,999 and 999,999,999.");
  }

  @Test
  @DisplayName("item 26 cost = 999,999,999 (widened costSize 9) PASSES; item 25 = 99,999,999 also OK")
  void put_widenedBoundaries_pass() throws Exception {
    int before = revisionOf(1022);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(before, 99999999, 1000, 999999999))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchasedLogCost.cost", is(99999999)))
        .andExpect(jsonPath("$.lessLogSales.cost", is(999999999)));
  }

  @Test
  @DisplayName("item 25 cost = 99,999,999 + 1 -> 400 (item 25 keeps the narrow ±99,999,999 range)")
  void put_item25JustOverNarrowRange_returns400() throws Exception {
    expect400NothingPersisted(body(revisionOf(1022), 100000000, 1000, 5000),
        "Entered cost must be between -99,999,999 and 99,999,999.");
  }

  @Test
  @DisplayName("missing revisionCount -> 400, nothing persisted")
  void put_missingRevisionCount_returns400() throws Exception {
    expect400NothingPersisted("""
        { "comments": "x", "purchasedLogCostCost": 5000, "lessLogSalesVolume": 1000, "lessLogSalesCost": 5000 }
        """, "revisionCount is required");
  }

  private void expect400NothingPersisted(String requestBody, String verbatimDetail) throws Exception {
    int before = revisionOf(1022);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is(verbatimDetail)));
    assertEquals(before, revisionOf(1022), "a rejected PUT must not persist anything");
  }

  // ---- non-Draft write gate -> 409 verbatim, no change. ------------------------------------------

  @Test
  @DisplayName("PUT against non-Draft (mill 523, track S) -> 409, no change")
  void put_nonDraft_returns409() throws Exception {
    int before = revisionOf(1023);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "523").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(before, 1, 1, 1)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(before, revisionOf(1023), "a gated PUT must not change data");
  }

  @Test
  @DisplayName("DELETE against non-Draft (mill 523, track S) -> 409, row survives")
  void delete_nonDraft_returns409() throws Exception {
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "523").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
        "ILCR_REPORT_SUMMARY_ID = 1023"), "gated DELETE must not remove the row");
  }

  // ---- optimistic concurrency. -------------------------------------------------------------------

  @Test
  @DisplayName("stale revisionCount -> 409; reload + retry with N+1 -> 200")
  void put_staleRevision_returns409_thenRetrySucceeds() throws Exception {
    int n = revisionOf(1022);
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(n, 500000, 2000, 100000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionCount", is(n + 1)));
    // Second writer still holds the stale token N -> rejected, no overwrite.
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(n, 500000, 2000, 100000)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail",
            is("This schedule was changed by another user. Please reload and try again.")));
    assertEquals(n + 1, revisionOf(1022), "the stale PUT must not overwrite");
    // Reload the fresh token and retry -> succeeds.
    mockMvc.perform(put(ENDPOINT).with(csrf()).param("millId", "522").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(n + 1, 500000, 2000, 100000)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionCount", is(greaterThan(n + 1))));
  }

  // ---- DELETE: removes summary + 25/26; idempotent on no summary. --------------------------------

  @Test
  @DisplayName("DELETE — 525 Draft removes summary + 25/26; re-GET is the empty editable doc; idempotent")
  void delete_removesWholeSchedule() throws Exception {
    // Precondition: 525 has a summary + details.
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "525").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
        "ILCR_REPORT_SUMMARY_ID = 1025"), "summary row must be gone");
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1025"), "all detail rows must be gone");
    // Re-GET of a Schedule 2 with no summary is the empty editable document, NOT 404.
    mockMvc.perform(get(ENDPOINT).param("millId", "525").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.revisionCount").doesNotExist());
    // Idempotent: a second DELETE on the now-empty schedule still returns 200 (never 404).
    mockMvc.perform(delete(ENDPOINT).with(csrf()).param("millId", "525").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
  }
}
