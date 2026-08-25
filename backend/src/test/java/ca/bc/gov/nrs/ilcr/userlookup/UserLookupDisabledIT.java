package ca.bc.gov.nrs.ilcr.userlookup;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Acceptance test for the state the slice actually ships in: {@code ilcr.user-lookup.enabled} off,
 * which is the default in every environment until the DL-27 service account exists.
 *
 * <p>This is the flag's whole safety story, and without a test nothing observes it — dropping the
 * {@code @ConditionalOnProperty} from the controller would put the endpoint live everywhere with
 * blank credentials, and every other test in this package would stay green because they all force
 * the flag on.
 *
 * <p>Sibling of {@link UserLookupIT}, which covers the enabled path.
 */
@TestPropertySource(properties = {"ilcr.security.enabled=true", "ilcr.user-lookup.enabled=false"})
@DisplayName("GET /api/v1/users/lookup — absent while the feature flag is off (Story 2.3)")
class UserLookupDisabledIT extends AbstractOracleIT {

  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  @DisplayName("the endpoint is not routed at all, even for an admin")
  void endpointIsAbsentForAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/users/lookup")
                .param("firstName", "jane")
                .with(
                    jwt()
                        .jwt(j -> j.claim("cognito:groups", List.of("ILCR_ADMIN")))
                        .authorities(j -> CONVERTER.convert(j).getAuthorities())))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("the assignments view is unaffected by the directory being switched off")
  void assignmentsStillAnswer() throws Exception {
    // The local xref never consults the directory, so turning the lookup off must not narrow the
    // admin screen that the picker will eventually sit on.
    mockMvc
        .perform(
            get("/api/v1/mills/{millId}/submitters", 514L)
                .with(
                    jwt()
                        .jwt(j -> j.claim("cognito:groups", List.of("ILCR_ADMIN")))
                        .authorities(j -> CONVERTER.convert(j).getAuthorities())))
        .andExpect(status().isOk());
  }
}
