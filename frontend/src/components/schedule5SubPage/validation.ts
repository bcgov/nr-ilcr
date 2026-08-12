// Advisory client-side validation for a Schedule 5 expense sub-page (Story 7.4). The BACKEND is
// authoritative; these checks give immediate inline feedback and gate the call to avoid a doomed
// round-trip. Ranges and messages MIRROR the backend SubPageRowRequest DTO, the per-page narrowing
// in Schedule5Service, and the message bundle — so an advisory message reads identically to a server
// rejection, which still renders verbatim on a 400 (AD-6/AD-8) and is never replaced by anything
// here.

import { parseDecimalInput, roundCost } from '@/utils/number'
import type { SubPageKind, SubPageRowForm } from '@/interfaces/Schedule5SubPage'

export { parseDecimalInput, roundCost }

/** The screen's own cap, on all four inputs across both pages (`maxlength="30"`). */
export const DESCRIPTION_MAX_LENGTH = 30

// Verbatim from the backend bundle so an advisory message is byte-identical to the server's
// rejection for the same field.
export const SUB_PAGE_MESSAGES = {
  descriptionRequired: 'Value Required',
  descriptionMaxLength: 'Description must be 30 characters or fewer.',
  costInvalid: 'Entered cost is invalid.',
  campCostRange: 'Entered cost must be between -9,999,999 and 9,999,999.',
  accessCostRange: 'Entered cost must be between -99,999,999 and 99,999,999.',
} as const

/**
 * The two cost bands, as DATA keyed by page rather than an `if` at each call site.
 *
 * <strong>The bound is per PAGE, not per control</strong> — this is the single most easily-lost fact
 * in the whole story. EVERY cost input on the Other Camp sub-page carries `costSize="7"`
 * (`schedule5CampExpenses.xhtml:45` add-form, `:79` grid) → ±9,999,999; NEITHER Other Access input
 * carries one (`schedule5AccessExpenses.xhtml:36-38`, `:71-76`) → the ILCRCostValidator default of
 * ±99,999,999.
 *
 * The committed AC and all three UC documents record this incorrectly (deviation (A)); the legacy
 * source is what this table follows.
 */
export const COST_BANDS = {
  CAMP: { min: -9_999_999, max: 9_999_999, message: SUB_PAGE_MESSAGES.campCostRange },
  ACCESS: { min: -99_999_999, max: 99_999_999, message: SUB_PAGE_MESSAGES.accessCostRange },
} as const satisfies Record<SubPageKind, { min: number; max: number; message: string }>

/**
 * Whether a cleared row description is rejected on change/blur or only at Save.
 *
 * <strong>This IS slice S21 vs S22.</strong> The Access grid's description input carries
 * `<f:ajax event="change">` (`schedule5AccessExpenses.xhtml:63`) and the Camp grid's does not
 * (`schedule5CampExpenses.xhtml:64-67`), so legacy genuinely defers the Camp check to Save.
 * Validating both on change would erase the distinction the two slices exist to describe.
 *
 * The scope is per INPUT, not per grid: legacy's `f:ajax` processes only the input that changed, so
 * a change validates THAT row's description and nothing else. Whole-grid validation on change would
 * flag untouched rows — including a legally-stored blank description (deviation (F)) the licensee
 * never touched. And no COST validates on change on either page: neither cost input carries
 * `f:ajax`, so the bands surface at Add/Save exactly like the Camp description.
 */
export const VALIDATES_ROW_ON_CHANGE: Record<SubPageKind, boolean> = {
  CAMP: false,
  ACCESS: true,
}

export type SubPageErrors = Readonly<Record<string, string>>

/** Field-key helper so the grid and the error map agree on one naming scheme. */
export const rowFieldKey = (index: number, field: 'description' | 'cost'): string =>
  `rows.${index}.${field}`

const validateDescription = (raw: string, required: boolean): string | null => {
  const trimmed = raw.trim()
  if (required && trimmed === '') {
    return SUB_PAGE_MESSAGES.descriptionRequired
  }
  // Length is measured on the RAW value, matching the input's own maxlength: a trailing space the
  // user typed still occupies a character in the column.
  if (raw.length > DESCRIPTION_MAX_LENGTH) {
    return SUB_PAGE_MESSAGES.descriptionMaxLength
  }
  return null
}

/**
 * The change-driven check for ONE row's description — the `f:ajax event="change"` analog. Returns
 * the error for that single field, or null; the caller touches no other row's errors.
 */
export const validateDescriptionOnChange = (row: SubPageRowForm): string | null =>
  validateDescription(row.description, true)

/**
 * Validate a cost entry for one page. Blank is VALID — a null cost is storable and is what Check
 * Status flags, not something to block here.
 *
 * The value is ROUNDED FIRST and the ROUNDED value is range-checked, because rounding is what will
 * actually be sent: `9,999,999.4` rounds to the accepted `9,999,999`, while checking the raw value
 * first would reject it and checking the raw value only would let `9,999,999.6` through to a server
 * 400.
 */
const validateCost = (raw: string, kind: SubPageKind): string | null => {
  if (raw.trim() === '') {
    return null
  }
  const parsed = parseDecimalInput(raw)
  if (parsed === null) {
    return SUB_PAGE_MESSAGES.costInvalid
  }
  const rounded = roundCost(parsed)
  const band = COST_BANDS[kind]
  if (rounded === null || rounded < band.min || rounded > band.max) {
    return band.message
  }
  return null
}

/**
 * Validate the ADD form. Both pages require a description here — verified at source:
 * `schedule5CampExpenses.xhtml:39` and `schedule5AccessExpenses.xhtml:32` both carry
 * `required="true"`. The committed AC3 claims the Camp add-form does not; it does (deviation (A)).
 */
export const validateAddForm = (form: SubPageRowForm, kind: SubPageKind): SubPageErrors => {
  const errors: Record<string, string> = {}
  const description = validateDescription(form.description, true)
  if (description) {
    errors.description = description
  }
  const cost = validateCost(form.cost, kind)
  if (cost) {
    errors.cost = cost
  }
  return errors
}

/**
 * Validate the GRID rows.
 *
 * `enforceRequired` carries the S21/S22 timing: pass `false` for a change-driven pass on the Camp
 * page (where legacy defers the required check to Save) and `true` at Save on either page, or on
 * any change on the Access page.
 */
export const validateRows = (
  rows: readonly SubPageRowForm[],
  kind: SubPageKind,
  enforceRequired: boolean,
): SubPageErrors => {
  const errors: Record<string, string> = {}
  rows.forEach((row, index) => {
    const description = validateDescription(row.description, enforceRequired)
    if (description) {
      errors[rowFieldKey(index, 'description')] = description
    }
    const cost = validateCost(row.cost, kind)
    if (cost) {
      errors[rowFieldKey(index, 'cost')] = cost
    }
  })
  return errors
}

export const isSubPageValid = (errors: SubPageErrors): boolean => Object.keys(errors).length === 0

/**
 * A grid row as it goes on the wire. A blank description is sent as `null` rather than `""` so the
 * client and the server agree on one representation of "absent" — Oracle stores an empty string as
 * NULL anyway, and the served document would otherwise disagree with what was posted.
 */
export const toRowRequest = (
  row: SubPageRowForm,
): { rowId: number | null; description: string | null; cost: number | null } => ({
  rowId: row.rowId,
  description: row.description.trim() === '' ? null : row.description,
  cost: roundCost(parseDecimalInput(row.cost)),
})
