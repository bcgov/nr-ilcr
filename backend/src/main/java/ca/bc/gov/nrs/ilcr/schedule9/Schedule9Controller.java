package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule9.api.Schedule9Api;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 9 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * — delegates ALL mill/year validation to {@link MillContextService} as its first line (AD-4), and
 * never touches repositories directly (AD-1). The server is the sole authority for {@code editable}
 * (AD-9/S30): it is derived from the caller's {@code EDIT_SCHEDULE} action and, in the service, the
 * Draft track status; the frontend never computes it.
 *
 * <p>{@code validateMillYearActive} is the correct guard (not {@code validateScheduleViewable("9")}):
 * Schedule 9 is summary-less — {@code CONTRACTUAL_WORK_REPORT} is the master with no category-'9'
 * {@code ILCR_REPORT_SUMMARY} row — so the summary-requiring guard would 404 every request.
 *
 * <p>{@code Authentication} is a method parameter, never {@code SecurityContextHolder}, which is
 * empty on Boot-4 MockMvc paths.
 */
@RestController
public class Schedule9Controller implements Schedule9Api {

  private static final String EDIT_SCHEDULE = "EDIT_SCHEDULE";

  private final MillContextService millContextService;
  private final Schedule9Service schedule9Service;
  private final SchedulePermissions permissions;

  public Schedule9Controller(
      MillContextService millContextService,
      Schedule9Service schedule9Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule9Service = schedule9Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule9Response> getSchedule9(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, EDIT_SCHEDULE);
    return ResponseEntity.ok(
        schedule9Service.getSchedule9(context.millId(), context.year(), callerMayEdit));
  }
}
