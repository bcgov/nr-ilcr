package ca.bc.gov.nrs.ilcr.schedule2;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Acceptance test — POST /api/v1/schedule2/check-status (read-only BR-07 evaluation, slices S07/S08).
 *
 * <p>Security is OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * holds VIEW_SCHEDULE — this isolates the evaluation + no-mutation guarantee from authz (the 403 case
 * lives in {@link Schedule2CheckStatusAuthorizationIT}, security ON). Reuses the read fixtures: mill
 * 514/2021 (item-25 cost present -> MET) and mill 515/2021 (no summary, unsaved -> ISSUES, never 404).
 *
 * <p>The CRITICAL acceptance assertion (AD-5): a check-status call changes NO rows — the
 * {@code ILCR_REPORT_SUMMARY}/{@code ILCR_COST_REPORT_DETAIL} row counts and the pinned summary's
 * {@code REVISION_COUNT} are captured before and after and asserted unchanged.
 */
@DisplayName("POST /api/v1/schedule2/check-status — read-only status evaluation (S07/S08)")
class Schedule2CheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2/check-status";
  private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Integer revisionOf(long summaryId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = ?",
        Integer.class, summaryId);
  }

  @Test
  @DisplayName("514/2021 item-25 cost present -> 200 MET with scheduleRequirementsMetMsg")
  void item25Present_returnsMet() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        .andExpect(jsonPath("$.messages[0].text",
            is("All requirements for this schedule have been met")));
  }

  @Test
  @DisplayName("515/2021 unsaved schedule (no summary) -> 200 ISSUES missingRequiredFieldMsg, NOT 404")
  void unsavedSchedule_returnsIssues_notFoundSuppressed() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "515").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.messages[0].key", is("missingRequiredFieldMsg")))
        .andExpect(jsonPath("$.messages[0].text", is("Value Required")));
  }

  @Test
  @DisplayName("read-only: check-status changes NO rows (counts + REVISION_COUNT unchanged) — AD-5")
  void checkStatus_mutatesNothing() throws Exception {
    long summaryBefore = JdbcTestUtils.countRowsInTable(jdbcTemplate, SUMMARY);
    long detailBefore = JdbcTestUtils.countRowsInTable(jdbcTemplate, DETAIL);
    int revisionBefore = revisionOf(1002); // pinned 514/2021 Schedule 2 summary

    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
    // An unsaved-schedule check must not create anything either.
    mockMvc.perform(post(ENDPOINT).param("millId", "515").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")));

    assertEquals(summaryBefore, JdbcTestUtils.countRowsInTable(jdbcTemplate, SUMMARY),
        "check-status must not add/remove ILCR_REPORT_SUMMARY rows");
    assertEquals(detailBefore, JdbcTestUtils.countRowsInTable(jdbcTemplate, DETAIL),
        "check-status must not add/remove ILCR_COST_REPORT_DETAIL rows");
    assertEquals(revisionBefore, revisionOf(1002),
        "check-status must not bump REVISION_COUNT");
  }
}
