package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service.SubPage;
import ca.bc.gov.nrs.ilcr.schedule5.api.Schedule5Api;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageSaveRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 5 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * — delegates ALL mill/year validation to {@link MillContextService} as its first line (AD-4), and
 * never touches repositories directly (AD-1). The server is the sole authority for {@code editable}
 * (AD-9/S19): it is derived here from the caller's {@code EDIT_SCHEDULE} action and, in the
 * service, the Draft track status; the frontend never computes it.
 *
 * <p>{@code validateMillYearActive} is the correct guard rather than {@code
 * validateScheduleViewable(millId, year, "5")}: the latter requires a category-{@code '5'} {@code
 * ILCR_REPORT_SUMMARY} row, and Schedule 5 has none in delivery (Story 7.1 Task 1 gate (ii) — zero
 * rows; summaries exist only for categories 1/2/3), so it would 404 every request.
 *
 * <p>{@code Authentication} is a method parameter, never {@code SecurityContextHolder}, which is
 * empty on Boot-4 MockMvc paths.
 */
@RestController
public class Schedule5Controller implements Schedule5Api {

  private static final String EDIT_SCHEDULE = "EDIT_SCHEDULE";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule5Service schedule5Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;
  private final Schedule5CheckStatusResolver checkStatusResolver;

  /**
   * Wires the mill/year guard, the derivation service, permissions, the message bundle, and the
   * check-status resolver.
   */
  public Schedule5Controller(
      MillContextService millContextService,
      Schedule5Service schedule5Service,
      SchedulePermissions permissions,
      MessageSource messageSource,
      Schedule5CheckStatusResolver checkStatusResolver) {
    this.millContextService = millContextService;
    this.schedule5Service = schedule5Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
    this.checkStatusResolver = checkStatusResolver;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule5Response> getSchedule5(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, EDIT_SCHEDULE);
    return ResponseEntity.ok(
        schedule5Service.getSchedule5(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule5Response> addCamp(
      String millId, String year, CampRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule5Response doc =
        schedule5Service.addCamp(
            context.millId(),
            context.year(),
            request,
            permissions.hasPermission(authentication, EDIT_SCHEDULE),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule5Response> updateCamp(
      int campId, String millId, String year, CampRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule5Response doc =
        schedule5Service.updateCamp(
            context.millId(),
            context.year(),
            campId,
            request,
            permissions.hasPermission(authentication, EDIT_SCHEDULE),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule5Response> deleteCamp(
      int campId, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule5Response doc =
        schedule5Service.deleteCamp(
            context.millId(),
            context.year(),
            campId,
            permissions.hasPermission(authentication, EDIT_SCHEDULE));
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule5CheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    // Read-only (AD-5): context guard first, then evaluate — mutates nothing, and no Draft gate.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(checkStatusResolver.checkStatus(context.millId(), context.year()));
  }

  // ===============================================================================================
  // Sub-pages (Story 7.4). Each method is the same four lines — guard, permission, delegate, echo —
  // differing only in the SubPage constant, which is where the two pages' cost bounds and item ids
  // live (Schedule5Service.SubPage).
  // ===============================================================================================

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<SubPageDocument> getOtherCampExpenses(
      int campId, String millId, String year, Authentication authentication) {
    return ResponseEntity.ok(readSubPage(campId, millId, year, SubPage.CAMP, authentication));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<SubPageDocument> getOtherAccessExpenses(
      int campId, String millId, String year, Authentication authentication) {
    return ResponseEntity.ok(readSubPage(campId, millId, year, SubPage.ACCESS, authentication));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<SubPageDocument> saveOtherCampExpenses(
      int campId,
      String millId,
      String year,
      SubPageSaveRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        saveSubPage(campId, millId, year, SubPage.CAMP, request, authentication));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<SubPageDocument> saveOtherAccessExpenses(
      int campId,
      String millId,
      String year,
      SubPageSaveRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        saveSubPage(campId, millId, year, SubPage.ACCESS, request, authentication));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<SubPageDocument> deleteOtherCampExpense(
      int campId, int rowId, String millId, String year, Authentication authentication) {
    return ResponseEntity.ok(
        deleteSubPageRow(campId, rowId, millId, year, SubPage.CAMP, authentication));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<SubPageDocument> deleteOtherAccessExpense(
      int campId, int rowId, String millId, String year, Authentication authentication) {
    return ResponseEntity.ok(
        deleteSubPageRow(campId, rowId, millId, year, SubPage.ACCESS, authentication));
  }

  private SubPageDocument readSubPage(
      int campId, String millId, String year, SubPage page, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return schedule5Service.getSubPage(
        context.millId(),
        context.year(),
        campId,
        page,
        permissions.hasPermission(authentication, EDIT_SCHEDULE));
  }

  private SubPageDocument saveSubPage(
      int campId,
      String millId,
      String year,
      SubPage page,
      SubPageSaveRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return schedule5Service
        .saveSubPage(
            context.millId(),
            context.year(),
            campId,
            page,
            request,
            permissions.hasPermission(authentication, EDIT_SCHEDULE),
            authentication.getName())
        .withMessage(message(MSG_SAVED));
  }

  private SubPageDocument deleteSubPageRow(
      int campId,
      int rowId,
      String millId,
      String year,
      SubPage page,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return schedule5Service
        .deleteSubPageRow(
            context.millId(),
            context.year(),
            campId,
            page,
            rowId,
            permissions.hasPermission(authentication, EDIT_SCHEDULE))
        .withMessage(message(MSG_DELETED));
  }

  /** Resolve a legacy bundle key (with optional MessageFormat args) to verbatim text (AD-8). */
  private MessageInfo message(String key, Object... args) {
    return new MessageInfo(key, resolveText(key, args));
  }

  /**
   * Resolve a bundle key to its verbatim text. NO default message: a missing or renamed key must
   * fail loudly rather than degrade into user-facing text — passing the key as its own default once
   * shipped "Road : 1 - TFL Number : missingRequiredFieldMsg" to a licensee while every happy-path
   * assertion still passed (Schedule 6 code review 2026-08-04).
   */
  private String resolveText(String key, Object... args) {
    return messageSource.getMessage(
        key, args.length == 0 ? null : args, LocaleContextHolder.getLocale());
  }
}
