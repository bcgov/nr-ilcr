package ca.bc.gov.nrs.ilcr.schedule2;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule2.api.Schedule2Api;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 2 endpoints. Authorizes by naming the {@code VIEW_SCHEDULE} action (AD-7), delegates all
 * mill/year validation to {@link MillContextService} (AD-4), and never touches repositories directly
 * (AD-1 layering). The read-only {@code editable} flag is derived from the caller's
 * {@code EDIT_SCHEDULE} permission, computed server-side (AD-5).
 *
 * <p>Read-only slice: no write path (PUT/DELETE/check-status deferred to Story 3.2). A valid, active
 * mill/year with no saved Schedule 2 returns a 200 empty editable document — the single deliberate
 * divergence from Schedule 1's read (which 404s on a missing summary), so this uses
 * {@link MillContextService#validateMillYearActive} instead of {@code validateScheduleViewable}.
 */
@RestController
public class Schedule2Controller implements Schedule2Api {

  private final MillContextService millContextService;
  private final Schedule2Service schedule2Service;
  private final SchedulePermissions permissions;

  public Schedule2Controller(
      MillContextService millContextService,
      Schedule2Service schedule2Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule2Service = schedule2Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule2Response> getSchedule2(
      long millId, int year, Authentication authentication) {
    // No summary-required 404 for Schedule 2 (AC4/AC6) — only mill/year existence + active checks.
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule2Service.getSchedule2(millId, year, callerMayEdit));
  }
}
