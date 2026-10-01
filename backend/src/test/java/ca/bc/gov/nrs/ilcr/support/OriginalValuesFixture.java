package ca.bc.gov.nrs.ilcr.support;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
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

  /**
   * What a field with <em>nothing</em> on file carries: an empty comparison value and legacy's
   * tooltip ending right after the separator. Every {@code ILCROriginalValue*Converter} composed
   * {@code "Original Submission Value: " + (value == null ? "" : value)} ({@code
   * ILCROriginalValueStringConverter.java:23-26}), so the text exists even when the value does not
   * — which is why the key is written rather than omitted.
   */
  public static OriginalValue nothingOnFile() {
    return new OriginalValue("", LABEL + " ");
  }

  /**
   * Asserts every entry of a map is {@link #nothingOnFile()} — the shape a report carries when no
   * {@code 'S'} snapshot exists for it, which is roughly half of live rows.
   */
  public static void assertAllNothingOnFile(Map<String, OriginalValue> originals) {
    assertThat(originals).isNotNull().isNotEmpty();
    originals.forEach(
        (field, original) ->
            assertThat(original).as("original for %s", field).isEqualTo(nothingOnFile()));
  }

  /** The fields that actually carry a submitted value — the rest are {@link #nothingOnFile()}. */
  public static Set<String> fieldsWithASubmittedValue(Map<String, OriginalValue> originals) {
    return originals.entrySet().stream()
        .filter(e -> !e.getValue().value().isEmpty())
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
