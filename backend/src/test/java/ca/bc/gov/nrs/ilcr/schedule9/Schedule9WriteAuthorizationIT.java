package ca.bc.gov.nrs.ilcr.schedule9;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Story 9.2 acceptance — authorization on the Schedule 9 writes (AC1/AC7; AD-7). Security ON,
 * driving the real {@code oauth2ResourceServer} chain and {@code @PreAuthorize}: {@code
 * EDIT_SCHEDULE} on POST/PUT/DELETE, {@code VIEW_SCHEDULE} on {@code POST /check-status}.
 * Authorities come from the PRODUCTION {@link CognitoGroupsJwtAuthenticationConverter}.
 *
 * <p>The 403 bodies are VALID on purpose ({@code @Valid}/{@code @Validated} bind BEFORE
 * {@code @PreAuthorize}, so an invalid body would 400 and prove nothing about authorization). Mill
 * 706 is this class's own fixture, track {@code 'S'}: an authorized caller reaches the Draft gate
 * and gets 409 — proof {@code @PreAuthorize} let it through — while nothing is written either way.
 *
 * <p>Coverage gap (inherited): a "holds VIEW but not EDIT -> 403" case is unreachable today because
 * both shipped roles hold {@code EDIT_SCHEDULE}; it becomes testable when the FAM auth story (DL-6)
 * adds a narrower view-only role.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 9 writes — authorization on EDIT_SCHEDULE / VIEW_SCHEDULE (AC1/AC7)")
class Schedule9WriteAuthorizationIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule9/records";
  private static final String CHECK_STATUS = "/api/v1/schedule9/check-status";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  /**
   * A VALID body (with a revision token, so a PUT 403 fires at @PreAuthorize, not at @Validated).
   */
  private static final String VALID_BODY =
      """
      {"contractorId":"Authz","contractualItemCode":108,"unitCode":"M3",
       "biogeoclimaticZone":"BZ1","sourceCode":"A","revisionCount":0}
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
                .param("millId", "706")
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
            put(RECORDS + "/9131")
                .with(csrf())
                .param("millId", "706")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /records/{id} with no group -> 403")
  void deleteRecord_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/9131")
                .with(csrf())
                .param("millId", "706")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /check-status with no group -> 403 (VIEW_SCHEDULE required)")
  void checkStatus_noGroup_returns403() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "702")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("check-status as ILCR_SUBMITTER -> 200 (VIEW_SCHEDULE, no Draft gate)")
  void checkStatus_submitter_returns200() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "702")
                .param("year", "2021")
                .with(canonicalSubmitter()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)));
  }

  @Test
  @DisplayName(
      "ILCR_SUBMITTER holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void submitter_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "706")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(canonicalSubmitter()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("ILCR_ADMIN holds EDIT_SCHEDULE -> authz passes (not 403); non-Draft gate -> 409")
  void admin_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            post(RECORDS)
                .with(csrf())
                .param("millId", "706")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("DELETE as an authorized caller also reaches the Draft gate, not a 403")
  void delete_passesEditAuthorization() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/9131")
                .with(csrf())
                .param("millId", "706")
                .param("year", "2021")
                .with(canonicalSubmitter()))
        .andExpect(status().isConflict());
  }
}
