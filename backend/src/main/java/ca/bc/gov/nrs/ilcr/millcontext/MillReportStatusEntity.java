package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for the two independent track status CODES of one
 * {@code THE.ILCR_MILL_REPORT_STATUS} row (AD-3). The table PK is composite
 * ({@code REPORT_YEAR, ILCR_MILL_ID}); the (mill, year)-filtered read returns 0..1 row, so
 * {@code ILCR_MILL_ID} serves as the read {@code @Id}. Either status code may be NULL (S07 — legacy
 * would NPE on a NULL silviculture code; the service tolerates it). {@link MillContextRepository}
 * maps it to {@link MillContextRepository.TrackCodes}.
 *
 * @param millId the mill id ({@code ILCR_MILL_ID}); read {@code @Id} only
 * @param schedules1To10Code the Schedules 1–10 code ({@code ILCR_MILL_REPORT_STATUS_CODE}); nullable
 * @param schedule11Code the Schedule 11 code ({@code MILL_SILVICULTUR_STATUS_CODE}); nullable
 */
@Table(name = "ILCR_MILL_REPORT_STATUS", schema = "THE")
public record MillReportStatusEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("ILCR_MILL_REPORT_STATUS_CODE") String schedules1To10Code,
    @Column("MILL_SILVICULTUR_STATUS_CODE") String schedule11Code) {
}
