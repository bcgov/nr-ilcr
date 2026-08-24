import type Schedule1Response from '@/interfaces/Schedule1Response'
import { addN, enteredNum, perUnitLegacy, subN, sumAsZero, wholeDollars } from '@/utils/derivedMath'
import { WRITABLE_LINE_ITEM_CODES } from '@/interfaces/Schedule1Request'

/**
 * Schedule 1's DISPLAY-ONLY derived-figure mirror (defect #291).
 *
 * Transcribed from `Schedule1Service` (`.../schedule1/Schedule1Service.java:505-557`), which is the
 * authority. Nothing here is ever sent on a write, and the Save echo replaces every figure it
 * produces.
 *
 * Two things on this page are easy to get wrong, and both are transcribed deliberately:
 *
 * 1. **`$/m³` uses the LEGACY scale-2 rule** ({@link perUnitLegacy}), not the scale-4 rule Schedules 2
 *    and 4 use. `Schedule1Service.perUnit` divides at scale 10 then rounds to 2, and its javadoc
 *    records that as the fix for an earlier divergence. It also takes `(volume, cost)` — the reverse
 *    of Schedule 2's and 4's helpers — which is why this module names its arguments.
 * 2. **Null-as-0 vs null-propagation is per FIGURE, not per page.** `subtotalCompanyLoggingCost` sums
 *    with nulls as 0 and is therefore never blank (legacy seeds its Other-Costs term at 0), while
 *    `totalSilvicultureCost` propagates null so an empty silviculture block leaves the cell blank
 *    rather than showing a negative admin cost. `totalCompanyLoggingCost` then adds the two.
 *
 * The carried Schedule 3 pulls (`forestMgmtAdminCost`, `lessSilvAdminCost`, `schedule3CrownVolume`)
 * and the Other-Costs subtotal come off the loaded document: they cannot move while Schedule 1 is
 * edited (the subtotal is owned by the Other-Costs sub-resource), so the mirror consumes them as
 * constants. The Other-Costs **volume**, by contrast, IS entered on this page, so its `$/m³` moves.
 */

/** Every entered value the mirror needs, parsed from the committed (blurred) form strings. */
export interface Schedule1Entered {
  /** Per-code volume, for the writable line items plus 143/144 and silviculture 1/2/139/140. */
  readonly volume: Readonly<Record<number, number | null>>
  /** Per-code cost, for the writable line items plus silviculture 1/2. */
  readonly cost: Readonly<Record<number, number | null>>
  /** The shared Other-Costs volume (item 19). */
  readonly otherCostsVolume: number | null
}

export interface Schedule1Derived {
  /** `$/m³` per cost-item code: the writable lines, 143, 139, 140, 144 and silviculture 1/2. */
  readonly perUnit: Readonly<Record<number, number | null>>
  readonly totalSilvicultureCost: number | null
  readonly subtotalCompanyLoggingCost: number | null
  readonly totalCompanyLoggingCost: number | null
  readonly totalCompanyLoggingPerUnit: number | null
  readonly otherCostsPerUnit: number | null
}

const CODE_SILV_ACTUAL = 1
const CODE_SILV_ACCRUED = 2
const CODE_SILV_LESS_ADMIN = 139
const CODE_SILV_TOTAL = 140
const CODE_FOREST_MGMT_ADMIN = 143
const CODE_SUBTOTAL_COMPANY_LOGGING = 144

/** Parse the committed form into the shape {@link deriveSchedule1} consumes. */
export function enteredFromForm(form: Readonly<Record<string, string>>): Schedule1Entered {
  const volume: Record<number, number | null> = {}
  const cost: Record<number, number | null> = {}
  const codes = [
    ...WRITABLE_LINE_ITEM_CODES,
    CODE_SILV_ACTUAL,
    CODE_SILV_ACCRUED,
    CODE_SILV_LESS_ADMIN,
    CODE_SILV_TOTAL,
    CODE_FOREST_MGMT_ADMIN,
    CODE_SUBTOTAL_COMPANY_LOGGING,
  ]
  for (const code of codes) {
    volume[code] = enteredNum(form[`vol-${code}`] ?? '')
    cost[code] = enteredNum(form[`cost-${code}`] ?? '')
  }
  return { volume, cost, otherCostsVolume: enteredNum(form['otherCostsVolume'] ?? '') }
}

export function deriveSchedule1(
  doc: Schedule1Response,
  entered: Schedule1Entered,
): Schedule1Derived {
  // Carried from Schedule 3 / the Other-Costs sub-resource — constants during Schedule 1 entry.
  const forestMgmtAdminCost = doc.forestMgmtAdminCost
  const lessSilvAdminCost = doc.lessSilvAdminCost
  const otherCostsSubtotal = doc.otherCosts.costSubtotal
  const crownVolume = doc.schedule3CrownVolume

  const perUnit: Record<number, number | null> = {}

  // The writable logging lines plus silviculture 1/2: both halves entered.
  for (const code of [...WRITABLE_LINE_ITEM_CODES, CODE_SILV_ACTUAL, CODE_SILV_ACCRUED]) {
    perUnit[code] = perUnitLegacy(entered.cost[code] ?? null, entered.volume[code] ?? null)
  }

  // 143 / 139: the cost is pulled from Schedule 3 (read-only), the volume is entered here.
  perUnit[CODE_FOREST_MGMT_ADMIN] = perUnitLegacy(
    forestMgmtAdminCost,
    entered.volume[CODE_FOREST_MGMT_ADMIN] ?? null,
  )
  perUnit[CODE_SILV_LESS_ADMIN] = perUnitLegacy(
    lessSilvAdminCost,
    entered.volume[CODE_SILV_LESS_ADMIN] ?? null,
  )

  // 140 Total Silviculture — legacy Schedule1MB.getTotalSilvCost: (Actual $ Spent − Sch3 silviculture
  // admin) + Accrued, with CoreUtil null-propagation, NOT null-as-0. The admin cost is subtracted only
  // when Actual is present, so a blank silviculture block stays blank instead of showing a negative.
  // `wholeDollars` mirrors the server's Integer COST column, as Schedule 2's mirror already did: a
  // fractional entry would otherwise show cents in a whole-dollar cell for a save the backend refuses
  // (`accept-float-as-int: false`) — code review 2026-08-21.
  const totalSilvicultureCost = wholeDollars(
    addN(
      subN(entered.cost[CODE_SILV_ACTUAL] ?? null, lessSilvAdminCost),
      entered.cost[CODE_SILV_ACCRUED] ?? null,
    ),
  )
  perUnit[CODE_SILV_TOTAL] = perUnitLegacy(
    totalSilvicultureCost,
    entered.volume[CODE_SILV_TOTAL] ?? null,
  )

  // 144 Subtotal Company Logging — the logging lines + Forest Mgmt Admin + Other Costs, nulls as 0.
  // Never blank in legacy (its Other-Costs term seeds at 0), hence sumAsZero rather than addN.
  const subtotalCompanyLoggingCost = wholeDollars(
    sumAsZero(
      ...WRITABLE_LINE_ITEM_CODES.map((code) => entered.cost[code] ?? null),
      forestMgmtAdminCost,
      otherCostsSubtotal,
    ),
  )
  perUnit[CODE_SUBTOTAL_COMPANY_LOGGING] = perUnitLegacy(
    subtotalCompanyLoggingCost,
    entered.volume[CODE_SUBTOTAL_COMPANY_LOGGING] ?? null,
  )

  // Grand total = subtotal + total silviculture (null-propagating add; the subtotal is never null, so
  // a blank Total Silviculture leaves the grand total equal to the subtotal). Its $/m³ divides by the
  // Schedule 3 harvested crown-timber volume (item 119), NOT by an entered volume.
  const totalCompanyLoggingCost = wholeDollars(
    addN(subtotalCompanyLoggingCost, totalSilvicultureCost),
  )
  const totalCompanyLoggingPerUnit = perUnitLegacy(totalCompanyLoggingCost, crownVolume)

  // Other Costs: the subtotal is owned by the sub-resource, but the shared volume is entered here.
  const otherCostsPerUnit = perUnitLegacy(otherCostsSubtotal, entered.otherCostsVolume)

  return {
    perUnit,
    totalSilvicultureCost,
    subtotalCompanyLoggingCost,
    totalCompanyLoggingCost,
    totalCompanyLoggingPerUnit,
    otherCostsPerUnit,
  }
}
