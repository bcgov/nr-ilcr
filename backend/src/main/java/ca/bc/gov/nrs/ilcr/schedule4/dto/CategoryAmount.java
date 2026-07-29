package ca.bc.gov.nrs.ilcr.schedule4.dto;

import java.math.BigDecimal;

/**
 * One Schedule 4 transportation-category amount within a location (AD-12). The pinned wire shape,
 * frozen across the later Schedule 4 stories.
 *
 * <p>{@code code} is the legacy {@code ILCR_REPORT_COST_ITEM_ID} (e.g. 40 Lakeside Dry Dump, 47 Truck
 * Barge/Ferry). {@code kind} is {@code "FIXED"} for the 9 no-distance categories
 * (40,41,42,44,45,49,50,51,53) and {@code "DISTANCE"} for the 3 distance-based categories (47 Truck
 * Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul). {@code volume} may be fractional (legacy
 * {@code VOLUME} is a {@code Double}); {@code cost} is whole dollars (legacy {@code COST} is an
 * {@code Integer}). {@code distance} is populated only for {@code kind=="DISTANCE"} — it is that
 * category's OWN {@code TRANSPORTATION_REPORT.DISTANCE} (each distance category lives on its own
 * report; delivery-DB confirmed, so two distance categories on the same location can differ); it is
 * null (omitted) for {@code FIXED} categories. {@code perUnit} ($/m³) is derived server-side
 * (AD-6, cost ÷ volume) and is read-only; null when volume is null or zero.
 *
 * <p>All fields are nullable and, with the app-wide Jackson {@code non_null} inclusion, omitted from
 * the JSON when null. Only categories actually stored for the location are present.
 */
public record CategoryAmount(
    int code,
    String kind,
    BigDecimal volume,
    Integer cost,
    BigDecimal distance,
    BigDecimal perUnit) {
}
