package ca.bc.gov.nrs.ilcr.support;

import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import java.util.Locale;
import org.springframework.context.support.StaticMessageSource;

/**
 * The <em>real</em> {@link OriginalValues} for unit tests, over a message source carrying just the
 * tooltip label.
 *
 * <p>Deliberately not a mock. The whole substance of this component is one rule — a track exposes
 * original values when its status is not {@code "D"} — so a mocked {@code forTrack} would return a
 * null builder and every test would assert against a stub of the thing under test. Story 16.1 was
 * bitten by exactly that shape (a suite that asserted a gate rather than enforcing it), and the
 * same reasoning that put {@code CallerRights} on the real {@code ScheduleEditability} applies
 * here.
 *
 * <p>Use it as a Mockito {@code @Spy} field so {@code @InjectMocks} picks it up:
 *
 * <pre>{@code
 * @Spy private OriginalValues originalValues = OriginalValuesFixture.real();
 * }</pre>
 */
public final class OriginalValuesFixture {

  /** The verbatim legacy sentence, as {@code messages.properties} carries it. */
  public static final String LABEL = "Original Submission Value:";

  private OriginalValuesFixture() {}

  /** A real {@code OriginalValues} whose label resolves to the production text. */
  public static OriginalValues real() {
    StaticMessageSource messages = new StaticMessageSource();
    messages.addMessage("originalSubmissionValueLabel", Locale.CANADA, LABEL);
    return new OriginalValues(messages);
  }

  /** The tooltip text a field with this submitted value should carry. */
  public static String tooltip(String formattedValue) {
    return LABEL + " " + formattedValue;
  }
}
