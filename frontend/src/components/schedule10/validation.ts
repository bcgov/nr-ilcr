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
import type { CodeDescription, ConstructionPage, RoadDetail } from '@/interfaces/Schedule10Response'
import { utf8Length } from '@/utils/forms'
import { numStrFixed, parseDecimalInput, roundCost } from '@/utils/number'

export { numStrFixed, parseDecimalInput, roundCost }

/** The sentinel the single TSA-or-TFL field carries when the page is TFL-located. */
export const TFL_SENTINEL = 'TFL'

export const DIVISION_MAX = 20
export const ROAD_NAME_MAX = 30
export const COMMENTS_MAX = 3500
/**
 * The comments column is 4000 BYTES while the contract caps entry at 3500 CHARACTERS, so the two
 * limits are not the same number. Checking bytes against 3500 blocked a legal save of any comment
 * over 3500 bytes but under 3500 characters — i.e. anything with accents or dashes in it.
 */
export const COMMENTS_MAX_BYTES = 4000
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
  staleRecord: 'This schedule was changed by another user. Please reload and try again.',
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
  /**
   * The stored classification's own label, kept only so a DE-LISTED one still renders as text. The
   * offerable list carries a label for everything it offers; a row whose classification has since
   * been de-listed is absent from that list, and dropping this left the combo showing the raw
   * catalogue id. Display only — never validated, never sent.
   */
  becbiogeoLabel: string
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

/**
 * The Additional Stabilizing figures ballast method `N` stores as zero, so the form can disable
 * exactly the inputs whose entry would be discarded. `stTtTransfer` is deliberately ABSENT: the
 * server keeps it on the `N` branch, so it stays editable.
 */
export const BALLAST_ZEROED_FIELDS = [
  'stLength',
  'stSurfaceWidth',
  'stDepth',
  'stDistanceToSource',
  'stActualCost',
  'stOtherTransfer',
] as const

/** The material code the server substitutes on the `N` and `D` branches. */
export const BALLAST_MATERIAL_NA = 'NA'

/** True when the ballast method forces the material to `NA`: both `N` and `D` do. */
export const ballastForcesMaterialNa = (methodCode: string): boolean => {
  const code = methodCode.trim().toUpperCase()
  return code === 'N' || code === 'D'
}

/**
 * Ballast method `N` is the branch whose dimensions and two costs the server forces to zero. The
 * MATERIAL is not this branch's business — `D` forces that too, so it goes through
 * {@link ballastForcesMaterialNa}.
 */
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
 *
 * That holds for a block absent from the CATALOGUE too, not just one off the chosen branch. Filtering
 * the served list for the stored code yields nothing when the code was never served — delivery page
 * 8904 stores TSB `16Z`, which no longer appears in the code table — and the field then renders blank
 * over a value that is really there. The stored code is synthesised as its own option instead, showing
 * the bare code because that is all the document carries about it.
 */
export const supplyBlocksFor = (
  blocks: readonly CodeDescription[],
  tsaOrTfl: string,
  selectedCode = '',
): CodeDescription[] => {
  const tsa = tsaOrTfl.trim()
  const selected = selectedCode.trim()
  const stored: CodeDescription[] =
    selected === ''
      ? []
      : [
          blocks.find((block) => block.code === selected) ?? {
            code: selected,
            description: selected,
          },
        ]

  if (tsa === '' || isTflLocated(tsa)) {
    return stored
  }

  const offered = blocks.filter((block) => block.code.startsWith(tsa))
  const missing = stored.filter((block) => !offered.some((o) => o.code === block.code))
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
  becbiogeoLabel: '',
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
  becbiogeoLabel: detail.becClassification?.label ?? '',
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
  //
  // The over-length check alone was dead code — the control carries maxLength={TFL_MAX}, so
  // `length > TFL_MAX` can never be true from the keyboard. What DOES reach the server is a blank or
  // non-numeric TFL on the TFL branch, which is exactly the doomed round trip this module exists to
  // stop, so that is what is gated. Legacy's own field is a 2-digit numeric.
  if (isTflLocated(form.tsaOrTfl)) {
    const tfl = form.tflNumberCode.trim()
    if (tfl === '' || tfl.length > TFL_MAX || !/^\d+$/.test(tfl)) {
      errors.tflNumberCode = SCH10_MESSAGES.tflInvalid
    }
  }

  return errors
}

/** Every road-detail field whose only rule is "must not be blank", with the server's own message. */
const REQUIRED_ROAD_DETAIL_FIELDS: readonly (readonly [keyof RoadDetailFormValues, string])[] = [
  ['roadLifetimeCode', SCH10_MESSAGES.roadTypeRequired],
  ['becbiogeoCatalogueId', SCH10_MESSAGES.becZoneRequired],
  ['relSoilMoistRgmClsCode', SCH10_MESSAGES.rsmrClassRequired],
]

/**
 * Every bounded numeric on the road form, as (field, how to check it, bounds, message).
 *
 * `range` checks the PARSED value; `cost` additionally checks the ROUNDED one, because rounding is
 * what actually reaches the wire (the 13.3 correction). Holding the rules as data rather than as five
 * near-identical loops is what keeps this module readable — and it makes the odd ones out visible:
 * `sideSlopePct` shares the percentage bounds but carries its own message, and the two length caps
 * are deliberately asymmetric (see SUB_GRADE_LENGTH / STABILIZING_LENGTH).
 */
type NumericCheck = readonly [keyof RoadDetailFormValues, 'range' | 'cost', Bounds, string]

const ROAD_DETAIL_NUMERICS: readonly NumericCheck[] = [
  // Shoulder — percentage bounds, its own message.
  ['sideSlopePct', 'range', PERCENTAGE, SCH10_MESSAGES.sideSlopeRange],

  // Material composition.
  ['solidRockPct', 'range', PERCENTAGE, SCH10_MESSAGES.percentageRange],
  ['rippableRockPct', 'range', PERCENTAGE, SCH10_MESSAGES.percentageRange],
  ['coarsePct', 'range', PERCENTAGE, SCH10_MESSAGES.percentageRange],
  ['finePct', 'range', PERCENTAGE, SCH10_MESSAGES.percentageRange],
  ['organicPct', 'range', PERCENTAGE, SCH10_MESSAGES.percentageRange],

  // Dimensions.
  ['sgLength', 'range', SUB_GRADE_LENGTH, SCH10_MESSAGES.rangeZeroToOneHundred],
  ['sgSurfaceWidth', 'range', SURFACE_WIDTH, SCH10_MESSAGES.rangeZeroTo999Point9],
  ['stLength', 'range', STABILIZING_LENGTH, SCH10_MESSAGES.rangeZeroTo999Point999],
  ['stSurfaceWidth', 'range', SURFACE_WIDTH, SCH10_MESSAGES.rangeZeroTo999Point9],
  ['stDepth', 'range', DEPTH, SCH10_MESSAGES.rangeZeroTo99Point9],
  ['stDistanceToSource', 'range', DISTANCE_TO_SOURCE, SCH10_MESSAGES.rangeZeroTo999Point9],
  ['endHaulDistance', 'range', HAUL_DISTANCE, SCH10_MESSAGES.rangeHaulDistance],
  ['overlandDistance', 'range', HAUL_DISTANCE, SCH10_MESSAGES.rangeHaulDistance],

  // Volumes.
  ['endHaulVolume', 'cost', VOLUME, SCH10_MESSAGES.volumeRange],
  ['overlandVolume', 'cost', VOLUME, SCH10_MESSAGES.volumeRange],

  // Non-negative costs.
  ['sgActualCost', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessBridges', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessCulverts', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessLandings', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessEndHaul', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessOverland', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['lessOtherEng', 'cost', COST, SCH10_MESSAGES.costNonNegative],
  ['stActualCost', 'cost', COST, SCH10_MESSAGES.costNonNegative],

  // Transfers, which may be negative.
  ['sgTtTransfer', 'cost', TRANSFER, SCH10_MESSAGES.costTransfer],
  ['sgOtherTransfer', 'cost', TRANSFER, SCH10_MESSAGES.costTransfer],
  ['stTtTransfer', 'cost', TRANSFER, SCH10_MESSAGES.costTransfer],
  ['stOtherTransfer', 'cost', TRANSFER, SCH10_MESSAGES.costTransfer],
]

/**
 * The road name: required, and capped at ROAD_NAME_MAX in BOTH characters and bytes.
 *
 * Blank and over-length answer with the SAME message, because that is what the server answers with —
 * `invalidCodeValue` belongs to the code-backed controls and reads nonsensically beside a free-text
 * name box ("A valid value must be selected from the list").
 */
const roadNameError = (form: RoadDetailFormValues): RoadDetailErrors => {
  const roadName = form.roadName.trim()
  const tooLong = roadName.length > ROAD_NAME_MAX || utf8Length(roadName) > ROAD_NAME_MAX
  return roadName === '' || tooLong ? { roadName: SCH10_MESSAGES.roadNameRequired } : {}
}

/** Ballast method is required, and its `C` branch (which a BLANK code also lands in) needs a material. */
const ballastErrors = (form: RoadDetailFormValues): RoadDetailErrors => {
  if (form.stBallastMethodCode.trim() === '') {
    return { stBallastMethodCode: SCH10_MESSAGES.ballastMethodRequired }
  }
  const needsMaterial =
    ballastMaterialRequired(form.stBallastMethodCode) && form.stBallastMaterialCode.trim() === ''
  return needsMaterial ? { stBallastMaterialCode: SCH10_MESSAGES.materialCodeTypeRequired } : {}
}

/** Advisory validation for one road detail. */
export function validateRoadDetail(form: RoadDetailFormValues): RoadDetailErrors {
  const errors: RoadDetailErrors = {}

  Object.assign(errors, roadNameError(form), ballastErrors(form))

  for (const [key, message] of REQUIRED_ROAD_DETAIL_FIELDS) {
    if (form[key].trim() === '') {
      errors[key] = message
    }
  }

  for (const [key, kind, bounds, message] of ROAD_DETAIL_NUMERICS) {
    const check = kind === 'cost' ? optionalCost : optionalRange
    const issue = check(form[key], bounds, message)
    if (issue) {
      errors[key] = issue
    }
  }

  const comments = form.comments.trim()
  if (comments.length > COMMENTS_MAX || utf8Length(comments) > COMMENTS_MAX_BYTES) {
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

/**
 * Additional Stabilizing, with ballast method `N` mirrored rather than left to the server.
 *
 * On `N` the server forces the four dimensions, `actualCost` and `otherTransfer` to zero and the
 * material to `NA` — but NOT `ttTransfer`, which is stored as submitted. Sending what the reporter
 * typed and letting the server silently overwrite it meant the echo differed from the form with no
 * explanation; mirroring the coercion means the value the reporter sees is the value that persists.
 * `D` coerces the material only, which is the server's own affair and is left to it.
 */
const buildStabilizing = (form: RoadDetailFormValues): StabilizingRequest => {
  const method = form.stBallastMethodCode.trim()
  // BOTH `N` and `D` have the material forced to `NA` server-side
  // (`Schedule10Service.java:557-560`), so both must send it. Mirroring only `N` left `D` with the
  // exact echo mismatch the mirroring exists to prevent: the combo is disabled on `D`, but the form
  // still held whatever material was picked BEFORE `D` was chosen, and that value was sent and then
  // silently replaced. Only the FIGURES are `N`-specific.
  const ballastMaterialCode = ballastForcesMaterialNa(method)
    ? BALLAST_MATERIAL_NA
    : blankToNull(form.stBallastMaterialCode)

  if (ballastZeroesFigures(method)) {
    return {
      ballastMethodCode: method,
      ballastMaterialCode,
      length: 0,
      surfaceWidth: 0,
      depth: 0,
      distanceToSource: 0,
      actualCost: 0,
      // Deliberately NOT zeroed — the server keeps this one on the `N` branch.
      ttTransfer: costOrNull(form.stTtTransfer),
      otherTransfer: 0,
    }
  }
  return {
    ballastMethodCode: method,
    ballastMaterialCode,
    length: numberOrNull(form.stLength),
    surfaceWidth: numberOrNull(form.stSurfaceWidth),
    depth: numberOrNull(form.stDepth),
    distanceToSource: numberOrNull(form.stDistanceToSource),
    actualCost: costOrNull(form.stActualCost),
    ttTransfer: costOrNull(form.stTtTransfer),
    otherTransfer: costOrNull(form.stOtherTransfer),
  }
}

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
