package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Story 13.1/13.2 acceptance — authorization (AD-7) on every Schedule 7B endpoint: {@code
 * VIEW_SCHEDULE} for the GET read and the POST check-status, {@code EDIT_SCHEDULE} for the
 * POST/PUT/DELETE culvert writes. Security ON: drives the real {@code oauth2ResourceServer} chain +
 * {@code @PreAuthorize}, with authorities derived through the production {@link
 * CognitoGroupsJwtAuthenticationConverter}.
 *
 * <p>This is where slice S30 / BR-08 lands. Legacy gated the page on its own WebADE action key
 * {@code schedule7B}, derived from the view filename and distinct from the coarser {@code
 * 'schedules'} menu key; the FAM two-group model has no per-page key, so the check is applied to
 * EVERY endpoint here instead. The 403 BODY text comes from the shared {@code
 * GlobalExceptionHandler} and is app-wide, so these tests assert the STATUS and the problem+json
 * content type rather than the legacy {@code webadeNotAuthorizedErrorMsg} wording.
 *
 * <p>The two production roles both hold VIEW+EDIT, so the write coverage asserts (a) an
 * unauthorized caller (no group / a foreign group) is denied 403 on each write, and (b) an
 * authorized role clears {@code @PreAuthorize} — proven with non-mutating requests (unknown id →
 * 404, check-status → 200) so this class, which has no per-test cleanup, never writes to the shared
 * fixture. Write-body-shaped requests carry a VALID body so a 403 comes from authorization, not
 * from bean validation (which is evaluated during argument resolution, BEFORE
 * {@code @PreAuthorize}).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 7B — authorization on VIEW_SCHEDULE / EDIT_SCHEDULE (S30/BR-08)")
class Schedule7bAuthorizationIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7b";
  private static final String CULVERTS = ENDPOINT + "/culverts";
  private static final String CHECK_STATUS = ENDPOINT + "/check-status";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  /**
   * A valid culvert body (default + OnUpdate groups) so a denial is authorization, not validation.
   */
  private static final String VALID_BODY =
      """
      {
        "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
        "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
        "comments": "AuthZ probe", "revisionCount": 0
      }
      """;

  private static final String VALID_BATCH =
      """
      {"culverts": [
        {"culvertReportId": 7801, "culvert": {
          "culvertTypeCode": "R", "spanSize": 1200, "riseSize": 900, "length": 12.5,
          "culvertPieceCount": 3, "materialCost": 4000, "installCost": 1500,
          "comments": "AuthZ probe", "revisionCount": 0}}
      ]}
      """;

  @MockitoBean private JwtDecoder jwtDecoder;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  private RequestPostProcessor jwtWithGroups(List<String> groups) {
    return jwt()
        .jwt(j -> j.claim("cognito:groups", groups))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  // --- Read + check-status require VIEW_SCHEDULE
  // ---------------------------------------------------

  @Test
  @DisplayName("S30: no group -> 403 ProblemDetail on the read")
  void noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("S30: a foreign application's group -> 403 on the read")
  void foreignGroup_returns403() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("ILCR_SUBMITTER clears authz on the read (200)")
  void submitter_passesAuthorizationOnRead() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("ILCR_ADMIN clears authz on the read (200)")
  void admin_passesAuthorizationOnRead() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("check-status without a group -> 403 (VIEW_SCHEDULE)")
  void checkStatus_noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("check-status with ILCR_SUBMITTER clears authz (200, and mutates nothing)")
  void checkStatus_submitter_passesAuthorization() throws Exception {
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isOk());
  }

  // --- Writes require EDIT_SCHEDULE
  // ---------------------------------------------------------------

  @Test
  @DisplayName("record without a group -> 403 (EDIT_SCHEDULE)")
  void addCulvert_noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            post(CULVERTS)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("record with a foreign group -> 403 (EDIT_SCHEDULE)")
  void addCulvert_foreignGroup_returns403() throws Exception {
    mockMvc
        .perform(
            post(CULVERTS)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("correct without a group -> 403 (EDIT_SCHEDULE)")
  void updateCulvert_noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            put(CULVERTS + "/7801")
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("save-all without a group -> 403 (EDIT_SCHEDULE)")
  void saveAllCulverts_noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            put(CULVERTS)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BATCH)
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("save-all with a foreign group -> 403 (EDIT_SCHEDULE)")
  void saveAllCulverts_foreignGroup_returns403() throws Exception {
    mockMvc
        .perform(
            put(CULVERTS)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BATCH)
                .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("delete without a group -> 403 (EDIT_SCHEDULE, and the culvert survives)")
  void deleteCulvert_noPermission_returns403() throws Exception {
    mockMvc
        .perform(
            delete(CULVERTS + "/7801")
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of())))
        .andExpect(status().isForbidden());

    // The DisplayName promises survival, so assert it: a denial must not have reached the service.
    Integer stillThere =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = 7801",
            Integer.class);
    assertThat(stillThere).isEqualTo(1);
  }

  @Test
  @DisplayName("delete with a foreign group -> 403 (EDIT_SCHEDULE)")
  void deleteCulvert_foreignGroup_returns403() throws Exception {
    mockMvc
        .perform(
            delete(CULVERTS + "/7801")
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("an authorized role clears DELETE's @PreAuthorize (proven non-mutatingly: 404)")
  void authorizedRoleClearsWriteAuthorization() throws Exception {
    // An unknown id reaches the service and 404s — which proves @PreAuthorize passed without this
    // class committing anything to the shared fixture.
    mockMvc
        .perform(
            delete(CULVERTS + "/999999")
                .param("millId", "514")
                .param("year", "2021")
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "an authorized role clears POST, PUT and save-all too (non-mutatingly: 409 on 517/S)")
  void authorizedRoleClearsEveryWriteVerb() throws Exception {
    // Previously only DELETE had a positive probe, so a typo in POST's, PUT's or save-all's action
    // name — 'EDIT_SCHEDULES', or an accidental VIEW_SCHEDULE — would have denied both production
    // roles on every add and every save while this class stayed green (hasPermission silently
    // DENIES
    // an unknown action rather than failing loudly).
    //
    // Each probe targets mill 517, whose 1-10 track is Submitted, so it clears @PreAuthorize and
    // then
    // stops at the service's Draft gate with a 409 — reaching the service is the proof, and nothing
    // is
    // written to the shared fixture.
    mockMvc
        .perform(
            post(CULVERTS)
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            put(CULVERTS + "/7851")
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .with(jwtWithGroups(List.of("ILCR_ADMIN"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            put(CULVERTS)
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"culverts": [
                  {"culvertReportId": 7851, "culvert": {
                    "culvertTypeCode": "PA", "spanSize": null, "riseSize": null, "length": 6.5,
                    "culvertPieceCount": 4, "materialCost": 1800, "installCost": 300,
                    "comments": null, "revisionCount": 0}}
                ]}
                """)
                .with(jwtWithGroups(List.of("ILCR_SUBMITTER"))))
        .andExpect(status().isConflict());

    // Nothing was mutated by any of the three.
    var stored =
        jdbc.queryForMap(
            "SELECT CULVERT_PIECE_COUNT, REVISION_COUNT FROM THE.CULVERT_REPORT "
                + "WHERE CULVERT_REPORT_ID = 7851");
    assertThat(((Number) stored.get("CULVERT_PIECE_COUNT")).intValue()).isEqualTo(4);
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
  }
}
