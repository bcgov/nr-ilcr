// Client-side FLD-001..005 validation for a code-table entry (Story 24.3 / UC-CODE-001). Mirrors the
// server-side rules so the user gets immediate feedback; the backend re-validates authoritatively.
// Messages are the exact FLD strings the API returns (AD-8 verbatim).

export type CodeEntryForm = {
  code: string
  description: string
  effectiveDate: string
  expiryDate: string
}

export type CodeEntryErrors = Partial<Record<keyof CodeEntryForm, string>>

/**
 * Validate an add/edit form. `requireCode` is false for an inline edit (the code is fixed and not
 * editable) so a blank-code error can't fire on an existing row. Contractual Item Codes requires an
 * expiry because the legacy page required both dates; generic tables retain the modern open-ended
 * null-expiry behavior. Per-table length caps are enforced by the inputs' maxLength (client) and
 * re-checked by the server. Messages are the FLD strings verbatim.
 */
export function validateCodeEntry(
  form: CodeEntryForm,
  requireCode = true,
  requireExpiry = false,
): CodeEntryErrors {
  const errors: CodeEntryErrors = {}
  if (requireCode && form.code.trim() === '') {
    errors.code = 'Code: Value is required.'
  }
  if (form.description.trim() === '') {
    errors.description = 'Description: Value is required.'
  }
  if (form.effectiveDate === '') {
    errors.effectiveDate = 'Effective Date: Value is required.'
  }
  if (requireExpiry && form.expiryDate === '') {
    errors.expiryDate = 'Expiry Date: Value is required.'
  }
  // Range check only when both are present; string ISO dates (yyyy-MM-dd) compare correctly.
  if (form.effectiveDate !== '' && form.expiryDate !== '' && form.expiryDate < form.effectiveDate) {
    errors.expiryDate = 'Expiry Date must be greater than or equal to Effective Date.'
  }
  return errors
}
