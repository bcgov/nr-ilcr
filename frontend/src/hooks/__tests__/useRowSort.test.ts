import { act, renderHook } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { useRowSort } from '@/hooks/useRowSort'
import type { EditRow } from '@/hooks/useEditableCostRows'

const row = (key: number, description: string, cost: string): EditRow => ({
  key,
  id: key,
  description,
  values: { cost },
})

// Three rows whose costs and descriptions sort into different orders, plus a blank cost to prove
// blanks always sort last.
const seed: EditRow[] = [
  row(1, 'Banana', '30'),
  row(2, 'Apple', '10'),
  row(3, 'Cherry', ''), // blank cost
]

const extractors = {
  description: (r: EditRow) => r.description,
  cost: (r: EditRow) => (r.values.cost === '' ? null : Number(r.values.cost)),
}

const keysOf = (rows: EditRow[]) => rows.map((r) => r.key)

describe('useRowSort', () => {
  test('unsorted until a header is clicked (no default sort)', () => {
    const { result } = renderHook(() => useRowSort(seed, extractors))
    expect(keysOf(result.current.sortedRows)).toEqual([1, 2, 3])
    expect(result.current.activeKey).toBeNull()
    expect(result.current.directionFor('cost')).toBe('NONE')
  })

  test('cycles NONE → ASC → DESC → NONE on repeated clicks, blanks last', () => {
    const { result } = renderHook(() => useRowSort(seed, extractors))

    act(() => result.current.toggleSort('cost'))
    // ASC by cost: 10, 30, then blank last.
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3])
    expect(result.current.directionFor('cost')).toBe('ASC')

    act(() => result.current.toggleSort('cost'))
    // DESC by cost: 30, 10, blank STILL last (blanks ignore direction).
    expect(keysOf(result.current.sortedRows)).toEqual([1, 2, 3])
    expect(result.current.directionFor('cost')).toBe('DESC')

    act(() => result.current.toggleSort('cost'))
    // Back to the original order.
    expect(keysOf(result.current.sortedRows)).toEqual([1, 2, 3])
    expect(result.current.activeKey).toBeNull()
  })

  test('sorts descriptions alphabetically', () => {
    const { result } = renderHook(() => useRowSort(seed, extractors))
    act(() => result.current.toggleSort('description'))
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3]) // Apple, Banana, Cherry
  })

  test('order is snapshotted: editing a cell does not re-sort the row mid-edit', () => {
    const { result, rerender } = renderHook(({ rows }) => useRowSort(rows, extractors), {
      initialProps: { rows: seed },
    })
    act(() => result.current.toggleSort('cost'))
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3])

    // Row 2's cost jumps to 999 (as if the user typed) — it must NOT leap to the bottom.
    const edited = seed.map((r) => (r.key === 2 ? row(2, 'Apple', '999') : r))
    rerender({ rows: edited })
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3])
  })

  test('rows added after a sort append at the end; removed rows drop out', () => {
    const { result, rerender } = renderHook(({ rows }) => useRowSort(rows, extractors), {
      initialProps: { rows: seed },
    })
    act(() => result.current.toggleSort('cost'))
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3])

    const withAdded = [...seed, row(4, 'Date', '5')]
    rerender({ rows: withAdded })
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3, 4]) // new row appended, not re-sorted

    const withRemoved = withAdded.filter((r) => r.key !== 1)
    rerender({ rows: withRemoved })
    expect(keysOf(result.current.sortedRows)).toEqual([2, 3, 4]) // removed key dropped
  })

  // Callers build the extractor map dynamically (see Schedule3SubPage), so a header marked sortable
  // can drift out of sync with it. That must degrade to an inert header, never a TypeError.
  test('a header with no extractor is inert: no throw, no sort state', () => {
    const { result } = renderHook(() => useRowSort(seed, extractors))

    expect(() => act(() => result.current.toggleSort('crown'))).not.toThrow()
    expect(keysOf(result.current.sortedRows)).toEqual([1, 2, 3])
    expect(result.current.activeKey).toBeNull()
    expect(result.current.directionFor('crown')).toBe('NONE')

    // The unknown key must not poison the cycle for real columns either.
    act(() => result.current.toggleSort('cost'))
    expect(keysOf(result.current.sortedRows)).toEqual([2, 1, 3])
  })
})
