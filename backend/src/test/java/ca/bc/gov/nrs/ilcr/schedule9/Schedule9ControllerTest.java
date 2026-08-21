package ca.bc.gov.nrs.ilcr.schedule9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
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
 * Unit tests for {@link Schedule9Controller}'s one non-delegating decision: resolving the caller's
 * {@code EDIT_SCHEDULE} action into the {@code callerMayEdit} flag the service combines with the
 * Draft track status (AD-9 / S19 — the server is the sole authority for {@code editable}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule9Controller — the editable permission lookup (AD-9)")
class Schedule9ControllerTest {

  private static final String MILL_PARAM = "514";
  private static final String YEAR_PARAM = "2021";
  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private MillContextService millContextService;

  @Mock private Schedule9Service schedule9Service;

  @Mock private SchedulePermissions permissions;

  @Mock private Authentication authentication;

  @Mock private MessageSource messageSource;

  private Schedule9Controller controller;

  @BeforeEach
  void setUp() {
    controller =
        new Schedule9Controller(millContextService, schedule9Service, permissions, messageSource);
    when(millContextService.validateMillYearActive(MILL_PARAM, YEAR_PARAM))
        .thenReturn(new MillYearContext(MILL, YEAR));
  }

  private void serviceReturns(boolean editable) {
    when(schedule9Service.getSchedule9(anyLong(), anyInt(), anyBoolean()))
        .thenReturn(new Schedule9Response(MILL, YEAR, "D", editable, List.of(), null, null));
  }

  @Test
  @DisplayName("a caller WITHOUT EDIT_SCHEDULE is passed callerMayEdit=false")
  void withoutEditPermission_passesFalse() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(false);
    serviceReturns(false);

    controller.getSchedule9(MILL_PARAM, YEAR_PARAM, authentication);

    verify(schedule9Service).getSchedule9(MILL, YEAR, false);
  }

  @Test
  @DisplayName("a caller WITH EDIT_SCHEDULE is passed callerMayEdit=true")
  void withEditPermission_passesTrue() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    controller.getSchedule9(MILL_PARAM, YEAR_PARAM, authentication);

    verify(schedule9Service).getSchedule9(MILL, YEAR, true);
  }

  @Test
  @DisplayName("the action asked for is exactly EDIT_SCHEDULE, not VIEW_SCHEDULE or a typo")
  void asksForTheEditScheduleActionByName() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    controller.getSchedule9(MILL_PARAM, YEAR_PARAM, authentication);

    verify(permissions).hasPermission(authentication, "EDIT_SCHEDULE");
  }

  @Test
  @DisplayName("the mill/year guard runs before anything else and its parsed context is used")
  void guardRunsFirstAndItsContextIsForwarded() {
    when(permissions.hasPermission(authentication, "EDIT_SCHEDULE")).thenReturn(true);
    serviceReturns(true);

    var response = controller.getSchedule9(MILL_PARAM, YEAR_PARAM, authentication);

    verify(millContextService).validateMillYearActive(MILL_PARAM, YEAR_PARAM);
    verify(schedule9Service).getSchedule9(eq(MILL), eq(YEAR), anyBoolean());
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }
}
