package ca.bc.gov.nrs.ilcr.schedule7b;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 7B culvert cost detail (AD-3): a {@code
 * THE.ILCR_COST_REPORT_DETAIL} row belonging to a culvert, keyed by {@code CULVERT_REPORT_ID} plus
 * one of the two fixed Schedule 7B cost items — {@code 77} material /{@code 78} installation
 * (legacy {@code REPORT_COST_ITEMS.Schedule7_30_Material}/{@code Schedule7_30_Installation}, {@code
 * dao/Constant.java:349}). {@code ILCR_REPORT_SUMMARY_ID} is NULL for this list schedule; {@code
 * VOLUME}/{@code ITEM_DESCRIPTION} are unused by Schedule 7B. Never crosses the service boundary —
 * {@link Schedule7bService} routes each cost row to its culvert field by item id.
 *
 * @param costDetailId the detail PK ({@code ILCR_COST_REPORT_DETAIL_ID})
 * @param culvertReportId the owning culvert id (no FK constraint in delivery)
 * @param costItemId the cost item id (77 or 78 after the query filter)
 * @param cost the whole-dollar cost ({@code NUMBER}); nullable — a cleared cost is stored as a NULL
 *     row, never as a missing row (see {@code Schedule7bService.writeCosts})
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record CulvertCostEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") long costDetailId,
    @Column("CULVERT_REPORT_ID") long culvertReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") int costItemId,
    @Column("COST") Integer cost) {
}
