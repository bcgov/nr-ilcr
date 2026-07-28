package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule11.api.Schedule11Api;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 11 endpoints. Authorizes by naming the {@code VIEW_SCHEDULE} action (AD-7), delegates
 * ALL mill/year validation to {@link MillContextService} as its first line (AD-4 — the list-
 * schedule guard: no summary check), and never touches repositories directly (AD-1 layering). The
 * read-only {@code editable} flag combines the caller's {@code EDIT_SCHEDULE} permission (from the
 * shared {@link SchedulePermissions} component — never inlined, AC7) with the silviculture track's
 * Draft state in the service.
 */
@RestController
public class Schedule11Controller implements Schedule11Api {

  private final MillContextService millContextService;
  private final Schedule11Service schedule11Service;
  private final SchedulePermissions permissions;

  public Schedule11Controller(
      MillContextService millContextService,
      Schedule11Service schedule11Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule11Service = schedule11Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule11Response> getSchedule11(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearViewable(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule11Service.getSchedule11(context.millId(), context.year(), callerMayEdit));
  }
}
