package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the Schedule 6 Check Status keys the service emits into the verbatim text the client
 * renders (AD-8), composing each {@code Value Required} line exactly as legacy {@code
 * FacesUtil.addCheckStatusErrorMessage} did — {@code label + ": " + text}, with the label's field
 * segment carrying a leading AND trailing space so the rendered line has a space on both sides of
 * the final colon ({@code "Road : 1 - TFL Number : Value Required"}).
 *
 * <p>Moved out of a private {@code Schedule6Controller} method so Story 15.1's sweep can obtain a
 * fully-resolved Schedule 6 verdict in process instead of over HTTP (AD-5: the validation is
 * called, never re-implemented).
 */
@Component
@RequiredArgsConstructor
public class Schedule6CheckStatusResolver {

  // The verbatim legacy field segments (Schedule6MB.checkStatus() :155-172), keyed by the
  // FieldIssue.field names the service emits. Each carries its legacy leading AND trailing space.
  private static final Map<String, String> FIELD_SEGMENTS =
      Map.of(
          Schedule6Service.FIELD_AREA_TYPE, " - TSA or TFL TYPE ",
          Schedule6Service.FIELD_TFL_NUMBER, " - TFL Number ",
          Schedule6Service.FIELD_SUPPLY_BLOCK, " - Supply Block ",
          // The label says TSA or TFL but the check is Cost — the legacy mislabel, ported verbatim
          // (Schedule6MB.java:172; the Gherkin asserts these bytes).
          Schedule6Service.FIELD_COST, " - TSA or TFL (Cost $) ");

  private final Schedule6Service schedule6Service;
  private final MessageSource messageSource;

  /**
   * Is the SAVED Schedule 6 complete? Evaluates the stored rows and resolves the text — the
   * cross-schedule sweep's entry point, needing only a mill and a year.
   *
   * <p><strong>This deliberately answers a different question from {@code POST
   * /schedule6/check-status}</strong>, which evaluates the payload the caller is looking at. The
   * two can legitimately disagree, and that is the point: the endpoint describes the SCREEN
   * (legacy's Check Status was an {@code ajax="false"} full postback that applied on-screen inputs
   * before validating, {@code Schedule6MB:139-140}), while a report-level sweep must describe what
   * is actually stored. See {@link Schedule6Service#checkStatusStored}.
   *
   * <p><strong>The caller MUST validate the mill/year context first</strong> (AD-4, {@code
   * MillContextService.validateMillYearActive}). This method does not, and the failure mode is
   * SILENT rather than loud: an absent or closed {@code (millId, year)} simply yields no rows, and
   * every schedule whose verdict is a loop over rows treats zero rows as a vacuous {@code MET}
   * (Schedules 4, 5, 6, 8, 10 and 11 all document that explicitly). A sweep that skipped the guard
   * would therefore report a nonexistent or closed mill-year as COMPLETE instead of raising
   * ERR-002/ERR-003 — the worst available direction to fail in. Story 15.1 owns exactly ONE guard
   * covering all twelve schedules; do not add a thirteenth here, and do not call this without it.
   *
   * @param millId the mill id (context already validated by the caller)
   * @param year the reporting year
   * @return the stored-data verdict with every message's verbatim text populated
   */
  public Schedule6CheckStatusResponse checkStatusStored(long millId, int year) {
    return resolve(schedule6Service.checkStatusStored(millId, year));
  }

  /**
   * Resolve an already-evaluated result: the schedule banner, each clean record's met message (with
   * its {@code rowCounter} as the {@code {0}} arg), and each composed "Value Required" line.
   *
   * @param raw the service verdict, carrying bundle keys and field names
   * @return the same verdict with resolved text
   */
  public Schedule6CheckStatusResponse resolve(Schedule6CheckStatusResponse raw) {
    List<MessageInfo> scheduleMessages =
        raw.messages().stream().map(m -> message(m.key())).toList();
    List<RoadRecordCheckResult> records =
        raw.records().stream()
            .map(
                roadRecord ->
                    new RoadRecordCheckResult(
                        roadRecord.recordId(),
                        roadRecord.rowCounter(),
                        roadRecord.met(),
                        roadRecord.metMessage() == null
                            ? null
                            // String, not int: MessageFormat applies locale number grouping to a
                            // numeric arg, so ordinal 1000 rendered "1,000". Legacy passed a String
                            // (Schedule6MB.java:147 — road.getRowCounter().toString()).
                            : message(
                                roadRecord.metMessage().key(),
                                String.valueOf(roadRecord.rowCounter())),
                        roadRecord.issues().stream()
                            .map(
                                issue ->
                                    new FieldIssue(
                                        issue.field(),
                                        composedValueRequired(
                                            roadRecord.rowCounter(),
                                            issue.field(),
                                            issue.message().key())))
                            .toList()))
            .toList();
    return new Schedule6CheckStatusResponse(raw.outcome(), scheduleMessages, records);
  }

  /**
   * One composed check-status line: {@code "Road : " + rowCounter + fieldSegment + ": " + text}
   * (legacy {@code Schedule6MB.addMessageCheckStatus} + {@code
   * FacesUtil.addCheckStatusErrorMessage} :127–139), with {@code missingRequiredFieldMsg} resolved
   * verbatim as the suffix.
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
   * own default, so a typo shipped "Road : 1 - TFL Number : missingRequiredFieldMsg" to the
   * licensee and every happy-path assertion still passed (code review 2026-08-04).
   */
  private String resolveText(String key, Object... args) {
    return messageSource.getMessage(
        key, args.length == 0 ? null : args, LocaleContextHolder.getLocale());
  }
}
