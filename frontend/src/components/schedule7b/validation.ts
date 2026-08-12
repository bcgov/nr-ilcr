// Advisory client-side validation for one Schedule 7B culvert. The BACKEND is authoritative; these
// checks give immediate inline feedback and gate the call to avoid a doomed round-trip. Ranges and
// messages MIRROR the backend CulvertRequest DTO and message bundle, so an advisory message reads
// byte-identically to a server rejection — which still renders verbatim on a 400 (AD-6/AD-8), never
// replaced by anything here.
//
// Only Type and No of Pieces are required (legacy `required="true"`, schedule7B.xhtml:87,153). Span,
// Rise, Length, both costs and Comments are OPTIONAL at Save — Check Status is what flags them
// (BR-07). Do not tighten them.

import { utf8Length } from '@/utils/forms'
import { parseDecimalInput, roundCost } from '@/utils/number'

// Re-exported so this module stays the single validation surface the page imports from.
export { parseDecimalInput, roundCost }

// Two units, both enforced by the DTO (`CulvertRequest.comments`): 3,500 CHARACTERS is the legacy
// screen's own maxlength, 4,000 BYTES is the column's real width in the AL32UTF8 delivery database.
const COMMENTS_MAX = 3500
const COMMENTS_MAX_BYTES = 4000

// Span and rise share one numeric bound in legacy (`zero.minValue` / `schedule7b.size.maxValue`) but
// carry SEPARATE messages, matching the backend's separate keys: the 400 names no field, so one
// shared message left a reporter unable to tell which dimension failed.
const SIZE_MM = { min: 0, max: 9_999_999 }
// NUMBER(7,1) in the delivery schema. RANGE only — the backend deliberately carries no @Digits and
// rounds extra decimals to scale 1 on write (a client formatting to two places must still be able to
// save), so a fraction-digit check here would reject what the server accepts.
const LENGTH_M = { min: 0, max: 999_999.9 }
const PIECE_COUNT = { min: 1, max: 9_999 }
const COST = { min: -99_999_999, max: 99_999_999 }

// Verbatim from the backend bundle so an advisory message is indistinguishable from the server's.
export const CULVERT_MESSAGES = {
  valueRequired: 'Value Required',
  spanRange: 'Entered span must be between 0 and 9,999,999.',
  spanInvalid: 'Entered span is invalid.',
  riseRange: 'Entered rise must be between 0 and 9,999,999.',
  riseInvalid: 'Entered rise is invalid.',
  lengthRange: 'Entered culvert length must be between 0.0 and 999,999.9.',
  pieceCountRange: 'Entered number of pieces must be between 1 and 9,999.',
  pieceCountInvalid: 'Entered number of pieces is invalid.',
  costRange: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
} as const

// The two cost keys, exported so the page binds inputs and builds the request body from ONE list.
export const COST_FIELDS = ['materialCost', 'installCost'] as const

export type CostField = (typeof COST_FIELDS)[number]

/**
 * The legacy display mask on each numeric input, as its decimal count. Every one of these fields
 * carried an `f:convertNumber pattern` (or, for the costs, the `costConverter`), so the stored value
 * DISPLAYED through a mask — grouped with thousands separators, and with a fixed number of decimals
 * that does not depend on how Oracle happened to return the number:
 *
 * | Field | Legacy mask | Source |
 * |---|---|---|
 * | `spanSize` / `riseSize` | `#,###,##0` | `mask.bigDecimal.schedule7b.size` (`messages.properties:199`) |
 * | `length` | `###,##0.0` | `mask.bigDecimal.6digits1decimal` (`:206`) |
 * | `culvertPieceCount` | `#,##0` | `mask.int.4digits` (`:200`) |
 * | `materialCost` / `installCost` | `##,###,###` | `ILCRCostConverter.getAsString` |
 *
 * `length` is the one field with a decimal, and the visible consequence of getting this wrong: a
 * stored `12.0` renders as `12` without the mask, where the legacy screen showed `12.0`.
 */
export const MASK_DIGITS = {
  spanSize: 0,
  riseSize: 0,
  length: 1,
  culvertPieceCount: 0,
  materialCost: 0,
  installCost: 0,
} as const

/** The numeric fields, i.e. those carrying a display mask (everything but Type and Comments). */
export type MaskedField = keyof typeof MASK_DIGITS

// Every value is held as the raw string the user typed or picked ('' = blank/unselected), so
// "required but not entered" stays expressible and a typo survives on screen for correction.
export type CulvertFormValues = {
  culvertTypeCode: string
  spanSize: string
  riseSize: string
  length: string
  culvertPieceCount: string
  comments: string
} & Record<CostField, string>

export type CulvertErrors = Partial<Record<keyof CulvertFormValues, string>>

export const emptyCulvertForm = (): CulvertFormValues => ({
  culvertTypeCode: '',
  spanSize: '',
  riseSize: '',
  length: '',
  culvertPieceCount: '',
  materialCost: '',
  installCost: '',
  comments: '',
})

/**
 * An OPTIONAL whole number in [min, max]. Unparseable text reports the field's own converter message
 * — Schedule 7B is the first schedule with non-cost Integer fields, and the backend added
 * `culvertSpanConverterErrorMsg` / `culvertRiseConverterErrorMsg` /
 * `culvertPieceCountConverterErrorMsg` for exactly this case rather than telling a reporter who
 * mistyped a span that their COST was invalid.
 *
 * A fractional entry is not rejected for being fractional — legacy accepted one and let the NUMBER
 * column round it on storage — but the range is checked on the value **as parsed**, which is what
 * legacy's `f:validateDoubleRange` saw. Checking only the ROUNDED value (as this did until the
 * 2026-08-12 review) let `0.6` pieces through: it rounds to `1` and passes, where legacy compared
 * `0.6 < one.minValue` and rejected. The rounded value is checked as well, so a `9,999,999.5` span —
 * in range as parsed, `10,000,000` once rounded — is caught here rather than by the server.
 */
const validateWholeNumber = (
  raw: string,
  bounds: { min: number; max: number },
  rangeMessage: string,
  invalidMessage: string,
): string | undefined => {
  if (raw.trim() === '') {
    return undefined
  }
  const value = parseDecimalInput(raw)
  if (value === null) {
    return invalidMessage
  }
  if (value < bounds.min || value > bounds.max) {
    return rangeMessage
  }
  const rounded = roundCost(value) as number
  return rounded < bounds.min || rounded > bounds.max ? rangeMessage : undefined
}

// Costs are optional at save — only Check Status flags a missing one (BR-07). Checked on the ROUNDED
// value because that is what the Integer wire actually carries.
const validateCost = (raw: string): string | undefined =>
  validateWholeNumber(raw, COST, CULVERT_MESSAGES.costRange, CULVERT_MESSAGES.costInvalid)

/**
 * The optional length in metres. Unparseable text reports the RANGE message rather than a converter
 * message (the Schedule 7A precedent): the backend deliberately left `length` off its field-name
 * converter overrides, because Schedule 7A's BridgeRequest also has a `length` and changing a shipped
 * schedule's message was out of scope — so the type fallback there answers with volume wording, which
 * would be worse text than the range line for a reporter who mistyped a length.
 */
const validateLength = (raw: string): string | undefined => {
  if (raw.trim() === '') {
    return undefined
  }
  const value = parseDecimalInput(raw)
  if (value === null || value < LENGTH_M.min || value > LENGTH_M.max) {
    return CULVERT_MESSAGES.lengthRange
  }
  return undefined
}

/**
 * Advisory validation for one culvert's entered values.
 *
 * `rowCounter` marks an INLINE-EDIT row and composes the `Id: {rowCounter} - ` display prefix onto
 * the two cost messages. Legacy carried that prefix through `validatorMessage`/`converterMessage` on
 * the list-row cost fields only, never on the Add form (`schedule7B.xhtml:434-435,453-454` vs
 * `:177-178,187-188`) — a JSF artifact of rendering N rows in one form, which the page reproduces
 * because a page-level Save covers every row at once and the reporter needs to know WHICH row's cost
 * was rejected. The backend returns the message unprefixed (Story 13.2 recorded deviation S23).
 */
export function validateCulvert(form: CulvertFormValues, rowCounter?: number): CulvertErrors {
  const errors: CulvertErrors = {}

  if (form.culvertTypeCode.trim() === '') {
    errors.culvertTypeCode = CULVERT_MESSAGES.valueRequired
  }

  const spanSize = validateWholeNumber(
    form.spanSize,
    SIZE_MM,
    CULVERT_MESSAGES.spanRange,
    CULVERT_MESSAGES.spanInvalid,
  )
  if (spanSize) {
    errors.spanSize = spanSize
  }

  const riseSize = validateWholeNumber(
    form.riseSize,
    SIZE_MM,
    CULVERT_MESSAGES.riseRange,
    CULVERT_MESSAGES.riseInvalid,
  )
  if (riseSize) {
    errors.riseSize = riseSize
  }

  const length = validateLength(form.length)
  if (length) {
    errors.length = length
  }

  // The one required numeric field: blank is a rejection here, unlike every other measurement.
  if (form.culvertPieceCount.trim() === '') {
    errors.culvertPieceCount = CULVERT_MESSAGES.valueRequired
  } else {
    const pieceCount = validateWholeNumber(
      form.culvertPieceCount,
      PIECE_COUNT,
      CULVERT_MESSAGES.pieceCountRange,
      CULVERT_MESSAGES.pieceCountInvalid,
    )
    if (pieceCount) {
      errors.culvertPieceCount = pieceCount
    }
  }

  for (const field of COST_FIELDS) {
    const cost = validateCost(form[field])
    if (cost) {
      errors[field] = rowCounter === undefined ? cost : `Id: ${String(rowCounter)} - ${cost}`
    }
  }

  // Measured on the trimmed value, because that is what the request body sends. BOTH server bounds
  // are mirrored: the DTO carries `@Size(max = 3500)` AND `@MaxByteLength(value = 4000, charMax =
  // 3500)`, because the column is VARCHAR2(4000 BYTE). Checking characters alone let ~3,000
  // characters of accented or CJK text through this gate and 400 on the byte rule. The two server
  // constraints share one message key, so a single message covers both here too.
  const comments = form.comments.trim()
  if (comments.length > COMMENTS_MAX || utf8Length(comments) > COMMENTS_MAX_BYTES) {
    errors.comments = CULVERT_MESSAGES.commentsMaxLength
  }

  return errors
}

/**
 * The `Total costs($)` PREVIEW: material + install as currently entered, or null when neither carries
 * a value. Display only — it is never sent (`CulvertRequest` has no `totalCost` member) and the served
 * figure replaces it on every echo, so the server stays the sole authority (AD-5/BR-05).
 *
 * Legacy showed this live. Each cost input re-rendered the disabled Total on `change` — add form
 * `schedule7B.xhtml:180,190`, rows `:440,460` — driven by `CulvertReportType.getTotalCost()`
 * (`:238-242`), with no save involved. Rendering only the SERVED total left the add form's Total
 * permanently blank and a row's Total showing the last-saved figure while the reporter typed.
 *
 * Deliberately mirrors the server's own null rule (`Schedule7bService.totalCost`): null only when BOTH
 * costs are absent, so an entered 0 still totals 0 and "no costs" still renders blank rather than `0`.
 * Each operand is rounded first, because whole dollars are what the Integer wire carries. A non-blank
 * but unparseable cost counts as absent here — the field's own advisory message is what reports it,
 * and legacy likewise did not update its Total when the converter rejected the entry.
 */
export const previewTotalCost = (form: CulvertFormValues): number | null => {
  const material = roundCost(parseDecimalInput(form.materialCost))
  const install = roundCost(parseDecimalInput(form.installCost))
  if (material === null && install === null) {
    return null
  }
  return (material ?? 0) + (install ?? 0)
}

export const COMMENTS_MAX_LENGTH = COMMENTS_MAX
