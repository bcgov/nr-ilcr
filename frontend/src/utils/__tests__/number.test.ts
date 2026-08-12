import { describe, expect, test } from 'vitest'
import {
  groupFixedInput,
  groupInput,
  numStrFixed,
  numStrGroup,
  parseDecimalInput,
  stripGroup,
  toNum,
} from '@/utils/number'

// The editable numeric fields now DISPLAY grouped values, so every parse of a form string has to
// tolerate separators — a regression here silently corrupts what gets saved (a grouped cost parsing
// to null would blank the field on the server), which is why the round-trip is pinned below.
describe('grouped numeric form strings', () => {
  describe('stripGroup', () => {
    test('removes separators and leaves everything else alone', () => {
      expect(stripGroup('1,234,567')).toBe('1234567')
      expect(stripGroup('-1,234.56')).toBe('-1234.56')
      expect(stripGroup('1234')).toBe('1234')
      expect(stripGroup('')).toBe('')
    })
  })

  describe('toNum', () => {
    test('parses grouped input that a bare Number() would reject as NaN', () => {
      expect(Number('1,000')).toBeNaN() // the trap this guards against
      expect(toNum('1,000')).toBe(1000)
      expect(toNum('99,999,999')).toBe(99999999)
      expect(toNum('-1,234.56')).toBe(-1234.56)
    })

    test('still parses ungrouped input and blanks', () => {
      expect(toNum('1000')).toBe(1000)
      expect(toNum('')).toBeNull()
      expect(toNum('   ')).toBeNull()
      expect(toNum('abc')).toBeNull()
    })
  })

  describe('groupInput', () => {
    test('groups the integer part', () => {
      expect(groupInput('1000')).toBe('1,000')
      expect(groupInput('99999999')).toBe('99,999,999')
      expect(groupInput('999')).toBe('999')
      expect(groupInput('-1234567')).toBe('-1,234,567')
    })

    test('is idempotent, so blurring an already-grouped value does not double up', () => {
      expect(groupInput('1,234,567')).toBe('1,234,567')
      expect(groupInput(groupInput('1234567'))).toBe('1,234,567')
    })

    test('preserves the fraction exactly as typed, including trailing zeros and a lone dot', () => {
      expect(groupInput('1234.50')).toBe('1,234.50') // NOT "1,234.5"
      expect(groupInput('1234.')).toBe('1,234.')
      expect(groupInput('0.125')).toBe('0.125')
    })

    test('passes invalid text through untouched so a typo stays visible for correction', () => {
      expect(groupInput('12abc')).toBe('12abc')
      expect(groupInput('1e5')).toBe('1e5')
      expect(groupInput('--5')).toBe('--5')
    })

    test('blank stays blank (never becomes "0")', () => {
      expect(groupInput('')).toBe('')
      expect(groupInput('   ')).toBe('')
    })
  })

  describe('numStrGroup', () => {
    test('renders a value for an input, blank for null/undefined', () => {
      expect(numStrGroup(50000)).toBe('50,000')
      expect(numStrGroup(-1234.5)).toBe('-1,234.5')
      expect(numStrGroup(0)).toBe('0')
      expect(numStrGroup(null)).toBe('')
      expect(numStrGroup(undefined)).toBe('')
    })
  })

  test('round-trip: a seeded field survives display → blur → save unchanged', () => {
    for (const value of [0, 7, 1000, 99999999, -1234.56]) {
      const displayed = numStrGroup(value)
      expect(toNum(groupInput(displayed))).toBe(value)
    }
  })

  // The fixed-decimal pair are the modern stand-in for a legacy `f:convertNumber pattern`: the MASK,
  // not the column's return shape, decides how many decimals a stored value displays with. Getting this
  // wrong is not cosmetic — a `NUMBER(7,1)` length of `12.0` renders as `12` and the reporter reads a
  // different value than the legacy screen showed them.
  describe('numStrFixed', () => {
    test('forces exactly the requested decimals, and groups', () => {
      expect(numStrFixed(12, 1)).toBe('12.0') // the case the 7B length field regressed on
      expect(numStrFixed(12.5, 1)).toBe('12.5')
      expect(numStrFixed(1234.5, 1)).toBe('1,234.5')
      expect(numStrFixed(1200, 0)).toBe('1,200')
      expect(numStrFixed(350, 0)).toBe('350') // no separator to show, which is why 7B looked fine
      expect(numStrFixed(0, 1)).toBe('0.0')
    })

    test('rounds to the mask rather than truncating', () => {
      expect(numStrFixed(12.55, 1)).toBe('12.6')
      expect(numStrFixed(1.5, 0)).toBe('2')
    })

    test('blank for null/undefined, so "not entered" never reads as 0', () => {
      expect(numStrFixed(null, 1)).toBe('')
      expect(numStrFixed(undefined, 0)).toBe('')
    })
  })

  describe('groupFixedInput', () => {
    test('re-applies the mask to typed text, as a legacy converter did on every change', () => {
      expect(groupFixedInput('1200', 0)).toBe('1,200')
      expect(groupFixedInput('12', 1)).toBe('12.0')
      expect(groupFixedInput('12.55', 1)).toBe('12.6')
    })

    test('is idempotent, so blurring an already-masked value does not drift', () => {
      expect(groupFixedInput(groupFixedInput('1200', 0), 0)).toBe('1,200')
      expect(groupFixedInput(groupFixedInput('12', 1), 1)).toBe('12.0')
      // Load-bearing: a no-op blur on an untouched row must return the identical string, or the row
      // would be marked edited merely by tabbing through it.
      expect(groupFixedInput('12.0', 1)).toBe('12.0')
      expect(groupFixedInput('1,200', 0)).toBe('1,200')
    })

    test('accepts already-grouped input rather than rejecting it as junk', () => {
      expect(groupFixedInput('1,234,567', 0)).toBe('1,234,567')
    })

    test('passes invalid text through untouched so a typo stays visible for correction', () => {
      expect(groupFixedInput('abc', 0)).toBe('abc')
      expect(groupFixedInput('12.', 1)).toBe('12.')
    })

    test('blank stays blank (never becomes "0" or "0.0")', () => {
      expect(groupFixedInput('', 1)).toBe('')
      expect(groupFixedInput('   ', 0)).toBe('')
    })

    test('round-trip: a masked field still parses back to the number it displays', () => {
      for (const [value, digits] of [
        [12, 1],
        [1200, 0],
        [999999.9, 1],
        [-1234, 0],
      ] as const) {
        expect(parseDecimalInput(numStrFixed(value, digits))).toBe(value)
      }
    })
  })
})
