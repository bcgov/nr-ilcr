package ca.bc.gov.nrs.ilcr.schedule2.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One Schedule 2 cost/volume/per-unit block (AD-12). Used for every block on the aggregate document
 * ({@code purchasedLogCost}, {@code purchasedWoodOverhead}, {@code subtotal}, {@code lessLogSales},
 * {@code netPurchased}, {@code totalCompanyLogging}, {@code totalAverage}).
 *
 * <p>{@code cost} is whole dollars (legacy {@code COST} is an {@code Integer}); {@code volume} may
 * be fractional (legacy {@code VOLUME} is a {@code Double}); {@code perUnit} ($/m³) is derived
 * server-side and is read-only. All fields are nullable and, with the app-wide Jackson {@code
 * non_null} inclusion, are omitted from the JSON when null.
 *
 * <p>{@code originalValues} carries the licensee's submitted figures for this block once the track
 * has left Draft (Story 16.2, BR-04), and is null at Draft. Only the two ENTERED blocks ever carry
 * it, and they carry different keys — {@code purchasedLogCost} a {@code cost} alone (its volume is
 * carried from Schedule 3, and legacy set no volume original for it: {@code
 * Schedule2DAO.java:122-123}) and {@code lessLogSales} both {@code volume} and {@code cost} ({@code
 * :118-119}). The five derived blocks carry none, having no snapshot column to expose.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostBlock(
    BigDecimal volume,
    Integer cost,
    BigDecimal perUnit,
    Map<String, OriginalValue> originalValues) {

  /** A carried or derived block: no original values, because nothing stores it. */
  public CostBlock(BigDecimal volume, Integer cost, BigDecimal perUnit) {
    this(volume, cost, perUnit, null);
  }
}
