import type Schedule2Response from '@/interfaces/Schedule2Response'
import type { CostBlock } from '@/interfaces/Schedule2Response'
import { addN, perUnitOf, subN, wholeDollars } from '@/utils/derivedMath'

/**
 * Schedule 2's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed line-for-line from `Schedule2Service.getSchedule2`
 * (`backend/src/main/java/ca/bc/gov/nrs/ilcr/schedule2/Schedule2Service.java:288-357`), which is the
 * authority. Nothing here is ever sent on a write, and the Save echo replaces every figure it
 * produces. Keep it in step with that method: when it changes, this changes.
 *
 * Only THREE values are entered on this page (item 25 cost, item 26 volume, item 26 cost). Everything
 * else is carried from Schedules 1 and 3 and cannot move while Schedule 2 is being edited, so the
 * mirror reads those carried figures straight off the loaded document rather than recomputing them:
 *
 * | carried input                     | in the document as              |
 * |-----------------------------------|---------------------------------|
 * | Sch3 PO&P timber volume (118)     | `purchasedWoodOverhead.volume`  |
 * | Sch3 Subtotal Actual Costs PO&P   | `purchasedWoodOverhead.cost`    |
 * | Sch3 Crown timber volume (119)    | `totalCompanyLogging.volume`    |
 * | legacy `getTotalLoggingCost`      | `totalCompanyLogging.cost`      |
 *
 * That equivalence is what makes this mirror possible with no backend change; it is asserted in
 * `Schedule2ServiceTest.carriedFigures_fromSchedule3`, so a contract change there breaks a test
 * rather than silently skewing the screen.
 */

/** The three entered values, already parsed from the committed (blurred) form strings. */
export interface Schedule2Entered {
  readonly purchasedLogCostCost: number | null
  readonly lessLogSalesVolume: number | null
  readonly lessLogSalesCost: number | null
}

/**
 * The blocks that move during entry. `purchasedWoodOverhead` and `totalCompanyLogging` are absent by
 * design — they are wholly carried, so the page renders them from the document unchanged.
 */
export interface Schedule2Derived {
  readonly purchasedLogCost: CostBlock
  readonly subtotal: CostBlock
  readonly lessLogSales: CostBlock
  readonly netPurchased: CostBlock
  readonly totalAverage: CostBlock
}

export function deriveSchedule2(
  doc: Schedule2Response,
  entered: Schedule2Entered,
): Schedule2Derived {
  // Carried, unaffected by this page's entry — see the table above.
  const popTimberVolume = doc.purchasedWoodOverhead.volume
  const popActualCost = doc.purchasedWoodOverhead.cost
  const crownVolume = doc.totalCompanyLogging.volume
  const totalLoggingCost = doc.totalCompanyLogging.cost

  const { purchasedLogCostCost, lessLogSalesVolume, lessLogSalesCost } = entered

  // Item 25 — cost entered, volume carried from Sch3 118 (BR-03), perUnit derived (getPurchasedLogCostCal).
  const purchasedLogCost: CostBlock = {
    volume: popTimberVolume,
    cost: purchasedLogCostCost,
    perUnit: perUnitOf(purchasedLogCostCost, popTimberVolume),
  }

  // Subtotal — cost = item 25 + Sch3 PO&P actual cost (getSubtotalCost); volume = Sch3 118;
  // perUnit = subtotalCost ÷ Sch3 118 (getSubtotalCal).
  const subtotalCost = addN(purchasedLogCostCost, popActualCost)
  const subtotal: CostBlock = {
    volume: popTimberVolume,
    cost: wholeDollars(subtotalCost),
    perUnit: perUnitOf(subtotalCost, popTimberVolume),
  }

  // Item 26 — volume + cost both entered; perUnit derived.
  const lessLogSales: CostBlock = {
    volume: lessLogSalesVolume,
    cost: lessLogSalesCost,
    perUnit: perUnitOf(lessLogSalesCost, lessLogSalesVolume),
  }

  // Net Purchased — volume = Sch3 118 − item 26 volume (getNetPurchasedVolume);
  // cost = subtotalCost − item 26 cost (getNetPurchasedCost); perUnit = net ÷ net.
  // Both are subtractions, so both can legitimately go negative.
  const netPurchasedVolume = subN(popTimberVolume, lessLogSalesVolume)
  const netPurchasedCost = subN(subtotalCost, lessLogSalesCost)
  const netPurchased: CostBlock = {
    volume: netPurchasedVolume,
    cost: wholeDollars(netPurchasedCost),
    perUnit: perUnitOf(netPurchasedCost, netPurchasedVolume),
  }

  // Total Average — volume = net volume + Crown (getTotalAverageVolume);
  // cost = net cost + total logging cost (getTotalAverageCost); perUnit = cost ÷ volume.
  // The UNROUNDED net cost feeds this, matching the service: it rounds to whole dollars only at the
  // point of display, never between steps.
  const totalAverageVolume = addN(netPurchasedVolume, crownVolume)
  const totalAverageCost = addN(netPurchasedCost, totalLoggingCost)
  const totalAverage: CostBlock = {
    volume: totalAverageVolume,
    cost: wholeDollars(totalAverageCost),
    perUnit: perUnitOf(totalAverageCost, totalAverageVolume),
  }

  return { purchasedLogCost, subtotal, lessLogSales, netPurchased, totalAverage }
}
