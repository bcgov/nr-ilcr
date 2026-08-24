import type Schedule3Response from '@/interfaces/Schedule3Response'
import type { ThreeColumnTotal, TimberBlock } from '@/interfaces/Schedule3Response'
import {
  addN,
  enteredNum,
  perUnitLegacy,
  scalingPopOf,
  sumAsZero,
  wholeDollars,
} from '@/utils/derivedMath'
import { ALL_LINE_CODES, HARVEST_POP_LINE_CODES } from '@/interfaces/Schedule3Request'

/**
 * Schedule 3's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed from `Schedule3Service.getSchedule3` (`.../schedule3/Schedule3Service.java:164-231`)
 * and `Schedule3Constants.resolvePop` / `scalingPop`, which are the authority. Nothing here is ever
 * sent on a write, and the Save echo replaces every figure it produces.
 *
 * This is the most interdependent of the four pages, and three things make it so:
 *
 * 1. **Scaling (33)'s PO&P is itself derived** — `round₀((popTimberVolume ÷ overheadVolume) ×
 *    scalingHarvest)` — so it moves with THREE entered fields (the Scaling harvest and both timber
 *    volumes) and then feeds the PO&P subtotal, Total Costs, both timber costs and Total Overhead.
 *    It is the one derived value on this page that lives in an entry column.
 * 2. **`includedUnacceptableCosts` mixes a sub-page constant with an entered field** — the item-38
 *    rows (owned by the sub-page) plus the Annual Rents (29) Harvest entered here. The main-page
 *    document carries only their SUM, so the constant is recovered once by subtracting the loaded
 *    Annual Rents harvest back out. That is exact, but it is implicit: see {@link unacceptableBase}.
 * 3. **`subtotalOtherCosts` is a sub-page figure** owned by the Other-Acceptable sub-resource. It
 *    cannot move while this page is edited, so it is consumed as a constant — and the page keeps
 *    rendering it straight from the document.
 *
 * `$/m³` uses the LEGACY scale-2 rule ({@link perUnitLegacy}), as `Schedule3Service.perUnit` does —
 * not the scale-4 rule Schedules 2 and 4 use.
 */

const CODE_ANNUAL_RENTS = 29
const CODE_SCALING = 33
const CODE_SILV_ADMIN = 37
/** Harvest-only lines: legacy forces PO&P to 0 once a harvest is present, so crown = harvest. */
const HARVEST_ONLY = new Set<number>([CODE_ANNUAL_RENTS, CODE_SILV_ADMIN])
const HARVEST_POP = new Set<number>(HARVEST_POP_LINE_CODES)

export interface Schedule3Line {
  readonly harvest: number | null
  readonly pop: number | null
  readonly crown: number | null
}

export interface Schedule3Derived {
  /** Per cost-item code: the entered harvest, the RESOLVED PO&P, and the derived crown. */
  readonly lines: Readonly<Record<number, Schedule3Line>>
  readonly subtotalActualCosts: ThreeColumnTotal
  readonly includedUnacceptableCosts: ThreeColumnTotal
  readonly totalCosts: ThreeColumnTotal
  readonly popTimber: TimberBlock
  readonly crownTimber: TimberBlock
  readonly totalOverhead: TimberBlock
  /** The Included-Unacceptable sub-page link count, which also moves with the Annual Rents harvest. */
  readonly unacceptableCount: number
}

export interface Schedule3Entered {
  readonly harvest: Readonly<Record<number, number | null>>
  readonly pop: Readonly<Record<number, number | null>>
  readonly popTimberVolume: number | null
  readonly crownTimberVolume: number | null
}

/** Parse the committed form into the shape {@link deriveSchedule3} consumes. */
export function enteredFromForm(form: Readonly<Record<string, string>>): Schedule3Entered {
  const harvest: Record<number, number | null> = {}
  const pop: Record<number, number | null> = {}
  for (const code of ALL_LINE_CODES) {
    harvest[code] = enteredNum(form[`harvest-${code}`] ?? '')
    pop[code] = HARVEST_POP.has(code) ? enteredNum(form[`pop-${code}`] ?? '') : null
  }
  return {
    harvest,
    pop,
    popTimberVolume: enteredNum(form['popTimberVolume'] ?? ''),
    crownTimberVolume: enteredNum(form['crownTimberVolume'] ?? ''),
  }
}

/**
 * Re-exported from {@link scalingPopOf} so this module stays the single place Schedule 3's mirror is
 * read from. The ratio is taken at **scale 15 HALF_UP before the multiply**, exactly as
 * `Schedule3Constants.scalingPop` does — the first implementation skipped that step and shipped a $1
 * divergence that cascaded into nine cells (code review 2026-08-21).
 */
export const scalingPop = scalingPopOf

/**
 * Recover the item-38 (sub-page) half of Included Unacceptable Costs from the loaded document, so the
 * entered Annual Rents harvest can be swapped in live.
 *
 * The document exposes only the SUM of the sub-page rows and the Annual Rents harvest, so the constant
 * is `loaded sum − loaded Annual Rents harvest`. Exact, because that is precisely how the server built
 * the sum (`Schedule3Service.java:197-206`), and stable, because the sub-page rows cannot change while
 * this page is open. Do not "simplify" this to the document's total: that would freeze the figure at
 * its loaded Annual Rents value, which is the bug being fixed.
 */
export function unacceptableBase(doc: Schedule3Response): number {
  const loadedTotal = doc.includedUnacceptableCosts?.harvest ?? 0
  const loadedAnnualRents =
    doc.lineItems.find((line) => line.costItemCode === CODE_ANNUAL_RENTS)?.harvest ?? 0
  return loadedTotal - loadedAnnualRents
}

/** The same recovery for the sub-page link's row count (`Schedule3Service.java:224-225`). */
function unacceptableBaseCount(doc: Schedule3Response): number {
  const loadedAnnualRents =
    doc.lineItems.find((line) => line.costItemCode === CODE_ANNUAL_RENTS)?.harvest ?? null
  const annualRentsCounted = loadedAnnualRents !== null && loadedAnnualRents !== 0 ? 1 : 0
  return doc.unacceptableCount - annualRentsCounted
}

/** Legacy `CostType.getCrownCost`: harvest − PO&P, null when EITHER side is absent. */
function crownCost(harvest: number | null, pop: number | null): number | null {
  if (harvest === null || pop === null) {
    return null
  }
  // `wholeDollars` both matches the server's integer COST column and normalises `-0` to `0`: a harvest
  // of `-0` on a harvest-only line (PO&P forced to 0) otherwise rendered "-0" (code review 2026-08-21).
  return wholeDollars(harvest - pop)
}

export function deriveSchedule3(
  doc: Schedule3Response,
  entered: Schedule3Entered,
): Schedule3Derived {
  const { popTimberVolume, crownTimberVolume } = entered
  // Total Overhead volume = PO&P Timber + Crown Timber (null-tolerant, legacy bigDecimalCostAddition).
  const overheadVolume = addN(popTimberVolume, crownTimberVolume)

  // --- The 11 fixed lines: entered harvest, RESOLVED PO&P, derived crown. -------------------------
  const lines: Record<number, Schedule3Line> = {}
  for (const code of ALL_LINE_CODES) {
    const harvest = entered.harvest[code] ?? null
    let pop: number | null
    if (HARVEST_ONLY.has(code)) {
      // Legacy forces PO&P to 0 once a harvest is present, so crown ends up equal to the harvest.
      pop = harvest === null ? null : 0
    } else if (code === CODE_SCALING) {
      pop = scalingPop(harvest, popTimberVolume, overheadVolume)
    } else {
      pop = entered.pop[code] ?? null
    }
    lines[code] = { harvest, pop, crown: crownCost(harvest, pop) }
  }

  // --- Subtotal Actual Costs = Subtotal Other Costs + Σ(11 lines), per column, nulls as 0. --------
  // The PO&P column sums the RESOLVED pops, so Scaling's derived figure is included (the server puts
  // the resolved value into popByCode before summing).
  const otherHarvest = doc.subtotalOtherCosts?.harvest ?? 0
  const otherPop = doc.subtotalOtherCosts?.pop ?? 0
  const subtotalHarvest = sumAsZero(
    otherHarvest,
    ...ALL_LINE_CODES.map((code) => lines[code].harvest),
  )
  const subtotalPop = sumAsZero(otherPop, ...ALL_LINE_CODES.map((code) => lines[code].pop))
  const subtotalActualCosts: ThreeColumnTotal = {
    harvest: wholeDollars(subtotalHarvest),
    pop: wholeDollars(subtotalPop),
    crown: wholeDollars(subtotalHarvest - subtotalPop),
  }

  // --- Included Unacceptable Costs = item-38 rows + the Annual Rents harvest; PO&P forced 0. ------
  const unacceptableHarvest = unacceptableBase(doc) + (lines[CODE_ANNUAL_RENTS].harvest ?? 0)
  const includedUnacceptableCosts: ThreeColumnTotal = {
    harvest: wholeDollars(unacceptableHarvest),
    pop: 0,
    crown: wholeDollars(unacceptableHarvest), // pop 0 => crown = harvest
  }

  // --- Total Costs = Subtotal Actual − Included Unacceptable. -------------------------------------
  const totalHarvest = subtotalHarvest - unacceptableHarvest
  const totalPop = subtotalPop - (includedUnacceptableCosts.pop ?? 0)
  const totalCosts: ThreeColumnTotal = {
    harvest: wholeDollars(totalHarvest),
    pop: wholeDollars(totalPop),
    crown: wholeDollars(totalHarvest - totalPop),
  }

  // --- Timber blocks: their costs are PUSHED DOWN from Total Costs; overhead sums the two. --------
  const popTimberCost = totalCosts.pop
  const crownTimberCost = totalCosts.crown
  const overheadCost = addN(popTimberCost, crownTimberCost)

  return {
    lines,
    subtotalActualCosts,
    includedUnacceptableCosts,
    totalCosts,
    popTimber: {
      volume: popTimberVolume,
      cost: popTimberCost,
      perUnit: perUnitLegacy(popTimberCost, popTimberVolume),
    },
    crownTimber: {
      volume: crownTimberVolume,
      cost: crownTimberCost,
      perUnit: perUnitLegacy(crownTimberCost, crownTimberVolume),
    },
    totalOverhead: {
      volume: overheadVolume,
      cost: overheadCost,
      perUnit: perUnitLegacy(overheadCost, overheadVolume),
    },
    unacceptableCount: unacceptableBaseCount(doc) + (lines[CODE_ANNUAL_RENTS].harvest ? 1 : 0),
  }
}
