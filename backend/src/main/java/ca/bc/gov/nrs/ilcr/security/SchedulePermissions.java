package ca.bc.gov.nrs.ilcr.security;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Central role &rarr; action map and the {@code @PreAuthorize} permission checker (AD-7).
 *
 * <p>Exposed as the Spring bean {@code permissions} so controllers authorize by naming an action:
 * {@code @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")}. No controller
 * references a role literal or a per-page boolean flag. This mirrors CSP's {@code PermissionService}
 * but ILCR's error contract and role set are its own.
 */
@Component("permissions")
public class SchedulePermissions {

  private static final Map<Role, Set<Action>> ROLE_ACTIONS = new EnumMap<>(Role.class);

  static {
    // Both FAM production roles may view and edit schedules; the two tracks / Draft-gate are
    // enforced separately in the domain services (AD-9), not by widening/narrowing this map.
    ROLE_ACTIONS.put(Role.ADMIN, EnumSet.of(Action.VIEW_SCHEDULE, Action.EDIT_SCHEDULE));
    ROLE_ACTIONS.put(Role.SUBMITTER, EnumSet.of(Action.VIEW_SCHEDULE, Action.EDIT_SCHEDULE));
  }

  /**
   * Whether a role grants an action, per the central map.
   *
   * @param role the role (may be {@code null} for an unknown/foreign authority)
   * @param action the action being checked
   * @return true if the role grants the action
   */
  public boolean grants(Role role, Action action) {
    return role != null
        && ROLE_ACTIONS.getOrDefault(role, EnumSet.noneOf(Action.class)).contains(action);
  }

  /**
   * {@code @PreAuthorize} entry point: does the authenticated principal hold any role that grants
   * the named action? Business logic never branches on the security toggle — the same check runs in
   * dev (mock principal) and prod (real FAM JWT).
   *
   * @param authentication the current authentication (may be {@code null})
   * @param action the action name (must match an {@link Action} constant, else denied)
   * @return true if permitted, false otherwise
   */
  public boolean hasPermission(Authentication authentication, String action) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    Action requested;
    try {
      requested = Action.valueOf(action);
    } catch (IllegalArgumentException ex) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(Role::fromValue)
        .anyMatch(role -> grants(role, requested));
  }
}
