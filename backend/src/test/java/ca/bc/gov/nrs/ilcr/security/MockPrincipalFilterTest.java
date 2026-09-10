package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit test for the dev/UAT mock principal filter (AD-7). Verifies it seeds a role-carrying
 * principal only when the context is empty, and always continues the chain.
 */
@ExtendWith(MockitoExtension.class)
class MockPrincipalFilterTest {

  @Mock private HttpServletRequest request;

  @Mock private HttpServletResponse response;

  @Mock private FilterChain chain;

  /** Any non-blank value; the filter only carries it through, it never interprets it. */
  private static final String TEST_GUID = "TESTGUIDAAAABBBBCCCCDDDD00000001";

  private static MockPrincipalFilter filter(Role defaultRole) {
    return new MockPrincipalFilter(defaultRole, TEST_GUID);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private Set<String> authorityNames(Authentication auth) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  @Test
  void seedsMockPrincipal_whenContextEmpty() throws Exception {
    filter(Role.ADMIN).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);
    assertEquals("dev-admin", auth.getName());
    assertEquals(Set.of("ADMIN"), authorityNames(auth));
    verify(chain).doFilter(request, response);
  }

  @Test
  void seedsConfiguredRole_submitter() throws Exception {
    filter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals("dev-submitter", auth.getName());
    assertEquals(Set.of("SUBMITTER"), authorityNames(auth));
    verify(chain).doFilter(request, response);
  }

  @Test
  void headerRole_overridesTheConfiguredDefault() throws Exception {
    // The SPA sends the selected mock user's roles; an admin header must grant ILCR_ADMIN even when
    // the configured default is SUBMITTER (so the admin-only actions are reachable in local dev).
    org.mockito.Mockito.when(request.getHeader("X-Mock-Groups")).thenReturn("ILCR_ADMIN");
    filter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals("dev-admin", auth.getName());
    assertEquals(Set.of("ADMIN"), authorityNames(auth));
  }

  @Test
  void headerRoles_supportADualRoleUser() throws Exception {
    org.mockito.Mockito.when(request.getHeader("X-Mock-Groups"))
        .thenReturn("ILCR_ADMIN,ILCR_SUBMITTER");
    filter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    assertEquals(
        Set.of("ADMIN", "SUBMITTER"),
        authorityNames(SecurityContextHolder.getContext().getAuthentication()));
  }

  @Test
  void unknownHeaderRole_fallsBackToTheConfiguredDefault() throws Exception {
    org.mockito.Mockito.when(request.getHeader("X-Mock-Groups")).thenReturn("SOME_OTHER_APP_ADMIN");
    filter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    assertEquals(
        Set.of("SUBMITTER"),
        authorityNames(SecurityContextHolder.getContext().getAuthentication()));
  }

  @Test
  void doesNotOverwrite_whenAlreadyAuthenticated() throws Exception {
    Authentication existing =
        new UsernamePasswordAuthenticationToken(
            "real-user", "N/A", List.of(new SimpleGrantedAuthority("SUBMITTER")));
    SecurityContextHolder.getContext().setAuthentication(existing);

    filter(Role.ADMIN).doFilterInternal(request, response, chain);

    assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
    verify(chain).doFilter(request, response);
  }

  @Test
  void trimsTheConfiguredGuid_soStrayWhitespaceCannotSilentlyMatchNothing() throws Exception {
    // A trailing space in application.yml or an env var matches no ILCR_MILL_USER_XREF row, and the
    // ONLY symptom is an empty Home dropdown — indistinguishable from missing seed data, which is
    // the confusion this whole change set exists to remove.
    new MockPrincipalFilter(Role.SUBMITTER, "  " + TEST_GUID + "  ")
        .doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals(TEST_GUID, ((MockUserPrincipal) auth.getPrincipal()).userGuid());
  }

  @Test
  void blankConfiguredGuid_stillSeedsAPrincipal_ratherThanFailing() throws Exception {
    // Deliberately NOT fatal: an ADMIN mock bypasses scoping and still works, so refusing to start
    // would be disproportionate. The filter warns instead (see its constructor). What must hold is
    // that the request still gets a principal, and the GUID is blank rather than null — blank is
    // what MillContextService.listMills fail-closes on.
    new MockPrincipalFilter(Role.SUBMITTER, "   ").doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals("", ((MockUserPrincipal) auth.getPrincipal()).userGuid());
    verify(chain).doFilter(request, response);
  }

  @Test
  void carriesTheDirectoryGuid_inATypedPrincipalNotTheName() throws Exception {
    filter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // The GUID is what MillContextController reads for the identity-scoped mill list; without it a
    // mock submitter fail-closes to zero mills (Story 5.5) and the e2e suite cannot reach a mill.
    assertInstanceOf(MockUserPrincipal.class, auth.getPrincipal());
    assertEquals(TEST_GUID, ((MockUserPrincipal) auth.getPrincipal()).userGuid());
    // And it must NOT be the name: getName() feeds the VARCHAR2(30) ENTRY_USERID/UPDATE_USERID
    // audit columns on every write, while a FAM GUID is 32 chars — naming the principal after it
    // would ORA-12899 every save. This is the guard against "simplifying" it into the name, and it
    // also pins that MockUserPrincipal implements AuthenticatedPrincipal: without that,
    // AbstractAuthenticationToken.getName() falls through to the record's toString() and the audit
    // columns get `MockUserPrincipal[name=dev-submitter, userGuid=...]` instead.
    assertEquals("dev-submitter", auth.getName());
  }
}
