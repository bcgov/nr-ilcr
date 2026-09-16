package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.api.CheckStatusApi;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.VerifyReportResponse;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.security.MockUserPrincipal;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Check Status endpoint (Story 15.1). Authorizes by naming the action (AD-7) — {@code
 * VIEW_SCHEDULE}, exactly as the twelve per-schedule check-status endpoints do — and delegates ALL
 * mill/year validation to {@link MillContextService} as its first line (AD-4). Mill scope arrives
 * with it: {@code validateMillYearActive}'s first statement is {@code validateMillAccess}, so a
 * submitter sweeping another mill's report is 403'd without any check here. Nothing else is
 * re-checked in this class, and there is no {@code isAuthenticated()}: the epic's "any signed-in
 * user" phrasing is imprecise and is not the spec (FR2 requires role AND mill scope).
 *
 * <p>This is the ONE guard the sweep owns (AC 9). None of the twelve in-process validations checks
 * its own context, and six of them report an absent or closed mill-year as a vacuous MET — so the
 * guard runs here, once, before any schedule is touched, and the service adds none.
 */
@RestController
public class CheckStatusController implements CheckStatusApi {

  private static final String VERIFIED_MESSAGE_KEY = "sch1-10VerifiedMsg";

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;
  private final ReportTransitionService transitionService;
  private final MessageSource messageSource;

  /** Wires the mill/year guard, the sweep, the transition and message resolution. */
  public CheckStatusController(
      MillContextService millContextService,
      CheckStatusSweepService sweepService,
      ReportTransitionService transitionService,
      MessageSource messageSource) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
    this.transitionService = transitionService;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<CheckStatusSweepResponse> checkStatus(String millId, String year) {
    try {
      // Read-only (AD-5): context guard first — 400 ERR-001 / 404 / 409 — then evaluate.
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      return ResponseEntity.ok(sweepService.sweep(context.millId(), context.year()));
    } catch (ScheduleNotFoundException notFound) {
      // "Schedules not found" is this page's own semantics (UC-CHK-001 S06). Legacy renders
      // checkStatusScheduleNotFoundErrorMsg both when the context has no status row and when any
      // schedule's read reports its data missing (CheckStatusMB.init():116-123), never the single
      // schedule page's "Schedule not found." — so the shared guard's 404 is re-keyed here, and a
      // schedule's own would be too. No schedule throws one today (all made checkable-when-unsaved
      // by defect #296); the translation is what keeps this true if one ever does.
      throw new CheckStatusScheduleNotFoundException(notFound);
    }
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'SET_REPORT_STATUS')")
  public ResponseEntity<VerifyReportResponse> verifySchedules1To10(
      String millId, String year, Authentication authentication) {
    try {
      // Same guard, same order as the sweep: 400 ERR-001 / 404 / 409 before anything is written.
      MillYearContext context = millContextService.validateMillYearActive(millId, year);
      String status =
          transitionService.verifySchedules1To10(
              context.millId(),
              context.year(),
              authentication.getName(),
              directoryGuid(authentication));
      return ResponseEntity.ok(new VerifyReportResponse(status, message(VERIFIED_MESSAGE_KEY)));
    } catch (ScheduleNotFoundException notFound) {
      // Re-keyed for the same reason the sweep re-keys it: this page has its own not-found text.
      throw new CheckStatusScheduleNotFoundException(notFound);
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
   * Resolves a legacy bundle key to its verbatim text, because the API returns text (AD-8).
   *
   * <p>The 4-argument form with the key as its own default, which is the house idiom: this runs
   * <em>after</em> the transactional transition has committed, so the 3-argument form's {@code
   * NoSuchMessageException} would answer a succeeded sign-off with a 500 — the same class of lie as
   * the legacy silent-success defect this endpoint exists to fix.
   */
  private MessageInfo message(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }
}
