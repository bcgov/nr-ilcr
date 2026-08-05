// Advisory client-side validation for a Schedule 11 (Basic Silviculture) location. The BACKEND is
// authoritative (Story 25.2); these checks give immediate inline feedback and gate the call to avoid
// a doomed round-trip. Ranges + messages MIRROR the backend SilvicultureLocationRequest DTO / message
// bundle — the frontend still renders the server's rejection verbatim (AD-8), so this never replaces
// the authoritative response.

import type { BiogeoclimaticOption } from '@/interfaces/Schedule11Response'

const LOCATION_MAX = 30
const NET_AREA = { min: 0, max: 999_999.9 }
const COST = { min: -99_999_999, max: 99_999_999 }
const COMMENTS_MAX = 3500

// Verbatim wording mirroring the backend bundle so an advisory message reads identically to a server
// response. Two strings are PROVISIONAL — the exact live-app text is confirmed in Story 25.4 — but
// the frontend renders the backend's authoritative text verbatim regardless.
export const SILV_MESSAGES = {
  locationRequired: 'Location: Value is required.',
  locationMaxLength: 'Location must be 30 characters or fewer.',
  // PROVISIONAL (S15): live-app text unconfirmed.
  enhancedRequired: 'Enhanced: Value is required.',
  biogeoRequired: 'Biogeo/Subzone/Variant: Value is required.',
  netAreaRequired: 'NAR(ha): Value is required.',
  // PROVISIONAL (S18): live-app text unconfirmed; also fires on more than one decimal place.
  netAreaRange: 'Entered NAR (ha) must be between 0 and 999,999.9.',
  costValidator: 'Entered cost must be between -99,999,999 and 99,999,999.',
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
} as const

export interface SilvicultureErrors {
  location?: string
  enhanced?: string
  bec?: string
  netArea?: string
  actualCost?: string
  plannedCost?: string
  comments?: string
}

// The raw form values gathered by the Add panel / inline editor before submission.
export interface LocationFormValues {
  location: string
  enhanced: boolean | null
  bec: BiogeoclimaticOption | null
  netArea: string
  actualCost: string
  plannedCost: string
  comments: string
}

// Legacy JSF numeric fields (NAR, costs) bind through a US-locale DecimalFormat converter, so the
// accepted syntax is: an optional leading '-', digits with optional comma grouping, and an optional
// '.' fractional part — the same format the page DISPLAYS (the money/area masks in index.tsx). Native
// Number() diverges both ways and must NOT be used here: it rejects grouped input the legacy app
// accepts (`Number('1,000')` -> NaN) and silently accepts JS-only forms the legacy app never allowed
// (`1e2` -> 100, `0x10` -> 16, `Infinity`). Parsing with an explicit format keeps both this advisory
// gate AND the submitted body (index.tsx buildBody) faithful to legacy. Returns the numeric value, or
// null when the string is blank or not a valid decimal in that format.
const DECIMAL_INPUT = /^-?(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$/
export const parseDecimalInput = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '' || !DECIMAL_INPUT.test(trimmed)) {
    return null
  }
  return Number(trimmed.replace(/,/g, ''))
}

const validateCost = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const n = parseDecimalInput(trimmed)
  if (n === null || n < COST.min || n > COST.max) {
    return SILV_MESSAGES.costValidator
  }
  return undefined
}

/** Advisory validation for one location's entered values. */
export function validateLocation(form: LocationFormValues): SilvicultureErrors {
  const errors: SilvicultureErrors = {}

  const location = form.location.trim()
  if (location === '') {
    errors.location = SILV_MESSAGES.locationRequired
  } else if (location.length > LOCATION_MAX) {
    errors.location = SILV_MESSAGES.locationMaxLength
  }

  // Enhanced is a required boolean — "not selected" (null) must be expressible and rejected (S15).
  if (form.enhanced === null) {
    errors.enhanced = SILV_MESSAGES.enhancedRequired
  }

  // Forced selection (BR-09/S16): only a value resolved to a catalogue option counts; free text that
  // was never chosen leaves `bec` null and is treated as empty.
  if (form.bec === null) {
    errors.bec = SILV_MESSAGES.biogeoRequired
  }

  const netAreaRaw = form.netArea.trim()
  if (netAreaRaw === '') {
    errors.netArea = SILV_MESSAGES.netAreaRequired
  } else {
    const n = parseDecimalInput(netAreaRaw)
    // Fractional digits counted from the raw string (grouping lives only in the integer part, so the
    // post-'.' slice is unaffected): >1 decimal trips the same S18/BR-05 one-decimal cap as the backend.
    const decimals = netAreaRaw.includes('.') ? netAreaRaw.split('.')[1].length : 0
    if (n === null || n < NET_AREA.min || n > NET_AREA.max || decimals > 1) {
      errors.netArea = SILV_MESSAGES.netAreaRange
    }
  }

  const actualCost = validateCost(form.actualCost)
  if (actualCost) {
    errors.actualCost = actualCost
  }
  const plannedCost = validateCost(form.plannedCost)
  if (plannedCost) {
    errors.plannedCost = plannedCost
  }

  if (form.comments.length > COMMENTS_MAX) {
    errors.comments = SILV_MESSAGES.commentsMaxLength
  }

  return errors
}

export const LOCATION_MAX_LENGTH = LOCATION_MAX
export const COMMENTS_MAX_LENGTH = COMMENTS_MAX
