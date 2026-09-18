package ca.bc.gov.nrs.ilcr.checkstatus;

import java.util.Optional;

/**
 * The four report-status transitions a track can make, keyed by (from, to) exactly as legacy keyed
 * them &mdash; one enum standing in for the two legacy tables that decided every button on the
 * Check Status page.
 *
 * <p>Legacy {@code SubmitReportDAO} guarded a transition in two places. {@code
 * isMillReportStatusValid():448-463} rejected only a same-code no-op and a direct {@code
 * D}&harr;{@code V} jump; then {@code setCategoryStateCode():465-487} mapped the concatenated pair
 * to the category state the transition writes on every {@code ILCR_REPORT_CATEGORY} row &mdash;
 * {@code DS}&rarr;{@code A}, {@code SV}&rarr;{@code V}, {@code VS}&rarr;{@code A}, {@code
 * SD}&rarr;{@code D} &mdash; and for any other pair produced {@code null}, which the NOT NULL
 * {@code CATEGORY_STATE_CODE} column refused at commit. So the set of transitions legacy could ever
 * COMPLETE is exactly these four, and that is what {@link #resolve} admits: the guard's two
 * rejections plus every pair the map does not name (the dead {@code O} status included). Same
 * observable outcome &mdash; an error and no transition &mdash; without the partial write.
 *
 * <p>Only {@link #SUBMIT} is exposed by an endpoint today (Story 15.3). The others are declared so
 * Verify (Story 17.1) and the two admin reversals (Story 18.1) add a controller method and a status
 * write, not a second guard. Schedule 11 (Story 26.1) reuses the same enum against {@code
 * MILL_SILVICULTUR_STATUS_CODE} with one category and one row family.
 */
public enum TrackTransition {
  /** Draft &rarr; Submitted: the Licensee hands the report to the ministry (this story). */
  SUBMIT("D", "S", "A", Recorded.LICENSEE, "sch1-10SubmittedMsg", "submitNotDraftErrorMsg"),
  /** Submitted &rarr; Verified: the ministry signs the report off (Story 17.1). */
  VERIFY("S", "V", "V", Recorded.AUDITOR, "sch1-10VerifiedMsg", "verifyNotSubmittedErrorMsg"),
  /** Submitted &rarr; Draft: the ministry hands the report back (Story 18.1). */
  SET_TO_DRAFT(
      "S", "D", "D", Recorded.AUDITOR, "sch1-10DraftMsg", "setToDraftNotSubmittedErrorMsg"),
  /** Verified &rarr; Submitted: the ministry withdraws a verification (Story 18.1). */
  SET_TO_SUBMIT(
      "V", "S", "A", Recorded.NONE, "sch1-10SubmittedMsg", "setToSubmitNotVerifiedErrorMsg");

  /**
   * Which status-row identity pair a transition records &mdash; legacy {@code
   * updateILCRMillReportStatus():403-413} wrote the caller's {@code ILCR_MILL_USER_XREF} row into
   * the {@code LICENSEE_*} columns on a Submit and into the {@code AUDITOR_*} columns on every
   * other non-Draft transition, and nothing on Set to Draft.
   */
  public enum Recorded {
    /** {@code LICENSEE_MILL_ID} / {@code LICENSEE_USER_GUID}. */
    LICENSEE,
    /** {@code AUDITOR_MILL_ID} / {@code AUDITOR_USER_GUID} (written by Story 17.1). */
    AUDITOR,
    /** Neither pair. */
    NONE
  }

  private final String from;
  private final String to;
  private final String categoryState;
  private final Recorded recorded;
  private final String successKey;
  private final String rejectedKey;

  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      String successKey,
      String rejectedKey) {
    this.from = from;
    this.to = to;
    this.categoryState = categoryState;
    this.recorded = recorded;
    this.successKey = successKey;
    this.rejectedKey = rejectedKey;
  }

  /**
   * The transition legacy could complete from one status code to another, or empty when legacy
   * refused it &mdash; a no-op, a direct {@code D}&harr;{@code V} jump, or any pair its category
   * map did not name (the dead {@code O} status, a null code).
   *
   * @param from the track's current status code; may be null
   * @param to the requested status code; may be null
   * @return the transition, or empty when the pair is not one of the four legacy could commit
   */
  public static Optional<TrackTransition> resolve(String from, String to) {
    for (TrackTransition transition : values()) {
      if (transition.from.equals(from) && transition.to.equals(to)) {
        return Optional.of(transition);
      }
    }
    return Optional.empty();
  }

  /** The status code the track must currently hold. */
  public String from() {
    return from;
  }

  /** The status code the track moves to. */
  public String to() {
    return to;
  }

  /** The {@code CATEGORY_STATE_CODE} written on every category row of the track. */
  public String categoryState() {
    return categoryState;
  }

  /** Which identity pair the status row records for this transition. */
  public Recorded recorded() {
    return recorded;
  }

  /** The legacy bundle key of the success message. */
  public String successKey() {
    return successKey;
  }

  /**
   * The bundle key of the message shown when the track is no longer at {@link #from()} &mdash;
   * "Schedules 1-10 are no longer in Draft and cannot be submitted." for {@link #SUBMIT}. Legacy
   * had no such text: its guard fell through to the generic {@code reportSubmissionErrorMsg}
   * ("contact ILCR application support"), which told a user who had merely double-clicked, or whose
   * colleague had just submitted, nothing about what happened. Named per transition so each says
   * which status the track has left and which action that rules out (Story 15.4, ruled by the
   * business 2026-09-17).
   */
  public String rejectedKey() {
    return rejectedKey;
  }
}
