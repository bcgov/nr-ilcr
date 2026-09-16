package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a report-status transition is refused because the track is not in a state the target
 * can be reached from — a no-op, or one of the two illegal direct Draft&harr;Verified jumps. Maps
 * to 409 with the verbatim {@code reportSubmissionErrorMsg}.
 *
 * <p>This is legacy's own outcome, not a fix applied over it. {@code
 * SubmitReportDAO.isMillReportStatusValid:448} refuses the move and {@code submitReport:69-71}
 * returns {@code false} without writing; {@code ILCRService.submitReport:718-723} — {@code void} —
 * turns that into {@code ILCSException(SCHEDULE_NOT_SUBMITTED)}, which {@code ILCSException:50}
 * maps to this same key, and the bean's {@code catch} ({@code CheckStatusMB:289-292}) renders it as
 * an error instead of the verified message at {@code :284}. {@code UC-CHK-007-S07} describes a
 * silent success here, but that record stops at the bean and the DAO without reading the service.
 *
 * <p>Also raised for a stored NULL track code, where legacy threw {@code NullPointerException}
 * instead; that branch is unreachable in delivery, whose status columns are {@code NOT NULL}.
 *
 * <p>The message is legacy's support-escalation text, which reads oddly for a legitimate state
 * conflict but is kept verbatim for parity (AD-8). It is shared with {@link
 * ReportTransitionFailedException}, so the two are distinguishable only by status, never by body.
 */
public class ReportTransitionRejectedException extends BusinessException {
  public ReportTransitionRejectedException() {
    super(HttpStatus.CONFLICT, "reportSubmissionErrorMsg");
  }
}
