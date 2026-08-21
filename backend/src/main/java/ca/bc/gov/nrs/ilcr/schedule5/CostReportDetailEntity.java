package ca.bc.gov.nrs.ilcr.schedule5;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.ILCR_COST_REPORT_DETAIL} table as
 * used by Schedule 5 (AD-3). Schedule 5 needs its own projection rather than reusing Schedule 6's:
 * the rows hang off a {@code CAMP_REPORT} via {@code CAMP_REPORT_ID} (not {@code
 * ROAD_MAINTENANCE_REPORT_ID}), and a camp carries MANY rows keyed by {@code
 * ILCR_REPORT_COST_ITEM_ID} rather than the single item-69 row a road record carries.
 *
 * <p>{@code COST} is whole dollars ({@code NUMBER(8,0)}, so an {@code Integer} holds any single
 * stored value; the service widens to {@code Long} before summing). {@code VOLUME} is m&sup3;
 * ({@code NUMBER(10,2)}). The item id decides which category the row feeds — items
 * 56/58/59/60/61/63-67/141/142 are the fixed grid, 62/68 are the sub-page rows the read side only
 * counts, and anything else is dropped with a warning (legacy parity, {@code
 * Schedule5DAO.java:283-285}).
 *
 * <p>{@code ITEM_DESCRIPTION} ({@code VARCHAR2(120)} in delivery, Task 1 gate (vii)) is set only on
 * the item-62/68 sub-page rows and is read for Check Status, whose fifth and seventh conditions
 * flag any such row with a null or empty description ({@code CheckStatusUtil.java:132-139}). The
 * twelve fixed-grid rows leave it NULL, exactly as legacy does. The served document does not expose
 * it — itemizing those rows is Story 7.4's.
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record CostReportDetailEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Integer detailId,
    @Column("CAMP_REPORT_ID") Integer campReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemId,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost,
    @Column("ITEM_DESCRIPTION") String itemDescription) {}
