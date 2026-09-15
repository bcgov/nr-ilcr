package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.api.Schedule3OtherCostsApi;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableSaveRequest;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import ca.bc.gov.nrs.ilcr.security.ScheduleEditability;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 3 Other Acceptable Costs endpoints (Story 4.4). Mirrors {@link Schedule3Controller}:
 * authorizes by naming the action (AD-7), delegates mill/year validation to {@link
 * MillContextService} with category {@code "3"} (AD-4), never touches repositories directly (AD-1),
 * and resolves success messages verbatim from the bundle (AD-8). editability gate + item-124 group
 * encoding live in {@link Schedule3Service}.
 */
@RestController
public class Schedule3OtherCostsController implements Schedule3OtherCostsApi {

  private static final String SCHEDULE_3_CATEGORY = "3";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule3Service schedule3Service;
  private final ScheduleEditability editability;
  private final MessageSource messageSource;

  /**
   * Constructs the Schedule 3 other costs controller.
   *
   * @param millContextService the mill context service
   * @param schedule3Service the schedule 3 service
   * @param editability the role×status editability resolver
   * @param messageSource the message source
   */
  public Schedule3OtherCostsController(
      MillContextService millContextService,
      Schedule3Service schedule3Service,
      ScheduleEditability editability,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule3Service = schedule3Service;
    this.editability = editability;
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
    EditableStatuses caller = editability.forCaller(authentication);
    return ResponseEntity.ok(schedule3Service.getOtherAcceptableDocument(millId, year, caller));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> addOtherAcceptable(
      long millId, int year, OtherAcceptableRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.addOtherAcceptable(
            millId, year, request, editability.forCaller(authentication), authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> saveOtherAcceptable(
      long millId,
      int year,
      String intent,
      OtherAcceptableSaveRequest request,
      Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.saveOtherAcceptable(
            millId,
            year,
            request.rows(),
            editability.forCaller(authentication),
            authentication.getName());
    // Persistence is identical for a save or a delete (legacy update()); only the message differs.
    return ResponseEntity.ok(
        doc.withMessage(message("delete".equals(intent) ? MSG_DELETED : MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> updateOtherAcceptable(
      int id,
      long millId,
      int year,
      OtherAcceptableRequest request,
      Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.updateOtherAcceptable(
            millId,
            year,
            id,
            request,
            editability.forCaller(authentication),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<OtherAcceptableDocument> deleteOtherAcceptable(
      int id, long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    OtherAcceptableDocument doc =
        schedule3Service.deleteOtherAcceptable(
            millId, year, id, editability.forCaller(authentication), authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }
}
