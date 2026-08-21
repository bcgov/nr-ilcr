package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule10.api.Schedule10Api;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPageRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule10.dto.PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailCheckResult;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule 10 endpoints. Authorizes by naming the action — {@code VIEW_SCHEDULE} for the read and
 * for Check Status, {@code EDIT_SCHEDULE} for every write — delegates ALL mill/year validation to
 * {@link MillContextService} as its first line, and never touches repositories directly. The server
 * is the sole authority for {@code editable}; derivation, rules and assembly live in {@link
 * Schedule10Service}.
 *
 * <p>The guard is {@code validateMillYearActive}, deliberately NOT {@code
 * validateScheduleViewable(millId, year, "10")}. The latter additionally requires an {@code
 * ILCR_REPORT_SUMMARY} row for the category, and Schedule 10 has none — only categories 1, 2 and 3
 * do. Using the summary-requiring guard would 404 every single request.
 *
 * <p><strong>Message text is resolved here, never in the service.</strong> Domain code carries
 * bundle keys and format arguments; this class turns them into the verbatim strings the client
 * renders, which keeps every user-facing byte in one place.
 */
@RestController
public class Schedule10Controller implements Schedule10Api {

  /** Emitted after a successful create, edit or copy. */
  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";

  /**
   * Emitted after a successful delete. Legacy showed nothing at all; the house envelope adds this.
   */
  private static final String MSG_DELETED = "dataDeletedSuccesfullyInfoMsg";

  /** The single schedule-level banner when every checked requirement passes. */
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";

  private final MillContextService millContextService;
  private final Schedule10Service schedule10Service;
  private final SchedulePermissions permissions;
  private final MessageSource messageSource;

  /**
   * Wires the mill/year guard, the domain service, permissions and the message bundle.
   *
   * @param millContextService the single owner of mill/year validation
   * @param schedule10Service the domain service
   * @param permissions the action-based permission component
   * @param messageSource the one message bundle, keyed by legacy property keys
   */
  public Schedule10Controller(
      MillContextService millContextService,
      Schedule10Service schedule10Service,
      SchedulePermissions permissions,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.schedule10Service = schedule10Service;
    this.permissions = permissions;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule10Response> getSchedule10(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    boolean callerMayEdit = permissions.hasPermission(authentication, "EDIT_SCHEDULE");
    return ResponseEntity.ok(
        schedule10Service.getSchedule10(context.millId(), context.year(), callerMayEdit));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> addPage(
      String millId, String year, ConstructionPageRequest request, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return saved(
        schedule10Service.addPage(
            context.millId(),
            context.year(),
            request,
            authentication.getName(),
            mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> updatePage(
      int pageId,
      String millId,
      String year,
      ConstructionPageRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return saved(
        schedule10Service.updatePage(
            context.millId(),
            context.year(),
            pageId,
            request,
            authentication.getName(),
            mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> copyPage(
      int pageId, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return saved(
        schedule10Service.copyPage(
            context.millId(),
            context.year(),
            pageId,
            authentication.getName(),
            mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> deletePage(
      int pageId, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    // No user is threaded into a delete: it stamps nothing, because the row is gone.
    return deleted(
        schedule10Service.deletePage(
            context.millId(), context.year(), pageId, mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> addRoadDetail(
      int pageId,
      String millId,
      String year,
      RoadDetailRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return saved(
        schedule10Service.addRoadDetail(
            context.millId(),
            context.year(),
            pageId,
            request,
            authentication.getName(),
            mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> updateRoadDetail(
      int pageId,
      int roadDetailId,
      String millId,
      String year,
      RoadDetailRequest request,
      Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return saved(
        schedule10Service.updateRoadDetail(
            context.millId(),
            context.year(),
            pageId,
            roadDetailId,
            request,
            authentication.getName(),
            mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_SCHEDULE')")
  public ResponseEntity<Schedule10Response> deleteRoadDetail(
      int pageId, int roadDetailId, String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    return deleted(
        schedule10Service.deleteRoadDetail(
            context.millId(), context.year(), pageId, roadDetailId, mayEdit(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<Schedule10CheckStatusResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    MillYearContext context = millContextService.validateMillYearActive(millId, year);
    Schedule10CheckStatus.Outcome outcome =
        schedule10Service.checkStatus(context.millId(), context.year());
    return ResponseEntity.ok(compose(outcome));
  }

  private boolean mayEdit(Authentication authentication) {
    return permissions.hasPermission(authentication, "EDIT_SCHEDULE");
  }

  private ResponseEntity<Schedule10Response> saved(Schedule10Response document) {
    return ResponseEntity.ok(document.withMessage(message(MSG_SAVED)));
  }

  private ResponseEntity<Schedule10Response> deleted(Schedule10Response document) {
    return ResponseEntity.ok(document.withMessage(message(MSG_DELETED)));
  }

  /**
   * Turns the rule outcome into the wire response, composing every verbatim line.
   *
   * <p>Two mutually exclusive branches, mirroring legacy: a pass emits the single banner and NO
   * per-page results, because legacy's pass branch never enters its loop; anything outstanding
   * emits no banner and every visible page and road detail.
   */
  private Schedule10CheckStatusResponse compose(Schedule10CheckStatus.Outcome outcome) {
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
