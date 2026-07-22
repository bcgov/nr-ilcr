// Advisory client-side validation for an Other-Costs row. The BACKEND is authoritative (Story 2.4);
// these checks give immediate inline feedback and gate the call to avoid a doomed round-trip. Ranges
// + messages MIRROR the backend OtherCostRequest DTO / message bundle — keep them in sync.

const COST = { min: -99_999_999, max: 99_999_999 } // FLD-001 (default cost range)
const DESCRIPTION_MAX = 30

// Verbatim wording (matches the backend bundle) so the advisory message reads identically to a server
// response. descriptionRequired replicates the legacy JSF default; descriptionMaxLength is the 2.4
// chosen text; cost/costInvalid match the legacy cost validator/converter.
export const OTHER_COST_MESSAGES = {
  descriptionRequired: 'Description: Value is required.',
  descriptionMaxLength: 'Description must be 30 characters or fewer.',
  cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
} as const

export interface OtherCostErrors {
  description?: string
  cost?: string
}

/** Advisory validation for one row's inputs (raw strings from the form). */
export function validateOtherCost(description: string, costRaw: string): OtherCostErrors {
  const errors: OtherCostErrors = {}

  const desc = description.trim()
  if (desc === '') {
    errors.description = OTHER_COST_MESSAGES.descriptionRequired
  } else if (desc.length > DESCRIPTION_MAX) {
    errors.description = OTHER_COST_MESSAGES.descriptionMaxLength
  }

  const cost = costRaw.trim()
  if (cost !== '') {
    const n = Number(cost)
    if (Number.isNaN(n)) {
      errors.cost = OTHER_COST_MESSAGES.costInvalid
    } else if (n < COST.min || n > COST.max) {
      errors.cost = OTHER_COST_MESSAGES.cost
    }
  }

  return errors
}

export const DESCRIPTION_MAX_LENGTH = DESCRIPTION_MAX
