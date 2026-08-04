import { describe, expect, it } from 'vitest'
import { parseDecimalInput, validateLocation, SILV_MESSAGES } from '../validation'
import type { LocationFormValues } from '../validation'

// A fully-valid location; individual tests override the one field under test so validateLocation
// returns only that field's error (or {}).
const validForm = (overrides: Partial<LocationFormValues> = {}): LocationFormValues => ({
  location: 'North Ridge',
  enhanced: false,
  bec: { id: 321, label: 'ICHdw1' },
  netArea: '120.5',
  actualCost: '25000',
  plannedCost: '10000',
  comments: '',
  ...overrides,
})

// The legacy JSF fields (NAR, costs) bound through a US-locale DecimalFormat, NOT JS Number(). These
// lock the two ways Number() diverges from that format: grouped input the legacy app accepts, and
// JS-only syntax (exponent, hex, Infinity) the legacy app rejects but Number() silently coerces.
describe('parseDecimalInput (legacy DecimalFormat parity)', () => {
  it('accepts comma-grouped values Number() would reject as NaN', () => {
    expect(parseDecimalInput('1,000')).toBe(1000)
    expect(parseDecimalInput('1,000,000')).toBe(1000000)
    expect(parseDecimalInput('10,000.5')).toBe(10000.5)
    expect(parseDecimalInput('-99,999,999')).toBe(-99999999)
  })

  it('accepts ordinary plain and decimal values', () => {
    expect(parseDecimalInput('1000')).toBe(1000)
    expect(parseDecimalInput('1000.50')).toBe(1000.5)
    expect(parseDecimalInput('0')).toBe(0)
    expect(parseDecimalInput('-500')).toBe(-500)
    expect(parseDecimalInput('  42  ')).toBe(42)
  })

  it('returns null for blank input', () => {
    expect(parseDecimalInput('')).toBeNull()
    expect(parseDecimalInput('   ')).toBeNull()
  })

  it('rejects JS-only numeric syntax that Number() silently coerces', () => {
    expect(parseDecimalInput('1e2')).toBeNull() // Number('1e2') === 100
    expect(parseDecimalInput('0x10')).toBeNull() // Number('0x10') === 16
    expect(parseDecimalInput('Infinity')).toBeNull()
    expect(parseDecimalInput('-Infinity')).toBeNull()
    expect(parseDecimalInput('0b101')).toBeNull()
    expect(parseDecimalInput('1_000')).toBeNull()
  })

  it('rejects non-numeric and malformed input', () => {
    expect(parseDecimalInput('abc')).toBeNull()
    expect(parseDecimalInput('1.2.3')).toBeNull()
    expect(parseDecimalInput('12,34')).toBeNull() // grouping must be 3-digit groups
    expect(parseDecimalInput('1,00')).toBeNull()
    expect(parseDecimalInput('+100')).toBeNull() // DecimalFormat has no positive prefix
  })
})

// validateLocation must gate on the SAME parser, so grouped input passes and JS-only syntax fails —
// mirroring the backend SilvicultureLocationRequest ranges/messages (AD-8).
describe('validateLocation numeric fields use the decimal parser', () => {
  it('accepts a comma-grouped cost within range', () => {
    expect(validateLocation(validForm({ actualCost: '1,000' })).actualCost).toBeUndefined()
    expect(validateLocation(validForm({ plannedCost: '99,999,999' })).plannedCost).toBeUndefined()
  })

  it('rejects exponent / hex cost that Number() would have accepted', () => {
    expect(validateLocation(validForm({ actualCost: '1e2' })).actualCost).toBe(
      SILV_MESSAGES.costValidator,
    )
    expect(validateLocation(validForm({ plannedCost: '0x10' })).plannedCost).toBe(
      SILV_MESSAGES.costValidator,
    )
  })

  it('rejects an out-of-range cost', () => {
    expect(validateLocation(validForm({ actualCost: '100000000' })).actualCost).toBe(
      SILV_MESSAGES.costValidator,
    )
  })

  it('accepts a comma-grouped NAR with one decimal place', () => {
    expect(validateLocation(validForm({ netArea: '1,000.5' })).netArea).toBeUndefined()
    expect(validateLocation(validForm({ netArea: '10,000' })).netArea).toBeUndefined()
  })

  it('rejects exponent / hex NAR that Number() would have accepted', () => {
    expect(validateLocation(validForm({ netArea: '1e2' })).netArea).toBe(SILV_MESSAGES.netAreaRange)
    expect(validateLocation(validForm({ netArea: '0x10' })).netArea).toBe(
      SILV_MESSAGES.netAreaRange,
    )
  })

  it('still enforces the one-decimal NAR cap on grouped input', () => {
    expect(validateLocation(validForm({ netArea: '1,000.25' })).netArea).toBe(
      SILV_MESSAGES.netAreaRange,
    )
  })
})
