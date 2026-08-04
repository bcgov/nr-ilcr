package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule6.api.Schedule6Api;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 6 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the
 * read — delegates ALL mill/year validation to {@link MillContextService} as its first line (AD-4,
 * list-schedule guard: verbatim ERR-001 on a missing/blank/non-numeric param, ERR-002/003 for
 * closed/absent context), and never touches repositories directly (AD-1). The server is the sole
 * authority for {@code editable} (AD-9): the controller resolves whether the caller holds
 * {@code EDIT_SCHEDULE} and passes it to {@link Schedule6Service}, which ANDs it with the Draft
 * track. Read-only for Story 8.1; the write path arrives with Story 8.2.
 */
@RestController
public class Schedule6Controller implements Schedule6Api {

  private final MillContextService millContextService;
  private final Schedule6Service schedule6Service;
  private final SchedulePermissions permissions;

  /** Wires the mill/year guard, the derivation service, and the permission evaluator. */
  public Schedule6Controller(
      MillContextService millContextService,
      Schedule6Service schedule6Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule6Service = schedule6Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule6Response> getSchedule6(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule6Service.getSchedule6(context.millId(), context.year(), callerMayEdit));
  }
}
