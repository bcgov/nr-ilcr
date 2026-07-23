package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * A derived three-column money total (AD-5/AD-12): Subtotal Other Costs, Subtotal Actual Costs,
 * Included Unacceptable Costs, and Total Costs. All fields are computed server-side read-only and are
 * never accepted from a client. {@code crown} follows {@code CostType.getCrownCost}
 * (harvest &minus; pop); the subtotals seed at zero (legacy {@code CoreUtil.sumCostType}) so they are
 * always present, while Included Unacceptable forces {@code pop} to 0 so its {@code crown} equals its
 * {@code harvest} (legacy {@code Schedule3DO.getIncludedUnacceptCosts}).
 *
 * <p>Fields are {@code Long} (not {@code Integer}) because these are sums over many line items: a
 * schedule could total beyond {@code Integer.MAX_VALUE} (~$2.1B) without overflowing.
 *
 * @param harvest the Harvest-column total (whole dollars)
 * @param pop the PO&amp;P-column total (whole dollars)
 * @param crown the derived Crown total (harvest &minus; pop)
 */
public record ThreeColumnTotal(Long harvest, Long pop, Long crown) {
}
