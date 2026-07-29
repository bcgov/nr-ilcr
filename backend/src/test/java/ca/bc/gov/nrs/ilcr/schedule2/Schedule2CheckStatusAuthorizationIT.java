package ca.bc.gov.nrs.ilcr.schedule2;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Authorization on VIEW_SCHEDULE (AD-7) for POST check-status. Security ON; drives the real
 * {@code oauth2ResourceServer} chain + {@code @PreAuthorize}. A principal without VIEW_SCHEDULE must
 * get 403 {@code problem+json}; a submitter/admin passes authz. Mirrors {@link Schedule2AuthorizationIT}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("POST /api/v1/schedule2/check-status — authorization on VIEW_SCHEDULE (AD-7)")
class Schedule2CheckStatusAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2/check-status";
  private static final long SEEDED_MILL = 514L;
  private static final int SEEDED_YEAR = 2021;
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("no VIEW_SCHEDULE (empty cognito:groups) -> 403 problem+json")
  void noPermission_returns403() throws Exception {
    mockMvc.perform(post(ENDPOINT)
            .param("millId", String.valueOf(SEEDED_MILL))
            .param("year", String.valueOf(SEEDED_YEAR))
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER group -> passes authz (not 403)")
  void submitter_passesAuthorization() throws Exception {
    mockMvc.perform(post(ENDPOINT)
            .param("millId", String.valueOf(SEEDED_MILL))
            .param("year", String.valueOf(SEEDED_YEAR))
            .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }
}
