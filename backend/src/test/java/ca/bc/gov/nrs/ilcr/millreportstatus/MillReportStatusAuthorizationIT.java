package ca.bc.gov.nrs.ilcr.millreportstatus;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test for authorization on GENERATE_MILL_REPORTS (AD-7). Security ON; drives the real
 * {@code oauth2ResourceServer} chain + {@code @PreAuthorize}.
 *
 * <p>A 1:1 clone of {@code MillInformationReportAuthorizationIT}'s gate against this story's JSON
 * endpoint. The distinguishing case is the SUBMITTER: they hold VIEW_SCHEDULE and so may print
 * schedules, but the ministry mill reports were never theirs — legacy hid the whole Generate
 * Reports area from them. A submitter reaching this endpoint must be denied, which is what
 * separates this gate from the one on the schedule endpoints.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("GET /api/v1/reports/mill-status — authorization on GENERATE_MILL_REPORTS (AD-7)")
class MillReportStatusAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/mill-status";
  private static final String SEEDED_YEAR = "2021";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("no token (anonymous) -> 401")
  void anonymous_returns401() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", SEEDED_YEAR).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("ILCR_SUBMITTER -> 403; holding VIEW_SCHEDULE is not enough for a ministry report")
  void submitter_returns403() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("year", SEEDED_YEAR)
                .accept(MediaType.APPLICATION_JSON)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("the canonical submitter, associated to every seeded mill, is still 403")
  void associatedSubmitter_returns403() throws Exception {
    // Mill association is irrelevant here: this is not a mill-scoped endpoint, it is an ADMIN-only
    // one. A submitter who could reach every schedule still cannot reach the ministry status table.
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("year", SEEDED_YEAR)
                .accept(MediaType.APPLICATION_JSON)
                .with(canonicalSubmitter()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("no groups at all -> 403")
  void noGroups_returns403() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("year", SEEDED_YEAR)
                .accept(MediaType.APPLICATION_JSON)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ILCR_ADMIN -> 200 and the rows are served")
  void admin_returnsRows() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("year", SEEDED_YEAR)
                .accept(MediaType.APPLICATION_JSON)
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().isOk())
        // An ARRAY, not a row count. The fixture count is MillReportStatusIT's to own; asserting it
        // here would make any new 2021 fixture fail two tests for one reason, with the second
        // failure pointing at the security chain.
        .andExpect(jsonPath("$").isArray());
  }
}
