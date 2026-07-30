// Advisory client-side validation for an Included Unacceptable Cost row (Story 4.4). The BACKEND is
// authoritative; these checks give immediate inline feedback and gate the call. Ranges + messages
// MIRROR the backend UnacceptableRequest DTO / message bundle.

const COST = { min: -99_999_999, max: 99_999_999 } // FLD-001 (default cost range)
const DESCRIPTION_MAX = 30

export const UNACCEPTABLE_MESSAGES = {
  descriptionRequired: 'Description: Value is required.',
  descriptionMaxLength: 'Description must be 30 characters or fewer.',
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
} as const

export interface UnacceptableErrors {
  description?: string
  total?: string
}

/** Advisory validation for one Included Unacceptable row's inputs (raw strings from the form). */
export function validateUnacceptable(description: string, totalRaw: string): UnacceptableErrors {
  const errors: UnacceptableErrors = {}
  const desc = description.trim()
  if (desc === '') {
    errors.description = UNACCEPTABLE_MESSAGES.descriptionRequired
  } else if (desc.length > DESCRIPTION_MAX) {
    errors.description = UNACCEPTABLE_MESSAGES.descriptionMaxLength
  }
  const value = totalRaw.trim()
  if (value !== '') {
    const n = Number(value)
    if (Number.isNaN(n)) {
      errors.total = UNACCEPTABLE_MESSAGES.costInvalid
    } else if (n < COST.min || n > COST.max) {
      errors.total = UNACCEPTABLE_MESSAGES.cost
    }
  }
  return errors
}

export const DESCRIPTION_MAX_LENGTH = DESCRIPTION_MAX
