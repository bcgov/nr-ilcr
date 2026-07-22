package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule4.api.Schedule4Api;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * is correct for Schedule 4 (Schedule 4 has no {@code ILCR_REPORT_SUMMARY} row of its own). The write
 * path (PUT/DELETE {@code /locations}, Story 4.2) uses the same no-summary-required context guard and
 * resolves the AD-8 success-message keys via {@link MessageSource} on the echo.
 */
@RestController
@RequiredArgsConstructor
public class Schedule4Controller implements Schedule4Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule4Service schedule4Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutating-response success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(key,
        messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule4Response> getSchedule4(
      long millId, int year, Authentication authentication) {
    // No no-locations 404 for Schedule 4 — only mill/year existence + active checks (404/409).
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule4Service.getSchedule4(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule4Response> saveLocation(
      long millId, int year, Schedule4LocationRequest request, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule4Response saved =
        schedule4Service.saveLocation(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deleteLocation(
      long millId, int year, int id, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    schedule4Service.deleteLocation(millId, year, id);
    return ResponseEntity.ok(new MessageResponse(message(MSG_DELETED)));
  }
}
