import { use } from 'react'
import MillYearContext from './MillYearContext'

export default function useMillYear() {
  const context = use(MillYearContext)

  if (!context) {
    throw new Error('useMillYear must be used inside MillYearProvider')
  }

  return context
}
