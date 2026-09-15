package ca.bc.gov.nrs.ilcr.originalvalue;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code THE.ILCR_REPORT_SUMMARY_S_VW} — the licensee's
 * submitted summary-level figures for the summary-shaped schedules 1, 2 and 3 (AD-3).
 *
 * <p>A <b>view</b>, read-only, legacy's {@code ILCRReportSummaryOv} ({@code :17}).
 *
 * <p>{@code LOCATION} is not a location here. Schedule 3 stores its "override total POP" flag in
 * that column — legacy reads the current value with {@code getLocation()} and the submitted one
 * with {@code getOriginal().getLocation()} ({@code Schedule3DAO.java:135-136}) — so the column name
 * does not name the field, and the mapping below keeps the legacy meaning rather than the column's
 * spelling.
 *
 * <p>{@code CROWN_VOLUME} is exposed by the view and by legacy's entity, but <b>no legacy DAO ever
 * reads it</b> and no schedule view renders a crown-volume indicator, so nothing here surfaces it
 * either (Story 16.2 deviation D9 — "only if the legacy app does it"). It is mapped so the next
 * reader can see it was considered rather than missed.
 */
@Table(schema = "THE", name = "ILCR_REPORT_SUMMARY_S_VW")
public record ReportSummarySnapshot(
    @Id @Column("ILCR_REPORT_SUMMARY_ID") Long id,
    @Column("CROWN_VOLUME") Integer crownVolume,
    @Column("LOCATION") String overrideTotalPop,
    @Column("COMMENTS") String comments) {}
