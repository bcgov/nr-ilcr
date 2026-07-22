package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule8.api.Schedule8Api;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 8 endpoints. Authorizes by naming the schedule action (AD-7), delegates all mill/year
 * validation to {@link MillContextService} (AD-4), and never touches repositories directly (AD-1
 * layering). The read-only {@code editable} flag is derived from the caller's {@code EDIT_SCHEDULE}
 * permission, computed server-side (AD-5).
 *
 * <p>The read (GET) never 404s on "no pages" — an opened active mill/year with no category-{@code '8'}
 * {@code TREE_TO_TRUCK_REPORT} rows returns a 200 empty list. It uses
 * {@link MillContextService#validateMillYearActive} (mill status only — no summary required), correct
 * for Schedule 8 (it has no {@code ILCR_REPORT_SUMMARY} row of its own).
 */
@RestController
@RequiredArgsConstructor
public class Schedule8Controller implements Schedule8Api {

  private final MillContextService millContextService;
  private final Schedule8Service schedule8Service;
  private final SchedulePermissions permissions;

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule8Response> getSchedule8(
      long millId, int year, Authentication authentication) {
    // No no-pages 404 for Schedule 8 — only mill/year existence + active checks (404/409).
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule8Service.getSchedule8(millId, year, callerMayEdit));
  }
}
