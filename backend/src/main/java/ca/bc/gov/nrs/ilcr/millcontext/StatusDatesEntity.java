package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW} row — the raw
 * (still 3-char-prefixed) per-status display dates (AD-3). On the delivery DB this is a VIEW with no
 * PK/uniqueness guarantee, so {@link MillContextRepository#findStatusDates} reads a list and takes
 * the first row (legacy {@code MillReportStatusDAO} {@code get(0)} parity); {@code ILCR_MILL_ID}
 * serves only as the read {@code @Id}. Any date component may be null. {@link MillContextRepository}
 * maps it to {@link MillContextRepository.StatusDates}.
 *
 * @param millId the mill id ({@code ILCR_MILL_ID}); read {@code @Id} only
 * @param open1To10 {@code MILL_STATUS_OPEN_DATE}; nullable
 * @param draft1To10 {@code MILL_STATUS_DRAFT_DATE}; nullable
 * @param submit1To10 {@code MILL_STATUS_SUBMIT_DATE}; nullable
 * @param verify1To10 {@code MILL_STATUS_VERIFY_DATE}; nullable
 * @param draftSilvi {@code SILVI_STATUS_DRAFT_DATE}; nullable
 * @param submitSilvi {@code SILVI_STATUS_SUBMIT_DATE}; nullable
 * @param verifySilvi {@code SILVI_STATUS_VERIFY_DATE}; nullable
 */
@Table(name = "ILCR_MILL_REPORT_STATUS_RPT_VW", schema = "THE")
public record StatusDatesEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("MILL_STATUS_OPEN_DATE") String open1To10,
    @Column("MILL_STATUS_DRAFT_DATE") String draft1To10,
    @Column("MILL_STATUS_SUBMIT_DATE") String submit1To10,
    @Column("MILL_STATUS_VERIFY_DATE") String verify1To10,
    @Column("SILVI_STATUS_DRAFT_DATE") String draftSilvi,
    @Column("SILVI_STATUS_SUBMIT_DATE") String submitSilvi,
    @Column("SILVI_STATUS_VERIFY_DATE") String verifySilvi) {
}
