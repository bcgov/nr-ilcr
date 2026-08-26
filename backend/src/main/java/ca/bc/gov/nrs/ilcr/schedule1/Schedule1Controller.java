package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.api.Schedule1Api;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 1 endpoints. Authorizes by naming the {@code VIEW_SCHEDULE} action (AD-7), delegates all
 * mill/year validation to {@link MillContextService} (AD-4), and never touches repositories
 * directly (AD-1 layering). The read-only {@code editable} flag is derived from the caller's {@code
 * EDIT_SCHEDULE} permission, computed server-side (AD-5).
 *
 * <p>The guard is {@code validateMillYearActive}, NOT {@code validateScheduleViewable(millId, year,
 * "1")}. The latter additionally requires a category-{@code "1"} {@code ILCR_REPORT_SUMMARY} row to
 * exist, which made a mill/year with no saved data a 404 with no form and no way to create one —
 * defect #296. A summary is now created by the first save (Schedule 2's shape), so zero saved data
 * is a valid 200 here, exactly as it already is on every other schedule. The sub-page controllers
 * keep {@code validateScheduleViewable}: they are reachable only from a saved parent (legacy
 * ALT-001, "The schedule has to be saved before opening other costs").
 */
@RestController
public class Schedule1Controller implements Schedule1Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";
  private static final String MSG_NOTHING_DELETED = "noDataToDeleteInfoMsg";

  private final MillContextService millContextService;
  private final Schedule1Service schedule1Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /**
   * Constructs the Schedule 1 controller.
   *
   * @param millContextService the mill context service
   * @param schedule1Service the Schedule 1 service
   * @param permissions the schedule permissions evaluator
   * @param messageSource the message source
   */
  public Schedule1Controller(
      MillContextService millContextService,
      Schedule1Service schedule1Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule1Service = schedule1Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  /**
   * Resolve a legacy bundle key to verbatim text (AD-8) for a mutating-response success message.
   */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule1Response> getSchedule1(
      long millId, int year, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule1Service.getSchedule1(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule1Response> saveSchedule1(
      long millId, int year, Schedule1Request request, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule1Response saved =
        schedule1Service.saveSchedule1(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deleteSchedule1(
      long millId, int year, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    // 200 either way (the DELETE never 404s since defect #296); the message tells the truth
    // about what happened, as Schedule 2's has since the #292 code review.
    boolean removed = schedule1Service.deleteSchedule1(millId, year);
    return ResponseEntity.ok(
        new MessageResponse(message(removed ? MSG_DELETED : MSG_NOTHING_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule1CheckStatusResponse> checkStatus(
      long millId, int year, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(schedule1Service.checkSchedule1Status(millId, year));
  }
}
