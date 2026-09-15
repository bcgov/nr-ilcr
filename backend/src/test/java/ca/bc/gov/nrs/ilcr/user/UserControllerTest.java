package ca.bc.gov.nrs.ilcr.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.user.dto.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit test for {@code GET /api/v1/me}'s caller resolution — both arms, security on and off.
 *
 * <p>WHY IT EXISTS. Until now this controller was covered ONLY by {@code UserMeIT} / {@code
 * UserMeSecurityOffIT}, and {@code pom.xml} defaults {@code skip.integration.tests=true} — so CI
 * ran neither and the file scored effectively zero coverage on every scan. That went unnoticed
 * until a change here (exposing the mock's directory GUID) put new lines in an uncovered file and
 * dragged the SonarCloud new-code coverage gate under its threshold. The ITs still own the wiring
 * and the HTTP contract; this owns the branch logic, in a suite CI actually runs.
 */
@DisplayName("GET /me — caller resolution, security on and off")
class UserControllerTest {

  private static final String GUID = "MOCKGUIDAAAABBBBCCCCDDDD00000001";

  private final UserController controller = new UserController();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(Authentication auth) {
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
  }

  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none").issuedAt(Instant.EPOCH);
    claims.forEach(b::claim);
    return b.build();
  }

  @Test
  @DisplayName("security ON: the GUID, display name, email and provider come from the token")
  void jwtCaller_isBuiltFromTheTokenClaims() {
    authenticate(
        new JwtAuthenticationToken(
            jwt(
                Map.of(
                    "sub", "ignored-when-idp-user-id-present",
                    "custom:idp_user_id", "REALGUIDAAAABBBBCCCCDDDD00000009",
                    "custom:idp_display_name", "Casey, Terry L",
                    "custom:idp_name", "idir",
                    "email", "  terry.casey@gov.bc.ca  ")),
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));

    CurrentUser me = controller.me().getBody();

    assertEquals("REALGUIDAAAABBBBCCCCDDDD00000009", me.userGuid());
    assertEquals("Casey, Terry L", me.displayName());
    // trimToNull is applied, so the padding above must not survive.
    assertEquals("terry.casey@gov.bc.ca", me.email());
    // Note the normalisation: JwtPrincipalUtil.getProvider upper-cases the claim, so a lower-case
    // `idir` in the token is reported as `IDIR`. Pinned here because the SPA reads this field.
    assertEquals("IDIR", me.identityProvider());
    assertEquals(List.of("ILCR_SUBMITTER"), me.roles());
  }

  @Test
  @DisplayName("security ON: with no display-name claims, the GUID stands in")
  void jwtCaller_fallsBackToTheGuidForDisplayName() {
    authenticate(
        new JwtAuthenticationToken(
            jwt(Map.of("sub", "SUBJECTONLYAAAABBBBCCCCDDDD00001")),
            List.of(new SimpleGrantedAuthority("ADMIN"))));

    CurrentUser me = controller.me().getBody();

    // No custom:idp_user_id, so the subject is the identity; no name claims, so it is also shown.
    assertEquals("SUBJECTONLYAAAABBBBCCCCDDDD00001", me.userGuid());
    assertEquals("SUBJECTONLYAAAABBBBCCCCDDDD00001", me.displayName());
    assertNull(me.email());
    assertNull(me.identityProvider());
  }

  @Test
  @DisplayName("security OFF: the mock's stand-in directory GUID is reported, not its display name")
  void mockCaller_reportsItsDirectoryGuid() {
    // The point of the change this test was written for: `userGuid` must be the same KIND of value
    // in both modes, so anything identity-dependent in the SPA behaves the same locally as
    // deployed. Before, this field carried "dev-submitter" — a display name in an identity slot.
    authenticate(
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal("dev-submitter", GUID),
            "N/A",
            List.of(new SimpleGrantedAuthority("SUBMITTER"))));

    CurrentUser me = controller.me().getBody();

    assertEquals(GUID, me.userGuid());
    assertEquals(UserController.MOCK_DISPLAY_NAME, me.displayName());
    assertNull(me.email());
    assertNull(me.identityProvider());
    assertEquals(List.of("ILCR_SUBMITTER"), me.roles());
  }

  @Test
  @DisplayName("security OFF: any other principal falls back to its name")
  void nonMockNonJwtCaller_fallsBackToThePrincipalName() {
    authenticate(
        new UsernamePasswordAuthenticationToken(
            "some-other-principal", "N/A", List.of(new SimpleGrantedAuthority("ADMIN"))));

    CurrentUser me = controller.me().getBody();

    assertEquals("some-other-principal", me.userGuid());
    assertEquals(UserController.MOCK_DISPLAY_NAME, me.displayName());
  }

  @Test
  @DisplayName("authorities that name no known role are dropped, and duplicates collapse")
  void unknownAuthoritiesAreDropped() {
    authenticate(
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal("dev-submitter", GUID),
            "N/A",
            List.of(
                new SimpleGrantedAuthority("SUBMITTER"),
                new SimpleGrantedAuthority("SUBMITTER"),
                new SimpleGrantedAuthority("SOME_OTHER_APP_ROLE"))));

    assertEquals(List.of("ILCR_SUBMITTER"), controller.me().getBody().roles());
  }
}
