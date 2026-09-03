package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule4.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule4.dto.LocationCheckResult;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the Schedule 4 Check Status keys the service emits into the verbatim text the client
 * renders (AD-8): the schedule banner, each location's met message, and each field's "Value
 * Required".
 *
 * <p>Moved out of a private {@code Schedule4Controller} method so Story 15.1's sweep can obtain a
 * fully-resolved Schedule 4 verdict in process instead of over HTTP (AD-5: the validation is
 * called, never re-implemented).
 */
@Component
@RequiredArgsConstructor
public class Schedule4CheckStatusResolver {

  private final Schedule4Service schedule4Service;
  private final MessageSource messageSource;

  /**
   * Evaluate and resolve Schedule 4 for a validated mill/year — the whole check in one call.
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
  public Schedule4CheckStatusResponse checkStatus(long millId, int year) {
    return resolve(schedule4Service.checkStatus(millId, year));
  }

  /**
   * Resolve an already-evaluated result.
   *
   * <p>The per-location met message takes the LOCATION NAME as its {@code {0}} argument — legacy
   * passes the name, not an ordinal, unlike Schedule 6's {@code rowCounter}. Do not normalize the
   * two: each argument is legacy-specific to its own schedule.
   *
   * @param raw the service verdict, carrying bundle keys only
   * @return the same verdict with resolved text
   */
  public Schedule4CheckStatusResponse resolve(Schedule4CheckStatusResponse raw) {
    List<MessageInfo> scheduleMessages =
        raw.messages().stream().map(m -> message(m.key())).toList();
    List<LocationCheckResult> locations =
        raw.locations().stream()
            .map(
                location ->
                    new LocationCheckResult(
                        location.id(),
                        location.name(),
                        location.met(),
                        location.messages().stream()
                            .map(m -> message(m.key(), location.name()))
                            .toList(),
                        location.issues().stream()
                            .map(
                                issue ->
                                    new FieldIssue(issue.code(), message(issue.message().key())))
                            .toList()))
            .toList();
    return new Schedule4CheckStatusResponse(raw.outcome(), scheduleMessages, locations);
  }

  /**
   * Resolve a legacy bundle key to verbatim text, substituting any positional args and echoing the
   * key when it is absent — Schedule 4's existing behaviour, kept deliberately (Schedules 5, 6 and
   * 10 resolve with no default on purpose; Story 15.0 preserves both).
   */
  private MessageInfo message(String key, Object... args) {
    return new MessageInfo(
        key, messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale()));
  }
}
