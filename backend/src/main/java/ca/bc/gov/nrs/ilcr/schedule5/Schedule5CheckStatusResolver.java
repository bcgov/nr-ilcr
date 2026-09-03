package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult.CampCheckMessage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the Schedule 5 Check Status keys the service emits into the verbatim text the client
 * renders (AD-8): the schedule banner, each passing camp's met message, and each composed "Value
 * Required" line.
 *
 * <p>Moved out of a private {@code Schedule5Controller} method so Story 15.1's sweep can obtain a
 * fully-resolved Schedule 5 verdict in process instead of over HTTP (AD-5: the validation is
 * called, never re-implemented).
 */
@Component
@RequiredArgsConstructor
public class Schedule5CheckStatusResolver {

  /**
   * The verbatim legacy check-status label segments, keyed by the field names {@link
   * Schedule5Service} emits ({@code Schedule5MB.checkValidatedCurrentCamp():348-359, 425-436}).
   *
   * <p>⚠ <strong>Each carries a leading space and NO trailing space.</strong> Schedule 6's
   * equivalent segments carry BOTH, so its rendered line has a space on either side of the final
   * colon ({@code "Road : 1 - TFL Number : Value Required"}) while Schedule 5's does not ({@code
   * "Camp Report Name : Cedar Flats Camp - Camp name: Value Required"}). The difference is real and
   * is in the legacy source, not a typo here: {@code FacesUtil.addCheckStatusErrorMessage} (:134)
   * appends {@code ": "} to whatever label it is given, and Schedule 5's callers pass segments that
   * stop at the word. Copying Schedule 6's {@code FIELD_SEGMENTS} map wholesale gets every one of
   * these eight lines wrong by one byte.
   */
  private static final Map<String, String> FIELD_SEGMENTS =
      Map.of(
          Schedule5Service.FIELD_CAMP_NAME, " - Camp name",
          Schedule5Service.FIELD_ROAD_DISTANCE, " - Road Distance to Operating Area",
          Schedule5Service.FIELD_SIZE_OF_CAMP, " - Size of Camp",
          Schedule5Service.FIELD_ASSOCIATED_CAMP_VOLUME, " - Associated Camp Volume",
          Schedule5Service.FIELD_OTHER_CAMP_DESCRIPTION, " - Other Camp Expense List (Description)",
          Schedule5Service.FIELD_OTHER_CAMP_COST, " - Other Camp Expense List (Cost $)",
          Schedule5Service.FIELD_OTHER_ACCESS_DESCRIPTION,
              " - Other Access Expense List (Description)",
          Schedule5Service.FIELD_OTHER_ACCESS_COST, " - Other Access Expense List (Cost $)");

  private final Schedule5Service schedule5Service;
  private final MessageSource messageSource;

  /**
   * Evaluate and resolve Schedule 5 for a validated mill/year — the whole check in one call.
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
   * @return the verdict with every message's verbatim text populated
   */
  public Schedule5CheckStatusResponse checkStatus(long millId, int year) {
    return resolve(schedule5Service.checkStatus(millId, year));
  }

  /**
   * Resolve an already-evaluated result. Each passing camp's met message takes the CAMP NAME as its
   * {@code {0}} argument — legacy passes the name, not an ordinal, unlike Schedule 6's {@code
   * rowCounter}.
   *
   * @param raw the service verdict, carrying bundle keys and field names
   * @return the same verdict with resolved text
   */
  public Schedule5CheckStatusResponse resolve(Schedule5CheckStatusResponse raw) {
    List<MessageInfo> scheduleMessages =
        raw.messages().stream().map(m -> message(m.key())).toList();
    List<CampCheckResult> camps =
        raw.camps().stream()
            .map(
                camp ->
                    new CampCheckResult(
                        camp.campId(),
                        camp.campName(),
                        camp.requirementsMet(),
                        camp.messages().stream()
                            .map(m -> resolveCampMessage(camp.campName(), m))
                            .toList()))
            .toList();
    return new Schedule5CheckStatusResponse(raw.outcome(), scheduleMessages, camps);
  }

  /**
   * Resolve one per-camp message. A finding (it names a {@code field}) becomes the composed {@code
   * Value Required} line; the met message takes the camp name as its {@code {0}} argument.
   */
  private CampCheckMessage resolveCampMessage(String campName, CampCheckMessage raw) {
    if (raw.field() == null) {
      return new CampCheckMessage(raw.key(), null, resolveText(raw.key(), campName));
    }
    return new CampCheckMessage(raw.key(), raw.field(), composedValueRequired(campName, raw));
  }

  /**
   * One composed check-status line, byte-for-byte: {@code "Camp Report Name : " + campName +
   * segment + ": " + text}.
   *
   * <p>That is legacy's {@code addMessageCheckStatus} ({@code Schedule5MB.java:337-339}) — {@code
   * "Camp Report Name : ".concat(reportID.concat(fieldMissing))}, where {@code reportID} is the
   * camp NAME, not an id — handed to {@code FacesUtil.addCheckStatusErrorMessage} (:131-139), which
   * does {@code label.concat(": ").concat(textMessageValue)}.
   */
  private String composedValueRequired(String campName, CampCheckMessage raw) {
    String segment = FIELD_SEGMENTS.get(raw.field());
    if (segment == null) {
      // Without this the line would render "Camp Report Name : Cedar Flatsnull: Value Required".
      // Schedule5Service and this map are the only two places these field names live, so a mismatch
      // is a programming error, never client input.
      throw new IllegalStateException(
          "No check-status field segment mapped for '" + raw.field() + "'");
    }
    return "Camp Report Name : " + campName + segment + ": " + resolveText(raw.key());
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
