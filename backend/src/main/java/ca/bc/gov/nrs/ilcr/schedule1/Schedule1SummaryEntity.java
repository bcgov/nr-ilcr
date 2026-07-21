package ca.bc.gov.nrs.ilcr.schedule1;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Minimal Spring Data JDBC row shape for the legacy THE.ILCR_REPORT_SUMMARY table. */
@Table(name = "ILCR_REPORT_SUMMARY", schema = "THE")
public record Schedule1SummaryEntity(
    @Id @Column("ILCR_REPORT_SUMMARY_ID") Integer summaryId,
    @Column("CROWN_VOLUME") Integer crownVolume,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
