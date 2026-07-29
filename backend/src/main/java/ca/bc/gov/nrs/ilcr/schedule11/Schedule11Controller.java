package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule11.api.Schedule11Api;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocationRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 11 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * and check-status, {@code EDIT_SCHEDULE} for the writes — delegates ALL mill/year validation to
 * {@link MillContextService} as its first line (AD-4, the list-schedule guard, verbatim ERR-001 on a
 * missing/blank/non-numeric param), and never touches repositories directly (AD-1). The Draft write
 * gate (silviculture track, AD-9), derivations, and success/error message composition live in
 * {@link Schedule11Service}; success text on a mutation echo is resolved verbatim from the bundle
 * here (AD-8).
 */
@RestController
public class Schedule11Controller implements Schedule11Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule11Service schedule11Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  public Schedule11Controller(
      MillContextService millContextService,
      Schedule11Service schedule11Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule11Service = schedule11Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule11Response> getSchedule11(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule11Service.getSchedule11(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule11Response> addLocation(
      String millId, String year, SilvicultureLocationRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule11Response doc = schedule11Service.addLocation(
        context.millId(), context.year(), request, true, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule11Response> updateLocation(
      long id, String millId, String year, SilvicultureLocationRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule11Response doc = schedule11Service.updateLocation(
        context.millId(), context.year(), id, request, true, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule11Response> deleteLocation(
      long id, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule11Response doc = schedule11Service.deleteLocation(
        context.millId(), context.year(), id, true);
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule11CheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(schedule11Service.checkStatus(context.millId(), context.year()));
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutation-echo success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
