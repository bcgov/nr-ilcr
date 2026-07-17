import { use } from 'react'
import LayoutContext from './LayoutContext'

export default function useLayout() {
  const context = use(LayoutContext)

  if (!context) {
    throw new Error('useLayout must be used inside LayoutProvider')
  }

  return context
}
