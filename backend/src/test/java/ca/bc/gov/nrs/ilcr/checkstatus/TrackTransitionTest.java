package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit test for {@link TrackTransition} — the guard table and the category-state map every status
 * transition shares (Story 15.3 AC 3/11, § Dev Notes 6). All sixteen (from, to) pairs over {@code
 * {D, S, V, O}} are pinned from legacy {@code SubmitReportDAO}: the four pairs its category map
 * named ({@code :465-487}) are the only ones that proceed; the four no-ops and the two {@code
 * D}↔{@code V} jumps its guard rejected ({@code :456-458}) and the six pairs the map left null are
 * all refused. Stories 17.1 and 18.1 add a controller method, not a second guard.
 */
class TrackTransitionTest {

  @ParameterizedTest(name = "{0} -> {1} is {2}, category state {3}")
  @DisplayName("the four transitions legacy could commit resolve, with legacy's category state")
  @CsvSource({
    "D, S, SUBMIT, A",
    "S, V, VERIFY, V",
    "V, S, SET_TO_SUBMIT, A",
    "S, D, SET_TO_DRAFT, D",
  })
  void legacyPairsResolve(String from, String to, TrackTransition expected, String categoryState) {
    Optional<TrackTransition> resolved = TrackTransition.resolve(from, to);

    assertThat(resolved).contains(expected);
    assertThat(resolved.orElseThrow().from()).isEqualTo(from);
    assertThat(resolved.orElseThrow().to()).isEqualTo(to);
    assertThat(resolved.orElseThrow().categoryState()).isEqualTo(categoryState);
  }

  @ParameterizedTest(name = "{0} -> {1} is refused")
  @DisplayName(
      "the twelve other pairs are refused: no-ops, D<->V jumps, and every pair off the map")
  @CsvSource({
    // the four no-ops legacy's guard rejected (currentMillStatus.equals(newMillStatus))
    "D, D",
    "S, S",
    "V, V",
    "O, O",
    // the direct D<->V jumps legacy's guard rejected
    "D, V",
    "V, D",
    // the pairs legacy's guard let through but its category map left null — a NOT NULL column
    // refused them at commit, so they never completed
    "O, S",
    "O, D",
    "O, V",
    "D, O",
    "S, O",
    "V, O",
  })
  void everyOtherPairIsRefused(String from, String to) {
    assertThat(TrackTransition.resolve(from, to)).isEmpty();
  }

  @Test
  @DisplayName("a null current or requested code never resolves")
  void nullCodesNeverResolve() {
    assertThat(TrackTransition.resolve(null, "S")).isEmpty();
    assertThat(TrackTransition.resolve("D", null)).isEmpty();
    assertThat(TrackTransition.resolve(null, null)).isEmpty();
  }

  @Test
  @DisplayName("each transition records the identity pair legacy keyed off its TARGET status")
  void recordedIdentityPairs() {
    // Legacy SubmitReportDAO.updateILCRMillReportStatus:401-412 branches on the target code alone:
    // 'D' skips the block, 'S' writes LICENSEE_*, anything else non-'D' writes AUDITOR_*.
    assertThat(TrackTransition.SUBMIT.recorded()).isEqualTo(TrackTransition.Recorded.LICENSEE);
    assertThat(TrackTransition.VERIFY.recorded()).isEqualTo(TrackTransition.Recorded.AUDITOR);
    // Set to Draft targets 'D', so legacy wrote neither pair — exact parity.
    assertThat(TrackTransition.SET_TO_DRAFT.recorded()).isEqualTo(TrackTransition.Recorded.NONE);
    // Set to Submit targets 'S', so legacy wrote the LICENSEE pair from the acting ADMIN's xref.
    // Story 18.1 D1(a) writes neither instead — recorded deviation (S), matching epics.md:2110.
    assertThat(TrackTransition.SET_TO_SUBMIT.recorded()).isEqualTo(TrackTransition.Recorded.NONE);
  }

  @Test
  @DisplayName("both submit-target transitions reuse legacy's one success key")
  void successKeys() {
    assertThat(TrackTransition.SUBMIT.successKey()).isEqualTo("sch1-10SubmittedMsg");
    assertThat(TrackTransition.VERIFY.successKey()).isEqualTo("sch1-10VerifiedMsg");
    assertThat(TrackTransition.SET_TO_DRAFT.successKey()).isEqualTo("sch1-10DraftMsg");
    // Legacy reused sch1-10SubmittedMsg for Set to Submit (CheckStatusMB.submitReport():281-283):
    // there is no distinct "verification reversed" text to port.
    assertThat(TrackTransition.SET_TO_SUBMIT.successKey()).isEqualTo("sch1-10SubmittedMsg");
  }

  @Test
  @DisplayName("each transition names the message for a track that has already left its start")
  void rejectedKeys() {
    assertThat(TrackTransition.SUBMIT.rejectedKey()).isEqualTo("submitNotDraftErrorMsg");
    assertThat(TrackTransition.VERIFY.rejectedKey()).isEqualTo("verifyNotSubmittedErrorMsg");
    assertThat(TrackTransition.SET_TO_DRAFT.rejectedKey())
        .isEqualTo("setToDraftNotSubmittedErrorMsg");
    assertThat(TrackTransition.SET_TO_SUBMIT.rejectedKey())
        .isEqualTo("setToSubmitNotVerifiedErrorMsg");
  }

  @Test
  @DisplayName("the 1-10 track advances ten category rows, '7' once; Schedule 11 advances one")
  void trackCategoryIds() {
    assertThat(ScheduleTrack.SCHEDULES_1_TO_10.categoryIds())
        .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    assertThat(ScheduleTrack.SCHEDULE_11.categoryIds()).containsExactly("11");
  }
}
