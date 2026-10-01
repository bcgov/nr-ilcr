import type { BridgeFormValues } from '@/components/schedule7a/validation'
import { deriveBridgeTotals } from '@/components/schedule7a/derived'
import { emptyBridgeForm } from '@/components/schedule7a/validation'

// The 8000/800/1200/12000, site-plan-only and partial-null cases are transcribed from
// `Schedule7aServiceTest`; the zero-preserved, negative, grouped and non-finite cases are client-only
// concerns computed here (narrowed 2026-08-21 after code review).
//
// Expected figures transcribed from `Schedule7aServiceTest` — `read_mapsBridgeAndDerivesTotals`,
// `totals_nullWhenNoContributingCost` and `totals_partialNullAddition` — so the mirror is pinned to
// the server's own arithmetic (defect #291 AC5).

const form = (costs: Partial<Record<keyof BridgeFormValues, string>>): BridgeFormValues => ({
  ...emptyBridgeForm(),
  ...costs,
})

describe('deriveBridgeTotals — the full-costs case', () => {
  // sitePlan 1000, ssMaterial 5000, ssDeliver 500, ssInstall 800, abutMaterial 3000,
  // abutDeliver 300, abutInstall 400, approach 700, afterInstall 200, other 100.
  const totals = deriveBridgeTotals(
    form({
      sitePlanCost: '1000',
      superstructureMaterialCost: '5000',
      superstructureDeliverCost: '500',
      superstructureInstallCost: '800',
      abutmentMaterialCost: '3000',
      abutmentDeliverCost: '300',
      abutmentInstallCost: '400',
      approachCost: '700',
      afterInstallCost: '200',
      otherCost: '100',
    }),
  )

  test('each pair total is superstructure + abutment', () => {
    expect(totals.totalMaterial).toBe(8000) // 5000 + 3000
    expect(totals.totalDeliver).toBe(800) // 500 + 300
    expect(totals.totalInstall).toBe(1200) // 800 + 400
  })

  test('the grand total sums the seven operands, Site Plan INCLUDED', () => {
    // 1000 + 8000 + 800 + 1200 + 700 + 200 + 100. The service javadoc that omits Site Plan is wrong;
    // the code includes it (recorded in story 12.1).
    expect(totals.grandTotal).toBe(12000)
  })
})

describe('deriveBridgeTotals — null tolerance', () => {
  test('a total with no contributing cost is null, not 0', () => {
    const totals = deriveBridgeTotals(form({ sitePlanCost: '1000' }))
    expect(totals.totalMaterial).toBeNull()
    expect(totals.totalDeliver).toBeNull()
    expect(totals.totalInstall).toBeNull()
    expect(totals.grandTotal).toBe(1000) // site plan only
  })

  test('null-tolerant addition returns the lone present operand', () => {
    const totals = deriveBridgeTotals(
      form({ superstructureMaterialCost: '5000', abutmentDeliverCost: '300' }),
    )
    expect(totals.totalMaterial).toBe(5000) // add(5000, null)
    expect(totals.totalDeliver).toBe(300) // add(null, 300)
    expect(totals.totalInstall).toBeNull()
  })

  test('a wholly empty bridge has four blank totals — never 0', () => {
    const totals = deriveBridgeTotals(emptyBridgeForm())
    expect(totals.totalMaterial).toBeNull()
    expect(totals.totalDeliver).toBeNull()
    expect(totals.totalInstall).toBeNull()
    expect(totals.grandTotal).toBeNull()
  })

  test('a real zero is preserved, and is not treated as absent', () => {
    const totals = deriveBridgeTotals(
      form({ superstructureMaterialCost: '0', abutmentMaterialCost: '0' }),
    )
    expect(totals.totalMaterial).toBe(0)
    expect(totals.grandTotal).toBe(0)
  })
})

describe('deriveBridgeTotals — entry forms', () => {
  test('accepts the grouped strings the money fields display', () => {
    const totals = deriveBridgeTotals(
      form({ superstructureMaterialCost: '5,000', abutmentMaterialCost: '3,000' }),
    )
    expect(totals.totalMaterial).toBe(8000)
  })

  test('a negative cost (a credit) flows through both levels', () => {
    const totals = deriveBridgeTotals(
      form({ superstructureMaterialCost: '5000', abutmentMaterialCost: '-8000' }),
    )
    expect(totals.totalMaterial).toBe(-3000)
    expect(totals.grandTotal).toBe(-3000)
  })

  test('non-finite entry is treated as absent rather than reaching a cell', () => {
    const totals = deriveBridgeTotals(
      form({ superstructureMaterialCost: 'Infinity', abutmentMaterialCost: '3000' }),
    )
    expect(totals.totalMaterial).toBe(3000)
    // Asserted as a VALUE: `Number.isFinite(x ?? 0)` passed on null and on any finite number, so it
    // could not observe the leak it was placed to exclude (code review 2026-08-21).
    expect(totals.grandTotal).toBe(3000)
  })
})
