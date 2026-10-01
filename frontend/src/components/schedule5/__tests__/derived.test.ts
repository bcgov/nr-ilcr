import type { Camp } from '@/interfaces/Schedule5Response'
import type { CampFormValues, CategoryKey } from '@/components/schedule5/validation'
import { categoryRate, deriveSchedule5 } from '@/components/schedule5/derived'
import { CATEGORY_KEYS } from '@/components/schedule5/validation'

// The cascade figures below are transcribed from `Schedule5ServiceTest`; the per-category rate cases
// and the `categoryRate` block are client-only concerns computed here (narrowed 2026-08-21 after code
// review, which found the blanket "every figure is transcribed" claim overstated the suite).
// Transcribed from `Schedule5ServiceTest` — the `CampSubTotal`,
// `campTotal` and `AccessTotals` nests — so the mirror is pinned to the server's own arithmetic
// (defect #291 AC5). Item codes map to keys as: 56 catering, 58 wages, 59 depreciation, 60 general
// camp, 61 recoveries, 62 other-camp rows, 63 crew transportation, 64/65/66/67 equip land/rail/air/
// water, 68 other-access rows.

/** A camp form with the given camp volume and per-category costs; volumes follow BR-03 propagation. */
const form = (
  campVolume: string,
  costs: Partial<Record<CategoryKey, string>>,
  volumes: Partial<Record<CategoryKey, string>> = {},
): CampFormValues => {
  const categories = {} as CampFormValues['categories']
  for (const key of CATEGORY_KEYS) {
    categories[key] = {
      // BR-03: the Associated Camp Volume propagates into every category except Recoveries.
      volume: key === 'recoveries' ? '' : (volumes[key] ?? campVolume),
      cost: costs[key] ?? '',
    }
  }
  return {
    campName: 'Camp A',
    roadDistanceToOperatingArea: '',
    sizeOfCamp: '',
    associatedCampVolume: campVolume,
    isolatedCamp: 'false',
    comments: '',
    categories,
  }
}

/** Only the two sub-page-owned costs matter on the served side; the rest is scaffolding. */
const served = (otherCampCost: number | null, otherAccessCost: number | null): Camp =>
  ({
    campId: 1,
    revisionCount: 0,
    campName: 'Camp A',
    roadDistanceToOperatingArea: null,
    sizeOfCamp: null,
    associatedCampVolume: 120000,
    isolatedCamp: false,
    comments: null,
    otherCampExpenses: { volume: 120000, cost: otherCampCost, costPerVolume: null },
    otherAccessExpenses: { volume: 120000, cost: otherAccessCost, costPerVolume: null },
    otherCampExpenseCount: 0,
    otherAccessExpenseCount: 0,
  }) as unknown as Camp

describe('Camp Sub-Total — sumsFiveComponents', () => {
  test('sums exactly the five camp-expense costs and derives $/m³', () => {
    const d = deriveSchedule5(
      form('120000', {
        cateringAndFood: '480000',
        wagesAndBenefits: '960000',
        depreciationLease: '120000',
        generalCampExpenses: '60000',
        recoveries: '44000', // must NOT be part of the Sub-Total
      }),
      served(24000, null), // the item-62 row sum
    )
    expect(d.campSubTotal.cost).toBe(1644000) // 480000+960000+120000+60000+24000
    expect(d.campSubTotal.costPerVolume).toBe(13.7) // 1644000/120000
    expect(d.campSubTotal.volume).toBe(120000)
  })

  test('all five components null -> null, never 0', () => {
    const d = deriveSchedule5(form('120000', {}), served(null, null))
    expect(d.campSubTotal.cost).toBeNull()
    expect(d.campSubTotal.costPerVolume).toBeNull()
  })
})

describe('Camp Total — Sub-Total minus Recoveries', () => {
  test('subtracts Recoveries from Sub-Total', () => {
    const d = deriveSchedule5(
      form('120000', { cateringAndFood: '480000', recoveries: '44000' }),
      served(null, null),
    )
    expect(d.campTotal.cost).toBe(436000)
  })

  test('null Recoveries leaves the Sub-Total unchanged', () => {
    const d = deriveSchedule5(form('120000', { cateringAndFood: '480000' }), served(null, null))
    expect(d.campTotal.cost).toBe(480000)
  })

  test('null Sub-Total yields null REGARDLESS of Recoveries (never a bare negative)', () => {
    const d = deriveSchedule5(form('120000', { recoveries: '5000' }), served(null, null))
    expect(d.campSubTotal.cost).toBeNull()
    expect(d.campTotal.cost).toBeNull()
  })

  test('Recoveries exceeding Sub-Total gives a NEGATIVE total — never clamped', () => {
    const d = deriveSchedule5(
      form('10000', { cateringAndFood: '30000', recoveries: '50000' }),
      served(null, null),
    )
    expect(d.campTotal.cost).toBe(-20000)
    expect(d.campTotal.costPerVolume).toBe(-2) // -20000/10000
  })
})

describe('Access Expense Total and Camp-and-Access', () => {
  test('sums exactly the six access costs', () => {
    const d = deriveSchedule5(
      form('120000', {
        crewTransportation: '180000',
        equipAndSuppliesLand: '90000',
        equipAndSuppliesRail: '0',
        equipAndSuppliesAir: '12000',
        equipAndSuppliesWater: '6000',
      }),
      served(null, 3000), // the item-68 row sum
    )
    expect(d.accessExpenseTotal.cost).toBe(291000) // 180000+90000+0+12000+6000+3000
    expect(d.accessExpenseTotal.costPerVolume).toBe(2.43) // 291000/120000 = 2.425 -> 2.43
  })

  test('§T1 — Sub-Total is computed first, so Camp Total is never a stale null', () => {
    // The canary from the service test: a collapsed Camp Total would leave 180000 here, not 660000.
    const d = deriveSchedule5(
      form('120000', { cateringAndFood: '480000', crewTransportation: '180000' }),
      served(null, null),
    )
    expect(d.campSubTotal.cost).toBe(480000)
    expect(d.campTotal.cost).toBe(480000)
    expect(d.accessExpenseTotal.cost).toBe(180000)
    expect(d.campAndAccessTotal.cost).toBe(660000)
  })

  test('Camp-and-Access adds null-tolerantly: one side null passes the other through', () => {
    const campOnly = deriveSchedule5(
      form('120000', { cateringAndFood: '480000' }),
      served(null, null),
    )
    expect(campOnly.accessExpenseTotal.cost).toBeNull()
    expect(campOnly.campAndAccessTotal.cost).toBe(480000)

    const accessOnly = deriveSchedule5(
      form('120000', { crewTransportation: '180000' }),
      served(null, null),
    )
    expect(accessOnly.campTotal.cost).toBeNull()
    expect(accessOnly.campAndAccessTotal.cost).toBe(180000)
  })

  test('both sides null -> Camp-and-Access is null, never 0', () => {
    const d = deriveSchedule5(form('120000', {}), served(null, null))
    expect(d.campAndAccessTotal.cost).toBeNull()
  })
})

describe('the seam between the camp mirror and the sub-page mirror', () => {
  test('a served Other cost of 0 makes the Sub-Total 0, not blank', () => {
    // Schedule5ServiceTest `campSideAsymmetryYieldsZero`. The sub-page mirror is tested to PRODUCE
    // that 0 (schedule5SubPage/derived.test.ts, the campSideServesZero case); this is the consuming
    // side, which the code review found untested — sumN would otherwise be reached with all-null and
    // return blank where the server returns 0.
    const d = deriveSchedule5(
      form('120000', {}),
      served(0, null), // an item-62 list of cost-free rows: the server serves 0, not null
    )
    expect(d.campSubTotal.cost).toBe(0)
    expect(d.campTotal.cost).toBe(0)
    expect(d.campAndAccessTotal.cost).toBe(0)
  })

  test('a served Other cost of null leaves the Sub-Total blank', () => {
    const d = deriveSchedule5(form('120000', {}), served(null, null))
    expect(d.campSubTotal.cost).toBeNull()
  })
})

describe('per-category rates', () => {
  test('the nine fully-entered categories divide their own cost by their own volume', () => {
    const d = deriveSchedule5(
      form('120000', { cateringAndFood: '480000', equipAndSuppliesAir: '12000' }),
      served(null, null),
    )
    expect(d.perUnit.cateringAndFood).toBe(4) // 480000/120000
    expect(d.perUnit.equipAndSuppliesAir).toBe(0.1) // 12000/120000
  })

  test('a category with no cost has a blank rate', () => {
    const d = deriveSchedule5(form('120000', {}), served(null, null))
    expect(d.perUnit.cateringAndFood).toBeNull()
  })

  test('the legacy scale-2 rule, not Schedules 2/4 scale-4', () => {
    // 200000/30000 = 6.66666… -> 6.67 here; the scale-4 rule would give 6.6667.
    const d = deriveSchedule5(form('30000', { cateringAndFood: '200000' }), served(null, null))
    expect(d.perUnit.cateringAndFood).toBe(6.67)
  })

  test('a zero camp volume blanks every derived rate rather than dividing by zero', () => {
    const d = deriveSchedule5(form('0', { cateringAndFood: '480000' }), served(null, null))
    expect(d.campSubTotal.cost).toBe(480000)
    expect(d.campSubTotal.costPerVolume).toBeNull()
    expect(d.perUnit.cateringAndFood).toBeNull()
  })
})

describe('categoryRate — which cells the mirror owns', () => {
  const d = deriveSchedule5(form('120000', { cateringAndFood: '480000' }), served(24000, null))

  test('a fully-entered category takes the mirrored rate', () => {
    expect(categoryRate('cateringAndFood', d, { volume: 1, cost: 1, costPerVolume: 999 })).toBe(4)
  })

  test('the two per-term Other rows keep the SERVED rate', () => {
    // Their $/m³ sums per-row quotients (costPerVolumePerTerm) and needs the individual sub-page row
    // costs, which the camp document does not carry — a recorded deviation, not an oversight.
    expect(
      categoryRate('otherCampExpenses', d, { volume: 120000, cost: 24000, costPerVolume: 0.2 }),
    ).toBe(0.2)
    expect(
      categoryRate('otherAccessExpenses', d, { volume: 120000, cost: 3000, costPerVolume: 0.03 }),
    ).toBe(0.03)
  })

  test('read-only mode takes the served rate for every category', () => {
    expect(categoryRate('cateringAndFood', null, { volume: 1, cost: 1, costPerVolume: 999 })).toBe(
      999,
    )
  })
})
