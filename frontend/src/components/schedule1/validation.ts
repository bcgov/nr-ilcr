// Advisory client-side validation for the Schedule 1 form. The BACKEND is authoritative (AD-8);
// these checks only give immediate inline feedback and gate Save to avoid a doomed round-trip.
// Ranges + messages MIRROR the Story 2.1 backend `Schedule1Request` DTO / message bundle — keep the
// two in sync (a range change on the server must be reflected here). Blank is always allowed (legacy
// accepts blank amounts at Save; Check Status catches missing required fields — Story 2.7).

const COST = { min: -99_999_999, max: 99_999_999 } // FLD-001 (default cost range)
const VOLUME_7_DIGIT = { min: -9_999_999, max: 9_999_999 } // FLD-002 (fixed line items + silviculture)
const VOLUME_8_DIGIT = { min: -99_999_999, max: 99_999_999 } // FLD-003 (shared Other-Costs volume)

// Verbatim legacy wording (matches the backend bundle) so the advisory message reads identically to
// what the server would return; they are frontend constants only because no request has been made yet.
export const VALIDATION_MESSAGES = {
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  volume7: 'Entered volume must be between -9,999,999 and 9,999,999.',
  volume8: 'Entered volume must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
  volumeInvalid: 'Entered volume entry is invalid.',
} as const

type FieldKind = 'cost' | 'volume7' | 'volume8'

/** Classify a form field key (e.g. `cost-12`, `vol-1`, `otherCostsVolume`) into a validator group. */
function fieldKind(key: string): FieldKind | null {
  if (key === 'otherCostsVolume') {
    return 'volume8'
  }
  if (key.startsWith('cost-')) {
    return 'cost'
  }
  if (key.startsWith('vol-')) {
    return 'volume7'
  }
  return null
}

function validateValue(raw: string, kind: FieldKind): string | null {
  if (raw.trim() === '') {
    return null
  }
  const value = Number(raw)
  if (Number.isNaN(value)) {
    return kind === 'cost' ? VALIDATION_MESSAGES.costInvalid : VALIDATION_MESSAGES.volumeInvalid
  }
  const range = kind === 'cost' ? COST : kind === 'volume7' ? VOLUME_7_DIGIT : VOLUME_8_DIGIT
  if (value < range.min || value > range.max) {
    if (kind === 'cost') {
      return VALIDATION_MESSAGES.cost
    }
    return kind === 'volume7' ? VALIDATION_MESSAGES.volume7 : VALIDATION_MESSAGES.volume8
  }
  return null
}

/** Advisory validation: returns a map of fieldKey → error message for every invalid writable field. */
export function validateSchedule1(form: Record<string, string>): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const [key, raw] of Object.entries(form)) {
    const kind = fieldKind(key)
    if (!kind) {
      continue
    }
    const message = validateValue(raw, kind)
    if (message) {
      errors[key] = message
    }
  }
  return errors
}
