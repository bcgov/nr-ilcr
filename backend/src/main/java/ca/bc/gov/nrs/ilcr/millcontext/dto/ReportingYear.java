package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * An opened reporting year for the Home page option list (Story 1.1, pinned wire contract AD-12).
 * One existing {@code THE.ILCR_REPORTING_PERIOD} row; the list is ordered by {@code REPORT_YEAR}
 * descending (BR-03).
 *
 * @param reportYear the reporting year ({@code THE.ILCR_REPORTING_PERIOD.REPORT_YEAR})
 */
public record ReportingYear(int reportYear) {
}
