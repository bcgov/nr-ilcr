package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit test for the action-based permission model (AD-7). No Spring, no DB — mirrors CSP's
 * PermissionServiceTest. The check names an ACTION, never a role literal.
 */
class SchedulePermissionTest {

  private final SchedulePermissions permissions = new SchedulePermissions();

  private Authentication auth(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
        "u", "p", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
  }

  @Test
  void submitter_grantsViewSchedule() {
    assertTrue(permissions.grants(Role.SUBMITTER, Action.VIEW_SCHEDULE));
  }

  @Test
  void admin_grantsViewSchedule() {
    assertTrue(permissions.grants(Role.ADMIN, Action.VIEW_SCHEDULE));
  }

  @Test
  void nullRole_deniedViewSchedule() {
    assertFalse(permissions.grants(null, Action.VIEW_SCHEDULE));
  }

  @Test
  void hasPermission_submitterAuthority_view() {
    assertTrue(permissions.hasPermission(auth("SUBMITTER"), "VIEW_SCHEDULE"));
  }

  @Test
  void hasPermission_ilcrPrefixedAuthority_view() {
    assertTrue(permissions.hasPermission(auth("ILCR_ADMIN"), "VIEW_SCHEDULE"));
  }

  @Test
  void hasPermission_foreignScopeAuthority_denied() {
    assertFalse(permissions.hasPermission(auth("SCOPE_read"), "VIEW_SCHEDULE"));
  }

  @Test
  void hasPermission_unknownAction_denied() {
    assertFalse(permissions.hasPermission(auth("ADMIN"), "NOT_AN_ACTION"));
  }

  @Test
  void hasPermission_nullAuthentication_denied() {
    assertFalse(permissions.hasPermission(null, "VIEW_SCHEDULE"));
  }
}
