package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dev/UAT mock principal used ONLY when {@code ilcr.security.enabled=false} (AD-7). Seeds the
 * SecurityContext so the SAME {@code @PreAuthorize} action checks run with security off — business
 * logic never branches on the toggle. Never registered when security is enabled.
 *
 * <p>The role comes from the {@code X-Mock-Groups} request header (comma-separated FAM role names)
 * when present, so the SPA's mock-user selector drives the backend principal — switching to an
 * admin user actually grants {@code ILCR_ADMIN} (needed for admin-only actions like
 * MAINTAIN_CODE_TABLES). When the header is absent or names no known role, it falls back to the
 * configured default ({@code ilcr.security.mock-role}). The header is dev-only: in prod this filter
 * is not registered, so it is never consulted — it can never widen a real principal's authority.
 */
public class MockPrincipalFilter extends OncePerRequestFilter {

  static final String MOCK_GROUPS_HEADER = "X-Mock-Groups";

  private final Role defaultRole;

  public MockPrincipalFilter(Role defaultRole) {
    this.defaultRole = defaultRole;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      List<Role> roles = rolesFor(request);
      var authorities =
          roles.stream().map(role -> new SimpleGrantedAuthority(role.name())).toList();
      String name =
          "dev-"
              + roles.stream()
                  .map(role -> role.name().toLowerCase(Locale.ROOT))
                  .collect(Collectors.joining("-"));
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(name, "N/A", authorities));
    }
    filterChain.doFilter(request, response);
  }

  private List<Role> rolesFor(HttpServletRequest request) {
    String header = request.getHeader(MOCK_GROUPS_HEADER);
    if (header != null && !header.isBlank()) {
      List<Role> parsed =
          Arrays.stream(header.split(","))
              .map(String::trim)
              .filter(token -> !token.isEmpty())
              .map(Role::fromValue)
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      if (!parsed.isEmpty()) {
        return parsed;
      }
    }
    return List.of(defaultRole);
  }
}
