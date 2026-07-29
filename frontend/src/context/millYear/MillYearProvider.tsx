import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import MillYearContext from './MillYearContext'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from './millYearDefaults'

type Props = {
  children: ReactNode
  // Optional seed for tests (e.g. the S19 empty-context case). Defaults to DEFAULT_MILL_ID/YEAR.
  initial?: { millId: number | null; year: number | null }
}

const STORAGE_KEY = 'ilcr:mill-year-context'

type StoredContext = { millId: number | null; year: number | null }

const isValidNullableNumber = (value: unknown): value is number | null =>
  value === null || typeof value === 'number'

function getDefaultContext(): StoredContext {
  return { millId: DEFAULT_MILL_ID, year: DEFAULT_YEAR }
}

function readStoredContext(): StoredContext {
  if (typeof window === 'undefined') {
    return getDefaultContext()
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return getDefaultContext()
    }
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') {
      return getDefaultContext()
    }

    const { millId, year } = parsed as Partial<StoredContext>
    if (!isValidNullableNumber(millId) || !isValidNullableNumber(year)) {
      return getDefaultContext()
    }

    return { millId, year }
  } catch {
    return getDefaultContext()
  }
}

export default function MillYearProvider({ children, initial }: Props) {
  const [millId, setMillId] = useState<number | null>(() =>
    initial ? initial.millId : readStoredContext().millId,
  )
  const [year, setYear] = useState<number | null>(() =>
    initial ? initial.year : readStoredContext().year,
  )

  const setContext = useCallback((nextMillId: number | null, nextYear: number | null) => {
    setMillId(nextMillId)
    setYear(nextYear)
  }, [])

  useEffect(() => {
    if (initial || typeof window === 'undefined') {
      return
    }

    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ millId, year }))
  }, [initial, millId, year])

  const value = useMemo(() => ({ millId, year, setContext }), [millId, year, setContext])

  return <MillYearContext value={value}>{children}</MillYearContext>
}
