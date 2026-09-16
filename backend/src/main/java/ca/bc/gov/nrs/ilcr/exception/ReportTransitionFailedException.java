package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a report-status transition passed every guard but could not be persisted — the status
 * write or one of the audit sweeps failed unexpectedly. Maps to 500 with the verbatim {@code
 * reportSubmissionErrorMsg}, which is legacy's text for the same condition (its {@code
 * SCHEDULE_NOT_SUBMITTED} exception code).
 *
 * <p>Shares its message with {@link ReportTransitionRejectedException}; the two differ only in
 * status, so a caller distinguishing them must read the status code.
 */
public class ReportTransitionFailedException extends BusinessException {
  public ReportTransitionFailedException(Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "reportSubmissionErrorMsg");
    initCause(cause);
  }

  /**
   * A transition that reached the writes but would have left the report in a shape no screen could
   * explain &mdash; fewer category rows advanced than the track carries, say. The detail reaches
   * the log and the cause chain only: the client still gets the verbatim bundle text, because AD-8
   * owns the wire message.
   *
   * @param detail what was inconsistent, for the log
   */
  public ReportTransitionFailedException(String detail) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "reportSubmissionErrorMsg");
    initCause(new IllegalStateException(detail));
  }
}
