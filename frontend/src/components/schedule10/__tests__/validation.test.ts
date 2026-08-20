import { describe, expect, test } from 'vitest'
import type { ConstructionPage, RoadDetail } from '@/interfaces/Schedule10Response'
import {
  BALLAST_ZEROED_FIELDS,
  MASK_DIGITS,
  SCH10_MESSAGES,
  ballastForcesMaterialNa,
  ballastMaterialRequired,
  ballastZeroesFigures,
  buildPageBody,
  buildRoadDetailBody,
  emptyPageForm,
  emptyRoadDetailForm,
  formFromPage,
  formFromRoadDetail,
  isTflLocated,
  previewCostPerVolumePerLength,
  previewMaterialTotal,
  previewStabilizingCostPerLength,
  previewSubGradeCostPerLength,
  previewSubGradeTotal,
  supplyBlocksFor,
  validatePage,
  validateRoadDetail,
} from '../validation'

const tsaPage: ConstructionPage = {
  pageId: 8900,
  pageNumber: 1,
  pageLabel: 'Page 1, Period: 2021-06, TSA: 01, SB: 01A, TFL:-',
  forestRegionCode: 'RNI',
  tsaNumber: '01',
  tsbNumberCode: '01A',
  tflNumberCode: null,
  roadGroup: '11',
  divisionName: 'North Division',
  constructionPeriod: '2021-06',
  roadDetailCount: 1,
  revisionCount: 0,
  roadDetails: [],
}

const tflPage: ConstructionPage = {
  ...tsaPage,
  pageId: 8902,
  tsaNumber: null,
  tsbNumberCode: null,
  tflNumberCode: '08',
  roadGroup: null,
}

const detail: RoadDetail = {
  roadDetailId: 8910,
  rowNumber: 1,
  roadDetailLabel: 'Road #1, Mainline A',
  roadName: 'Mainline A',
  roadLifetimeCode: 'P',
  becClassification: {
    biogeoclimaticCatalogueId: 8801,
    becZoneCode: 'ICH',
    subzone: 'dw',
    variant: '1',
    phase: null,
    label: 'ICHdw1',
  },
  relSoilMoistRgmClsCode: '1',
  sideSlopePct: 25,
  subGrade: {
    // A length stored as 12.500 arrives as 12.5 once JSON.parse has run.
    length: 12.5,
    surfaceWidth: 6.5,
    actualCost: 150000,
    ttTransfer: -5000,
    otherTransfer: 2000,
    lessBridges: 1000,
    lessCulverts: 2000,
    lessLandings: 3000,
    lessOverland: 4000,
    lessOtherEng: 5000,
    lessEndHaul: 6000,
    totalCosts: 147000,
    totalDeductions: 21000,
    total: 126000,
    costPerLength: 10080,
  },
  stabilizing: {
    ballastMethodCode: 'C',
    ballastMaterialCode: 'GR',
    length: 3,
    surfaceWidth: 6.5,
    depth: 0.3,
    distanceToSource: 12.4,
    actualCost: 40000,
    ttTransfer: 2500,
    otherTransfer: -1500,
    total: 41000,
    costPerLength: 13666.67,
  },
  materialComposition: {
    solidRockPct: 10,
    rippableRockPct: 20,
    coarsePct: 40,
    finePct: 20,
    organicPct: 10,
    totalPct: 100,
  },
  detailedEngineeringCostInd: 'N',
  endHaulDistance: 2.5,
  endHaulVolume: 1200,
  overlandDistance: 1.5,
  overlandVolume: 800,
  comments: 'Seeded road detail',
  revisionCount: 0,
}

describe('location branch helpers', () => {
  test('recognises the TFL sentinel regardless of case', () => {
    expect(isTflLocated('TFL')).toBe(true)
    expect(isTflLocated('tfl')).toBe(true)
    expect(isTflLocated('01')).toBe(false)
    expect(isTflLocated('')).toBe(false)
  })

  test('narrows supply blocks to the chosen TSA', () => {
    const blocks = [
      { code: '01A', description: 'Block A' },
      { code: '01B', description: 'Block B' },
      { code: '16G', description: 'Block G' },
    ]
    expect(supplyBlocksFor(blocks, '01').map((b) => b.code)).toEqual(['01A', '01B'])
    expect(supplyBlocksFor(blocks, '16').map((b) => b.code)).toEqual(['16G'])
  })

  test('offers no supply blocks without a TSA, or on the TFL branch', () => {
    const blocks = [{ code: '01A', description: 'Block A' }]
    expect(supplyBlocksFor(blocks, '')).toEqual([])
    expect(supplyBlocksFor(blocks, 'TFL')).toEqual([])
  })

  test('keeps a stored block that does not belong to the stored TSA', () => {
    // Delivery holds pages on TSA 02 carrying block 01D, because the TSA leg was never validated.
    // Narrowing that away would blank a field that does hold a value.
    const blocks = [
      { code: '01D', description: 'Arrow TSA Block D' },
      { code: '02A', description: 'Boundary TSA Block A' },
      { code: '02B', description: 'Boundary TSA Block B' },
    ]
    const options = supplyBlocksFor(blocks, '02', '01D').map((b) => b.code)
    expect(options).toContain('01D')
    expect(options).toEqual(expect.arrayContaining(['02A', '02B']))
  })

  test('does not duplicate a stored block that already belongs to the TSA', () => {
    const blocks = [
      { code: '02A', description: 'Boundary TSA Block A' },
      { code: '02B', description: 'Boundary TSA Block B' },
    ]
    expect(supplyBlocksFor(blocks, '02', '02A').map((b) => b.code)).toEqual(['02A', '02B'])
  })

  test('keeps a stored block even with no TSA chosen, so it is never hidden', () => {
    const blocks = [{ code: '01D', description: 'Arrow TSA Block D' }]
    expect(supplyBlocksFor(blocks, '', '01D').map((b) => b.code)).toEqual(['01D'])
  })
})

describe('ballast method coupling', () => {
  test('requires a material for C, and for a blank code', () => {
    expect(ballastMaterialRequired('C')).toBe(true)
    expect(ballastMaterialRequired('')).toBe(true)
    expect(ballastMaterialRequired('N')).toBe(false)
    expect(ballastMaterialRequired('D')).toBe(false)
  })

  test('identifies the method whose figures the server zeroes', () => {
    expect(ballastZeroesFigures('N')).toBe(true)
    expect(ballastZeroesFigures('C')).toBe(false)
  })
})

describe('seeding a page form', () => {
  test('seeds a TSA page with its supply block', () => {
    const form = formFromPage(tsaPage)
    expect(form.tsaOrTfl).toBe('01')
    expect(form.supplyBlock).toBe('01A')
    expect(form.tflNumberCode).toBe('')
  })

  test('seeds a TFL page with the sentinel and no supply block', () => {
    const form = formFromPage(tflPage)
    expect(form.tsaOrTfl).toBe('TFL')
    expect(form.tflNumberCode).toBe('08')
    expect(form.supplyBlock).toBe('')
  })
})

describe('seeding a road-detail form through the display masks', () => {
  test('restores the decimal places JSON.parse dropped', () => {
    const form = formFromRoadDetail(detail)
    expect(form.sgLength).toBe('12.500')
    expect(form.stLength).toBe('3.000')
    expect(form.sgSurfaceWidth).toBe('6.5')
    expect(form.stDepth).toBe('0.3')
    expect(form.endHaulDistance).toBe('2.5')
  })

  test('groups whole-number costs and volumes', () => {
    const form = formFromRoadDetail(detail)
    expect(form.sgActualCost).toBe('150,000')
    expect(form.endHaulVolume).toBe('1,200')
  })

  test('seeds the BEC catalogue id and defaults the engineering indicator', () => {
    const form = formFromRoadDetail(detail)
    expect(form.becbiogeoCatalogueId).toBe('8801')
    expect(form.detailedEngineeringCostInd).toBe('N')
  })

  test('seeds an absent value as blank rather than zero', () => {
    const bare: RoadDetail = {
      ...detail,
      sideSlopePct: null,
      subGrade: { ...detail.subGrade, actualCost: null, length: null },
      comments: null,
    }
    const form = formFromRoadDetail(bare)
    expect(form.sideSlopePct).toBe('')
    expect(form.sgActualCost).toBe('')
    expect(form.sgLength).toBe('')
    expect(form.comments).toBe('')
  })

  test('every masked field is a real form field, and every numeric form field is masked', () => {
    // The original assertion checked the CONVERSE of its name — that each mask key exists on the
    // form — which cannot catch the failure that matters: a numeric field with no declared mask,
    // which then renders at whatever scale JSON.parse happened to leave (Trap 2).
    const form = emptyRoadDetailForm()
    const masked = new Set<string>(Object.keys(MASK_DIGITS))
    for (const key of masked) {
      expect(form).toHaveProperty(key)
    }
    // Every field the road form holds that is not one of these text/code fields must carry a mask.
    const unmasked = new Set([
      'roadName',
      'roadLifetimeCode',
      'becbiogeoCatalogueId',
      'becbiogeoLabel',
      'relSoilMoistRgmClsCode',
      'stBallastMethodCode',
      'stBallastMaterialCode',
      'detailedEngineeringCostInd',
      'comments',
    ])
    for (const key of Object.keys(form)) {
      expect(masked.has(key) || unmasked.has(key)).toBe(true)
    }
  })
})

describe('validatePage', () => {
  test('accepts a complete page', () => {
    expect(validatePage(formFromPage(tsaPage))).toEqual({})
  })

  test('requires region and the TSA-or-TFL selection', () => {
    const errors = validatePage(emptyPageForm())
    expect(errors.forestRegionCode).toBe(SCH10_MESSAGES.regionRequired)
    expect(errors.tsaOrTfl).toBe(SCH10_MESSAGES.tsaOrTflRequired)
  })

  test('caps the division at twenty characters', () => {
    const ok = { ...formFromPage(tsaPage), divisionName: 'x'.repeat(20) }
    expect(validatePage(ok).divisionName).toBeUndefined()
    const tooLong = { ...formFromPage(tsaPage), divisionName: 'x'.repeat(21) }
    expect(validatePage(tooLong).divisionName).toBe(SCH10_MESSAGES.divisionMaxLength)
  })

  test('counts the division cap in bytes, not UTF-16 units', () => {
    const multibyte = { ...formFromPage(tsaPage), divisionName: 'é'.repeat(15) }
    expect(validatePage(multibyte).divisionName).toBe(SCH10_MESSAGES.divisionMaxLength)
  })

  test.each([
    ['2021-01', true],
    ['2021-12', true],
    ['2021-00', false],
    ['2021-13', false],
    ['2021-1', false],
    ['0000-00', false],
  ])('period %s is valid: %s', (period, valid) => {
    const errors = validatePage({ ...formFromPage(tsaPage), constructionPeriod: period })
    expect(errors.constructionPeriod === undefined).toBe(valid)
  })

  test('a blank period passes', () => {
    expect(
      validatePage({ ...formFromPage(tsaPage), constructionPeriod: '' }).constructionPeriod,
    ).toBeUndefined()
  })
})

describe('validateRoadDetail required fields', () => {
  test('names each missing required field with its own message', () => {
    const errors = validateRoadDetail(emptyRoadDetailForm())
    expect(errors.roadName).toBe(SCH10_MESSAGES.roadNameRequired)
    expect(errors.roadLifetimeCode).toBe(SCH10_MESSAGES.roadTypeRequired)
    expect(errors.becbiogeoCatalogueId).toBe(SCH10_MESSAGES.becZoneRequired)
    expect(errors.relSoilMoistRgmClsCode).toBe(SCH10_MESSAGES.rsmrClassRequired)
    expect(errors.stBallastMethodCode).toBe(SCH10_MESSAGES.ballastMethodRequired)
  })

  test('requires a material type when the method is C', () => {
    const form = { ...formFromRoadDetail(detail), stBallastMaterialCode: '' }
    expect(validateRoadDetail(form).stBallastMaterialCode).toBe(
      SCH10_MESSAGES.materialCodeTypeRequired,
    )
  })

  test('does not require a material type for method D', () => {
    const form = {
      ...formFromRoadDetail(detail),
      stBallastMethodCode: 'D',
      stBallastMaterialCode: '',
    }
    expect(validateRoadDetail(form).stBallastMaterialCode).toBeUndefined()
  })

  test('accepts a fully populated detail', () => {
    expect(validateRoadDetail(formFromRoadDetail(detail))).toEqual({})
  })
})

describe('validateRoadDetail ranges, pinned at both bounds', () => {
  const base = () => formFromRoadDetail(detail)

  test.each([
    ['sgLength', '0', '100', '100.001', SCH10_MESSAGES.rangeZeroToOneHundred],
    ['sgSurfaceWidth', '0', '999.9', '1000', SCH10_MESSAGES.rangeZeroTo999Point9],
    ['stLength', '0', '999.999', '1000', SCH10_MESSAGES.rangeZeroTo999Point999],
    ['stDepth', '0', '99.9', '100', SCH10_MESSAGES.rangeZeroTo99Point9],
    ['stDistanceToSource', '0', '999.9', '1000', SCH10_MESSAGES.rangeZeroTo999Point9],
  ] as const)('%s accepts its bounds and rejects beyond', (key, min, max, over, message) => {
    expect(validateRoadDetail({ ...base(), [key]: min })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: max })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: over })[key]).toBe(message)
  })

  test('sub-grade length stops at 100 while stabilizing length allows 999.999', () => {
    expect(validateRoadDetail({ ...base(), sgLength: '101' }).sgLength).toBe(
      SCH10_MESSAGES.rangeZeroToOneHundred,
    )
    expect(validateRoadDetail({ ...base(), stLength: '101' }).stLength).toBeUndefined()
  })

  test.each(['endHaulDistance', 'overlandDistance'] as const)(
    '%s spans the negative haul range',
    (key) => {
      expect(validateRoadDetail({ ...base(), [key]: '-9999.9' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '9999.9' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '-10000' })[key]).toBe(
        SCH10_MESSAGES.rangeHaulDistance,
      )
      // The upper bound was unpinned: raising HAUL_DISTANCE.max left the suite green.
      expect(validateRoadDetail({ ...base(), [key]: '10000' })[key]).toBe(
        SCH10_MESSAGES.rangeHaulDistance,
      )
    },
  )

  test.each(['sgActualCost', 'lessBridges', 'stActualCost'] as const)(
    '%s is a non-negative cost',
    (key) => {
      expect(validateRoadDetail({ ...base(), [key]: '0' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '9999999' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '-1' })[key]).toBe(
        SCH10_MESSAGES.costNonNegative,
      )
      expect(validateRoadDetail({ ...base(), [key]: '10000000' })[key]).toBe(
        SCH10_MESSAGES.costNonNegative,
      )
    },
  )

  test.each(['sgTtTransfer', 'stOtherTransfer'] as const)('%s allows negatives', (key) => {
    expect(validateRoadDetail({ ...base(), [key]: '-9999999' })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: '9999999' })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: '-10000000' })[key]).toBe(
      SCH10_MESSAGES.costTransfer,
    )
    // The upper bound was unpinned: raising TRANSFER.max left the suite green.
    expect(validateRoadDetail({ ...base(), [key]: '10000000' })[key]).toBe(
      SCH10_MESSAGES.costTransfer,
    )
  })

  test.each(['endHaulVolume', 'overlandVolume'] as const)('%s is a bounded volume', (key) => {
    expect(validateRoadDetail({ ...base(), [key]: '0' })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: '9999999' })[key]).toBeUndefined()
    expect(validateRoadDetail({ ...base(), [key]: '10000000' })[key]).toBe(
      SCH10_MESSAGES.volumeRange,
    )
    // The lower bound was unpinned: a volume is never negative.
    expect(validateRoadDetail({ ...base(), [key]: '-1' })[key]).toBe(SCH10_MESSAGES.volumeRange)
  })

  test.each(['solidRockPct', 'rippableRockPct', 'coarsePct', 'finePct', 'organicPct'] as const)(
    '%s is a percentage pinned at both bounds',
    (key) => {
      expect(validateRoadDetail({ ...base(), [key]: '0' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '100' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '101' })[key]).toBe(
        SCH10_MESSAGES.percentageRange,
      )
      // The lower bound was unpinned: a composition percentage is never negative.
      expect(validateRoadDetail({ ...base(), [key]: '-1' })[key]).toBe(
        SCH10_MESSAGES.percentageRange,
      )
    },
  )

  test('side slope is pinned at both bounds under its own message', () => {
    expect(validateRoadDetail({ ...base(), sideSlopePct: '0' }).sideSlopePct).toBeUndefined()
    expect(validateRoadDetail({ ...base(), sideSlopePct: '100' }).sideSlopePct).toBeUndefined()
    expect(validateRoadDetail({ ...base(), sideSlopePct: '-1' }).sideSlopePct).toBe(
      SCH10_MESSAGES.sideSlopeRange,
    )
  })

  test('the surface widths and the remaining dimensions are pinned at both bounds', () => {
    // `stSurfaceWidth` had no test of its own at all, despite being the field AC8 auto-copies into.
    for (const key of ['sgSurfaceWidth', 'stSurfaceWidth'] as const) {
      expect(validateRoadDetail({ ...base(), [key]: '0' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '999.9' })[key]).toBeUndefined()
      expect(validateRoadDetail({ ...base(), [key]: '1000' })[key]).toBe(
        SCH10_MESSAGES.rangeZeroTo999Point9,
      )
      expect(validateRoadDetail({ ...base(), [key]: '-0.1' })[key]).toBe(
        SCH10_MESSAGES.rangeZeroTo999Point9,
      )
    }
    expect(validateRoadDetail({ ...base(), stDepth: '99.9' }).stDepth).toBeUndefined()
    expect(validateRoadDetail({ ...base(), stDepth: '100' }).stDepth).toBe(
      SCH10_MESSAGES.rangeZeroTo99Point9,
    )
    expect(validateRoadDetail({ ...base(), stDepth: '-0.1' }).stDepth).toBe(
      SCH10_MESSAGES.rangeZeroTo99Point9,
    )
    expect(
      validateRoadDetail({ ...base(), stDistanceToSource: '999.9' }).stDistanceToSource,
    ).toBeUndefined()
    expect(validateRoadDetail({ ...base(), stDistanceToSource: '1000' }).stDistanceToSource).toBe(
      SCH10_MESSAGES.rangeZeroTo999Point9,
    )
    expect(validateRoadDetail({ ...base(), sgLength: '0' }).sgLength).toBeUndefined()
    expect(validateRoadDetail({ ...base(), sgLength: '100' }).sgLength).toBeUndefined()
    expect(validateRoadDetail({ ...base(), sgLength: '-0.1' }).sgLength).toBe(
      SCH10_MESSAGES.rangeZeroToOneHundred,
    )
    expect(validateRoadDetail({ ...base(), stLength: '999.999' }).stLength).toBeUndefined()
    expect(validateRoadDetail({ ...base(), stLength: '1000' }).stLength).toBe(
      SCH10_MESSAGES.rangeZeroTo999Point999,
    )
  })

  test('caps the road name at 30 characters, under the required-field message', () => {
    // ROAD_NAME_MAX was untested. The server answers an over-length name with the required text,
    // not the off-list text (review M8).
    expect(validateRoadDetail({ ...base(), roadName: 'x'.repeat(30) }).roadName).toBeUndefined()
    expect(validateRoadDetail({ ...base(), roadName: 'x'.repeat(31) }).roadName).toBe(
      SCH10_MESSAGES.roadNameRequired,
    )
    expect(validateRoadDetail({ ...base(), roadName: 'x'.repeat(31) }).roadName).not.toBe(
      SCH10_MESSAGES.invalidCodeValue,
    )
  })

  test('side slope carries its own message', () => {
    expect(validateRoadDetail({ ...base(), sideSlopePct: '101' }).sideSlopePct).toBe(
      SCH10_MESSAGES.sideSlopeRange,
    )
  })

  test('a cost that rounds out of range is rejected on the rounded value', () => {
    expect(validateRoadDetail({ ...base(), sgActualCost: '9999999.6' }).sgActualCost).toBe(
      SCH10_MESSAGES.costNonNegative,
    )
  })

  test('caps comments at 3500 characters', () => {
    expect(validateRoadDetail({ ...base(), comments: 'x'.repeat(3500) }).comments).toBeUndefined()
    expect(validateRoadDetail({ ...base(), comments: 'x'.repeat(3501) }).comments).toBe(
      SCH10_MESSAGES.commentsMaxLength,
    )
  })

  test('blank optional values pass', () => {
    const blanked = {
      ...base(),
      sgLength: '',
      sgActualCost: '',
      sideSlopePct: '',
      endHaulVolume: '',
    }
    const errors = validateRoadDetail(blanked)
    expect(errors.sgLength).toBeUndefined()
    expect(errors.sgActualCost).toBeUndefined()
    expect(errors.sideSlopePct).toBeUndefined()
    expect(errors.endHaulVolume).toBeUndefined()
  })

  test('rejects grouped-but-malformed numeric text the strict parser refuses', () => {
    expect(validateRoadDetail({ ...base(), sgActualCost: '1,00' }).sgActualCost).toBe(
      SCH10_MESSAGES.costNonNegative,
    )
    expect(validateRoadDetail({ ...base(), sgActualCost: '1e2' }).sgActualCost).toBe(
      SCH10_MESSAGES.costNonNegative,
    )
    expect(validateRoadDetail({ ...base(), sgActualCost: '1,000' }).sgActualCost).toBeUndefined()
  })
})

describe('buildPageBody', () => {
  test('sends the TSA branch and clears the TFL number', () => {
    const body = buildPageBody(formFromPage(tsaPage))
    expect(body.tsaOrTfl).toBe('01')
    expect(body.supplyBlock).toBe('01A')
    expect(body.tflNumberCode).toBeNull()
  })

  test('sends the TFL branch and clears the supply block', () => {
    const body = buildPageBody(formFromPage(tflPage))
    expect(body.tsaOrTfl).toBe('TFL')
    expect(body.tflNumberCode).toBe('08')
    expect(body.supplyBlock).toBeNull()
  })

  test('normalises a lower-case sentinel so it takes the TFL branch', () => {
    const body = buildPageBody({ ...formFromPage(tflPage), tsaOrTfl: 'tfl' })
    expect(body.tsaOrTfl).toBe('TFL')
  })

  test('omits the lock token on create and carries it on update', () => {
    expect(buildPageBody(formFromPage(tsaPage)).revisionCount).toBeUndefined()
    expect(buildPageBody(formFromPage(tsaPage), 0).revisionCount).toBe(0)
    expect(buildPageBody(formFromPage(tsaPage), 4).revisionCount).toBe(4)
  })

  test('sends blank optional text as null', () => {
    const body = buildPageBody({ ...formFromPage(tsaPage), divisionName: '  ' })
    expect(body.divisionName).toBeNull()
  })
})

describe('buildRoadDetailBody', () => {
  test('sends every substructure whole', () => {
    const body = buildRoadDetailBody(formFromRoadDetail(detail))
    expect(body.roadName).toBe('Mainline A')
    expect(body.becbiogeoCatalogueId).toBe(8801)
    expect(body.subGrade?.actualCost).toBe(150000)
    expect(body.subGrade?.lessBridges).toBe(1000)
    expect(body.stabilizing.ballastMethodCode).toBe('C')
    expect(body.materialComposition?.solidRockPct).toBe(10)
  })

  test('rounds a fractional cost half away from zero', () => {
    const body = buildRoadDetailBody({ ...formFromRoadDetail(detail), sgActualCost: '1.5' })
    expect(body.subGrade?.actualCost).toBe(2)
  })

  test('keeps dimension decimals rather than rounding them to whole numbers', () => {
    const body = buildRoadDetailBody(formFromRoadDetail(detail))
    expect(body.subGrade?.length).toBe(12.5)
    expect(body.stabilizing.depth).toBe(0.3)
  })

  test('sends blanks as null so a cleared field clears in place', () => {
    const body = buildRoadDetailBody({
      ...formFromRoadDetail(detail),
      sgActualCost: '',
      comments: '',
    })
    expect(body.subGrade?.actualCost).toBeNull()
    expect(body.comments).toBeNull()
  })

  test('never sends a derived total', () => {
    const body = buildRoadDetailBody(formFromRoadDetail(detail))
    expect(body.subGrade).not.toHaveProperty('total')
    expect(body.subGrade).not.toHaveProperty('totalCosts')
    expect(body.stabilizing).not.toHaveProperty('costPerLength')
    expect(body.materialComposition).not.toHaveProperty('totalPct')
  })

  test('omits the lock token on create and carries it on update', () => {
    expect(buildRoadDetailBody(formFromRoadDetail(detail)).revisionCount).toBeUndefined()
    expect(buildRoadDetailBody(formFromRoadDetail(detail), 0).revisionCount).toBe(0)
  })
})

describe('derived previews', () => {
  const form = formFromRoadDetail(detail)

  test('sub-grade total is costs less deductions', () => {
    expect(previewSubGradeTotal(form)).toBe(126000)
  })

  test('cost per length divides by the entered length', () => {
    expect(previewSubGradeCostPerLength(form)).toBe(10080)
  })

  test('cost per length is blank when the length is zero or absent', () => {
    expect(previewSubGradeCostPerLength({ ...form, sgLength: '0' })).toBeNull()
    expect(previewSubGradeCostPerLength({ ...form, sgLength: '' })).toBeNull()
    expect(previewStabilizingCostPerLength({ ...form, stLength: '' })).toBeNull()
  })

  test('an absent cost counts as zero inside a total', () => {
    const blank = { ...emptyRoadDetailForm() }
    expect(previewSubGradeTotal(blank)).toBe(0)
    expect(previewMaterialTotal(blank)).toBe(0)
  })

  test('material total sums the five percentages', () => {
    expect(previewMaterialTotal(form)).toBe(100)
  })

  test('the haul rate divides deduction by volume then distance', () => {
    expect(previewCostPerVolumePerLength('6000', '1200', '2.5')).toBe(2)
  })

  test('the haul rate is blank when a divisor is zero or absent', () => {
    expect(previewCostPerVolumePerLength('6000', '0', '2.5')).toBeNull()
    expect(previewCostPerVolumePerLength('6000', '1200', '')).toBeNull()
    expect(previewCostPerVolumePerLength('', '1200', '2.5')).toBeNull()
  })
})

describe('fixes from the 2026-08-19 code review', () => {
  const base = () => formFromRoadDetail(detail)

  test('H2 — a supply block absent from the catalogue is synthesised as its own option', () => {
    const served = [
      { code: '01A', description: 'Arrow TSA Block A' },
      { code: '16G', description: 'Lakes TSA Block G' },
    ]
    // Delivery page 8904 stores TSB `16Z`, which the code table no longer serves. Filtering the
    // served list for it yielded [] and the field rendered blank over a real value.
    const options = supplyBlocksFor(served, '16', '16Z')
    expect(options.map((o) => o.code)).toContain('16Z')
    expect(options.find((o) => o.code === '16Z')?.description).toBe('16Z')

    // Same on the TFL/no-TSA branch, where only the stored option is offered at all.
    expect(supplyBlocksFor(served, '', '16Z').map((o) => o.code)).toEqual(['16Z'])
    expect(supplyBlocksFor(served, 'TFL', '16Z').map((o) => o.code)).toEqual(['16Z'])
  })

  test('H2 — a served block is not duplicated when it is also the stored one', () => {
    const served = [{ code: '01A', description: 'Arrow TSA Block A' }]
    expect(supplyBlocksFor(served, '01', '01A').map((o) => o.code)).toEqual(['01A'])
  })

  test('M5 — a blank or non-numeric TFL is rejected on the TFL branch', () => {
    const tflForm = { ...emptyPageForm(), forestRegionCode: 'RNI', tsaOrTfl: 'TFL' }
    // The old over-length check was unreachable behind maxLength={2}; what actually reached the
    // server was a blank, which is the doomed round trip this module exists to stop.
    expect(validatePage({ ...tflForm, tflNumberCode: '' }).tflNumberCode).toBe(
      SCH10_MESSAGES.tflInvalid,
    )
    expect(validatePage({ ...tflForm, tflNumberCode: 'AB' }).tflNumberCode).toBe(
      SCH10_MESSAGES.tflInvalid,
    )
    expect(validatePage({ ...tflForm, tflNumberCode: '08' }).tflNumberCode).toBeUndefined()
    // On the TSA branch the field is cleared before sending, so it is never gated.
    expect(
      validatePage({ ...emptyPageForm(), forestRegionCode: 'RNI', tsaOrTfl: '01' }).tflNumberCode,
    ).toBeUndefined()
  })

  test('M6 — comments cap at 3500 characters but 4000 BYTES', () => {
    // The byte cap was checked against 3500, so any comment with accents or dashes in it was
    // blocked well under the real limit. An em dash is 3 bytes.
    const wide = '—'.repeat(1200) // 1200 chars, 3600 bytes
    expect(validateRoadDetail({ ...base(), comments: wide }).comments).toBeUndefined()
    // Over the BYTE cap, still under the character cap.
    expect(validateRoadDetail({ ...base(), comments: '—'.repeat(1400) }).comments).toBe(
      SCH10_MESSAGES.commentsMaxLength,
    )
  })

  test('P2 — ballast method N sends the coerced figures, keeping ttTransfer', () => {
    const form = {
      ...base(),
      stBallastMethodCode: 'N',
      stBallastMaterialCode: 'GR',
      stLength: '3',
      stSurfaceWidth: '6.5',
      stDepth: '0.3',
      stDistanceToSource: '12.4',
      stActualCost: '5000',
      stTtTransfer: '750',
      stOtherTransfer: '250',
    }
    expect(buildRoadDetailBody(form).stabilizing).toEqual({
      ballastMethodCode: 'N',
      ballastMaterialCode: 'NA',
      length: 0,
      surfaceWidth: 0,
      depth: 0,
      distanceToSource: 0,
      actualCost: 0,
      // Trap 8: the server does NOT zero this one on the `N` branch.
      ttTransfer: 750,
      otherTransfer: 0,
    })
  })

  test('P2 — any other method sends what was entered', () => {
    const form = { ...base(), stBallastMethodCode: 'C', stActualCost: '5000', stLength: '3' }
    expect(buildRoadDetailBody(form).stabilizing).toMatchObject({
      ballastMethodCode: 'C',
      length: 3,
      actualCost: 5000,
    })
  })

  test('review #325 — method D forces the material to NA but keeps every figure', () => {
    // The N mirroring originally covered only N, so D sent whatever material was picked before D was
    // chosen and the server (Schedule10Service:557-560) silently replaced it with NA -- the exact
    // echo mismatch the mirroring exists to prevent. D coerces the material ONLY; its figures are
    // stored as submitted.
    const form = {
      ...base(),
      stBallastMethodCode: 'D',
      stBallastMaterialCode: 'GR',
      stLength: '3',
      stSurfaceWidth: '6.5',
      stDepth: '0.3',
      stDistanceToSource: '12.4',
      stActualCost: '5000',
      stTtTransfer: '750',
      stOtherTransfer: '250',
    }
    expect(buildRoadDetailBody(form).stabilizing).toEqual({
      ballastMethodCode: 'D',
      ballastMaterialCode: 'NA',
      length: 3,
      surfaceWidth: 6.5,
      depth: 0.3,
      distanceToSource: 12.4,
      actualCost: 5000,
      ttTransfer: 750,
      otherTransfer: 250,
    })
  })

  test('review #325 — the zeroed-field set matches what the N branch actually sends', () => {
    // BALLAST_ZEROED_FIELDS drives which inputs the form DISABLES. If it drifts from what
    // buildStabilizing zeroes, the UI either disables a field that is still recorded or leaves one
    // editable whose entry is discarded -- the bug this review caught, in the other direction.
    const filled = {
      ...base(),
      stBallastMethodCode: 'N',
      stLength: '3',
      stSurfaceWidth: '6.5',
      stDepth: '0.3',
      stDistanceToSource: '12.4',
      stActualCost: '5000',
      stTtTransfer: '750',
      stOtherTransfer: '250',
    }
    const sent = buildRoadDetailBody(filled).stabilizing as Record<string, unknown>
    // Map each form key to the request field it lands in.
    const requestField: Record<string, string> = {
      stLength: 'length',
      stSurfaceWidth: 'surfaceWidth',
      stDepth: 'depth',
      stDistanceToSource: 'distanceToSource',
      stActualCost: 'actualCost',
      stOtherTransfer: 'otherTransfer',
      stTtTransfer: 'ttTransfer',
    }
    const zeroed = Object.keys(requestField).filter((key) => sent[requestField[key]] === 0)
    expect(zeroed.sort()).toEqual([...BALLAST_ZEROED_FIELDS].sort())
    // And the one deliberately left out is genuinely still carried.
    expect(BALLAST_ZEROED_FIELDS).not.toContain('stTtTransfer')
    expect(sent.ttTransfer).toBe(750)
  })

  test('P2 — both N and D force the material to NA in the UI', () => {
    expect(ballastForcesMaterialNa('N')).toBe(true)
    expect(ballastForcesMaterialNa('D')).toBe(true)
    expect(ballastForcesMaterialNa('C')).toBe(false)
    expect(ballastForcesMaterialNa('')).toBe(false)
    // Only `N` zeroes the figures.
    expect(ballastZeroesFigures('N')).toBe(true)
    expect(ballastZeroesFigures('D')).toBe(false)
  })

  test('M7 — the stored BEC label survives the round trip through the form', () => {
    const seeded = formFromRoadDetail(detail)
    expect(seeded.becbiogeoLabel).toBe('ICHdw1')
    // Display only: it never reaches the wire.
    expect(buildRoadDetailBody(seeded)).not.toHaveProperty('becbiogeoLabel')
  })
})

describe('the numeric rule table stays complete (SonarQube refactor guard)', () => {
  // validateRoadDetail was refactored from five near-identical loops into one declarative table to
  // bring its cognitive complexity under the gate. The risk a table introduces is a SILENTLY DROPPED
  // field -- it would simply stop being validated, with no error and no failing test.
  //
  // MASK_DIGITS is the independent list of every numeric on the road form (it exists to drive the
  // display mask). The two sets must agree exactly: a numeric with a mask but no bounds check is an
  // unvalidated field, and one with a check but no mask renders at whatever scale JSON.parse left.
  test('every masked numeric is range-checked, and every checked field is masked', () => {
    const form = emptyRoadDetailForm()
    // A field is bounds-checked if some out-of-range value produces an error for it. 1e12 is outside
    // every band in the module (the widest max is 9,999,999).
    const checked = new Set(
      Object.keys(MASK_DIGITS).filter(
        (key) =>
          validateRoadDetail({ ...form, [key]: '999999999999' })[
            key as keyof RoadDetailFormValues
          ] !== undefined,
      ),
    )
    expect([...checked].sort()).toEqual(Object.keys(MASK_DIGITS).sort())
  })

  test('the negative-capable fields are exactly the transfers and the haul distances', () => {
    const form = emptyRoadDetailForm()
    const acceptsNegative = Object.keys(MASK_DIGITS).filter(
      (key) =>
        validateRoadDetail({ ...form, [key]: '-1' })[key as keyof RoadDetailFormValues] ===
        undefined,
    )
    // Everything else is floored at 0. Losing a floor here is how a negative cost reaches the wire.
    expect(acceptsNegative.sort()).toEqual(
      [
        'endHaulDistance',
        'overlandDistance',
        'sgOtherTransfer',
        'sgTtTransfer',
        'stOtherTransfer',
        'stTtTransfer',
      ].sort(),
    )
  })
})
