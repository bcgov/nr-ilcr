package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule6.api.Schedule6Api;
import ca.bc.gov.nrs.ilcr.schedule6.dto.GeneralCommentsRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import java.util.Map;
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
 * Draft write gate (1–10 track), BR-02/BR-03/BR-09, and check-status evaluation live in
 * {@link Schedule6Service}; the service emits bundle keys and this controller resolves the verbatim
 * text (AD-8), composing the check-status {@code Value Required} lines exactly as legacy
 * {@code FacesUtil.addCheckStatusErrorMessage} did — {@code label + ": " + text} with the label's
 * field segment carrying a leading AND trailing space, so the rendered line has a space on both
 * sides of the final colon ({@code "Road : 1 - TFL Number : Value Required"}).
 */
@RestController
public class Schedule6Controller implements Schedule6Api {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";

  // The verbatim legacy field segments (Schedule6MB.checkStatus() :155-172), keyed by the
  // FieldIssue.field names the service emits. Each carries its legacy leading AND trailing space.
  private static final Map<String, String> FIELD_SEGMENTS = Map.of(
      Schedule6Service.FIELD_AREA_TYPE, " - TSA or TFL TYPE ",
      Schedule6Service.FIELD_TFL_NUMBER, " - TFL Number ",
      Schedule6Service.FIELD_SUPPLY_BLOCK, " - Supply Block ",
      // The label says TSA or TFL but the check is Cost — the legacy mislabel, ported verbatim
      // (Schedule6MB.java:172; the Gherkin asserts these bytes).
      Schedule6Service.FIELD_COST, " - TSA or TFL (Cost $) ");

  private final MillContextService millContextService;
  private final Schedule6Service schedule6Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /** Wires the mill/year guard, the derivation service, permissions, and the message bundle. */
  public Schedule6Controller(
      MillContextService millContextService,
      Schedule6Service schedule6Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule6Service = schedule6Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
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
  public ResponseEntity<Schedule6Response> addRoadRecord(
      String millId, String year, RoadRecordRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc = schedule6Service.addRecord(
        context.millId(), context.year(), request,
        permissions.hasPermission(authentication, "EDIT_SCHEDULE"), authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule6Response> updateRoadRecord(
      int recordId, String millId, String year, RoadRecordRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc = schedule6Service.updateRecord(
        context.millId(), context.year(), recordId, request,
        permissions.hasPermission(authentication, "EDIT_SCHEDULE"), authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule6Response> saveGeneralComments(
      String millId, String year, GeneralCommentsRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6Response doc = schedule6Service.saveGeneralComments(
        context.millId(), context.year(), request,
        permissions.hasPermission(authentication, "EDIT_SCHEDULE"), authentication.getName());
    return ResponseEntity.ok(doc.withMessage(message(MSG_SAVED)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule6CheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    // Read-only (AD-5): context guard first, then evaluate — mutates nothing.
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule6CheckStatusResponse raw =
        schedule6Service.checkStatus(context.millId(), context.year());
    // Resolve every bundle key to verbatim text (AD-8): the schedule banner, each clean record's
    // met message (with its rowCounter as the {0} arg), and each composed "Value Required" line.
    List<MessageInfo> scheduleMessages = raw.messages().stream()
        .map(m -> message(m.key()))
        .toList();
    List<RoadRecordCheckResult> records = raw.records().stream()
        .map(record -> new RoadRecordCheckResult(
            record.recordId(),
            record.rowCounter(),
            record.met(),
            record.metMessage() == null
                ? null
                // String, not int: MessageFormat applies locale number grouping to a numeric arg,
                // so ordinal 1000 rendered "1,000". Legacy passed a String
                // (Schedule6MB.java:147 — road.getRowCounter().toString()).
                : message(record.metMessage().key(), String.valueOf(record.rowCounter())),
            record.issues().stream()
                .map(issue -> new FieldIssue(
                    issue.field(),
                    composedValueRequired(record.rowCounter(), issue.field(),
                        issue.message().key())))
                .toList()))
        .toList();
    return ResponseEntity.ok(
        new Schedule6CheckStatusResponse(raw.outcome(), scheduleMessages, records));
  }

  /**
   * One composed check-status line: {@code "Road : " + rowCounter + fieldSegment + ": " + text}
   * (legacy {@code Schedule6MB.addMessageCheckStatus} +
   * {@code FacesUtil.addCheckStatusErrorMessage} :127–139), with
   * {@code missingRequiredFieldMsg} resolved verbatim as the suffix.
   */
  private MessageInfo composedValueRequired(int rowCounter, String field, String key) {
    String segment = FIELD_SEGMENTS.get(field);
    if (segment == null) {
      // Unmapped field name — without this the line would render "Road : 1null: Value Required".
      // The service and this map are the only two places the field names live, so a mismatch is a
      // programming error, not client input (code review 2026-08-04).
      throw new IllegalStateException("No check-status field segment mapped for '" + field + "'");
    }
    String label = "Road : " + rowCounter + segment;
    return new MessageInfo(key, label + ": " + resolveText(key));
  }

  /** Resolve a legacy bundle key (with optional MessageFormat args) to verbatim text (AD-8). */
  private MessageInfo message(String key, Object... args) {
    return new MessageInfo(key, resolveText(key, args));
  }

  /**
   * Resolve a bundle key to its verbatim text. No default message: a missing or renamed key must
   * fail loudly rather than degrade into user-facing text — the previous form passed the key as its
   * own default, so a typo shipped "Road : 1 - TFL Number : missingRequiredFieldMsg" to the licensee
   * and every happy-path assertion still passed (code review 2026-08-04).
   */
  private String resolveText(String key, Object... args) {
    return messageSource.getMessage(
        key, args.length == 0 ? null : args, LocaleContextHolder.getLocale());
  }
}
