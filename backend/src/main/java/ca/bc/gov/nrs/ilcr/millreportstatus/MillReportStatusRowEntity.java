package ca.bc.gov.nrs.ilcr.millreportstatus;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one line of the Mill Status Report table (AD-3), projected by
 * explicit {@code @Query} from {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW} joined to {@code
 * THE.MILL}.
 *
 * <p>The driving table is the report-status view, which is what makes the report year the only
 * input: a mill appears in the table exactly when it has a row there for the selected year.
 *
 * <p>The SEVEN milestone columns are {@code VARCHAR2(30)} strings carrying their legacy status
 * prefix — {@code "O: 2021-01-05"}, {@code "D: "} — and they are NOT stripped anywhere on this
 * surface. The status letter inside the value is exactly what the page's O/D/S/V legend decodes
 * ({@code millReportStatus.xhtml:57-73}); legacy binds them straight to {@code h:outputText}
 * ({@code :93-110}). Stripping would delete the information the legend exists to explain. Contrast
 * {@code MillInformationRowEntity}, whose PDF labels each milestone in words and therefore does
 * strip.
 *
 * <p>There is deliberately no {@code SILVI_STATUS_OPEN_DATE}: the view has no such column and the
 * Schedule 11 track has no independent opened date (PRD FR9). Legacy re-renders the Schedules 1–10
 * open date in the Schedule 11 column group ({@code millReportStatus.xhtml:103}), so one field
 * serves both groups.
 *
 * <p>Every column outboard of {@code MILL} is nullable, and null is the common shape rather than
 * the edge: 80 of the 118 delivery rows carry an unreached milestone. Mapped by {@code THE} column
 * name — this never crosses the service boundary.
 *
 * @param millId the mill id ({@code ILCR_MILL_ID}) — the view's row key and the join key to {@code
 *     MILL}
 * @param millNumber the mill number ({@code MILL.MILL_NUMBER}); the table's first column, and NOT
 *     the same value as the mill id
 * @param millName the mill name ({@code MILL.MILL_NAME}); legacy's "Mill" / licensee column
 * @param millStatusCode {@code ACT}/{@code CLS} for the REPORTING YEAR, from the view — not the
 *     mill's status today, which is what {@code ILCR_MILL_STATUS_XREF} carries
 * @param regionCode the selling-price zone code; nullable. Its DESCRIPTION is resolved separately
 *     (never joined) — see {@link MillReportStatusService}
 * @param openDate raw prefixed Schedules 1–10 Opened milestone; nullable. Rendered in BOTH track
 *     column groups
 * @param draftDate raw prefixed Schedules 1–10 Draft milestone; nullable
 * @param submitDate raw prefixed Schedules 1–10 Submitted milestone; nullable
 * @param verifyDate raw prefixed Schedules 1–10 Verified milestone; nullable
 * @param silviDraftDate raw prefixed Schedule 11 Draft milestone; nullable
 * @param silviSubmitDate raw prefixed Schedule 11 Submitted milestone; nullable
 * @param silviVerifyDate raw prefixed Schedule 11 Verified milestone; nullable
 */
@Table(name = "ILCR_MILL_REPORT_STATUS_RPT_VW", schema = "THE")
public record MillReportStatusRowEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("MILL_NUMBER") String millNumber,
    @Column("MILL_NAME") String millName,
    @Column("ILCR_MILL_STATUS_CODE") String millStatusCode,
    @Column("REGION_CODE") String regionCode,
    @Column("MILL_STATUS_OPEN_DATE") String openDate,
    @Column("MILL_STATUS_DRAFT_DATE") String draftDate,
    @Column("MILL_STATUS_SUBMIT_DATE") String submitDate,
    @Column("MILL_STATUS_VERIFY_DATE") String verifyDate,
    @Column("SILVI_STATUS_DRAFT_DATE") String silviDraftDate,
    @Column("SILVI_STATUS_SUBMIT_DATE") String silviSubmitDate,
    @Column("SILVI_STATUS_VERIFY_DATE") String silviVerifyDate) {}
