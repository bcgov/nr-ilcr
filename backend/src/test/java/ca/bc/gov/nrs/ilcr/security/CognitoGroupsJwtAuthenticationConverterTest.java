package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test for the cognito:groups -&gt; role authority mapping (AD-7). Proves app-scoped matching:
 * ILCR_/org-prefixed groups map; a foreign app's group with a bare _ADMIN suffix does not.
 */
class CognitoGroupsJwtAuthenticationConverterTest {

  private final CognitoGroupsJwtAuthenticationConverter converter =
      new CognitoGroupsJwtAuthenticationConverter();

  private Jwt jwtWithGroups(Object groupsClaim) {
    Jwt.Builder builder = Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .subject("user");
    if (groupsClaim != null) {
      builder.claim("cognito:groups", groupsClaim);
    }
    return builder.build();
  }

  private Set<String> authorities(AbstractAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  @Test
  void ilcrSubmitter_mapsToSubmitter() {
    assertEquals(Set.of("SUBMITTER"), authorities(converter.convert(jwtWithGroups(List.of("ILCR_SUBMITTER")))));
  }

  @Test
  void ilcrAdmin_mapsToAdmin() {
    assertEquals(Set.of("ADMIN"), authorities(converter.convert(jwtWithGroups(List.of("ILCR_ADMIN")))));
  }

  @Test
  void orgPrefixedGroup_matches() {
    assertEquals(Set.of("ADMIN"), authorities(converter.convert(jwtWithGroups(List.of("NRS_ILCR_ADMIN")))));
  }

  @Test
  void foreignGroup_noAuthorities() {
    assertTrue(authorities(converter.convert(jwtWithGroups(List.of("SOME_OTHER_APP_ADMIN")))).isEmpty());
  }

  @Test
  void emptyGroups_noAuthorities() {
    assertTrue(authorities(converter.convert(jwtWithGroups(List.of()))).isEmpty());
  }

  @Test
  void missingGroupsClaim_noAuthorities() {
    assertTrue(authorities(converter.convert(jwtWithGroups(null))).isEmpty());
  }

  @Test
  void singleStringGroupsClaim_mapsRole() {
    // A groups claim emitted as a plain String (not an array) must still map.
    assertEquals(Set.of("ADMIN"), authorities(converter.convert(jwtWithGroups("ILCR_ADMIN"))));
  }

  @Test
  void bothGroups_mapBothRoles() {
    assertEquals(
        Set.of("SUBMITTER", "ADMIN"),
        authorities(converter.convert(jwtWithGroups(List.of("ILCR_SUBMITTER", "ILCR_ADMIN")))));
  }
}
