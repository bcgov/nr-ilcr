package ca.bc.gov.nrs.ilcr.schedule2;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2CheckStatusResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the Schedule 2 Check Status keys the service emits into the verbatim text the client
 * renders (AD-8).
 *
 * <p>This composition used to live in a private {@code Schedule2Controller} method, which meant the
 * only way to obtain a fully-resolved Schedule 2 verdict was an HTTP round trip. Story 15.1's
 * cross-schedule sweep needs it in process, and AD-5 says validation is never re-implemented — so
 * it moved here rather than being written a second time. The service still emits keys and only
 * keys; the single concatenation still happens in exactly one place.
 */
@Component
@RequiredArgsConstructor
public class Schedule2CheckStatusResolver {

  private final Schedule2Service schedule2Service;
  private final MessageSource messageSource;

  /**
   * Evaluate and resolve Schedule 2 for a validated mill/year — the whole check in one call.
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
  public Schedule2CheckStatusResponse checkStatus(long millId, int year) {
    return resolve(schedule2Service.checkStatus(millId, year));
  }

  /**
   * Resolve an already-evaluated result. The service carries an optional field label in {@code
   * MessageInfo.text}; when present it is prefixed as {@code "<label>: <resolvedText>"} (legacy
   * {@code Schedule2MB:168} + the Schedule 1 {@code valueRequired} parity).
   *
   * @param raw the service verdict, carrying bundle keys and optional labels
   * @return the same verdict with resolved text
   */
  public Schedule2CheckStatusResponse resolve(Schedule2CheckStatusResponse raw) {
    List<MessageInfo> resolved =
        raw.messages().stream()
            .map(
                m -> {
                  MessageInfo base = message(m.key());
                  return m.text() == null
                      ? base
                      : new MessageInfo(base.key(), m.text() + ": " + base.text());
                })
            .toList();
    return new Schedule2CheckStatusResponse(raw.outcome(), resolved);
  }

  /**
   * Resolve a legacy bundle key to verbatim text, echoing the key when it is absent — Schedule 2's
   * existing behaviour, kept deliberately. Schedules 5, 6 and 10 resolve with NO default and fail
   * loudly instead; that divergence is intentional and Story 15.0 preserves both sides of it rather
   * than homogenizing them.
   */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
