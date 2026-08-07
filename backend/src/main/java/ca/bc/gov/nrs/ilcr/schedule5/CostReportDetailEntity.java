package ca.bc.gov.nrs.ilcr.schedule5;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.ILCR_COST_REPORT_DETAIL} table as
 * used by Schedule 5 (AD-3). Schedule 5 needs its own projection rather than reusing Schedule 6's:
 * the rows hang off a {@code CAMP_REPORT} via {@code CAMP_REPORT_ID} (not
 * {@code ROAD_MAINTENANCE_REPORT_ID}), and a camp carries MANY rows keyed by
 * {@code ILCR_REPORT_COST_ITEM_ID} rather than the single item-69 row a road record carries.
 *
 * <p>{@code COST} is whole dollars ({@code NUMBER(8,0)}, so an {@code Integer} holds any single
 * stored value; the service widens to {@code Long} before summing). {@code VOLUME} is m&sup3;
 * ({@code NUMBER(10,2)}). The item id decides which category the row feeds — items
 * 56/58/59/60/61/63-67/141/142 are the fixed grid, 62/68 are the sub-page rows this story only
 * counts, and anything else is dropped with a warning (legacy parity,
 * {@code Schedule5DAO.java:283-285}).
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record CostReportDetailEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Integer detailId,
    @Column("CAMP_REPORT_ID") Integer campReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemId,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost) {
}
