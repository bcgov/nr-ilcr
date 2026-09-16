package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a report-status transition is attempted while one or more of the track's schedules
 * fail validation — the gate legacy re-ran at the moment of the transition, not merely at the point
 * the button was rendered. Maps to 409 with the verbatim {@code reportNotSubmittedErrorMsg}.
 *
 * <p>Legacy reused that key for a second, unrelated cause: its post-commit session refresh
 * returning false, which it reported <em>alongside</em> the success message. Only the gate-failure
 * meaning is carried forward; the other half was a defect and is not reproduced.
 *
 * <p>409 rather than 422 is a story-level decision (Story 17.1): the request is well formed and the
 * conflict is with the current state of the report, which is how this codebase already answers
 * {@link ScheduleNotEditableException} and {@link StaleRevisionException}.
 */
public class ReportNotSubmittedException extends BusinessException {
  public ReportNotSubmittedException() {
    super(HttpStatus.CONFLICT, "reportNotSubmittedErrorMsg");
  }
}
