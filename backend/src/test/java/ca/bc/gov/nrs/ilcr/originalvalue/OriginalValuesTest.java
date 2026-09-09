package ca.bc.gov.nrs.ilcr.originalvalue;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The specification for the original-value gate and its wire shape (Story 16.2).
 *
 * <p>The rule exists in exactly one place, so its table belongs in exactly one test — the same
 * reasoning {@code ScheduleEditabilityTest} states for the editability matrix. Everything a
 * schedule service can get wrong about original values is a consequence of the three behaviours
 * asserted here: the status gate, the null-original representation, and the canonical-vs-display
 * split.
 */
@DisplayName("OriginalValues — the isSubmit() gate and the pinned wire shape")
class OriginalValuesTest {

  private final OriginalValues originalValues = OriginalValuesFixture.real();

  @Nested
  @DisplayName("the gate is a status gate, not a permission gate")
  class Gate {

    @Test
    @DisplayName("Draft exposes nothing, however many values are offered")
    void draft_exposesNothing() {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("D")
              .put("volume", new BigDecimal("60000"), OriginalValueFormat.WHOLE)
              .put("comments", "the licensee's note", OriginalValueFormat.TEXT)
              .build();

      // Null, not empty: with @JsonInclude(NON_NULL) on the carrying record this leaves the
      // Draft-time payload byte-identical to what it was before the feature existed, and it is what
      // tells the page to render no indicator at all rather than to evaluate each field.
      assertThat(map).isNull();
      assertThat(originalValues.exposesOriginalValues("D")).isFalse();
    }

    @ParameterizedTest(name = "status {0} exposes original values")
    @ValueSource(strings = {"S", "V", "O"})
    @DisplayName("every status other than Draft exposes them — including Verified and legacy O")
    void beyondDraft_exposes(String status) {
      // Legacy asked only !D.equals(status) (UserSessionMB.java:541-554). Verified qualifies, and
      // so
      // does the dead legacy 'O', which is read-only but still shows what the licensee submitted:
      // visibility is deliberately NOT coupled to the editability matrix.
      assertThat(originalValues.exposesOriginalValues(status)).isTrue();
      assertThat(originalValues.forTrack(status).build()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("a null status fails closed — a deliberate deviation from legacy")
    void nullStatus_failsClosed() {
      // Legacy resolved an unknown code to INVALID through a swallowed NPE and !D.equals(INVALID)
      // is true, so a report with no status row would have had indicators forced ON against a
      // snapshot that cannot exist — flagging every populated field. Story 16.1 pinned the same
      // cell
      // to read-only for editability; this matches it.
      assertThat(originalValues.exposesOriginalValues(null)).isFalse();
      assertThat(
              originalValues
                  .forTrack(null)
                  .put("volume", new BigDecimal("1"), OriginalValueFormat.WHOLE)
                  .build())
          .isNull();
    }
  }

  @Nested
  @DisplayName("a null submitted value is represented by the key's ABSENCE")
  class NullOriginal {

    @Test
    @DisplayName("no key is written, so the page falls to the added-since-submission branch")
    void nullSubmitted_writesNoKey() {
      // This is the common case, not an edge one: roughly half the live cost-detail rows carry no
      // 'S' snapshot at all. Legacy's isOriginalVal treats original == null as "flag whenever the
      // current value is non-empty", and an absent key is how the client is told to do that. A
      // snapshot row with a null column and no snapshot row are indistinguishable in legacy too.
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("volume", null, OriginalValueFormat.WHOLE)
              .put("cost", 600, OriginalValueFormat.WHOLE)
              .build();

      assertThat(map).containsOnlyKeys("cost");
    }

    @Test
    @DisplayName("beyond Draft with nothing on file is an empty map, never null")
    void nothingOnFile_isEmptyNotNull() {
      // A real and distinct state from Draft: the page must still evaluate every field for the
      // added-since-submission branch, which it cannot do if this collapses to null.
      assertThat(
              originalValues.forTrack("S").put("volume", null, OriginalValueFormat.WHOLE).build())
          .isNotNull()
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("value is for comparing, tooltip is for reading")
  class Shape {

    @Test
    @DisplayName("the tooltip is the verbatim label plus the formatted value")
    void tooltip_isLabelPlusFormattedValue() {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("cost", new BigDecimal("60000"), OriginalValueFormat.WHOLE)
              .build();

      assertThat(map.get("cost").tooltip())
          .isEqualTo(OriginalValuesFixture.tooltip("60,000"))
          .isEqualTo("Original Submission Value: 60,000");
    }

    @Test
    @DisplayName("the compared value is ungrouped, and a BigDecimal's trailing zeros are stripped")
    void value_isCanonical() {
      // Legacy compared rounded BigDecimals (CoreUtil.isBigDecimalOriginalVal), so 600.0 and 600
      // are the same submitted value. Stripping here is what lets the client compare text without
      // reintroducing a difference legacy did not see.
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("volume", new BigDecimal("600.0"), OriginalValueFormat.WHOLE)
              .put("length", new BigDecimal("12.50"), OriginalValueFormat.ONE_DECIMAL)
              .build();

      assertThat(map.get("volume").value()).isEqualTo("600");
      assertThat(map.get("length").value()).isEqualTo("12.5");
      // ...while the tooltip still renders at the field's own legacy scale.
      assertThat(map.get("length").tooltip()).isEqualTo("Original Submission Value: 12.5");
    }

    @Test
    @DisplayName("a boolean is canonicalised to the stored Y/N")
    void booleanValue_isYorN() {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("isolatedCamp", true, OriginalValueFormat.YES_NO)
              .build();

      assertThat(map.get("isolatedCamp").value()).isEqualTo("Y");
      assertThat(map.get("isolatedCamp").tooltip()).isEqualTo("Original Submission Value: Yes");
    }

    @Test
    @DisplayName("the map is immutable — it is served state, not a scratch buffer")
    void built_isImmutable() {
      Map<String, OriginalValue> map =
          originalValues.forTrack("S").put("cost", 1, OriginalValueFormat.WHOLE).build();

      assertThat(map).isUnmodifiable();
    }
  }

  @Nested
  @DisplayName("tooltip formats reproduce the seven legacy converters")
  class Formats {

    @ParameterizedTest(name = "{0} renders {2} as \"{3}\"")
    @CsvSource({
      "WHOLE,           volume,   1234567,  '1,234,567'",
      "ONE_DECIMAL,     netArea,  1234.56,  '1,234.6'",
      "TWO_DECIMAL,     rate,     1234.567, '1,234.57'",
      "THREE_DECIMAL,   depth,    1.2345,   '1.234'",
      "PERCENTAGE,      slope,    42,       '42'",
    })
    @DisplayName("numeric patterns are legacy's DecimalFormat patterns verbatim")
    // Note 1.2345 -> 1.234, not 1.235: DecimalFormat rounds HALF_EVEN by default, and legacy called
    // it the same way, so half-way values round to even here exactly as they did on the old
    // screens.
    void numericFormats(String format, String field, String value, String expected) {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put(field, new BigDecimal(value), OriginalValueFormat.valueOf(format))
              .build();

      assertThat(map.get(field).tooltip()).isEqualTo("Original Submission Value: " + expected);
    }

    @Test
    @DisplayName("a submitted zero is a real value and still renders")
    void zero_rendersAsZero() {
      // "##,###,###" carries no '0' digit placeholder, yet DecimalFormat still emits "0" for zero
      // rather than an empty string. Asserted rather than assumed, because the pattern reads as
      // though it would drop it — and legacy called DecimalFormat the same way, so a licensee who
      // submitted zero and a ministry correction away from it show the indicator with a readable
      // tooltip.
      Map<String, OriginalValue> map =
          originalValues.forTrack("S").put("cost", 0, OriginalValueFormat.WHOLE).build();

      assertThat(map.get("cost").value()).isEqualTo("0");
      assertThat(map.get("cost").tooltip()).isEqualTo("Original Submission Value: 0");
    }

    @ParameterizedTest(name = "{0} maps {1} to \"{2}\"")
    @CsvSource({
      "UPHILL_DIRECTION, Y, Uphill",
      "UPHILL_DIRECTION, N, Downhill",
      "WATER_DUMP,       Y, Water Dump",
      "WATER_DUMP,       N, Land Dump",
      "YES_NO,           Y, Yes",
      "YES_NO,           N, No",
    })
    @DisplayName("indicator codes render as the legacy words, not as Y/N")
    void codeFormats(String format, String stored, String expected) {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("flag", stored, OriginalValueFormat.valueOf(format))
              .build();

      assertThat(map.get("flag").tooltip()).isEqualTo("Original Submission Value: " + expected);
      // The compared value stays the stored code, since that is what the page holds.
      assertThat(map.get("flag").value()).isEqualTo(stored);
    }

    @Test
    @DisplayName("an unrecognised code renders as nothing, as legacy's converters did")
    void unknownCode_rendersEmpty() {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("flag", "X", OriginalValueFormat.UPHILL_DIRECTION)
              .build();

      assertThat(map.get("flag").tooltip()).isEqualTo("Original Submission Value: ");
    }

    @Test
    @DisplayName("text is unformatted")
    void text_isRaw() {
      Map<String, OriginalValue> map =
          originalValues
              .forTrack("S")
              .put("comments", "  as the mill reported it  ", OriginalValueFormat.TEXT)
              .build();

      assertThat(map.get("comments").value()).isEqualTo("  as the mill reported it  ");
      assertThat(map.get("comments").tooltip())
          .isEqualTo("Original Submission Value:   as the mill reported it  ");
    }
  }
}
