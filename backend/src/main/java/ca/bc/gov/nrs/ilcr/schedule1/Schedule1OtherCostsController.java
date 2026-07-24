package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule1.api.Schedule1OtherCostsApi;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subtotal Other Costs endpoints (Story 2.4). Mirrors {@link Schedule1Controller}: authorizes by
 * naming the action (AD-7), delegates mill/year validation to {@link MillContextService} (AD-4),
 * never touches repositories directly (AD-1), and resolves success messages verbatim from the bundle
 * (AD-8). The Draft write gate and derivation live in {@link Schedule1Service}.
 */
@RestController
public class Schedule1OtherCostsController implements Schedule1OtherCostsApi {

  private static final String SCHEDULE_1_CATEGORY = "1";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule1Service schedule1Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  public Schedule1OtherCostsController(
      MillContextService millContextService,
      Schedule1Service schedule1Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule1Service = schedule1Service;
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
  public ResponseEntity<OtherCostsDocument> getOtherCosts(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule1Service.getOtherCostsDocument(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherCostsDocument> addOtherCost(
      long millId, int year, OtherCostRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    OtherCostsDocument doc =
        schedule1Service.addOtherCost(millId, year, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherCostsDocument> updateOtherCost(
      int id, long millId, int year, OtherCostRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    OtherCostsDocument doc =
        schedule1Service.updateOtherCost(millId, year, id, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherCostsDocument> deleteOtherCost(
      int id, long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_1_CATEGORY);
    OtherCostsDocument doc = schedule1Service.deleteOtherCost(millId, year, id);
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }
}
