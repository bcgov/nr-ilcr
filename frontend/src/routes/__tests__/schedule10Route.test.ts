import { describe, expect, test, vi } from 'vitest'

// The real createFileRoute demands a generated route tree; capturing the options object is all this
// suite needs — validateSearch is a pure function and is tested as one. It is the only thing between
// a garbage URL and a road level mounted against a page that cannot exist.
vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => options,
}))
vi.mock('@/components/schedule10', () => ({ default: () => null }))

import { Route } from '@/routes/schedule-10'

type Search = { pageId?: number }
const validateSearch = (
  Route as unknown as { validateSearch: (search: Record<string, unknown>) => Search }
).validateSearch

describe('schedule-10 validateSearch', () => {
  test('a positive integer page id passes through', () => {
    expect(validateSearch({ pageId: '8900' })).toEqual({ pageId: 8900 })
    expect(validateSearch({ pageId: 12 })).toEqual({ pageId: 12 })
  })

  test('fractional, negative, zero and non-numeric ids fall back to the page list', () => {
    expect(validateSearch({ pageId: '3.5' }).pageId).toBeUndefined()
    expect(validateSearch({ pageId: -1 }).pageId).toBeUndefined()
    expect(validateSearch({ pageId: 0 }).pageId).toBeUndefined()
    expect(validateSearch({ pageId: 'abc' }).pageId).toBeUndefined()
    expect(validateSearch({ pageId: '' }).pageId).toBeUndefined()
    expect(validateSearch({ pageId: null }).pageId).toBeUndefined()
  })

  test('absent params yield an empty search', () => {
    expect(validateSearch({})).toEqual({ pageId: undefined })
  })

  test('unrelated params are dropped rather than carried', () => {
    expect(validateSearch({ pageId: 8900, roadDetailId: 5 })).toEqual({ pageId: 8900 })
  })
})
