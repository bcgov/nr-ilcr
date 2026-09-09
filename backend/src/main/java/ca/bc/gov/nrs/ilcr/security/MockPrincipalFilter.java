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
 *
 * <p><b>The mock also carries a directory GUID</b> ({@code ilcr.security.mock-user-guid}), because
 * a role alone no longer stands in for a real principal. Since Story 5.5 the Home mill list is
 * IDENTITY-scoped: {@code MillContextService.listMills} looks the caller's GUID up in {@code
 * ILCR_MILL_USER_XREF} and fail-closes to an empty list without one. So a GUID-less mock submitter
 * saw ZERO mills, and only {@code ILCR_ADMIN} (which bypasses scoping, DL-22) saw any. That stayed
 * invisible until Story 16.1 made editability role-dependent and admin lost Draft editing, leaving
 * no single mock role able to both reach a mill and edit its Draft. An identity fixes it at the
 * cause, and lets the dev/e2e principal exercise the REAL scoped query rather than bypass it.
 *
 * <p>The DEFAULT GUID is the canonical submitter of the test-scope seed ({@code
 * R__70_test_scope_canonical_submitter.sql}), which is what the CI e2e database holds. Against any
 * other database — an extract-backed local stack, say — that GUID has no {@code
 * ILCR_MILL_USER_XREF} row, so a mock SUBMITTER is correctly scoped to nothing; point {@code
 * ilcr.security.mock-user-guid} at a GUID that database does associate.
 *
 * <p>The GUID lives in the token's {@code details}, NOT its name, and that is load-bearing: {@code
 * Authentication.getName()} is written straight into the {@code ENTRY_USERID} / {@code
 * UPDATE_USERID} audit columns by every write controller, and those are {@code VARCHAR2(30)} while
 * a FAM GUID is 32 chars — so naming the principal after it would {@code ORA-12899} on every save.
 * The name stays the short, readable {@code dev-<roles>}.
 */
public class MockPrincipalFilter extends OncePerRequestFilter {

  static final String MOCK_GROUPS_HEADER = "X-Mock-Groups";

  private final Role defaultRole;
  private final String userGuid;

  public MockPrincipalFilter(Role defaultRole, String userGuid) {
    this.defaultRole = defaultRole;
    this.userGuid = userGuid;
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
      var token = new UsernamePasswordAuthenticationToken(name, "N/A", authorities);
      // The GUID a real principal carries as `custom:idp_user_id`. In `details`, not in the
      // principal name — the class javadoc explains why the name cannot hold it.
      token.setDetails(userGuid);
      SecurityContextHolder.getContext().setAuthentication(token);
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
