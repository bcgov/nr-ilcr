package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule10.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule10.dto.PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailCheckResult;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckStatusResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Assembles the Schedule 10 Check Status wire response from the rule outcome, composing every
 * verbatim line.
 *
 * <p><strong>Why this class exists (Story 15.0 AC 3).</strong> {@link
 * Schedule10Service#checkStatus} is a public method with a PACKAGE-PRIVATE return type — {@code
 * Schedule10CheckStatus.Outcome} — so no class outside {@code ca.bc.gov.nrs.ilcr.schedule10} could
 * name its result, and the only assembled {@link Schedule10CheckStatusResponse} in the tree was
 * built by a private {@code Schedule10Controller} method. For Story 15.1's sweep that was a COMPILE
 * error, not a runtime one, and {@code var} does not rescue it: resolving {@code outcome.met()}
 * needs access to the declaring type. Living inside the package, this class can name {@code
 * Outcome}; being public, it hands the finished DTO to any caller.
 *
 * <p><strong>Placement was a deliberate choice between two house conventions</strong>, which
 * disagree here. Schedules 2/4/5/6/8/10 follow "the service emits keys, the controller resolves";
 * Schedules 1/3/7A/7B/9/11 resolve inside the service. Story 15.0 took a third option for all six
 * of the first group: a resolver {@code @Component} in the schedule's own package. It keeps text
 * resolution OUT of the domain service (so {@code Schedule10Service} still needs no {@code
 * MessageSource}, and its "message text is resolved elsewhere" contract holds), while moving the
 * assembly out of the controller so it is reachable in process. Both conventions survive; neither
 * is bent to fit.
 */
@Component
@RequiredArgsConstructor
public class Schedule10CheckStatusResolver {

  /** The single schedule-level banner when every checked requirement passes. */
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";

  private final Schedule10Service schedule10Service;
  private final MessageSource messageSource;

  /**
   * Evaluate and resolve Schedule 10 for a validated mill/year — the whole check in one call.
   *
   * @param millId the mill id (context already validated by the caller)
   * @param year the reporting year
   * @return the verdict with every message's verbatim text populated
   */
  public Schedule10CheckStatusResponse checkStatus(long millId, int year) {
    return compose(schedule10Service.checkStatus(millId, year));
  }

  /**
   * Turns the rule outcome into the wire response, composing every verbatim line.
   *
   * <p>Two mutually exclusive branches, mirroring legacy: a pass emits the single banner and NO
   * per-page results, because legacy's pass branch never enters its loop; anything outstanding
   * emits no banner and every visible page and road detail.
   *
   * <p>Package-private by necessity, not by preference: {@code Outcome} is package-private, so a
   * public signature naming it would be unusable outside this package anyway. {@link
   * #checkStatus(long, int)} is the public door.
   */
  Schedule10CheckStatusResponse compose(Schedule10CheckStatus.Outcome outcome) {
    if (outcome.met()) {
      return new Schedule10CheckStatusResponse(
          Schedule10CheckStatusResponse.MET, List.of(message(MSG_REQUIREMENTS_MET)), List.of());
    }
    List<PageCheckResult> pages = outcome.pages().stream().map(this::composePage).toList();
    return new Schedule10CheckStatusResponse(
        Schedule10CheckStatusResponse.ISSUES, List.of(), pages);
  }

  private PageCheckResult composePage(Schedule10CheckStatus.PageOutcome page) {
    List<RoadDetailCheckResult> details =
        page.roadDetails().stream()
            .map(
                detail ->
                    new RoadDetailCheckResult(
                        detail.roadDetailId(),
                        detail.rowNumber(),
                        detail.roadDetailLabel(),
                        detail.issues().isEmpty(),
                        composeIssues(detail.issues())))
            .toList();
    boolean met = page.issues().isEmpty() && details.stream().allMatch(RoadDetailCheckResult::met);
    return new PageCheckResult(
        page.pageId(),
        page.pageNumber(),
        page.pageLabel(),
        met,
        composeIssues(page.issues()),
        details);
  }

  private List<FieldIssue> composeIssues(List<Schedule10CheckStatus.Issue> issues) {
    return issues.stream()
        .map(
            issue ->
                new FieldIssue(
                    issue.field(),
                    new MessageInfo(
                        issue.messageKey(),
                        issue.label()
                            + ": "
                            + resolve(issue.messageKey(), issue.args().toArray()))))
        .toList();
  }

  private MessageInfo message(String key) {
    return new MessageInfo(key, resolve(key));
  }

  /**
   * Resolves a bundle key with NO default.
   *
   * <p>Passing the key as its own fallback would ship a raw key to the user as though it were a
   * message, and every happy-path test would still pass — so a missing or renamed key must fail
   * loudly instead.
   */
  private String resolve(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }
}
