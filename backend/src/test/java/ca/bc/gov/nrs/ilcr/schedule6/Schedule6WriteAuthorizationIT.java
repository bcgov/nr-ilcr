package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Story 8.2 acceptance — authorization on the Schedule 6 writes (AC8; AD-7). Security ON: drives
 * the real {@code oauth2ResourceServer} chain + {@code @PreAuthorize} ({@code EDIT_SCHEDULE} on the
 * three writes, {@code VIEW_SCHEDULE} on check-status), authorities via the production {@link
 * CognitoGroupsJwtAuthenticationConverter}.
 *
 * <p>Bodies on the 403 write tests are VALID because {@code @Valid} body binding runs during
 * argument resolution BEFORE {@code @PreAuthorize} fires — an invalid body would yield 400, not the
 * 403 under test. The "authorized" proof POSTs to the non-Draft mill (662/'S'): authz passes so the
 * request is NOT 403, and the service's Draft gate rejects it 409 WITHOUT mutating anything — no
 * fixture churn.
 *
 * <p>Mill <b>666</b> is this class's own fixture (V32), added at code review 2026-08-04. These
 * probes previously fired at 661/2021 — the year {@code Schedule6WriteIT.optimisticLockPerRecord}
 * owns — so an {@code @PreAuthorize} regression would have let a write land on that test's lock
 * target and failed a different suite instead of this one.
 *
 * <p><b>Coverage gap (inherited, recorded on 25.2):</b> a "holds VIEW but not EDIT → 403" case is
 * unreachable today — both shipped roles hold {@code EDIT_SCHEDULE}. It becomes testable when the
 * FAM auth story (DL-6) introduces a narrower view-only role.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 6 writes — authorization on EDIT_SCHEDULE / VIEW_SCHEDULE (AC8)")
class Schedule6WriteAuthorizationIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule6/records";
  private static final String COMMENTS = "/api/v1/schedule6/general-comments";
  private static final String CHECK_STATUS = "/api/v1/schedule6/check-status";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  // A valid record body — so a 403 fires at @PreAuthorize, not at @Valid (which runs first).
  private static final String VALID_BODY =
      """
        {"areaType":"01","supplyBlock":"01B","volume":10,"cost":100,"revisionCount":0}
        """;

  @MockitoBean private JwtDecoder jwtDecoder;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  @Test
  @DisplayName("POST /records with no group -> 403")
  void addRecord_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "666")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
  }

  @Test
  @DisplayName("PUT /records/{id} with no group -> 403")
  void updateRecord_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            put(RECORDS + "/8358")
                .with(csrf())
                .param("millId", "666")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /general-comments with no group -> 403")
  void saveGeneralComments_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            put(COMMENTS)
                .with(csrf())
                .param("millId", "666")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generalComments\":\"x\"}")
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
                .param("millId", "663")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("check-status with VIEW_SCHEDULE only in play (ILCR_SUBMITTER) -> 200")
  void checkStatus_submitter_returns200() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "663")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
  }

  @Test
  @DisplayName(
      "ILCR_SUBMITTER holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void submitter_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isConflict()); // 409 not-editable — authz passed, no mutation
  }

  @Test
  @DisplayName("ILCR_ADMIN holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void admin_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().isConflict());
  }
}
