import { useMemo, useRef, useState } from 'react'

export type SortDirection = 'NONE' | 'ASC' | 'DESC'

/** A cell's sort key: numbers sort numerically, strings alphabetically; null/blank always sort last. */
export type SortValue = number | string | null | undefined

/** A row's stable identity, used to snapshot and restore display order. */
export type RowIdentity = number | string

/** Per-column value extractors keyed by the column's sort key (e.g. `'description'`, `'total'`). */
export type SortExtractors<T> = Record<string, (row: T) => SortValue>

export interface RowSort<T> {
  /**
   * The rows in display order (the SAME array reference as the input while no sort is active).
   *
   * `readonly` on purpose: it may be the caller's own array, so an in-place `.sort()` here would
   * reorder a component's props under it.
   */
  sortedRows: readonly T[]
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
 * Client-side, single-column table sort, mirroring the legacy PrimeFaces header-click sort: the whole
 * list is already loaded, so sorting is in-memory and on demand with no default sort. The order is
 * SNAPSHOTTED at click time (by row identity) so editing a cell does not re-sort and yank the row out
 * from under the cursor — legacy re-evaluates the sort key only on the next header click. Rows added
 * after the snapshot append at the end; removed rows drop out. Blank/absent values always sort last,
 * regardless of direction.
 *
 * Generic over the row type (Story 19.2): the editable cost sub-pages sort `EditRow`s keyed by
 * `row.key`, while the Mill Status Report sorts read-only DTO rows keyed by `row.millId`. `keyOf`
 * supplies that identity, so the hook never has to know the shape of a row.
 *
 * @param rows the rows in the order the server returned them
 * @param extractors per-column value extractors, keyed by the column's sort key
 * @param keyOf a row's STABLE identity — it must not change while the row is on screen, or the
 *   snapshotted order cannot find it again
 */
export function useRowSort<T>(
  rows: readonly T[],
  extractors: SortExtractors<T>,
  keyOf: (row: T) => RowIdentity,
): RowSort<T> {
  const [activeKey, setActiveKey] = useState<string | null>(null)
  const [direction, setDirection] = useState<SortDirection>('NONE')
  // Frozen display order (row keys) captured at the last header click; null means unsorted.
  const [order, setOrder] = useState<RowIdentity[] | null>(null)
  // keyOf lives in a ref, not in the memo's dependency list. Every call site passes an inline arrow,
  // so a fresh identity arrives on each render; depending on it would rebuild `sortedRows` — and
  // hand out a NEW array — every render. The editable cost sub-pages mount an editor per row and are
  // the documented cause of frontend CI timeouts, so that is a cost worth avoiding. A ref is honest
  // rather than suppressed: the value is read at call time, and it is a pure identity reader whose
  // result cannot change for the same row.
  const keyRef = useRef(keyOf)
  keyRef.current = keyOf

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
      .map(keyRef.current)
    setOrder(snapshot)
  }

  const sortedRows = useMemo(() => {
    if (!order) {
      return rows
    }
    const identify = keyRef.current
    const byKey = new Map(rows.map((r) => [identify(r), r]))
    const ordered = order.map((k) => byKey.get(k)).filter((r): r is T => r !== undefined)
    const seen = new Set(order)
    const appended = rows.filter((r) => !seen.has(identify(r)))
    return [...ordered, ...appended]
  }, [rows, order])

  return {
    sortedRows,
    activeKey,
    directionFor: (key) => (activeKey === key ? direction : 'NONE'),
    toggleSort,
  }
}
