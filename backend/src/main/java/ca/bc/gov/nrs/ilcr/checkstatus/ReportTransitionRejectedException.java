package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The transition guard refused: the track is not at the status the transition starts from &mdash; a
 * Submit against a track already Submitted or Verified, or at the dead {@code O} (UC-CHK-002 S08,
 * BR-06). Maps to HTTP 409 with the transition's own {@link TrackTransition#rejectedKey()} &mdash;
 * for a Submit, "Schedules 1-10 are no longer in Draft and cannot be submitted."
 *
 * <p>A deliberate departure from legacy, ruled by the business 2026-09-17 (Story 15.4). Legacy's
 * guard returned {@code false}, {@code ILCRService.submitReport():720-722} threw {@code
 * SCHEDULE_NOT_SUBMITTED}, and the bean showed the generic {@code reportSubmissionErrorMsg}
 * ("contact ILCR application support", {@code CheckStatusMB:289-292}) &mdash; harmless there,
 * because the page simply re-rendered. Here the row lock makes the loser of an ordinary double
 * click, or of a colleague's submit in another window, land on this exact response, and that user
 * has done nothing wrong; the message now says what happened. The 500 for a persistence failure
 * ({@link ReportSubmissionException}) keeps the legacy support text.
 *
 * <p>Without a transition to name, the refusal falls back to that same legacy text: a 409 with the
 * generic message is still the right answer, never a 500 from a null dereference.
 */
public class ReportTransitionRejectedException extends BusinessException {

  /** Legacy's generic guard text, the fallback when no transition names a more specific one. */
  static final String GENERIC_KEY = "reportSubmissionErrorMsg";

  /**
   * A refusal named by the transition that was attempted.
   *
   * @param transition the transition the track could not make; null falls back to the generic key
   */
  public ReportTransitionRejectedException(TrackTransition transition) {
    super(HttpStatus.CONFLICT, transition == null ? GENERIC_KEY : transition.rejectedKey());
  }

  /**
   * A refusal with legacy's generic text.
   *
   * @deprecated name the transition instead, so the message says which status the track has left;
   *     kept so callers that predate {@link TrackTransition#rejectedKey()} still compile
   */
  @Deprecated
  public ReportTransitionRejectedException() {
    this(null);
  }
}
