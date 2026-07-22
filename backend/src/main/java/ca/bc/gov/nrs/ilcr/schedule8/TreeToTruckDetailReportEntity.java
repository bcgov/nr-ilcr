package ca.bc.gov.nrs.ilcr.schedule8;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for a Schedule 8 sample ({@code THE.TREE_TO_TRUCK_DETAIL_REPORT}, AD-3) —
 * a child of {@link TreeToTruckReportEntity} via {@code TREE_TO_TRUCK_REPORT_ID}. Derived roll-ups
 * ({@code percentTotal}, {@code actualHarvested}, {@code additionsTotal}/{@code deductionsTotal},
 * {@code finalRate}) are computed in {@code Schedule8Service} (AD-5/AD-6), never stored here.
 */
@Table(name = "TREE_TO_TRUCK_DETAIL_REPORT", schema = "THE")
public record TreeToTruckDetailReportEntity(
    @Id @Column("TREE_TO_TRUCK_DETAIL_REPORT_ID") Integer id,
    @Column("TREE_TO_TRUCK_REPORT_ID") Integer reportId,
    @Column("CONTRACTOR_ID") String contractId,
    @Column("CUT_BLOCK") String cutBlock,
    @Column("GROUND_BASE_PCT") Integer groundBasePct,
    @Column("GRAPPLE_PCT") Integer grapplePct,
    @Column("SKYLINE_PCT") Integer skylinePct,
    @Column("HIGHLEAD_PCT") Integer highleadPct,
    @Column("HELICOPTER_PCT") Integer helicopterPct,
    @Column("OTHER_SKIDDING_PCT") Integer otherSkiddingPct,
    @Column("SKYLINE_SLOPE_DISTANCE") Integer skylineSlopeDistance,
    @Column("SKYLINE_SUPPORT_NUMBER") Integer skylineSupportNumber,
    @Column("SUPPORT_AVERAGE_DISTANCE") BigDecimal supportAverageDistance,
    @Column("CYCLE_TIME") BigDecimal cycleTime,
    @Column("DISTANCE") BigDecimal distance,
    @Column("WATER_DUMP_DESTINATION_IND") String waterDumpDestinationInd,
    @Column("UPHILL_DIRECTION_IND") String uphillDirectionInd,
    @Column("ILCR_SKID_TYPE_CODE") String skidTypeCode,
    @Column("CONIFEROUS_VOLUME") Integer coniferousVolume,
    @Column("DECIDUOUS_VOLUME") Integer deciduousVolume,
    @Column("ORIGINAL_TREE_TO_TRUCK_RATE") BigDecimal originalRate,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
