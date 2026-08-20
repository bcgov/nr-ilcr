package ca.bc.gov.nrs.ilcr.schedule10;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A Schedule 10 construction page — one {@code THE.ROAD_CONSTRUCTION_REPRT} row.
 *
 * <p>Shape is delivery-verified (Story 11.1 Task 1 gate (i)). Two things to note:
 *
 * <p><strong>There is no Road Group column.</strong> {@code RMG} is derived on every read from the
 * TSA/TSB or TFL tables via {@link RoadGroup10Lookup}, never stored.
 *
 * <p><strong>{@code CONSTRUCTION_DIVISION_NAME} is {@code VARCHAR2(20)}</strong> even though
 * {@code schedule10.xhtml:140} sets {@code maxlength="30"} — a real defect recorded for Story 11.2.
 * This read story is unaffected.
 *
 * <p>Numeric columns are boxed: ojdbc maps Oracle {@code NUMBER} to {@code BigDecimal} and a
 * primitive would NPE on a null. {@code REPORT_YEAR} and {@code ILCR_MILL_ID} are {@code NOT NULL}
 * in delivery but stay boxed for consistency with the shipped schedules.
 */
@Table(name = "ROAD_CONSTRUCTION_REPRT", schema = "THE")
public record RoadConstructionReportEntity(
    @Id @Column("ROAD_CONSTRUCTION_REPRT_ID") Integer roadConstructionReprtId,
    @Column("REPORT_YEAR") Integer reportYear,
    @Column("ILCR_MILL_ID") Long ilcrMillId,
    @Column("ILCR_CATEGORY_ID") String ilcrCategoryId,
    @Column("CONSTRUCTION_PERIOD") String constructionPeriod,
    @Column("CONSTRUCTION_DIVISION_NAME") String constructionDivisionName,
    @Column("ILCR_FOREST_REGION_CODE") String ilcrForestRegionCode,
    @Column("TSB_NUMBER_CODE") String tsbNumberCode,
    @Column("TSA_NUMBER") String tsaNumber,
    @Column("TFL_NUMBER_CODE") String tflNumberCode,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
