package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain chain;

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
    new MockPrincipalFilter(Role.ADMIN).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(auth);
    assertEquals("dev-admin", auth.getPrincipal());
    assertEquals(Set.of("ADMIN"), authorityNames(auth));
    verify(chain).doFilter(request, response);
  }

  @Test
  void seedsConfiguredRole_submitter() throws Exception {
    new MockPrincipalFilter(Role.SUBMITTER).doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertEquals("dev-submitter", auth.getPrincipal());
    assertEquals(Set.of("SUBMITTER"), authorityNames(auth));
    verify(chain).doFilter(request, response);
  }

  @Test
  void doesNotOverwrite_whenAlreadyAuthenticated() throws Exception {
    Authentication existing = new UsernamePasswordAuthenticationToken(
        "real-user", "N/A", List.of(new SimpleGrantedAuthority("SUBMITTER")));
    SecurityContextHolder.getContext().setAuthentication(existing);

    new MockPrincipalFilter(Role.ADMIN).doFilterInternal(request, response, chain);

    assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
    verify(chain).doFilter(request, response);
  }
}
