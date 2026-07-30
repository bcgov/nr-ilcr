// Advisory client-side validation for the Schedule 3 form. The BACKEND is authoritative (AD-8); these
// checks only give immediate inline feedback and gate Save to avoid a doomed round-trip. Ranges +
// messages MIRROR the Story 4.2 backend `Schedule3Request` DTO / message bundle — keep the two in sync.
// Blank is always allowed (legacy accepts blank amounts at Save; Check Status catches missing required
// fields — Story 4.2). NOTE: Schedule 3 volumes are NON-NEGATIVE [0, 9,999,999] (volumeValidatorErrorMsg),
// distinct from Schedule 1's signed 7-digit range.

const COST = { min: -99_999_999, max: 99_999_999 } // FLD-001 (costValidatorErrorMsg)
const VOLUME = { min: 0, max: 9_999_999 } // FLD-002 (volumeValidatorErrorMsg) — non-negative

// Verbatim legacy wording (matches the backend bundle) so the advisory message reads identically to
// what the server would return; frontend constants only because no request has been made yet.
export const VALIDATION_MESSAGES = {
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  volume: 'Entered volume must be between 0 and 9,999,999.',
  costInvalid: 'Entered cost is invalid.',
  volumeInvalid: 'Entered volume entry is invalid.',
} as const

type FieldKind = 'cost' | 'volume'

/** Classify a form field key (`harvest-27`, `pop-28`, `popTimberVolume`, `crownTimberVolume`). */
function fieldKind(key: string): FieldKind | null {
  if (key === 'popTimberVolume' || key === 'crownTimberVolume') {
    return 'volume'
  }
  if (key.startsWith('harvest-') || key.startsWith('pop-')) {
    return 'cost'
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
  const range = kind === 'cost' ? COST : VOLUME
  if (value < range.min || value > range.max) {
    return kind === 'cost' ? VALIDATION_MESSAGES.cost : VALIDATION_MESSAGES.volume
  }
  return null
}

/** Advisory validation: returns a map of fieldKey → error message for every invalid writable field. */
export function validateSchedule3(form: Record<string, string>): Record<string, string> {
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
