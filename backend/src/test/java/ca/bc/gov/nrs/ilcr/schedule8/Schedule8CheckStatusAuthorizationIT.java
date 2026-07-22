package ca.bc.gov.nrs.ilcr.schedule8;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
 * Acceptance test — authorization on VIEW_SCHEDULE (AD-7) for the Schedule 8 Check Status endpoints.
 * Security ON. Empty group → 403; {@code ILCR_SUBMITTER} passes (read-only, so VIEW is sufficient).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("POST /api/v1/schedule8/check-status — authorization on VIEW_SCHEDULE (AD-7)")
class Schedule8CheckStatusAuthorizationIT extends AbstractOracleIT {

  private static final String ALL = "/api/v1/schedule8/check-status";
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
  @DisplayName("no VIEW_SCHEDULE -> POST check-status 403")
  void noPermission_returns403() throws Exception {
    mockMvc.perform(post(ALL).param("millId", "600").param("year", "2021")
            .with(csrf()).with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER -> POST check-status passes authz (not 403)")
  void submitter_passesAuthorization() throws Exception {
    mockMvc.perform(post(ALL).param("millId", "600").param("year", "2021")
            .with(csrf()).with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }
}
