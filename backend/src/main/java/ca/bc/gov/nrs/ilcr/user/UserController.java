package ca.bc.gov.nrs.ilcr.user;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.user.api.UserApi;
import ca.bc.gov.nrs.ilcr.user.dto.CurrentUser;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@code GET /api/v1/me}. Builds the current user from the validated token when security is
 * on, and from the {@code MockPrincipalFilter} principal when it is off (AD-7) — the SPA gets the
 * same shape either way. No {@code @PreAuthorize}: any authenticated caller may read their own
 * identity, and with security off every request already carries the mock principal.
 */
@RestController
public class UserController implements UserApi {

  /**
   * Shown for the security-off local principal, which carries no name claims. A fixed, obviously
   * non-production value so a deployed environment still running the mock is easy to spot.
   */
  static final String MOCK_DISPLAY_NAME = "Local Development User";

  @Override
  public ResponseEntity<CurrentUser> me() {
    return ResponseEntity.ok(toCurrentUser(SecurityContextHolder.getContext().getAuthentication()));
  }

  private static CurrentUser toCurrentUser(Authentication authentication) {
    List<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(Role::fromValue)
        .filter(Objects::nonNull)
        .map(Role::getRoleName)
        .distinct()
        .toList();

    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String userGuid = StringUtils.firstNonBlank(
          JwtPrincipalUtil.getIdpUserId(jwt), jwt.getSubject());
      String displayName = StringUtils.firstNonBlank(
          JwtPrincipalUtil.getDisplayName(jwt), JwtPrincipalUtil.getName(jwt), userGuid);
      return new CurrentUser(
          userGuid,
          displayName,
          StringUtils.trimToNull(JwtPrincipalUtil.getEmail(jwt)),
          StringUtils.trimToNull(JwtPrincipalUtil.getProvider(jwt)),
          roles);
    }

    // Security-off mock principal (name e.g. "dev-submitter") — no token claims to read.
    return new CurrentUser(authentication.getName(), MOCK_DISPLAY_NAME, null, null, roles);
  }
}
