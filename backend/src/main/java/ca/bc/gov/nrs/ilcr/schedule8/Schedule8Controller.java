package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule8.api.Schedule8Api;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule8Service schedule8Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /** Resolve a legacy bundle key to verbatim text (AD-8). */
  private MessageInfo message(String key) {
    return new MessageInfo(key,
        messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule8Response> getSchedule8(
      long millId, int year, Authentication authentication) {
    // No no-pages 404 for Schedule 8 — only mill/year existence + active checks (404/409).
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule8Service.getSchedule8(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule8Response> savePage(
      long millId, int year, Schedule8PageRequest request, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule8Response saved = schedule8Service.savePage(millId, year, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<MessageResponse> deletePage(
      long millId, int year, int id, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    schedule8Service.deletePage(millId, year, id);
    return ResponseEntity.ok(new MessageResponse(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule8Response> saveSample(
      long millId, int year, int pageId, Schedule8SampleRequest request,
      Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    String user = authentication.getName();
    Schedule8Response saved =
        schedule8Service.saveSample(millId, year, pageId, request, callerMayEdit, user);
    return ResponseEntity.ok(saved.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule8Response> deleteSample(
      long millId, int year, int pageId, int id, Authentication authentication) {
    millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    Schedule8Response updated =
        schedule8Service.deleteSample(millId, year, pageId, id, callerMayEdit);
    return ResponseEntity.ok(updated.withMessage(message(MSG_DELETED)));
  }
}
