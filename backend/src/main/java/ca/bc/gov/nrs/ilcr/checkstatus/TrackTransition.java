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
 * <p>All four are exposed by an endpoint: Submit (Story 15.3), Verify (Story 17.1) and the two
 * admin reversals (Story 18.1). Each added a controller method and a status write, not a second
 * guard. Schedule 11 (Story 26.1) reuses the same enum against {@code MILL_SILVICULTUR_STATUS_CODE}
 * with one category and one row family.
 */
public enum TrackTransition {
  /** Draft &rarr; Submitted: the Licensee hands the report to the ministry (Story 15.3). */
  SUBMIT(
      "D",
      "S",
      "A",
      Recorded.LICENSEE,
      "sch1-10SubmittedMsg",
      "submitNotDraftErrorMsg",
      GateKeys.GENERIC),
  /** Submitted &rarr; Verified: the ministry signs the report off (Story 17.1). */
  VERIFY(
      "S",
      "V",
      "V",
      Recorded.AUDITOR,
      "sch1-10VerifiedMsg",
      "verifyNotSubmittedErrorMsg",
      GateKeys.GENERIC),
  /** Submitted &rarr; Draft: the ministry hands the report back (Story 18.1). */
  SET_TO_DRAFT(
      "S",
      "D",
      "D",
      Recorded.NONE,
      "sch1-10DraftMsg",
      "setToDraftNotSubmittedErrorMsg",
      "setToDraftNotValidErrorMsg"),
  /** Verified &rarr; Submitted: the ministry withdraws a verification (Story 18.1). */
  SET_TO_SUBMIT(
      "V",
      "S",
      "A",
      Recorded.NONE,
      "sch1-10SubmittedMsg",
      "setToSubmitNotVerifiedErrorMsg",
      "setToSubmitNotValidErrorMsg");

  /**
   * Legacy's one gate-failure text, kept by the two transitions for which it is accurate.
   *
   * <p>On a nested type rather than a field of this enum because a field declared after the
   * constants is an <em>illegal forward reference</em> from their initializers (JLS 8.3.3), and a
   * field cannot be declared before them. A nested class is initialized on first use, so the
   * constants may read it.
   */
  private static final class GateKeys {
    /** Legacy {@code messages.properties:119}, via {@code CheckStatusMB.submitReport():294}. */
    static final String GENERIC = "reportNotSubmittedErrorMsg";

    private GateKeys() {}
  }

  /**
   * Which status-row identity pair a transition records. Legacy {@code
   * updateILCRMillReportStatus():401-412} keyed this on the TARGET status code alone: a {@code 'D'}
   * target skipped the association block entirely ({@code :401}), a {@code 'S'} target wrote the
   * caller's {@code ILCR_MILL_USER_XREF} row into the {@code LICENSEE_*} columns ({@code
   * :406-407}), and any other non-{@code D} target wrote it into the {@code AUDITOR_*} columns
   * ({@code :409}).
   *
   * <p>So {@link #SET_TO_DRAFT} writes NEITHER pair, which is legacy exactly. {@link
   * #SET_TO_SUBMIT} is the one deliberate departure: legacy wrote the LICENSEE pair there from the
   * acting ADMIN's cross-reference, destroying the record of who actually submitted and storing
   * NULL whenever that admin has no assignment for the mill — the normal case for a ministry user.
   * Story 18.1 D1(a) ratified {@link Recorded#NONE} instead (recorded deviation (S)), which is what
   * {@code epics.md:2110} and PRD FR5 already specified.
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
  private final String gateFailedKey;

  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      String successKey,
      String rejectedKey,
      String gateFailedKey) {
    this.from = from;
    this.to = to;
    this.categoryState = categoryState;
    this.recorded = recorded;
    this.successKey = successKey;
    this.rejectedKey = rejectedKey;
    this.gateFailedKey = gateFailedKey;
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

  /**
   * The bundle key of the message shown when the eleven-schedule VALIDATION gate refuses this
   * transition &mdash; distinct from {@link #rejectedKey()}, which is about the track's STATUS.
   *
   * <p>{@link #SUBMIT} and {@link #VERIFY} keep legacy's one text, {@code
   * reportNotSubmittedErrorMsg} ("The report cannot be submitted. One or more of the Schedules have
   * not passed validation. Please review and correct any errors."). For submit that sentence is
   * simply true, and Story 17.1 ruled verify back onto legacy parity.
   *
   * <p>The two reversals do not, and that is a deliberate improvement ratified by the BA 2026-09-21
   * (deviation (V)). Legacy reused the same text there, so a ministry user clicking <em>Set to
   * Draft</em> on a report with errors was told their <em>submission</em> had failed &mdash; naming
   * neither the action they took nor the remedy. <strong>The gate itself is correct and
   * unchanged:</strong> a Submitted report can only acquire errors because a ministry user
   * introduced them, and ADMIN edit rights at Submitted ({@code ScheduleEditability.java:63-64})
   * let that user correct them in place and retry, so nothing is stranded. Only the wording
   * changes. Same split as deviation (T).
   */
  public String gateFailedKey() {
    return gateFailedKey;
  }
}
