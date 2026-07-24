package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.dto.base.IdentityProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit test for the SpEL role/idp checks used from {@code @PreAuthorize}. Drives the
 * {@link SecurityContextHolder} directly — no Spring context, no DB.
 */
class JwtRoleCheckerTest {

  private final JwtRoleChecker checker = new JwtRoleChecker();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  /** Seed the context with an authenticated principal carrying the given authorities. */
  private void authenticateWith(String... authorities) {
    var granted = List.of(authorities).stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
    var token = new UsernamePasswordAuthenticationToken("user", "N/A", granted);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  private Jwt jwtWithProvider(String idpName) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .subject("user")
        .claim("custom:idp_name", idpName)
        .build();
  }

  // ---- hasRole: exact, prefix, and substring matches (all case-insensitive) ----

  @Test
  void hasRole_exactMatch_ignoringCase() {
    authenticateWith("ADMIN");
    assertTrue(checker.hasRole("admin"));
  }

  @Test
  void hasRole_abstractPrefixMatch() {
    authenticateWith("ADMIN_12345");
    assertTrue(checker.hasRole("admin"));
  }

  @Test
  void hasRole_substringMatch() {
    authenticateWith("ILCR_SUBMITTER");
    assertTrue(checker.hasRole("submitter"));
  }

  @Test
  void hasRole_noMatch_returnsFalse() {
    authenticateWith("SUBMITTER");
    assertFalse(checker.hasRole("admin"));
  }

  // ---- hasConcreteRole: exact match only ----

  @Test
  void hasConcreteRole_exactMatch() {
    authenticateWith("ADMIN");
    assertTrue(checker.hasConcreteRole("admin"));
  }

  @Test
  void hasConcreteRole_prefixedRole_isNotConcrete() {
    authenticateWith("ILCR_ADMIN");
    assertFalse(checker.hasConcreteRole("admin"));
  }

  // ---- hasAbstractRole: prefix_clientId ----

  @Test
  void hasAbstractRole_matchesPrefixAndClientId() {
    authenticateWith("PLANNER_12345");
    assertTrue(checker.hasAbstractRole("PLANNER", "12345"));
  }

  @Test
  void hasAbstractRole_wrongClientId_returnsFalse() {
    authenticateWith("PLANNER_12345");
    assertFalse(checker.hasAbstractRole("PLANNER", "99999"));
  }

  // ---- hasRoleMatching: guards on the authentication state ----

  @Test
  void hasRoleMatching_noAuthentication_returnsFalse() {
    assertFalse(checker.hasRoleMatching(role -> true));
  }

  @Test
  void hasRoleMatching_notAuthenticated_returnsFalse() {
    // Two-arg token is created with isAuthenticated() == false.
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("user", "N/A"));
    assertFalse(checker.hasRoleMatching(role -> true));
  }

  // ---- hasIdpProvider ----

  @Test
  void hasIdpProvider_byString_matchesClaim() {
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtWithProvider("idir"),
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));
    assertTrue(checker.hasIdpProvider("idir"));
  }

  @Test
  void hasIdpProvider_byString_differentProvider_returnsFalse() {
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtWithProvider("idir"),
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));
    assertFalse(checker.hasIdpProvider("bceid"));
  }

  @Test
  void hasIdpProvider_byString_unknownClaim_returnsFalse() {
    // fromClaim returns empty -> short-circuits before touching the JWT.
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtWithProvider("idir"),
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));
    assertFalse(checker.hasIdpProvider("not-a-provider"));
  }

  @Test
  void hasIdpProvider_byEnum_matches() {
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtWithProvider("idir"),
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));
    assertTrue(checker.hasIdpProvider(IdentityProvider.IDIR));
  }

  @Test
  void hasIdpProvider_noAuthentication_throwsIllegalState() {
    assertThrows(IllegalStateException.class,
        () -> checker.hasIdpProvider(IdentityProvider.IDIR));
  }

  @Test
  void hasIdpProvider_principalNotAJwt_throwsIllegalState() {
    authenticateWith("SUBMITTER");
    assertThrows(IllegalStateException.class,
        () -> checker.hasIdpProvider(IdentityProvider.IDIR));
  }
}
