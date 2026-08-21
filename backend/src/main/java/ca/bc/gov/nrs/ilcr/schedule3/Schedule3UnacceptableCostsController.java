package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule3.api.Schedule3UnacceptableCostsApi;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableSaveRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 3 Included Unacceptable Costs endpoints (Story 4.4). Mirrors {@link
 * Schedule3OtherCostsController}: authorizes by naming the action (AD-7), delegates mill/year
 * validation to {@link MillContextService} with category {@code "3"} (AD-4), and resolves success
 * messages verbatim from the bundle (AD-8).
 */
@RestController
public class Schedule3UnacceptableCostsController implements Schedule3UnacceptableCostsApi {

  private static final String SCHEDULE_3_CATEGORY = "3";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule3Service schedule3Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /**
   * Constructs the controller.
   *
   * @param millContextService the mill context service
   * @param schedule3Service the schedule 3 service
   * @param permissions the schedule permissions
   * @param messageSource the message source
   */
  public Schedule3UnacceptableCostsController(
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
  public ResponseEntity<UnacceptableDocument> getUnacceptable(
      long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(schedule3Service.getUnacceptableDocument(millId, year, callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<UnacceptableDocument> addUnacceptable(
      long millId, int year, UnacceptableRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    UnacceptableDocument doc =
        schedule3Service.addUnacceptable(millId, year, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<UnacceptableDocument> saveUnacceptable(
      long millId,
      int year,
      String intent,
      UnacceptableSaveRequest request,
      Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    UnacceptableDocument doc =
        schedule3Service.saveUnacceptable(millId, year, request.rows(), authentication.getName());
    // Persistence is identical for a save or a delete (legacy update()); only the message differs.
    return ResponseEntity.ok(
        doc.withMessage(message("delete".equals(intent) ? MSG_DELETED : MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<UnacceptableDocument> updateUnacceptable(
      int id, long millId, int year, UnacceptableRequest request, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    UnacceptableDocument doc =
        schedule3Service.updateUnacceptable(millId, year, id, request, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<UnacceptableDocument> deleteUnacceptable(
      int id, long millId, int year, Authentication authentication) {
    millContextService.validateScheduleViewable(millId, year, SCHEDULE_3_CATEGORY);
    UnacceptableDocument doc =
        schedule3Service.deleteUnacceptable(millId, year, id, authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }
}
