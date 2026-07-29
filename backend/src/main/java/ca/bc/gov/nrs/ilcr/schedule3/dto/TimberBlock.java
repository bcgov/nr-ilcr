package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.math.BigDecimal;

/**
 * A timber / overhead block (AD-12): PO&amp;P Timber, Crown Timber, and Total Overhead. The
 * {@code volume} is the entered value (Crown Timber's is the BR-09 source pushed into Schedule 1 on
 * save — Story 4.2). {@code cost} and {@code perUnit} are computed server-side read-only:
 * PO&amp;P/Crown Timber cost are pushed from Total Costs' PO&amp;P/Crown (legacy
 * {@code Schedule3DO.getPopTimber/getCrownTimber}); Total Overhead's {@code volume}/{@code cost} are
 * the sums of the two timber blocks. {@code perUnit} is {@code cost / volume} at scale-2 HALF_UP
 * (legacy {@code CostVolumeType.getCostVolume} via {@code CoreUtil.bigDecimalDivision}), null when
 * {@code cost} is null or {@code volume} is null/zero.
 *
 * <p>{@code cost} is {@code Long} (not {@code Integer}) because it is pushed from the Total Costs
 * sums, which can exceed {@code Integer.MAX_VALUE}.
 *
 * @param volume the entered volume (Total Overhead's is derived: PO&amp;P + Crown)
 * @param cost the derived cost (whole dollars)
 * @param perUnit the derived {@code $ / m³} (cost / volume), scale 2, or null
 */
public record TimberBlock(BigDecimal volume, Long cost, BigDecimal perUnit) {
}
