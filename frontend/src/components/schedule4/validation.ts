// Category template + advisory client-side validation for the Schedule 4 location panel. The BACKEND
// is authoritative; these checks give immediate inline feedback and gate Save to avoid a doomed
// round-trip. Ranges + messages MIRROR the Schedule 4 write DTO / message bundle. Category labels are
// the legacy cost-item names (Constants.REPORT_COST_ITEMS).

import { legacyRequiredMessage } from '@/utils/legacyValidationBanner'
import { isBlank, rangeError } from './fieldRange'
import { subPageDefByCode } from './subPageDefs'

export interface CategoryDef {
  code: number
  label: string
  kind: 'FIXED' | 'DISTANCE'
}

// The 9 fixed no-distance categories (on the primary report), in legacy order.
export const FIXED_CATEGORIES: CategoryDef[] = [
  { code: 40, label: 'Lakeside Dry Dump', kind: 'FIXED' },
  { code: 41, label: 'Water Dump', kind: 'FIXED' },
  { code: 42, label: 'Water Boom', kind: 'FIXED' },
  { code: 44, label: 'Williston Lake Dewater Only', kind: 'FIXED' },
  { code: 45, label: 'Dewater and Reload', kind: 'FIXED' },
  { code: 49, label: 'Hydro Dam Log Transfer', kind: 'FIXED' },
  { code: 50, label: 'Truck to Truck Transfer', kind: 'FIXED' },
  { code: 51, label: 'Truck to Rail Transfer', kind: 'FIXED' },
  { code: 53, label: 'Low Water Bridge', kind: 'FIXED' },
]

// The 3 distance-based categories (each its own report + distance).
export const DISTANCE_CATEGORIES: CategoryDef[] = [
  { code: 47, label: 'Truck Barge/Ferry', kind: 'DISTANCE' },
  { code: 48, label: 'Crew Barge/Ferry', kind: 'DISTANCE' },
  { code: 52, label: 'Rail Haul', kind: 'DISTANCE' },
]

export const ALL_CATEGORIES: CategoryDef[] = [...FIXED_CATEGORIES, ...DISTANCE_CATEGORIES]

/**
 * The legacy cost-item name for a Schedule 4 code — the twelve category codes above plus the three
 * list sub-pages — so a Check Status issue, which carries only the code and the bare "Value Required",
 * can name the field the way legacy did ("Location : <name> - Lakeside Dry Dump (Cost $): Value
 * Required", Schedule4MB.java:688). Undefined for a code the tables lack: callers fall back to the
 * bare text rather than inventing a label.
 */
export function labelFor(code: number): string | undefined {
  return ALL_CATEGORIES.find((def) => def.code === code)?.label ?? subPageDefByCode(code)?.label
}

/**
 * The location description's issue code — `FieldIssue.LOCATION_DESCRIPTION` on the API. Never a
 * cost-item code (those start at 40).
 */
export const LOCATION_DESCRIPTION_CODE = 0

/**
 * The field a Schedule 4 Check Status issue refers to, as legacy named it. Since issue #465 the API
 * reports one field only — the location description, which legacy's Check Status tab labelled
 * "Description" (checkStatusSchedule4.xhtml:16-23) — because legacy never required a Schedule 4
 * Cost: its per-category checks sat behind `isXxxToCheck` flags that were never set true. A
 * cost-item code still resolves to "<category> (Cost $)" (the Schedule4MB.java:688 wording, #326) so
 * a finding would be named should a Cost check ever be enabled; an unknown code is undefined and the
 * caller shows the bare text rather than inventing a label.
 */
export function checkStatusFieldLabel(code: number): string | undefined {
  if (code === LOCATION_DESCRIPTION_CODE) return 'Description'
  const category = labelFor(code)
  return category === undefined ? undefined : `${category} (Cost $)`
}

/**
 * How a Check Status banner names a location. The name, when there is one; when the description is
 * null or blank — which is the one finding the check can raise, and the backend passes the stored
 * value through as-is — the location has no name to show, so the report id stands in. Legacy's tab
 * printed "Location Descriprion - " followed by nothing.
 */
export function checkStatusLocationName(location: {
  readonly id: number | null | undefined
  readonly name: string | null | undefined
}): string {
  if (location.name !== null && location.name !== undefined && location.name.trim() !== '') {
    return location.name
  }
  // Guard undefined as well as null: `id` is typed nullable, but a payload that omits it would
  // otherwise render "Location undefined".
  return location.id === null || location.id === undefined ? 'Location' : `Location ${location.id}`
}

const VOLUME = { min: 0, max: 9_999_999 }
const COST = { min: -99_999_999, max: 99_999_999 }
const DISTANCE = { min: 0, max: 999_999.9 }

export const VALIDATION_MESSAGES = {
  nameEmpty: 'Location Name can not be empty. Please enter a description.',
  volume: 'Entered volume must be between 0 and 9,999,999.',
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  // Shares `distanceValidatorErrorMsg` with Schedule 5, which enforces the identical 0.0–999,999.9
  // band (DISTANCE above). Legacy's text understated the bound by 0.9 on BOTH schedules; the
  // Ministry ruled the text the defect on PR #370 (2026-08-27), so both now state the real bound.
  distance: 'Entered distance must be between 0 and 999,999.9.',
  required: 'Value Required',
} as const

export interface CategoryFormValue {
  volume: string
  cost: string
  distance: string
}

export type CategoryForm = Record<number, CategoryFormValue>

export interface LocationValidation {
  nameError?: string
  fieldErrors: Record<string, string> // key: `${code}-volume` | `${code}-cost` | `${code}-distance`
}

/**
 * Advisory validation for the location panel: blank name (ERR-001), per-category range checks, and
 * BR-04 bidirectional-required on the 3 distance categories (distance ⇄ volume/cost). Returns a name
 * error + a map of `${code}-{field}` → message for every invalid field.
 */
export function validateLocationForm(name: string, categories: CategoryForm): LocationValidation {
  const fieldErrors: Record<string, string> = {}
  const nameError = name.trim() === '' ? VALIDATION_MESSAGES.nameEmpty : undefined

  for (const def of ALL_CATEGORIES) {
    const value = categories[def.code] ?? { volume: '', cost: '', distance: '' }
    if (!isBlank(value.volume)) {
      const e = rangeError(value.volume, VOLUME, VALIDATION_MESSAGES.volume)
      if (e) fieldErrors[`${def.code}-volume`] = e
    }
    if (!isBlank(value.cost)) {
      const e = rangeError(value.cost, COST, VALIDATION_MESSAGES.cost)
      if (e) fieldErrors[`${def.code}-cost`] = e
    }
    if (def.kind === 'DISTANCE') {
      if (!isBlank(value.distance)) {
        const e = rangeError(value.distance, DISTANCE, VALIDATION_MESSAGES.distance)
        if (e) fieldErrors[`${def.code}-distance`] = e
      }
      // BR-04 bidirectional required (advisory).
      const hasVolume = !isBlank(value.volume)
      const hasCost = !isBlank(value.cost)
      const hasDistance = !isBlank(value.distance)
      if ((hasVolume || hasCost) && !hasDistance) {
        fieldErrors[`${def.code}-distance`] = VALIDATION_MESSAGES.required
      }
      if (hasDistance && !hasVolume) {
        fieldErrors[`${def.code}-volume`] = VALIDATION_MESSAGES.required
      }
      if (hasDistance && !hasCost) {
        fieldErrors[`${def.code}-cost`] = VALIDATION_MESSAGES.required
      }
    }
  }

  return { nameError, fieldErrors }
}

/** True when the validation has no name error and no field errors. */
export function isLocationFormValid(v: LocationValidation): boolean {
  return v.nameError === undefined && Object.keys(v.fieldErrors).length === 0
}

/**
 * Which legacy panel a Schedule 4 panel mode corresponds to, for its field labels. An open EXISTING
 * location rendered `schedule4ExistingLocation.xhtml`; Add New Location AND Copy both rendered
 * `schedule4NewLocation.xhtml` (`Schedule4MB.copyLocation` calls `addNewLocation`, `:284-285`). The
 * two files label the same inputs differently, so the banner follows the panel on screen.
 */
export type LegacyPanel = 'existing' | 'new'

type DistanceLabels = { distance: string; volume: string; cost: string }

/**
 * The `label` of every input Schedule 4 can REQUIRE, verbatim from the legacy XHTML — only the three
 * distance categories' Distance/Volume/Cost carry a (conditional) `required`, so these are the only
 * fields whose blank value reads `{label}: Value is required.`. Every other field error is a range
 * message, reported verbatim.
 *
 * - existing: `schedule4ExistingLocation.xhtml` — Truck Barge/Ferry `:523,547,568`, Crew Barge/Ferry
 *   `:606,629,650`, Rail Haul `:880,902,922`.
 * - new/copy: `schedule4NewLocation.xhtml` — Truck Barge/Ferry `:138,143,146`, Crew Barge/Ferry
 *   `:153,158,161`, Rail Haul `:201,206,209`. Every Distance there is labelled plain `Distance (Km)`,
 *   and each Volume label carries the markup `m&lt;sup&gt;3&lt;/sup&gt;`. Legacy's escaping banner printed
 *   that markup literally; that is a legacy defect, so the unit is rendered as `m³` here (Iman,
 *   2026-09-28). The label text is otherwise verbatim.
 */
const DISTANCE_LABELS: Record<LegacyPanel, Record<number, DistanceLabels>> = {
  existing: {
    47: {
      distance: 'Truck Barge Ferry (Km)',
      volume: 'Truck Barge Ferry Volume (m3)',
      cost: 'Truck Barge Ferry (Cost $)',
    },
    48: {
      distance: 'Crew Barge Ferry (Km)',
      volume: 'Crew Barge Ferry Volume (m3)',
      cost: 'Crew Barge Ferry (Cost $)',
    },
    52: {
      distance: 'Rail Haul (Km)',
      volume: 'Rail Haul Volume (m3)',
      cost: 'Rail Haul (Cost $)',
    },
  },
  new: {
    47: {
      distance: 'Distance (Km)',
      volume: 'Truck Barge Ferry (Volume m³)',
      cost: 'Truck Barge Ferry (Cost $)',
    },
    48: {
      distance: 'Distance (Km)',
      volume: 'Crew Barge Ferry (Volume m³)',
      cost: 'Crew Barge Ferry (Cost $)',
    },
    52: {
      distance: 'Distance (Km)',
      volume: 'Rail Haul (Volume m³)',
      cost: 'Rail Haul (Cost $)',
    },
  },
}

/** The location name's `label` — identical in both panels (`schedule4ExistingLocation.xhtml:15`,
 * `schedule4NewLocation.xhtml:14`), both `required="true"`. */
const LOCATION_NAME_LABEL = 'Location Name'

// The panel's grid order (legacy code order 40-55) and each row's column order (Dist, Volume, Cost),
// which is the order legacy's messages listed the failing fields in.
const GRID_ORDER = [...ALL_CATEGORIES].sort((a, b) => a.code - b.code)
const COLUMN_ORDER = ['distance', 'volume', 'cost'] as const

/**
 * The legacy banner lines for a blocked location panel (#359 group B), in page order: the name first,
 * then the grid row by row.
 *
 * The name follows JSF: `required` rejects only an EMPTY submission, so a blank name reads `Location
 * Name: Value is required.`, while a whitespace-only name passes `required` and meets the bean's own
 * `locationEmptyOrNull` check (`Schedule4MB.java:615`) — the ERR-001 text this page already shows
 * inline. A category's blank-but-required field reads `{label}: Value is required.`; a range error its
 * inline text.
 */
export function locationBannerLines(
  name: string,
  validation: LocationValidation,
  panel: LegacyPanel,
): string[] {
  const lines: string[] = []
  if (validation.nameError !== undefined) {
    lines.push(name === '' ? legacyRequiredMessage(LOCATION_NAME_LABEL) : validation.nameError)
  }
  for (const def of GRID_ORDER) {
    for (const field of COLUMN_ORDER) {
      const message = validation.fieldErrors[`${def.code}-${field}`]
      if (message === undefined) {
        continue
      }
      const label =
        message === VALIDATION_MESSAGES.required
          ? DISTANCE_LABELS[panel][def.code]?.[field]
          : undefined
      lines.push(label === undefined ? message : legacyRequiredMessage(label))
    }
  }
  return lines
}
