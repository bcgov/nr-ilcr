package ca.bc.gov.nrs.ilcr.reporting;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Shared pre-formatting for the bean-datasource sections: the legacy {@code SchedulesPrinter}
 * handed Jasper display Strings, substituting {@code "-"} for a null value, so the templates never
 * mask a number themselves. These helpers reproduce that — a null becomes {@code "-"}, costs render
 * with thousands separators and no decimals, and $-per-unit / volume figures keep two decimals.
 *
 * <p>Every {@link DecimalFormat} is pinned to {@link Locale#CANADA} symbols (matching Schedule 9's
 * {@code Schedule9Service}): the grouping/decimal separators must be a comma and a period
 * regardless of the JVM default locale, which a non-en container pod would otherwise flip (e.g.
 * {@code 1.234,56}).
 */
final class SectionFormat {

  private static final String DASH = "-";

  /** Canadian number symbols (comma grouping, period decimal) — never the JVM default. */
  private static final DecimalFormatSymbols SYMBOLS =
      DecimalFormatSymbols.getInstance(Locale.CANADA);

  private SectionFormat() {}

  /** A whole-dollar cost with thousands separators, or {@code "-"} when null. */
  static String money(Long value) {
    return value == null ? DASH : new DecimalFormat("#,##0", SYMBOLS).format(value);
  }

  /** A whole-dollar cost with thousands separators, or {@code "-"} when null. */
  static String money(Integer value) {
    return value == null ? DASH : new DecimalFormat("#,##0", SYMBOLS).format(value.longValue());
  }

  /**
   * A whole-dollar cost from a {@code BigDecimal} source, grouped with no decimals, or {@code "-"}
   * when null. Schedule 10's cost fields are {@code BigDecimal} (unlike Schedule 6's {@code Long});
   * the legacy report renders them grouped with no cents (e.g. {@code 25,000}).
   */
  static String money(BigDecimal value) {
    return value == null ? DASH : new DecimalFormat("#,##0", SYMBOLS).format(value);
  }

  /**
   * A measurement at its stored precision with a trailing unit (e.g. {@code 2.123 m}, {@code 1.1
   * km}), or a bare {@code "-"} (no unit) when null — matching the legacy Schedule 10 report, which
   * drops the unit for an absent measure. Uses the value's own scale (not a fixed two decimals) so
   * {@code 4.5} stays {@code 4.5} and {@code 1.500} stays {@code 1.500}.
   */
  static String measure(BigDecimal value, String unit) {
    return value == null ? DASH : value.toPlainString() + " " + unit;
  }

  /**
   * A raw numeric figure at its stored precision (no unit, no grouping), or {@code "-"} when null.
   */
  static String plain(BigDecimal value) {
    return value == null ? DASH : value.toPlainString();
  }

  /** A decimal figure (volume / $-per-unit) at two decimals, or {@code "-"} when null. */
  static String decimal(BigDecimal value) {
    return value == null ? DASH : new DecimalFormat("#,##0.00", SYMBOLS).format(value);
  }

  /** A raw integer as text, or {@code "-"} when null. */
  static String integer(Integer value) {
    return value == null ? DASH : String.valueOf(value);
  }

  /** A percentage text (e.g. {@code 45 %}), or {@code "-"} when null. */
  static String percent(Integer value) {
    return value == null ? DASH : value.toString() + " %";
  }

  /** A stored text value verbatim, or {@code "-"} when null/blank. */
  static String text(String value) {
    return value == null || value.isBlank() ? DASH : value;
  }
}
