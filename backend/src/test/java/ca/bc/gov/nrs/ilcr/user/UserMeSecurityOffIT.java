package ca.bc.gov.nrs.ilcr.user;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/v1/me} with security OFF (the default local/test profile). The
 * {@code MockPrincipalFilter} seeds a principal so {@code /me} answers without a Cognito round-trip
 * and existing schedule tests keep working (AD-7). The mock role selector drives the backend
 * principal via {@code X-Mock-Groups}, so a dev can act as an admin locally.
 */
@DisplayName("GET /api/v1/me — security off (mock principal)")
class UserMeSecurityOffIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/me";

  @Test
  @DisplayName("default mock principal -> 200 as ILCR_SUBMITTER")
  void defaultMockPrincipal_returnsSubmitter() throws Exception {
    mockMvc.perform(get(ENDPOINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGuid").value("dev-submitter"))
        .andExpect(jsonPath("$.displayName").value("Local Development User"))
        .andExpect(jsonPath("$.roles", contains("ILCR_SUBMITTER")));
  }

  @Test
  @DisplayName("X-Mock-Groups selects the admin role locally")
  void mockAdminViaHeader_returnsAdmin() throws Exception {
    mockMvc.perform(get(ENDPOINT).header("X-Mock-Groups", "ILCR_ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGuid").value("dev-admin"))
        .andExpect(jsonPath("$.roles", contains("ILCR_ADMIN")));
  }
}
