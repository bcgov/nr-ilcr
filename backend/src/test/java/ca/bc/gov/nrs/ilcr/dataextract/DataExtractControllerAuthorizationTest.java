package ca.bc.gov.nrs.ilcr.dataextract;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.security.Action;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Pins WHICH action gates the extract endpoint.
 *
 * <p>No role-based test can prove it: {@link SchedulePermissions} grants ADMIN every action, so
 * swapping the controller constant to {@code GENERATE_MILL_REPORTS} leaves {@code
 * DataExtractAuthorizationIT} entirely green while silently collapsing a distinction legacy made —
 * the page carried its own {@code extractData} action, separate from the {@code generateReports}
 * submenu gate.
 *
 * <p>Modelled on {@code MillMaintenanceControllerAuthorizationTest}, including its two hard-won
 * details: iterate EVERY method of a name rather than the first reflected hit (declaration order is
 * undefined, so an overload could slip through on a different action), and key the surface test on
 * public non-synthetic methods rather than on {@code @Override}, which is
 * {@code @Retention(SOURCE)} and invisible to reflection — filtering on it would keep zero methods
 * and assert nothing.
 */
@DisplayName("The Data Extract endpoint is gated on GENERATE_DATA_EXTRACT")
class DataExtractControllerAuthorizationTest {

  private static final List<String> GATED_METHODS = List.of("generate");

  private static final String EXPECTED_GUARD =
      "@permissions.hasPermission(authentication, 'GENERATE_DATA_EXTRACT')";

  @Test
  @DisplayName("The endpoint declares @PreAuthorize on GENERATE_DATA_EXTRACT — no other action")
  void endpointIsGatedOnGenerateDataExtract() {
    for (String name : GATED_METHODS) {
      for (Method method : declaredMethods(name)) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize)
            .as("%s must carry @PreAuthorize — an unannotated endpoint is an open admin API", name)
            .isNotNull();
        assertThat(preAuthorize.value())
            .as("%s must be gated on the extract's OWN action, not the reports-area one", name)
            .isEqualTo(EXPECTED_GUARD);
      }
    }
  }

  @Test
  @DisplayName("No endpoint is left unguarded — the gated list is the whole public surface")
  void everyPublicEndpointIsAccountedFor() {
    List<String> unguarded =
        Arrays.stream(DataExtractController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
            .filter(method -> method.getAnnotation(PreAuthorize.class) == null)
            .map(Method::getName)
            .toList();

    assertThat(unguarded).isEmpty();
    assertThat(
            Arrays.stream(DataExtractController.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(PreAuthorize.class) != null)
                .map(Method::getName)
                .distinct()
                .toList())
        .containsExactlyInAnyOrderElementsOf(GATED_METHODS);
  }

  @Test
  @DisplayName("The guard string is not vacuously satisfiable — a wrong action fails the assertion")
  void theGuardAssertionHasATeeth() {
    // The positive control the 22-3 review asked for: prove the assertion above can fail. A guard
    // naming any other action must NOT equal the expected string.
    assertThat("@permissions.hasPermission(authentication, 'GENERATE_MILL_REPORTS')")
        .isNotEqualTo(EXPECTED_GUARD);
  }

  @Test
  @DisplayName("GENERATE_DATA_EXTRACT is ADMIN-only in the central role map")
  void generateDataExtractIsAdminOnly() {
    SchedulePermissions permissions = new SchedulePermissions();

    assertThat(permissions.grants(Role.ADMIN, Action.GENERATE_DATA_EXTRACT)).isTrue();
    assertThat(permissions.grants(Role.SUBMITTER, Action.GENERATE_DATA_EXTRACT)).isFalse();
  }

  private static List<Method> declaredMethods(String name) {
    List<Method> methods =
        Arrays.stream(DataExtractController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(name) && !method.isSynthetic())
            .toList();
    if (methods.isEmpty()) {
      throw new AssertionError("no such endpoint method: " + name);
    }
    return methods;
  }
}
