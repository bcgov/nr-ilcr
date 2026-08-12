package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Asserts which ACTION each Schedule 7B endpoint authorizes on, by reading its {@code @PreAuthorize}
 * annotation.
 *
 * <p>This is not belt-and-braces — it closes a hole {@link Schedule7bAuthorizationIT} cannot. Both
 * production roles ({@code ILCR_ADMIN}, {@code ILCR_SUBMITTER}) hold BOTH {@code VIEW_SCHEDULE} and
 * {@code EDIT_SCHEDULE}, and every negative case in that IT uses a caller with no ILCR group at all.
 * So swapping a write's action to {@code VIEW_SCHEDULE} — or to a misspelled action name, which
 * {@code SchedulePermissions.hasPermission} silently DENIES rather than failing loudly — passes every
 * one of those tests. Until the role×status matrix lands (AR14 / Story 16.1) and the two roles diverge,
 * the annotation values are the only place the read/write distinction is actually recorded, so they are
 * asserted directly. Runs under surefire (no container), so it also counts toward measured coverage.
 */
@DisplayName("Schedule7bController — @PreAuthorize action contract per endpoint")
class Schedule7bAuthorizationContractTest {

  private static final String VIEW = "@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')";
  private static final String EDIT = "@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')";

  /** Endpoint method name → the exact {@code @PreAuthorize} expression it must carry. */
  private static final Map<String, String> EXPECTED = Map.of(
      "getSchedule7b", VIEW,
      "checkStatus", VIEW,
      "addCulvert", EDIT,
      "updateCulvert", EDIT,
      "saveAllCulverts", EDIT,
      "deleteCulvert", EDIT);

  @Test
  @DisplayName("Reads authorize VIEW_SCHEDULE, writes authorize EDIT_SCHEDULE — no endpoint unguarded")
  void everyEndpointNamesTheRightAction() {
    for (Map.Entry<String, String> expected : EXPECTED.entrySet()) {
      Method endpoint = endpoint(expected.getKey());
      PreAuthorize annotation = endpoint.getAnnotation(PreAuthorize.class);

      assertThat(annotation)
          .as("%s carries @PreAuthorize", expected.getKey())
          .isNotNull();
      assertThat(annotation.value())
          .as("%s authorizes the right action", expected.getKey())
          .isEqualTo(expected.getValue());
    }
  }

  @Test
  @DisplayName("Every public endpoint on the controller is covered by this contract")
  void noEndpointEscapesTheContract() {
    // Guards the guard: a new endpoint added without an entry above would otherwise be unverified.
    var endpoints = java.util.Arrays.stream(Schedule7bController.class.getDeclaredMethods())
        .filter(m -> m.getAnnotation(Override.class) != null
            || m.getAnnotation(PreAuthorize.class) != null)
        .map(Method::getName)
        .filter(name -> !name.equals("message"))
        .collect(java.util.stream.Collectors.toSet());

    assertThat(endpoints).isEqualTo(EXPECTED.keySet());
  }

  private static Method endpoint(String name) {
    return java.util.Arrays.stream(Schedule7bController.class.getDeclaredMethods())
        .filter(m -> m.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no Schedule7bController." + name));
  }
}
