import type { CategoryForm } from '@/components/schedule4/validation'
import { deriveCategoryPerUnits } from '@/components/schedule4/derived'
import { ALL_CATEGORIES } from '@/components/schedule4/validation'

// `Schedule4Service` computes each category's $/m³ as cost ÷ volume, null when either operand is null
// or the volume is zero (Schedule4Service.java:141,513). These pin the mirror to that rule.

const form = (
  values: Partial<Record<number, { volume: string; cost: string; distance: string }>>,
) => {
  const built: CategoryForm = {}
  for (const def of ALL_CATEGORIES) {
    built[def.code] = values[def.code] ?? { volume: '', cost: '', distance: '' }
  }
  return built
}

describe('deriveCategoryPerUnits', () => {
  test('computes $/m³ per category from the entered pair', () => {
    // The Schedule4.test fixture's Harbour Dump: code 40 = 2000/100000, code 47 = 500/25000.
    const perUnit = deriveCategoryPerUnits(
      form({
        40: { volume: '2000', cost: '100000', distance: '' },
        47: { volume: '500', cost: '25000', distance: '120.5' },
      }),
    )
    expect(perUnit[40]).toBe(50)
    expect(perUnit[47]).toBe(50)
  })

  test('covers every category, null where nothing is entered', () => {
    const perUnit = deriveCategoryPerUnits(form({}))
    for (const def of ALL_CATEGORIES) {
      expect(perUnit[def.code]).toBeNull()
    }
    expect(Object.keys(perUnit)).toHaveLength(ALL_CATEGORIES.length)
  })

  test('is null when only one half of the pair is entered', () => {
    const perUnit = deriveCategoryPerUnits(
      form({
        40: { volume: '2000', cost: '', distance: '' },
        41: { volume: '', cost: '100000', distance: '' },
      }),
    )
    expect(perUnit[40]).toBeNull()
    expect(perUnit[41]).toBeNull()
  })

  test('is null when the volume is zero, never Infinity', () => {
    const perUnit = deriveCategoryPerUnits(
      form({ 40: { volume: '0', cost: '100000', distance: '' } }),
    )
    expect(perUnit[40]).toBeNull()
  })

  test('rounds at scale 4 HALF_UP like the server', () => {
    const perUnit = deriveCategoryPerUnits(
      form({ 40: { volume: '30000', cost: '200000', distance: '' } }),
    )
    expect(perUnit[40]).toBe(6.6667)
  })

  test('accepts grouped input, since the inputs display grouped values', () => {
    const perUnit = deriveCategoryPerUnits(
      form({ 40: { volume: '2,000', cost: '100,000', distance: '' } }),
    )
    expect(perUnit[40]).toBe(50)
  })

  test('a negative cost (a credit) yields a negative rate', () => {
    const perUnit = deriveCategoryPerUnits(
      form({ 40: { volume: '2000', cost: '-100000', distance: '' } }),
    )
    expect(perUnit[40]).toBe(-50)
  })

  test('distance never enters the rate', () => {
    const withDistance = deriveCategoryPerUnits(
      form({ 47: { volume: '500', cost: '25000', distance: '120.5' } }),
    )
    const withoutDistance = deriveCategoryPerUnits(
      form({ 47: { volume: '500', cost: '25000', distance: '' } }),
    )
    expect(withDistance[47]).toBe(withoutDistance[47])
  })
})
