import type { SubPageKind, SubPageRowForm } from '@/interfaces/Schedule5SubPage'
import { enteredNum, perUnitLegacy } from '@/utils/derivedMath'

/**
 * The Schedule 5 sub-pages' DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed from `Schedule5Service.buildSubPageDocument` / `subPageTotals`
 * (`.../schedule5/Schedule5Service.java:1211-1290`), which is the authority. Nothing here is ever
 * sent on a write, and the Save echo replaces every figure it produces.
 *
 * `costPerVolume` is the LEGACY rule — divide at scale 10 HALF_UP then round to scale 2 — as on the
 * camp panel and Schedules 1, 3 and 6.
 *
 * ## Only the cost is entered
 *
 * A row has no stored volume: `stampedVolume` is the camp's item-141 (CAMP) or item-142 (ACCESS)
 * amount and every row displays it (deviation (B)), while `SubPageRowRequest` carries description and
 * cost only. So each row's rate is `enteredCost ÷ stampedVolume`, and the footer moves with the
 * entered costs.
 *
 * ## The two pages' footers are genuinely different shapes — do not symmetrize
 *
 * Transcribed from the service's own warning (deviation (C)):
 *
 * - **CAMP** is `CoreUtil.sumDescriptionCostVolumeType`: it sums cost AND volume, and flags
 *   "something contributed" on a non-null cost **or** — uniquely — on a null cost when the stamped
 *   volume is non-null. So an all-null-cost list still yields a cost of `0` rather than null. Its
 *   volume is `n × stampedVolume` (the row volumes summed), and a null stamped volume totals `0`, not
 *   null, because legacy starts that accumulator at zero.
 * - **ACCESS** is `sumDescriptionCostVolumeTypeCostOnly`: cost only, so an all-null-cost list yields
 *   null whatever the volume — after which the total's volume is overwritten with the SINGLE camp
 *   volume, unconditionally, including on an empty list.
 *
 * ## Which cells legacy refreshed, and why both are mirrored anyway
 *
 * Legacy was asymmetric: the CAMP page's row-cost handler rendered `footer`
 * (`schedule5CampExpenses.xhtml:78`) and the page has no per-row rate id at all; the ACCESS page's
 * handlers rendered `@this calcCost` (`schedule5AccessExpenses.xhtml:63,75`) — the row's own rate —
 * and the page has no footer id at all. The modern component renders BOTH cells on BOTH pages, a
 * pre-existing shape choice this defect did not introduce.
 *
 * Both are mirrored on both pages, applying the ruling already made for the Schedule 1/3 divergences
 * (2026-08-21): legacy moved a figure while leaving a figure derived from the same entry stale, and
 * reproducing that inconsistency would read as the very bug being fixed. The footer ARITHMETIC stays
 * page-aware, because that difference is real rather than an inconsistency.
 */

export interface SubPageTotals {
  readonly volume: number | null
  readonly cost: number | null
  readonly costPerVolume: number | null
}

/** One row's `$/m³`: the entered cost over the stamped camp volume. */
export function rowCostPerVolume(
  row: SubPageRowForm,
  stampedVolume: number | null | undefined,
): number | null {
  return perUnitLegacy(enteredNum(row.cost), stampedVolume ?? null)
}

export function deriveSubPageTotals(
  kind: SubPageKind,
  rows: readonly SubPageRowForm[],
  stampedVolume: number | null | undefined,
): SubPageTotals {
  const volume = stampedVolume ?? null
  let cost = 0
  let contributed = false
  for (const row of rows) {
    const entered = enteredNum(row.cost)
    if (entered !== null) {
      cost += entered
      contributed = true
    } else if (kind === 'CAMP' && volume !== null) {
      // CAMP's flag trips on a null cost too, whenever the stamped volume is present — which is how
      // an all-null-cost list yields 0 rather than null, and how that zero then propagates into the
      // camp panel's Sub-Total (7.1 deviation (h)/(L)).
      contributed = true
    }
  }

  if (kind === 'ACCESS') {
    // Volume is the single camp volume, set even when no cost contributed.
    return {
      volume,
      cost: contributed ? cost : null,
      costPerVolume: contributed ? perUnitLegacy(cost, volume) : null,
    }
  }

  if (!contributed) {
    return { volume: null, cost: null, costPerVolume: null }
  }
  // Legacy starts the running volume at ZERO and only adds non-null row volumes, so a camp whose
  // item-141 volume is null totals 0 here rather than null.
  const summedVolume = volume === null ? 0 : volume * rows.length
  return { volume: summedVolume, cost, costPerVolume: perUnitLegacy(cost, summedVolume) }
}
