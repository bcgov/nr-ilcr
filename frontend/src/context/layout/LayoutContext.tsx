import { createContext } from 'react'

export type LayoutContextValue = {
  // Incidental close — used when navigating below `lg`, where the nav is an overlay covering the
  // page just navigated to. NOT treated as a preference: a user who taps a link on a phone and later
  // opens the app on a desktop should still get the expanded default.
  closeSideNav: () => void
  // Deliberate dismissal — Escape, or a click on the overlay. This IS a preference, so it stops a
  // later breakpoint crossing from reopening the nav (see LayoutProvider).
  dismissSideNav: () => void
  // True at Carbon's `lg` breakpoint and above. Consumers use it to tell the two side-nav modes
  // apart: at lg+ the nav sits beside the page, below lg it is an overlay on top of it.
  isLargeViewport: boolean
  isSideNavExpanded: boolean
  toggleSideNav: () => void
}

const LayoutContext = createContext<LayoutContextValue | undefined>(undefined)

export default LayoutContext
