package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule7a.api.Schedule7aApi;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import ca.bc.gov.nrs.ilcr.security.ScheduleEditability;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 7A endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the
 * read and check-status, {@code EDIT_SCHEDULE} for the writes — delegates ALL mill/year validation
 * to {@link MillContextService} as its first line (AD-4, the list-schedule guard, verbatim ERR-001
 * on a missing/blank/non-numeric param), and never touches repositories directly (AD-1). The Draft
 * write gate (1–10 track, AD-9), derivations, validation, and check-status live in {@link
 * Schedule7aService}; success text on a mutation echo is resolved verbatim from the bundle here
 * (AD-8).
 */
@RestController
public class Schedule7aController implements Schedule7aApi {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";
  private static final String MSG_EMPTY = "anyDataToSaveInfoMsg";

  private final MillContextService millContextService;
  private final Schedule7aService schedule7aService;
  private final ScheduleEditability editability;
  private final MessageSource messageSource;

  /**
   * Constructs a new {@link Schedule7aController}.
   *
   * @param millContextService the mill context service
   * @param schedule7aService the schedule 7a service
   * @param editability the role×status editability resolver
   * @param messageSource the message source
   */
  public Schedule7aController(
      MillContextService millContextService,
      Schedule7aService schedule7aService,
      ScheduleEditability editability,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule7aService = schedule7aService;
    this.editability = editability;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule7aResponse> getSchedule7a(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    EditableStatuses caller = editability.forCaller(authentication);
    return ResponseEntity.ok(
        schedule7aService.getSchedule7a(context.millId(), context.year(), caller));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7aResponse> addBridge(
      String millId, String year, BridgeRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7aResponse doc =
        schedule7aService.addBridge(
            context.millId(),
            context.year(),
            request,
            editability.forCaller(authentication),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7aResponse> updateBridge(
      long id, String millId, String year, BridgeRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7aResponse doc =
        schedule7aService.updateBridge(
            context.millId(),
            context.year(),
            id,
            request,
            editability.forCaller(authentication),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7aResponse> saveAllBridges(
      String millId, String year, BridgeSaveAllRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7aResponse doc =
        schedule7aService.saveAllBridges(
            context.millId(),
            context.year(),
            request,
            editability.forCaller(authentication),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7aResponse> deleteBridge(
      long id, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7aResponse doc =
        schedule7aService.deleteBridge(
            context.millId(), context.year(), id, editability.forCaller(authentication));
    // SUC-002 when bridges remain; SUC-003 (empty schedule) when the last bridge was removed.
    String key = doc.bridges().isEmpty() ? MSG_EMPTY : MSG_DELETED;
    return ResponseEntity.ok(doc.withMessage(message(key)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule7aCheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(schedule7aService.checkStatus(context.millId(), context.year()));
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutation-echo success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
