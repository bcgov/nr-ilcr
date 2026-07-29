// Category template + advisory client-side validation for the Schedule 4 location panel. The BACKEND
// is authoritative; these checks give immediate inline feedback and gate Save to avoid a doomed
// round-trip. Ranges + messages MIRROR the Schedule 4 write DTO / message bundle. Category labels are
// the legacy cost-item names (Constants.REPORT_COST_ITEMS).

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

const VOLUME = { min: 0, max: 9_999_999 }
const COST = { min: -99_999_999, max: 99_999_999 }
const DISTANCE = { min: 0, max: 999_999.9 }

export const VALIDATION_MESSAGES = {
  nameEmpty: 'Location Name can not be empty. Please enter a description.',
  volume: 'Entered volume must be between 0 and 9,999,999.',
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  distance: 'Entered distance must be between 0 and 999,999.',
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

const isBlank = (raw: string | undefined): boolean => raw === undefined || raw.trim() === ''

const rangeError = (
  raw: string,
  range: { min: number; max: number },
  message: string,
): string | undefined => {
  const value = Number(raw)
  if (Number.isNaN(value) || value < range.min || value > range.max) {
    return message
  }
  return undefined
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
