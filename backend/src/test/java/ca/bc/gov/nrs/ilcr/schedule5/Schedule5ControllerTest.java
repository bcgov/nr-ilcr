package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
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
 * Unit tests for {@link Schedule5Controller}'s one non-delegating decision: resolving the caller's
 * {@code EDIT_SCHEDULE} action into the {@code callerMayEdit} flag the service combines with the
 * Draft track status (AD-9 / S19 — the server is the sole authority for {@code editable}).
 *
 * <p><strong>Why this cannot be an integration test.</strong> {@code SchedulePermissions} grants
 * BOTH {@code VIEW_SCHEDULE} and {@code EDIT_SCHEDULE} to BOTH shipped roles, so no JWT can produce
 * a caller who may view but not edit: once {@code @PreAuthorize} passes, the lookup always returns
 * true and the conjunction in the service degenerates to the Draft check alone. {@link
 * Schedule5DocumentIT} runs with security off, and {@link Schedule5AuthorizationIT} only asserts
 * status codes. The consequence was that replacing the controller's lookup with a literal {@code
 * true}, or misspelling the action string — which makes {@code SchedulePermissions.hasPermission}
 * return false for everyone and silently strips edit rights — left the entire suite green. Mocking
 * the collaborator is the only way to observe it.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Controller — the editable permission lookup (AD-9)")
class Schedule5ControllerTest {

  private static final String MILL_PARAM = "514";
  private static final String YEAR_PARAM = "2021";
  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;

  @Mock private Schedule5Service schedule5Service;

  @Mock private SchedulePermissions permissions;

  @Mock private Authentication authentication;

  @Mock private MessageSource messageSource;

  private Schedule5Controller controller;

  @BeforeEach
  void setUp() {
    controller =
        new Schedule5Controller(
            millContextService,
            schedule5Service,
            permissions,
            messageSource,
            new Schedule5CheckStatusResolver(schedule5Service, messageSource));
    when(millContextService.validateMillYearActive(MILL_PARAM, YEAR_PARAM))
        .thenReturn(new MillYearContext(MILL, YEAR));
  }

  private void serviceReturns(boolean editable) {
    when(schedule5Service.getSchedule5(anyLong(), anyInt(), anyBoolean()))
        .thenReturn(new Schedule5Response(MILL, YEAR, "D", editable, List.of(), null));
  }

  @Test
  @DisplayName("a caller WITHOUT EDIT_SCHEDULE is passed callerMayEdit=false")
  void withoutEditPermission_passesFalse() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    serviceReturns(false);

    controller.getSchedule5(MILL_PARAM, YEAR_PARAM, authentication);

    // The mutation this kills: `boolean callerMayEdit = true;`.
    verify(schedule5Service).getSchedule5(MILL, YEAR, false);
  }

  @Test
  @DisplayName("a caller WITH EDIT_SCHEDULE is passed callerMayEdit=true")
  void withEditPermission_passesTrue() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    controller.getSchedule5(MILL_PARAM, YEAR_PARAM, authentication);

    verify(schedule5Service).getSchedule5(MILL, YEAR, true);
  }

  @Test
  @DisplayName("the action asked for is exactly EDIT_SCHEDULE, not VIEW_SCHEDULE or a typo")
  void asksForTheEditScheduleActionByName() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    controller.getSchedule5(MILL_PARAM, YEAR_PARAM, authentication);

    // An unrecognized action name returns false from SchedulePermissions for every caller, which
    // would strip edit rights app-wide with no other signal.
    verify(permissions).hasPermission(authentication, "EDIT_SCHEDULE");
  }

  @Test
  @DisplayName("the mill/year guard runs before anything else and its parsed context is used")
  void guardRunsFirstAndItsContextIsForwarded() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    var response = controller.getSchedule5(MILL_PARAM, YEAR_PARAM, authentication);

    verify(millContextService).validateMillYearActive(MILL_PARAM, YEAR_PARAM);
    // The service receives the guard's PARSED longs/ints, never the raw request strings.
    verify(schedule5Service).getSchedule5(eq(MILL), eq(YEAR), anyBoolean());
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }
}
