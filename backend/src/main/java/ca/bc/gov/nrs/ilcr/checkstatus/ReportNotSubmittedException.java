package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The validation gate refused: at least one of the eleven Schedules 1&ndash;10 validations is not
 * met (UC-CHK-002 S03, BR-02). Maps to HTTP 409 with the transition's {@link
 * TrackTransition#gateFailedKey()}. Nothing has been written when this is raised &mdash; for submit
 * the gate runs inside the write transaction, after the status row is locked and before any UPDATE;
 * for verify and the two reversals it runs before the write transaction opens.
 *
 * <p>Submit and verify carry legacy's verbatim {@code reportNotSubmittedErrorMsg}, the text {@code
 * CheckStatusMB.submitReport():294} showed when its eleven-term {@code &&} was false. The two Story
 * 18.1 reversals carry their own (deviation (V)): legacy reused that one sentence for every
 * transition, so a ministry user clicking <em>Set to Draft</em> was told their <em>submission</em>
 * had failed. See {@link TrackTransition#gateFailedKey()} for why the gate itself did not change.
 */
public class ReportNotSubmittedException extends BusinessException {

  /** Legacy's text, for a caller with no transition to name. */
  public ReportNotSubmittedException() {
    this(null);
  }

  /**
   * A gate refusal named by the transition that was attempted.
   *
   * @param transition the transition the gate refused; null falls back to legacy's generic text
   */
  public ReportNotSubmittedException(TrackTransition transition) {
    super(
        HttpStatus.CONFLICT,
        transition == null ? "reportNotSubmittedErrorMsg" : transition.gateFailedKey());
  }
}
