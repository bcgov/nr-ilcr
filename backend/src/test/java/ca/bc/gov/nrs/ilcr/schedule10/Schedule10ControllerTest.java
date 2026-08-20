package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link Schedule10Controller}'s one non-delegating decision: resolving the caller's
 * {@code EDIT_SCHEDULE} action into the {@code callerMayEdit} flag that {@link Schedule10Service}
 * combines with the Draft track status (AD-9 — the server is the sole authority for
 * {@code editable}).
 *
 * <p><strong>This layer cannot be covered by the integration tests.</strong>
 * {@code SchedulePermissions} grants {@code EDIT_SCHEDULE} to BOTH production groups, so no
 * principal reachable from {@code Schedule10AuthorizationIT} can produce {@code callerMayEdit =
 * false} — every caller that gets past {@code VIEW_SCHEDULE} also holds {@code EDIT_SCHEDULE}. And
 * {@code Schedule10ServiceTest} is handed the boolean directly, mocking the lookup away entirely.
 * Without this class, hardcoding {@code callerMayEdit = true}, or asking for {@code VIEW_SCHEDULE}
 * instead, passes the entire suite (code review 2026-08-17).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule10Controller — the editable permission lookup (AD-9)")
class Schedule10ControllerTest {

  private static final String MILL_PARAM = "710";
  private static final String YEAR_PARAM = "2021";
  private static final long MILL = 710L;
  private static final int YEAR = 2021;

  @Mock
  private MillContextService millContextService;

  @Mock
  private Schedule10Service schedule10Service;

  @Mock
  private SchedulePermissions permissions;

  @Mock
  private Authentication authentication;

  @Mock
  private MessageSource messageSource;

  private Schedule10Controller controller;

  @BeforeEach
  void setUp() {
    controller = new Schedule10Controller(
        millContextService, schedule10Service, permissions, messageSource);
    when(millContextService.validateMillYearActive(MILL_PARAM, YEAR_PARAM))
        .thenReturn(new MillYearContext(MILL, YEAR));
  }

  private static Schedule10Response document(boolean editable) {
    return new Schedule10Response(MILL, YEAR, "D", editable, List.of(), null, null);
  }

  @Test
  @DisplayName("asks for the EDIT_SCHEDULE action BY NAME, not VIEW_SCHEDULE")
  void asksForTheEditScheduleActionByName() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule10Service.getSchedule10(MILL, YEAR, true)).thenReturn(document(true));

    controller.getSchedule10(MILL_PARAM, YEAR_PARAM, authentication);

    // Pinning the action string matters: asking for VIEW_SCHEDULE here would silently grant edit
    // authority to every caller who can read, and no other test would notice.
    verify(permissions).hasPermission(authentication, "EDIT_SCHEDULE");
  }

  @Test
  @DisplayName("passes callerMayEdit=false through when the caller lacks EDIT_SCHEDULE")
  void deniedPermissionIsPassedThroughAsFalse() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    when(schedule10Service.getSchedule10(MILL, YEAR, false)).thenReturn(document(false));

    var response = controller.getSchedule10(MILL_PARAM, YEAR_PARAM, authentication);

    // The false must actually reach the service — a hardcoded `true` would fail here.
    verify(schedule10Service).getSchedule10(MILL, YEAR, false);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().editable()).isFalse();
  }

  @Test
  @DisplayName("passes callerMayEdit=true through when the caller holds EDIT_SCHEDULE")
  void grantedPermissionIsPassedThroughAsTrue() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule10Service.getSchedule10(MILL, YEAR, true)).thenReturn(document(true));

    var response = controller.getSchedule10(MILL_PARAM, YEAR_PARAM, authentication);

    verify(schedule10Service).getSchedule10(MILL, YEAR, true);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().editable()).isTrue();
  }

  @Test
  @DisplayName("guards mill/year FIRST, delegating to millcontext with the raw params (AD-4)")
  void validatesMillYearContextBeforeReading() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    when(schedule10Service.getSchedule10(MILL, YEAR, true)).thenReturn(document(true));

    controller.getSchedule10(MILL_PARAM, YEAR_PARAM, authentication);

    // The raw Strings go to millcontext, which owns parsing and the verbatim ERR-001 message;
    // the service only ever sees the validated, typed context.
    verify(millContextService).validateMillYearActive(MILL_PARAM, YEAR_PARAM);
    verify(schedule10Service).getSchedule10(MILL, YEAR, true);
  }
}
