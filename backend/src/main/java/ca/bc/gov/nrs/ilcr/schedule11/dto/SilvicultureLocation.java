package ca.bc.gov.nrs.ilcr.schedule11.dto;

import java.math.BigDecimal;

/**
 * One Schedule 11 location row of the pinned Story 25.1 wire contract (AD-12). All derived figures
 * are computed server-side per BR-08 (AD-5); costs are whole-dollar integers ({@code
 * ILCR_COST_REPORT_DETAIL.COST} is {@code NUMBER(8,0)}). Clean camelCase, never JSF control-id
 * spellings.
 *
 * @param locationId the location id ({@code BASIC_SILVICULTURE_REPORT_ID})
 * @param location the location text (label "Location", max 30)
 * @param enhancedIndicator {@code ENHANCED_IND} {@code "Y"} → true (label "ES")
 * @param biogeoclimaticCatalogueId the BEC catalogue id the row references
 * @param becLabel legacy {@code getBiogeoSubZoneVariantPase()}: zone+subzone+variant+phase, nulls →
 *     {@code ""}; null only if the catalogue row is missing
 * @param netArea {@code REFORESTED_NET_AREA} (label "NAR (ha)")
 * @param actualCost item-24 cost (label "Actual Cost ($)"); null when absent
 * @param plannedCost item-23 cost (label "Planned Cost ($)"); null when absent
 * @param totalCost {@code actualCost + plannedCost}, null-tolerant (legacy "Total Act Plus Plan
 *     Cost ($)"); null when both are null
 * @param costPerNetArea {@code totalCost / netArea} (legacy "Total/NAR(ha)"); null on null total,
 *     null NAR, or zero NAR; scale 4 HALF_UP, min scale 1 (recorded deviation)
 * @param comments the row comments (max 3500); nullable
 * @param revisionCount the per-row optimistic-lock token (served now so 25.2's PUT/DELETE has it —
 *     recorded AR11 keying delta)
 */
public record SilvicultureLocation(
    long locationId,
    String location,
    boolean enhancedIndicator,
    long biogeoclimaticCatalogueId,
    String becLabel,
    BigDecimal netArea,
    Integer actualCost,
    Integer plannedCost,
    Integer totalCost,
    BigDecimal costPerNetArea,
    String comments,
    Integer revisionCount) {}
