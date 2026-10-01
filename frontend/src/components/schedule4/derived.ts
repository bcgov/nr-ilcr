import type { CategoryForm } from './validation'
import { enteredNum, perUnitOf } from '@/utils/derivedMath'
import { ALL_CATEGORIES } from './validation'

/**
 * Schedule 4's DISPLAY-ONLY derived-figure mirror for the Add/Edit Location panel (defect #291).
 *
 * Mirrors `Schedule4Service`'s per-category `perUnit`
 * (`backend/src/main/java/ca/bc/gov/nrs/ilcr/schedule4/Schedule4Service.java:141,513`), which is the
 * authority: `$/m³ = cost ÷ volume`, null when either operand is null or the volume is zero. Nothing
 * here is ever sent on a write, and the Save echo re-seeds the panel from the server.
 *
 * The panel previously showed the `$/m³` captured from the server when it opened (`panelPerUnit`),
 * which meant the column was stale for the whole of data entry — and blank outright in **copy** mode,
 * where the amounts are cloned but no server figure exists for them yet. Both follow from the same
 * missing mirror.
 *
 * Parses with {@link enteredNum} — `toNum` plus a finiteness guard, so `Infinity` cannot reach a cell — so the rate on screen is computed
 * from exactly the numbers a Save would send.
 */
export function deriveCategoryPerUnits(categories: CategoryForm): Record<number, number | null> {
  const perUnit: Record<number, number | null> = {}
  for (const def of ALL_CATEGORIES) {
    const values = categories[def.code]
    perUnit[def.code] = values
      ? perUnitOf(enteredNum(values.cost), enteredNum(values.volume))
      : null
  }
  return perUnit
}
