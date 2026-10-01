import type Schedule3Response from '@/interfaces/Schedule3Response'
import type { CostLine } from '@/interfaces/Schedule3Response'
import { deriveSchedule3, enteredFromForm, scalingPop, unacceptableBase } from '../derived'

// Expected figures transcribed from `Schedule3ServiceTest` — normalLine_crownIsHarvestMinusPop,
// harvestOnlyLines_popForcedZero_crownEqualsHarvest, scalingPop_derivedFromTimberVolumeRatio,
// fullDocument_derivedCascadeMatchesLegacy, unacceptableCount_addsItem38RowsPlusAnnualRents and
// emptySchedule_subtotalsAreZero — so the mirror is pinned to the server's arithmetic (#291 AC5).

const total = (harvest: number | null, pop: number | null, crown: number | null) => ({
  harvest,
  pop,
  crown,
})

/**
 * A loaded document. Only what the mirror reads matters: `subtotalOtherCosts` (a sub-page constant),
 * `includedUnacceptableCosts.harvest` + the Annual Rents line (to recover the item-38 constant), and
 * `unacceptableCount`.
 */
const doc = (opts: {
  otherAcceptable?: { harvest: number; pop: number }
  loadedUnacceptableHarvest?: number
  loadedAnnualRents?: number | null
  unacceptableCount?: number
}): Schedule3Response => {
  const lineItems: CostLine[] = []
  if (opts.loadedAnnualRents !== undefined && opts.loadedAnnualRents !== null) {
    lineItems.push({
      costItemCode: 29,
      harvest: opts.loadedAnnualRents,
      pop: 0,
      crown: opts.loadedAnnualRents,
    })
  }
  return {
    millId: 514,
    year: 2021,
    trackStatus: 'D',
    editable: true,
    revisionCount: 0,
    overrideHarvestTotalPop: 'N',
    comments: null,
    lineItems,
    popTimber: total(null, null, null),
    crownTimber: total(null, null, null),
    totalOverhead: total(null, null, null),
    subtotalOtherCosts: total(
      opts.otherAcceptable?.harvest ?? 0,
      opts.otherAcceptable?.pop ?? 0,
      0,
    ),
    subtotalActualCosts: total(null, null, null),
    includedUnacceptableCosts: total(opts.loadedUnacceptableHarvest ?? 0, 0, 0),
    totalCosts: total(null, null, null),
    otherAcceptableCount: 0,
    unacceptableCount: opts.unacceptableCount ?? 0,
  } as unknown as Schedule3Response
}

describe('per-line crown', () => {
  test('a both-columns line: crown = harvest − PO&P', () => {
    const d = deriveSchedule3(
      doc({}),
      enteredFromForm({ 'harvest-27': '100000', 'pop-27': '40000' }),
    )
    expect(d.lines[27]).toEqual({ harvest: 100000, pop: 40000, crown: 60000 })
  })

  test('crown is blank unless BOTH sides are present', () => {
    const d = deriveSchedule3(doc({}), enteredFromForm({ 'harvest-27': '100000' }))
    expect(d.lines[27].crown).toBeNull()
    const e = deriveSchedule3(doc({}), enteredFromForm({ 'pop-27': '40000' }))
    expect(e.lines[27].crown).toBeNull()
  })

  test('the harvest-only lines force PO&P to 0, so crown equals harvest', () => {
    const d = deriveSchedule3(
      doc({}),
      enteredFromForm({ 'harvest-29': '30000', 'harvest-37': '150000' }),
    )
    expect(d.lines[29]).toEqual({ harvest: 30000, pop: 0, crown: 30000 })
    expect(d.lines[37]).toEqual({ harvest: 150000, pop: 0, crown: 150000 })
  })

  test('a "-0" harvest renders as 0, not "-0"', () => {
    // `crownCost` is a bare subtraction, so it bypassed halfUp's own -0 guard: toNum('-0') is -0 and a
    // harvest-only line forces PO&P to 0, giving -0, which fmtNumber renders as "-0" where the
    // server's integer arithmetic gives 0 (code review 2026-08-21).
    const d = deriveSchedule3(doc({}), enteredFromForm({ 'harvest-29': '-0' }))
    expect(Object.is(d.lines[29].crown, 0)).toBe(true)
  })

  test('a harvest-only line with no harvest keeps a null PO&P, not 0', () => {
    const d = deriveSchedule3(doc({}), enteredFromForm({}))
    expect(d.lines[29].pop).toBeNull()
    expect(d.lines[37].pop).toBeNull()
  })
})

describe('Scaling (33) — the derived PO&P', () => {
  test('scalingPop_derivedFromTimberVolumeRatio: ratio 0.5 of a 60000 harvest', () => {
    // popTimberVol 54321 / (54321 + 54321) = 0.5; 0.5 * 60000 = 30000.
    expect(scalingPop(60000, 54321, 108642)).toBe(30000)
    const d = deriveSchedule3(
      doc({}),
      enteredFromForm({
        'harvest-33': '60000',
        popTimberVolume: '54321',
        crownTimberVolume: '54321',
      }),
    )
    expect(d.lines[33]).toEqual({ harvest: 60000, pop: 30000, crown: 30000 })
  })

  test('it moves with EITHER timber volume, not just its own harvest', () => {
    const entered = (crown: string) =>
      enteredFromForm({
        'harvest-33': '60000',
        popTimberVolume: '54321',
        crownTimberVolume: crown,
      })
    // Tripling the crown volume drops the ratio to 1/4: 15000.
    expect(deriveSchedule3(doc({}), entered('162963')).lines[33].pop).toBe(15000)
  })

  test('it is blank when the harvest or either volume is missing', () => {
    expect(scalingPop(null, 54321, 108642)).toBeNull()
    expect(scalingPop(60000, null, 108642)).toBeNull()
    expect(scalingPop(60000, 54321, null)).toBeNull()
  })

  test('a zero overhead volume is blank, never Infinity', () => {
    expect(scalingPop(60000, 0, 0)).toBeNull()
  })

  test('the whole-dollar rounding is HALF_UP away from zero', () => {
    // ratio 0.5 of 3 = 1.5 -> 2 (a reachable exact tie: equal PO&P and Crown volumes).
    expect(scalingPop(3, 1000, 2000)).toBe(2)
    expect(scalingPop(-3, 1000, 2000)).toBe(-2)
  })

  test('it feeds the PO&P subtotal, since the server sums the RESOLVED pops', () => {
    const d = deriveSchedule3(
      doc({}),
      enteredFromForm({
        'harvest-33': '60000',
        popTimberVolume: '54321',
        crownTimberVolume: '54321',
      }),
    )
    expect(d.subtotalActualCosts.pop).toBe(30000) // the derived Scaling PO&P, not 0
  })
})

describe('fullDocument_derivedCascadeMatchesLegacy — the whole chain', () => {
  // Reproduces the service test's totals: subtotal 900000/300000 from a both-columns line of
  // 870000/300000 plus the harvest-only Annual Rents 30000, with both timber volumes at 54321.
  const d = deriveSchedule3(
    doc({ loadedUnacceptableHarvest: 30000, loadedAnnualRents: 30000, unacceptableCount: 1 }),
    enteredFromForm({
      'harvest-27': '870000',
      'pop-27': '300000',
      'harvest-29': '30000',
      popTimberVolume: '54321',
      crownTimberVolume: '54321',
    }),
  )

  test('Subtotal (Actual Costs)', () => {
    expect(d.subtotalActualCosts).toEqual({ harvest: 900000, pop: 300000, crown: 600000 })
  })

  test('Included Unacceptable Costs — PO&P forced 0, crown = harvest', () => {
    expect(d.includedUnacceptableCosts).toEqual({ harvest: 30000, pop: 0, crown: 30000 })
  })

  test('Total Costs = Subtotal Actual − Included Unacceptable', () => {
    expect(d.totalCosts).toEqual({ harvest: 870000, pop: 300000, crown: 570000 })
  })

  test('the timber costs are pushed down from Total Costs; overhead sums the two', () => {
    expect(d.popTimber.cost).toBe(300000)
    expect(d.crownTimber.cost).toBe(570000)
    expect(d.totalOverhead.cost).toBe(870000)
    expect(d.totalOverhead.volume).toBe(108642) // 54321 + 54321
  })

  test('$/m³ uses the legacy scale-2 rule', () => {
    expect(d.popTimber.perUnit).toBe(5.52) // 300000/54321 = 5.52272…
    expect(d.crownTimber.perUnit).toBe(10.49) // 570000/54321 = 10.49318…
    expect(d.totalOverhead.perUnit).toBe(8.01) // 870000/108642 = 8.00795…
  })
})

describe('Included Unacceptable Costs — the sub-page constant plus an entered field', () => {
  // unacceptableCount_addsItem38RowsPlusAnnualRents: item-38 rows 1000 + 2000, Annual Rents 30000,
  // loaded total 33000 and count 3. The item-38 half must be recovered as 3000 / 2 rows.
  const loaded = doc({
    loadedUnacceptableHarvest: 33000,
    loadedAnnualRents: 30000,
    unacceptableCount: 3,
  })

  test('the item-38 constant is recovered by subtracting the loaded Annual Rents', () => {
    expect(unacceptableBase(loaded)).toBe(3000)
  })

  test('re-entering the same Annual Rents reproduces the served figures exactly', () => {
    const d = deriveSchedule3(loaded, enteredFromForm({ 'harvest-29': '30000' }))
    expect(d.includedUnacceptableCosts.harvest).toBe(33000)
    expect(d.unacceptableCount).toBe(3)
  })

  test('changing the Annual Rents moves the total while keeping the sub-page rows', () => {
    const d = deriveSchedule3(loaded, enteredFromForm({ 'harvest-29': '50000' }))
    expect(d.includedUnacceptableCosts.harvest).toBe(53000) // 3000 + 50000
    expect(d.unacceptableCount).toBe(3) // still counted
  })

  test('clearing the Annual Rents drops it from both the total and the count', () => {
    const d = deriveSchedule3(loaded, enteredFromForm({ 'harvest-29': '' }))
    expect(d.includedUnacceptableCosts.harvest).toBe(3000) // the sub-page rows alone
    expect(d.unacceptableCount).toBe(2) // the +1 for Annual Rents drops off
  })

  test('a zero Annual Rents is not counted (matching the server’s != 0 test)', () => {
    const d = deriveSchedule3(loaded, enteredFromForm({ 'harvest-29': '0' }))
    expect(d.includedUnacceptableCosts.harvest).toBe(3000)
    expect(d.unacceptableCount).toBe(2)
  })
})

describe('Subtotal Other Costs is a sub-page constant', () => {
  test('it seeds both subtotal columns and is never recomputed here', () => {
    const d = deriveSchedule3(
      doc({ otherAcceptable: { harvest: 5000, pop: 2000 } }),
      enteredFromForm({ 'harvest-27': '100000', 'pop-27': '40000' }),
    )
    expect(d.subtotalActualCosts.harvest).toBe(105000) // 5000 + 100000
    expect(d.subtotalActualCosts.pop).toBe(42000) // 2000 + 40000
  })
})

describe('emptySchedule_subtotalsAreZero', () => {
  const d = deriveSchedule3(doc({}), enteredFromForm({}))

  test('the subtotals are 0 — not blank — on an empty schedule', () => {
    expect(d.subtotalActualCosts.harvest).toBe(0)
    expect(d.subtotalActualCosts.pop).toBe(0)
    expect(d.totalCosts.harvest).toBe(0)
  })

  test('but a timber volume and its $/m³ stay blank', () => {
    expect(d.popTimber.volume).toBeNull()
    expect(d.popTimber.perUnit).toBeNull()
    expect(d.totalOverhead.volume).toBeNull()
  })
})

describe('enteredFromForm', () => {
  test('strips the thousands separators the fields display', () => {
    const entered = enteredFromForm({ 'harvest-27': '100,000', 'pop-27': '40,000' })
    expect(entered.harvest[27]).toBe(100000)
    expect(entered.pop[27]).toBe(40000)
  })

  test('the harvest-only and Scaling lines never take an entered PO&P', () => {
    const entered = enteredFromForm({ 'pop-29': '999', 'pop-33': '999', 'pop-37': '999' })
    expect(entered.pop[29]).toBeNull()
    expect(entered.pop[33]).toBeNull()
    expect(entered.pop[37]).toBeNull()
  })
})
