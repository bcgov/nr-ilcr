package ca.bc.gov.nrs.ilcr.schedule2;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — POST /api/v1/schedule2/check-status (read-only BR-07 evaluation, slices
 * S07/S08).
 *
 * <p>Security is OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * holds VIEW_SCHEDULE — this isolates the evaluation + no-mutation guarantee from authz (the 403
 * case lives in {@link Schedule2CheckStatusAuthorizationIT}, security ON). Reuses the read
 * fixtures: mill 621/2021 (item-25 cost 500000 stored) and mill 515/2021 (no summary, unsaved).
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries the on-screen item-25 cost, and
 * the stored value is never consulted. The cases below that post a body mirroring the stored value
 * pin today's verdicts; the "unsaved" cases post a body that DISAGREES with Oracle and prove the
 * body wins.
 *
 * <p>The CRITICAL acceptance assertion (AD-5): a check-status call changes NO rows — the {@code
 * ILCR_REPORT_SUMMARY}/{@code ILCR_COST_REPORT_DETAIL} row counts and the pinned summary's {@code
 * REVISION_COUNT} are captured before and after and asserted unchanged.
 */
@DisplayName("POST /api/v1/schedule2/check-status — read-only status evaluation (S07/S08)")
class Schedule2CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2/check-status";
  private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  /** The 621/2021 screen as served: item-25 cost 500000 (V10 detail 6030). */
  private static final String STORED_621 = "{\"purchasedLogCostCost\":500000}";

  /** A blank item-25 cost on screen — sent as null, never coerced to 0. */
  private static final String BLANK = "{\"purchasedLogCostCost\":null}";

  @Autowired private JdbcTemplate jdbcTemplate;

  private Integer revisionOf(long summaryId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = ?",
        Integer.class,
        summaryId);
  }

  private static MockHttpServletRequestBuilder check(String millId, String body) {
    return post(ENDPOINT)
        .param("millId", millId)
        .param("year", "2021")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  @DisplayName("621/2021 item-25 cost present -> 200 MET with scheduleRequirementsMetMsg")
  void item25Present_returnsMet() throws Exception {
    mockMvc
        .perform(check("621", STORED_621))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")));
  }

  @Test
  @DisplayName(
      "515/2021 unsaved schedule (no summary) -> 200 ISSUES missingRequiredFieldMsg, NOT 404")
  void unsavedSchedule_returnsIssues_notFoundSuppressed() throws Exception {
    mockMvc
        .perform(check("515", BLANK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.messages[0].key", is("missingRequiredFieldMsg")))
        // ISSUES carries the human-readable label prefix (mirrors Schedule 1 / review HP#5); the
        // MET
        // case above is unlabeled. Keep in sync with Schedule2Service#evaluate labelPrefix.
        .andExpect(
            jsonPath(
                "$.messages[0].text", is("Purchased/Private Log Costs - Cost: Value Required")));
  }

  @Test
  @DisplayName("#359 unsaved violation: 621 stores 500000 but the screen is blank -> ISSUES")
  void unsavedViolation_bodyWinsOverStoredValue() throws Exception {
    mockMvc
        .perform(check("621", BLANK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.messages", hasSize(1)))
        .andExpect(
            jsonPath(
                "$.messages[0].text", is("Purchased/Private Log Costs - Cost: Value Required")));
  }

  @Test
  @DisplayName("#359 unsaved fix: 515 has no summary but 40000 is on screen -> MET")
  void unsavedFix_bodyWinsOverMissingRecord() throws Exception {
    mockMvc
        .perform(check("515", "{\"purchasedLogCostCost\":40000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages", hasSize(1)))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")));
  }

  @Test
  @DisplayName("#359 a typed 0 is PRESENT -> MET (null test, not truthiness)")
  void typedZero_isPresent() throws Exception {
    mockMvc
        .perform(check("515", "{\"purchasedLogCostCost\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "621").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("read-only: check-status changes NO rows (counts + REVISION_COUNT unchanged) — AD-5")
  void checkStatus_mutatesNothing() throws Exception {
    long summaryBefore = JdbcTestUtils.countRowsInTable(jdbcTemplate, SUMMARY);
    long detailBefore = JdbcTestUtils.countRowsInTable(jdbcTemplate, DETAIL);
    int revisionBefore = revisionOf(1202); // pinned 621/2021 Schedule 2 summary
    List<Map<String, Object>> rowsBefore =
        jdbcTemplate.queryForList(
            "SELECT * FROM " + DETAIL + " WHERE ILCR_REPORT_SUMMARY_ID = 1202 ORDER BY 1");

    mockMvc
        .perform(check("621", STORED_621))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
    // A body that disagrees with Oracle must not be written through either (#359).
    mockMvc
        .perform(check("621", BLANK))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")));
    // An unsaved-schedule check must not create anything, even with a value on screen.
    mockMvc
        .perform(check("515", "{\"purchasedLogCostCost\":40000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));

    assertEquals(
        summaryBefore,
        JdbcTestUtils.countRowsInTable(jdbcTemplate, SUMMARY),
        "check-status must not add/remove ILCR_REPORT_SUMMARY rows");
    assertEquals(
        detailBefore,
        JdbcTestUtils.countRowsInTable(jdbcTemplate, DETAIL),
        "check-status must not add/remove ILCR_COST_REPORT_DETAIL rows");
    assertEquals(revisionBefore, revisionOf(1202), "check-status must not bump REVISION_COUNT");
    assertEquals(
        rowsBefore,
        jdbcTemplate.queryForList(
            "SELECT * FROM " + DETAIL + " WHERE ILCR_REPORT_SUMMARY_ID = 1202 ORDER BY 1"),
        "check-status must not rewrite the 621/2021 detail rows");
  }
}
