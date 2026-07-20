import { createContext } from 'react'

export type MillYearContextValue = {
  millId: number | null
  year: number | null
  setContext: (millId: number | null, year: number | null) => void
}

const MillYearContext = createContext<MillYearContextValue | undefined>(undefined)

export default MillYearContext
