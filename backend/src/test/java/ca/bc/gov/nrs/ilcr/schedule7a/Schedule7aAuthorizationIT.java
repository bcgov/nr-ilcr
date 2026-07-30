package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Story 12.1 acceptance — authorization on {@code VIEW_SCHEDULE} for {@code GET /api/v1/schedule7a}
 * (AC6; AD-7). Security ON: drives the real {@code oauth2ResourceServer} chain + {@code @PreAuthorize},
 * with authorities derived through the production {@link CognitoGroupsJwtAuthenticationConverter}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("GET /api/v1/schedule7a — authorization on VIEW_SCHEDULE (AC6)")
class Schedule7aAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7a";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("no group -> 403 ProblemDetail")
  void noPermission_returns403() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("foreign group -> 403 ProblemDetail")
  void foreignGroup_returns403() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ILCR_SUBMITTER -> passes authz (200)")
  void submitter_passesAuthorization() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "514").param("year", "2021")
            .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }
}
