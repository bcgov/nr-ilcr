package ca.bc.gov.nrs.ilcr.schedule10.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One Schedule 10 road-detail row.
 *
 * <p><strong>Three legacy fields are deliberately absent</strong> per the business-directed
 * departures confirmed 2026-08-11: {@code asmCode} (LD-1), {@code soilMoistureCode} (LD-2) and
 * {@code boulderAreaPct} (LD-3). Any of them appearing in a response is a defect. Their removal
 * also
 * eliminates legacy BR-06's runtime filtering, so slice S13 no longer applies.
 *
 * @param roadDetailId the {@code ROAD_CONSTRUCTION_REPRT_DTL_ID}
 * @param rowNumber positional 1-based number assigned on read; not stored
 *     ({@code Schedule10DAO:405})
 * @param roadDetailLabel the legacy summary-list label, {@code "Road #{n}, {name}"}
 * @param roadName the road name
 * @param roadLifetimeCode the Road Type code
 * @param becClassification the stored BEC classification, served structurally so a later base-zone
 *     reduction (LD-4, provisional) is a projection change rather than a breaking one
 * @param relSoilMoistRgmClsCode the RSMR class; nullable in delivery despite the view marking it
 *     required, and absent on most real rows
 * @param sideSlopePct the side slope percentage
 * @param subGrade the sub-grade substructure with its six deduction lines
 * @param stabilizing the additional-stabilizing substructure
 * @param materialComposition the material composition percentages and their total
 * @param detailedEngineeringCostInd the legacy {@code Y}/{@code N} indicator, served verbatim
 *     rather than as a boolean so the stored value round-trips unchanged
 * @param endHaulDistance the end-haul distance, which may be negative
 * @param endHaulVolume the end-haul volume
 * @param overlandDistance the overland distance, which may be negative
 * @param overlandVolume the overland volume
 * @param comments the free-text comments
 * @param revisionCount the per-row optimistic-lock counter
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoadDetail(
    int roadDetailId,
    int rowNumber,
    String roadDetailLabel,
    String roadName,
    String roadLifetimeCode,
    BecClassification becClassification,
    String relSoilMoistRgmClsCode,
    Integer sideSlopePct,
    SubGrade subGrade,
    Stabilizing stabilizing,
    MaterialComposition materialComposition,
    String detailedEngineeringCostInd,
    BigDecimal endHaulDistance,
    BigDecimal endHaulVolume,
    BigDecimal overlandDistance,
    BigDecimal overlandVolume,
    String comments,
    Integer revisionCount) {
}
