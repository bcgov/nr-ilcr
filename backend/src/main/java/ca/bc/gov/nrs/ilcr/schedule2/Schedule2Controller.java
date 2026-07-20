package ca.bc.gov.nrs.ilcr.schedule2;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule2.api.Schedule2Api;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 2 endpoints. Authorizes by naming the schedule action (AD-7), delegates all mill/year
 * validation to {@link MillContextService} (AD-4), and never touches repositories directly (AD-1
 * layering). The read-only {@code editable} flag is derived from the caller's {@code EDIT_SCHEDULE}
 * permission, computed server-side (AD-5).
 *
 * <p>The read (GET) never 404s on a missing Schedule 2 summary — the single deliberate divergence
 * from Schedule 1's read — so it uses {@link MillContextService#validateMillYearActive} instead of
 * {@code validateScheduleViewable}. The write path (PUT/DELETE) uses the same no-summary-required
 * context guard: SAVE creates the summary when absent and DELETE is idempotent, so neither 404s.
 */
@RestController
@RequiredArgsConstructor
public class Schedule2Controller implements Schedule2Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule2Service schedule2Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutating-response success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
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

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule2Response> saveSchedule2(
      long millId, int year, Schedule2Request request, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule2Response saved = schedule2Service.saveSchedule2(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deleteSchedule2(
      long millId, int year, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    schedule2Service.deleteSchedule2(millId, year);
    return ResponseEntity.ok(new MessageResponse(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<CheckStatusResponse> checkStatus(
      long millId, int year, Authentication authentication) {
    // Read-only (AD-5): context guard first (no summary-required), then evaluate — mutates nothing.
    millContextService.validateMillYearActive(millId, year);
    CheckStatusResponse status = schedule2Service.checkStatus(millId, year);
    // Resolve each message's verbatim bundle text (AD-8), same as the save/delete success message.
    List<MessageInfo> resolved = status.messages().stream()
        .map(m -> message(m.key()))
        .toList();
    return ResponseEntity.ok(new CheckStatusResponse(status.outcome(), resolved));
  }
}
