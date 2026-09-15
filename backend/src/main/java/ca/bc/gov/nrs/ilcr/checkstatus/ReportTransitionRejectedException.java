package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The transition guard refused: the track is not at the status the transition starts from &mdash; a
 * Submit against a track already Submitted or Verified, or at the dead {@code O} (UC-CHK-002 S08,
 * BR-06). Maps to HTTP 409 with the verbatim legacy {@code reportSubmissionErrorMsg}: legacy's
 * guard returned {@code false}, {@code ILCRService.submitReport():720-722} threw {@code
 * SCHEDULE_NOT_SUBMITTED}, and the bean showed this generic text ({@code CheckStatusMB:289-292}).
 * Unreachable from the page &mdash; Submit is disabled and {@code canSubmit} is false off-Draft
 * &mdash; so only a forged or racing request ever sees it, and parity wins over a friendlier
 * message.
 */
public class ReportTransitionRejectedException extends BusinessException {

  public ReportTransitionRejectedException() {
    super(HttpStatus.CONFLICT, "reportSubmissionErrorMsg");
  }
}
