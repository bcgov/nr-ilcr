import type { SubPageRowForm } from '@/interfaces/Schedule5SubPage'
import { deriveSubPageTotals, rowCostPerVolume } from '@/components/schedule5SubPage/derived'

// Transcribed from `Schedule5SubPageServiceTest` — the backend TEST, not just the service source
// (corrected 2026-08-21 after code review: the previous header cited the source while a dedicated
// backend test already pinned every one of these behaviours with concrete figures the frontend shared
// none of, so the two sides pinned the same rules on disjoint numbers). The service's own javadoc warns
// that the two pages are genuinely different shapes and must not be symmetrized (deviation (C)).

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
    // Schedule5SubPageServiceTest `accessRowsTotalOnTheSingleCampVolume`: 7000 + 3000 over 120,000.
    const totals = deriveSubPageTotals('ACCESS', [row('7000', 1), row('3000', 2)], 120000)
    expect(totals.cost).toBe(10000)
    expect(totals.volume).toBe(120000) // NOT n x volume
    expect(totals.costPerVolume).toBe(0.08) // 10000/120000 = 0.08333… -> 0.08
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
    // Schedule5SubPageServiceTest `threeRowsTotalTheSummedVolume`: 10000 + 2500 + 500 over
    // 3 x 120,000 = 360,000 -> 0.036111… -> 0.04.
    const totals = deriveSubPageTotals(
      'CAMP',
      [row('10000', 1), row('2500', 2), row('500', 3)],
      120000,
    )
    expect(totals.cost).toBe(13000)
    expect(totals.volume).toBe(360000)
    expect(totals.costPerVolume).toBe(0.04)
  })

  test("a row's own rate is its cost over the stamped volume", () => {
    // Schedule5SubPageServiceTest `everyRowVolumeIsTheCampVolume`: 10000 / 120000 -> 0.08.
    expect(rowCostPerVolume(row('10000'), 120000)).toBe(0.08)
  })

  test('an all-null-cost list still contributes when the stamped volume is present — 0, not null', () => {
    // Schedule5SubPageServiceTest `campSideServesZero`: ONE cost-free row at a 60,000 camp volume
    // yields cost 0 and volume 60,000. The flag trips on a non-null VOLUME as well as a non-null cost,
    // so the zero-initialised accumulator is returned rather than discarded (7.1 deviation (h)/(L)) —
    // and that zero then propagates into the camp panel's Sub-Total.
    const totals = deriveSubPageTotals('CAMP', [row('')], 60000)
    expect(totals.cost).toBe(0)
    expect(totals.volume).toBe(60000)
  })

  test('ACCESS is the mirror image on identical input — null, not 0', () => {
    // Schedule5SubPageServiceTest `accessSideStaysNull`: seeded identically to the case above; only
    // the service code differs. This is the seam the two pages must never be symmetrized across.
    expect(deriveSubPageTotals('ACCESS', [row('')], 60000).cost).toBeNull()
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
    // Both asserted as values: `.not.toBe` also passed if one side were null (code review 2026-08-21).
    expect(camp.costPerVolume).toBe(0.15) // 24000 / 160000
    expect(access.costPerVolume).toBe(0.3) // 24000 / 80000
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
