package ca.bc.gov.nrs.ilcr.reportingyear.dto;

/**
 * Internal outcome of {@code ReportingYearService.open} — the year created and how many active
 * mills were initialized. The controller resolves the verbatim success message (AD-8) and maps this
 * to the client-facing {@link OpenReportingYearResponse}.
 *
 * @param year the reporting year that was created
 * @param millsInitialized the number of active mills given a report-status row
 */
public record OpenReportingYearResult(int year, int millsInitialized) {}
