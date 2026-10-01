package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps the FAM {@code cognito:groups} claim to ILCR role authorities (AD-7). Spring's default JWT
 * converter yields only {@code SCOPE_*} authorities; this wires the group &rarr; role mapping so
 * {@code @PreAuthorize} action checks work with a real FAM token.
 *
 * <p>Matching is CSP {@code groupMatchesRole} style but ILCR-app-scoped: a group grants a role when
 * it equals the role name (e.g. {@code ILCR_ADMIN}) or ends with {@code _ILCR_ADMIN} (e.g. {@code
 * NRS_ILCR_ADMIN}). A foreign group like {@code SOME_OTHER_APP_ADMIN} does NOT match — the bare
 * {@code _ADMIN} suffix is deliberately not accepted, to keep authorization app-scoped.
 */
@Component
public class CognitoGroupsJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String GROUPS_CLAIM = "cognito:groups";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    List<String> groups = extractGroups(jwt);
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (Role role : Role.values()) {
      if (groups.stream().anyMatch(group -> matchesRole(group, role))) {
        authorities.add(new SimpleGrantedAuthority(role.name()));
      }
    }
    return new JwtAuthenticationToken(jwt, authorities, auditUsername(jwt));
  }

  /**
   * The principal name — i.e. {@code authentication.getName()}, which every admin write stamps into
   * the {@code ENTRY_/UPDATE_USERID VARCHAR2(30)} audit columns. FAM's {@code custom:idp_username}
   * is sized for that (per the identity contract); without this the token name defaults to the
   * {@code sub} (a ~36-char Cognito UUID), which overflows the column (ORA-12899) on every write in
   * production. Falls back to the {@code sub} only when the claim is absent (defensive; never
   * expected with FAM).
   */
  private static String auditUsername(Jwt jwt) {
    String username = jwt.getClaimAsString("custom:idp_username");
    return (username != null && !username.isBlank()) ? username : jwt.getSubject();
  }

  private static List<String> extractGroups(Jwt jwt) {
    Object claim = jwt.getClaim(GROUPS_CLAIM);
    if (claim instanceof List<?> list) {
      return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    // Some identity providers emit a single-valued groups claim as a plain String rather than a
    // one-element array; handle it so a lone group isn't silently dropped (→ unexpected 403).
    if (claim instanceof String single) {
      return List.of(single);
    }
    return List.of();
  }

  private static boolean matchesRole(String group, Role role) {
    if (group == null) {
      return false;
    }
    String upper = group.toUpperCase(Locale.ROOT);
    String roleName = role.getRoleName().toUpperCase(Locale.ROOT); // e.g. ILCR_ADMIN
    return upper.equals(roleName) || upper.endsWith("_" + roleName);
  }
}
