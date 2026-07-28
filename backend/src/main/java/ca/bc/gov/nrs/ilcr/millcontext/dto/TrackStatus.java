package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * One report-track status for the Home working context (Story 1.2, pinned wire contract AD-12 as
 * AMENDED 2026-07-21: the {@code date} component was added to the Story 1.1 pin because the epic's
 * banner requires {@code Date: {date-or-Not Initiated}} per track — a backward-compatible addition,
 * recorded in both story files). The Schedules 1–10 track and the Schedule 11 (silviculture) track
 * are independent instances of this shape and are never coupled (AR6/AD-9).
 *
 * <p>NULLABILITY: {@code description} may be null (a code present in
 * {@code ILCR_MILL_REPORT_STATUS} but absent from the {@code ILCR_MILL_REPORT_STATUS_CODE} lookup)
 * and is then omitted from the JSON under global Jackson {@code non_null} — like
 * {@code MillSummary}'s nullable fields, Story 1.4's banner MUST render a present-{@code code}/
 * absent-{@code description} track defensively rather than assume the pair is always complete.
 * {@code date} is null when blank/absent (rendered as {@code Not Initiated}).
 *
 * @param code the status code {@code D}/{@code S}/{@code V}/{@code O}
 *     ({@code THE.ILCR_MILL_REPORT_STATUS_CODE.ILCR_MILL_REPORT_STATUS_CODE})
 * @param description the code's description resolved from {@code THE.ILCR_MILL_REPORT_STATUS_CODE}
 *     (e.g. {@code Draft}); may be null if the code has no lookup row (render defensively)
 * @param date the display date from {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW}, selected by this
 *     track's own code with the legacy 3-character prefix stripped; null when blank/absent — the
 *     frontend (Story 1.4) renders null as {@code Not Initiated}
 */
public record TrackStatus(String code, String description, String date) {
}
