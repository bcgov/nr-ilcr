import { describe, expect, it } from 'vitest'
import { validateSchedule2, VALIDATION_MESSAGES } from '../validation'

// Locks the Schedule-2-SPECIFIC advisory ranges — the parts that differ from Schedule 1:
// item-26 cost is widened to +/-999,999,999 (vs item-25's +/-99,999,999), and item-26 volume
// is min 0 (not signed). A regression that made both cost fields share one range, or allowed a
// negative volume, would slip past the page-level tests.
describe('validateSchedule2 ranges', () => {
  it('accepts the exact inclusive edges', () => {
    expect(
      validateSchedule2({
        purchasedLogCostCost: '99999999',
        lessLogSalesVolume: '9999999',
        lessLogSalesCost: '999999999',
      }),
    ).toEqual({})
    expect(
      validateSchedule2({
        purchasedLogCostCost: '-99999999',
        lessLogSalesVolume: '0',
        lessLogSalesCost: '-999999999',
      }),
    ).toEqual({})
  })

  it('applies DIFFERENT cost ranges to item 25 vs item 26', () => {
    // 500,000,000 is out of range for item-25 cost but IN range for the widened item-26 cost.
    const errors = validateSchedule2({
      purchasedLogCostCost: '500000000',
      lessLogSalesCost: '500000000',
    })
    expect(errors.purchasedLogCostCost).toBe(VALIDATION_MESSAGES.item25Cost)
    expect(errors.lessLogSalesCost).toBeUndefined()
  })

  it('rejects a negative item-26 volume (min is 0, not signed)', () => {
    expect(validateSchedule2({ lessLogSalesVolume: '-1' })).toEqual({
      lessLogSalesVolume: VALIDATION_MESSAGES.item26Volume,
    })
    expect(validateSchedule2({ lessLogSalesVolume: '10000000' })).toEqual({
      lessLogSalesVolume: VALIDATION_MESSAGES.item26Volume,
    })
  })

  it('treats blank as valid (no error) but flags non-numeric', () => {
    expect(validateSchedule2({ purchasedLogCostCost: '', lessLogSalesVolume: '  ' })).toEqual({})
    expect(validateSchedule2({ purchasedLogCostCost: 'abc' })).toEqual({
      purchasedLogCostCost: VALIDATION_MESSAGES.costInvalid,
    })
  })
})
