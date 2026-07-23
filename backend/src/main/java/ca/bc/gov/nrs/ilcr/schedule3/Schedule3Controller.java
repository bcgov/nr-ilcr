package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.api.Schedule3Api;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 3 endpoints. Authorizes by naming the {@code VIEW_SCHEDULE}/{@code EDIT_SCHEDULE} action
 * (AD-7), delegates all mill/year validation to {@link MillContextService} with category {@code "3"}
 * (AD-4), and never touches repositories directly (AD-1 layering). The read-only {@code editable} flag
 * is derived from the caller's {@code EDIT_SCHEDULE} permission, computed server-side (AD-5).
 */
@RestController
public class Schedule3Controller implements Schedule3Api {

  private static final String SCHEDULE_3_CATEGORY = "3";

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule3Service schedule3Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  public Schedule3Controller(
      MillContextService millContextService,
      Schedule3Service schedule3Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule3Service = schedule3Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutating-response success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule3Response> getSchedule3(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule3Service.getSchedule3(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule3Response> saveSchedule3(
      long millId, int year, Schedule3Request request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule3Response saved =
        schedule3Service.saveSchedule3(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deleteSchedule3(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    schedule3Service.deleteSchedule3(millId, year);
    return ResponseEntity.ok(new MessageResponse(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<CheckStatusResponse> checkStatus(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    return ResponseEntity.ok(schedule3Service.checkSchedule3Status(millId, year));
  }
}
