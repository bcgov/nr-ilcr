import type { BridgeFormValues } from './validation'
import { addN, committedNum, sumN, wholeDollars } from '@/utils/derivedMath'

/**
 * Schedule 7A's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed from `Schedule7aService` (`.../schedule7a/Schedule7aService.java:533-536`), which is the
 * authority. Nothing here is ever sent on a write, and the Save echo replaces every figure it
 * produces.
 *
 * ```
 * totalMaterial = ssMaterial + abutMaterial          // add(), null-tolerant pair
 * totalDeliver  = ssDeliver  + abutDeliver
 * totalInstall  = ssInstall  + abutInstall
 * grandTotal    = sum(sitePlan, totalMaterial, totalDeliver, totalInstall,
 *                     approach, afterInstall, other)
 * ```
 *
 * Two things to keep straight:
 *
 * - **No division, so no rounding rule.** Every operand is a whole-dollar `Integer`, so this page has
 *   no `$/m³` and none of the scale-2-vs-scale-4 hazard that separates Schedules 1/3 from 2/4.
 * - **Null-tolerant, and null-not-zero.** `add` is null only when both sides are null; `sum` is null
 *   only when every operand is null. A bridge with no costs entered shows blank totals, not `0` —
 *   `sumN`, not `sumAsZero`. The grand total DOES include Site Plan (the service's javadoc note that
 *   omits it is wrong; the code wins — recorded in story 12.1).
 *
 * The Add panel previously passed no totals at all, so all four read blank while a new bridge was being
 * entered — the same "blank, not stale" shape as Schedule 4's copy mode. Both are fixed by this mirror.
 */

export interface Schedule7aTotals {
  readonly totalMaterial: number | null
  readonly totalDeliver: number | null
  readonly totalInstall: number | null
  readonly grandTotal: number | null
}

export function deriveBridgeTotals(form: BridgeFormValues): Schedule7aTotals {
  // `committedNum` (the strict wire parser) and `wholeDollars` together keep the mirror on the same
  // numbers the Save carries: `buildBody` sends `roundCost(parseDecimalInput(...))`, so a lax parse or
  // an unrounded sum guarantees a jump on the echo (code review 2026-08-21).
  const cost = (key: keyof BridgeFormValues): number | null =>
    wholeDollars(committedNum(form[key] ?? ''))

  const totalMaterial = wholeDollars(
    addN(cost('superstructureMaterialCost'), cost('abutmentMaterialCost')),
  )
  const totalDeliver = wholeDollars(
    addN(cost('superstructureDeliverCost'), cost('abutmentDeliverCost')),
  )
  const totalInstall = wholeDollars(
    addN(cost('superstructureInstallCost'), cost('abutmentInstallCost')),
  )
  const grandTotal = wholeDollars(
    sumN(
      cost('sitePlanCost'),
      totalMaterial,
      totalDeliver,
      totalInstall,
      cost('approachCost'),
      cost('afterInstallCost'),
      cost('otherCost'),
    ),
  )

  return { totalMaterial, totalDeliver, totalInstall, grandTotal }
}
