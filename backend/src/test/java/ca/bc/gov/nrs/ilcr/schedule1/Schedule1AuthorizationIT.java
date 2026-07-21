package ca.bc.gov.nrs.ilcr.schedule1;

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
 * Acceptance test for authorization on VIEW_SCHEDULE (AD-7). Runs with security ON and drives the
 * real {@code oauth2ResourceServer} chain + {@code @PreAuthorize}. Authorities are derived through
 * the production {@link CognitoGroupsJwtAuthenticationConverter} (the spring-security-test
 * {@code jwt()} default reads only the {@code scope} claim, so we plug our converter into the
 * {@code .authorities(...)} overload) — so this exercises the real cognito:groups &rarr; role &rarr;
 * action path end to end.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("GET /api/v1/schedule1 — authorization on VIEW_SCHEDULE (AD-7)")
class Schedule1AuthorizationIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule1";
    private static final long SEEDED_MILL = 514L;
    private static final int SEEDED_YEAR = 2021;
    private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
            new CognitoGroupsJwtAuthenticationConverter();

    // With security on, oauth2ResourceServer().jwt() needs a JwtDecoder bean at config time. The
    // jwt() post-processor injects the Jwt directly (no real decoding), so a mock satisfies
    // construction. Real FAM decoder wiring (issuer/jwk) is the deferred auth story.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private RequestPostProcessor jwtWithGroups(List<String> groups) {
        return jwt()
                .jwt(j -> j.claim("cognito:groups", groups))
                .authorities(j -> CONVERTER.convert(j).getAuthorities());
    }

    @Test
    @DisplayName("no VIEW_SCHEDULE (empty cognito:groups) -> 403")
    void noPermission_returns403() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", String.valueOf(SEEDED_MILL))
                        .param("year", String.valueOf(SEEDED_YEAR))
                        .with(jwtWithGroups(List.of())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("foreign group (no ILCR_ suffix) -> 403")
    void foreignGroup_returns403() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", String.valueOf(SEEDED_MILL))
                        .param("year", String.valueOf(SEEDED_YEAR))
                        .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("ILCR_SUBMITTER group -> passes authz (not 403)")
    void submitter_passesAuthorization() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", String.valueOf(SEEDED_MILL))
                        .param("year", String.valueOf(SEEDED_YEAR))
                        .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("ILCR_ADMIN group -> passes authz (not 403)")
    void admin_passesAuthorization() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("millId", String.valueOf(SEEDED_MILL))
                        .param("year", String.valueOf(SEEDED_YEAR))
                        .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
                .andExpect(status().is2xxSuccessful());
    }
}
