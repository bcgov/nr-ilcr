package ca.bc.gov.nrs.ilcr.schedule8;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for a Schedule 8 report page ({@code THE.TREE_TO_TRUCK_REPORT}, AD-3).
 * The aggregate type for {@link Schedule8Repository}. Code fields are surfaced as their stored values
 * (label/Road-Group derivation deferred — Story 14.1 §Decisions 2/3).
 */
@Table(name = "TREE_TO_TRUCK_REPORT", schema = "THE")
public record TreeToTruckReportEntity(
    @Id @Column("TREE_TO_TRUCK_REPORT_ID") Integer id,
    @Column("ILCR_SUPPORT_CENTRE_CODE") String supportCentre,
    @Column("ILCR_FOREST_REGION_CODE") String region,
    @Column("BEC_ZONE_CODE") String becZone,
    @Column("TSA_NUMBER") String tsaNumber,
    @Column("TSB_NUMBER_CODE") String supplyBlock,
    @Column("TFL_NUMBER_CODE") String tflNumber,
    @Column("CUTTING_PERMIT_NUMBER") String cuttingPermit,
    @Column("HARVEST_LICENSE_NUMBER") String license,
    @Column("DIVISION_LOCATION") String division,
    @Column("CONTACT_NAME") String contact,
    @Column("CONTACT_PHONE_NUMBER") String phone,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
