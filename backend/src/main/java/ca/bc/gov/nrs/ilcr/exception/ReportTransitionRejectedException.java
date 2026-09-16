package ca.bc.gov.nrs.ilcr.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a report-status transition is refused because the track is not in a state the target
 * can be reached from — a no-op, or one of the two illegal direct Draft&harr;Verified jumps. Maps
 * to 409 with the verbatim {@code reportSubmissionErrorMsg}.
 *
 * <p>Legacy detected exactly this and then lost it: the DAO returned {@code false} without rolling
 * back and its caller discarded the return value, so the screen showed the success message for a
 * transition that never happened. The refusal is surfaced truthfully here.
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
