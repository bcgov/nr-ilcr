package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule4.api.Schedule4Api;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 4 endpoints. Authorizes by naming the schedule action (AD-7), delegates all mill/year
 * validation to {@link MillContextService} (AD-4), and never touches repositories directly (AD-1
 * layering). The read-only {@code editable} flag is derived from the caller's {@code EDIT_SCHEDULE}
 * permission, computed server-side (AD-5).
 *
 * <p>The read (GET) never 404s on "no locations" — an opened active mill/year with no
 * {@code TRANSPORTATION_REPORT} rows returns a 200 empty list. It uses
 * {@link MillContextService#validateMillYearActive} (mill status only — no summary required), which
 * is correct for Schedule 4 (Schedule 4 has no {@code ILCR_REPORT_SUMMARY} row of its own).
 */
@RestController
@RequiredArgsConstructor
public class Schedule4Controller implements Schedule4Api {

  private final MillContextService millContextService;
  private final Schedule4Service schedule4Service;
  private final SchedulePermissions permissions;

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule4Response> getSchedule4(
      long millId, int year, Authentication authentication) {
    // No no-locations 404 for Schedule 4 — only mill/year existence + active checks (404/409).
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule4Service.getSchedule4(millId, year, callerMayEdit));
  }
}
