package ca.bc.gov.nrs.ilcr.schedule11;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Story 25.2 acceptance — authorization on the Schedule 11 writes (AC8; AD-7). Security ON: drives
 * the real {@code oauth2ResourceServer} chain + {@code @PreAuthorize} ({@code EDIT_SCHEDULE} on
 * POST/PUT/DELETE, {@code VIEW_SCHEDULE} on check-status), authorities via the production {@link
 * CognitoGroupsJwtAuthenticationConverter}.
 *
 * <p>Bodies on the 403 write tests are VALID because {@code @Valid} body binding runs during
 * argument resolution BEFORE {@code @PreAuthorize} fires — an invalid body would yield 400, not the
 * 403 under test. The "authorized" proof POSTs to a non-Draft mill (615/'S'): authz passes so the
 * request is NOT 403, and the service's Draft gate rejects it 409 WITHOUT mutating anything — no
 * fixture churn.
 *
 * <p><b>Coverage gap (recorded):</b> a "holds VIEW but not EDIT → 403" case is unreachable today —
 * both shipped roles ({@code ILCR_SUBMITTER}, {@code ILCR_ADMIN}) hold {@code EDIT_SCHEDULE}. It
 * becomes testable when a narrower (view-only) role exists.
 *
 * <p>TODO(DL-6, FAM auth story): when the FAM auth story introduces a narrower (view-only) role,
 * add a "holds VIEW_SCHEDULE but not EDIT_SCHEDULE → 403 on POST/PUT/DELETE" test here. The full
 * action-key mapping / narrower roles land with that story (see implementation-readiness-report
 * DL-6).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 11 writes — authorization on EDIT_SCHEDULE / VIEW_SCHEDULE (AC8)")
class Schedule11WriteAuthorizationIT extends AbstractOracleIT {

  private static final String LOCATIONS = "/api/v1/schedule11/locations";
  private static final String CHECK_STATUS = "/api/v1/schedule11/check-status";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  // A valid create body — so the 403 fires at @PreAuthorize, not at @Valid (which runs first).
  private static final String VALID_BODY =
      """
        {"location":"Authz Probe","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,
         "netArea":10.0,"revisionCount":0}
        """;

  @MockitoBean private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("POST with no group -> 403")
  void addLocation_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            post(LOCATIONS)
                .with(csrf())
                .param("millId", "614")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("PUT with no group -> 403")
  void updateLocation_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            put(LOCATIONS + "/9201")
                .with(csrf())
                .param("millId", "614")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE with no group -> 403")
  void deleteLocation_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            delete(LOCATIONS + "/9201")
                .with(csrf())
                .param("millId", "614")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("check-status with no group -> 403 (VIEW_SCHEDULE required)")
  void checkStatus_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "616")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "ILCR_SUBMITTER holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void submitter_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(LOCATIONS)
                .with(csrf())
                .param("millId", "615")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isConflict()); // 409 not-editable — proves authz passed, no mutation
  }

  @Test
  @DisplayName("ILCR_ADMIN holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void admin_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(LOCATIONS)
                .with(csrf())
                .param("millId", "615")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().isConflict());
  }
}
