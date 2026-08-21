import type { BridgeFormValues } from './validation'
import { addN, enteredNum, sumN } from '@/utils/derivedMath'

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
  const cost = (key: keyof BridgeFormValues): number | null => enteredNum(form[key] ?? '')

  const totalMaterial = addN(cost('superstructureMaterialCost'), cost('abutmentMaterialCost'))
  const totalDeliver = addN(cost('superstructureDeliverCost'), cost('abutmentDeliverCost'))
  const totalInstall = addN(cost('superstructureInstallCost'), cost('abutmentInstallCost'))
  const grandTotal = sumN(
    cost('sitePlanCost'),
    totalMaterial,
    totalDeliver,
    totalInstall,
    cost('approachCost'),
    cost('afterInstallCost'),
    cost('otherCost'),
  )

  return { totalMaterial, totalDeliver, totalInstall, grandTotal }
}
