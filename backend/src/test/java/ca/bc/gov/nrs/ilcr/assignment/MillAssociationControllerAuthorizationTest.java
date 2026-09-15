package ca.bc.gov.nrs.ilcr.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.security.Action;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Pins WHICH action gates the mill-record association endpoints.
 *
 * <p>This exists because no role-based test can prove it. {@code
 * MillAssociationIT.submitterIsDeniedEveryOperation} shows that an admin passes and a submitter is
 * refused — but {@link SchedulePermissions} grants ADMIN <em>every</em> action and SUBMITTER only
 * the two schedule ones, so swapping the controller's constant to {@code MAINTAIN_USERS} or {@code
 * OPEN_REPORTING_YEAR} leaves that test green while silently moving the whole Mills panel behind a
 * different capability. The endpoint set and its authorizing action are a contract (AD-7:
 * "authorization names the capability"), and this is the assertion that holds them together.
 */
@DisplayName("Mill-record association endpoints are gated on MAINTAIN_MILLS")
class MillAssociationControllerAuthorizationTest {

  private static final List<String> GATED_METHODS =
      List.of("list", "add", "activate", "deactivate");

  @Test
  @DisplayName("Every endpoint declares @PreAuthorize on MAINTAIN_MILLS — no other action")
  void everyEndpointIsGatedOnMaintainMills() {
    for (String name : GATED_METHODS) {
      // EVERY method carrying the name, not the first reflected hit: getDeclaredMethods() has no
      // defined order, so with an overload present findFirst() could verify one signature while
      // the other slips through gated on a different action (PR #459 review, the sibling
      // MillMaintenance test's finding — the helper here had the same shape).
      for (Method method : declaredMethods(name)) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize)
            .as("%s must carry @PreAuthorize — an unannotated endpoint is an open admin API", name)
            .isNotNull();
        assertThat(preAuthorize.value())
            .as("%s must be gated on MAINTAIN_MILLS, the Mills page's own action", name)
            .isEqualTo("@permissions.hasPermission(authentication, 'MAINTAIN_MILLS')");
      }
    }
  }

  @Test
  @DisplayName("MAINTAIN_MILLS itself stays ADMIN-only in the central role map")
  void maintainMillsIsAdminOnly() {
    SchedulePermissions permissions = new SchedulePermissions();

    assertThat(permissions.grants(Role.ADMIN, Action.MAINTAIN_MILLS)).isTrue();
    assertThat(permissions.grants(Role.SUBMITTER, Action.MAINTAIN_MILLS)).isFalse();
  }

  private static List<Method> declaredMethods(String name) {
    List<Method> methods =
        java.util.Arrays.stream(MillAssociationController.class.getDeclaredMethods())
            // Bridge/synthetic methods are compiler artifacts shadowing a real method already in
            // the stream.
            .filter(m -> m.getName().equals(name) && !m.isSynthetic())
            .toList();
    if (methods.isEmpty()) {
      throw new AssertionError("no such endpoint method: " + name);
    }
    return methods;
  }
}
