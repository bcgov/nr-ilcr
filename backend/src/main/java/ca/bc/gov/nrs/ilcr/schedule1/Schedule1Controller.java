package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.api.Schedule1Api;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 1 endpoints. Authorizes by naming the {@code VIEW_SCHEDULE} action (AD-7), delegates all
 * mill/year validation to {@link MillContextService} (AD-4), and never touches repositories directly
 * (AD-1 layering). The read-only {@code editable} flag is derived from the caller's
 * {@code EDIT_SCHEDULE} permission, computed server-side (AD-5).
 */
@RestController
@RequiredArgsConstructor
public class Schedule1Controller implements Schedule1Api {

  private static final String SCHEDULE_1_CATEGORY = "1";

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule1Service schedule1Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutating-response success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule1Response> getSchedule1(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule1Service.getSchedule1(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule1Response> saveSchedule1(
      long millId, int year, Schedule1Request request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule1Response saved = schedule1Service.saveSchedule1(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deleteSchedule1(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    schedule1Service.deleteSchedule1(millId, year);
    return ResponseEntity.ok(new MessageResponse(message(MSG_DELETED)));
  }
}
