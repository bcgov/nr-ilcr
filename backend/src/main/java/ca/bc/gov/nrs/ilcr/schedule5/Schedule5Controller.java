package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule5.api.Schedule5Api;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 5 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * — delegates ALL mill/year validation to {@link MillContextService} as its first line (AD-4), and
 * never touches repositories directly (AD-1). The server is the sole authority for {@code editable}
 * (AD-9/S19): it is derived here from the caller's {@code EDIT_SCHEDULE} action and, in the
 * service, the Draft track status; the frontend never computes it.
 *
 * <p>{@code validateMillYearActive} is the correct guard rather than
 * {@code validateScheduleViewable(millId, year, "5")}: the latter requires a category-{@code '5'}
 * {@code ILCR_REPORT_SUMMARY} row, and Schedule 5 has none in delivery (Story 7.1 Task 1 gate (ii)
 * — zero rows; summaries exist only for categories 1/2/3), so it would 404 every request.
 *
 * <p>{@code Authentication} is a method parameter, never {@code SecurityContextHolder}, which is
 * empty on Boot-4 MockMvc paths.
 */
@RestController
public class Schedule5Controller implements Schedule5Api {

  private final MillContextService millContextService;
  private final Schedule5Service schedule5Service;
  private final SchedulePermissions permissions;

  /** Wires the mill/year guard, the derivation service, and permissions. */
  public Schedule5Controller(
      MillContextService millContextService,
      Schedule5Service schedule5Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule5Service = schedule5Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule5Response> getSchedule5(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule5Service.getSchedule5(context.millId(), context.year(), callerMayEdit));
  }
}
