package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

  @ParameterizedTest(name = "{0}")
  @EnumSource(TrackTransition.class)
  @DisplayName("every success key carries the legacy text")
  void successKeysResolve(TrackTransition transition) {
    assertResolves(transition.successKey());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(TrackTransition.class)
  @DisplayName("every rejection key carries its text, including the one no endpoint reaches")
  void rejectedKeysResolve(TrackTransition transition) {
    // verifyNotSubmittedErrorMsg is declared but unreached — Story 17.1 ruled VERIFY back onto
    // legacy's generic message. It is asserted anyway: the key is live the moment that ruling is
    // revisited, and an unreachable key is exactly the one nothing else would notice going missing.
    assertResolves(transition.rejectedKey());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(TrackTransition.class)
  @DisplayName("every validation-gate key carries its text")
  void gateFailedKeysResolve(TrackTransition transition) {
    assertResolves(transition.gateFailedKey());
  }

  @org.junit.jupiter.api.Test
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
