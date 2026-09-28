package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Every bundle key a {@link TrackTransition} names must resolve to real text.
 *
 * <p><strong>This class exists because one of them did not.</strong> {@code
 * TrackTransition.SET_TO_DRAFT} named {@code sch1-10DraftMsg} from the day the enum was written,
 * and the key was never added to {@code messages.properties} — a repo-wide grep found exactly one
 * occurrence, the enum literal itself. {@code CheckStatusController.message()} resolves keys with
 * {@code messageSource.getMessage(key, null, key, locale)}, whose third argument is the key as its
 * own default, so Set to Draft would have answered <em>200 OK</em> with the literal string {@code
 * "sch1-10DraftMsg"} as its success message. A missing key that fails loudly is a bug you find in
 * five minutes; one that falls back to itself ships.
 *
 * <p>So the assertion is deliberately not "getMessage does not throw" — that can never fail with a
 * default supplied. It is that the resolved text differs from the key, which is the only shape of
 * this defect (Story 18.1 D4).
 */
@DisplayName("TrackTransition message keys resolve to text, not to themselves")
class TrackTransitionMessageBundleTest {

  /** The production bundle, at Spring Boot's default {@code messages} basename. */
  private static final ResourceBundleMessageSource BUNDLE = bundle();

  private static ResourceBundleMessageSource bundle() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding("UTF-8");
    return source;
  }

  /**
   * Every (transition, track) pair that carries message keys. Driven by {@link
   * TrackTransition#isDefinedOn}, so a later story defining a Schedule 11 pair without adding its
   * bundle entries turns this class red rather than shipping the key as its own text.
   */
  static Stream<Arguments> definedPairs() {
    return Arrays.stream(TrackTransition.values())
        .flatMap(
            transition ->
                Arrays.stream(ScheduleTrack.values())
                    .filter(transition::isDefinedOn)
                    .map(track -> Arguments.of(transition, track)));
  }

  /** The complement of {@link #definedPairs()}: the pairs whose accessors must throw. */
  static Stream<Arguments> undefinedPairs() {
    return Arrays.stream(TrackTransition.values())
        .flatMap(
            transition ->
                Arrays.stream(ScheduleTrack.values())
                    .filter(track -> !transition.isDefinedOn(track))
                    .map(track -> Arguments.of(transition, track)));
  }

  @ParameterizedTest(name = "{0} on {1}")
  @MethodSource("definedPairs")
  @DisplayName("every success key carries the legacy text")
  void successKeysResolve(TrackTransition transition, ScheduleTrack track) {
    assertResolves(transition.successKey(track));
  }

  @ParameterizedTest(name = "{0} on {1}")
  @MethodSource("definedPairs")
  @DisplayName("every rejection key carries its text, including the one no endpoint reaches")
  void rejectedKeysResolve(TrackTransition transition, ScheduleTrack track) {
    // verifyNotSubmittedErrorMsg is declared but unreached — Story 17.1 ruled VERIFY back onto
    // legacy's generic message. It is asserted anyway: the key is live the moment that ruling is
    // revisited, and an unreachable key is exactly the one nothing else would notice going missing.
    assertResolves(transition.rejectedKey(track));
  }

  @ParameterizedTest(name = "{0} on {1}")
  @MethodSource("definedPairs")
  @DisplayName("every validation-gate key carries its text")
  void gateFailedKeysResolve(TrackTransition transition, ScheduleTrack track) {
    assertResolves(transition.gateFailedKey(track));
  }

  @Test
  @DisplayName("the defined pairs are exactly the four 1-10 rows plus Submit on Schedule 11")
  void definedPairsAreTheShippedOnes() {
    assertThat(definedPairs().map(a -> a.get()[0] + "/" + a.get()[1]))
        .containsExactlyInAnyOrder(
            "SUBMIT/SCHEDULES_1_TO_10",
            "VERIFY/SCHEDULES_1_TO_10",
            "SET_TO_DRAFT/SCHEDULES_1_TO_10",
            "SET_TO_SUBMIT/SCHEDULES_1_TO_10",
            "SUBMIT/SCHEDULE_11");
  }

  @ParameterizedTest(name = "{0} on {1}")
  @MethodSource("undefinedPairs")
  @DisplayName("every undefined pair throws rather than falling back to another track's text")
  void undefinedPairsThrow(TrackTransition transition, ScheduleTrack track) {
    assertThatThrownBy(() -> transition.successKey(track))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> transition.rejectedKey(track))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> transition.gateFailedKey(track))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("the Schedule 11 confirm prompt the page renders is in the bundle")
  void schedule11ConfirmResolves() {
    assertResolves("confirmSubmitSch11Msg");
  }

  @Test
  @DisplayName("the generic fallback the refusals share also resolves")
  void genericFallbackResolves() {
    assertResolves(ReportTransitionRejectedException.GENERIC_KEY);
  }

  private static void assertResolves(String key) {
    String text = BUNDLE.getMessage(key, null, key, Locale.ENGLISH);
    assertThat(text)
        .as(
            "messages.properties must define %s; resolving to the key itself is the silent-200 bug",
            key)
        .isNotBlank()
        .isNotEqualTo(key);
  }
}
