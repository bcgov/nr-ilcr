// Advisory client-side validation for an Other Acceptable Cost row (Story 4.4). The BACKEND is
// authoritative; these checks give immediate inline feedback and gate the call. Ranges + messages
// MIRROR the backend OtherAcceptableRequest DTO / message bundle — keep them in sync.

const COST = { min: -99_999_999, max: 99_999_999 } // FLD-001 (default cost range)
const DESCRIPTION_MAX = 30

export const OTHER_ACCEPTABLE_MESSAGES = {
  descriptionRequired: 'Description: Value is required.',
  descriptionMaxLength: 'Description must be 30 characters or fewer.',
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
} as const

export interface OtherAcceptableErrors {
  description?: string
  total?: string
  pop?: string
}

function costError(raw: string): string | undefined {
  const value = raw.trim()
  if (value === '') {
    return undefined
  }
  const n = Number(value)
  if (Number.isNaN(n)) {
    return OTHER_ACCEPTABLE_MESSAGES.costInvalid
  }
  if (n < COST.min || n > COST.max) {
    return OTHER_ACCEPTABLE_MESSAGES.cost
  }
  return undefined
}

/** Advisory validation for one Other Acceptable row's inputs (raw strings from the form). */
export function validateOtherAcceptable(
  description: string,
  totalRaw: string,
  popRaw: string,
): OtherAcceptableErrors {
  const errors: OtherAcceptableErrors = {}
  const desc = description.trim()
  if (desc === '') {
    errors.description = OTHER_ACCEPTABLE_MESSAGES.descriptionRequired
  } else if (desc.length > DESCRIPTION_MAX) {
    errors.description = OTHER_ACCEPTABLE_MESSAGES.descriptionMaxLength
  }
  const total = costError(totalRaw)
  if (total) {
    errors.total = total
  }
  const pop = costError(popRaw)
  if (pop) {
    errors.pop = pop
  }
  return errors
}

export const DESCRIPTION_MAX_LENGTH = DESCRIPTION_MAX
