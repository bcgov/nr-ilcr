package ca.bc.gov.nrs.ilcr.originalvalue;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code THE.ILCR_COST_REPORT_DETAIL_S_VW} — the licensee's
 * submitted cost-detail rows (AD-3). This one view backs every schedule's cost and volume figures,
 * because {@code ILCR_COST_REPORT_DETAIL} is the shared cost table for all of them: which schedule
 * a row belongs to is decided by which of its ten parent foreign keys is non-null.
 *
 * <p>A <b>view</b>, and read-only by nature: legacy mapped its counterpart {@code @Immutable}
 * ({@code ILCRCostReportDetailOv.java:17-18}). Nothing may ever write here. As with every other
 * entity in this codebase it exists only to give {@link CostDetailSnapshotRepository} a mapped
 * root; all access is through explicit {@code @Query} methods projecting into row records, and the
 * service maps those onward (entities never cross the service boundary — AD-3).
 */
@Table(schema = "THE", name = "ILCR_COST_REPORT_DETAIL_S_VW")
public record CostDetailSnapshot(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Long id,
    @Column("ILCR_REPORT_SUMMARY_ID") Integer summaryId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemCode,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost,
    @Column("ITEM_DESCRIPTION") String itemDescription,
    @Column("COMMENTS") String comments) {}
