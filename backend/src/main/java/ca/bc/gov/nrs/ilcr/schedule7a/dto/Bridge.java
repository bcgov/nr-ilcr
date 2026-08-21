package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import java.math.BigDecimal;

/**
 * One Schedule 7A bridge on the served document (AD-12). Attribute, measurement, and cost fields
 * are the stored values; the four total fields are SERVER-COMPUTED (BR-06) from the costs and are
 * read-only — never stored, never client-supplied. Jackson serializes non-null only, so an absent
 * value (a blank optional cost, a total with no contributing costs) is omitted.
 *
 * <p>Legacy shape source: {@code service/domain/type/BridgeReportType.java}. Costs are whole-dollar
 * {@code Integer} (legacy {@code COST} is {@code Integer}, stored via {@code intValueExact()});
 * {@code builtDate} is {@code yyyy-MM}; {@code abutmentHeight}/{@code length}/{@code width} are
 * one-decimal metres.
 *
 * @param bridgeReportId the bridge id
 * @param rowCounter the 1-based list index (legacy display id, used in check-status messages)
 * @param locationName the bridge name/location
 * @param builtDate the completion date ({@code yyyy-MM})
 * @param constructionTypeCode the New/Used construction type code
 * @param superstructureTypeCode the superstructure type code
 * @param deckTypeCode the decking type code
 * @param abutmentTypeCode the abutment type code
 * @param loadRatingCode the load rating code
 * @param lifeSpan the expected life span
 * @param abutmentHeight the abutment height (m)
 * @param length the bridge length (m)
 * @param width the deck width (m)
 * @param distance the distance from storage (km)
 * @param sitePlanCost site plan / general arrangement (item 70)
 * @param superstructureMaterialCost superstructure material (item 79)
 * @param superstructureDeliverCost superstructure deliver (item 80)
 * @param superstructureInstallCost superstructure install (item 81)
 * @param abutmentMaterialCost abutment material (item 74)
 * @param abutmentDeliverCost abutment deliver (item 75)
 * @param abutmentInstallCost abutment install (item 76)
 * @param approachCost approach works (item 71)
 * @param afterInstallCost certification after install (item 72)
 * @param otherCost other costs (item 73)
 * @param comments the row comments
 * @param totalMaterial read-only derived = superstructure + abutment material
 * @param totalDeliver read-only derived = superstructure + abutment deliver
 * @param totalInstall read-only derived = superstructure + abutment install
 * @param grandTotal read-only derived = sitePlan + totals + approach + afterInstall + other
 * @param revisionCount the per-row optimistic-lock token
 */
public record Bridge(
    long bridgeReportId,
    int rowCounter,
    String locationName,
    String builtDate,
    String constructionTypeCode,
    String superstructureTypeCode,
    String deckTypeCode,
    String abutmentTypeCode,
    String loadRatingCode,
    Integer lifeSpan,
    BigDecimal abutmentHeight,
    BigDecimal length,
    BigDecimal width,
    Integer distance,
    Integer sitePlanCost,
    Integer superstructureMaterialCost,
    Integer superstructureDeliverCost,
    Integer superstructureInstallCost,
    Integer abutmentMaterialCost,
    Integer abutmentDeliverCost,
    Integer abutmentInstallCost,
    Integer approachCost,
    Integer afterInstallCost,
    Integer otherCost,
    String comments,
    Integer totalMaterial,
    Integer totalDeliver,
    Integer totalInstall,
    Integer grandTotal,
    int revisionCount) {}
