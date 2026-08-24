import type Schedule2Response from '@/interfaces/Schedule2Response'
import { deriveSchedule2 } from '@/components/schedule2/derived'

// The fixtures and expected figures below are transcribed from `Schedule2ServiceTest` so the mirror is
// pinned to the server's own arithmetic: if `Schedule2Service` changes, these numbers stop matching
// and a test fails here, instead of a figure silently jumping when the Save echo lands (defect #291).

const block = (volume: number | null, cost: number | null, perUnit: number | null) => ({
  volume,
  cost,
  perUnit,
})

/**
 * The carried half of `Schedule2ServiceTest.stubFullDraft`: Sch3 PO&P timber volume 10000, Sch3
 * Subtotal Actual Costs PO&P column 20000, Sch3 Crown timber volume 12345, and the legacy
 * `getTotalLoggingCost` figure 740700. Only the four carried values the mirror reads are meaningful;
 * the rest is document scaffolding.
 */
const doc = (
  popVolume: number | null,
  popActualCost: number | null,
  crownVolume: number | null,
  totalLoggingCost: number | null,
): Schedule2Response =>
  ({
    millId: 514,
    year: 2021,
    trackStatus: 'D',
    editable: true,
    revisionCount: 0,
    comments: null,
    purchasedLogCost: block(popVolume, null, null),
    purchasedWoodOverhead: block(popVolume, popActualCost, null),
    subtotal: block(null, null, null),
    lessLogSales: block(null, null, null),
    netPurchased: block(null, null, null),
    totalCompanyLogging: block(crownVolume, totalLoggingCost, null),
    totalAverage: block(null, null, null),
  }) as Schedule2Response

const FULL_DRAFT = doc(10000, 20000, 12345, 740700)
const NO_CROSS_SCHEDULE = doc(null, null, null, null)

describe('deriveSchedule2 — the stubFullDraft figures', () => {
  // Entered: item 25 cost 500000, item 26 volume 2000, item 26 cost 100000.
  const derived = deriveSchedule2(FULL_DRAFT, {
    purchasedLogCostCost: 500000,
    lessLogSalesVolume: 2000,
    lessLogSalesCost: 100000,
  })

  test('purchasedLogCost — entered cost over the carried Sch3 volume', () => {
    expect(derived.purchasedLogCost.volume).toBe(10000)
    expect(derived.purchasedLogCost.cost).toBe(500000)
    expect(derived.purchasedLogCost.perUnit).toBe(50) // 500000/10000 -> 50.0
  })

  test('subtotal — item 25 + Sch3 PO&P actual cost', () => {
    expect(derived.subtotal.cost).toBe(520000) // 500000 + 20000
    expect(derived.subtotal.volume).toBe(10000)
    expect(derived.subtotal.perUnit).toBe(52) // 52.0
  })

  test('lessLogSales — both entered', () => {
    expect(derived.lessLogSales.volume).toBe(2000)
    expect(derived.lessLogSales.cost).toBe(100000)
    expect(derived.lessLogSales.perUnit).toBe(50) // 100000/2000 -> 50.0
  })

  test('netPurchased — Sch3 volume and the subtotal, each less item 26', () => {
    expect(derived.netPurchased.volume).toBe(8000) // 10000 - 2000
    expect(derived.netPurchased.cost).toBe(420000) // 520000 - 100000
    expect(derived.netPurchased.perUnit).toBe(52.5)
  })

  test('totalAverage — net plus the carried Crown volume and total logging cost', () => {
    expect(derived.totalAverage.volume).toBe(20345) // 8000 + 12345
    expect(derived.totalAverage.cost).toBe(1160700) // 420000 + 740700
    expect(derived.totalAverage.perUnit).toBe(57.0509) // 1160700/20345 = 57.050872…, scale-4 HALF_UP
  })
})

describe('deriveSchedule2 — absent Schedule 3 (absentSchedule3_dependentFiguresNull)', () => {
  // Sch2 entered, but Schedule 3 absent so every carried figure is null. Entered: 333000 / 500 / 25000.
  const derived = deriveSchedule2(NO_CROSS_SCHEDULE, {
    purchasedLogCostCost: 333000,
    lessLogSalesVolume: 500,
    lessLogSalesCost: 25000,
  })

  test('a carried volume being null nulls the dependent perUnit, not the entered cost', () => {
    expect(derived.purchasedLogCost.cost).toBe(333000)
    expect(derived.purchasedLogCost.volume).toBeNull()
    expect(derived.purchasedLogCost.perUnit).toBeNull()
  })

  test('subtotal falls back to item 25 alone (CoreUtil addition, not null)', () => {
    expect(derived.subtotal.cost).toBe(333000)
  })

  test('lessLogSales is still fully derived from the entered pair', () => {
    expect(derived.lessLogSales.perUnit).toBe(50) // 25000/500
  })

  test('a null minuend nulls the net volume while the net cost still computes', () => {
    expect(derived.netPurchased.volume).toBeNull() // subtract(null, 500) -> null
    expect(derived.netPurchased.cost).toBe(308000) // 333000 - 25000
    expect(derived.netPurchased.perUnit).toBeNull() // volume null
  })

  test('totalAverage keeps the present term and drops the absent one', () => {
    expect(derived.totalAverage.volume).toBeNull()
    expect(derived.totalAverage.cost).toBe(308000)
  })
})

describe('deriveSchedule2 — the unsaved document (unsavedSchedule_returnsEmptyEditableDocument)', () => {
  const derived = deriveSchedule2(NO_CROSS_SCHEDULE, {
    purchasedLogCostCost: null,
    lessLogSalesVolume: null,
    lessLogSalesCost: null,
  })

  test('every derived figure is null — never a fabricated 0', () => {
    expect(derived.subtotal.cost).toBeNull()
    expect(derived.netPurchased.cost).toBeNull()
    expect(derived.totalAverage.volume).toBeNull()
    expect(derived.totalAverage.cost).toBeNull()
    expect(derived.purchasedLogCost.perUnit).toBeNull()
  })
})

describe('deriveSchedule2 — carried figures never move', () => {
  test('an entered change leaves the wholly-carried blocks untouched in the document', () => {
    const before = deriveSchedule2(FULL_DRAFT, {
      purchasedLogCostCost: 500000,
      lessLogSalesVolume: 2000,
      lessLogSalesCost: 100000,
    })
    const after = deriveSchedule2(FULL_DRAFT, {
      purchasedLogCostCost: 900000,
      lessLogSalesVolume: 2000,
      lessLogSalesCost: 100000,
    })
    // The mirror does not return purchasedWoodOverhead / totalCompanyLogging at all — the page renders
    // them from the document — so the carried figures cannot drift by construction.
    expect(after.subtotal.cost).toBe(920000)
    expect(before.subtotal.cost).toBe(520000)
    expect(FULL_DRAFT.purchasedWoodOverhead.cost).toBe(20000)
    expect(FULL_DRAFT.totalCompanyLogging.cost).toBe(740700)
  })
})

describe('deriveSchedule2 — entry that drives a figure negative', () => {
  test('selling more volume than was purchased gives a negative net, rounded HALF_UP away from zero', () => {
    const derived = deriveSchedule2(FULL_DRAFT, {
      purchasedLogCostCost: 500000,
      lessLogSalesVolume: 12000,
      lessLogSalesCost: 900000,
    })
    expect(derived.netPurchased.volume).toBe(-2000) // 10000 - 12000
    expect(derived.netPurchased.cost).toBe(-380000) // 520000 - 900000
    expect(derived.netPurchased.perUnit).toBe(190) // -380000 / -2000
  })
})
