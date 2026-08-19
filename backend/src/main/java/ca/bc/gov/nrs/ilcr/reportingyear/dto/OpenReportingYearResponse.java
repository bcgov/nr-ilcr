package ca.bc.gov.nrs.ilcr.reportingyear.dto;

/**
 * The outcome of opening a reporting year (UC-RY-001, SUC-001). Carries the year created, how many
 * active mills received report-status rows, and the verbatim success message (AD-8) so the client
 * renders it without duplicating text.
 *
 * @param year the reporting year that was created
 * @param millsInitialized the number of active mills given a report-status row for the new year
 * @param messageKey the {@code messages.properties} key of the success message
 * @param message the verbatim success text (SUC-001)
 */
public record OpenReportingYearResponse(int year, int millsInitialized, String messageKey, String message) {
}
