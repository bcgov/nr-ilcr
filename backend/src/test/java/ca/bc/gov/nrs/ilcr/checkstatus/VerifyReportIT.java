package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — {@code POST /api/v1/check-status/verify}, the Submitted&rarr;Verified
 * transition (UC-CHK-007/012, FR5). Security ON, which is required rather than incidental: with
 * security off there is no token, so no directory GUID, so the auditor columns would always be NULL
 * and the auditor-recording arm could not be proved at all.
 *
 * <p>Every refusal asserts the {@code (status, detail)} pair. Two of the three failure texts are
 * the same string — legacy reused one message for a rejected transition and for a persistence
 * failure — so a body assertion alone cannot tell a 409 from a 500.
 *
 * <p>Mills are this class's own ({@code R__55}). Verifying mutates the status row, ten category
 * rows and thirteen tables' audit columns, so no mill here may be shared with another suite, and
 * none of the mills {@code CheckStatusSweepIT} fingerprints is touched. Only 757 and 762 are
 * mutated, by one test each.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("POST /api/v1/check-status/verify — the Submitted->Verified transition (Story 17.1)")
class VerifyReportIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/verify";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String YEAR = "2021";

  private static final String HAPPY_MILL = "757";
  private static final String GATE_FAILS_MILL = "758";
  private static final String DRAFT_MILL = "759";
  private static final String VERIFIED_MILL = "760";
  private static final String CLOSED_MILL = "761";
  private static final String NO_XREF_MILL = "762";

  /** The admin seeded with an {@code ILCR_MILL_USER_XREF} row for mill 757, and only 757. */
  private static final String ADMIN_GUID = "VERIFYADMIN0000111122223333AAAA1";

  /** The principal name, which is what lands in the audit columns (30-char limit). */
  private static final String ACTING_USER = "verifyadmin";

  /**
   * The token's {@code sub} — deliberately different from {@link #ACTING_USER}, and deliberately
   * the 36-char Cognito UUID shape that would overflow {@code UPDATE_USERID VARCHAR2(30)}. If the
   * app ever stamps the subject instead of {@code custom:idp_username}, the audit assertions fail
   * rather than silently agreeing.
   */
  private static final String COGNITO_SUB = "11111111-2222-3333-4444-555555555555";

  /** What the fixture seeds into the audit columns it populates at all. */
  private static final String SEED_USER = "SEED";

  private static final String VERIFIED_MSG = "Schedules 1-10 status has been updated to verified.";
  private static final String NOT_SUBMITTED_MSG =
      "The report cannot be submitted. One or more of the Schedules have not passed validation."
          + " Please review and correct any errors.";
  private static final String SUBMISSION_ERROR_MSG =
      "An error has been found submitting schedules. The error details have been logged."
          + " Please contact ILCR application support.";

  /** ERR-001, byte-for-byte from legacy — the trailing space is real. */
  private static final String ERR_001 = "Please Select Mill and Reporting Year in the Home Page. ";

  private static final String ERR_002 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";

  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ReportTransitionRepository repository;

  /**
   * An admin whose token carries a directory GUID. The group-only helper the sibling authorization
   * IT uses would leave {@code custom:idp_user_id} absent, which resolves to no cross-reference and
   * writes NULL — so an auditor arm built on it would silently test the wrong branch.
   *
   * <p>Built through the PRODUCTION converter, and {@code sub} is deliberately NOT the acting user.
   * {@code spring-security-test}'s {@code jwt()} constructs the token itself and never calls {@link
   * CognitoGroupsJwtAuthenticationConverter}, so the principal name falls back to {@code sub}:
   * seeding {@code sub} with the acting user (as this class first did) made the stamped-actor
   * assertion pass whichever claim the app read, harness or app alike. Converting the Jwt here
   * means {@code getName()} comes from {@code auditUsername}'s {@code custom:idp_username}, so the
   * assertion now fails if that resolution regresses — and {@code sub} is a 36-char UUID shape
   * precisely because falling back to it would overflow the {@code VARCHAR2(30)} audit column
   * (ORA-12899) in production.
   */
  private RequestPostProcessor adminWithGuid(String guid) {
    Jwt token =
        Jwt.withTokenValue("verify-it-token")
            .header("alg", "none")
            .subject(COGNITO_SUB)
            .claim("custom:idp_user_id", guid)
            .claim("custom:idp_username", ACTING_USER)
            .claim("cognito:groups", List.of("ILCR_ADMIN"))
            .build();
    return authentication(CONVERTER.convert(token));
  }

  private RequestPostProcessor admin() {
    return adminWithGuid(ADMIN_GUID);
  }

  private String trackStatus(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        String.class,
        Integer.valueOf(mill));
  }

  private String silvicultureStatus(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT MILL_SILVICULTUR_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        String.class,
        Integer.valueOf(mill));
  }

  private List<String> categoryStates(String mill) {
    return jdbcTemplate.queryForList(
        "SELECT CATEGORY_STATE_CODE FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
            + " AND ILCR_CATEGORY_ID IN ('1','2','3','4','5','6','7','8','9','10')"
            + " ORDER BY TO_NUMBER(ILCR_CATEGORY_ID)",
        String.class,
        Integer.valueOf(mill));
  }

  private String categoryState(String mill, String categoryId) {
    return jdbcTemplate.queryForObject(
        "SELECT CATEGORY_STATE_CODE FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = ?",
        String.class,
        Integer.valueOf(mill),
        categoryId);
  }

  private String auditorGuid(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT AUDITOR_USER_GUID FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        String.class,
        Integer.valueOf(mill));
  }

  private Long auditorMillId(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT AUDITOR_MILL_ID FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        Long.class,
        Integer.valueOf(mill));
  }

  /**
   * Rows of {@code table} for this mill/year that the acting user did NOT stamp.
   *
   * <p>A COUNT of the unstamped, not {@code MAX(UPDATE_USERID)}, which this class used first:
   * {@code 'verifyadmin'} sorts above the fixture's {@code 'SEED'}, so MAX returned the actor as
   * soon as ONE row was stamped and a sweep narrowed to a single category would still have passed.
   * Paired with {@link #rowCount} at every call site so a table that is simply empty cannot satisfy
   * the assertion vacuously.
   */
  private int unstampedRows(String table, String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE."
            + table
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
            + " AND (UPDATE_USERID IS NULL OR UPDATE_USERID <> ?)",
        Integer.class,
        Integer.valueOf(mill),
        ACTING_USER);
  }

  private int rowCount(String table, String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE." + table + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        Integer.class,
        Integer.valueOf(mill));
  }

  /**
   * Unstamped category rows among ids {@code '1'}-{@code '10'} only — {@code '11'} must NOT move.
   */
  private int unstampedCategoryRows(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
            + " AND ILCR_CATEGORY_ID IN ('1','2','3','4','5','6','7','8','9','10')"
            + " AND (UPDATE_USERID IS NULL OR UPDATE_USERID <> ?)",
        Integer.class,
        Integer.valueOf(mill),
        ACTING_USER);
  }

  private String categoryUpdateUser(String mill, String categoryId) {
    return jdbcTemplate.queryForObject(
        "SELECT UPDATE_USERID FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = ?",
        String.class,
        Integer.valueOf(mill),
        categoryId);
  }

  /**
   * The cost-detail rows hanging off this mill's Schedule 1/2/3 summaries that the actor did NOT
   * stamp. {@code ILCR_COST_REPORT_DETAIL} carries no mill/year of its own, which is why it needed
   * its own helper — and why AC3's fourth table had no assertion at all before this.
   */
  private int unstampedSummaryCostDetails(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL d"
            + " WHERE d.ILCR_REPORT_SUMMARY_ID IN ("
            + "   SELECT s.ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_REPORT_SUMMARY s"
            + "    WHERE s.ILCR_MILL_ID = ? AND s.REPORT_YEAR = 2021)"
            + " AND (d.UPDATE_USERID IS NULL OR d.UPDATE_USERID <> ?)",
        Integer.class,
        Integer.valueOf(mill),
        ACTING_USER);
  }

  private int summaryCostDetailCount(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL d"
            + " WHERE d.ILCR_REPORT_SUMMARY_ID IN ("
            + "   SELECT s.ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_REPORT_SUMMARY s"
            + "    WHERE s.ILCR_MILL_ID = ? AND s.REPORT_YEAR = 2021)",
        Integer.class,
        Integer.valueOf(mill));
  }

  private int auditRowCount(String auditTable) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM THE." + auditTable, Integer.class);
  }

  /**
   * Everything a refused verify must leave byte-identical: the status row (code, revision, both
   * auditor columns, actor), every one of the eleven category rows with its actor, and the audit
   * columns of the summaries and their cost details.
   *
   * <p>Task 9 pinned a before/after fingerprint on every "nothing persisted" arm and the arms
   * asserted two or three columns instead, so a stray audit stamp inside a refused request passed
   * all of them. Row counts alone cannot catch that — a stamp changes no count — so this reads the
   * audit columns themselves.
   */
  private String fingerprint(String mill) {
    StringBuilder print = new StringBuilder();
    print
        .append("STATUS=")
        .append(
            jdbcTemplate.queryForObject(
                "SELECT NVL(ILCR_MILL_REPORT_STATUS_CODE,'-')"
                    + " || '/' || NVL(MILL_SILVICULTUR_STATUS_CODE,'-')"
                    + " || '/r' || NVL(TO_CHAR(REVISION_COUNT),'-')"
                    + " || '/' || NVL(TO_CHAR(AUDITOR_MILL_ID),'-')"
                    + " || '/' || NVL(AUDITOR_USER_GUID,'-')"
                    + " || '/' || NVL(UPDATE_USERID,'-')"
                    + " FROM THE.ILCR_MILL_REPORT_STATUS"
                    + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
                String.class,
                Integer.valueOf(mill)))
        .append(';');
    print
        .append("CATEGORIES=")
        .append(
            jdbcTemplate.queryForList(
                "SELECT ILCR_CATEGORY_ID || '=' || NVL(CATEGORY_STATE_CODE,'-')"
                    + " || '/' || NVL(UPDATE_USERID,'-')"
                    + " FROM THE.ILCR_REPORT_CATEGORY"
                    + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
                    + " ORDER BY TO_NUMBER(ILCR_CATEGORY_ID)",
                String.class,
                Integer.valueOf(mill)))
        .append(';');
    print
        .append("SUMMARIES=")
        .append(
            jdbcTemplate.queryForList(
                "SELECT s.ILCR_CATEGORY_ID || '/' || NVL(s.UPDATE_USERID,'-')"
                    + " FROM THE.ILCR_REPORT_SUMMARY s"
                    + " WHERE s.ILCR_MILL_ID = ? AND s.REPORT_YEAR = 2021"
                    + " ORDER BY s.ILCR_REPORT_SUMMARY_ID",
                String.class,
                Integer.valueOf(mill)))
        .append(';');
    print
        .append("COST_DETAIL_STAMPS=")
        .append(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL d"
                    + " WHERE d.ILCR_REPORT_SUMMARY_ID IN ("
                    + "   SELECT s.ILCR_REPORT_SUMMARY_ID FROM THE.ILCR_REPORT_SUMMARY s"
                    + "    WHERE s.ILCR_MILL_ID = ? AND s.REPORT_YEAR = 2021)"
                    + " AND d.UPDATE_USERID IS NOT NULL",
                Integer.class,
                Integer.valueOf(mill)));
    return print.toString();
  }

  private int revision(String mill) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM THE.ILCR_MILL_REPORT_STATUS"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        Integer.class,
        Integer.valueOf(mill));
  }

  @Test
  @DisplayName(
      "AC1/AC2/AC3: all-met Submitted track -> 200, S->V, ten categories -> V, auditor recorded")
  void verifiesSubmittedTrack() throws Exception {
    assertThat(trackStatus(HAPPY_MILL)).isEqualTo("S");
    int revisionBefore = revision(HAPPY_MILL);
    int summaryAuditBefore = auditRowCount("ILCR_REPORT_SUMMARY_AUDIT");
    int costDetailAuditBefore = auditRowCount("ILCR_COST_REPORT_DETAIL_AUD");

    mockMvc
        .perform(post(ENDPOINT).param("millId", HAPPY_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("V")))
        .andExpect(jsonPath("$.message.key", is("sch1-10VerifiedMsg")))
        .andExpect(jsonPath("$.message.text", is(VERIFIED_MSG)));

    assertThat(trackStatus(HAPPY_MILL)).isEqualTo("V");
    assertThat(categoryStates(HAPPY_MILL)).hasSize(10).containsOnly("V");

    // Track independence (CHK-012 S09): Schedule 11's status and its category row are untouched.
    assertThat(silvicultureStatus(HAPPY_MILL)).isEqualTo("D");
    assertThat(categoryState(HAPPY_MILL, "11")).isEqualTo("D");

    // The auditor is a composite key, asserted by value — non-null alone would pass on the wrong
    // row.
    assertThat(auditorGuid(HAPPY_MILL)).isEqualTo(ADMIN_GUID);
    assertThat(auditorMillId(HAPPY_MILL)).isEqualTo(757L);

    // Audit columns: EVERY row stamped, not merely one. Each paired with its row count so an
    // empty table cannot satisfy the assertion vacuously.
    assertThat(rowCount("ILCR_MILL_REPORT_STATUS", HAPPY_MILL)).isOne();
    assertThat(unstampedRows("ILCR_MILL_REPORT_STATUS", HAPPY_MILL)).isZero();

    assertThat(rowCount("ILCR_REPORT_SUMMARY", HAPPY_MILL)).isPositive();
    assertThat(unstampedRows("ILCR_REPORT_SUMMARY", HAPPY_MILL)).isZero();

    // AC3's fourth table, which had no assertion at all: the cost details under those summaries.
    assertThat(summaryCostDetailCount(HAPPY_MILL)).isPositive();
    assertThat(unstampedSummaryCostDetails(HAPPY_MILL)).isZero();

    // Categories '1'-'10' all stamped; '11' byte-identical, asserted on the column rather than
    // through an aggregate that could not distinguish the two.
    assertThat(unstampedCategoryRows(HAPPY_MILL)).isZero();
    assertThat(categoryUpdateUser(HAPPY_MILL, "11")).isEqualTo(SEED_USER);

    assertThat(revision(HAPPY_MILL)).isEqualTo(revisionBefore + 1);

    // AC3: the application inserts no _AUD row — delivery triggers own those, and the test
    // snapshot creates the shadow tables without them (V20260910), so this is directly provable.
    assertThat(auditRowCount("ILCR_REPORT_SUMMARY_AUDIT")).isEqualTo(summaryAuditBefore);
    assertThat(auditRowCount("ILCR_COST_REPORT_DETAIL_AUD")).isEqualTo(costDetailAuditBefore);
  }

  @Test
  @DisplayName("AC2: an admin with no mill association verifies, and both auditor columns are NULL")
  void recordsNoAuditorWithoutAnAssociation() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", NO_XREF_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trackStatus", is("V")));

    assertThat(trackStatus(NO_XREF_MILL)).isEqualTo("V");
    assertThat(auditorGuid(NO_XREF_MILL)).isNull();
    assertThat(auditorMillId(NO_XREF_MILL)).isNull();
  }

  @Test
  @DisplayName(
      "AC4/AC10: a failing validation gate -> 409 reportNotSubmittedErrorMsg, nothing persisted")
  void refusesWhenTheGateFails() throws Exception {
    String before = fingerprint(GATE_FAILS_MILL);

    mockMvc
        .perform(post(ENDPOINT).param("millId", GATE_FAILS_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(NOT_SUBMITTED_MSG)));

    assertThat(trackStatus(GATE_FAILS_MILL)).isEqualTo("S");
    assertThat(categoryStates(GATE_FAILS_MILL)).containsOnly("A");
    assertThat(auditorGuid(GATE_FAILS_MILL)).isNull();
    assertThat(fingerprint(GATE_FAILS_MILL)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC5/AC10: a Draft track -> 409 reportSubmissionErrorMsg (the illegal D->V jump)")
  void refusesADraftTrack() throws Exception {
    String before = fingerprint(DRAFT_MILL);

    mockMvc
        .perform(post(ENDPOINT).param("millId", DRAFT_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR_MSG)));

    assertThat(trackStatus(DRAFT_MILL)).isEqualTo("D");
    assertThat(categoryStates(DRAFT_MILL)).containsOnly("D");
    assertThat(fingerprint(DRAFT_MILL)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC5/AC10: an already-Verified track -> 409 reportSubmissionErrorMsg (the no-op)")
  void refusesANoOp() throws Exception {
    String before = fingerprint(VERIFIED_MILL);

    mockMvc
        .perform(post(ENDPOINT).param("millId", VERIFIED_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR_MSG)));

    assertThat(trackStatus(VERIFIED_MILL)).isEqualTo("V");
    assertThat(fingerprint(VERIFIED_MILL)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC6: an ILCR_SUBMITTER lacks SET_REPORT_STATUS -> 403, nothing persisted")
  void refusesASubmitter() throws Exception {
    String before = fingerprint(GATE_FAILS_MILL);

    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", GATE_FAILS_MILL)
                .param("year", YEAR)
                .with(canonicalSubmitter()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(trackStatus(GATE_FAILS_MILL)).isEqualTo("S");
    assertThat(fingerprint(GATE_FAILS_MILL)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC7: a closed mill -> 409 ERR-002, refused before any schedule is read")
  void refusesAClosedMill() throws Exception {
    String before = fingerprint(CLOSED_MILL);

    mockMvc
        .perform(post(ENDPOINT).param("millId", CLOSED_MILL).param("year", YEAR).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(ERR_002)));

    assertThat(trackStatus(CLOSED_MILL)).isEqualTo("S");
    assertThat(fingerprint(CLOSED_MILL)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC8: missing millId -> 400 ERR-001 verbatim, trailing space included")
  void refusesAMissingMillId() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("year", YEAR).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC8: non-numeric millId -> 400 ERR-001, never Spring's generic 400")
  void refusesANonNumericMillId() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "not-a-mill").param("year", YEAR).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC8: a blank millId -> 400 ERR-001 — blank is not the same path as absent")
  void refusesABlankMillId() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "  ").param("year", YEAR).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC8: missing year -> 400 ERR-001 (the year half was untested)")
  void refusesAMissingYear() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", HAPPY_MILL).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC8: blank year -> 400 ERR-001")
  void refusesABlankYear() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", HAPPY_MILL).param("year", "  ").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC8: non-numeric year -> 400 ERR-001, never Spring's generic 400")
  void refusesANonNumericYear() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", HAPPY_MILL).param("year", "twenty21").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is(ERR_001)));
  }

  @Test
  @DisplayName("AC12: the revision guard matches no rows when the stored revision has moved")
  void revisionGuardRefusesAStaleRevision() {
    String before = trackStatus(GATE_FAILS_MILL);
    int stored = revision(GATE_FAILS_MILL);

    // Exercised against real Oracle rather than a mock: a guard that silently matched every row
    // would still satisfy a mocked-repository test of the same translation.
    int rows =
        repository.updateTrackStatusWithAuditor(
            758L, 2021, "V", null, null, stored + 99, ACTING_USER);

    assertThat(rows).isZero();
    assertThat(trackStatus(GATE_FAILS_MILL)).isEqualTo(before);
    assertThat(revision(GATE_FAILS_MILL)).isEqualTo(stored);
  }

  @Test
  @DisplayName("AC9: an unknown mill -> 404 with the Check Status page's own not-found text")
  void refusesAnUnknownMill() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "999999").param("year", YEAR).with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath(
                "$.detail",
                is("One or more of the schedules for the report have not been found.")));
  }
}
