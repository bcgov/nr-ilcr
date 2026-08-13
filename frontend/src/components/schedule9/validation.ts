// Advisory client-side validation for one Schedule 9 contractual-work record. The BACKEND is
// authoritative (FLD-001..005, the Draft gate, the per-record optimistic lock); these checks give
// immediate inline feedback and gate the call to avoid a doomed round-trip. Ranges and messages
// MIRROR the ContractualWorkRecordRequest DTO and the message bundle, so an advisory message reads
// like a server rejection — which still renders verbatim on a 400 (AD-6/AD-8), never replaced here.
//
// Required at Save (legacy `required="true"`): Company ID, Contractual Item, Unit Type, Biogeoclimatic
// Zone, Source. Number of Units, Cost and Side Slope are OPTIONAL at Save; the three "Other"
// descriptions are NOT required either — legacy leaves them un-required (the shipped 9.2 parity fix),
// and Check Status flags blank units/cost. Do NOT tighten the descriptions to required.

import type ContractualWorkRecordRequest from '@/interfaces/Schedule9Request'
import type { ContractualWorkRecord } from '@/interfaces/Schedule9Response'
import { utf8Length } from '@/utils/forms'
import { numStrFixed, parseDecimalInput, roundCost } from '@/utils/number'

export { numStrFixed, parseDecimalInput, roundCost }

const COMMENTS_MAX = 2000
const COST = { min: 0, max: 9_999_999 }
const SIDE_SLOPE = { min: 0, max: 100 }
const UNITS = { min: 0, max: 99_999.9 }
const ITEM_DESC_MAX = 30
const OTHER_DESC_MAX = 120

// The driving code values that enable the four conditional fields (BR-04, from Schedule9DO).
export const ITEM_OTHER = '114'
export const ROAD_ITEMS = ['111', '112'] as const
export const UNIT_OTHER = 'O'
export const SOURCE_DESC_CODES = ['O', 'S'] as const

export const sideSlopeEnabled = (itemCode: string): boolean =>
  (ROAD_ITEMS as readonly string[]).includes(itemCode)
export const itemDescriptionEnabled = (itemCode: string): boolean => itemCode === ITEM_OTHER
export const unitDescriptionEnabled = (unitCode: string): boolean => unitCode === UNIT_OTHER
export const sourceDescriptionEnabled = (sourceCode: string): boolean =>
  (SOURCE_DESC_CODES as readonly string[]).includes(sourceCode)

// Verbatim from the backend bundle so an advisory message is indistinguishable from the server's.
export const RECORD_MESSAGES = {
  valueRequired: 'Value Required',
  costRange: 'Entered cost must be between 0 and 9,999,999.',
  sideSlopeRange: 'Side slope (%): percentage must be between 0 and 100.',
  unitsRange: 'Entered number of units must be between 0.0 and 99,999.9.',
  itemDescriptionMaxLength: 'Item Other Description must be 30 characters or fewer.',
  unitDescriptionMaxLength: 'Unit Other Description must be 120 characters or fewer.',
  sourceDescriptionMaxLength: 'Source Other Description must be 120 characters or fewer.',
  commentsMaxLength: 'Comments must be 2000 characters or fewer.',
} as const

/**
 * The legacy display mask on each numeric input, as its decimal count. Cost and Side Slope are whole
 * numbers; Number of Units is one decimal ({@code PERFORMED_UNIT NUMBER(6,1)}) — a stored {@code
 * 12.5} must read {@code 12.5} and a stored {@code 100} must read {@code 100.0}, which is the visible
 * consequence of getting the units mask wrong.
 */
export const MASK_DIGITS = {
  numberOfUnits: 1,
  cost: 0,
  sideSlopePct: 0,
} as const

export type MaskedField = keyof typeof MASK_DIGITS

// Every value is held as the raw string the user typed or picked ('' = blank/unselected).
export type RecordFormValues = {
  contractorId: string
  contractualItemCode: string
  itemDescription: string
  sideSlopePct: string
  numberOfUnits: string
  unitCode: string
  unitDescription: string
  biogeoclimaticZone: string
  cost: string
  sourceCode: string
  sourceDescription: string
  comments: string
}

export type RecordErrors = Partial<Record<keyof RecordFormValues, string>>

export const emptyRecordForm = (): RecordFormValues => ({
  contractorId: '',
  contractualItemCode: '',
  itemDescription: '',
  sideSlopePct: '',
  numberOfUnits: '',
  unitCode: '',
  unitDescription: '',
  biogeoclimaticZone: '',
  cost: '',
  sourceCode: '',
  sourceDescription: '',
  comments: '',
})

// Seed an editor from a stored record. `{code, description}` selects become their code; nulls seed
// blank so "not entered" stays distinguishable from a value. Numeric fields seed THROUGH their mask.
export const formFromRecord = (record: ContractualWorkRecord): RecordFormValues => ({
  contractorId: record.contractorId ?? '',
  contractualItemCode: record.contractualItem?.code ?? '',
  itemDescription: record.itemDescription ?? '',
  sideSlopePct: numStrFixed(record.sideSlopePct, MASK_DIGITS.sideSlopePct),
  numberOfUnits: numStrFixed(record.numberOfUnits, MASK_DIGITS.numberOfUnits),
  unitCode: record.unitType?.code ?? '',
  unitDescription: record.unitDescription ?? '',
  biogeoclimaticZone: record.biogeoclimaticZone?.code ?? '',
  cost: numStrFixed(record.cost, MASK_DIGITS.cost),
  sourceCode: record.source?.code ?? '',
  sourceDescription: record.sourceDescription ?? '',
  comments: record.comments ?? '',
})

/** An OPTIONAL whole number in [min, max]; blank passes. Checked on the parsed AND rounded value. */
const validateWholeNumber = (
  raw: string,
  bounds: { min: number; max: number },
  rangeMessage: string,
): string | undefined => {
  if (raw.trim() === '') {
    return undefined
  }
  const value = parseDecimalInput(raw)
  if (value === null || value < bounds.min || value > bounds.max) {
    return rangeMessage
  }
  const rounded = roundCost(value) as number
  return rounded < bounds.min || rounded > bounds.max ? rangeMessage : undefined
}

/** Round to one decimal (the PERFORMED_UNIT NUMBER(6,1) scale), or null. */
const roundUnits = (value: number | null): number | null =>
  value === null ? null : Math.round(value * 10) / 10

/**
 * The optional Number of Units (one decimal); blank passes. Range is checked on the value as parsed
 * AND as rounded to scale 1 (the stored scale), mirroring `validateWholeNumber` for cost — a value
 * near the boundary must not pass advisory validation only to round out of range on the wire.
 */
const validateUnits = (raw: string): string | undefined => {
  if (raw.trim() === '') {
    return undefined
  }
  const value = parseDecimalInput(raw)
  if (value === null || value < UNITS.min || value > UNITS.max) {
    return RECORD_MESSAGES.unitsRange
  }
  const rounded = roundUnits(value) as number
  return rounded < UNITS.min || rounded > UNITS.max ? RECORD_MESSAGES.unitsRange : undefined
}

/**
 * Advisory validation for one record's entered values. Required = the five selects. Number of Units,
 * Cost and Side Slope are optional ranges; the "Other" descriptions are optional but length-capped.
 * Side Slope is only range-checked when the item enables it (111/112) — otherwise its value is
 * cleared and not sent.
 */
export function validateRecord(form: RecordFormValues): RecordErrors {
  const errors: RecordErrors = {}

  if (form.contractorId.trim() === '') {
    errors.contractorId = RECORD_MESSAGES.valueRequired
  }
  if (form.contractualItemCode.trim() === '') {
    errors.contractualItemCode = RECORD_MESSAGES.valueRequired
  }
  if (form.unitCode.trim() === '') {
    errors.unitCode = RECORD_MESSAGES.valueRequired
  }
  if (form.biogeoclimaticZone.trim() === '') {
    errors.biogeoclimaticZone = RECORD_MESSAGES.valueRequired
  }
  if (form.sourceCode.trim() === '') {
    errors.sourceCode = RECORD_MESSAGES.valueRequired
  }

  const units = validateUnits(form.numberOfUnits)
  if (units) {
    errors.numberOfUnits = units
  }
  const cost = validateWholeNumber(form.cost, COST, RECORD_MESSAGES.costRange)
  if (cost) {
    errors.cost = cost
  }
  if (sideSlopeEnabled(form.contractualItemCode)) {
    const sideSlope = validateWholeNumber(
      form.sideSlopePct,
      SIDE_SLOPE,
      RECORD_MESSAGES.sideSlopeRange,
    )
    if (sideSlope) {
      errors.sideSlopePct = sideSlope
    }
  }

  // "Other" descriptions: optional, but capped to the delivery column widths when entered.
  if (
    itemDescriptionEnabled(form.contractualItemCode) &&
    form.itemDescription.trim().length > ITEM_DESC_MAX
  ) {
    errors.itemDescription = RECORD_MESSAGES.itemDescriptionMaxLength
  }
  if (
    unitDescriptionEnabled(form.unitCode) &&
    form.unitDescription.trim().length > OTHER_DESC_MAX
  ) {
    errors.unitDescription = RECORD_MESSAGES.unitDescriptionMaxLength
  }
  if (
    sourceDescriptionEnabled(form.sourceCode) &&
    form.sourceDescription.trim().length > OTHER_DESC_MAX
  ) {
    errors.sourceDescription = RECORD_MESSAGES.sourceDescriptionMaxLength
  }

  const comments = form.comments.trim()
  if (comments.length > COMMENTS_MAX || utf8Length(comments) > COMMENTS_MAX) {
    errors.comments = RECORD_MESSAGES.commentsMaxLength
  }

  return errors
}

/**
 * Build the write body from the form. The four conditional dependents are sent as the server would
 * store them — nulled when their driver is not the enabling value (mirroring the backend's
 * conditional-null, so a stray value in a disabled field never reaches the wire). Optional blanks send
 * `null` (clearing in place). `contractualItemCode` is a required select, so it parses to a number.
 */
export const buildBody = (
  form: RecordFormValues,
  revisionCount?: number,
): ContractualWorkRecordRequest => {
  const itemCode = form.contractualItemCode
  const trimOrNull = (raw: string): string | null => (raw.trim() === '' ? null : raw.trim())

  return {
    contractorId: form.contractorId.trim(),
    contractualItemCode: Number(itemCode),
    itemDescription: itemDescriptionEnabled(itemCode) ? trimOrNull(form.itemDescription) : null,
    unitCode: form.unitCode,
    unitDescription: unitDescriptionEnabled(form.unitCode)
      ? trimOrNull(form.unitDescription)
      : null,
    // Rounded to scale 1 to match the PERFORMED_UNIT NUMBER(6,1) column and the display mask, so a
    // 2-decimal entry saved without a blur cannot reach the wire (cost rounds the same way).
    numberOfUnits: roundUnits(parseDecimalInput(form.numberOfUnits)),
    biogeoclimaticZone: form.biogeoclimaticZone,
    cost: roundCost(parseDecimalInput(form.cost)),
    sideSlopePct: sideSlopeEnabled(itemCode)
      ? roundCost(parseDecimalInput(form.sideSlopePct))
      : null,
    sourceCode: form.sourceCode,
    sourceDescription: sourceDescriptionEnabled(form.sourceCode)
      ? trimOrNull(form.sourceDescription)
      : null,
    comments: trimOrNull(form.comments),
    ...(revisionCount === undefined ? {} : { revisionCount }),
  }
}

/**
 * The read-only $/Unit PREVIEW: cost ÷ units at scale 2, or null when units are 0/blank or cost is
 * blank — mirroring the server's `costPerUnit` null rule (S14). Display only; never sent, and the
 * served figure replaces it on every echo (AD-5). Legacy recomputed this live as cost/units changed.
 */
export const previewCostPerUnit = (form: RecordFormValues): number | null => {
  const cost = roundCost(parseDecimalInput(form.cost))
  const units = parseDecimalInput(form.numberOfUnits)
  if (cost === null || units === null || units <= 0) {
    return null
  }
  return Math.round((cost / units) * 100) / 100
}

export const COMMENTS_MAX_LENGTH = COMMENTS_MAX
