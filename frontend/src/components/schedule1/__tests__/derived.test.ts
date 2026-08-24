import type Schedule1Response from '@/interfaces/Schedule1Response'
import { deriveSchedule1, enteredFromForm } from '@/components/schedule1/derived'

// The fixtures and expected figures are transcribed from `Schedule1ServiceTest` — chiefly
// `derivedTotals_foldInSchedule3AndLineItems` and
// `totalSilviculture_blankWhenSilvCostsAbsent_matchesLegacyNullSemantics` — so the mirror is pinned to
// the server's own arithmetic (defect #291 AC5).

/** Only the four values the mirror reads off the document are meaningful; the rest is scaffolding. */
const doc = (carried: {
  forestMgmtAdminCost: number | null
  lessSilvAdminCost: number | null
  otherCostsSubtotal: number | null
  schedule3CrownVolume: number | null
}): Schedule1Response =>
  ({
    millId: 514,
    year: 2021,
    trackStatus: 'D',
    editable: true,
    crownVolume: null,
    schedule3CrownVolume: carried.schedule3CrownVolume,
    revisionCount: 0,
    comments: null,
    lineItems: [],
    silviculture: { actualSpent: null, accruedLessActual: null, lessAdmin: null, total: null },
    forestMgmtAdminCost: carried.forestMgmtAdminCost,
    lessSilvAdminCost: carried.lessSilvAdminCost,
    otherCosts: { volume: null, costSubtotal: carried.otherCostsSubtotal, perUnit: null, count: 0 },
    forestMgmtAdminPerUnit: null,
    lessSilvAdminPerUnit: null,
    totalSilvicultureCost: null,
    totalSilviculturePerUnit: null,
    subtotalCompanyLoggingCost: null,
    subtotalCompanyLoggingPerUnit: null,
    totalCompanyLoggingCost: null,
    totalCompanyLoggingPerUnit: null,
  }) as Schedule1Response

describe('deriveSchedule1 — derivedTotals_foldInSchedule3AndLineItems', () => {
  // Entered: 12 = 100/40000, 13 = 50/10000, 143 volume 1000, 144 volume 2000,
  // silviculture 1 = 10/200000, 2 = 10/50000, 140 volume 500.
  const derived = deriveSchedule1(
    doc({
      forestMgmtAdminCost: 600000,
      lessSilvAdminCost: 150000,
      otherCostsSubtotal: 0,
      schedule3CrownVolume: 4000,
    }),
    enteredFromForm({
      'vol-12': '100',
      'cost-12': '40000',
      'vol-13': '50',
      'cost-13': '10000',
      'vol-143': '1000',
      'vol-144': '2000',
      'vol-1': '10',
      'cost-1': '200000',
      'vol-2': '10',
      'cost-2': '50000',
      'vol-140': '500',
    }),
  )

  test('Subtotal Company Logging = logging lines + Forest Mgmt Admin + Other Costs', () => {
    expect(derived.subtotalCompanyLoggingCost).toBe(650000) // (40000 + 10000) + 600000 + 0
  })

  test('Total Silviculture = actual − Sch3 silv admin + accrued', () => {
    expect(derived.totalSilvicultureCost).toBe(100000) // 200000 − 150000 + 50000
  })

  test('Total Company Logging = subtotal + total silviculture, over the crown volume', () => {
    expect(derived.totalCompanyLoggingCost).toBe(750000) // 650000 + 100000
    expect(derived.totalCompanyLoggingPerUnit).toBe(187.5) // 750000 / 4000
  })

  test('the pulled Schedule 3 admin costs divide by the volume entered here', () => {
    // `lessSilvAdminPerUnit` (139) was asserted by neither the frontend nor the backend before the
    // 2026-08-21 review, despite being newly mirror-driven: 150000 Sch3 pull / 55 entered volume.
    const withAdminVolume = deriveSchedule1(
      doc({
        forestMgmtAdminCost: 600000,
        lessSilvAdminCost: 150000,
        otherCostsSubtotal: 0,
        schedule3CrownVolume: null,
      }),
      enteredFromForm({ 'vol-139': '55', 'vol-143': '1000' }),
    )
    expect(withAdminVolume.perUnit[139]).toBe(2727.27) // 150000/55 at the legacy scale-2 rule
    expect(withAdminVolume.perUnit[143]).toBe(600)
  })

  test('the per-unit cells divide the right cost by the entered volume', () => {
    expect(derived.perUnit[143]).toBe(600) // 600000 / 1000
    expect(derived.perUnit[144]).toBe(325) // 650000 / 2000
    expect(derived.perUnit[12]).toBe(400) // 40000 / 100
    expect(derived.perUnit[13]).toBe(200) // 10000 / 50
    expect(derived.perUnit[140]).toBe(200) // 100000 / 500
  })
})

describe('deriveSchedule1 — totalSilviculture_blankWhenSilvCostsAbsent (legacy null semantics)', () => {
  // A logging line only, with the Schedule 3 Less Silv Admin present. This is the S02 crown-prefill
  // screen: Total Silviculture must be BLANK, not −150000.
  const derived = deriveSchedule1(
    doc({
      forestMgmtAdminCost: null,
      lessSilvAdminCost: 150000,
      otherCostsSubtotal: 0,
      schedule3CrownVolume: null,
    }),
    enteredFromForm({ 'vol-12': '100', 'cost-12': '40000' }),
  )

  test('Total Silviculture is blank rather than a negative admin cost', () => {
    expect(derived.totalSilvicultureCost).toBeNull()
    expect(derived.perUnit[140]).toBeNull()
  })

  test('Subtotal Company Logging is the logging line alone — nulls as 0, never blank', () => {
    expect(derived.subtotalCompanyLoggingCost).toBe(40000)
  })

  test('a blank Total Silviculture leaves the grand total equal to the subtotal', () => {
    expect(derived.totalCompanyLoggingCost).toBe(40000) // NOT 40000 − 150000
  })

  test('the grand-total $/m³ is blank without a crown volume', () => {
    expect(derived.totalCompanyLoggingPerUnit).toBeNull()
  })
})

describe('deriveSchedule1 — the two rules that are easy to conflate', () => {
  const carried = {
    forestMgmtAdminCost: null,
    lessSilvAdminCost: null,
    otherCostsSubtotal: 0,
    schedule3CrownVolume: null,
  }

  test('Subtotal Company Logging is 0 — not blank — on a wholly empty schedule', () => {
    const derived = deriveSchedule1(doc(carried), enteredFromForm({}))
    expect(derived.subtotalCompanyLoggingCost).toBe(0)
    // ...while the null-propagating figure beside it stays blank.
    expect(derived.totalSilvicultureCost).toBeNull()
  })

  test('accrued alone still produces a Total Silviculture (addition needs only one operand)', () => {
    const derived = deriveSchedule1(doc(carried), enteredFromForm({ 'cost-2': '50000' }))
    expect(derived.totalSilvicultureCost).toBe(50000)
  })

  test('actual alone with no admin pull subtracts nothing', () => {
    const derived = deriveSchedule1(doc(carried), enteredFromForm({ 'cost-1': '200000' }))
    expect(derived.totalSilvicultureCost).toBe(200000)
  })

  test('$/m³ uses the legacy scale-2 rule, not Schedule 2/4 scale-4', () => {
    // 200000 / 30000 = 6.66666… -> Schedules 1/3 round to 6.67; Schedules 2/4 would give 6.6667.
    const derived = deriveSchedule1(
      doc(carried),
      enteredFromForm({ 'vol-12': '30000', 'cost-12': '200000' }),
    )
    expect(derived.perUnit[12]).toBe(6.67)
  })
})

describe('deriveSchedule1 — the Other Costs row', () => {
  test('the sub-page subtotal over the volume entered on THIS page', () => {
    const derived = deriveSchedule1(
      doc({
        forestMgmtAdminCost: null,
        lessSilvAdminCost: null,
        otherCostsSubtotal: 90000,
        schedule3CrownVolume: null,
      }),
      enteredFromForm({ otherCostsVolume: '1500' }),
    )
    expect(derived.otherCostsPerUnit).toBe(60) // 90000 / 1500
    // The subtotal also feeds Subtotal Company Logging.
    expect(derived.subtotalCompanyLoggingCost).toBe(90000)
  })

  test('no volume entered leaves the rate blank rather than dividing by zero', () => {
    const derived = deriveSchedule1(
      doc({
        forestMgmtAdminCost: null,
        lessSilvAdminCost: null,
        otherCostsSubtotal: 90000,
        schedule3CrownVolume: null,
      }),
      enteredFromForm({ otherCostsVolume: '' }),
    )
    expect(derived.otherCostsPerUnit).toBeNull()
  })
})

describe('enteredFromForm', () => {
  test('strips the thousands separators the fields display', () => {
    const entered = enteredFromForm({ 'vol-12': '1,000', 'cost-12': '40,000' })
    expect(entered.volume[12]).toBe(1000)
    expect(entered.cost[12]).toBe(40000)
  })

  test('an absent or blank field parses to null, not 0', () => {
    const entered = enteredFromForm({ 'cost-12': '' })
    expect(entered.cost[12]).toBeNull()
    expect(entered.volume[12]).toBeNull()
  })
})
