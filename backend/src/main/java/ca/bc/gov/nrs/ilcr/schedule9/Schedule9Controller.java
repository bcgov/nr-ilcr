package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule9.api.Schedule9Api;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 9 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * and Check Status, {@code EDIT_SCHEDULE} for the writes — delegates ALL mill/year validation to
 * {@link MillContextService} as its first line (AD-4), and never touches repositories directly
 * (AD-1). The server is the sole authority for {@code editable} (AD-9/S30): derived from the
 * caller's {@code EDIT_SCHEDULE} action and, in the service, the Draft track status; the frontend
 * never computes it.
 *
 * <p>{@code validateMillYearActive} is the correct guard (not {@code
 * validateScheduleViewable("9")}): Schedule 9 is summary-less — {@code CONTRACTUAL_WORK_REPORT} is
 * the master with no category-'9' {@code ILCR_REPORT_SUMMARY} row — so the summary-requiring guard
 * would 404 every request.
 *
 * <p>The write responses echo the recomputed document with the AD-8 success message ({@code
 * dataSavedSuccesfullyInfoMsg} on add/edit, {@code dataDeletedSuccesfullyInfoMsg} on delete),
 * resolved to verbatim text here so the service stays message-free. {@code Authentication} is a
 * method parameter, never {@code SecurityContextHolder}, which is empty on Boot-4 MockMvc paths.
 */
@RestController
public class Schedule9Controller implements Schedule9Api {

  private static final String EDIT_SCHEDULE = "EDIT_SCHEDULE";
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule9Service schedule9Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /**
   * Instantiates the controller.
   *
   * @param millContextService the mill context service
   * @param schedule9Service the Schedule 9 service
   * @param permissions the schedule permissions checker
   * @param messageSource the message source for localized messages
   */
  public Schedule9Controller(
      MillContextService millContextService,
      Schedule9Service schedule9Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule9Service = schedule9Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  /**
   * Retrieves the Schedule 9 aggregate document.
   *
   * @param millId the mill ID
   * @param year the reporting year
   * @param authentication the caller's principal
   * @return the Schedule 9 aggregate document
   */
  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule9Response> getSchedule9(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, EDIT_SCHEDULE);
    return ResponseEntity.ok(
        schedule9Service.getSchedule9(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule9Response> addRecord(
      String millId,
      String year,
      ContractualWorkRecordRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule9Response doc =
        schedule9Service.addRecord(
            context.millId(),
            context.year(),
            request,
            permissions.hasPermission(authentication, EDIT_SCHEDULE),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule9Response> updateRecord(
      int id,
      String millId,
      String year,
      ContractualWorkRecordRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule9Response doc =
        schedule9Service.updateRecord(
            context.millId(),
            context.year(),
            id,
            request,
            permissions.hasPermission(authentication, EDIT_SCHEDULE),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule9Response> deleteRecord(
      int id, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule9Response doc =
        schedule9Service.deleteRecord(
            context.millId(),
            context.year(),
            id,
            permissions.hasPermission(authentication, EDIT_SCHEDULE));
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule9CheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    // Read-only (AD-5): context guard first, then evaluate — mutates nothing, and no Draft gate.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return ResponseEntity.ok(schedule9Service.checkStatus(context.millId(), context.year()));
  }

  /** Resolve a legacy bundle key to a {@link MessageInfo} carrying its verbatim text (AD-8). */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
