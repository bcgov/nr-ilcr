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
 * <p>All four are exposed by an endpoint on both tracks: Submit, Verify and the two admin
 * reversals. Each added a controller method and a status write, not a second guard.
 *
 * <p><strong>The track is a parameter, not a row.</strong> Schedule 11 reuses these same four
 * transitions against {@code MILL_SILVICULTUR_STATUS_CODE}, because legacy did: its guard and its
 * category map never knew which track they were on, and the track reached only the status-write
 * helper, as the boolean {@code isSchedule11Submitted} ({@code
 * updateILCRMillReportStatus:395-426}). From, to, category state and {@link Recorded} are therefore
 * track-independent and {@link #resolve} takes no track. What does differ per track is the wording
 * &mdash; legacy's bean picked {@code sch11*} or {@code sch1-10*} texts ({@code
 * CheckStatusMB:212-239} vs {@code :245-296}) &mdash; so every message accessor takes a {@link
 * ScheduleTrack}, and there is no default. Every transition carries both tracks' keys, so a
 * Schedule 11 screen can never borrow the 1&ndash;10 sentence.
 *
 * <p>The two tracks' reversal refusals read differently, and that is intended. Schedules 1&ndash;10
 * say which status the track has left and which button that rules out (deviations (T) and (V)).
 * Schedule 11 keeps legacy's generic texts, as its Submit gate and its Verify refusal already do:
 * legacy had no Schedule 11 reversal error of its own ({@code CheckStatusMB:234-235}, {@code
 * ILCSException:50}).
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
  /**
   * Submitted &rarr; Verified: the ministry signs the report off (Stories 17.1, 26.3).
   *
   * <p>Schedule 11's {@code rejectedKey} is legacy's generic {@code reportSubmissionErrorMsg}
   * because that is the text a refused Schedule 11 verify actually shows: legacy's DAO guard
   * returned {@code false}, which its service turned into {@code SCHEDULE_NOT_SUBMITTED}, and the
   * epic keeps it as legacy parity. The service reaches it without this key ({@code refusedVerify}
   * names no transition, on either track), so naming it here keeps the key and the text shown the
   * same, rather than declaring a second key nothing reaches beside 1&ndash;10's {@code
   * verifyNotSubmittedErrorMsg}.
   */
  VERIFY(
      "S",
      "V",
      "V",
      Recorded.AUDITOR,
      new Keys("sch1-10VerifiedMsg", "verifyNotSubmittedErrorMsg", GateKeys.GENERIC),
      new Keys("sch11VerifiedMsg", "reportSubmissionErrorMsg", GateKeys.GENERIC)),
  /**
   * Submitted &rarr; Draft: the ministry hands the report back. Schedule 11's success text is
   * legacy's {@code sch11DraftMsg} ({@code CheckStatusMB:226-227}).
   */
  SET_TO_DRAFT(
      "S",
      "D",
      "D",
      Recorded.NONE,
      new Keys("sch1-10DraftMsg", "setToDraftNotSubmittedErrorMsg", "setToDraftNotValidErrorMsg"),
      new Keys("sch11DraftMsg", "reportSubmissionErrorMsg", GateKeys.GENERIC)),
  /**
   * Verified &rarr; Submitted: the ministry withdraws a verification. {@link Recorded#NONE} on both
   * tracks (deviation (S)); on Schedule 11 legacy's LICENSEE write would also have overwritten the
   * record of who submitted Schedules 1&ndash;10, because the two tracks share that pair (as they
   * share the AUDITOR pair). Both tracks reuse their submit success text, as legacy did.
   */
  SET_TO_SUBMIT(
      "V",
      "S",
      "A",
      Recorded.NONE,
      new Keys(
          "sch1-10SubmittedMsg", "setToSubmitNotVerifiedErrorMsg", "setToSubmitNotValidErrorMsg"),
      new Keys("sch11SubmittedMsg", "reportSubmissionErrorMsg", GateKeys.GENERIC));

  /**
   * Legacy's one gate-failure text, kept by Submit and Verify on both tracks and by both Schedule
   * 11 reversals.
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

  TrackTransition(
      String from,
      String to,
      String categoryState,
      Recorded recorded,
      Keys schedules1To10Keys,
      Keys schedule11Keys) {
    this.from = from;
    this.to = to;
    this.categoryState = categoryState;
    this.recorded = recorded;
    // Every transition is defined on both tracks. A missing Keys fails here, at class load, by
    // name, rather than as an anonymous NPE inside successKey() on the first request.
    this.schedules1To10Keys = Objects.requireNonNull(schedules1To10Keys, "schedules1To10Keys");
    this.schedule11Keys = Objects.requireNonNull(schedule11Keys, "schedule11Keys");
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

  /** The legacy bundle key of the success message on this track. */
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
   * Schedule 11). Schedule 11's Verify and reversals keep legacy's generic text instead.
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
   * <p>The two 1&ndash;10 reversals do not, and that is a deliberate improvement ratified by the BA
   * 2026-09-21 (deviation (V)). Legacy reused the same text there, so a ministry user clicking
   * <em>Set to Draft</em> on a report with errors was told their <em>submission</em> had failed
   * &mdash; naming neither the action they took nor the remedy. <strong>The gate itself is correct
   * and unchanged:</strong> a Submitted report can only acquire errors because a ministry user
   * introduced them, and ADMIN edit rights at Submitted ({@code ScheduleEditability.java:63-64})
   * let that user correct them in place and retry, so nothing is stranded. Only the wording
   * changes. Same split as deviation (T). Schedule 11's reversals keep legacy's text, as its Submit
   * and Verify do.
   */
  public String gateFailedKey(ScheduleTrack track) {
    return keys(track).gateFailed();
  }

  private Keys keys(ScheduleTrack track) {
    return Objects.requireNonNull(track, "track") == ScheduleTrack.SCHEDULES_1_TO_10
        ? schedules1To10Keys
        : schedule11Keys;
  }
}
