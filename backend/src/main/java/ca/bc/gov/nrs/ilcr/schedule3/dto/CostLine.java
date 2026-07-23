package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * One fixed Schedule 3 admin-cost line in the three-column model (AD-12). Money columns in whole
 * dollars. {@code harvest} and {@code pop} are the entered amounts (a line may be Harvest-only, in
 * which case {@code pop} is 0 per the legacy load — {@code Schedule3DAO} sets Annual Rents (29) and
 * Silviculture Admin (37) PO&amp;P to zero). {@code crown} is derived read-only
 * ({@code CostType.getCrownCost} = harvest &minus; pop, null unless BOTH sides are present) and is
 * ignored/rejected on write (Story 4.2).
 *
 * <p>Scaling Expense (33) is special: its {@code pop} is not stored but derived server-side from the
 * timber-volume ratio, so it carries both {@code pop} and {@code crown}.
 *
 * @param costItemCode the legacy Harvest cost-item id (27–37) identifying the line
 * @param harvest the Harvest-column amount (nullable when no row stored)
 * @param pop the PO&amp;P-column amount (nullable; 0 for the Harvest-only lines; derived for Scaling)
 * @param crown the derived Crown amount (harvest &minus; pop; null unless both present)
 */
public record CostLine(Integer costItemCode, Integer harvest, Integer pop, Integer crown) {
}
