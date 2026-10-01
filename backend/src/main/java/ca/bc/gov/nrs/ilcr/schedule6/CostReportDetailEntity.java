package ca.bc.gov.nrs.ilcr.schedule6;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.ILCR_COST_REPORT_DETAIL} table as
 * used by Schedule 6 (AD-3): the cost/volume/comment for a road record hangs off a {@code
 * ROAD_MAINTENANCE_REPORT} via {@code ROAD_MAINTENANCE_REPORT_ID} and is the single detail row for
 * cost item {@code 69} ({@code Schedule6_1_Cost}). {@code COST} is whole dollars; {@code VOLUME} is
 * m&sup3;; {@code COMMENTS} is the per-record comment (distinct from the schedule-level general
 * comment on the master row).
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record CostReportDetailEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Integer detailId,
    @Column("ROAD_MAINTENANCE_REPORT_ID") Integer roadMaintenanceReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemCode,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost,
    @Column("COMMENTS") String comments) {}
