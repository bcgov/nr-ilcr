package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — the transition is atomic across all fifteen tables (AC 11, {@code
 * epics.md:2058}). A mid-sweep failure is forced by spying the repository, because no fixture can
 * provoke one: every statement is valid SQL against a seeded schema.
 *
 * <p>This proves the property that matters about reproducing legacy's full audit sweep. The write
 * order is status &rarr; audit stamps &rarr; category advance (legacy's, and load-bearing for the
 * delivery audit triggers), so forcing the failure at the category advance means the status write
 * AND all twenty audit statements have already run inside the transaction: the arm now proves both
 * roll back, not just the status write. If the boundary were wrong the report would be left
 * Verified, or stamped by a verify that never happened — a half-transitioned report no screen could
 * explain.
 *
 * <p>Its own mill (763) and its own class: a spy that throws is context-wide, and a half-applied
 * transition would poison any fixture shared with another arm.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("POST /api/v1/check-status/verify — rollback on a mid-sweep failure (Story 17.1)")
class VerifyReportRollbackIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/verify";
  private static final String MILL = "763";
  private static final String YEAR = "2021";
  private static final String ACTING_USER = "verifyadmin";
  private static final String ADMIN_GUID = "VERIFYADMIN0000111122223333AAAA1";
  private static final String SUBMISSION_ERROR_MSG =
      "An error has been found submitting schedules. The error details have been logged."
          + " Please contact ILCR application support.";

  /** AC12's text — a recorded AD-8 deviation, since legacy had no message for this condition. */
  private static final String REVISION_CONFLICT_MSG =
      "This schedule was changed by another user. Please reload and try again.";

  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoSpyBean private ReportTransitionRepository repository;

  private RequestPostProcessor admin() {
    return jwt()
        .jwt(
            j ->
                j.subject(ACTING_USER)
                    .claim("custom:idp_user_id", ADMIN_GUID)
                    .claim("custom:idp_username", ACTING_USER)
                    .claim("cognito:groups", List.of("ILCR_ADMIN")))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  private String trackStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = 763 AND REPORT_YEAR = 2021",
        String.class);
  }

  private List<String> categoryStates() {
    return jdbcTemplate.queryForList(
        "SELECT CATEGORY_STATE_CODE FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = 763 AND REPORT_YEAR = 2021"
            + " AND ILCR_CATEGORY_ID IN ('1','2','3','4','5','6','7','8','9','10')",
        String.class);
  }

  private int revision() {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = 763 AND REPORT_YEAR = 2021",
        Integer.class);
  }

  /**
   * Rows this mill's transition would have stamped. Zero before and after a rolled-back verify: the
   * sweeps run BEFORE the category advance that fails, so a leaked stamp is exactly what a broken
   * transaction boundary would leave behind.
   */
  private int stampedRows() {
    Integer summaries =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_REPORT_SUMMARY"
                + " WHERE ILCR_MILL_ID = 763 AND REPORT_YEAR = 2021 AND UPDATE_USERID = ?",
            Integer.class,
            ACTING_USER);
    Integer costDetails =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL d"
                + " WHERE d.ILCR_REPORT_SUMMARY_ID IN ("
                + "   SELECT s.ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_REPORT_SUMMARY s"
                + "    WHERE s.ILCR_MILL_ID = 763 AND s.REPORT_YEAR = 2021)"
                + " AND d.UPDATE_USERID = ?",
            Integer.class,
            ACTING_USER);
    Integer statusRow =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_MILL_REPORT_STATUS"
                + " WHERE ILCR_MILL_ID = 763 AND REPORT_YEAR = 2021 AND UPDATE_USERID = ?",
            Integer.class,
            ACTING_USER);
    return summaries + costDetails + statusRow;
  }

  @Test
  @DisplayName("AC11: a failure after the status write rolls the whole transition back")
  void rollsBackTheWholeTransition() throws Exception {
    assertThat(trackStatus()).isEqualTo("S");
    assertThat(categoryStates()).containsOnly("A");
    int revisionBefore = revision();

    doThrow(new DataIntegrityViolationException("forced mid-sweep failure"))
        .when(repository)
        .advanceCategoryStates(anyLong(), anyInt(), anyString(), anyString());

    mockMvc
        .perform(post(ENDPOINT).param("millId", MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR_MSG)));

    // The status write and all twenty audit statements had already succeeded inside the
    // transaction; the rollback undoes every one of them.
    assertThat(trackStatus()).isEqualTo("S");
    assertThat(categoryStates()).containsOnly("A");
    assertThat(revision()).isEqualTo(revisionBefore);
    assertThat(stampedRows()).isZero();
  }

  @Test
  @DisplayName(
      "AC12: a stale status row -> 409 scheduleRevisionConflictErrorMsg, nothing persisted")
  void staleRevisionAnswers409ThroughTheEndpoint() throws Exception {
    assertThat(trackStatus()).isEqualTo("S");
    int revisionBefore = revision();

    // The optimistic guard matching zero rows is what a concurrent change looks like from inside
    // the transaction. Stubbed rather than raced because the FOR UPDATE lock makes the branch
    // unreachable single-threaded — but the HTTP outcome it maps to is the AC, and asserting it at
    // the SQL row-count level (as the sibling IT does) never exercised the endpoint's mapping. The
    // key is a recorded AD-8 deviation, so a wrong one would otherwise go unnoticed.
    doReturn(0)
        .when(repository)
        .updateTrackStatusWithAuditor(
            anyLong(), anyInt(), anyString(), any(), any(), anyInt(), anyString());

    mockMvc
        .perform(post(ENDPOINT).param("millId", MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(REVISION_CONFLICT_MSG)));

    assertThat(trackStatus()).isEqualTo("S");
    assertThat(categoryStates()).containsOnly("A");
    assertThat(revision()).isEqualTo(revisionBefore);
    assertThat(stampedRows()).isZero();
  }
}
