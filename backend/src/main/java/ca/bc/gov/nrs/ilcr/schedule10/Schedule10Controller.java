package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule10.api.Schedule10Api;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 10 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for this
 * read — delegates ALL mill/year validation to {@link MillContextService} as its first line (AD-4),
 * and never touches repositories directly (AD-1). The server is the sole authority for
 * {@code editable} (AD-9); derivation and assembly live in {@link Schedule10Service}.
 *
 * <p>The guard is {@code validateMillYearActive}, deliberately NOT
 * {@code validateScheduleViewable(millId, year, "10")}. The latter additionally requires an
 * {@code ILCR_REPORT_SUMMARY} row for the category, and Schedule 10 has none — only categories 1, 2
 * and 3 do (delivery-confirmed, Story 11.1 Task 1 gate (ii)). Using the summary-requiring guard
 * would 404 every single request.
 */
@RestController
public class Schedule10Controller implements Schedule10Api {

  private final MillContextService millContextService;
  private final Schedule10Service schedule10Service;
  private final SchedulePermissions permissions;

  /**
   * Wires the mill/year guard, the derivation service, and permissions.
   *
   * @param millContextService the single owner of mill/year validation (AD-4)
   * @param schedule10Service the assembly and derivation service
   * @param permissions the action-based permission component (AD-7)
   */
  public Schedule10Controller(
      MillContextService millContextService,
      Schedule10Service schedule10Service,
      SchedulePermissions permissions) {
    this.millContextService = millContextService;
    this.schedule10Service = schedule10Service;
    this.permissions = permissions;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule10Response> getSchedule10(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule10Service.getSchedule10(context.millId(), context.year(), callerMayEdit));
  }
}
