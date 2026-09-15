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

  // EDIT_SCHEDULE (Story 16.1). Until the editability matrix landed, this action had NO assertion
  // here at all — the whole write side of the map was unpinned. It matters more than it looks:
  // ScheduleEditability.forCaller consults grants(role, EDIT_SCHEDULE) before it consults the
  // matrix, so dropping either entry below turns every caller's permitted-status set empty and
  // every write in the application into a 409 — a failure no *AuthorizationIT would name, because
  // 409 is exactly what those tests expect from the role/status pair they probe with.

  @Test
  void submitter_grantsEditSchedule() {
    assertTrue(permissions.grants(Role.SUBMITTER, Action.EDIT_SCHEDULE));
  }

  @Test
  void admin_grantsEditSchedule() {
    // Both shipped roles hold EDIT_SCHEDULE; which STATUS each may edit at is the matrix's job
    // (ScheduleEditabilityTest), never this map's — the map carries no status dimension (AD-9,
    // ratified by Story 5.4). An admin holding the action and still being refused at Draft is the
    // two working together, not a contradiction.
    assertTrue(permissions.grants(Role.ADMIN, Action.EDIT_SCHEDULE));
  }

  @Test
  void nullRole_deniedEditSchedule() {
    assertFalse(permissions.grants(null, Action.EDIT_SCHEDULE));
  }

  @Test
  void hasPermission_ilcrPrefixedAuthority_edit() {
    assertTrue(permissions.hasPermission(auth("ILCR_ADMIN"), "EDIT_SCHEDULE"));
    assertTrue(permissions.hasPermission(auth("ILCR_SUBMITTER"), "EDIT_SCHEDULE"));
  }

  @Test
  void hasPermission_foreignScopeAuthority_deniedEdit() {
    assertFalse(permissions.hasPermission(auth("SCOPE_write"), "EDIT_SCHEDULE"));
  }

  @Test
  void admin_grantsMaintainCodeTables() {
    // Story 24.3 / S13 — the code-table maintenance action is ADMIN-only.
    assertTrue(permissions.grants(Role.ADMIN, Action.MAINTAIN_CODE_TABLES));
  }

  @Test
  void submitter_deniedMaintainCodeTables() {
    // A submitter hitting the Table Maintenance APIs must be denied (403), not merely menu-hidden.
    assertFalse(permissions.grants(Role.SUBMITTER, Action.MAINTAIN_CODE_TABLES));
    assertFalse(permissions.hasPermission(auth("SUBMITTER"), "MAINTAIN_CODE_TABLES"));
  }

  @Test
  void hasPermission_adminAuthority_maintainCodeTables() {
    assertTrue(permissions.hasPermission(auth("ILCR_ADMIN"), "MAINTAIN_CODE_TABLES"));
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
