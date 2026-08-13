package ca.bc.gov.nrs.ilcr.reporting;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Shared pre-formatting for the bean-datasource sections: the legacy {@code SchedulesPrinter}
 * handed Jasper display Strings, substituting {@code "-"} for a null value, so the templates never
 * mask a number themselves. These helpers reproduce that — a null becomes {@code "-"}, costs render
 * with thousands separators and no decimals, and $-per-unit / volume figures keep two decimals.
 */
final class SectionFormat {

  private static final String DASH = "-";

  private SectionFormat() {
  }

  /** A whole-dollar cost with thousands separators, or {@code "-"} when null. */
  static String money(Long value) {
    return value == null ? DASH : new DecimalFormat("#,##0").format(value);
  }

  /** A whole-dollar cost with thousands separators, or {@code "-"} when null. */
  static String money(Integer value) {
    return value == null ? DASH : new DecimalFormat("#,##0").format(value.longValue());
  }

  /** A decimal figure (volume / $-per-unit) at two decimals, or {@code "-"} when null. */
  static String decimal(BigDecimal value) {
    return value == null ? DASH : new DecimalFormat("#,##0.00").format(value);
  }

  /** A raw integer as text, or {@code "-"} when null. */
  static String integer(Integer value) {
    return value == null ? DASH : String.valueOf(value);
  }

  /** A stored text value verbatim, or {@code "-"} when null/blank. */
  static String text(String value) {
    return value == null || value.isBlank() ? DASH : value;
  }
}
