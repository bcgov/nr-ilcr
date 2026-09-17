package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — Story 15.3 AC 5: authorization on {@code SUBMIT_REPORT} plus mill scope for the
 * submit endpoint (AD-6/AD-7, PRD FR5 "ministry users cannot submit on a Licensee's behalf,
 * regardless of UI state"). Security ON. An ADMIN-only caller is 403'd by the action check before
 * any read; a submitter with no active assignment to the mill is 403'd by the shared guard's
 * mill-scope check; an ADMIN+SUBMITTER retains the action but remains bound to that same SUBMITTER
 * scope. The canonical associated submitter REACHES the transition (proven by the transition's own
 * 409 on an already-Submitted anchor), so nothing is mutated here. Draft offer and denial cases use
 * the read-only 515 anchor; mill 780 belongs exclusively to {@code CheckStatusSubmitIT} and is
 * permanently submitted there.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName(
    "POST /api/v1/check-status/submit — authorization on SUBMIT_REPORT + mill scope (15.3)")
class CheckStatusSubmitAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/submit";
  private static final long DRAFT_ANCHOR = 515L;
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String SUBMISSION_ERROR =
      "An error has been found submitting schedules. The error details have been logged. Please"
          + " contact ILCR application support.";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private JdbcTemplate jdbc;

  private static MockHttpServletRequestBuilder submit(String mill) {
    return post(ENDPOINT).param("millId", mill).param("year", "2021");
  }

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  /** A submitter with NO xref association to any mill (32-char GUID, never seeded). */
  private RequestPostProcessor unassociatedSubmitter() {
    return jwt()
        .jwt(
            j ->
                j.claim("custom:idp_user_id", "UNASSOCIATEDSUBMITTERXXXX0000001")
                    .claim("cognito:groups", List.of("ILCR_SUBMITTER")))
        .authorities(new SimpleGrantedAuthority("SUBMITTER"));
  }

  private RequestPostProcessor dualRole(String userGuid) {
    return jwt()
        .jwt(
            j ->
                j.claim("custom:idp_user_id", userGuid)
                    .claim("cognito:groups", List.of("ILCR_ADMIN", "ILCR_SUBMITTER")))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  private RequestPostProcessor admin() {
    return jwtWithGroups(List.of("ILCR_ADMIN"));
  }

  private String statusOf(long mill) {
    return jdbc.queryForObject(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE || '/' || REVISION_COUNT || '/' || UPDATE_USERID"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        String.class,
        mill);
  }

  @Test
  @DisplayName("ADMIN-only caller on a Draft mill -> 403 problem+json, nothing written")
  void admin_returns403_writesNothing() throws Exception {
    String before = statusOf(DRAFT_ANCHOR);

    mockMvc
        .perform(submit(String.valueOf(DRAFT_ANCHOR)).with(admin()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(statusOf(DRAFT_ANCHOR)).isEqualTo(before).startsWith("D/0/");
  }

  @Test
  @DisplayName("no group at all -> 403")
  void noPermission_returns403() throws Exception {
    mockMvc
        .perform(submit(String.valueOf(DRAFT_ANCHOR)).with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER not associated to the mill -> 403 by mill scope, nothing written")
  void unassociatedSubmitter_returns403_writesNothing() throws Exception {
    String before = statusOf(DRAFT_ANCHOR);

    mockMvc
        .perform(submit(String.valueOf(DRAFT_ANCHOR)).with(unassociatedSubmitter()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(statusOf(DRAFT_ANCHOR)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "canonical associated ILCR_SUBMITTER reaches the transition: an already-Submitted mill answers"
          + " the guard's 409, not a 403")
  void associatedSubmitter_reachesTheTransition() throws Exception {
    mockMvc
        .perform(submit("517").with(canonicalSubmitter()))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));
  }

  @Test
  @DisplayName("dual-role caller without a submitter assignment cannot submit for the mill")
  void unassociatedDualRole_returns403_writesNothing() throws Exception {
    String before = statusOf(DRAFT_ANCHOR);

    mockMvc
        .perform(
            submit(String.valueOf(DRAFT_ANCHOR)).with(dualRole("UNASSOCIATEDSUBMITTERXXXX0000001")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(statusOf(DRAFT_ANCHOR)).isEqualTo(before);
  }

  @Test
  @DisplayName("dual-role caller with a submitter assignment reaches the transition")
  void associatedDualRole_reachesTheTransition() throws Exception {
    mockMvc
        .perform(submit("517").with(dualRole(CANONICAL_SUBMITTER_GUID)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(SUBMISSION_ERROR)));
  }

  @Test
  @DisplayName("dual-role caller without a submitter assignment does not see Submit offered")
  void unassociatedDualRole_canViewButCannotSubmit() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/check-status")
                .param("millId", String.valueOf(DRAFT_ANCHOR))
                .param("year", "2021")
                .with(dualRole("UNASSOCIATEDSUBMITTERXXXX0000001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules1To10.canSubmit", is(false)));
  }

  @Test
  @DisplayName("dual-role caller with a submitter assignment sees Submit offered at Draft")
  void associatedDualRole_canSubmit() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/check-status")
                .param("millId", String.valueOf(DRAFT_ANCHOR))
                .param("year", "2021")
                .with(dualRole(CANONICAL_SUBMITTER_GUID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules1To10.canSubmit", is(true)));
  }
}
