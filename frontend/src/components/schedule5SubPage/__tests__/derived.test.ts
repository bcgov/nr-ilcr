import type { SubPageRowForm } from '@/interfaces/Schedule5SubPage'
import { deriveSubPageTotals, rowCostPerVolume } from '@/components/schedule5SubPage/derived'

// Transcribed from `Schedule5Service.subPageTotals` (:1257-1290), whose own javadoc warns that the
// two pages are genuinely different shapes and must not be symmetrized (deviation (C)).

const row = (cost: string, rowId: number | null = 1): SubPageRowForm => ({
  rowId,
  description: 'Row',
  cost,
})

describe('rowCostPerVolume', () => {
  test('the entered cost over the stamped camp volume', () => {
    expect(rowCostPerVolume(row('24000'), 80000)).toBe(0.3)
    expect(rowCostPerVolume(row('50000'), 1000)).toBe(50)
  })

  test('blank when the cost is absent or the stamped volume is missing/zero', () => {
    expect(rowCostPerVolume(row(''), 80000)).toBeNull()
    expect(rowCostPerVolume(row('24000'), null)).toBeNull()
    expect(rowCostPerVolume(row('24000'), 0)).toBeNull()
  })

  test('the legacy scale-2 rule', () => {
    // 200000/30000 = 6.66666… -> 6.67, not the scale-4 rule's 6.6667.
    expect(rowCostPerVolume(row('200000'), 30000)).toBe(6.67)
  })
})

describe('deriveSubPageTotals — ACCESS (cost only, single camp volume)', () => {
  test('sums the entered costs; volume is the single camp volume', () => {
    const totals = deriveSubPageTotals('ACCESS', [row('3000', 1), row('2000', 2)], 60000)
    expect(totals.cost).toBe(5000)
    expect(totals.volume).toBe(60000) // NOT n x volume
    expect(totals.costPerVolume).toBe(0.08) // 5000/60000 = 0.08333… -> 0.08
  })

  test('an all-null-cost list yields a null cost whatever the volume', () => {
    const totals = deriveSubPageTotals('ACCESS', [row(''), row('')], 60000)
    expect(totals.cost).toBeNull()
    expect(totals.costPerVolume).toBeNull()
    // ...but the volume is set unconditionally, including on an empty list.
    expect(totals.volume).toBe(60000)
    expect(deriveSubPageTotals('ACCESS', [], 60000).volume).toBe(60000)
  })
})

describe('deriveSubPageTotals — CAMP (sums cost AND volume)', () => {
  test('volume is n x the stamped volume, not the single value', () => {
    const totals = deriveSubPageTotals('CAMP', [row('10000', 1), row('14000', 2)], 80000)
    expect(totals.cost).toBe(24000)
    expect(totals.volume).toBe(160000) // 2 rows x 80,000
    expect(totals.costPerVolume).toBe(0.15) // 24000/160000
  })

  test('an all-null-cost list still contributes when the stamped volume is present — 0, not null', () => {
    // The CAMP flag trips on a null cost whenever the volume is non-null (7.1 deviation (h)/(L)),
    // and that zero then propagates into the camp panel's Sub-Total.
    const totals = deriveSubPageTotals('CAMP', [row(''), row('')], 80000)
    expect(totals.cost).toBe(0)
    expect(totals.volume).toBe(160000)
  })

  test('nothing contributes at all -> the whole triple is null', () => {
    // No rows, or rows with no cost and no stamped volume.
    expect(deriveSubPageTotals('CAMP', [], 80000)).toEqual({
      volume: null,
      cost: null,
      costPerVolume: null,
    })
    expect(deriveSubPageTotals('CAMP', [row('')], null)).toEqual({
      volume: null,
      cost: null,
      costPerVolume: null,
    })
  })

  test('a null stamped volume with a real cost totals volume 0, not null', () => {
    // Legacy starts that accumulator at zero and only adds non-null row volumes.
    const totals = deriveSubPageTotals('CAMP', [row('24000')], null)
    expect(totals.cost).toBe(24000)
    expect(totals.volume).toBe(0)
    expect(totals.costPerVolume).toBeNull() // no divide-by-zero
  })
})

describe('deriveSubPageTotals — the two pages must not be symmetrized', () => {
  test('the same rows and volume give DIFFERENT footers per page', () => {
    const rows = [row('10000', 1), row('14000', 2)]
    const camp = deriveSubPageTotals('CAMP', rows, 80000)
    const access = deriveSubPageTotals('ACCESS', rows, 80000)

    expect(camp.cost).toBe(access.cost) // the cost side agrees
    expect(camp.volume).toBe(160000) // ...the volume side does not
    expect(access.volume).toBe(80000)
    expect(camp.costPerVolume).not.toBe(access.costPerVolume)
  })

  test('and they disagree on an all-null-cost list too', () => {
    expect(deriveSubPageTotals('CAMP', [row('')], 80000).cost).toBe(0)
    expect(deriveSubPageTotals('ACCESS', [row('')], 80000).cost).toBeNull()
  })
})

describe('deriveSubPageTotals — entry forms', () => {
  test('accepts the grouped strings the cost cells display', () => {
    expect(deriveSubPageTotals('ACCESS', [row('24,000')], 60000).cost).toBe(24000)
  })

  test('non-finite entry is treated as absent', () => {
    expect(deriveSubPageTotals('ACCESS', [row('Infinity')], 60000).cost).toBeNull()
  })
})
