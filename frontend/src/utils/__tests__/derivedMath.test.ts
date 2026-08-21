import { addN, halfUp, perUnitOf, subN, sumAsZero, wholeDollars } from '@/utils/derivedMath'

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

describe('sumAsZero', () => {
  test('treats null as 0', () => {
    expect(sumAsZero(100, null, 250)).toBe(350)
  })

  test('is 0 — not null — when every operand is null', () => {
    expect(sumAsZero(null, null)).toBe(0)
    expect(sumAsZero()).toBe(0)
  })
})
