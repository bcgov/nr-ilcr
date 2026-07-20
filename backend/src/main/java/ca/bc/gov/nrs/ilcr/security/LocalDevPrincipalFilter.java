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
 * Local-development principal bridge used ONLY when {@code ilcr.security.enabled=false} (AD-7).
 * Seeds the SecurityContext with a configurable role authority (default SUBMITTER) so the same
 * {@code @PreAuthorize} action checks run until FAM/JWT wiring is enabled. Never registered when
 * security is enabled.
 */
public class LocalDevPrincipalFilter extends OncePerRequestFilter {

  private final Role localDevRole;

  public LocalDevPrincipalFilter(Role localDevRole) {
    this.localDevRole = localDevRole;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      var authentication = new UsernamePasswordAuthenticationToken(
          "dev-" + localDevRole.name().toLowerCase(java.util.Locale.ROOT),
          "N/A",
          List.of(new SimpleGrantedAuthority(localDevRole.name())));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
