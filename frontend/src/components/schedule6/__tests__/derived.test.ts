import {
  EMPTY_RATE_INPUTS,
  rateInputsOf,
  recordCostPerVolume,
} from '@/components/schedule6/derived'
import type { RoadRecordFormValues } from '@/components/schedule6/validation'

// The 50.00 / 75.00 / zero-volume / absent-volume cases are transcribed from `Schedule6ServiceTest`;
// the scale-2, half-cent, grouped, negative and non-finite cases are client-only concerns computed
// here (narrowed 2026-08-21 after code review).
//
// Expected figures transcribed from `Schedule6ServiceTest` — `tsaRecord_derivesRmgFromSupplyBlock`
// (1000 / 50000 -> 50.00), `tflRecord_derivesRmgFromTfl` (400 / 30000 -> 75.00) and
// `zeroOrAbsentVolume_costPerVolumeNull` — so the mirror is pinned to the server's own arithmetic
// (defect #291 AC5).

describe('recordCostPerVolume', () => {
  test('divides cost by volume at the legacy scale-2 rule', () => {
    expect(recordCostPerVolume({ volume: '1000', cost: '50000' })).toBe(50)
    expect(recordCostPerVolume({ volume: '400', cost: '30000' })).toBe(75)
  })

  test('a zero or absent volume gives no rate, never Infinity', () => {
    expect(recordCostPerVolume({ volume: '0', cost: '5000' })).toBeNull()
    expect(recordCostPerVolume({ volume: '', cost: '6000' })).toBeNull()
  })

  test('an absent cost gives no rate', () => {
    expect(recordCostPerVolume({ volume: '1000', cost: '' })).toBeNull()
    expect(recordCostPerVolume(EMPTY_RATE_INPUTS)).toBeNull()
  })

  test('scale 2, not Schedules 2/4 scale 4', () => {
    // 200000 / 30000 = 6.66666… -> 6.67 here; the scale-4 rule would give 6.6667.
    expect(recordCostPerVolume({ volume: '30000', cost: '200000' })).toBe(6.67)
  })

  test('an exact half-cent quotient rounds UP, as BigDecimal does', () => {
    // 3075 / 5000 is 0.615 in decimal; a float multiply-and-round would give 0.61.
    expect(recordCostPerVolume({ volume: '5000', cost: '3075' })).toBe(0.62)
  })

  test('accepts the grouped strings the fields display', () => {
    expect(recordCostPerVolume({ volume: '1,000', cost: '50,000' })).toBe(50)
  })

  test('a negative cost (a credit) yields a negative rate', () => {
    expect(recordCostPerVolume({ volume: '1000', cost: '-50000' })).toBe(-50)
  })

  test('non-finite entry is treated as absent rather than rendering as a symbol', () => {
    expect(recordCostPerVolume({ volume: '1000', cost: 'Infinity' })).toBeNull()
  })
})

describe('rateInputsOf', () => {
  test('takes only the two fields the rate depends on', () => {
    const form = {
      areaType: '01',
      supplyBlock: '01B',
      tflNumber: '',
      volume: '1000',
      cost: '50000',
      comments: 'note',
    } as RoadRecordFormValues
    expect(rateInputsOf(form)).toEqual({ volume: '1000', cost: '50000' })
  })
})
