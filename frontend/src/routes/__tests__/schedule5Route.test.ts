import { describe, expect, test, vi } from 'vitest'

// The real createFileRoute demands a generated route tree; capturing the options object is all this
// suite needs — validateSearch is a pure function and is tested as one. Previously it had zero
// coverage, and the component indexes SUB_PAGE_DEFS[sub] unguarded, so this whitelist is the only
// thing between a garbage URL and a runtime TypeError (the verification-gap review finding,
// 2026-08-12).
vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => options,
}))
vi.mock('@/components/schedule5', () => ({ default: () => null }))

import { Route } from '@/routes/schedule-5'

type Search = { camp?: number; sub?: 'CAMP' | 'ACCESS' }
const validateSearch = (
  Route as unknown as { validateSearch: (search: Record<string, unknown>) => Search }
).validateSearch

describe('schedule-5 validateSearch', () => {
  test('a positive integer camp and a whitelisted sub pass through', () => {
    expect(validateSearch({ camp: '8401', sub: 'CAMP' })).toEqual({ camp: 8401, sub: 'CAMP' })
    expect(validateSearch({ camp: 12, sub: 'ACCESS' })).toEqual({ camp: 12, sub: 'ACCESS' })
  })

  test('fractional, negative, zero and non-numeric camps fall back to the camp list', () => {
    // Number.isFinite alone admitted 3.5 and -1, which mounted the sub-page and produced a server
    // 400 instead of the promised fallback (the review finding this pins).
    expect(validateSearch({ camp: '3.5', sub: 'CAMP' }).camp).toBeUndefined()
    expect(validateSearch({ camp: -1, sub: 'CAMP' }).camp).toBeUndefined()
    expect(validateSearch({ camp: 0, sub: 'CAMP' }).camp).toBeUndefined()
    expect(validateSearch({ camp: 'abc', sub: 'CAMP' }).camp).toBeUndefined()
    expect(validateSearch({ camp: '', sub: 'CAMP' }).camp).toBeUndefined()
  })

  test('an unknown sub is dropped — SUB_PAGE_DEFS is indexed with it unguarded', () => {
    expect(validateSearch({ camp: 8401, sub: 'BOGUS' }).sub).toBeUndefined()
    expect(validateSearch({ camp: 8401 }).sub).toBeUndefined()
  })

  test('absent params yield an empty search', () => {
    expect(validateSearch({})).toEqual({ camp: undefined, sub: undefined })
  })
})
