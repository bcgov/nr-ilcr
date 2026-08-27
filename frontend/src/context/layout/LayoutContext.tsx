import { createContext } from 'react'

export type LayoutContextValue = {
  closeSideNav: () => void
  // True at Carbon's `lg` breakpoint and above. Consumers use it to tell the two side-nav modes
  // apart: at lg+ the nav sits beside the page, below lg it is an overlay on top of it.
  isLargeViewport: boolean
  isSideNavExpanded: boolean
  toggleSideNav: () => void
}

const LayoutContext = createContext<LayoutContextValue | undefined>(undefined)

export default LayoutContext
