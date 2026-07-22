package ca.bc.gov.nrs.ilcr.schedule4;

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
 * Authorization on EDIT_SCHEDULE (AD-7) for the Schedule 4 location write verbs. Security ON; drives
 * the real {@code oauth2ResourceServer} chain + {@code @PreAuthorize}. A principal without
 * EDIT_SCHEDULE must get 403 {@code problem+json} on both PUT and DELETE; a submitter passes authz.
 * Mirrors {@link ca.bc.gov.nrs.ilcr.schedule2.Schedule2WriteAuthorizationIT}. Uses the dedicated V8
 * mill 543 ("Authz Dump", primary id 8020, revision 0) so the hard-coded token can never be made
 * stale by another class sharing the container.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("PUT/DELETE /api/v1/schedule4/locations — authorization on EDIT_SCHEDULE (AD-7)")
class Schedule4WriteAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule4/locations";
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
      { "id": 8020, "revisionCount": 0, "name": "Authz Dump",
        "categories": [ { "code": 40, "volume": 100, "cost": 5000, "distance": null } ] }
      """;

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> PUT 403 problem+json")
  void put_noPermission_returns403() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "543").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(BODY)
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> DELETE 403 problem+json")
  void delete_noPermission_returns403() throws Exception {
    mockMvc.perform(delete(ENDPOINT).param("millId", "543").param("year", "2021")
            .param("id", "8020")
            .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("ILCR_SUBMITTER holds EDIT_SCHEDULE -> PUT passes authz (not 403)")
  void put_submitter_passesAuthorization() throws Exception {
    mockMvc.perform(put(ENDPOINT).param("millId", "543").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(BODY)
            .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }
}
