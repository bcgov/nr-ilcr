// Form shapes + advisory client-side validation for the three Schedule 8 levels (page / sample /
// rate). The BACKEND is authoritative (Stories 14.2–14.4); these checks give immediate inline
// feedback and gate the write to avoid a doomed round-trip. Ranges mirror the write DTOs. The
// skidding 100% asymmetry is preserved here exactly: a sum > 100 blocks Save; a sum ≠ 100 is NOT
// blocked at Save (it is flagged only at Check Status — 14.6).

import type { Page, Sample } from '@/interfaces/Schedule8Response'

// ---- Shared helpers ------------------------------------------------------------------------------

export const isBlank = (raw: string | undefined): boolean => raw === undefined || raw.trim() === ''

export const toNum = (raw: string): number | null => {
  const trimmed = raw.trim()
  if (trimmed === '') return null
  const n = Number(trimmed)
  return Number.isNaN(n) ? null : n
}

export const numStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value)

export const fmt = (value: number | null | undefined): string =>
  value === null || value === undefined ? '—' : String(value)

const rangeError = (
  raw: string,
  range: { min: number; max: number },
  message: string,
): string | undefined => {
  if (isBlank(raw)) return undefined
  const value = Number(raw)
  if (Number.isNaN(value) || value < range.min || value > range.max) return message
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
 * Advisory validation for the page editor: License / Support Centre / Region / BEC Zone / TSA-or-TFL
 * all required at Save (FLD-006). Returns a map of field → message for every invalid field.
 */
export function validatePageForm(form: PageForm): Record<string, string> {
  const errors: Record<string, string> = {}
  if (isBlank(form.license)) errors.license = MESSAGES.required
  if (isBlank(form.supportCentre)) errors.supportCentre = MESSAGES.required
  if (isBlank(form.region)) errors.region = MESSAGES.required
  if (isBlank(form.becZone)) errors.becZone = MESSAGES.required
  // TSA-or-TFL context: the TSA-or-TFL field must be chosen (BR-03).
  if (isBlank(form.tsaNumber)) errors.tsaNumber = MESSAGES.required
  return errors
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
 * Advisory validation for the sample editor. Contract ID required (S20); each % individually 0–100
 * (S17); the skidding sum blocks Save only when > 100 (a sum < 100 is allowed — the exact-100 rule is
 * a Check-Status concern, S14/BR-06); Helicopter-conditional (Distance/Cycle/Direction/Dump required
 * when Helicopter% ≠ 0) and Other-conditional (skid type required when Other% ≠ 0); volume + original
 * rate ranges. Returns a map of field → message.
 */
export function validateSampleForm(form: SampleForm): Record<string, string> {
  const errors: Record<string, string> = {}
  if (isBlank(form.contractId)) errors.contractId = MESSAGES.required

  for (const field of PCT_FIELDS) {
    const e = rangeError(form[field], PERCENT, MESSAGES.percentage)
    if (e) errors[field] = e
  }
  // 100% asymmetry: block Save on > 100 only.
  if (skiddingTotal(form) > 100) errors.percentTotal = MESSAGES.percentOver

  // Helicopter-conditional required fields (S18 / BR at Save).
  if (isNonZero(form.helicopterPct)) {
    if (isBlank(form.distance)) errors.distance = MESSAGES.required
    if (isBlank(form.cycleTime)) errors.cycleTime = MESSAGES.required
    if (isBlank(form.uphillDirection)) errors.uphillDirection = MESSAGES.required
    if (isBlank(form.waterDumpDestination)) errors.waterDumpDestination = MESSAGES.required
  }
  // Other-conditional skid type (S18): required and not "NA" when Other% ≠ 0.
  if (isNonZero(form.otherSkiddingPct)) {
    if (isBlank(form.skidTypeCode) || form.skidTypeCode.trim().toUpperCase() === 'NA') {
      errors.skidTypeCode = MESSAGES.required
    }
  }

  const coniferous = rangeError(form.coniferousVolume, VOLUME, MESSAGES.volume)
  if (coniferous) errors.coniferousVolume = coniferous
  const deciduous = rangeError(form.deciduousVolume, VOLUME, MESSAGES.volume)
  if (deciduous) errors.deciduousVolume = deciduous
  const rate = rangeError(form.originalRate, ORIGINAL_RATE, MESSAGES.originalRate)
  if (rate) errors.originalRate = rate

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
  } else {
    const e = rangeError(form.costingRate, COSTING_RATE, MESSAGES.costingRate)
    if (e) errors.costingRate = e
  }
  if (isBlank(form.costTypeCode)) errors.costTypeCode = MESSAGES.required
  if (form.itemDescription.length > 30) errors.itemDescription = MESSAGES.descriptionMax
  return errors
}
