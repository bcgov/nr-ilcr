package ca.bc.gov.nrs.ilcr.schedule1;

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
 * RED-PHASE ATDD SCAFFOLD — Story 2.1 (AD-7). Authorization on EDIT_SCHEDULE for the write verbs.
 *
 * <p>Runs with security ON and drives the real {@code oauth2ResourceServer} chain + {@code
 * @PreAuthorize} — authorities are derived through the production {@link
 * CognitoGroupsJwtAuthenticationConverter} (same pattern as {@link Schedule1AuthorizationIT}, which
 * covers VIEW_SCHEDULE on GET). A principal without EDIT_SCHEDULE must get 403 {@code problem+json}
 * on both PUT and DELETE, with no data change.
 *
 * <p>RED before implementation: with no PUT/DELETE handler mapped, security returns 405, not the
 * asserted 403. Class-level {@code @Disabled} is the red-phase gate; dev-story removes it once the
 * write endpoints carry {@code @PreAuthorize("@permissions.hasPermission(authentication,
 * 'EDIT_SCHEDULE')")}.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("PUT/DELETE /api/v1/schedule1 — authorization on EDIT_SCHEDULE (AD-7)")
class Schedule1WriteAuthorizationIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule1";
    private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
            new CognitoGroupsJwtAuthenticationConverter();

    // With security on, oauth2ResourceServer().jwt() needs a JwtDecoder bean at config time; the
    // jwt() post-processor injects the Jwt directly, so a mock satisfies construction.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private RequestPostProcessor jwtWithGroups(List<String> groups) {
        return jwt()
                .jwt(j -> j.claim("cognito:groups", groups))
                .authorities(j -> CONVERTER.convert(j).getAuthorities());
    }

    private static final String BODY = """
        { "revisionCount": 0,
          "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": 5000 } ],
          "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
          "otherCostsVolume": 0 }
        """;

    @Test
    @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> PUT 403 problem+json")
    void put_noPermission_returns403() throws Exception {
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "518").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwtWithGroups(List.of())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("no EDIT_SCHEDULE (empty cognito:groups) -> DELETE 403 problem+json")
    void delete_noPermission_returns403() throws Exception {
        mockMvc.perform(delete(ENDPOINT)
                        .param("millId", "519").param("year", "2021")
                        .with(jwtWithGroups(List.of())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("ILCR_SUBMITTER holds EDIT_SCHEDULE -> PUT passes authz (not 403)")
    void put_submitter_passesAuthorization() throws Exception {
        // Dedicated mill 521 (REVISION_COUNT 0, only this test writes it) so the hard-coded token in
        // BODY can never be made stale by another class sharing the container.
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "521").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
                .andExpect(status().is2xxSuccessful());
    }
}
