package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;

/**
 * Subtotal Other Costs summary (AD-5/AD-12). {@code volume} is the shared item-19 volume;
 * {@code costSubtotal}/{@code perUnit}/{@code count} are derived server-side from the itemized
 * item-19 rows (rows with a non-empty description).
 */
public record OtherCostsSummary(
    BigDecimal volume,
    Long costSubtotal,
    BigDecimal perUnit,
    int count) {
}
