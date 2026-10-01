import type { SubPageValues } from '@/components/schedule3SubPage'
import { committedNum, sumAsZero, wholeDollars } from '@/utils/derivedMath'

/**
 * The Other Acceptable Costs footer's DISPLAY-ONLY mirror (defect #291).
 *
 * Transcribed from `Schedule3Service.subtotalOtherCosts`
 * (`.../schedule3/Schedule3Service.java:986-997`) and `total` (`:1059-1061`): each item-124 row's TOT
 * half sums into Harvest and its PO&P peer into PO&P, **nulls as 0**, and Crown is the difference.
 *
 * `sumAsZero`, NOT `sumN`: the service seeds both accumulators at `0` with `nullToZero`, so an empty
 * page shows `0 / 0 / 0` rather than three blanks. That is the opposite of the Schedule 5 and 7A
 * totals, and the per-figure choice is exactly what the two helpers exist to keep straight.
 *
 * Parses with `committedNum` and rounds with `wholeDollars` to match the wire, which sends
 * `roundCost(parseDecimalInput(...))` — see the code-review note on `derivedMath.committedNum`.
 *
 * Lives in its own module rather than as a lambda in the page config (code review 2026-08-21): AD-5's
 * amendment confines mirror arithmetic to one `derived.ts` per schedule, and a config lambda had no
 * unit test of its own — it was the only new figure in that batch pinned by nothing.
 */

/**
 * The footer triple, keyed so it cannot be mis-paired with the summary labels positionally.
 *
 * `type`, NOT `interface`: `Schedule3SubPage`'s `deriveSummary` prop is typed
 * `=> Readonly<Record<string, number | null>>` because it looks the triple up by key, and TypeScript
 * grants an implicit index signature to a type alias but never to an interface. As an interface this
 * assignment does not type-check — and it shipped that way, because `tsc --noEmit` aborts on this
 * repo's tsconfig before reading a single source file (see deferred-work.md).
 */
export type OtherAcceptableSubtotal = {
  readonly harvest: number | null
  readonly pop: number | null
  readonly crown: number | null
}

export function deriveOtherAcceptableSubtotal(
  rows: readonly SubPageValues[],
): OtherAcceptableSubtotal {
  const harvest = sumAsZero(...rows.map((values) => wholeDollars(committedNum(values.total ?? ''))))
  const pop = sumAsZero(...rows.map((values) => wholeDollars(committedNum(values.pop ?? ''))))
  return { harvest, pop, crown: harvest - pop }
}
