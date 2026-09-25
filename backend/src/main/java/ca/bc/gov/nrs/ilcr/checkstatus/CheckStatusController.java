package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.api.CheckStatusApi;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.SetTrackStatusResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.TrackCheckResult;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.VerifyReportResponse;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.security.ReportSubmission;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Check Status endpoints (Stories 15.1, 15.3, 17.1, 18.1, 26.1 and 26.3). Each authorizes by
 * naming an action (AD-7) — {@code VIEW_SCHEDULE} for the sweep, exactly as the twelve per-schedule
 * check-status endpoints do, {@code SUBMIT_REPORT} for the licensee's submit on either track and
 * {@code SET_REPORT_STATUS} for the ministry's transitions (verify on either track and both
 * reversals; one action, not one per button — see {@code Action.SET_REPORT_STATUS}) — and delegates
 * ALL mill/year validation to {@link MillContextService} as its first line (AD-4). Mill scope
 * normally arrives with it: {@code validateMillYearActive}'s first statement is {@code
 * validateMillAccess}. Submit adds one action-specific check for a dual-role ADMIN+SUBMITTER
 * because ADMIN's browsing scope must not widen SUBMITTER's submission scope. There is no {@code
 * isAuthenticated()}: the epic's "any signed-in user" phrasing is imprecise and is not the spec
 * (FR2 requires role AND mill scope).
 *
 * <p>This is the ONE guard the sweep owns (AC 9). None of the twelve in-process validations checks
 * its own context, and six of them report an absent or closed mill-year as a vacuous MET — so the
 * guard runs here, once, before any schedule is touched, and the service adds none. The submit path
 * runs the same guard once, here; inside its transaction the locked status read is the existence
 * check.
 */
@RestController
public class CheckStatusController implements CheckStatusApi {

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;
  private final ReportTrackTransitionService transitionService;
  private final ReportSubmission reportSubmission;
  private final MessageSource messageSource;

  /** Wires the mill/year guard, the sweep, the one transition service and the offer rule. */
  public CheckStatusController(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportTrackTransitionService transitionService,
      ReportSubmission reportSubmission,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.transitionService = transitionService;
    this.reportSubmission = reportSubmission;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<CheckStatusSweepResponse> checkStatus(
      String millId, String year, Authentication authentication) {
    try {
      // Read-only (AD-5): context guard first — 400 ERR-001 / 404 / 409 — then evaluate.
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      CheckStatusSweepResponse sweep = sweepService.sweep(context.millId(), context.year());
      // One offer rule for both tracks, each against its own status code (legacy
      // canUserSubmitReport(boolean):502-517 took the track as its argument).
      TrackCheckResult schedules1To10 = sweep.schedules1To10();
      TrackCheckResult schedule11 = sweep.schedule11();
      return ResponseEntity.ok(
          new CheckStatusSweepResponse(
              sweep.millId(),
              sweep.year(),
              schedules1To10.withCanSubmit(
                  reportSubmission.canSubmit(
                      authentication, schedules1To10.statusCode(), context.millId())),
              schedule11.withCanSubmit(
                  reportSubmission.canSubmit(
                      authentication, schedule11.statusCode(), context.millId()))));
    } catch (ScheduleNotFoundException notFound) {
      throw checkStatusNotFound(notFound);
    }
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SUBMIT_REPORT')")
  public ResponseEntity<MessageResponse> submit(
      String millId, String year, Authentication authentication) {
    return submitOnTrack(ScheduleTrack.SCHEDULES_1_TO_10, millId, year, authentication);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SUBMIT_REPORT')")
  public ResponseEntity<MessageResponse> submitSchedule11(
      String millId, String year, Authentication authentication) {
    return submitOnTrack(ScheduleTrack.SCHEDULE_11, millId, year, authentication);
  }

  /**
   * Both submits, which differ only in the track they name: the same guard in the same order, the
   * same Submit-specific mill scope, one service method.
   */
  private ResponseEntity<MessageResponse> submitOnTrack(
      ScheduleTrack track, String millId, String year, Authentication authentication) {
    try {
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      reportSubmission.validateSubmitterMillAccess(authentication, context.millId());
      String key =
          transitionService.submit(
              track, context.millId(), context.year(), authentication, authentication.getName());
      return ResponseEntity.ok(new MessageResponse(message(key)));
    } catch (ScheduleNotFoundException notFound) {
      throw checkStatusNotFound(notFound);
    }
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SET_REPORT_STATUS')")
  public ResponseEntity<VerifyReportResponse> verifySchedules1To10(
      String millId, String year, Authentication authentication) {
    return verifyOnTrack(ScheduleTrack.SCHEDULES_1_TO_10, millId, year, authentication);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SET_REPORT_STATUS')")
  public ResponseEntity<VerifyReportResponse> verifySchedule11(
      String millId, String year, Authentication authentication) {
    return verifyOnTrack(ScheduleTrack.SCHEDULE_11, millId, year, authentication);
  }

  /**
   * Both verifies, which differ only in the track they name. Same guard, same order as the sweep:
   * 400 ERR-001 / 404 / 409 before anything is written. No submitter mill-scope check, for the
   * reason {@link #reverse} gives: verifying is an ADMIN capability and ADMIN is all-mills.
   */
  private ResponseEntity<VerifyReportResponse> verifyOnTrack(
      ScheduleTrack track, String millId, String year, Authentication authentication) {
    try {
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      String status =
          transitionService.verify(
              track,
              context.millId(),
              context.year(),
              authentication.getName(),
              directoryGuid(authentication));
      return ResponseEntity.ok(
          new VerifyReportResponse(status, message(TrackTransition.VERIFY.successKey(track))));
    } catch (ScheduleNotFoundException notFound) {
      throw checkStatusNotFound(notFound);
    }
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SET_REPORT_STATUS')")
  public ResponseEntity<SetTrackStatusResponse> setSchedules1To10ToDraft(
      String millId, String year, Authentication authentication) {
    return reverse(TrackTransition.SET_TO_DRAFT, millId, year, authentication);
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SET_REPORT_STATUS')")
  public ResponseEntity<SetTrackStatusResponse> setSchedules1To10ToSubmit(
      String millId, String year, Authentication authentication) {
    return reverse(TrackTransition.SET_TO_SUBMIT, millId, year, authentication);
  }

  /**
   * Both admin reversals, which differ only in the transition they name (Story 18.1). Same guard
   * and same order as the sweep and verify: 400 ERR-001 / 404 / 409 before anything is written.
   *
   * <p>No {@code ReportSubmission.validateSubmitterMillAccess} call, unlike {@link #submit}. That
   * extra check exists because a dual-role ADMIN+SUBMITTER must not use ADMIN's browsing scope to
   * submit outside SUBMITTER's assignment; reversing is an ADMIN capability and ADMIN is all-mills
   * ({@code MillContextService:81-83}), so applying it here would refuse a ministry user acting
   * within their own authority.
   *
   * <p>No directory GUID is resolved either: neither reversal records an identity pair (D1), so
   * there is no {@code ILCR_MILL_USER_XREF} row to look up. The audit name alone is passed.
   */
  private ResponseEntity<SetTrackStatusResponse> reverse(
      TrackTransition transition, String millId, String year, Authentication authentication) {
    try {
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      String status =
          transitionService.reverse(
              context.millId(), context.year(), transition, authentication.getName());
      return ResponseEntity.ok(
          new SetTrackStatusResponse(
              status, message(transition.successKey(ScheduleTrack.SCHEDULES_1_TO_10))));
    } catch (ScheduleNotFoundException notFound) {
      throw checkStatusNotFound(notFound);
    }
  }

  /**
   * The acting user's directory GUID, which the report's auditor cross-reference is keyed by, or
   * null when the principal carries no directory identity to record.
   *
   * <p>Resolves both principal shapes, as {@link
   * ca.bc.gov.nrs.ilcr.millcontext.MillContextController} does for this same {@code
   * ILCR_MILL_USER_XREF} key: a real token under security-on, and {@code MockUserPrincipal} with
   * security off, which {@code MockPrincipalFilter} seeds precisely so the GUID-scoped queries are
   * exercised in dev/UAT and the e2e suite. Handling only the token would have written NULL into
   * both auditor columns in every environment the e2e suite runs against.
   *
   * <p>Blank is normalized to null: {@link JwtPrincipalUtil#getIdpUserId} returns {@code ""}, never
   * null, when {@code custom:idp_user_id} is absent, so without this the documented
   * no-directory-identity short-circuit would be dead for every real token and a pointless
   * cross-reference lookup on an empty GUID would run instead.
   */
  private static String directoryGuid(Authentication authentication) {
    String guid = null;
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      guid = JwtPrincipalUtil.getIdpUserId(jwtAuth);
    } else if (authentication != null
        && authentication.getPrincipal() instanceof MockUserPrincipal mock) {
      guid = mock.userGuid();
    }
    return guid == null || guid.isBlank() ? null : guid;
  }

  /**
   * "Schedules not found" is this page's own semantics (UC-CHK-001 S06). Legacy renders {@code
   * checkStatusScheduleNotFoundErrorMsg} both when the context has no status row and when any
   * schedule's read reports its data missing ({@code CheckStatusMB.init():116-123}), never the
   * single schedule page's "Schedule not found." — so the shared guard's 404 is re-keyed here on
   * both endpoints, and a schedule's own would be too. No schedule throws one today (all made
   * checkable-when-unsaved by defect #296); the translation is what keeps this true if one ever
   * does.
   */
  private static CheckStatusScheduleNotFoundException checkStatusNotFound(
      ScheduleNotFoundException cause) {
    return new CheckStatusScheduleNotFoundException(cause);
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for the success envelope. */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
