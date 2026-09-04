package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.checkstatus.api.CheckStatusApi;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  private final MillContextService millContextService;
  private final CheckStatusSweepService sweepService;

  /** Wires the mill/year guard and the sweep. */
  public CheckStatusController(
      MillContextService millContextService, CheckStatusSweepService sweepService) {
    this.millContextService = millContextService;
    this.sweepService = sweepService;
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
}
