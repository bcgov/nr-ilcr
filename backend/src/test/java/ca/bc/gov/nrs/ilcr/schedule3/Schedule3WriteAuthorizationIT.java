package ca.bc.gov.nrs.ilcr.schedule3;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * Acceptance test for authorization on EDIT_SCHEDULE for the Schedule 3 writes (AD-7). Security ON;
 * a caller with no ILCR group is denied 403 on PUT and DELETE (before any persistence). The write
 * behavior itself is proven security-off by {@link Schedule3WriteIT}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("PUT/DELETE /api/v1/schedule3 — authorization on EDIT_SCHEDULE (AD-7)")
class Schedule3WriteAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3";
  private static final String BODY = """
      { "revisionCount": 0, "overrideHarvestTotalPop": "N", "lineItems": [],
        "popTimberVolume": 5000, "crownTimberVolume": 5000 }
      """;
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
  @DisplayName("PUT without permission → 403")
  void putNoPermission_returns403() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "540").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(BODY)
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("DELETE without permission → 403")
  void deleteNoPermission_returns403() throws Exception {
    mockMvc.perform(delete(ENDPOINT).param("millId", "540").param("year", "2021")
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }
}
