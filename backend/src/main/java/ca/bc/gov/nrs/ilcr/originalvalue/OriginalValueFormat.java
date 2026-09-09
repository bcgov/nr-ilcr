package ca.bc.gov.nrs.ilcr.originalvalue;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Locale;

/**
 * How an originally-submitted value is rendered inside its tooltip — one constant per legacy
 * {@code @FacesConverter} that the schedule views bound to an original-value tooltip.
 *
 * <p>Legacy had no message bundle for any of this: seven converter classes under {@code
 * common/converter/} each hardcoded {@code "Original Submission Value: "} and applied their own
 * {@link DecimalFormat}. Which converter a field used is part of behaviour parity — the same figure
 * renders {@code 1,234} on one screen and {@code 1,234.0} on another — so the choice is pinned per
 * field at the call site rather than inferred from the Java type.
 *
 * <p>The patterns are copied verbatim from legacy, and so is the rounding: {@link DecimalFormat}
 * rounds {@code HALF_EVEN} by default and legacy never overrode it, so {@code 1.2345} at three
 * decimals renders {@code 1.234}. A submitted zero renders {@code "0"} despite no {@code 0}
 * placeholder in the integer part — asserted in {@code OriginalValuesTest} rather than assumed,
 * because the patterns read as though they would drop it.
 */
public enum OriginalValueFormat {

  /**
   * Free text, unformatted — legacy {@code originalValueStringConverter} ({@code
   * ILCROriginalValueStringConverter.java:25}: {@code value == null ? "" : value.toString()}). Used
   * by every comments field.
   */
  TEXT(null),

  /**
   * Whole numbers, grouped — legacy {@code originalValueVolumeConverter} ({@code
   * ILCROriginalValueVolumeConverter.java:29}) and {@code originalValueCostConverter} at its
   * default precision ({@code ILCROriginalValueCostConverter.java:43}). This is the common case:
   * only 19 of the legacy tooltips passed an explicit {@code precision}.
   */
  WHOLE("##,###,###"),

  /**
   * One decimal place — legacy {@code originalValueDecimalConverter} ({@code
   * ILCROriginalValueDecimalConverter.java:29}) and {@code originalValueCostConverter} with {@code
   * precision=1}.
   */
  ONE_DECIMAL("##,###,##0.0"),

  /** Two decimal places — legacy {@code originalValueCostConverter} with {@code precision=2}. */
  TWO_DECIMAL("##,###,##0.00"),

  /** Three decimal places — legacy {@code originalValueCostConverter} with {@code precision=3}. */
  THREE_DECIMAL("##,###,##0.000"),

  /**
   * Bare percentage — legacy {@code originalValueSideSlopePercentageConverter} ({@code
   * ILCROriginalvalueSideSlopePercentageConverter.java:43}). Legacy threw a {@code
   * NullPointerException} here on a null value ({@code :38} dereferenced before any null check); a
   * null simply yields no tooltip value instead.
   */
  PERCENTAGE("##"),

  /**
   * {@code Y}/{@code N} rendered as the uphill-direction words — legacy {@code
   * originalValueDirectionConverter} ({@code ILCROriginalValueDirectionConverter.java:40} mapping
   * {@code Constant.UPHILL_DIRECTION}, {@code Constant.java:639-652}). Anything else yields
   * nothing, as in legacy.
   */
  UPHILL_DIRECTION(null) {
    @Override
    String render(Object value) {
      return switch (String.valueOf(value)) {
        case "Y" -> "Uphill";
        case "N" -> "Downhill";
        default -> "";
      };
    }
  },

  /**
   * {@code Y}/{@code N} rendered as the water-dump words — legacy {@code
   * originalValueDumpConverter} ({@code ILCROriginalValueDumpConverter.java:40} mapping {@code
   * Constant.WATER_DUMP_DESTINATION}, {@code Constant.java:653-666}).
   */
  WATER_DUMP(null) {
    @Override
    String render(Object value) {
      return switch (String.valueOf(value)) {
        case "Y" -> "Water Dump";
        case "N" -> "Land Dump";
        default -> "";
      };
    }
  },

  /**
   * A yes/no indicator rendered as {@code Yes}/{@code No}. Legacy reached the same screen text by
   * binding a {@code getXxxAsString()} accessor straight into an uncoverted tooltip (e.g. {@code
   * schedule5ExistingCamp.xhtml:103} for the isolated-camp flag), so there is no converter to name;
   * the rendering is the accessor's.
   */
  YES_NO(null) {
    @Override
    String render(Object value) {
      if (value instanceof Boolean flag) {
        return flag.booleanValue() ? "Yes" : "No";
      }
      return switch (String.valueOf(value)) {
        case "Y", "true" -> "Yes";
        case "N", "false" -> "No";
        default -> "";
      };
    }
  };

  private final String pattern;

  OriginalValueFormat(String pattern) {
    this.pattern = pattern;
  }

  /**
   * The tooltip rendering of a submitted value: grouped, fixed-scale, or worded per this constant.
   * A null value renders as nothing, so the tooltip reads as the bare label — legacy's behaviour
   * whenever the snapshot column held no value.
   */
  String render(Object value) {
    if (value == null) {
      return "";
    }
    if (pattern == null) {
      return value.toString();
    }
    // A fresh instance per call: DecimalFormat is mutable and not thread-safe, and these are served
    // concurrently. Locale.CANADA fixes ',' grouping and '.' decimals regardless of server locale.
    return new DecimalFormat(pattern, new java.text.DecimalFormatSymbols(Locale.CANADA))
        .format(asNumber(value));
  }

  private static Number asNumber(Object value) {
    if (value instanceof Number number) {
      return number;
    }
    return new BigDecimal(value.toString());
  }
}
