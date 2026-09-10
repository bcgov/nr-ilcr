package ca.bc.gov.nrs.ilcr.millcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.security.JwtRoleChecker;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit coverage for the Story 5.5 identity/role resolution in {@link MillContextController} — the
 * {@code isAdmin} flag and the {@code currentUserGuid()} branch that reads the raw {@code
 * custom:idp_user_id} claim off the {@code SecurityContext}. Mocked service + role checker; no
 * Spring container. (The scoped SQL and the HTTP path are proven by the Oracle ITs; this pins the
 * controller's caller-resolution logic — admin vs submitter, real Jwt vs the dev mock principal.)
 */
@ExtendWith(MockitoExtension.class)
class MillContextControllerTest {

  @Mock private MillContextService millContextService;
  @Mock private JwtRoleChecker roleChecker;
  @InjectMocks private MillContextController controller;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(Object principal) {
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    if (principal instanceof Jwt jwt) {
      ctx.setAuthentication(new JwtAuthenticationToken(jwt));
    } else if (principal != null) {
      ctx.setAuthentication(new UsernamePasswordAuthenticationToken(principal, "N/A", List.of()));
    }
    SecurityContextHolder.setContext(ctx);
  }

  private static Jwt jwtWithIdpUserId(String guid) {
    return Jwt.withTokenValue("t").header("alg", "none").claim("custom:idp_user_id", guid).build();
  }

  @Test
  @DisplayName("admin → listMills(isAdmin=true, guid) — the admin flag drives the call")
  void listMills_admin_passesIsAdminTrue() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(true);
    authenticate(jwtWithIdpUserId("ADMINGUID")); // guid present but admin is tied to no mill
    when(millContextService.listMills(true, "ADMINGUID")).thenReturn(List.<MillSummary>of());

    assertEquals(200, controller.listMills().getStatusCode().value());
    verify(millContextService).listMills(true, "ADMINGUID");
  }

  @Test
  @DisplayName("submitter with a real Jwt → listMills(false, custom:idp_user_id)")
  void listMills_submitterJwt_passesResolvedGuid() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticate(jwtWithIdpUserId("SUBGUID"));

    controller.listMills();

    verify(millContextService).listMills(false, "SUBGUID");
  }

  @Test
  @DisplayName("dev mock principal → listMills(false, its stand-in directory GUID)")
  void listMills_mockUserPrincipal_passesItsGuid() {
    // What MockPrincipalFilter actually presents with security off (#385). Before it carried a
    // GUID this call passed "" and a mock submitter was scoped to ZERO mills — an empty Home
    // dropdown, which is what broke local dev and the whole e2e suite under Story 16.1.
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticate(new MockUserPrincipal("dev-submitter", "MOCKGUIDAAAABBBBCCCCDDDD00000001"));

    controller.listMills();

    verify(millContextService).listMills(false, "MOCKGUIDAAAABBBBCCCCDDDD00000001");
  }

  @Test
  @DisplayName("non-Jwt principal with no identity → listMills(false, \"\") — fail-closed")
  void listMills_identitylessNonJwtPrincipal_passesBlankGuid() {
    // Not the dev mock principal (that is the case above) — any OTHER non-Jwt authentication,
    // which carries nothing we can scope by. Must fail closed to an empty list, never all mills.
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    authenticate("some-other-principal"); // UsernamePasswordAuthenticationToken, no JWT claims

    controller.listMills();

    verify(millContextService).listMills(false, "");
  }

  @Test
  @DisplayName("no authentication → listMills(false, \"\") — blank guid")
  void listMills_noAuthentication_passesBlankGuid() {
    when(roleChecker.hasConcreteRole("ADMIN")).thenReturn(false);
    SecurityContextHolder.clearContext();

    controller.listMills();

    verify(millContextService).listMills(false, "");
  }
}
