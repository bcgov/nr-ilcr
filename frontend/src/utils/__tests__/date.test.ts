import { describe, expect, it } from 'vitest'
import { legacyDate } from '@/utils/date'

describe('legacyDate', () => {
  it('re-spells an ISO date as dd/MM/yyyy', () => {
    expect(legacyDate('2026-03-04')).toBe('04/03/2026')
  })

  it('returns null for absent values', () => {
    expect(legacyDate(null)).toBeNull()
    expect(legacyDate(undefined)).toBeNull()
    expect(legacyDate('')).toBeNull()
  })

  it('re-spells whatever three hyphen-separated parts it is given, even nonsense', () => {
    // The brief's own test asserted a pass-through here ('not-a-date' -> 'not-a-date'), but the
    // shipped function (copied verbatim, not rewritten) has no validation: 'not-a-date'.split('-')
    // is ['not', 'a', 'date'], all three truthy, so it re-spells them positionally regardless of
    // content.
    expect(legacyDate('not-a-date')).toBe('date/a/not')
  })

  it('passes through a value with no hyphen at all', () => {
    expect(legacyDate('nodate')).toBe('nodate')
  })

  it('does not shift the day west of UTC', () => {
    // new Date('2026-01-01') is UTC midnight, which is 31/12/2025 in Vancouver.
    // This helper re-spells the parts and must never construct a Date.
    expect(legacyDate('2026-01-01')).toBe('01/01/2026')
  })
})
