package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for a RANGE read of {@code THE.ILCR_MILL_REPORT_STATUS} (AD-3) — many
 * mills across several years in one query. Unlike {@link MillReportStatusEntity}, which serves the
 * 0..1 (mill, year) read, this carries the year, because the caller needs to know which pair each
 * row belongs to. The table PK is composite and Spring Data JDBC wants one {@code @Id} column for a
 * read projection; the mill id stands in, and repeats across years are fine for a query result.
 *
 * @param millId the mill id ({@code ILCR_MILL_ID}); read {@code @Id} only
 * @param year the reporting year
 * @param schedules1To10Code the Schedules 1–10 code; nullable
 * @param schedule11Code the Schedule 11 code; nullable
 */
@Table(name = "ILCR_MILL_REPORT_STATUS", schema = "THE")
public record MillYearReportStatusEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("REPORT_YEAR") int year,
    @Column("ILCR_MILL_REPORT_STATUS_CODE") String schedules1To10Code,
    @Column("MILL_SILVICULTUR_STATUS_CODE") String schedule11Code) {}
