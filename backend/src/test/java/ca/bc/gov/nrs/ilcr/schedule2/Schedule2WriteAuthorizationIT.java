package ca.bc.gov.nrs.ilcr.schedule2;

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
 * Authorization on EDIT_SCHEDULE (AD-7) for the Schedule 2 write verbs. Security ON; drives the real
 * {@code oauth2ResourceServer} chain + {@code @PreAuthorize}. A principal without EDIT_SCHEDULE must
 * get 403 {@code problem+json} on both PUT and DELETE; a submitter passes authz. Mirrors
 * {@link ca.bc.gov.nrs.ilcr.schedule1.Schedule1WriteAuthorizationIT}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("PUT/DELETE /api/v1/schedule2 — authorization on EDIT_SCHEDULE (AD-7)")
class Schedule2WriteAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule2";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  private static final String BODY = """
      { "revisionCount": 0, "comments": "authz", "purchasedLogCostCost": 5000,
        "lessLogSalesVolume": 1000, "lessLogSalesCost": 5000 }
      """;

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> PUT 403 problem+json")
  void put_noPermission_returns403() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(BODY)
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> DELETE 403 problem+json")
  void delete_noPermission_returns403() throws Exception {
    mockMvc.perform(delete(ENDPOINT).param("millId", "524").param("year", "2021")
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER holds EDIT_SCHEDULE -> PUT passes authz (not 403)")
  void put_submitter_passesAuthorization() throws Exception {
    // Dedicated mill 524 (REVISION_COUNT 0, only this test writes it) so the hard-coded token can
    // never be made stale by another class sharing the container.
    mockMvc.perform(put(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(BODY)
            .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }
}
