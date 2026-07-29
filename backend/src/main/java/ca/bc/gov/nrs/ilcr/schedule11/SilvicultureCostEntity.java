package ca.bc.gov.nrs.ilcr.schedule11;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 11 cost detail (AD-3): a
 * {@code THE.ILCR_COST_REPORT_DETAIL} row belonging to a silviculture location (24 = Actual,
 * 23 = Planned — legacy {@code REPORT_COST_ITEMS.Schedule11_1_*}). {@code VOLUME} and
 * {@code ITEM_DESCRIPTION} are unused by Schedule 11 and not read. Never crosses the service
 * boundary — {@code Schedule11Service} unpacks these into the per-location cost fields.
 *
 * @param costDetailId the detail PK ({@code ILCR_COST_REPORT_DETAIL_ID})
 * @param basicSilvicultureReportId the owning location id (no FK constraint in delivery — AC9)
 * @param costItemId the cost item id (23/24 after the query filter)
 * @param cost the whole-dollar cost ({@code NUMBER(8,0)}); nullable
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record SilvicultureCostEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") long costDetailId,
    @Column("BASIC_SILVICULTURE_REPORT_ID") long basicSilvicultureReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") int costItemId,
    @Column("COST") Integer cost) {
}
