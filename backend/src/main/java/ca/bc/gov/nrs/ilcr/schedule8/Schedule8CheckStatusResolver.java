package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleCheckResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the Schedule 8 Check Status keys the service emits into the verbatim text the client
 * renders (AD-8), for both the all-pages sweep and the single-page scope.
 *
 * <p>Moved out of a private {@code Schedule8Controller} method so Story 15.1's sweep can obtain a
 * fully-resolved Schedule 8 verdict in process instead of over HTTP (AD-5: the validation is
 * called, never re-implemented).
 */
@Component
@RequiredArgsConstructor
public class Schedule8CheckStatusResolver {

  private final Schedule8Service schedule8Service;
  private final MessageSource messageSource;

  /**
   * Evaluate and resolve the whole schedule for a validated mill/year — the all-pages sweep.
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
  public Schedule8CheckStatusResponse checkStatus(long millId, int year) {
    return resolve(schedule8Service.checkStatus(millId, year));
  }

  /**
   * Evaluate and resolve one page only (the S14 single-page scope).
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
   * @param pageId the page to check
   * @return the verdict for that page, with resolved text
   */
  public Schedule8CheckStatusResponse checkStatusPage(long millId, int year, int pageId) {
    return resolve(schedule8Service.checkStatusPage(millId, year, pageId));
  }

  /**
   * Resolve every emitted bundle key in an already-evaluated result to its verbatim text (AD-8).
   *
   * @param raw the service verdict, carrying bundle keys only
   * @return the same verdict with resolved text
   */
  public Schedule8CheckStatusResponse resolve(Schedule8CheckStatusResponse raw) {
    List<MessageInfo> messages = raw.messages().stream().map(m -> message(m.key())).toList();
    List<Schedule8PageCheckResult> pages =
        raw.pages().stream()
            .map(
                page ->
                    new Schedule8PageCheckResult(
                        page.id(),
                        page.met(),
                        resolveIssues(page.issues()),
                        page.samples().stream()
                            .map(
                                sample ->
                                    new Schedule8SampleCheckResult(
                                        sample.id(), sample.met(), resolveIssues(sample.issues())))
                            .toList()))
            .toList();
    return new Schedule8CheckStatusResponse(raw.outcome(), messages, pages);
  }

  private List<Schedule8CheckFieldIssue> resolveIssues(List<Schedule8CheckFieldIssue> issues) {
    return issues.stream()
        .map(i -> new Schedule8CheckFieldIssue(i.field(), message(i.message().key())))
        .toList();
  }

  /**
   * Resolve a legacy bundle key to verbatim text, echoing the key when it is absent — Schedule 8's
   * existing behaviour, kept deliberately (Schedules 5, 6 and 10 resolve with no default on
   * purpose; Story 15.0 preserves both).
   */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
