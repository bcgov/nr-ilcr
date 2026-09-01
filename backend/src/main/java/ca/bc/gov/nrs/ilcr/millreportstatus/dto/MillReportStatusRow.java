package ca.bc.gov.nrs.ilcr.millreportstatus.dto;

/**
 * One row of the Mill Status Report table (UC-MRPT-004) — where a single mill stands on each of the
 * two independent schedule tracks for one reporting year.
 *
 * <p>Six rendered columns: Mill Number, Mill, Region, Active, then one stacked cell per track. The
 * Schedules 1–10 cell stacks {@code openDate}/{@code draftDate}/{@code submitDate}/{@code
 * verifyDate}; the Schedule 11 cell stacks {@code openDate} again followed by {@code
 * silviDraftDate}/{@code silviSubmitDate}/{@code silviVerifyDate}. Both groups share ONE opened
 * value because the view has no {@code SILVI_STATUS_OPEN_DATE} and the Schedule 11 track has no
 * independent opened date (PRD FR9) — legacy re-renders the same field ({@code
 * millReportStatus.xhtml:103}).
 *
 * <p>The seven milestone strings are RAW: they carry the legacy status prefix ({@code "O:
 * 2021-01-05"}, {@code "D: "}) exactly as the view holds it, because the page's O/D/S/V legend is
 * what decodes that letter. They are not prefix-stripped anywhere on this surface, and a {@code
 * null} must render as an empty line — never the text {@code null}.
 *
 * <p>Carries NO personal data. Legacy's shared {@code MillReportStatusType} also held addresses,
 * contact names, phones and emails for the drill-down PDF; none of that belongs on a status table
 * (AD-11/NFR3), and the drill-down is Story 19.3.
 *
 * @param millId the mill id — the React row key and the arrival-order sort key; NOT the mill number
 * @param millNumber the mill number, the table's first (sortable) column
 * @param millName the mill (licensee) name, the second sortable column. Plain text: the legacy
 *     {@code p:commandLink} drill-down is Story 19.3 and is out of scope here
 * @param region the selling-price zone DESCRIPTION, or {@code null} when the code is absent or the
 *     lookup table cannot be read; the page renders {@code "-"} then, as legacy did
 * @param active whether the mill was ACTIVE IN THIS REPORTING YEAR (the view's {@code
 *     ILCR_MILL_STATUS_CODE} being {@code ACT}), rendered {@code Yes}/{@code No}. Never the mill's
 *     status today
 * @param openDate raw Schedules 1–10 Opened milestone; nullable. Rendered in BOTH track groups
 * @param draftDate raw Schedules 1–10 Draft milestone; nullable
 * @param submitDate raw Schedules 1–10 Submitted milestone; nullable
 * @param verifyDate raw Schedules 1–10 Verified milestone; nullable
 * @param silviDraftDate raw Schedule 11 Draft milestone; nullable
 * @param silviSubmitDate raw Schedule 11 Submitted milestone; nullable
 * @param silviVerifyDate raw Schedule 11 Verified milestone; nullable
 */
public record MillReportStatusRow(
    long millId,
    String millNumber,
    String millName,
    String region,
    boolean active,
    String openDate,
    String draftDate,
    String submitDate,
    String verifyDate,
    String silviDraftDate,
    String silviSubmitDate,
    String silviVerifyDate) {}
