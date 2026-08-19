package ca.bc.gov.nrs.ilcr.reportingyear.dto;

/**
 * Request to open a reporting year (UC-RY-001). On the recurring path the year is derived server-side
 * ({@code max + 1}) and {@code year} is ignored/null; on first-time setup the administrator selects a
 * starting year from the bounded dropdown and it arrives here.
 *
 * @param year the selected starting year for first-time setup, or {@code null} on the recurring path
 */
public record OpenReportingYearRequest(Integer year) {
}
