import type { Camp, CategoryAmount } from '@/interfaces/Schedule5Response'
import type { CampFormValues, CategoryKey } from './validation'
import { addN, committedNum, perUnitLegacy, subN, sumN, wholeDollars } from '@/utils/derivedMath'

/**
 * Schedule 5's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed from `Schedule5Service` (`.../schedule5/Schedule5Service.java:255-300`), which is the
 * authority. Nothing here is ever sent on a write, and the Save echo replaces every figure it
 * produces.
 *
 * `costPerVolume` is the LEGACY rule — divide at scale 10 HALF_UP, then round to scale 2
 * (`Schedule5Service.costPerVolume`), the same rule Schedules 1, 3 and 6 use and NOT Schedules 2/4's
 * scale 4.
 *
 * ```
 * campSubTotal        = sumCosts(catering, wages, depreciation, generalCamp, otherCamp)   // 5 costs
 * campTotal           = campSubTotal - recoveries        // BR-04/S09, never clamped at 0
 * accessExpenseTotal  = sumCosts(crewTransport, equipLand, equipRail, equipAir, equipWater,
 *                                otherAccess)                                            // 6 costs
 * campAndAccessTotal  = campTotal + accessExpenseTotal   // null-tolerant add
 * ```
 *
 * All four derived rows carry the **Associated Camp Volume** as their volume, so every one of their
 * rates moves when that single field changes — and BR-03 propagates the same value into all eleven
 * volume-bearing categories, so their rates move with it too (the page already does that propagation
 * in `setCampVolume`).
 *
 * `sumN`, not `sumAsZero`: `Schedule5Service.sumCosts` returns null when EVERY operand is null, so a
 * camp with no costs entered shows blank totals rather than a fabricated `0`. `subtractCost` is
 * asymmetric — a null Recoveries leaves the sub-total unchanged, but a null sub-total stays null
 * regardless of Recoveries.
 *
 * ## What is deliberately NOT mirrored, and why
 *
 * **`otherCampExpenses` / `otherAccessExpenses` rates stay served.** Their `$/m³` is not
 * `cost ÷ volume`: `Schedule5Service.costPerVolumePerTerm` divides EACH item-62/68 sub-page row by the
 * stamped volume, rounds each term to scale 2, and sums those — which is not equal to rounding the
 * summed cost once. Reproducing it needs the individual row costs, and the camp document carries only
 * their sum plus a count (`Camp.otherCampExpenseCount`). Legacy DID refresh these two on a volume
 * change (`schedule5ExistingCamp.xhtml:223` renders `otherCampExpensesCostVolume`), so this is a real
 * parity gap — recorded deliberately, because a stale-but-correct figure beats a live-but-wrong one,
 * and a single-division approximation would violate the no-jump-on-Save guarantee.
 *
 * Their **costs** are server-owned (the sub-page row sums) and are consumed here as constants, which
 * is correct: they cannot change while the camp panel is open.
 *
 * **Recoveries has no rate at all**, ever — no volume cell and no `$/m³` cell exist for it.
 */

/** The nine categories whose BOTH halves are entered here, so their rate is fully mirrorable. */
const MIRRORED_RATE_KEYS = [
  'cateringAndFood',
  'wagesAndBenefits',
  'depreciationLease',
  'generalCampExpenses',
  'crewTransportation',
  'equipAndSuppliesLand',
  'equipAndSuppliesRail',
  'equipAndSuppliesAir',
  'equipAndSuppliesWater',
] as const satisfies readonly CategoryKey[]

/** A derived row: the camp volume, the computed cost, and their rate. Mirrors `CategoryAmount`. */
export interface DerivedAmount {
  readonly volume: number | null
  readonly cost: number | null
  readonly costPerVolume: number | null
}

export interface Schedule5Derived {
  /** `$/m³` per category — present only for the nine fully-entered ones. */
  readonly perUnit: Readonly<Partial<Record<CategoryKey, number | null>>>
  readonly campSubTotal: DerivedAmount
  readonly campTotal: DerivedAmount
  readonly accessExpenseTotal: DerivedAmount
  readonly campAndAccessTotal: DerivedAmount
}

export function deriveSchedule5(values: CampFormValues, served?: Camp): Schedule5Derived {
  const campVolume = committedNum(values.associatedCampVolume)
  // `committedNum` + `wholeDollars` match `buildRequest`'s roundCost(parseDecimalInput(...)), so the
  // mirror sums the same integers the server will (code review 2026-08-21).
  const cost = (key: CategoryKey): number | null =>
    wholeDollars(committedNum(values.categories[key].cost))

  // Sub-page-owned costs: constants while the panel is open (the sub-resource is their only writer).
  const otherCampCost = served?.otherCampExpenses?.cost ?? null
  const otherAccessCost = served?.otherAccessExpenses?.cost ?? null

  const perUnit: Partial<Record<CategoryKey, number | null>> = {}
  for (const key of MIRRORED_RATE_KEYS) {
    perUnit[key] = perUnitLegacy(cost(key), committedNum(values.categories[key].volume))
  }

  // (1) Sub-Total over EXACTLY five costs — Recoveries excluded (CampReportType.java:335-347).
  const campSubTotalCost = sumN(
    cost('cateringAndFood'),
    cost('wagesAndBenefits'),
    cost('depreciationLease'),
    cost('generalCampExpenses'),
    otherCampCost,
  )

  // (2) Camp Total = Sub-Total − Recoveries. Recoveries is stored POSITIVE and subtracted; a negative
  // Recoveries therefore INCREASES the total and is never clamped (the 0-floor is client-side only).
  const campTotalCost = subN(campSubTotalCost, cost('recoveries'))

  // (3) Access Expense Total over EXACTLY six costs (CampReportType.java:413-425).
  const accessExpenseTotalCost = sumN(
    cost('crewTransportation'),
    cost('equipAndSuppliesLand'),
    cost('equipAndSuppliesRail'),
    cost('equipAndSuppliesAir'),
    cost('equipAndSuppliesWater'),
    otherAccessCost,
  )

  // (4) Camp and Access Total — null only when BOTH sides are null.
  const campAndAccessTotalCost = addN(campTotalCost, accessExpenseTotalCost)

  const derived = (amount: number | null): DerivedAmount => ({
    volume: campVolume,
    cost: wholeDollars(amount),
    costPerVolume: perUnitLegacy(amount, campVolume),
  })

  return {
    perUnit,
    campSubTotal: derived(campSubTotalCost),
    campTotal: derived(campTotalCost),
    accessExpenseTotal: derived(accessExpenseTotalCost),
    campAndAccessTotal: derived(campAndAccessTotalCost),
  }
}

/**
 * The rate to render for one category row: the mirror for the nine fully-entered categories, the
 * served figure for the two per-term Other rows (see the module note) and for read-only mode.
 *
 * **The two Other rows go BLANK once their denominator moves** (ruled 2026-08-21 after code review).
 * BR-03 rewrites their VOLUME cell from the Associated Camp Volume while their rate stays served, so
 * with the shipped fixture a camp volume of 60,000 left the row reading 60,000 / 24,000 / 0.31 — a row
 * no arithmetic reconciles, since 0.31 came from the served 80,000. A blank cell is honest; a figure
 * computed against a denominator no longer on screen is not, and the per-term formula
 * (`costPerVolumePerTerm`) cannot be reproduced client-side to replace it.
 */
export function categoryRate(
  key: CategoryKey,
  derived: Schedule5Derived | null,
  served?: CategoryAmount,
  campVolumeMoved = false,
): number | null | undefined {
  if (derived === null) {
    return served?.costPerVolume
  }
  if (!(key in derived.perUnit)) {
    // A per-term Other row: keep the served rate only while the camp volume it was computed against
    // still stands. NOTE the comparison is committed-vs-SERVED CAMP volume, not against the row's own
    // volume — an Other row's stored volume is its item-141/142 amount and legitimately differs from
    // the camp volume, so comparing those blanked the rate on load.
    return campVolumeMoved ? null : served?.costPerVolume
  }
  return derived.perUnit[key]
}

/**
 * True when the committed Associated Camp Volume differs from the one the served document carried —
 * i.e. BR-03 has rewritten the two Other rows' volume cells, so their served per-term rate no longer
 * describes anything on screen. See {@link categoryRate}.
 */
export function campVolumeMovedFrom(values: CampFormValues, served?: Camp): boolean {
  const committed = committedNum(values.associatedCampVolume)
  const servedVolume = served?.associatedCampVolume ?? null
  return committed !== servedVolume
}
