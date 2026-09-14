package ca.bc.gov.nrs.ilcr.dataextract.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * The cell formatting rules of legacy {@code CoreUtil}, reproduced so a rebuilt cell is
 * byte-identical to the legacy one.
 *
 * <p>Every rule here is a transcription, including the two that look like defects: a null {@code
 * $/m³} in the Schedule 4 and 6 formula cells renders as {@code ="-"} because legacy wrapped the
 * null marker in the Excel formula quotes too, and {@code replaceCharsForExtractFormat} strips
 * {@code \n} but never {@code \r}. Both are on the ratified keep-verbatim list.
 *
 * <p>{@code DecimalFormat} with the default {@code HALF_EVEN} rounding, as legacy's was. Grouping
 * and decimal symbols are pinned to the Canadian-English set rather than the JVM default so a pod's
 * locale cannot change the file.
 */
public final class ExtractFormat {

  /** Legacy {@code SHOW_WHEN_NULL_VALUE}. */
  public static final String NULL_VALUE = "-";

  /** Legacy {@code NO_DATA_FOUND}. */
  public static final String NO_DATA_FOUND = "*** NO DATA FOUND ***";

  /** Legacy {@code NO_SCHEDULE_3}. */
  public static final String NO_SCHEDULE_3 = "*** NO SCHEDULE 3 ***";

  private static final DecimalFormatSymbols SYMBOLS =
      DecimalFormatSymbols.getInstance(Locale.CANADA);

  private static final String PATTERN_WHOLE = "###,###,##0";
  private static final String PATTERN_TWO_DECIMALS = "###,###,##0.00";
  private static final String PATTERN_FORMULA = "###,##0.00";
  private static final String PATTERN_AREA = "#,###,##0";
  private static final String PATTERN_MONEY_TWO = "#,###,##0.00";

  private ExtractFormat() {}

  /** {@code ###,###,##0} — the default cost/volume/distance cell; null is {@code -}. */
  public static String whole(Number value) {
    return format(value, PATTERN_WHOLE);
  }

  /** {@code ###,###,##0.00} — the ordinary {@code $/m³} cell; null is {@code -}. */
  public static String twoDecimals(Number value) {
    return format(value, PATTERN_TWO_DECIMALS);
  }

  /**
   * The Excel-formula wrapped {@code $/m³} of Schedules 4 and 6: {@code ="1,234.56"}, so a
   * spreadsheet keeps the trailing zeros. Legacy wrapped the null marker too, giving {@code ="-"}.
   */
  public static String formula(Number value) {
    return "=\"" + format(value, PATTERN_FORMULA) + "\"";
  }

  /** Schedule 11's {@code #,###,##0} area cell; null is {@code -}. */
  public static String area(Number value) {
    return format(value, PATTERN_AREA);
  }

  /** Schedule 11's {@code #,###,##0.00} money cell; null is {@code -}. */
  public static String moneyTwoDecimals(Number value) {
    return format(value, PATTERN_MONEY_TWO);
  }

  /** {@code String.valueOf} with the null marker — the Schedule 10 Road raw-number cells. */
  public static String plain(Object value) {
    return value == null ? NULL_VALUE : String.valueOf(value);
  }

  /**
   * Free text as legacy sanitised it: tab to two spaces, newline to a space, {@code &nbsp;}
   * removed; null or empty is the null marker.
   */
  public static String text(String value) {
    if (value == null || value.isEmpty()) {
      return NULL_VALUE;
    }
    return defuse(sanitize(value));
  }

  /** Free text as legacy sanitised it, with a blank-after-trim also treated as absent. */
  public static String textTrimmed(String value) {
    if (value == null || value.trim().isEmpty()) {
      return NULL_VALUE;
    }
    return defuse(sanitize(value));
  }

  /** Legacy {@code replaceCharsForExtractFormat} — note it never strips {@code \r}. */
  public static String sanitize(String value) {
    return value.replace("\t", "  ").replace("\n", " ").replace("&nbsp;", "");
  }

  /** Text written RAW, as legacy did for a few cells; null or empty is the null marker. */
  public static String raw(String value) {
    return value == null || value.isEmpty() ? NULL_VALUE : defuse(value);
  }

  /**
   * Text guarded on null ALONE, which is how legacy guarded the sub-page location and camp name
   * cells — {@code x == null ? "-" : String.valueOf(x)} rather than its own {@code
   * isNullOrEmptyString} helper. An empty name therefore reaches the file as an empty quoted cell,
   * not as the null marker. A distinct helper rather than a flag on {@link #raw}: the two guards
   * are each correct for their own cells, and legacy applied both.
   */
  public static String nullOnly(String value) {
    return value == null ? NULL_VALUE : defuse(value);
  }

  /**
   * Stops a user-entered cell from being read as a spreadsheet formula.
   *
   * <p>The extract exists to be opened in a spreadsheet — it emits its own {@code ="…"} cells — so
   * a licensee-typed comment, camp or location name beginning with {@code =}, {@code +}, {@code @},
   * a tab or a carriage return would be EVALUATED on the administrator's workstation, not shown.
   * Legacy shared the exposure; this is an untrusted-input-to-privileged-reader path and is closed
   * here rather than reproduced (recorded deviation). A leading apostrophe is the spreadsheet
   * convention for "this is text" and is invisible in the cell itself.
   *
   * <p>A leading minus is defused only when what follows could be arithmetic ({@code -1+1} is a
   * formula; {@code - see note} is prose). The null marker {@code -} never passes through here, and
   * a dash-led comment is ordinary legacy data that must not gain an apostrophe.
   *
   * <p>Applied by the text helpers ONLY — never by {@link #formula}, whose leading {@code =} is the
   * point, and never by the numeric formatters, whose leading {@code -} is a sign.
   */
  public static String defuse(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    boolean trigger =
        first == '=' || first == '+' || first == '@' || first == '\t' || first == '\r';
    if (!trigger && first == '-' && value.length() > 1) {
      char second = value.charAt(1);
      trigger =
          Character.isDigit(second)
              || second == '='
              || second == '+'
              || second == '-'
              || second == '(';
    }
    return trigger ? "'" + value : value;
  }

  /**
   * Legacy {@code bigDecimalDivision}: scale-10 {@code HALF_UP} then rounded to two places; null on
   * a null operand or a zero divisor.
   */
  public static BigDecimal divide(Number numerator, Number denominator) {
    BigDecimal n = decimal(numerator);
    BigDecimal d = decimal(denominator);
    if (n == null || d == null || d.signum() == 0) {
      return null;
    }
    return n.divide(d, 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
  }

  /** Legacy {@code bigDecimalDivisionNoRounding}: scale-15 {@code HALF_UP}; same null rules. */
  public static BigDecimal divideNoRounding(Number numerator, Number denominator) {
    BigDecimal n = decimal(numerator);
    BigDecimal d = decimal(denominator);
    if (n == null || d == null || d.signum() == 0) {
      return null;
    }
    return n.divide(d, 15, RoundingMode.HALF_UP);
  }

  /** Legacy {@code bigDecimalAddition}: null when either side is null. */
  public static BigDecimal add(Number a, Number b) {
    BigDecimal x = decimal(a);
    BigDecimal y = decimal(b);
    return x == null || y == null ? null : x.add(y);
  }

  /** Legacy {@code bigDecimalSubtraction}: null when either side is null. */
  public static BigDecimal subtract(Number a, Number b) {
    BigDecimal x = decimal(a);
    BigDecimal y = decimal(b);
    return x == null || y == null ? null : x.subtract(y);
  }

  /**
   * Legacy {@code sumBigDecimalCosts}: each non-null term rounded to a whole number and summed;
   * null when nothing was added.
   */
  public static BigDecimal sumCosts(List<? extends Number> values) {
    BigDecimal total = BigDecimal.ZERO;
    boolean added = false;
    for (Number value : values) {
      BigDecimal v = decimal(value);
      if (v != null) {
        added = true;
        total = total.add(v.setScale(0, RoundingMode.HALF_UP));
      }
    }
    return added ? total : null;
  }

  /** Legacy {@code sumBig2DecimalCosts}: each non-null term rounded to two places and summed. */
  public static BigDecimal sumCostsTwoDecimals(List<? extends Number> values) {
    BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    boolean added = false;
    for (Number value : values) {
      BigDecimal v = decimal(value);
      if (v != null) {
        added = true;
        total = total.add(v.setScale(2, RoundingMode.HALF_UP));
      }
    }
    return added ? total : null;
  }

  /** Legacy {@code sumBigDecimalAreas}: non-null terms summed, rounded to one place. */
  public static BigDecimal sumAreas(List<? extends Number> values) {
    BigDecimal total = BigDecimal.ZERO;
    boolean added = false;
    for (Number value : values) {
      BigDecimal v = decimal(value);
      if (v != null) {
        added = true;
        total = total.add(v);
      }
    }
    return added ? total.setScale(1, RoundingMode.HALF_UP) : null;
  }

  /** Any boxed number as a {@code BigDecimal}, or null. */
  public static BigDecimal decimal(Number value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Integer || value instanceof Long) {
      return BigDecimal.valueOf(value.longValue());
    }
    return new BigDecimal(value.toString());
  }

  private static String format(Number value, String pattern) {
    BigDecimal decimal = decimal(value);
    if (decimal == null) {
      return NULL_VALUE;
    }
    return new DecimalFormat(pattern, SYMBOLS).format(decimal);
  }
}
