package ca.bc.gov.nrs.ilcr.schedule10;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One Schedule 10 road-detail row — a {@code THE.ROAD_CONSTRUCTION_REPRT_DTL} row.
 *
 * <p>Shape is delivery-verified (Story 11.1 Task 1 gate (i)). Costs are NOT columns here: they are
 * keyed rows in {@code THE.ILCR_COST_REPORT_DETAIL} joined by {@code
 * ROAD_CONSTRUCTION_REPRT_DTL_ID} (BR-08), reassembled by {@link Schedule10Repository}.
 *
 * <p><strong>Three delivery columns are deliberately absent</strong> — business-directed departures
 * LD-1/LD-2/LD-3 remove ASM Code ({@code RELATIVE_SOIL_MOISTUR_RGM_CODE}), Soil Moisture Code
 * ({@code ILCR_SOIL_MOISTURE_CODE}) and Boulder Area % ({@code BOULDER_AREA_PCT}). They still exist
 * in the table and the first two are {@code NOT NULL} there, which is a hard problem for the Story
 * 11.2 write path but none at all for this read: we simply never select them.
 *
 * <p><strong>{@code REL_SOIL_MOIST_RGM_CLS_CODE} (RSMR Class) is nullable</strong> despite the
 * legacy view marking it required — it is populated in only 18 of 66 real delivery rows.
 *
 * <p>All numerics are boxed. Oracle {@code NUMBER} arrives as {@code BigDecimal} and null is a
 * first-class value throughout Schedule 10 (see {@link Schedule10Amounts}).
 */
@Table(name = "ROAD_CONSTRUCTION_REPRT_DTL", schema = "THE")
public record RoadConstructionReportDetailEntity(
    @Id @Column("ROAD_CONSTRUCTION_REPRT_DTL_ID") Integer roadConstructionReprtDtlId,
    @Column("ROAD_CONSTRUCTION_REPRT_ID") Integer roadConstructionReprtId,
    @Column("ROAD_NAME") String roadName,
    @Column("SIDE_SLOPE_PCT") Integer sideSlopePct,
    @Column("ILCR_ROAD_LIFETIME_CODE") String ilcrRoadLifetimeCode,
    @Column("RIPPABLE_ROCK_PCT") Integer rippableRockPct,
    @Column("SOLID_ROCK_PCT") Integer solidRockPct,
    @Column("COARSE_MATERIAL_PCT") Integer coarseMaterialPct,
    @Column("BECBIOGEO_CATALOGUE_ID") Integer becbiogeoCatalogueId,
    @Column("FINE_MATERIAL_PCT") Integer fineMaterialPct,
    @Column("ORGANIC_MATERIAL_PCT") Integer organicMaterialPct,
    @Column("SUB_GRADE_LENGTH") BigDecimal subGradeLength,
    @Column("DETAIL_ENGINEERING_COST_IND") String detailEngineeringCostInd,
    @Column("END_HAUL_DISTANCE") BigDecimal endHaulDistance,
    @Column("END_HAUL_VOLUME") BigDecimal endHaulVolume,
    @Column("OVERLAND_DISTANCE") BigDecimal overlandDistance,
    @Column("OVERLAND_VOLUME") BigDecimal overlandVolume,
    @Column("ILCR_ROAD_BALLAST_METHOD_CODE") String ilcrRoadBallastMethodCode,
    @Column("SUB_GRADE_SURFACE_WIDTH") BigDecimal subGradeSurfaceWidth,
    @Column("ILCR_ROAD_BALLAST_MATERL_CODE") String ilcrRoadBallastMaterlCode,
    @Column("STABILIZING_LENGTH") BigDecimal stabilizingLength,
    @Column("STABILIZING_SURFACE_WIDTH") BigDecimal stabilizingSurfaceWidth,
    @Column("STABILIZING_DEPTH") BigDecimal stabilizingDepth,
    @Column("STABILIZING_DISTANCE_TO_SOURCE") BigDecimal stabilizingDistanceToSource,
    @Column("REL_SOIL_MOIST_RGM_CLS_CODE") String relSoilMoistRgmClsCode,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {}
