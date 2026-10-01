package ca.bc.gov.nrs.ilcr.util;

/**
 * The legacy display-date text rule shared by every reader of {@code
 * THE.ILCR_MILL_REPORT_STATUS_RPT_VW}.
 *
 * <p>That view returns each milestone as a string carrying a three-character sort prefix — delivery
 * rows read {@code "O: 2021-01-05"} or, for a milestone not yet reached, {@code "D: "} with nothing
 * after the prefix. Legacy strips it with {@code substring(3)} ({@code UserSessionMB.java:374}).
 *
 * <p>This lives in one place because two surfaces render the same dates — the Home working-context
 * banner and the Mill Information report — and a disagreement between them would show up as the
 * same mill reporting two different milestone dates on two screens. Legacy itself had exactly that
 * bug: {@code MillReportStatusReport.createReportDataSource} used {@code substring(2)}, leaving a
 * stray leading space on every date the report printed.
 */
public final class LegacyDateText {

  private static final int PREFIX_LENGTH = 3;

  private LegacyDateText() {}

  /**
   * Strip the three-character sort prefix from a view date string.
   *
   * @param raw the raw view value; may be null
   * @return the date text, or null when the value is absent or holds only the prefix
   */
  public static String stripPrefix(String raw) {
    if (raw == null || raw.length() <= PREFIX_LENGTH) {
      return null;
    }
    String rest = raw.substring(PREFIX_LENGTH);
    return rest.isBlank() ? null : rest;
  }
}
