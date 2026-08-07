import { describe, expect, test } from 'vitest'
import { groupInput, numStrGroup, stripGroup, toNum } from '@/utils/number'

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
})
