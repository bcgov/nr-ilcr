package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.api.Schedule3OtherCostsApi;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 3 Other Acceptable Costs endpoints (Story 4.4). Mirrors {@link Schedule3Controller}:
 * authorizes by naming the action (AD-7), delegates mill/year validation to {@link MillContextService}
 * with category {@code "3"} (AD-4), never touches repositories directly (AD-1), and resolves success
 * messages verbatim from the bundle (AD-8). Draft gate + item-124 group encoding live in
 * {@link Schedule3Service}.
 */
@RestController
public class Schedule3OtherCostsController implements Schedule3OtherCostsApi {

  private static final String SCHEDULE_3_CATEGORY = "3";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule3Service schedule3Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  public Schedule3OtherCostsController(
      MillContextService millContextService,
      Schedule3Service schedule3Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule3Service = schedule3Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> getOtherAcceptable(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule3Service.getOtherAcceptableDocument(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> addOtherAcceptable(
      long millId, int year, OtherAcceptableRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.addOtherAcceptable(millId, year, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> updateOtherAcceptable(
      int id, long millId, int year, OtherAcceptableRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.updateOtherAcceptable(millId, year, id, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> deleteOtherAcceptable(
      int id, long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc = schedule3Service.deleteOtherAcceptable(millId, year, id);
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }
}
