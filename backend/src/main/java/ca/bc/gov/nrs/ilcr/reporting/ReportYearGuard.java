package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The report-year guard shared by every unscoped ministry report endpoint: parse the raw {@code
 * year} request parameter and reject anything that is not an OPENED reporting period.
 *
 * <p>Extracted from {@code ReportController}'s private {@code requireOpenYear} (Story 19.2) rather
 * than copied, so the two 400s stay byte-identical across the Mill Information PDF and the Mill
 * Report Status table. A second copy would drift the moment one of the two messages was reworded.
 *
 * <p>This guard is deliberately NOT a mill/year working-context check. It never touches {@code
 * MillContextService.validateMillYearActive} — that applies submitter mill scope and rejects closed
 * mills, neither of which belongs on an all-mills administrator report.
 */
@Component
public class ReportYearGuard {

  /**
   * An all-digit value, optionally signed — a year that WAS supplied, whatever its magnitude.
   *
   * <p>It exists to keep {@code year=99999999999} out of the required-field rejection. That value
   * overflows an int, so {@code Integer.parseInt} throws the same {@code NumberFormatException} as
   * {@code "not-a-year"} — and reporting "Report Year: Value is required." for a year the caller
   * demonstrably typed is precisely the confusion the two-message split exists to prevent.
   */
  private static final Pattern SUPPLIED_NUMBER = Pattern.compile("[+-]?\\d+");

  private final MillContextService millContextService;

  /**
   * Constructs a new ReportYearGuard.
   *
   * @param millContextService the source of the opened reporting periods
   */
  public ReportYearGuard(MillContextService millContextService) {
    this.millContextService = millContextService;
  }

  /**
   * Parse and validate the report year. Absent, blank and non-numeric collapse to one rejection —
   * the legacy control was a dropdown of opened periods, so any value that is not a year means no
   * year was chosen. A parseable year that is not an OPEN period is rejected separately: without
   * that check {@code year=0} or a mistyped {@code 202} would reach the report, find no mills and
   * surface as {@code undefinedError}, which reads as a system fault rather than a bad selection.
   *
   * @param year the raw {@code year} request parameter; may be {@code null} or blank
   * @return the parsed year, guaranteed to be an opened reporting period
   * @throws ReportYearRequiredException when the value is absent, blank or not a number at all
   * @throws ReportYearNotOpenException when a number was supplied but is not an opened period —
   *     including one too large to be an int, which is a bad selection, not a missing one
   */
  public int requireOpenYear(String year) {
    if (year == null || year.isBlank()) {
      throw new ReportYearRequiredException();
    }
    String supplied = year.trim();
    int parsed;
    try {
      parsed = Integer.parseInt(supplied);
    } catch (NumberFormatException e) {
      if (SUPPLIED_NUMBER.matcher(supplied).matches()) {
        // All digits, just far outside any reporting period. A number was chosen; it is simply not
        // an open year.
        throw new ReportYearNotOpenException();
      }
      throw new ReportYearRequiredException();
    }
    boolean open =
        millContextService.listReportingYears().stream().anyMatch(y -> y.reportYear() == parsed);
    if (!open) {
      throw new ReportYearNotOpenException();
    }
    return parsed;
  }
}
