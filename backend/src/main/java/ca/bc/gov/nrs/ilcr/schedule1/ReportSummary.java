package ca.bc.gov.nrs.ilcr.schedule1;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for the legacy {@code THE.ILCR_REPORT_SUMMARY} table (AD-3). This
 * type exists only to give {@link Schedule1Repository} a mapped root; all Schedule 1 access goes
 * through explicit {@code @Query} methods that project into the repository's row records, and the
 * service maps those to DTOs (entities never cross the service boundary — AD-3). It is never
 * persisted via the CRUD API, so the detail-row sequence is written explicitly in the insert SQL.
 */
@Table(schema = "THE", name = "ILCR_REPORT_SUMMARY")
public record ReportSummary(
    @Id @Column("ILCR_REPORT_SUMMARY_ID") Long id,
    @Column("ILCR_MILL_ID") Long millId,
    @Column("REPORT_YEAR") Integer reportYear,
    @Column("ILCR_CATEGORY_ID") String categoryId,
    @Column("CROWN_VOLUME") Integer crownVolume,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
