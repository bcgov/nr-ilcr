package ca.bc.gov.nrs.ilcr.millcontext;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance tests — Story 5.5 (per-user mill filtering on Home). {@code GET /api/v1/mills} is now
 * caller-scoped: an {@code ILCR_SUBMITTER} sees only mills they are ACTIVELY associated to (legacy
 * {@code getMills(userGuid)}, {@code imux.activeDate <> null} → new-app {@code INACTIVE_DATE IS
 * NULL}); closed associated mills are still included (S06); an ended assignment is excluded; a
 * submitter with no associations sees an empty list (fail-closed). Admin's all-mills view is proven
 * in {@link MillContextListIT}.
 *
 * <p>Runs with security ON (app default). Identity is the raw {@code custom:idp_user_id} claim
 * (equals {@code ILCR_MILL_USER_XREF.USER_GUID}); the {@code jwt()} post-processor injects it.
 * Seeds its own {@code THE.ILCR_USER} + {@code THE.ILCR_MILL_USER_XREF} rows against the shared V2
 * mills (514 ACT, 515 ACT, 516 CLS, 517 ACT) and cleans them up, so it is order-independent of
 * other ITs.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Home mill list — per-user scoping (Story 5.5)")
class MillContextListScopeIT extends AbstractOracleIT {

  // 32 chars — the directory-GUID column width (custom:idp_user_id).
  private static final String GUID = "SCOPEIT1BBBBCCCCDDDDEEEEFFFF0001";
  private static final String GUID_NONE = "SCOPEIT2BBBBCCCCDDDDEEEEFFFF0002";
  // A DIFFERENT submitter, used to prove cross-user isolation: a mill actively assigned to this
  // user must NOT appear in GUID's list (the WHERE USER_GUID predicate actually partitions by
  // user).
  private static final String GUID_OTHER = "SCOPEIT3BBBBCCCCDDDDEEEEFFFF0003";

  private static final RequestPostProcessor SUBMITTER =
      jwt()
          .jwt(j -> j.claim("custom:idp_user_id", GUID))
          .authorities(new SimpleGrantedAuthority("SUBMITTER"));

  private static final RequestPostProcessor SUBMITTER_NO_ASSOC =
      jwt()
          .jwt(j -> j.claim("custom:idp_user_id", GUID_NONE))
          .authorities(new SimpleGrantedAuthority("SUBMITTER"));

  @MockitoBean private JwtDecoder jwtDecoder;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  @AfterEach
  void cleanSeed() {
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_MILL_USER_XREF WHERE USER_GUID IN (?, ?, ?)",
        GUID,
        GUID_NONE,
        GUID_OTHER);
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_USER WHERE USER_GUID IN (?, ?, ?)", GUID, GUID_NONE, GUID_OTHER);
  }

  private void seedUser(String guid) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_USER
            (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES (?, 'LICENSEE', 'Y', 0, 'scopeit', SYSDATE, 'scopeit', SYSDATE)
        """,
        guid);
  }

  /** Active assignment (INACTIVE_DATE NULL) of {@code guid} to {@code millId}. */
  private void seedActive(String guid, long millId) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_MILL_USER_XREF
            (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES (?, ?, SYSDATE, NULL, 0, 'scopeit', SYSDATE, 'scopeit', SYSDATE)
        """,
        millId,
        guid);
  }

  /** Ended assignment (ACTIVE_DATE NULL, INACTIVE_DATE set) of {@code guid} to {@code millId}. */
  private void seedEnded(String guid, long millId) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_MILL_USER_XREF
            (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES (?, ?, NULL, SYSDATE, 0, 'scopeit', SYSDATE, 'scopeit', SYSDATE)
        """,
        millId,
        guid);
  }

  @Test
  @DisplayName(
      "GET /api/v1/mills — submitter sees only actively-associated mills (closed included, ended excluded)")
  void submitter_seesOnlyActivelyAssociatedMills() throws Exception {
    seedUser(GUID);
    seedActive(GUID, 514L); // ACT mill — associated
    seedActive(GUID, 516L); // CLS mill — associated; closed still shown (S06)
    seedEnded(GUID, 515L); // ended assignment — must be excluded
    // Cross-user isolation: mill 517 is actively assigned to a DIFFERENT submitter. Its absence
    // from GUID's list proves the USER_GUID predicate partitions by user (not just "any active
    // assignment") — a query dropping `AND USER_GUID = :userGuid` would wrongly surface it here.
    seedUser(GUID_OTHER);
    seedActive(GUID_OTHER, 517L);

    mockMvc
        .perform(get("/api/v1/mills").with(SUBMITTER).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // Associated active mills present…
        .andExpect(jsonPath("$[?(@.millId == 514)].millStatusCode", contains("ACT")))
        .andExpect(jsonPath("$[?(@.millId == 516)].millStatusCode", contains("CLS")))
        // …ended assignment (515) absent, and mill 517 — actively assigned to ANOTHER user —
        // absent.
        .andExpect(jsonPath("$[?(@.millId == 515)]").isEmpty())
        .andExpect(jsonPath("$[?(@.millId == 517)]").isEmpty());
  }

  @Test
  @DisplayName(
      "GET /api/v1/mills — submitter with no associations sees an empty list (fail-closed)")
  void submitter_noAssociations_returnsEmpty() throws Exception {
    seedUser(GUID_NONE); // account exists, but zero assignments

    mockMvc
        .perform(get("/api/v1/mills").with(SUBMITTER_NO_ASSOC).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(0));
  }
}
