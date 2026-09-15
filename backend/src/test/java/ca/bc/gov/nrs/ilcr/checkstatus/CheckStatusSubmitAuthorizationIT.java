package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
 * regardless of UI state"). Security ON. An {@code ILCR_ADMIN} is 403'd by the action check before
 * any read; a submitter with no active assignment to the mill is 403'd by the shared guard's
 * mill-scope check; the canonical associated submitter REACHES the transition (proven by the
 * transition's own 409 on an already-Submitted anchor, so nothing is mutated here — mill 760
 * belongs to {@code CheckStatusSubmitIT}).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName(
    "POST /api/v1/check-status/submit — authorization on SUBMIT_REPORT + mill scope (15.3)")
class CheckStatusSubmitAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/submit";
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
  @DisplayName("ILCR_ADMIN on a Draft + all-MET mill -> 403 problem+json, nothing written")
  void admin_returns403_writesNothing() throws Exception {
    String before = statusOf(760);

    mockMvc
        .perform(submit("760").with(admin()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(statusOf(760)).isEqualTo(before).startsWith("D/0/SEED");
  }

  @Test
  @DisplayName("no group at all -> 403")
  void noPermission_returns403() throws Exception {
    mockMvc
        .perform(submit("760").with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER not associated to the mill -> 403 by mill scope, nothing written")
  void unassociatedSubmitter_returns403_writesNothing() throws Exception {
    String before = statusOf(760);

    mockMvc
        .perform(submit("760").with(unassociatedSubmitter()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

    assertThat(statusOf(760)).isEqualTo(before);
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
}
