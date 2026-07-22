package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dev/UAT mock principal used ONLY when {@code ilcr.security.enabled=false} (AD-7). Seeds the
 * SecurityContext with a configurable role authority (default SUBMITTER) so the SAME
 * {@code @PreAuthorize} action checks run with security off — business logic never branches on the
 * toggle. Never registered when security is enabled.
 */
public class MockPrincipalFilter extends OncePerRequestFilter {

  private final Role mockRole;

  public MockPrincipalFilter(Role mockRole) {
    this.mockRole = mockRole;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      var authentication = new UsernamePasswordAuthenticationToken(
          "dev-" + mockRole.name().toLowerCase(java.util.Locale.ROOT),
          "N/A",
          List.of(new SimpleGrantedAuthority(mockRole.name())));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
