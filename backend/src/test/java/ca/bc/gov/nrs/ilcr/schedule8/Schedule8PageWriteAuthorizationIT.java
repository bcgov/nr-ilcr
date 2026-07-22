package ca.bc.gov.nrs.ilcr.schedule8;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
 * Acceptance test — authorization on EDIT_SCHEDULE (AD-7) for the Schedule 8 page write path. Security
 * ON; drives the real {@code oauth2ResourceServer} chain + {@code @PreAuthorize}. VIEW-only
 * ({@code ILCR_SUBMITTER} without edit? here SUBMITTER holds EDIT) — an empty group is denied (403);
 * a submitter passes. Mirrors the Schedule 4 write-authz pattern.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("PUT/DELETE /api/v1/schedule8/pages — authorization on EDIT_SCHEDULE (AD-7)")
class Schedule8PageWriteAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule8/pages";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();
  private static final String VALID_BODY = """
      {"license": "LAUTH", "supportCentre": "SC1", "region": "R1", "becZone": "BZ1",
       "tsaNumber": "TSA5"}""";

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty groups) -> PUT 403")
  void put_noPermission_returns403() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).with(jwtWithGroups(List.of()))
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty groups) -> DELETE 403")
  void delete_noPermission_returns403() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/8810").param("millId", "582").param("year", "2021")
            .with(csrf()).with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER -> PUT passes authz (not 403)")
  void put_submitter_passesAuthorization() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).with(jwtWithGroups(List.of("ILCR_SUBMITTER")))
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().is2xxSuccessful());
  }
}
