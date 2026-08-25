import {
  addN,
  sumN,
  enteredNum,
  halfUp,
  isUnusableEntry,
  perUnitLegacy,
  perUnitOf,
  scalingPopOf,
  subN,
  sumAsZero,
  wholeDollars,
} from '@/utils/derivedMath'

// The expected values here are transcribed from the BACKEND tests that pin the same arithmetic, so a
// drift between the mirror and the server trips a test here rather than surfacing as a figure that
// jumps when the Save echo lands (defect #291, AC5).

describe('halfUp', () => {
  test('rounds half AWAY FROM ZERO, matching Java RoundingMode.HALF_UP', () => {
    expect(halfUp(1.5)).toBe(2)
    expect(halfUp(2.5)).toBe(3)
    // The whole point of not using Math.round: it gives -1 here, HALF_UP gives -2.
    expect(halfUp(-1.5)).toBe(-2)
    expect(halfUp(-2.5)).toBe(-3)
  })

  test('rounds at the requested scale', () => {
    expect(halfUp(6.66666666, 4)).toBe(6.6667)
    expect(halfUp(-6.66666666, 4)).toBe(-6.6667)
    expect(halfUp(47.857142857, 4)).toBe(47.8571)
  })

  test('leaves zero and already-exact values alone', () => {
    expect(halfUp(0)).toBe(0)
    expect(halfUp(0, 4)).toBe(0)
    expect(halfUp(52, 4)).toBe(52)
    expect(halfUp(-800)).toBe(-800)
  })

  test('never returns negative zero (it would render as "-0")', () => {
    expect(Object.is(halfUp(-0), 0)).toBe(true)
    expect(Object.is(halfUp(-0.0000001, 4), 0)).toBe(true)
  })
})

describe('perUnitOf', () => {
  test('divides cost by volume', () => {
    expect(perUnitOf(500000, 10000)).toBe(50) // Schedule2ServiceTest: 50.0
    expect(perUnitOf(20000, 10000)).toBe(2) // 2.0
    expect(perUnitOf(520000, 10000)).toBe(52) // subtotal 52.0
    expect(perUnitOf(420000, 8000)).toBe(52.5) // netPurchased 52.5
    expect(perUnitOf(740700, 12345)).toBe(60) // totalCompanyLogging 60.0
  })

  test('rounds a non-terminating quotient at scale 4 HALF_UP', () => {
    // Schedule2ServiceTest.perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient: 6.6667, not 6.6666.
    expect(perUnitOf(200000, 30000)).toBe(6.6667)
  })

  test('is null when either operand is null', () => {
    expect(perUnitOf(null, 10000)).toBeNull()
    expect(perUnitOf(500000, null)).toBeNull()
    expect(perUnitOf(null, null)).toBeNull()
  })

  test('is null when the volume is zero (no divide-by-zero)', () => {
    // Schedule2ServiceTest.perUnit_nullWhenVolumeZero
    expect(perUnitOf(25000, 0)).toBeNull()
  })

  test('a zero cost over a real volume is 0, not null', () => {
    expect(perUnitOf(0, 10000)).toBe(0)
  })
})

describe('wholeDollars', () => {
  test('rounds to whole dollars half away from zero', () => {
    expect(wholeDollars(520000)).toBe(520000)
    expect(wholeDollars(1160700.5)).toBe(1160701)
    expect(wholeDollars(-1160700.5)).toBe(-1160701)
  })

  test('passes null through', () => {
    expect(wholeDollars(null)).toBeNull()
  })
})

describe('addN (CoreUtil.bigDecimalAddition)', () => {
  test('adds two present operands', () => {
    expect(addN(500000, 20000)).toBe(520000)
  })

  test('returns the present operand when the other is null', () => {
    // Schedule2ServiceTest.absentSchedule3_dependentFiguresNull: subtotal = item25 alone (333000).
    expect(addN(333000, null)).toBe(333000)
    expect(addN(null, 333000)).toBe(333000)
  })

  test('is null only when BOTH are null — never 0', () => {
    expect(addN(null, null)).toBeNull()
  })

  test('a real zero operand is preserved', () => {
    expect(addN(0, null)).toBe(0)
    expect(addN(0, 5)).toBe(5)
  })
})

describe('subN (CoreUtil.bigDecimalSubtraction)', () => {
  test('subtracts two present operands', () => {
    expect(subN(10000, 2000)).toBe(8000)
    expect(subN(520000, 100000)).toBe(420000)
  })

  test('returns the minuend when the subtrahend is null (nothing to take away)', () => {
    expect(subN(10000, null)).toBe(10000)
  })

  test('is null when the minuend is null (nothing to subtract from)', () => {
    expect(subN(null, 2000)).toBeNull()
    expect(subN(null, null)).toBeNull()
  })

  test('can go negative — the reason halfUp must not use Math.round', () => {
    expect(subN(200, 1000)).toBe(-800)
  })
})

describe('sumN — null-tolerant, the counterpart to sumAsZero', () => {
  test('sums the present operands', () => {
    expect(sumN(100, 250)).toBe(350)
    expect(sumN(100, null, 250)).toBe(350)
  })

  test('is null when EVERY operand is null — the whole point of the helper', () => {
    // Where sumAsZero returns 0. Schedule 5's camp totals and Schedule 7A's bridge totals must stay
    // BLANK on an empty form; Schedule 1's subtotal and Schedule 3's column subtotals must read 0.
    expect(sumN(null, null)).toBeNull()
    expect(sumN()).toBeNull()
    expect(sumAsZero(null, null)).toBe(0) // the contrast, asserted side by side
  })

  test('a real zero operand contributes, and is not treated as absent', () => {
    expect(sumN(0)).toBe(0)
    expect(sumN(null, 0)).toBe(0)
  })

  test('handles negatives', () => {
    expect(sumN(500, -800)).toBe(-300)
  })
})

describe('sumAsZero', () => {
  test('treats null as 0', () => {
    expect(sumAsZero(100, null, 250)).toBe(350)
  })

  test('is 0 — not null — when every operand is null', () => {
    expect(sumAsZero(null, null)).toBe(0)
    expect(sumAsZero()).toBe(0)
  })
})

describe('halfUp — exact decimal halves (the float-representation trap)', () => {
  test('an exact half-cent rounds UP, the way BigDecimal does', () => {
    // 3075/5000 = 0.615 in decimal; the nearest double is 0.6149999999999999911…, so a
    // multiply-and-round implementation gives 0.61 where the server gives 0.62.
    expect(halfUp(3075 / 5000, 2)).toBe(0.62)
    expect(halfUp(0.615, 2)).toBe(0.62)
    expect(halfUp(1.005, 2)).toBe(1.01)
    expect(halfUp(2.675, 2)).toBe(2.68)
  })

  test('and the same half rounds away from zero when negative', () => {
    expect(halfUp(-0.615, 2)).toBe(-0.62)
    expect(halfUp(-1.005, 2)).toBe(-1.01)
  })

  test('a value just BELOW the half still rounds down', () => {
    expect(halfUp(0.6149, 2)).toBe(0.61)
    expect(halfUp(1.00499, 2)).toBe(1.0)
  })

  test('exact halves at other scales', () => {
    expect(halfUp(0.00005, 4)).toBe(0.0001)
    expect(halfUp(2.5, 0)).toBe(3)
    expect(halfUp(0.5, 0)).toBe(1)
  })
})

describe('perUnitLegacy (Schedules 1 and 3)', () => {
  test('rounds to two decimals, not four', () => {
    // 200000/30000 = 6.66666… -> 6.67 here; perUnitOf would give 6.6667.
    expect(perUnitLegacy(200000, 30000)).toBe(6.67)
    expect(perUnitOf(200000, 30000)).toBe(6.6667)
  })

  test('the Schedule3ServiceTest timber rates', () => {
    expect(perUnitLegacy(300000, 54321)).toBe(5.52) // 5.52272…
    expect(perUnitLegacy(570000, 54321)).toBe(10.49) // 10.49318…
    expect(perUnitLegacy(870000, 108642)).toBe(8.01) // 8.00795…
  })

  test('an exact half-cent quotient rounds up', () => {
    expect(perUnitLegacy(3075, 5000)).toBe(0.62)
  })

  test('null and zero-volume rules match perUnitOf', () => {
    expect(perUnitLegacy(null, 5000)).toBeNull()
    expect(perUnitLegacy(3075, null)).toBeNull()
    expect(perUnitLegacy(3075, 0)).toBeNull()
  })
})

// ---- Code-review regressions (2026-08-21) -------------------------------------------------------
// Every case below is a divergence from the Java BigDecimal path that the first implementation shipped
// and the review caught. Each expected value was computed with an exact BigInt model of the server's
// arithmetic, NOT with this module.

describe('regression: rounding must happen in integer space, not double space', () => {
  test('toFixed pre-rounding turned a near-tie into a false tie', () => {
    // The old expansion-and-carry gave 1 and 3; HALF_UP on the true value gives 0 and 2.
    expect(halfUp(0.4999999951, 0)).toBe(0)
    expect(halfUp(2.499999995, 0)).toBe(2)
  })

  test('a one-cent divergence at ordinary magnitudes (Schedules 1 and 3)', () => {
    // cost 41,956,411 / volume 40 = 1,048,910.275 exactly -> scale 2 HALF_UP -> .28, not .27
    expect(perUnitLegacy(41956411, 40)).toBe(1048910.28)
  })

  test('the same at scale 4 (Schedules 2 and 4)', () => {
    // cost 32,781,251 / volume 4,000 = 8,195.31275 exactly -> scale 4 HALF_UP -> .3128, not .3127
    expect(perUnitOf(32781251, 4000)).toBe(8195.3128)
  })

  test('values at or above 1e21, where toFixed emits exponential notation', () => {
    // The old positional parse read "2.5e+21" and returned 0.25.
    expect(halfUp(2.5e21, 4)).toBe(2.5e21)
    expect(halfUp(1e21, 2)).toBe(1e21)
    // Reachable through the mirror: a huge cost over a tiny volume.
    expect(perUnitLegacy(99999999, 0.00000000000001)).toBe(9.9999999e21)
  })

  test('the scale-10 intermediate no longer overflows MAX_SAFE_INTEGER', () => {
    // A 7-digit quotient needs 17 significant digits at scale 10 — past what a double holds.
    expect(perUnitLegacy(41956411, 4)).toBe(10489102.75)
    expect(perUnitLegacy(99999999, 3)).toBe(33333333)
  })

  test('exact decimal halves still round up (the original 0.615 case)', () => {
    expect(halfUp(3075 / 5000, 2)).toBe(0.62)
    expect(perUnitLegacy(3075, 5000)).toBe(0.62)
  })
})

describe('regression: scalingPopOf keeps the server scale-15 ratio step', () => {
  test('the ratio rounding decides the .5 boundary', () => {
    // popVol 5,000,000 / overhead 6,000,000 -> ratio 0.833333333333333 (scale 15) -> x 999,999
    // = 833332.4999999997 -> 833,332. The raw quotient gives exactly 833332.5 -> 833,333.
    expect(scalingPopOf(999999, 5000000, 6000000)).toBe(833332)
    expect(scalingPopOf(3, 500000, 600000)).toBe(2)
  })

  test('a terminating ratio is unaffected (the pre-existing 0.5 cases)', () => {
    expect(scalingPopOf(60000, 54321, 108642)).toBe(30000)
    expect(scalingPopOf(3, 1000, 2000)).toBe(2) // 1.5 -> HALF_UP -> 2
    expect(scalingPopOf(-3, 1000, 2000)).toBe(-2)
  })

  test('absent operands and a zero overhead volume stay blank', () => {
    expect(scalingPopOf(null, 54321, 108642)).toBeNull()
    expect(scalingPopOf(60000, null, 108642)).toBeNull()
    expect(scalingPopOf(60000, 54321, null)).toBeNull()
    expect(scalingPopOf(60000, 0, 0)).toBeNull()
  })
})

describe('regression: non-finite values never reach a cell', () => {
  test('enteredNum treats the values toNum lets through as absent', () => {
    expect(enteredNum('Infinity')).toBeNull()
    expect(enteredNum('-Infinity')).toBeNull()
    expect(enteredNum('1e999')).toBeNull()
    expect(enteredNum('NaN')).toBeNull()
    // ...while ordinary entry still parses, grouped or not.
    expect(enteredNum('1,234.5')).toBe(1234.5)
    expect(enteredNum('')).toBeNull()
  })

  test('the perUnit helpers refuse non-finite operands', () => {
    expect(perUnitOf(Infinity, 100)).toBeNull()
    expect(perUnitOf(100, Infinity)).toBeNull()
    expect(perUnitLegacy(Infinity, 100)).toBeNull()
    expect(perUnitLegacy(NaN, 100)).toBeNull()
  })
})

describe('regression: unusable entries must not advance the mirror baseline', () => {
  test('non-blank but unparseable text is flagged', () => {
    for (const raw of ['-', '.', '-.', '1.2.3', 'abc', 'Infinity']) {
      expect(isUnusableEntry(raw)).toBe(true)
    }
  })

  test('blank and valid values are not', () => {
    for (const raw of ['', '   ', '0', '-0', '1,234', '12.5', '-500']) {
      expect(isUnusableEntry(raw)).toBe(false)
    }
  })
})

describe('regression: fractional entry is coerced to whole dollars for display', () => {
  test('wholeDollars rounds half away from zero at scale 0', () => {
    expect(wholeDollars(100.6)).toBe(101)
    expect(wholeDollars(100.5)).toBe(101)
    expect(wholeDollars(-100.5)).toBe(-101)
    expect(wholeDollars(100.4)).toBe(100)
  })
})

describe('an ABSENT figure behaves exactly like a null one (the `non_null` wire)', () => {
  // The API omits null fields (`default-property-inclusion: non_null`), so a carried figure the server
  // has nothing for arrives as an ABSENT key, not a null one — an unsaved Schedule 2 serves
  // `"purchasedWoodOverhead": {}`, and the value reaches these helpers as `undefined`. Every guard is a
  // LOOSE `== null` for that reason.
  //
  // This is a REGRESSION TEST for a bug that reached main on 2026-08-24: with strict `=== null`,
  // `undefined` walked past the guards into `12345 + undefined`, and Schedule 2 rendered the user a
  // literal "NaN" — before they had typed anything, on any mill/year whose Schedule 3 figures are
  // absent. Nothing here may produce NaN.

  test('addN treats an absent operand as nothing to add', () => {
    expect(addN(12345, undefined)).toBe(12345) // ← the exact NaN case from the defect
    expect(addN(undefined, 12345)).toBe(12345)
    expect(addN(undefined, undefined)).toBeNull()
    expect(addN(undefined, null)).toBeNull()
  })

  test('subN keeps the asymmetry it has for null: absent minuend → null, absent subtrahend → minuend', () => {
    expect(subN(undefined, 300)).toBeNull()
    expect(subN(8000, undefined)).toBe(8000)
    expect(subN(undefined, undefined)).toBeNull()
  })

  test('sumN skips absent contributors and stays null when every one is absent', () => {
    expect(sumN(1000, undefined, 500)).toBe(1500)
    expect(sumN(undefined, undefined)).toBeNull()
  })

  test('the per-unit rules and wholeDollars refuse an absent operand rather than dividing by it', () => {
    expect(perUnitOf(500000, undefined)).toBeNull()
    expect(perUnitOf(undefined, 10000)).toBeNull()
    expect(perUnitLegacy(500000, undefined)).toBeNull()
    expect(perUnitLegacy(undefined, 10000)).toBeNull()
    expect(wholeDollars(undefined)).toBeNull()
    expect(scalingPopOf(999999, undefined, 6000000)).toBeNull()
  })

  test('sumAsZero counts an absent value as 0, like a null one', () => {
    expect(sumAsZero(1000, undefined, 500)).toBe(1500)
    expect(sumAsZero(undefined, undefined)).toBe(0)
  })

  test('no helper returns NaN for any absent/null combination', () => {
    const operands = [undefined, null, 0, 12345] as const
    for (const a of operands) {
      for (const b of operands) {
        expect(addN(a, b) ?? 0).not.toBeNaN()
        expect(subN(a, b) ?? 0).not.toBeNaN()
        expect(sumN(a, b) ?? 0).not.toBeNaN()
        expect(perUnitOf(a, b) ?? 0).not.toBeNaN()
        expect(perUnitLegacy(a, b) ?? 0).not.toBeNaN()
        expect(sumAsZero(a, b)).not.toBeNaN()
      }
    }
  })
})
