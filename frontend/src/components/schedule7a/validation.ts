// Advisory client-side validation for one Schedule 7A bridge. The BACKEND is authoritative; these
// checks give immediate inline feedback and gate the call to avoid a doomed round-trip. Ranges and
// messages MIRROR the backend BridgeRequest DTO and message bundle, so an advisory message reads
// byte-identically to a server rejection — which still renders verbatim on a 400 (AD-6/AD-8), never
// replaced by anything here.

import { parseDecimalInput, roundCost } from '@/utils/number'
import { utf8Length } from '@/utils/forms'

// Re-exported so this module stays the single validation surface the page imports from.
export { parseDecimalInput, roundCost }

const LOCATION_MAX = 30
const COMMENTS_MAX = 3500
// Both columns are BYTE-declared in delivery (LOCATION_NAME VARCHAR2(30 BYTE), COMMENTS
// VARCHAR2(4000 BYTE)), and BridgeRequest carries a @MaxByteLength for each alongside its @Size. The
// character caps alone let multi-byte text past this gate and into a 400 — and for the location that
// is the sharper case, because BridgeFields sets maxLength={LOCATION_MAX_LENGTH}: 15 CJK characters
// sit inside every limit the UI shows and still exceed 30 bytes.
const LOCATION_MAX_BYTES = 30
const COMMENTS_MAX_BYTES = 4000

const LIFE_SPAN = { min: 0, max: 999 }
// Height, length and width share one shape: NUMBER(5,1) in the delivery schema, so one decimal place.
const METRES = { min: 0, max: 9999.9, maxFractionDigits: 1 }
// The enforced bound is 0-9,999 while the message text says 0.0-999.99. That disagreement is real and
// deliberate: the legacy validator and its message string never matched, the backend ports both
// exactly as found, and the discrepancy is an open question for the Ministry. Reproducing the
// mismatch here keeps the advisory gate and the server byte-identical. Do not "fix" either half.
const DISTANCE = { min: 0, max: 9999 }
const COST = { min: -99_999_999, max: 99_999_999 }

// Verbatim from the backend bundle so an advisory message is indistinguishable from the server's.
export const BRIDGE_MESSAGES = {
  valueRequired: 'Value Required',
  locationMaxLength: 'Name / Location of Bridge must be 30 characters or fewer.',
  dateFormat: 'The date is not valid. Enter date in format: YYYY-MM.',
  lifeSpanRange: 'Entered bridge expected life must be between 0 and 999',
  abutmentHeightRange: 'Entered abutments height must be between 0.0 and 9,999.9',
  lengthRange: 'Entered bridge length must be between 0.0 and 9,999.9',
  widthRange: 'Entered bridge width must be between 0.0 and 9,999.9',
  distanceRange: 'Entered bridge distance must be between 0.0 and 999.99',
  costRange: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
} as const

// The ten cost keys in the order the legacy grid presents them, exported so the page binds inputs and
// builds the request body from ONE list instead of ten repeated literals.
export const COST_FIELDS = [
  'sitePlanCost',
  'superstructureMaterialCost',
  'superstructureDeliverCost',
  'superstructureInstallCost',
  'abutmentMaterialCost',
  'abutmentDeliverCost',
  'abutmentInstallCost',
  'approachCost',
  'afterInstallCost',
  'otherCost',
] as const

export type CostField = (typeof COST_FIELDS)[number]

// The five code keys paired with the codeLists member each one draws its options from.
export const CODE_FIELDS = [
  'constructionTypeCode',
  'superstructureTypeCode',
  'deckTypeCode',
  'abutmentTypeCode',
  'loadRatingCode',
] as const

export type CodeField = (typeof CODE_FIELDS)[number]

// Every value is held as the raw string the user typed or picked ('' = blank/unselected), so
// "required but not selected" stays expressible and a typo survives on screen for correction.
export type BridgeFormValues = {
  locationName: string
  builtDate: string
  lifeSpan: string
  abutmentHeight: string
  length: string
  width: string
  distance: string
  comments: string
} & Record<CodeField, string> &
  Record<CostField, string>

export type BridgeErrors = Partial<Record<keyof BridgeFormValues, string>>

export const emptyBridgeForm = (): BridgeFormValues => ({
  locationName: '',
  builtDate: '',
  constructionTypeCode: '',
  superstructureTypeCode: '',
  deckTypeCode: '',
  abutmentTypeCode: '',
  loadRatingCode: '',
  lifeSpan: '',
  abutmentHeight: '',
  length: '',
  width: '',
  distance: '',
  sitePlanCost: '',
  superstructureMaterialCost: '',
  superstructureDeliverCost: '',
  superstructureInstallCost: '',
  abutmentMaterialCost: '',
  abutmentDeliverCost: '',
  abutmentInstallCost: '',
  approachCost: '',
  afterInstallCost: '',
  otherCost: '',
  comments: '',
})

// Grouping only ever appears in the integer part, so the post-'.' slice is the fraction as typed.
const fractionDigits = (raw: string): number => (raw.includes('.') ? raw.split('.')[1].length : 0)

const BUILT_DATE = /^\d{4}-(0[1-9]|1[0-2])$/

// Non-lenient yyyy-MM: four-digit year, two-digit month 01-12, nothing else. A trailing day
// component, a single-digit month, or a reversed order are all rejected — matching the legacy
// SimpleDateFormat with setLenient(false).
const validateBuiltDate = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return BRIDGE_MESSAGES.valueRequired
  }
  return BUILT_DATE.test(trimmed) ? undefined : BRIDGE_MESSAGES.dateFormat
}

// A required whole number in [min, max]. A non-numeric entry reports the same range message rather
// than a converter message: legacy emitted a bare resource key here, which is not usable text.
const validateInteger = (
  raw: string,
  bounds: { min: number; max: number },
  rangeMessage: string,
): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return BRIDGE_MESSAGES.valueRequired
  }
  const value = parseDecimalInput(trimmed)
  if (value === null || !Number.isInteger(value) || value < bounds.min || value > bounds.max) {
    return rangeMessage
  }
  return undefined
}

// A required one-decimal measurement in metres.
const validateMetres = (raw: string, rangeMessage: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return BRIDGE_MESSAGES.valueRequired
  }
  const value = parseDecimalInput(trimmed)
  if (
    value === null ||
    value < METRES.min ||
    value > METRES.max ||
    fractionDigits(trimmed) > METRES.maxFractionDigits
  ) {
    return rangeMessage
  }
  return undefined
}

// Costs are optional at save — only Check Status flags a missing one (BR-08).
const validateCost = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = parseDecimalInput(trimmed)
  if (value === null) {
    return BRIDGE_MESSAGES.costInvalid
  }
  // Checked on the ROUNDED value because that is what the Integer wire actually carries: a raw
  // 99,999,999.5 would otherwise pass this gate and be rejected by the server at 100,000,000.
  const rounded = roundCost(value) as number
  if (rounded < COST.min || rounded > COST.max) {
    return BRIDGE_MESSAGES.costRange
  }
  return undefined
}

/** Advisory validation for one bridge's entered values. */
export function validateBridge(form: BridgeFormValues): BridgeErrors {
  const errors: BridgeErrors = {}

  const locationName = form.locationName.trim()
  if (locationName === '') {
    errors.locationName = BRIDGE_MESSAGES.valueRequired
  } else if (locationName.length > LOCATION_MAX || utf8Length(locationName) > LOCATION_MAX_BYTES) {
    errors.locationName = BRIDGE_MESSAGES.locationMaxLength
  }

  const builtDate = validateBuiltDate(form.builtDate)
  if (builtDate) {
    errors.builtDate = builtDate
  }

  for (const field of CODE_FIELDS) {
    if (form[field].trim() === '') {
      errors[field] = BRIDGE_MESSAGES.valueRequired
    }
  }

  const lifeSpan = validateInteger(form.lifeSpan, LIFE_SPAN, BRIDGE_MESSAGES.lifeSpanRange)
  if (lifeSpan) {
    errors.lifeSpan = lifeSpan
  }
  const distance = validateInteger(form.distance, DISTANCE, BRIDGE_MESSAGES.distanceRange)
  if (distance) {
    errors.distance = distance
  }

  const abutmentHeight = validateMetres(form.abutmentHeight, BRIDGE_MESSAGES.abutmentHeightRange)
  if (abutmentHeight) {
    errors.abutmentHeight = abutmentHeight
  }
  const length = validateMetres(form.length, BRIDGE_MESSAGES.lengthRange)
  if (length) {
    errors.length = length
  }
  const width = validateMetres(form.width, BRIDGE_MESSAGES.widthRange)
  if (width) {
    errors.width = width
  }

  for (const field of COST_FIELDS) {
    const cost = validateCost(form[field])
    if (cost) {
      errors[field] = cost
    }
  }

  // Measured on the trimmed value, because that is what buildBody sends. Both server bounds are
  // mirrored — @Size(max = 3500) AND @MaxByteLength(4000, charMax = 3500) — and they share one
  // message key, so one message covers both here too.
  const comments = form.comments.trim()
  if (comments.length > COMMENTS_MAX || utf8Length(comments) > COMMENTS_MAX_BYTES) {
    errors.comments = BRIDGE_MESSAGES.commentsMaxLength
  }

  return errors
}

export const LOCATION_MAX_LENGTH = LOCATION_MAX
export const COMMENTS_MAX_LENGTH = COMMENTS_MAX
