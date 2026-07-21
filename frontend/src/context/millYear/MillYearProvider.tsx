import type { ReactNode } from 'react'
import { useCallback, useMemo, useState } from 'react'
import MillYearContext from './MillYearContext'
import { DEFAULT_MILL_ID, DEFAULT_YEAR } from './millYearDefaults'

type Props = {
  children: ReactNode
  // Optional seed for tests (e.g. the S19 empty-context case). Defaults to 514/2021.
  initial?: { millId: number | null; year: number | null }
}

export default function MillYearProvider({ children, initial }: Props) {
  const [millId, setMillId] = useState<number | null>(initial ? initial.millId : DEFAULT_MILL_ID)
  const [year, setYear] = useState<number | null>(initial ? initial.year : DEFAULT_YEAR)

  const setContext = useCallback((nextMillId: number | null, nextYear: number | null) => {
    setMillId(nextMillId)
    setYear(nextYear)
  }, [])

  const value = useMemo(() => ({ millId, year, setContext }), [millId, year, setContext])

  return <MillYearContext value={value}>{children}</MillYearContext>
}
