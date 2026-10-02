// Form shapes + advisory client-side validation for the three Schedule 8 levels (page / sample /
// rate). The BACKEND is authoritative (Stories 14.2–14.4); these checks give immediate inline
// feedback and gate the write to avoid a doomed round-trip. Ranges mirror the write DTOs. The
// skidding 100% asymmetry is preserved here exactly: a sum > 100 blocks Save; a sum ≠ 100 is NOT
// blocked at Save (it is flagged only at Check Status — 14.6).

import type { Page, Sample } from '@/interfaces/Schedule8Response'
import type { BannerEntry } from '@/utils/legacyValidationBanner'
import { legacyRequiredMessage } from '@/utils/legacyValidationBanner'

// ---- Shared helpers ------------------------------------------------------------------------------

export const isBlank = (raw: string | undefined): boolean => raw === undefined || raw.trim() === ''

// Grouping commas are dropped: legacy's number masks group thousands (`#,##0`, `###,##0.0`, ...,
// `messages.properties:200-218`), so `1,200` was a number there.
export const toNum = (raw: string): number | null => {
  const trimmed = raw.trim().replaceAll(',', '')
  if (trimmed === '') return null
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

export const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

export const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

/**
 * True when `raw` reads as a decimal number, grouping commas allowed (legacy's masks group thousands,
 * and its parse did not police where a comma fell). Stricter than `Number()`, which would also take
 * `1e3`, `0x10` or `Infinity` — none of which legacy's `f:convertNumber` accepted.
 */
export const isNumberText = (raw: string): boolean =>
  /^[+-]?(\d+(,\d+)*(\.\d*)?|\.\d+)$/.test(raw.trim())

/**
 * JSF `NumberConverter.PATTERN` as legacy's bundle words it (`common/validation.properties:64`,
 * `{2}: ''{0}'' is not a number pattern.`): `{2}` is the input's `label`, `{0}` the submitted text.
 * Every legacy Schedule 8 number box carried an `f:convertNumber pattern`, so typed text that is not
 * a number failed conversion with this line.
 */
export const numberPatternMessage = (label: string, raw: string): string =>
  `${label}: '${raw.trim()}' is not a number pattern.`

const rangeError = (
  raw: string,
  range: { min: number; max: number },
  message: string,
): string | undefined => {
  if (isBlank(raw)) return undefined
  const value = isNumberText(raw) ? toNum(raw) : null
  if (value === null || value < range.min || value > range.max) return message
  return undefined
}

// Advisory messages. Verbatim where the legacy literal is known; otherwise a faithful paraphrase of
// the bundle key's intent (the API returns the authoritative verbatim text on the server round-trip).
export const MESSAGES = {
  required: 'Value Required',
  percentage: 'Entered percentage must be between 0 and 100.',
  percentOver: 'Skidding/Yarding percentages can not total more than 100%.',
  volume: 'Entered volume must be between 0 and 9,999,999.',
  originalRate: 'Entered rate must be between 0 and 999,999.99.',
  costingRate: 'Entered rate must be between 0 and 9,999,999.99.',
  descriptionMax: 'Description can not exceed 30 characters.',
  phone: 'Phone must be a complete 10-digit number (e.g. 250-555-1212).',
  // Legacy's `f:validateDoubleRange` limits on the Skyline and Helicopter boxes
  // (`schedule8EditDetail.xhtml:165,191,217,274,299`), worded as the other ranges here.
  slopeDistance: 'Entered distance must be between 0 and 99,999.',
  supportNumber: 'Entered number must be between 0 and 9,999.',
  supportAvgDistance: 'Entered distance must be between 0 and 99,999.9.',
  distance: 'Entered distance must be between 0 and 999,999.9.',
  cycleTime: 'Entered time must be between 0 and 999,999.9.',
  // `tflNumberValidatorErrorMsg` and `notApplicableValidatorErrorMsg` (legacy `messages.properties`).
  tflInvalid: 'Entered TFL number is not valid for Interior Regions.',
  notApplicable: 'A valid value must be selected from the list.',
} as const

const PERCENT = { min: 0, max: 100 }
const VOLUME = { min: 0, max: 9_999_999 }
const ORIGINAL_RATE = { min: 0, max: 999_999.99 }
const COSTING_RATE = { min: 0, max: 9_999_999.99 }

// ---- Page (report-page) form ---------------------------------------------------------------------

export interface PageForm {
  license: string
  supportCentre: string
  region: string
  becZone: string
  tsaNumber: string
  tflNumber: string
  supplyBlock: string
  division: string
  contact: string
  phone: string
  cuttingPermit: string
  comments: string
}

export const emptyPageForm = (): PageForm => ({
  license: '',
  supportCentre: '',
  region: '',
  becZone: '',
  tsaNumber: '',
  tflNumber: '',
  supplyBlock: '',
  division: '',
  contact: '',
  phone: '',
  cuttingPermit: '',
  comments: '',
})

// `keepIdentity=false` (copy) clones the fields but the server treats it as a create.
export const seedPageForm = (page: Page): PageForm => ({
  license: page.license ?? '',
  supportCentre: page.supportCentre ?? '',
  region: page.region ?? '',
  becZone: page.becZone ?? '',
  tsaNumber: page.tsaNumber ?? '',
  tflNumber: page.tflNumber ?? '',
  supplyBlock: page.supplyBlock ?? '',
  division: page.division ?? '',
  contact: page.contact ?? '',
  phone: page.phone ?? '',
  cuttingPermit: page.cuttingPermit ?? '',
  comments: page.comments ?? '',
})

// TFL is selected when the TSA-or-TFL field holds the literal 'TFL' (STA-002 / BR-03). When TFL is
// active the Supply Block field is disabled/cleared, and vice-versa.
export const isTflSelected = (form: PageForm): boolean =>
  form.tsaNumber.trim().toUpperCase() === 'TFL'

/**
 * The page fields' format checks — what legacy's converter / validator rejected on the field's
 * change. The required checks are added by {@link validatePageForm}.
 */
const pageFormatErrors = (form: PageForm): Record<string, string> => {
  const errors: Record<string, string> = {}
  // Phone is optional at Save (required only at Check Status), but a partial entry is rejected — it
  // must be a complete 10-digit number. Count digits (format-agnostic) rather than matching the dashed
  // shape, so a stored value seeded without dashes (e.g. 4564564566) still validates as it displays.
  const phoneDigits = form.phone.replace(/\D/g, '')
  if (phoneDigits.length > 0 && phoneDigits.length !== 10) errors.phone = MESSAGES.phone
  // Legacy's `TflNumberValidator` (`schedule8EditReport.xhtml:235`) accepts only a TFL that resolves
  // to a Road Group. That mapping is the server's (its check is deferred with the RoadGroupUtil port),
  // so the client rejects what can never resolve: anything but 1-2 digits. A blank TFL # is not
  // validated (JSF skips validators on an empty value); Check Status reports it.
  const tfl = form.tflNumber.trim()
  if (isTflSelected(form) && tfl !== '' && !/^\d{1,2}$/.test(tfl)) {
    errors.tflNumber = MESSAGES.tflInvalid
  }
  return errors
}

/**
 * Advisory validation for the page editor: License / Support Centre / Region / BEC Zone / TSA-or-TFL
 * all required at Save (FLD-006), plus the per-field format checks. Returns a map of field → message
 * for every invalid field.
 */
export function validatePageForm(form: PageForm): Record<string, string> {
  const errors: Record<string, string> = {}
  if (isBlank(form.license)) errors.license = MESSAGES.required
  if (isBlank(form.supportCentre)) errors.supportCentre = MESSAGES.required
  if (isBlank(form.region)) errors.region = MESSAGES.required
  if (isBlank(form.becZone)) errors.becZone = MESSAGES.required
  // TSA-or-TFL context: the TSA-or-TFL field must be chosen (BR-03).
  if (isBlank(form.tsaNumber)) errors.tsaNumber = MESSAGES.required
  return { ...errors, ...pageFormatErrors(form) }
}

// ---- Sample form ---------------------------------------------------------------------------------

export interface SampleForm {
  contractId: string
  cutBlock: string
  groundBasePct: string
  grapplePct: string
  skylinePct: string
  highleadPct: string
  helicopterPct: string
  otherSkiddingPct: string
  skylineSlopeDistance: string
  skylineSupportNumber: string
  supportAvgDistance: string
  cycleTime: string
  distance: string
  uphillDirection: string // '' | 'Y' | 'N'
  waterDumpDestination: string // '' | 'Y' | 'N'
  skidTypeCode: string
  coniferousVolume: string
  deciduousVolume: string
  originalRate: string
}

export const emptySampleForm = (): SampleForm => ({
  contractId: '',
  cutBlock: '',
  groundBasePct: '',
  grapplePct: '',
  skylinePct: '',
  highleadPct: '',
  helicopterPct: '',
  otherSkiddingPct: '',
  skylineSlopeDistance: '',
  skylineSupportNumber: '',
  supportAvgDistance: '',
  cycleTime: '',
  distance: '',
  uphillDirection: '',
  waterDumpDestination: '',
  skidTypeCode: '',
  coniferousVolume: '',
  deciduousVolume: '',
  originalRate: '',
})

const boolToYn = (value: boolean | null | undefined): string =>
  value === true ? 'Y' : value === false ? 'N' : ''

export const seedSampleForm = (sample: Sample): SampleForm => ({
  contractId: sample.contractId ?? '',
  cutBlock: sample.cutBlock ?? '',
  groundBasePct: numStr(sample.groundBasePct),
  grapplePct: numStr(sample.grapplePct),
  skylinePct: numStr(sample.skylinePct),
  highleadPct: numStr(sample.highleadPct),
  helicopterPct: numStr(sample.helicopterPct),
  otherSkiddingPct: numStr(sample.otherSkiddingPct),
  skylineSlopeDistance: numStr(sample.skylineSlopeDistance),
  skylineSupportNumber: numStr(sample.skylineSupportNumber),
  supportAvgDistance: numStr(sample.supportAvgDistance),
  cycleTime: numStr(sample.cycleTime),
  distance: numStr(sample.distance),
  uphillDirection: boolToYn(sample.uphillDirection),
  waterDumpDestination: boolToYn(sample.waterDumpDestination),
  skidTypeCode: sample.skidTypeCode ?? '',
  coniferousVolume: numStr(sample.coniferousVolume),
  deciduousVolume: numStr(sample.deciduousVolume),
  originalRate: numStr(sample.originalRate),
})

const PCT_FIELDS: (keyof SampleForm)[] = [
  'groundBasePct',
  'grapplePct',
  'skylinePct',
  'highleadPct',
  'helicopterPct',
  'otherSkiddingPct',
]

// The live skidding/yarding total (sum of the six %s), for the read-only Total display.
export const skiddingTotal = (form: SampleForm): number =>
  PCT_FIELDS.reduce((total, field) => total + (toNum(form[field]) ?? 0), 0)

// Actual Harvested computed live = coniferous + deciduous (mirrors the disabled legacy field).
export const liveActualHarvested = (form: SampleForm): number | null => {
  const coniferous = toNum(form.coniferousVolume)
  const deciduous = toNum(form.deciduousVolume)
  if (coniferous === null && deciduous === null) return null
  return (coniferous ?? 0) + (deciduous ?? 0)
}

const isNonZero = (raw: string): boolean => {
  const n = toNum(raw)
  return n !== null && n !== 0
}

/**
 * The free-number sample fields that carried an `f:convertNumber` in legacy, with their legacy
 * `label` (`schedule8EditDetail.xhtml`): typed text that is not a number fails with
 * `<label>: '<text>' is not a number pattern.` The percentages are absent: they had no converter,
 * only `percentageValidator`, whose one message stands for text and range alike.
 */
const NUMBER_LABELS: readonly (readonly [keyof SampleForm, string])[] = [
  ['skylineSlopeDistance', 'Slope Distance'], // :160
  ['skylineSupportNumber', 'Support Number'], // :186
  ['supportAvgDistance', 'Support Avg Dist'], // :212
  ['distance', 'Distance'], // :267
  ['cycleTime', 'CycleTime'], // :293 (sic)
  ['coniferousVolume', 'Coniferous'], // :443
  ['deciduousVolume', 'Deciduous'], // :465
  ['originalRate', 'Original TtT Rate'], // :502
]

/** The sample number fields that also carry a range: legacy's limits, in the rebuild's wording. */
const SAMPLE_RANGES: Partial<
  Record<
    keyof SampleForm,
    { readonly range: { min: number; max: number }; readonly message: string }
  >
> = {
  skylineSlopeDistance: { range: { min: 0, max: 99_999 }, message: MESSAGES.slopeDistance },
  skylineSupportNumber: { range: { min: 0, max: 9_999 }, message: MESSAGES.supportNumber },
  supportAvgDistance: { range: { min: 0, max: 99_999.9 }, message: MESSAGES.supportAvgDistance },
  distance: { range: { min: 0, max: 999_999.9 }, message: MESSAGES.distance },
  cycleTime: { range: { min: 0, max: 999_999.9 }, message: MESSAGES.cycleTime },
  coniferousVolume: { range: VOLUME, message: MESSAGES.volume },
  deciduousVolume: { range: VOLUME, message: MESSAGES.volume },
  originalRate: { range: ORIGINAL_RATE, message: MESSAGES.originalRate },
}

/**
 * The sample fields' format checks — what legacy's converter / validator rejected on the field's
 * change: a percentage outside 0-100 (or not a number), a number box holding text, a value out of
 * range. The required and cross-field rules are added by {@link validateSampleForm}.
 */
const sampleFormatErrors = (form: SampleForm): Record<string, string> => {
  const errors: Record<string, string> = {}
  for (const field of PCT_FIELDS) {
    const e = rangeError(form[field], PERCENT, MESSAGES.percentage)
    if (e) errors[field] = e
  }
  for (const [field, label] of NUMBER_LABELS) {
    const raw = form[field]
    if (isBlank(raw)) continue
    if (!isNumberText(raw)) {
      errors[field] = numberPatternMessage(label, raw)
      continue
    }
    const ranged = SAMPLE_RANGES[field]
    const e = ranged ? rangeError(raw, ranged.range, ranged.message) : undefined
    if (e) errors[field] = e
  }
  return errors
}

/**
 * Advisory validation for the sample editor. Contract ID required (S20); each % individually 0–100
 * (S17); the skidding sum blocks Save only when > 100 (a sum < 100 is allowed — the exact-100 rule is
 * a Check-Status concern, S14/BR-06); Helicopter-conditional (Distance/Cycle/Direction/Dump required
 * when Helicopter% ≠ 0) and Other-conditional (skid type required, and not NA, when Other% ≠ 0); the
 * number boxes reject text; volume + original rate ranges. Returns a map of field → message.
 */
export function validateSampleForm(form: SampleForm): Record<string, string> {
  const errors: Record<string, string> = sampleFormatErrors(form)
  if (isBlank(form.contractId)) errors.contractId = MESSAGES.required

  // 100% asymmetry: block Save on > 100 only.
  if (skiddingTotal(form) > 100) errors.percentTotal = MESSAGES.percentOver

  // Helicopter-conditional required fields (S18 / BR at Save).
  if (isNonZero(form.helicopterPct)) {
    if (isBlank(form.distance)) errors.distance = MESSAGES.required
    if (isBlank(form.cycleTime)) errors.cycleTime = MESSAGES.required
    if (isBlank(form.uphillDirection)) errors.uphillDirection = MESSAGES.required
    if (isBlank(form.waterDumpDestination)) errors.waterDumpDestination = MESSAGES.required
  }
  // Other-conditional skid type (S18), split as legacy split it (`schedule8EditDetail.xhtml:372-375`):
  // a blank one fails `required`; `NA` passes `required` and then fails `notApplicableTypeValidator`.
  if (isNonZero(form.otherSkiddingPct)) {
    if (isBlank(form.skidTypeCode)) {
      errors.skidTypeCode = MESSAGES.required
    } else if (form.skidTypeCode.trim().toUpperCase() === 'NA') {
      errors.skidTypeCode = MESSAGES.notApplicable
    }
  }

  return errors
}

// ---- Rate (addition/deduction) form --------------------------------------------------------------

export interface RateForm {
  costItemCode: string
  costingRate: string
  costTypeCode: string
  itemDescription: string
}

export const emptyRateForm = (): RateForm => ({
  costItemCode: '',
  costingRate: '',
  costTypeCode: '',
  itemDescription: '',
})

/**
 * Advisory validation for a rate add-row (S21): Cost Item, $/m³ (with range), and Cost Type all
 * required; item description ≤ 30 chars. Returns a map of field → message.
 */
export function validateRateForm(form: RateForm): Record<string, string> {
  const errors: Record<string, string> = {}
  if (isBlank(form.costItemCode)) errors.costItemCode = MESSAGES.required
  if (isBlank(form.costingRate)) {
    errors.costingRate = MESSAGES.required
  } else if (!isNumberText(form.costingRate)) {
    // Legacy's `$/m3` carried an `f:convertNumber` (`schedule8AdditionsAndDeductions.xhtml:114,337`).
    errors.costingRate = numberPatternMessage('$/m3', form.costingRate)
  } else {
    const e = rangeError(form.costingRate, COSTING_RATE, MESSAGES.costingRate)
    if (e) errors.costingRate = e
  }
  if (isBlank(form.costTypeCode)) errors.costTypeCode = MESSAGES.required
  if (form.itemDescription.length > 30) errors.itemDescription = MESSAGES.descriptionMax
  return errors
}

// ---- The legacy validation banner (#359 group C change log, 2026-10-02) ----------------------------
//
// Save lists each failing field's line at the top, verbatim legacy, in legacy DOCUMENT order (JSF
// validates in component-tree order); a field's change adds or removes only its own line (the
// accumulating banner of Schedules 4, 7A, 9 and 10). A blank required field reports JSF's
// `{label}: Value is required.` here, while `Value Required` sits under the field; every other error
// (range, format, number pattern, NA) reports its inline text.

/** One editor's banner wiring: its key scope, its legacy field order, its required fields' labels. */
export interface BannerScheme {
  readonly scope: string
  readonly order: readonly string[]
  /** The legacy XHTML `label` of each field that can be required. */
  readonly requiredLabels: Readonly<Record<string, string>>
  /** Added to every rank, so two editors sharing one banner keep their own blocks. */
  readonly rankOffset?: number
}

/**
 * The page editor (`schedule8EditReport.xhtml`), row by row, left to right: Division, License,
 * Contact, Cutting Permit, Phone, Support Centre, Region, BioGeoClimatic Zone, TSA or TFL, TFL,
 * Supply Block, then Comments.
 */
export const PAGE_BANNER: BannerScheme = {
  scope: 'page',
  order: [
    'division',
    'license',
    'contact',
    'cuttingPermit',
    'phone',
    'supportCentre',
    'region',
    'becZone',
    'tsaNumber',
    'tflNumber',
    'supplyBlock',
    'comments',
  ],
  requiredLabels: {
    license: 'License', // :40
    supportCentre: 'Support Centre', // :128
    region: 'Region', // :156
    becZone: 'BioGeoClimatic Zone', // :184 (legacy's spelling; the visible heading differs)
    tsaNumber: 'TSA or TFL', // :208
  },
}

/**
 * The sample editor (`schedule8EditDetail.xhtml`) in document order: Contract ID, Cut Block, the
 * Ground Base / Grapple / Highlead %s, the Skyline panel, the Helicopter panel, Other (skid type, then
 * Other %), the Total, the volumes, then Original TtT Rate.
 */
export const SAMPLE_BANNER: BannerScheme = {
  scope: 'sample',
  order: [
    'contractId',
    'cutBlock',
    'groundBasePct',
    'grapplePct',
    'highleadPct',
    'skylinePct',
    'skylineSlopeDistance',
    'skylineSupportNumber',
    'supportAvgDistance',
    'helicopterPct',
    'distance',
    'cycleTime',
    'uphillDirection',
    'waterDumpDestination',
    'skidTypeCode',
    'otherSkiddingPct',
    'percentTotal',
    'coniferousVolume',
    'deciduousVolume',
    'originalRate',
  ],
  requiredLabels: {
    contractId: 'Contract ID', // :13
    distance: 'Distance', // :267
    cycleTime: 'CycleTime', // :293 (sic)
    uphillDirection: 'Direction', // :322
    waterDumpDestination: 'Dump Destination', // :346
    skidTypeCode: 'Other', // :375
  },
}

/**
 * One Additions / Deductions add form (`schedule8AdditionsAndDeductions.xhtml` :83-142 and
 * :307-361): the cost item (labelled with the table's own name), its description, `$/m3`, `Cost type`.
 */
export const rateBanner = (kind: 'addition' | 'deduction'): BannerScheme => ({
  scope: kind,
  order: ['costItemCode', 'itemDescription', 'costingRate', 'costTypeCode'],
  requiredLabels: {
    costItemCode: kind === 'addition' ? 'Additions' : 'Deductions', // :87 / :311
    costingRate: '$/m3', // :112 / :335
    costTypeCode: 'Cost type', // :124 / :346
  },
  rankOffset: kind === 'addition' ? 0 : 100,
})

/** One field's banner entry, or null when it has no error. */
export const bannerEntry = (
  scheme: BannerScheme,
  field: string,
  message: string | undefined,
): BannerEntry | null => {
  if (message === undefined) {
    return null
  }
  const label = message === MESSAGES.required ? scheme.requiredLabels[field] : undefined
  return {
    key: `${scheme.scope}:${field}`,
    rank: (scheme.rankOffset ?? 0) + scheme.order.indexOf(field),
    line: label === undefined ? message : legacyRequiredMessage(label),
  }
}

/** Every banner entry for a validated editor, in legacy order — the full list Save shows. */
export const bannerEntries = (
  scheme: BannerScheme,
  errors: Readonly<Record<string, string>>,
): BannerEntry[] => scheme.order.flatMap((field) => bannerEntry(scheme, field, errors[field]) ?? [])
