// Advisory client-side validation for a Schedule 6 (Road Management Costs) road record. The BACKEND
// is authoritative (Story 8.2); these checks give immediate inline feedback and gate the call to
// avoid a doomed round-trip. Ranges + messages MIRROR the backend RoadRecordRequest DTO and message
// bundle, so an advisory message reads identically to a server rejection — which still renders
// verbatim on a 400 (AD-8/AD-6), never replaced by anything here.

// Column-fidelity caps, exported so index.tsx binds the SAME numbers to maxLength/maxCount that this
// module validates against. The two comment caps are DIFFERENT columns, not a typo: the per-record
// comment lands in ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE) while the schedule-level
// general comment lands in the 4000-wide ROAD_MAINTENANCE_REPORT.COMMENTS. Legacy set maxlength=3500
// on BOTH textareas (schedule6.xhtml:180,410,496), so legacy itself failed at the 400-wide column;
// the Story 8.2 code review corrected the server and the UI matches the server, not legacy.
const AREA_TYPE_MAX = 3
const TSA_CODE_MAX = 2
const TFL_MAX = 2
const SUPPLY_BLOCK_MAX = 3
const RECORD_COMMENTS_MAX = 400
const GENERAL_COMMENTS_MAX = 3500

const VOLUME = { min: 0, max: 9_999_999, maxFractionDigits: 2 }
const COST = { min: -99_999_999, max: 99_999_999 }

/** The literal area-type value that selects the Tree Farm Licence branch (BR-02). */
export const TFL_AREA_TYPE = 'TFL'

// Verbatim from the backend bundle (messages.properties) so an advisory message is byte-identical to
// the server's rejection for the same field.
export const ROAD_MESSAGES = {
  areaTypeRequired: 'TSA or TFL: Value is required.',
  invalidCodeValue: 'A valid value must be selected from the list.',
  tflNumberInvalid: 'Entered TFL number is not valid for Interior Regions.',
  volumeRange: 'Entered volume must be between 0 and 9,999,999.',
  volumeInvalid: 'Entered volume entry is invalid.',
  costRange: 'Entered cost must be between -99,999,999 and 99,999,999.',
  costInvalid: 'Entered cost is invalid.',
  recordCommentsMaxLength: 'Comments must be 400 characters or fewer.',
  generalCommentsMaxLength: 'Comments must be 3500 characters or fewer.',
} as const

export interface RoadRecordErrors {
  areaType?: string
  tflNumber?: string
  supplyBlock?: string
  volume?: string
  cost?: string
  comments?: string
}

// The raw form values gathered by the Add panel / row editor before submission. Every field is a
// string: the numeric ones are parsed with the legacy DecimalFormat semantics below, and the area
// type is the raw code (deviation (A): text input over the code, no codes endpoint exists).
export interface RoadRecordFormValues {
  areaType: string
  tflNumber: string
  supplyBlock: string
  volume: string
  cost: string
  comments: string
}

// Legacy JSF numeric fields (volume, cost) bind through a US-locale DecimalFormat converter
// (ILCRVolumeConverter/ILCRCostConverter :36). This parser accepts that format — an optional leading
// '-', digits with optional comma grouping, and an optional '.' fractional part (the same format the
// page DISPLAYS) — but is deliberately STRICTER than legacy: DecimalFormat.parse has no
// consumed-length check, so legacy also accepted junk-suffixed/mis-grouped input by silently
// mangling it ('12,34' -> 1234, '1e2' -> 1, '1000abc' -> 1000); this gate rejects those outright
// (recorded deviation (L), Story 8.3). Native Number() diverges the other way and must NOT be used:
// it rejects grouped input legacy accepts (`Number('1,000')` -> NaN) and accepts JS-only forms
// legacy never allowed as full values (`1e2` -> 100, `0x10` -> 16, `Infinity`). Returns the numeric
// value, or null when the string is blank or not a valid decimal in this strict format.
const DECIMAL_INPUT = /^-?(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$/
export const parseDecimalInput = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '' || !DECIMAL_INPUT.test(trimmed)) {
    return null
  }
  return Number(trimmed.replace(/,/g, ''))
}

// Whole-dollar costs: legacy accepted fractional input (ILCRCostConverter BigDecimal parse) and
// Oracle rounded it on insert, while the modern Integer wire would silently TRUNCATE at
// deserialization. Round half-away-from-zero (Oracle's rounding) before send so the stored value
// matches legacy; the backend independently rejects any fractional cost.
export const roundCost = (value: number | null): number | null =>
  value === null ? null : Math.sign(value) * Math.round(Math.abs(value))

const validateAreaType = (raw: string): string | undefined => {
  if (raw === '') {
    return ROAD_MESSAGES.areaTypeRequired
  }
  if (raw.length > AREA_TYPE_MAX) {
    return ROAD_MESSAGES.invalidCodeValue
  }
  // Only the literal "TFL" may occupy 3 characters; a TSA code lands in TSA_NUMBER VARCHAR2(2), and
  // the service enforces that narrower cap on the TSA branch (an over-wide code is ORA-12899 -> 500).
  if (raw !== TFL_AREA_TYPE && raw.length > TSA_CODE_MAX) {
    return ROAD_MESSAGES.invalidCodeValue
  }
  return undefined
}

const validateVolume = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = parseDecimalInput(trimmed)
  if (value === null) {
    return ROAD_MESSAGES.volumeInvalid
  }
  // Fractional digits counted from the raw string (grouping lives only in the integer part, so the
  // post-'.' slice is unaffected): the delivery column is VOLUME NUMBER(10,2), and >2 decimals trips
  // the backend's @Digits, which resolves the SAME key as the range bound.
  const decimals = trimmed.includes('.') ? trimmed.split('.')[1].length : 0
  if (value < VOLUME.min || value > VOLUME.max || decimals > VOLUME.maxFractionDigits) {
    return ROAD_MESSAGES.volumeRange
  }
  return undefined
}

const validateCost = (raw: string): string | undefined => {
  const trimmed = raw.trim()
  if (trimmed === '') {
    return undefined
  }
  const value = parseDecimalInput(trimmed)
  if (value === null) {
    return ROAD_MESSAGES.costInvalid
  }
  // Range-checked on the ROUNDED value because that is what the Integer wire actually carries — a
  // raw 99,999,999.5 would otherwise pass the gate and be rejected by the server at 100,000,000.
  const rounded = roundCost(value) as number
  if (rounded < COST.min || rounded > COST.max) {
    return ROAD_MESSAGES.costRange
  }
  return undefined
}

/** Advisory validation for one road record's entered values. */
export function validateRoadRecord(form: RoadRecordFormValues): RoadRecordErrors {
  const errors: RoadRecordErrors = {}

  const areaType = form.areaType.trim()
  const areaTypeError = validateAreaType(areaType)
  if (areaTypeError) {
    errors.areaType = areaTypeError
  }

  // Required-and-validated iff the TFL branch (BR-03); on the TSA branch the counterpart is cleared,
  // not validated. "Valid" ultimately means "resolves to an RMG", which only the server can decide —
  // this gate only catches blank and over-wide entries, with the same verbatim text either way.
  const tflNumber = form.tflNumber.trim()
  if (areaType === TFL_AREA_TYPE) {
    if (tflNumber === '' || tflNumber.length > TFL_MAX) {
      errors.tflNumber = ROAD_MESSAGES.tflNumberInvalid
    }
  }

  // A missing Supply Block is a Check Status finding (S09), never a save failure — only its width is
  // enforced here (TSB_NUMBER_CODE VARCHAR2(3)).
  if (form.supplyBlock.trim().length > SUPPLY_BLOCK_MAX) {
    errors.supplyBlock = ROAD_MESSAGES.invalidCodeValue
  }

  const volumeError = validateVolume(form.volume)
  if (volumeError) {
    errors.volume = volumeError
  }
  const costError = validateCost(form.cost)
  if (costError) {
    errors.cost = costError
  }

  if (form.comments.length > RECORD_COMMENTS_MAX) {
    errors.comments = ROAD_MESSAGES.recordCommentsMaxLength
  }

  return errors
}

/** Advisory validation for the schedule-level General Comment (blank is valid — blank clears). */
export const validateGeneralComments = (raw: string): string | undefined =>
  raw.length > GENERAL_COMMENTS_MAX ? ROAD_MESSAGES.generalCommentsMaxLength : undefined

export const AREA_TYPE_MAX_LENGTH = AREA_TYPE_MAX
export const TFL_MAX_LENGTH = TFL_MAX
export const SUPPLY_BLOCK_MAX_LENGTH = SUPPLY_BLOCK_MAX
export const RECORD_COMMENTS_MAX_LENGTH = RECORD_COMMENTS_MAX
export const GENERAL_COMMENTS_MAX_LENGTH = GENERAL_COMMENTS_MAX
