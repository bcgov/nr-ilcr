package ca.bc.gov.nrs.ilcr.millmaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.security.Action;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Pins WHICH action gates the mill lifecycle endpoints.
 *
 * <p>This exists because no role-based test can prove it. {@code MillMaintenanceIT} shows that an
 * admin passes and a submitter is refused — but {@link SchedulePermissions} grants ADMIN
 * <em>every</em> action and SUBMITTER only the two schedule ones, so swapping a controller constant
 * to {@code MAINTAIN_USERS} or {@code OPEN_REPORTING_YEAR} leaves that IT green while silently
 * moving the whole Mills page behind a different capability. The endpoint set and its authorizing
 * action are a contract (AD-7: "authorization names the capability"), and this is the assertion
 * that holds them together.
 *
 * <p>The sibling {@code MillAssociationControllerAuthorizationTest} already does this for the four
 * association endpoints on the same page. These eight were the half still unpinned.
 */
@DisplayName("Mill maintenance endpoints are gated on MAINTAIN_MILLS")
class MillMaintenanceControllerAuthorizationTest {

  private static final List<String> GATED_METHODS =
      List.of(
          "search",
          "findImportable",
          "findById",
          "contactOptions",
          "importMill",
          "activate",
          "deactivate",
          "saveContacts");

  @Test
  @DisplayName("Every endpoint declares @PreAuthorize on MAINTAIN_MILLS — no other action")
  void everyEndpointIsGatedOnMaintainMills() {
    for (String name : GATED_METHODS) {
      Method method = declaredMethod(name);
      PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

      assertThat(preAuthorize)
          .as("%s must carry @PreAuthorize — an unannotated endpoint is an open admin API", name)
          .isNotNull();
      assertThat(preAuthorize.value())
          .as("%s must be gated on MAINTAIN_MILLS, the Mills page's own action", name)
          .isEqualTo("@permissions.hasPermission(authentication, 'MAINTAIN_MILLS')");
    }
  }

  @Test
  @DisplayName("No endpoint is left unguarded — the gated list is the whole public surface")
  void everyPublicEndpointIsAccountedFor() {
    // Guards the list itself: a ninth endpoint added without a @PreAuthorize would otherwise pass
    // the loop above simply by not being named in it.
    List<String> unguarded =
        Arrays.stream(MillMaintenanceController.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(Override.class) != null)
            .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
            .map(Method::getName)
            .toList();

    assertThat(unguarded).isEmpty();
    assertThat(
            Arrays.stream(MillMaintenanceController.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PreAuthorize.class) != null)
                .map(Method::getName)
                .distinct()
                .toList())
        .containsExactlyInAnyOrderElementsOf(GATED_METHODS);
  }

  @Test
  @DisplayName("MAINTAIN_MILLS itself stays ADMIN-only in the central role map")
  void maintainMillsIsAdminOnly() {
    SchedulePermissions permissions = new SchedulePermissions();

    assertThat(permissions.grants(Role.ADMIN, Action.MAINTAIN_MILLS)).isTrue();
    assertThat(permissions.grants(Role.SUBMITTER, Action.MAINTAIN_MILLS)).isFalse();
  }

  private static Method declaredMethod(String name) {
    return Arrays.stream(MillMaintenanceController.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no such endpoint method: " + name));
  }
}
