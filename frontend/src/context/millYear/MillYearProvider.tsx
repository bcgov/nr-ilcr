import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import MillYearContext from './MillYearContext'

type Props = {
  children: ReactNode
  // Optional explicit seed, used by tests that need a known context without going through Home.
  // Absent, the context starts EMPTY unless local storage holds a previous selection.
  initial?: { millId: number | null; year: number | null }
}

const STORAGE_KEY = 'ilcr:mill-year-context'

type StoredContext = { millId: number | null; year: number | null }

const isValidNullableNumber = (value: unknown): value is number | null =>
  value === null || typeof value === 'number'

// No working context until the user picks one on Home. This used to seed the 13050/2017 dev default,
// which meant the app ALWAYS had a context: Home's AC4/S03 reflection then displayed it, so the
// "Select Mill" / "Select Reporting Year" placeholders were unreachable and a new user landed on
// someone else's mill. The default was a scaffold from before the Home selector existed.
// A previous selection still returns via local storage (readStoredContext), so this only changes a
// first-ever visit — the AC4/S03 reflection of a saved context is untouched.
function getDefaultContext(): StoredContext {
  return { millId: null, year: null }
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
