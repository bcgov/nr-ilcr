package ca.bc.gov.nrs.ilcr.schedule8;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for a Schedule 8 rate-adjustment row ({@code THE.TREE_TO_TRUCK_RATE_DETAIL},
 * AD-3) — a child of {@link TreeToTruckDetailReportEntity} via {@code TREE_TO_TRUCK_DETAIL_REPORT_ID}.
 * The row carries no add/deduct flag; {@code Schedule8Service} decides addition vs deduction from the
 * cost item's subcategory (§Decision 1) and resolves {@code costTypeCode}'s label (§Decision 3). The
 * stored {@code RATE_COST_TYPE_DESCRIPTION} column is intentionally not read — the label comes from
 * the {@code ILCR_RATE_COST_TYPE_CODE} code table so it stays consistent with the other code lookups.
 */
@Table(name = "TREE_TO_TRUCK_RATE_DETAIL", schema = "THE")
public record TreeToTruckRateDetailEntity(
    @Id @Column("TREE_TO_TRUCK_RATE_DETAIL_ID") Integer id,
    @Column("TREE_TO_TRUCK_DETAIL_REPORT_ID") Integer detailReportId,
    @Column("ILCR_RATE_COST_TYPE_CODE") String costTypeCode,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemCode,
    @Column("ITEM_DESCRIPTION") String itemDescription,
    @Column("COSTING_RATE") BigDecimal costingRate,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
