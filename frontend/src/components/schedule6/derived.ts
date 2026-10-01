import type { RoadRecordFormValues } from './validation'
import { committedNum, perUnitLegacy, wholeDollars } from '@/utils/derivedMath'

/**
 * Schedule 6's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Mirrors `Schedule6Service.perUnit` — cost ÷ volume at the LEGACY rule (divide at scale 10 HALF_UP,
 * then round to scale 2), the same rule Schedules 1 and 3 use, NOT Schedules 2/4's scale 4. Nothing
 * here is ever sent on a write, and the Save echo replaces the figure.
 *
 * ## Scope: the record's own `$ / m³` ONLY
 *
 * Legacy refreshed exactly one derived cell per record on a volume or cost change — `cal` / `calcule`,
 * the row's own rate (`schedule6.xhtml:153,163,364,383`). The **footer totals** (`totalVol`,
 * `totalCos`, `totalCal`) appear in **no** legacy render or update target, so legacy left them until
 * Save and the page's existing behaviour — rendering them from the document — is already faithful.
 * They are deliberately not mirrored, the same call made for the Schedule 1 Other Costs footer.
 *
 * `rmg` is also server-derived and also not mirrored, for a different reason: it has no id anywhere in
 * `schedule6.xhtml` and no handler targets it, and deriving it needs the year-scoped TSA/TFL code
 * caches that have no REST counterpart (the page's pre-existing deviation A). It keeps refreshing on
 * the Save echo.
 *
 * The page comment this replaces read "legacy re-derived them live over ajax, which AD-5 forbids
 * re-implementing on the client (deviation D)" — accurate about legacy, and exactly the AD-5 reading
 * the 2026-08-20 amendment corrects: the rule governs authority, not display.
 */
export function recordCostPerVolume(values: {
  readonly volume: string
  readonly cost: string
}): number | null {
  // `committedNum` + `wholeDollars` match `buildBody`'s roundCost(parseDecimalInput(...)) exactly, so
  // a fractional or lax-parsed entry cannot show a rate the Save would change (code review 2026-08-21).
  return perUnitLegacy(wholeDollars(committedNum(values.cost)), committedNum(values.volume))
}

/** The two committed fields the rate is computed from — the rest of the form cannot affect it. */
export type RateInputs = { readonly volume: string; readonly cost: string }

export const rateInputsOf = (form: RoadRecordFormValues): RateInputs => ({
  volume: form.volume,
  cost: form.cost,
})

export const EMPTY_RATE_INPUTS: RateInputs = { volume: '', cost: '' }
