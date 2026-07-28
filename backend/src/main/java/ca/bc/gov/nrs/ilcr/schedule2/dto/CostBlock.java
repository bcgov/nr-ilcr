package ca.bc.gov.nrs.ilcr.schedule2.dto;

import java.math.BigDecimal;

/**
 * One Schedule 2 cost/volume/per-unit block (AD-12). Used for every block on the aggregate document
 * ({@code purchasedLogCost}, {@code purchasedWoodOverhead}, {@code subtotal}, {@code lessLogSales},
 * {@code netPurchased}, {@code totalCompanyLogging}, {@code totalAverage}).
 *
 * <p>{@code cost} is whole dollars (legacy {@code COST} is an {@code Integer}); {@code volume} may be
 * fractional (legacy {@code VOLUME} is a {@code Double}); {@code perUnit} ($/m³) is derived
 * server-side and is read-only. All fields are nullable and, with the app-wide Jackson
 * {@code non_null} inclusion, are omitted from the JSON when null.
 */
public record CostBlock(
    BigDecimal volume,
    Integer cost,
    BigDecimal perUnit) {
}
