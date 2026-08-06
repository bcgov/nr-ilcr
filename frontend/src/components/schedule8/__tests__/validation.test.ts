import { describe, expect, test } from 'vitest'
import {
  emptyPageForm,
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

  test('Other% nonzero requires a skid type; NA counts as blank', () => {
    expect(validateSampleForm({ ...base(), otherSkiddingPct: '5' }).skidTypeCode).toBe(
      'Value Required',
    )
    expect(
      validateSampleForm({ ...base(), otherSkiddingPct: '5', skidTypeCode: 'na' }).skidTypeCode,
    ).toBe('Value Required')
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
