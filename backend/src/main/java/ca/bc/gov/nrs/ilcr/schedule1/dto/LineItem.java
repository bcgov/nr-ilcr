package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;

/**
 * One Schedule 1 fixed line item, keyed by the legacy cost-item identifier (AD-12). {@code perUnit}
 * ($/m³) is derived server-side and is read-only; {@code cost} is whole dollars, {@code volume} may
 * be fractional.
 */
public record LineItem(
    Integer costItemCode,
    BigDecimal volume,
    Integer cost,
    BigDecimal perUnit) {
}
