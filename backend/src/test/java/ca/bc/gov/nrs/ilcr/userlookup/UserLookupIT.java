package ca.bc.gov.nrs.ilcr.userlookup;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — the directory-search endpoint (UC-USR-001, Story 2.3), security ON, the
 * outbound client mocked (the real API needs the DL-27 service account, which does not exist yet).
 * Proves the ADMIN gate, the request validation, the ProblemDetail degradation, and that a
 * directory outage leaves the assignments view untouched.
 */
@TestPropertySource(properties = {"ilcr.security.enabled=true", "ilcr.user-lookup.enabled=true"})
@DisplayName("GET /api/v1/users/lookup — directory search (admin-gated, Story 2.3)")
class UserLookupIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/users/lookup";
  private static final String GUID = "AAAABBBBCCCCDDDDEEEEFFFF00001111";

  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserLookupClient directory;

  @Test
  @DisplayName("an admin IDIR search returns the picker candidates")
  void adminIdirSearchReturnsCandidates() throws Exception {
    when(directory.searchIdir("jane", null, null))
        .thenReturn(List.of(new DirectoryUser(GUID, "Doe, Jane", "JDOE", "IDIR")));

    mockMvc
        .perform(get(ENDPOINT).param("firstName", "jane").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].userGuid").value(GUID))
        .andExpect(jsonPath("$[0].displayName").value("Doe, Jane"))
        .andExpect(jsonPath("$[0].idpUsername").value("JDOE"))
        .andExpect(jsonPath("$[0].identityProvider").value("IDIR"));
  }

  @Test
  @DisplayName("a BCeID exact lookup by GUID routes to the exact operation")
  void bceidExactLookupRoutes() throws Exception {
    when(directory.findBusinessBceid("userGuid", GUID))
        .thenReturn(List.of(new DirectoryUser(GUID, "Biz, User", "bizuser", "BCEIDBUSINESS")));

    mockMvc
        .perform(get(ENDPOINT).param("idp", "BCEIDBUSINESS").param("userGuid", GUID).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].identityProvider").value("BCEIDBUSINESS"));
  }

  @Test
  @DisplayName("a search with no criteria is rejected — the directory has no list-everyone")
  void blankCriteriaAreRejected() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("At least one search criterion is required."));

    mockMvc
        .perform(get(ENDPOINT).param("idp", "BCEIDBUSINESS").with(admin()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an identity provider the directory does not serve is rejected")
  void unknownIdpIsRejected() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("idp", "GITHUB").param("userId", "x").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("The identity provider must be IDIR or BCEIDBUSINESS."));
  }

  @Test
  @DisplayName("a directory outage degrades to a ProblemDetail while assignments keep working")
  void directoryOutageDegradesGracefully() throws Exception {
    when(directory.searchIdir("jane", null, null)).thenThrow(new DirectoryUnavailableException());

    mockMvc
        .perform(get(ENDPOINT).param("firstName", "jane").with(admin()))
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.detail")
                .value("The user directory is currently unavailable. Please try again later."));

    // The assignments view reads the local xref, never the directory — the outage must not
    // touch it.
    mockMvc
        .perform(get("/api/v1/mills/{millId}/submitters", 514L).with(admin()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("a submitter is denied the directory search")
  void submitterIsDenied() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("firstName", "jane").with(submitter()))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor admin() {
    return groups("ILCR_ADMIN");
  }

  private RequestPostProcessor submitter() {
    return groups("ILCR_SUBMITTER");
  }

  private RequestPostProcessor groups(String... groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", List.of(groups)))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }
}
