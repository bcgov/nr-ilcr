package ca.bc.gov.nrs.ilcr.schedule6;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.ROAD_MAINTENANCE_REPORT} table
 * (AD-3), the Schedule 6 master record. Each row is one road-maintenance record keyed by
 * {@code ILCR_MILL_ID} + {@code REPORT_YEAR} + {@code ILCR_CATEGORY_ID = '6'}; the classification
 * is stored directly as the code strings ({@code TSA_NUMBER}/{@code TSB_NUMBER_CODE}/
 * {@code TFL_NUMBER_CODE}, delivery-DB confirmed). {@code COMMENTS} holds the schedule-level
 * general comment (replicated on every row in the legacy data model). Cost/volume hang off this in
 * {@code ILCR_COST_REPORT_DETAIL} (see {@link CostReportDetailEntity}). The aggregate type for
 * {@link Schedule6Repository}; write SQL stays explicit ({@code @Query}) because the model is a
 * legacy-projection, not a save()-driven aggregate.
 */
@Table(name = "ROAD_MAINTENANCE_REPORT", schema = "THE")
public record RoadMaintenanceReportEntity(
    @Id @Column("ROAD_MAINTENANCE_REPORT_ID") Integer roadMaintenanceReportId,
    @Column("TSA_NUMBER") String tsaNumber,
    @Column("TSB_NUMBER_CODE") String tsbNumberCode,
    @Column("TFL_NUMBER_CODE") String tflNumberCode,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
