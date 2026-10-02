import { describe, expect, test } from 'vitest'
import {
  PAGE_BANNER,
  SAMPLE_BANNER,
  bannerEntries,
  emptyPageForm,
  isNumberText,
  rateBanner,
  emptyRateForm,
  emptySampleForm,
  fmt,
  isBlank,
  liveActualHarvested,
  numStr,
  skiddingTotal,
  toNum,
  validatePageForm,
  validateRateForm,
  validateSampleForm,
} from '../validation'

// Direct unit coverage for the advisory validation branches that the component-level tests reach
// only obliquely (per-field ranges, the Other-skid-type NA rule, description max, etc.).

describe('schedule8 validation helpers', () => {
  test('toNum trims, rejects blank and NaN, parses numbers', () => {
    expect(toNum('')).toBeNull()
    expect(toNum('   ')).toBeNull()
    expect(toNum('abc')).toBeNull()
    expect(toNum(' 12.5 ')).toBe(12.5)
  })

  test('numStr / fmt round-trip null and values', () => {
    expect(numStr(null)).toBe('')
    expect(numStr(undefined)).toBe('')
    expect(numStr(0)).toBe('0')
    expect(fmt(null)).toBe('—')
    expect(fmt(undefined)).toBe('—')
    expect(fmt(42)).toBe('42')
  })

  test('isBlank treats undefined and whitespace as blank', () => {
    expect(isBlank(undefined)).toBe(true)
    expect(isBlank('   ')).toBe(true)
    expect(isBlank('x')).toBe(false)
  })

  test('skiddingTotal sums the six percentages', () => {
    const form = { ...emptySampleForm(), groundBasePct: '60', grapplePct: '40' }
    expect(skiddingTotal(form)).toBe(100)
  })

  test('liveActualHarvested returns null only when both volumes blank', () => {
    expect(liveActualHarvested(emptySampleForm())).toBeNull()
    expect(liveActualHarvested({ ...emptySampleForm(), coniferousVolume: '1000' })).toBe(1000)
    expect(
      liveActualHarvested({
        ...emptySampleForm(),
        coniferousVolume: '1000',
        deciduousVolume: '500',
      }),
    ).toBe(1500)
  })
})

describe('validateSampleForm branch coverage', () => {
  const base = () => ({ ...emptySampleForm(), contractId: 'C-1' })

  test('an individual percentage out of range is flagged per field', () => {
    const errors = validateSampleForm({ ...base(), groundBasePct: '150' })
    expect(errors.groundBasePct).toBe('Entered percentage must be between 0 and 100.')
  })

  test('sum over 100 flags percentTotal', () => {
    const errors = validateSampleForm({ ...base(), groundBasePct: '60', grapplePct: '60' })
    expect(errors.percentTotal).toBe('Skidding/Yarding percentages can not total more than 100%.')
  })

  test('Helicopter% nonzero requires all four conditional fields', () => {
    const errors = validateSampleForm({ ...base(), helicopterPct: '10' })
    expect(errors.distance).toBe('Value Required')
    expect(errors.cycleTime).toBe('Value Required')
    expect(errors.uphillDirection).toBe('Value Required')
    expect(errors.waterDumpDestination).toBe('Value Required')
  })

  test('Helicopter% nonzero with all fields filled clears the conditional errors', () => {
    const errors = validateSampleForm({
      ...base(),
      helicopterPct: '10',
      distance: '500',
      cycleTime: '12',
      uphillDirection: 'Y',
      waterDumpDestination: 'N',
    })
    expect(errors.distance).toBeUndefined()
    expect(errors.waterDumpDestination).toBeUndefined()
  })

  // Re-grounded for #359 group C: legacy split the two (`schedule8EditDetail.xhtml:372-375`) —
  // blank fails `required`, NA fails `notApplicableTypeValidator` with its own text.
  test('Other% nonzero requires a skid type; NA fails with the not-applicable text', () => {
    expect(validateSampleForm({ ...base(), otherSkiddingPct: '5' }).skidTypeCode).toBe(
      'Value Required',
    )
    expect(
      validateSampleForm({ ...base(), otherSkiddingPct: '5', skidTypeCode: 'na' }).skidTypeCode,
    ).toBe('A valid value must be selected from the list.')
    expect(
      validateSampleForm({ ...base(), otherSkiddingPct: '5', skidTypeCode: 'Cable' }).skidTypeCode,
    ).toBeUndefined()
  })

  test('volume and original-rate ranges are enforced', () => {
    const errors = validateSampleForm({
      ...base(),
      coniferousVolume: '-1',
      deciduousVolume: '10000000',
      originalRate: '1000000',
    })
    expect(errors.coniferousVolume).toBe('Entered volume must be between 0 and 9,999,999.')
    expect(errors.deciduousVolume).toBe('Entered volume must be between 0 and 9,999,999.')
    expect(errors.originalRate).toBe('Entered rate must be between 0 and 999,999.99.')
  })

  test('a fully valid sample form produces no errors', () => {
    expect(validateSampleForm({ ...base(), groundBasePct: '100' })).toEqual({})
  })
})

describe('validateRateForm branch coverage', () => {
  test('all-blank form flags each required field', () => {
    const errors = validateRateForm(emptyRateForm())
    expect(errors.costItemCode).toBe('Value Required')
    expect(errors.costingRate).toBe('Value Required')
    expect(errors.costTypeCode).toBe('Value Required')
  })

  test('an out-of-range costing rate is flagged with the rate message', () => {
    const errors = validateRateForm({
      costItemCode: '82',
      costingRate: '99999999',
      costTypeCode: 'CT1',
      itemDescription: '',
    })
    expect(errors.costingRate).toBe('Entered rate must be between 0 and 9,999,999.99.')
  })

  test('a too-long description is flagged', () => {
    const errors = validateRateForm({
      costItemCode: '82',
      costingRate: '5',
      costTypeCode: 'CT1',
      itemDescription: 'x'.repeat(31),
    })
    expect(errors.itemDescription).toBe('Description can not exceed 30 characters.')
  })

  test('a valid rate form produces no errors', () => {
    expect(
      validateRateForm({
        costItemCode: '82',
        costingRate: '5',
        costTypeCode: 'CT1',
        itemDescription: 'Bridge',
      }),
    ).toEqual({})
  })
})

describe('validatePageForm phone validation', () => {
  // A page with all the required fields filled so only the phone rule can flag.
  const validBase = () => ({
    ...emptyPageForm(),
    license: 'LIC1',
    supportCentre: 'SC1',
    region: 'R1',
    becZone: 'BZ1',
    tsaNumber: 'TSA1',
  })

  test('phone is optional — blank produces no phone error', () => {
    expect(validatePageForm({ ...validBase(), phone: '' }).phone).toBeUndefined()
  })

  test('a partial phone is rejected', () => {
    expect(validatePageForm({ ...validBase(), phone: '250-555' }).phone).toBe(
      'Phone must be a complete 10-digit number (e.g. 250-555-1212).',
    )
  })

  test('a complete 10-digit phone passes', () => {
    expect(validatePageForm({ ...validBase(), phone: '250-555-1212' }).phone).toBeUndefined()
  })

  test('a 10-digit phone seeded without dashes still passes (matches the formatted display)', () => {
    expect(validatePageForm({ ...validBase(), phone: '4564564566' }).phone).toBeUndefined()
  })
})

// #359 group C change log (2026-10-02): the number boxes reject text with JSF's
// `NumberConverter.PATTERN` line, and the banner reads in legacy document order.
describe('legacy number-pattern and banner lines (#359 group C)', () => {
  const base = () => ({ ...emptySampleForm(), contractId: 'C-1' })

  test('isNumberText accepts decimals with grouping commas only', () => {
    for (const ok of ['0', '12', ' 12.5 ', '-3', '.5', '7.', '1,200', '1,234,567.5']) {
      expect(isNumberText(ok)).toBe(true)
    }
    for (const bad of ['', 'abc', '1e3', '0x10', 'Infinity', ',1', '1,', '12abc']) {
      expect(isNumberText(bad)).toBe(false)
    }
  })

  test('each sample number box rejects text with its legacy label', () => {
    const errors = validateSampleForm({
      ...base(),
      skylineSlopeDistance: 'asdf',
      skylineSupportNumber: 'b',
      supportAvgDistance: 'c',
      distance: 'd',
      cycleTime: ' e ',
      coniferousVolume: 'f',
      deciduousVolume: 'g',
      originalRate: 'h',
    })
    expect(errors).toMatchObject({
      skylineSlopeDistance: "Slope Distance: 'asdf' is not a number pattern.",
      skylineSupportNumber: "Support Number: 'b' is not a number pattern.",
      supportAvgDistance: "Support Avg Dist: 'c' is not a number pattern.",
      distance: "Distance: 'd' is not a number pattern.",
      cycleTime: "CycleTime: 'e' is not a number pattern.",
      coniferousVolume: "Coniferous: 'f' is not a number pattern.",
      deciduousVolume: "Deciduous: 'g' is not a number pattern.",
      originalRate: "Original TtT Rate: 'h' is not a number pattern.",
    })
  })

  test('a percentage holding text keeps the percentage text', () => {
    expect(validateSampleForm({ ...base(), grapplePct: 'x' }).grapplePct).toBe(
      'Entered percentage must be between 0 and 100.',
    )
  })

  test('a typed-but-not-numeric helicopter distance reports its conversion, not "required"', () => {
    const errors = validateSampleForm({ ...base(), helicopterPct: '10', distance: 'x' })
    expect(errors.distance).toBe("Distance: 'x' is not a number pattern.")
  })

  test('the $/m3 box rejects text with its legacy label', () => {
    expect(validateRateForm({ ...emptyRateForm(), costingRate: 'x' }).costingRate).toBe(
      "$/m3: 'x' is not a number pattern.",
    )
  })

  test('the TFL # on the TFL branch must be 1-2 digits; blank is left to Check Status', () => {
    const tfl = { ...emptyPageForm(), tsaNumber: 'TFL' }
    expect(validatePageForm({ ...tfl, tflNumber: 'x1' }).tflNumber).toBe(
      'Entered TFL number is not valid for Interior Regions.',
    )
    expect(validatePageForm({ ...tfl, tflNumber: '' }).tflNumber).toBeUndefined()
    expect(validatePageForm({ ...tfl, tflNumber: '18' }).tflNumber).toBeUndefined()
  })

  test('the page banner maps required fields to their legacy labels, in legacy order', () => {
    const lines = bannerEntries(PAGE_BANNER, validatePageForm({ ...emptyPageForm(), phone: '1' }))
    expect(lines.map((entry) => entry.line)).toEqual([
      'License: Value is required.',
      'Phone must be a complete 10-digit number (e.g. 250-555-1212).',
      'Support Centre: Value is required.',
      'Region: Value is required.',
      'BioGeoClimatic Zone: Value is required.',
      'TSA or TFL: Value is required.',
    ])
  })

  test('the sample banner carries the helicopter and Other lines with legacy labels', () => {
    const errors = validateSampleForm({
      ...emptySampleForm(),
      helicopterPct: '10',
      otherSkiddingPct: '5',
    })
    expect(bannerEntries(SAMPLE_BANNER, errors).map((entry) => entry.line)).toEqual([
      'Contract ID: Value is required.',
      'Distance: Value is required.',
      'CycleTime: Value is required.',
      'Direction: Value is required.',
      'Dump Destination: Value is required.',
      'Other: Value is required.',
    ])
  })

  test('the two rate forms keep their own blocks, Additions first', () => {
    const errors = validateRateForm(emptyRateForm())
    const deductions = bannerEntries(rateBanner('deduction'), errors)
    const additions = bannerEntries(rateBanner('addition'), errors)
    expect(
      [...deductions, ...additions].sort((a, b) => a.rank - b.rank).map((e) => e.line),
    ).toEqual([
      'Additions: Value is required.',
      '$/m3: Value is required.',
      'Cost type: Value is required.',
      'Deductions: Value is required.',
      '$/m3: Value is required.',
      'Cost type: Value is required.',
    ])
  })
})

describe('legacy ranges and grouping commas on the sample numbers (#359 group C review)', () => {
  const base = () => ({ ...emptySampleForm(), contractId: 'C-1' })

  test('the Skyline and Helicopter boxes enforce legacy’s f:validateDoubleRange limits', () => {
    const over = validateSampleForm({
      ...base(),
      skylineSlopeDistance: '100000',
      skylineSupportNumber: '10000',
      supportAvgDistance: '100000',
      distance: '1000000',
      cycleTime: '1000000',
    })
    expect(over).toMatchObject({
      skylineSlopeDistance: 'Entered distance must be between 0 and 99,999.',
      skylineSupportNumber: 'Entered number must be between 0 and 9,999.',
      supportAvgDistance: 'Entered distance must be between 0 and 99,999.9.',
      distance: 'Entered distance must be between 0 and 999,999.9.',
      cycleTime: 'Entered time must be between 0 and 999,999.9.',
    })
    const max = validateSampleForm({
      ...base(),
      skylineSlopeDistance: '99,999',
      skylineSupportNumber: '9,999',
      supportAvgDistance: '99,999.9',
      distance: '999,999.9',
      cycleTime: '0',
    })
    expect(max).toEqual({})
  })

  test('grouping commas parse as numbers', () => {
    expect(toNum('1,200')).toBe(1200)
    expect(validateSampleForm({ ...base(), coniferousVolume: '1,200' })).toEqual({})
    expect(
      validateRateForm({
        ...emptyRateForm(),
        costItemCode: '82',
        costingRate: '1,000.5',
        costTypeCode: 'CT1',
      }),
    ).toEqual({})
  })
})
