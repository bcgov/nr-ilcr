package ca.bc.gov.nrs.ilcr.schedule7a;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 7A bridge cost detail (AD-3): a {@code
 * THE.ILCR_COST_REPORT_DETAIL} row belonging to a bridge, keyed by {@code BRIDGE_REPORT_ID} plus
 * the fixed Schedule 7 cost-item id ({@code ILCR_REPORT_COST_ITEM_ID} ∈
 * {70,71,72,73,74,75,76,79,80,81} — legacy {@code REPORT_COST_ITEMS.Schedule7_*}). {@code
 * ILCR_REPORT_SUMMARY_ID} is NULL for this list schedule. {@code VOLUME}/{@code COMMENTS} are
 * unused by Schedule 7A. Never crosses the service boundary — {@link Schedule7aService} routes each
 * cost row to its bridge field by item id.
 *
 * @param costDetailId the detail PK ({@code ILCR_COST_REPORT_DETAIL_ID})
 * @param bridgeReportId the owning bridge id (no FK constraint in delivery)
 * @param costItemId the cost item id (70-76/79-81 after the query filter)
 * @param cost the whole-dollar cost ({@code NUMBER}); nullable
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record BridgeCostEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") long costDetailId,
    @Column("BRIDGE_REPORT_ID") long bridgeReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") int costItemId,
    @Column("COST") Integer cost) {}
