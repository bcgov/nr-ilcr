package ca.bc.gov.nrs.ilcr.schedule7b;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule7b.api.Schedule7bApi;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 7B endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the
 * read and check-status, {@code EDIT_SCHEDULE} for the writes — delegates ALL mill/year validation
 * to {@link MillContextService} as its first line (AD-4, the list-schedule guard, verbatim ERR-003
 * on a missing/blank/non-numeric param), and never touches repositories directly (AD-1). The Draft
 * write gate (1–10 track, AD-9), derivations, validation, and the type-conditional check-status
 * live in {@link Schedule7bService}; success text on a mutation echo is resolved verbatim from the
 * bundle here (AD-8).
 *
 * <p>Legacy gated this page on its own WebADE action key {@code schedule7B}, derived from the view
 * filename and distinct from the coarser {@code 'schedules'} menu-visibility key ({@code
 * AuthorizationPhaseListener}). The FAM two-group model (PRD DL-23) has no per-page key, so that
 * key maps onto the central {@code VIEW_SCHEDULE}/{@code EDIT_SCHEDULE} actions; the separation
 * legacy expressed — submenu visible does not imply page reachable — is preserved by checking the
 * action on EVERY endpoint here rather than only at menu render.
 */
@RestController
public class Schedule7bController implements Schedule7bApi {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule7bService schedule7bService;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /**
   * Construct the controller with its four collaborators.
   *
   * @param millContextService the single owner of mill/year validation (AD-4)
   * @param schedule7bService the domain service owning derivation, gates and check-status
   * @param permissions the central role → action map used by {@code @PreAuthorize} (AD-7)
   * @param messageSource the legacy message bundle, for verbatim success text (AD-8)
   */
  public Schedule7bController(
      MillContextService millContextService,
      Schedule7bService schedule7bService,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule7bService = schedule7bService;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule7bResponse> getSchedule7b(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule7bService.getSchedule7b(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7bResponse> addCulvert(
      String millId, String year, CulvertRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7bResponse doc =
        schedule7bService.addCulvert(
            context.millId(), context.year(), request, true, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7bResponse> updateCulvert(
      long id, String millId, String year, CulvertRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7bResponse doc =
        schedule7bService.updateCulvert(
            context.millId(), context.year(), id, request, true, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7bResponse> saveAllCulverts(
      String millId, String year, CulvertSaveAllRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7bResponse doc =
        schedule7bService.saveAllCulverts(
            context.millId(), context.year(), request, true, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule7bResponse> deleteCulvert(
      long id, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule7bResponse doc =
        schedule7bService.deleteCulvert(context.millId(), context.year(), id, true);
    // SUC-002 unconditionally — including when that was the last culvert. Legacy 7B's
    // Schedule7bMB.update() (managedBean/Schedule7bMB.java:216-230) always emits the key it was
    // passed; the empty-list branch that swaps in anyDataToSaveInfoMsg exists ONLY in
    // Schedule7aMB.java:374, and that string appears nowhere else in the legacy source. Telling a
    // reporter data "was saved" on a delete would be a fabricated message for this schedule.
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule7bCheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(schedule7bService.checkStatus(context.millId(), context.year()));
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for a mutation-echo success message. */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
