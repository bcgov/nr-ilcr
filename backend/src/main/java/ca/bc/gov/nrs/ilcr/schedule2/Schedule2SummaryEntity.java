package ca.bc.gov.nrs.ilcr.schedule2;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Minimal Spring Data JDBC row shape for the Schedule 2 view of {@code THE.ILCR_REPORT_SUMMARY} (AD-3). */
@Table(name = "ILCR_REPORT_SUMMARY", schema = "THE")
public record Schedule2SummaryEntity(
    @Id @Column("ILCR_REPORT_SUMMARY_ID") Integer summaryId,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
