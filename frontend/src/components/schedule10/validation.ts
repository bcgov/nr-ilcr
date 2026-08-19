// Advisory client-side validation for a Schedule 10 construction page and road detail. The BACKEND is
// authoritative; these checks give immediate inline feedback and gate the call to avoid a doomed
// round-trip. Ranges and messages MIRROR the request DTOs and the message bundle, so an advisory
// message reads like a server rejection — which still renders verbatim on a 400, never replaced here.
//
// Inline per-field errors have to come from HERE. A Schedule 10 validation failure returns one
// ProblemDetail whose `detail` is a '; '-joined sentence with no field names, so the server response
// can drive a banner but can never mark the field that failed.

import type {
  ConstructionPageRequest,
  MaterialCompositionRequest,
  RoadDetailRequest,
  StabilizingRequest,
  SubGradeRequest,
} from '@/interfaces/Schedule10Request'
import type { ConstructionPage, RoadDetail } from '@/interfaces/Schedule10Response'
import { utf8Length } from '@/utils/forms'
import { numStrFixed, parseDecimalInput, roundCost } from '@/utils/number'

export { numStrFixed, parseDecimalInput, roundCost }

/** The sentinel the single TSA-or-TFL field carries when the page is TFL-located. */
export const TFL_SENTINEL = 'TFL'

export const DIVISION_MAX = 20
export const ROAD_NAME_MAX = 30
export const COMMENTS_MAX = 3500
export const TFL_MAX = 2

const PERIOD_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/

// Sub-grade length is capped at 100 while additional-stabilizing length allows 999.999. The asymmetry
// is legacy's on both of its own surfaces and is reproduced rather than normalised.
const SUB_GRADE_LENGTH = { min: 0, max: 100 }
const SURFACE_WIDTH = { min: 0, max: 999.9 }
const STABILIZING_LENGTH = { min: 0, max: 999.999 }
const DEPTH = { min: 0, max: 99.9 }
const DISTANCE_TO_SOURCE = { min: 0, max: 999.9 }
const HAUL_DISTANCE = { min: -9999.9, max: 9999.9 }
const VOLUME = { min: 0, max: 9_999_999 }
const COST = { min: 0, max: 9_999_999 }
const TRANSFER = { min: -9_999_999, max: 9_999_999 }
const PERCENTAGE = { min: 0, max: 100 }

// Verbatim from the backend bundle so an advisory message is indistinguishable from the server's.
export const SCH10_MESSAGES = {
  regionRequired: 'Region is required.',
  tsaOrTflRequired: 'TSA or TFL is required.',
  roadNameRequired: 'Road Name is required.',
  rsmrClassRequired: 'RSMR Class is required.',
  roadTypeRequired: 'Road Type: Value is required.',
  becZoneRequired: 'BEC Zone: Value is required.',
  ballastMethodRequired: 'Ballast Method Code: Value is required.',
  materialCodeTypeRequired: 'Material Code Type: Value is required.',
  invalidCodeValue: 'A valid value must be selected from the list.',
  tflInvalid: 'Entered TFL number is not valid for Interior Regions.',
  periodInvalid: 'The date is not valid. Enter date in format: YYYY-MM.',
  divisionMaxLength: 'Division must be 20 characters or fewer.',
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
  rangeZeroToOneHundred: 'Entered value must be between 0 and 100.',
  rangeZeroTo999Point9: 'Entered value must be between 0 and 999.9.',
  rangeZeroTo999Point999: 'Entered value must be between 0 and 999.999.',
  rangeZeroTo99Point9: 'Entered value must be between 0 and 99.9.',
  rangeHaulDistance: 'Entered value must be between -9999.9 and 9999.9.',
  costNonNegative: 'Entered cost must be between 0 and 9,999,999.',
  costTransfer: 'Entered cost must be between -9,999,999 and 9,999,999.',
  volumeRange: 'Entered volume must be between 0 and 9,999,999.',
  percentageRange: 'Entered percentage must be between 0 and 100.',
  sideSlopeRange: 'Side slope (%): percentage must be between 0 and 100.',
} as const

/**
 * The legacy display mask on each numeric input, as its decimal count. The mask — not the shape the
 * column happens to return — decides how a stored value displays, and JSON.parse has already dropped
 * the wire's trailing zeros by the time a value reaches here: a length served as 3.000 arrives as 3
 * and must still read 3.000.
 *
 * Applying the mask on blur also keeps entry inside the scale the server stores at, which otherwise
 * rounds silently — a depth of 0.04 becomes 0.0, which then makes its cost-per-length divide by zero.
 */
export const MASK_DIGITS = {
  sideSlopePct: 0,
  solidRockPct: 0,
  rippableRockPct: 0,
  coarsePct: 0,
  finePct: 0,
  organicPct: 0,
  sgLength: 3,
  sgSurfaceWidth: 1,
  sgActualCost: 0,
  sgTtTransfer: 0,
  sgOtherTransfer: 0,
  lessBridges: 0,
  lessCulverts: 0,
  lessLandings: 0,
  lessEndHaul: 0,
  lessOverland: 0,
  lessOtherEng: 0,
  stLength: 3,
  stSurfaceWidth: 1,
  stDepth: 1,
  stDistanceToSource: 1,
  stActualCost: 0,
  stTtTransfer: 0,
  stOtherTransfer: 0,
  endHaulDistance: 1,
  endHaulVolume: 0,
  overlandDistance: 1,
  overlandVolume: 0,
} as const

export type MaskedField = keyof typeof MASK_DIGITS

export type PageFormValues = {
  divisionName: string
  constructionPeriod: string
  forestRegionCode: string
  tsaOrTfl: string
  supplyBlock: string
  tflNumberCode: string
}

export type PageErrors = Partial<Record<keyof PageFormValues, string>>

export type RoadDetailFormValues = {
  roadName: string
  roadLifetimeCode: string
  becbiogeoCatalogueId: string
  relSoilMoistRgmClsCode: string
  sideSlopePct: string
  solidRockPct: string
  rippableRockPct: string
  coarsePct: string
  finePct: string
  organicPct: string
  sgLength: string
  sgSurfaceWidth: string
  sgActualCost: string
  sgTtTransfer: string
  sgOtherTransfer: string
  lessBridges: string
  lessCulverts: string
  lessLandings: string
  lessEndHaul: string
  lessOverland: string
  lessOtherEng: string
  stBallastMethodCode: string
  stBallastMaterialCode: string
  stLength: string
  stSurfaceWidth: string
  stDepth: string
  stDistanceToSource: string
  stActualCost: string
  stTtTransfer: string
  stOtherTransfer: string
  detailedEngineeringCostInd: string
  endHaulDistance: string
  endHaulVolume: string
  overlandDistance: string
  overlandVolume: string
  comments: string
}

export type RoadDetailErrors = Partial<Record<keyof RoadDetailFormValues, string>>

/** True when the location is TFL-based, which is what disables Supply Block and enables TFL. */
export const isTflLocated = (tsaOrTfl: string): boolean =>
  tsaOrTfl.trim().toUpperCase() === TFL_SENTINEL

/** Ballast method `C` is the branch that requires a material code; a blank method lands there too. */
export const ballastMaterialRequired = (methodCode: string): boolean => {
  const code = methodCode.trim().toUpperCase()
  return code === '' || code === 'C'
}

/** Ballast method `N` is the branch whose dimensions and two costs the server forces to zero. */
export const ballastZeroesFigures = (methodCode: string): boolean =>
  methodCode.trim().toUpperCase() === 'N'

/**
 * Legacy narrowed the supply-block list to blocks whose code begins with the chosen TSA, which is
 * what makes the pair coherent — block `01A` belongs to TSA `01`. With no TSA chosen the list is
 * empty rather than the full catalogue, matching the legacy control's cleared state.
 *
 * A stored block is always kept, even when it does not belong to the stored TSA. Delivery holds such
 * pairs — a page on TSA `02` carrying block `01D` — because the TSA leg was never validated, and
 * narrowing them away would blank a field that does hold a value and silently drop it on the next
 * save. The narrowing governs what can be CHOSEN; it must not hide what is already there.
 */
export const supplyBlocksFor = <T extends { readonly code: string }>(
  blocks: readonly T[],
  tsaOrTfl: string,
  selectedCode = '',
): T[] => {
  const tsa = tsaOrTfl.trim()
  const selected = selectedCode.trim()
  const stored = selected === '' ? [] : blocks.filter((block) => block.code === selected)

  if (tsa === '' || isTflLocated(tsa)) {
    return stored
  }

  const offered = blocks.filter((block) => block.code.startsWith(tsa))
  const missing = stored.filter((block) => !offered.includes(block))
  return [...offered, ...missing]
}

export const emptyPageForm = (): PageFormValues => ({
  divisionName: '',
  constructionPeriod: '',
  forestRegionCode: '',
  tsaOrTfl: '',
  supplyBlock: '',
  tflNumberCode: '',
})

/**
 * Seed a page editor from a stored page. The stored location is one of two branches, so a TFL page
 * seeds the sentinel into the shared field and leaves the supply block blank.
 */
export const formFromPage = (page: ConstructionPage): PageFormValues => ({
  divisionName: page.divisionName ?? '',
  constructionPeriod: page.constructionPeriod ?? '',
  forestRegionCode: page.forestRegionCode ?? '',
  tsaOrTfl: page.tflNumberCode != null ? TFL_SENTINEL : (page.tsaNumber ?? ''),
  supplyBlock: page.tsbNumberCode ?? '',
  tflNumberCode: page.tflNumberCode ?? '',
})

export const emptyRoadDetailForm = (): RoadDetailFormValues => ({
  roadName: '',
  roadLifetimeCode: '',
  becbiogeoCatalogueId: '',
  relSoilMoistRgmClsCode: '',
  sideSlopePct: '',
  solidRockPct: '',
  rippableRockPct: '',
  coarsePct: '',
  finePct: '',
  organicPct: '',
  sgLength: '',
  sgSurfaceWidth: '',
  sgActualCost: '',
  sgTtTransfer: '',
  sgOtherTransfer: '',
  lessBridges: '',
  lessCulverts: '',
  lessLandings: '',
  lessEndHaul: '',
  lessOverland: '',
  lessOtherEng: '',
  stBallastMethodCode: '',
  stBallastMaterialCode: '',
  stLength: '',
  stSurfaceWidth: '',
  stDepth: '',
  stDistanceToSource: '',
  stActualCost: '',
  stTtTransfer: '',
  stOtherTransfer: '',
  detailedEngineeringCostInd: 'N',
  endHaulDistance: '',
  endHaulVolume: '',
  overlandDistance: '',
  overlandVolume: '',
  comments: '',
})

/** Seed a road-detail editor from a stored row. Numeric fields seed THROUGH their mask. */
export const formFromRoadDetail = (detail: RoadDetail): RoadDetailFormValues => ({
  roadName: detail.roadName ?? '',
  roadLifetimeCode: detail.roadLifetimeCode ?? '',
  becbiogeoCatalogueId:
    detail.becClassification == null
      ? ''
      : String(detail.becClassification.biogeoclimaticCatalogueId),
  relSoilMoistRgmClsCode: detail.relSoilMoistRgmClsCode ?? '',
  sideSlopePct: numStrFixed(detail.sideSlopePct, MASK_DIGITS.sideSlopePct),
  solidRockPct: numStrFixed(detail.materialComposition.solidRockPct, MASK_DIGITS.solidRockPct),
  rippableRockPct: numStrFixed(
    detail.materialComposition.rippableRockPct,
    MASK_DIGITS.rippableRockPct,
  ),
  coarsePct: numStrFixed(detail.materialComposition.coarsePct, MASK_DIGITS.coarsePct),
  finePct: numStrFixed(detail.materialComposition.finePct, MASK_DIGITS.finePct),
  organicPct: numStrFixed(detail.materialComposition.organicPct, MASK_DIGITS.organicPct),
  sgLength: numStrFixed(detail.subGrade.length, MASK_DIGITS.sgLength),
  sgSurfaceWidth: numStrFixed(detail.subGrade.surfaceWidth, MASK_DIGITS.sgSurfaceWidth),
  sgActualCost: numStrFixed(detail.subGrade.actualCost, MASK_DIGITS.sgActualCost),
  sgTtTransfer: numStrFixed(detail.subGrade.ttTransfer, MASK_DIGITS.sgTtTransfer),
  sgOtherTransfer: numStrFixed(detail.subGrade.otherTransfer, MASK_DIGITS.sgOtherTransfer),
  lessBridges: numStrFixed(detail.subGrade.lessBridges, MASK_DIGITS.lessBridges),
  lessCulverts: numStrFixed(detail.subGrade.lessCulverts, MASK_DIGITS.lessCulverts),
  lessLandings: numStrFixed(detail.subGrade.lessLandings, MASK_DIGITS.lessLandings),
  lessEndHaul: numStrFixed(detail.subGrade.lessEndHaul, MASK_DIGITS.lessEndHaul),
  lessOverland: numStrFixed(detail.subGrade.lessOverland, MASK_DIGITS.lessOverland),
  lessOtherEng: numStrFixed(detail.subGrade.lessOtherEng, MASK_DIGITS.lessOtherEng),
  stBallastMethodCode: detail.stabilizing.ballastMethodCode ?? '',
  stBallastMaterialCode: detail.stabilizing.ballastMaterialCode ?? '',
  stLength: numStrFixed(detail.stabilizing.length, MASK_DIGITS.stLength),
  stSurfaceWidth: numStrFixed(detail.stabilizing.surfaceWidth, MASK_DIGITS.stSurfaceWidth),
  stDepth: numStrFixed(detail.stabilizing.depth, MASK_DIGITS.stDepth),
  stDistanceToSource: numStrFixed(
    detail.stabilizing.distanceToSource,
    MASK_DIGITS.stDistanceToSource,
  ),
  stActualCost: numStrFixed(detail.stabilizing.actualCost, MASK_DIGITS.stActualCost),
  stTtTransfer: numStrFixed(detail.stabilizing.ttTransfer, MASK_DIGITS.stTtTransfer),
  stOtherTransfer: numStrFixed(detail.stabilizing.otherTransfer, MASK_DIGITS.stOtherTransfer),
  detailedEngineeringCostInd: detail.detailedEngineeringCostInd ?? 'N',
  endHaulDistance: numStrFixed(detail.endHaulDistance, MASK_DIGITS.endHaulDistance),
  endHaulVolume: numStrFixed(detail.endHaulVolume, MASK_DIGITS.endHaulVolume),
  overlandDistance: numStrFixed(detail.overlandDistance, MASK_DIGITS.overlandDistance),
  overlandVolume: numStrFixed(detail.overlandVolume, MASK_DIGITS.overlandVolume),
  comments: detail.comments ?? '',
})

type Bounds = { min: number; max: number }

/** An OPTIONAL number within bounds; blank passes. */
const optionalRange = (raw: string, bounds: Bounds, message: string): string | undefined => {
  if (raw.trim() === '') {
    return undefined
  }
  const value = parseDecimalInput(raw)
  if (value === null || value < bounds.min || value > bounds.max) {
    return message
  }
  return undefined
}

/**
 * An OPTIONAL whole-dollar cost within bounds; blank passes. Checked on the parsed AND the rounded
 * value, because rounding is what actually reaches the wire.
 */
const optionalCost = (raw: string, bounds: Bounds, message: string): string | undefined => {
  const parsed = optionalRange(raw, bounds, message)
  if (parsed !== undefined || raw.trim() === '') {
    return parsed
  }
  const rounded = roundCost(parseDecimalInput(raw))
  return rounded !== null && (rounded < bounds.min || rounded > bounds.max) ? message : undefined
}

/** Advisory validation for a construction page. Region and TSA-or-TFL are the required pair. */
export function validatePage(form: PageFormValues): PageErrors {
  const errors: PageErrors = {}

  if (form.forestRegionCode.trim() === '') {
    errors.forestRegionCode = SCH10_MESSAGES.regionRequired
  }
  if (form.tsaOrTfl.trim() === '') {
    errors.tsaOrTfl = SCH10_MESSAGES.tsaOrTflRequired
  }

  const division = form.divisionName.trim()
  if (division.length > DIVISION_MAX || utf8Length(division) > DIVISION_MAX) {
    errors.divisionName = SCH10_MESSAGES.divisionMaxLength
  }

  const period = form.constructionPeriod.trim()
  if (period !== '' && !PERIOD_PATTERN.test(period)) {
    errors.constructionPeriod = SCH10_MESSAGES.periodInvalid
  }

  // The TFL number is only meaningful on its own branch; the other branch clears it before sending.
  if (isTflLocated(form.tsaOrTfl) && form.tflNumberCode.trim().length > TFL_MAX) {
    errors.tflNumberCode = SCH10_MESSAGES.tflInvalid
  }

  return errors
}

/** Advisory validation for one road detail. */
export function validateRoadDetail(form: RoadDetailFormValues): RoadDetailErrors {
  const errors: RoadDetailErrors = {}

  if (form.roadName.trim() === '') {
    errors.roadName = SCH10_MESSAGES.roadNameRequired
  } else if (
    form.roadName.trim().length > ROAD_NAME_MAX ||
    utf8Length(form.roadName.trim()) > ROAD_NAME_MAX
  ) {
    errors.roadName = SCH10_MESSAGES.invalidCodeValue
  }
  if (form.roadLifetimeCode.trim() === '') {
    errors.roadLifetimeCode = SCH10_MESSAGES.roadTypeRequired
  }
  if (form.becbiogeoCatalogueId.trim() === '') {
    errors.becbiogeoCatalogueId = SCH10_MESSAGES.becZoneRequired
  }
  if (form.relSoilMoistRgmClsCode.trim() === '') {
    errors.relSoilMoistRgmClsCode = SCH10_MESSAGES.rsmrClassRequired
  }
  if (form.stBallastMethodCode.trim() === '') {
    errors.stBallastMethodCode = SCH10_MESSAGES.ballastMethodRequired
  } else if (
    ballastMaterialRequired(form.stBallastMethodCode) &&
    form.stBallastMaterialCode.trim() === ''
  ) {
    errors.stBallastMaterialCode = SCH10_MESSAGES.materialCodeTypeRequired
  }

  const sideSlope = optionalRange(form.sideSlopePct, PERCENTAGE, SCH10_MESSAGES.sideSlopeRange)
  if (sideSlope) {
    errors.sideSlopePct = sideSlope
  }

  const percentages = [
    'solidRockPct',
    'rippableRockPct',
    'coarsePct',
    'finePct',
    'organicPct',
  ] as const
  for (const key of percentages) {
    const issue = optionalRange(form[key], PERCENTAGE, SCH10_MESSAGES.percentageRange)
    if (issue) {
      errors[key] = issue
    }
  }

  const dimensions: [keyof RoadDetailFormValues, Bounds, string][] = [
    ['sgLength', SUB_GRADE_LENGTH, SCH10_MESSAGES.rangeZeroToOneHundred],
    ['sgSurfaceWidth', SURFACE_WIDTH, SCH10_MESSAGES.rangeZeroTo999Point9],
    ['stLength', STABILIZING_LENGTH, SCH10_MESSAGES.rangeZeroTo999Point999],
    ['stSurfaceWidth', SURFACE_WIDTH, SCH10_MESSAGES.rangeZeroTo999Point9],
    ['stDepth', DEPTH, SCH10_MESSAGES.rangeZeroTo99Point9],
    ['stDistanceToSource', DISTANCE_TO_SOURCE, SCH10_MESSAGES.rangeZeroTo999Point9],
    ['endHaulDistance', HAUL_DISTANCE, SCH10_MESSAGES.rangeHaulDistance],
    ['overlandDistance', HAUL_DISTANCE, SCH10_MESSAGES.rangeHaulDistance],
  ]
  for (const [key, bounds, message] of dimensions) {
    const issue = optionalRange(form[key], bounds, message)
    if (issue) {
      errors[key] = issue
    }
  }

  for (const key of ['endHaulVolume', 'overlandVolume'] as const) {
    const issue = optionalCost(form[key], VOLUME, SCH10_MESSAGES.volumeRange)
    if (issue) {
      errors[key] = issue
    }
  }

  const nonNegativeCosts = [
    'sgActualCost',
    'lessBridges',
    'lessCulverts',
    'lessLandings',
    'lessEndHaul',
    'lessOverland',
    'lessOtherEng',
    'stActualCost',
  ] as const
  for (const key of nonNegativeCosts) {
    const issue = optionalCost(form[key], COST, SCH10_MESSAGES.costNonNegative)
    if (issue) {
      errors[key] = issue
    }
  }

  const transfers = ['sgTtTransfer', 'sgOtherTransfer', 'stTtTransfer', 'stOtherTransfer'] as const
  for (const key of transfers) {
    const issue = optionalCost(form[key], TRANSFER, SCH10_MESSAGES.costTransfer)
    if (issue) {
      errors[key] = issue
    }
  }

  const comments = form.comments.trim()
  if (comments.length > COMMENTS_MAX || utf8Length(comments) > COMMENTS_MAX) {
    errors.comments = SCH10_MESSAGES.commentsMaxLength
  }

  return errors
}

const blankToNull = (raw: string): string | null => (raw.trim() === '' ? null : raw.trim())
const numberOrNull = (raw: string): number | null => parseDecimalInput(raw)
const costOrNull = (raw: string): number | null => roundCost(parseDecimalInput(raw))

/**
 * Build the page write body. The TSA-or-TFL sentinel is normalised to upper case so a lower-case
 * entry takes the TFL branch rather than being stored as a TSA number, and each branch sends only
 * its own half — the server clears the other side regardless.
 */
export const buildPageBody = (
  form: PageFormValues,
  revisionCount?: number,
): ConstructionPageRequest => {
  const tfl = isTflLocated(form.tsaOrTfl)
  return {
    forestRegionCode: form.forestRegionCode.trim(),
    tsaOrTfl: tfl ? TFL_SENTINEL : form.tsaOrTfl.trim(),
    supplyBlock: tfl ? null : blankToNull(form.supplyBlock),
    tflNumberCode: tfl ? blankToNull(form.tflNumberCode) : null,
    divisionName: blankToNull(form.divisionName),
    constructionPeriod: blankToNull(form.constructionPeriod),
    ...(revisionCount === undefined ? {} : { revisionCount }),
  }
}

const buildSubGrade = (form: RoadDetailFormValues): SubGradeRequest => ({
  length: numberOrNull(form.sgLength),
  surfaceWidth: numberOrNull(form.sgSurfaceWidth),
  actualCost: costOrNull(form.sgActualCost),
  ttTransfer: costOrNull(form.sgTtTransfer),
  otherTransfer: costOrNull(form.sgOtherTransfer),
  lessBridges: costOrNull(form.lessBridges),
  lessCulverts: costOrNull(form.lessCulverts),
  lessLandings: costOrNull(form.lessLandings),
  lessOverland: costOrNull(form.lessOverland),
  lessOtherEng: costOrNull(form.lessOtherEng),
  lessEndHaul: costOrNull(form.lessEndHaul),
})

const buildStabilizing = (form: RoadDetailFormValues): StabilizingRequest => ({
  ballastMethodCode: form.stBallastMethodCode.trim(),
  ballastMaterialCode: blankToNull(form.stBallastMaterialCode),
  length: numberOrNull(form.stLength),
  surfaceWidth: numberOrNull(form.stSurfaceWidth),
  depth: numberOrNull(form.stDepth),
  distanceToSource: numberOrNull(form.stDistanceToSource),
  actualCost: costOrNull(form.stActualCost),
  ttTransfer: costOrNull(form.stTtTransfer),
  otherTransfer: costOrNull(form.stOtherTransfer),
})

const buildMaterial = (form: RoadDetailFormValues): MaterialCompositionRequest => ({
  solidRockPct: costOrNull(form.solidRockPct),
  rippableRockPct: costOrNull(form.rippableRockPct),
  coarsePct: costOrNull(form.coarsePct),
  finePct: costOrNull(form.finePct),
  organicPct: costOrNull(form.organicPct),
})

/** Build the road-detail write body. Every substructure is sent whole; a write is not a patch. */
export const buildRoadDetailBody = (
  form: RoadDetailFormValues,
  revisionCount?: number,
): RoadDetailRequest => ({
  roadName: form.roadName.trim(),
  roadLifetimeCode: form.roadLifetimeCode.trim(),
  becbiogeoCatalogueId:
    form.becbiogeoCatalogueId.trim() === '' ? null : Number(form.becbiogeoCatalogueId),
  relSoilMoistRgmClsCode: form.relSoilMoistRgmClsCode.trim(),
  sideSlopePct: costOrNull(form.sideSlopePct),
  detailedEngineeringCostInd: blankToNull(form.detailedEngineeringCostInd),
  subGrade: buildSubGrade(form),
  stabilizing: buildStabilizing(form),
  materialComposition: buildMaterial(form),
  endHaulDistance: numberOrNull(form.endHaulDistance),
  endHaulVolume: costOrNull(form.endHaulVolume),
  overlandDistance: numberOrNull(form.overlandDistance),
  overlandVolume: costOrNull(form.overlandVolume),
  comments: blankToNull(form.comments),
  ...(revisionCount === undefined ? {} : { revisionCount }),
})

/**
 * The derived figures the legacy screen recomputed on every blur, so the reporter sees the effect of
 * an entry before saving. Display only — the server's own values replace these on the next echo, and
 * none of them is ever sent.
 *
 * An absent cost counts as zero inside a total, which is why a road detail with nothing entered
 * shows 0 rather than a blank total.
 */
const sumCosts = (raws: readonly string[]): number =>
  raws.reduce((total, raw) => total + (roundCost(parseDecimalInput(raw)) ?? 0), 0)

const atScale2 = (value: number): number => Math.round(value * 100) / 100

const dividedBy = (total: number, divisorRaw: string): number | null => {
  const divisor = parseDecimalInput(divisorRaw)
  if (divisor === null || divisor === 0) {
    return null
  }
  return atScale2(total / divisor)
}

export const previewSubGradeTotalCosts = (form: RoadDetailFormValues): number =>
  sumCosts([form.sgActualCost, form.sgTtTransfer, form.sgOtherTransfer])

export const previewSubGradeTotalDeductions = (form: RoadDetailFormValues): number =>
  sumCosts([
    form.lessBridges,
    form.lessCulverts,
    form.lessLandings,
    form.lessEndHaul,
    form.lessOverland,
    form.lessOtherEng,
  ])

export const previewSubGradeTotal = (form: RoadDetailFormValues): number =>
  previewSubGradeTotalCosts(form) - previewSubGradeTotalDeductions(form)

export const previewSubGradeCostPerLength = (form: RoadDetailFormValues): number | null =>
  dividedBy(previewSubGradeTotal(form), form.sgLength)

export const previewStabilizingTotal = (form: RoadDetailFormValues): number =>
  sumCosts([form.stActualCost, form.stTtTransfer, form.stOtherTransfer])

export const previewStabilizingCostPerLength = (form: RoadDetailFormValues): number | null =>
  dividedBy(previewStabilizingTotal(form), form.stLength)

/** Integer arithmetic that coerces blanks to zero, so this is never blank — five blanks total 0. */
export const previewMaterialTotal = (form: RoadDetailFormValues): number =>
  sumCosts([form.solidRockPct, form.rippableRockPct, form.coarsePct, form.finePct, form.organicPct])

/**
 * The read-only $/m³/km preview beside each haul row: the deduction ÷ volume ÷ distance, at scale 2.
 * Null whenever a divisor is zero or blank.
 *
 * The document does not serve this rate, but it serves all three inputs and the legacy screen shows
 * it, so it is computed here for display only and never sent.
 */
export const previewCostPerVolumePerLength = (
  cost: string,
  volume: string,
  distance: string,
): number | null => {
  const costValue = roundCost(parseDecimalInput(cost))
  const volumeValue = parseDecimalInput(volume)
  const distanceValue = parseDecimalInput(distance)
  if (
    costValue === null ||
    volumeValue === null ||
    distanceValue === null ||
    volumeValue === 0 ||
    distanceValue === 0
  ) {
    return null
  }
  return Math.round((costValue / volumeValue / distanceValue) * 100) / 100
}
