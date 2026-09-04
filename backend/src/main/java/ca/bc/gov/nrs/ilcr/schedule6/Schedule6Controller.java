package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule6.api.Schedule6Api;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6SaveRequest;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 6 endpoints. Authorizes by naming the action (AD-7) — {@code VIEW_SCHEDULE} for the read
 * and check-status, {@code EDIT_SCHEDULE} for the writes — delegates ALL mill/year validation to
 * {@link MillContextService} as its first line (AD-4, list-schedule guard: verbatim ERR-001 on a
 * missing/blank/non-numeric param, ERR-002/003 for closed/absent context), and never touches
 * repositories directly (AD-1). The server is the sole authority for {@code editable} (AD-9). The
 * Draft write gate (1–10 track), BR-02/BR-03/BR-09, and check-status evaluation live in {@link
 * Schedule6Service}, which emits bundle keys only.
 *
 * <p>This controller resolves the verbatim text for its own save/delete confirmations (AD-8). The
 * check-status {@code Value Required} composition moved to {@link Schedule6CheckStatusResolver} in
 * Story 15.0 so a report-level caller can obtain a fully-resolved verdict in process; the bytes are
 * unchanged — {@code label + ": " + text}, exactly as legacy {@code
 * FacesUtil.addCheckStatusErrorMessage} did, with the label's field segment carrying a leading AND
 * trailing space so the rendered line has a space on both sides of the final colon ({@code "Road :
 * 1 - TFL Number : Value Required"}).
 *
 * <p>Check Status here stays PAYLOAD-driven: the verdict describes the screen, not the database.
 * {@link Schedule6CheckStatusResolver#checkStatusStored} answers the other question.
 */
@RestController
public class Schedule6Controller implements Schedule6Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  private final MillContextService millContextService;
  private final Schedule6Service schedule6Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;
  private final Schedule6CheckStatusResolver checkStatusResolver;

  /**
   * Wires the mill/year guard, the derivation service, permissions, the message bundle, and the
   * check-status resolver.
   */
  public Schedule6Controller(
      MillContextService millContextService,
      Schedule6Service schedule6Service,
      SchedulePermissions permissions,
      MessageSource messageSource,
      Schedule6CheckStatusResolver checkStatusResolver) {
    this.millContextService = millContextService;
    this.schedule6Service = schedule6Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
    this.checkStatusResolver = checkStatusResolver;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule6Response> getSchedule6(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule6Service.getSchedule6(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule6Response> saveSchedule6Document(
      String millId, String year, Schedule6SaveRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc =
        schedule6Service.saveDocument(
            context.millId(),
            context.year(),
            request,
            permissions.hasPermission(authentication, "EDIT_SCHEDULE"),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule6Response> addRoadRecord(
      String millId, String year, RoadRecordRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc =
        schedule6Service.addRecord(
            context.millId(),
            context.year(),
            request,
            permissions.hasPermission(authentication, "EDIT_SCHEDULE"),
            authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule6CheckStatusResponse> checkStatus(
      String millId, String year, Schedule6CheckRequest request, Authentication authentication) {
    // Read-only (AD-5): context guard first, then evaluate — mutates nothing.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    // The PAYLOAD path stays the endpoint's semantics: the verdict describes the screen, not the
    // database (Schedule6CheckStatusResolver#checkStatusStored answers the other question).
    return ResponseEntity.ok(
        checkStatusResolver.resolve(
            schedule6Service.checkStatus(context.millId(), context.year(), request)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule6Response> deleteRoadRecord(
      int recordId, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc =
        schedule6Service.deleteRecord(
            context.millId(),
            context.year(),
            recordId,
            permissions.hasPermission(authentication, "EDIT_SCHEDULE"),
            authentication.getName());
    // Legacy's delete path resolved its own message key, not the save one
    // (Schedule6MB.delete() :224-226 -> "dataDeletedSuccesfullyInfoMsg").
    return ResponseEntity.ok(doc.withMessage(message(MSG_DELETED)));
  }

  /** Resolve a legacy bundle key (with optional MessageFormat args) to verbatim text (AD-8). */
  private MessageInfo message(String key, Object... args) {
    return new MessageInfo(key, resolveText(key, args));
  }

  /**
   * Resolve a bundle key to its verbatim text. No default message: a missing or renamed key must
   * fail loudly rather than degrade into user-facing text — the previous form passed the key as its
   * own default, so a typo shipped "Road : 1 - TFL Number : missingRequiredFieldMsg" to the
   * licensee and every happy-path assertion still passed (code review 2026-08-04).
   */
  private String resolveText(String key, Object... args) {
    return messageSource.getMessage(
        key, args.length == 0 ? null : args, LocaleContextHolder.getLocale());
  }
}
