import { createContext } from 'react'

export type LayoutContextValue = {
  closeSideNav: () => void
  isSideNavExpanded: boolean
  toggleSideNav: () => void
}

const LayoutContext = createContext<LayoutContextValue | undefined>(undefined)

export default LayoutContext
