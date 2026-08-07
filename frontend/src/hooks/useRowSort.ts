import { useMemo, useState } from 'react'
import type { EditRow } from '@/hooks/useEditableCostRows'

export type SortDirection = 'NONE' | 'ASC' | 'DESC'

/** A cell's sort key: numbers sort numerically, strings alphabetically; null/blank always sort last. */
export type SortValue = number | string | null | undefined

/** Per-column value extractors keyed by the column's sort key (e.g. `'description'`, `'total'`). */
export type SortExtractors = Record<string, (row: EditRow) => SortValue>

export interface RowSort {
  /** The rows in display order (unchanged from input while no sort is active). */
  sortedRows: EditRow[]
  /** The active sort column's key, or null when unsorted. */
  activeKey: string | null
  /** The sort direction to hand a Carbon `TableHeader` for the given column. */
  directionFor: (key: string) => SortDirection
  /** Cycle a column's sort: NONE → ASC → DESC → NONE. */
  toggleSort: (key: string) => void
}

const isBlank = (v: SortValue): boolean => v === null || v === undefined || v === ''

/** Next direction in the header-click cycle: NONE → ASC → DESC → NONE, restarting at ASC on a new column. */
const nextDirection = (sameColumn: boolean, current: SortDirection): SortDirection => {
  if (!sameColumn) {
    return 'ASC'
  }
  if (current === 'ASC') {
    return 'DESC'
  }
  if (current === 'DESC') {
    return 'NONE'
  }
  return 'ASC'
}

/** Compare two present values; missing values are handled by the caller (always last). */
const compareValues = (a: SortValue, b: SortValue): number => {
  if (typeof a === 'number' && typeof b === 'number') {
    return a - b
  }
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' })
}

/**
 * Client-side, single-column table sort for the editable cost sub-pages, mirroring the legacy
 * PrimeFaces header-click sort: the whole list is already loaded, so sorting is in-memory and on
 * demand with no default sort. The order is SNAPSHOTTED at click time (by row identity) so editing a
 * cell does not re-sort and yank the row out from under the cursor — legacy re-evaluates the sort key
 * only on the next header click. Rows added after the snapshot append at the end; removed rows drop
 * out. Blank/absent values always sort last, regardless of direction.
 */
export function useRowSort(rows: EditRow[], extractors: SortExtractors): RowSort {
  const [activeKey, setActiveKey] = useState<string | null>(null)
  const [direction, setDirection] = useState<SortDirection>('NONE')
  // Frozen display order (row keys) captured at the last header click; null means unsorted.
  const [order, setOrder] = useState<number[] | null>(null)

  const toggleSort = (key: string) => {
    // A header with no extractor cannot be sorted — ignore the click rather than throwing (or
    // parking the table in an "active sort" state that never reorders anything).
    if (!extractors[key]) {
      return
    }
    const next = nextDirection(activeKey === key, direction)
    setDirection(next)
    setActiveKey(next === 'NONE' ? null : key)
    if (next === 'NONE') {
      setOrder(null)
      return
    }
    const extract = extractors[key]
    const sign = next === 'ASC' ? 1 : -1
    const snapshot = [...rows]
      .sort((a, b) => {
        const av = extract(a)
        const bv = extract(b)
        const aBlank = isBlank(av)
        const bBlank = isBlank(bv)
        if (aBlank && bBlank) return 0
        if (aBlank) return 1 // blanks last, independent of sign
        if (bBlank) return -1
        return compareValues(av, bv) * sign
      })
      .map((r) => r.key)
    setOrder(snapshot)
  }

  const sortedRows = useMemo(() => {
    if (!order) {
      return rows
    }
    const byKey = new Map(rows.map((r) => [r.key, r]))
    const ordered = order.map((k) => byKey.get(k)).filter((r): r is EditRow => r !== undefined)
    const seen = new Set(order)
    const appended = rows.filter((r) => !seen.has(r.key))
    return [...ordered, ...appended]
  }, [rows, order])

  return {
    sortedRows,
    activeKey,
    directionFor: (key) => (activeKey === key ? direction : 'NONE'),
    toggleSort,
  }
}
