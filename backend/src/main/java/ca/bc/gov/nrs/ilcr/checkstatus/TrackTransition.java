package ca.bc.gov.nrs.ilcr.checkstatus;

import java.util.Objects;
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
 * guard.
 *
 * <p><strong>The track is a parameter, not a row.</strong> Schedule 11 reuses these same four
 * transitions against {@code MILL_SILVICULTUR_STATUS_CODE}, because legacy did: its guard and its
 * category map never knew which track they were on, and the track reached only the status-write
 * helper, as the boolean {@code isSchedule11Submitted} ({@code
 * updateILCRMillReportStatus:395-426}). From, to, category state and {@link Recorded} are therefore
 * track-independent and {@link #resolve} takes no track. What does differ per track is the wording
 * &mdash; legacy's bean picked {@code sch11*} or {@code sch1-10*} texts ({@code
 * CheckStatusMB:212-239} vs {@code :245-296}) &mdash; so every message accessor takes a {@link
 * ScheduleTrack}, and there is no default. A transition whose Schedule 11 story has not shipped
 * carries no Schedule 11 keys at all and throws when asked for one, rather than lending that track
 * the 1&ndash;10 sentence.
 */
public enum TrackTransition {
  /** Draft &rarr; Submitted: the Licensee hands the report to the ministry (Stories 15.3, 26.1). */
  SUBMIT(
      "D",
      "S",
      "A",
      Recorded.LICENSEE,
      new Keys("sch1-10SubmittedMsg", "submitNotDraftErrorMsg", GateKeys.GENERIC),
      new Keys("sch11SubmittedMsg", "sch11SubmitNotDraftErrorMsg", GateKeys.GENERIC)),
  /** Submitted &rarr; Verified: the ministry signs the report off (Story 17.1). */
  VERIFY(
      "S",
      "V",
      "V",
      Recorded.AUDITOR,
      new Keys("sch1-10VerifiedMsg", "verifyNotSubmittedErrorMsg", GateKeys.GENERIC),
      "Story 26.3"),
  /** Submitted &rarr; Draft: the ministry hands the report back (Story 18.1). */
  SET_TO_DRAFT(
      "S",
      "D",
      "D",
      Recorded.NONE,
      new Keys("sch1-10DraftMsg", "setToDraftNotSubmittedErrorMsg", "setToDraftNotValidErrorMsg"),
      "Story 26.5"),
  /**
   * Verified &rarr; Submitted: the ministry withdraws a verification (Story 18.1). Its {@link
   * Recorded#NONE} was argued for Schedules 1&ndash;10 (deviation (S)); whether Schedule 11 follows
   * is Story 26.5's to decide when it defines this row's Schedule 11 keys.
   */
  SET_TO_SUBMIT(
      "V",
      "S",
      "A",
      Recorded.NONE,
      new Keys(
          "sch1-10SubmittedMsg", "setToSubmitNotVerifiedErrorMsg", "setToSubmitNotValidErrorMsg"),
      "Story 26.5");

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

  /**
   * One track's three bundle keys for a transition: the 200 envelope, the 409 for a track that has
   * left {@link #from()}, and the 409 for a failed validation gate.
   *
   * @param success the success message key
   * @param rejected the "no longer in &hellip;" key
   * @param gateFailed the validation-gate refusal key
   */
  private record Keys(String success, String rejected, String gateFailed) {}

  private final String from;
  private final String to;
  private final String categoryState;
  private final Recorded recorded;
  private final Keys schedules1To10Keys;
  private final Keys schedule11Keys;
  private final String schedule11Owner;

  /** A transition live on both tracks. */
  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      Keys schedules1To10Keys,
      Keys schedule11Keys) {
    this(from, to, categoryState, recorded, schedules1To10Keys, schedule11Keys, null);
  }

  /** A transition live on Schedules 1&ndash;10 only, naming who owns its Schedule 11 texts. */
  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      Keys schedules1To10Keys,
      String schedule11Owner) {
    this(from, to, categoryState, recorded, schedules1To10Keys, null, schedule11Owner);
  }

  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      Keys schedules1To10Keys,
      Keys schedule11Keys,
      String schedule11Owner) {
    this.from = from;
    this.to = to;
    this.categoryState = categoryState;
    this.recorded = recorded;
    this.schedules1To10Keys = schedules1To10Keys;
    this.schedule11Keys = schedule11Keys;
    this.schedule11Owner = schedule11Owner;
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

  /**
   * Whether this transition carries message keys on the track &mdash; i.e. whether any endpoint may
   * perform it there.
   *
   * @param track the track
   * @return true when {@link #successKey}, {@link #rejectedKey} and {@link #gateFailedKey} answer
   *     for it
   */
  public boolean isDefinedOn(ScheduleTrack track) {
    return Objects.requireNonNull(track, "track") == ScheduleTrack.SCHEDULES_1_TO_10
        || schedule11Keys != null;
  }

  /**
   * The legacy bundle key of the success message on this track.
   *
   * @throws IllegalStateException the transition is not defined on the track ({@link #isDefinedOn})
   */
  public String successKey(ScheduleTrack track) {
    return keys(track).success();
  }

  /**
   * The bundle key of the message shown when the track is no longer at {@link #from()} &mdash;
   * "Schedules 1-10 are no longer in Draft and cannot be submitted." for {@link #SUBMIT}. Legacy
   * had no such text: its guard fell through to the generic {@code reportSubmissionErrorMsg}
   * ("contact ILCR application support"), which told a user who had merely double-clicked, or whose
   * colleague had just submitted, nothing about what happened. Named per transition so each says
   * which status the track has left and which action that rules out (Story 15.4, ruled by the
   * business 2026-09-17), and per track so it names the right schedules (deviation (AB) for
   * Schedule 11).
   *
   * @throws IllegalStateException the transition is not defined on the track ({@link #isDefinedOn})
   */
  public String rejectedKey(ScheduleTrack track) {
    return keys(track).rejected();
  }

  /**
   * The bundle key of the message shown when the track's VALIDATION gate refuses this transition
   * &mdash; distinct from {@link #rejectedKey}, which is about the track's STATUS.
   *
   * <p>{@link #SUBMIT} and {@link #VERIFY} keep legacy's one text, {@code
   * reportNotSubmittedErrorMsg} ("The report cannot be submitted. One or more of the Schedules have
   * not passed validation. Please review and correct any errors."). For submit that sentence is
   * simply true &mdash; on both tracks, and legacy used it on both ({@code CheckStatusMB:235},
   * {@code :294}) &mdash; and Story 17.1 ruled verify back onto legacy parity.
   *
   * <p>The two reversals do not, and that is a deliberate improvement ratified by the BA 2026-09-21
   * (deviation (V)). Legacy reused the same text there, so a ministry user clicking <em>Set to
   * Draft</em> on a report with errors was told their <em>submission</em> had failed &mdash; naming
   * neither the action they took nor the remedy. <strong>The gate itself is correct and
   * unchanged:</strong> a Submitted report can only acquire errors because a ministry user
   * introduced them, and ADMIN edit rights at Submitted ({@code ScheduleEditability.java:63-64})
   * let that user correct them in place and retry, so nothing is stranded. Only the wording
   * changes. Same split as deviation (T).
   *
   * @throws IllegalStateException the transition is not defined on the track ({@link #isDefinedOn})
   */
  public String gateFailedKey(ScheduleTrack track) {
    return keys(track).gateFailed();
  }

  private Keys keys(ScheduleTrack track) {
    if (!isDefinedOn(track)) {
      throw new IllegalStateException(
          this + " is not defined on " + track + "; its texts belong to " + schedule11Owner);
    }
    return track == ScheduleTrack.SCHEDULES_1_TO_10 ? schedules1To10Keys : schedule11Keys;
  }
}
