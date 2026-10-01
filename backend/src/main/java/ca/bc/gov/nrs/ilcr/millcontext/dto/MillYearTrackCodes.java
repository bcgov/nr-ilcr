package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * One {@code ILCR_MILL_REPORT_STATUS} row's two independent track codes, keyed by the (mill, year)
 * it belongs to — the bulk shape for a caller that spans many mills and years at once (the Data
 * Extract's "Data Verified" rule), where one query beats a round trip per pair. millcontext stays
 * the single owner of the track-status read (AD-9).
 *
 * @param millId the mill id
 * @param year the reporting year
 * @param schedules1To10Code the Schedules 1–10 code ({@code ILCR_MILL_REPORT_STATUS_CODE});
 *     nullable
 * @param schedule11Code the Schedule 11 code ({@code MILL_SILVICULTUR_STATUS_CODE}); nullable
 */
public record MillYearTrackCodes(
    long millId, int year, String schedules1To10Code, String schedule11Code) {}
