import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import LayoutContext from './LayoutContext'

// Carbon's `lg` breakpoint (66rem / 1056px — @carbon/grid/scss/_config.scss). Kept in rem, and named,
// so this query and the `breakpoint('lg')` block in components/Layout/index.scss cannot drift apart.
const LARGE_VIEWPORT_QUERY = '(min-width: 66rem)'

function matchesLargeViewport(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }

  return window.matchMedia(LARGE_VIEWPORT_QUERY).matches
}

export default function LayoutProvider({ children }: { children: ReactNode }) {
  // Lazy initialisers so the FIRST paint is already correct. Expanding in an effect instead would
  // render a collapsed nav and then open it, flashing on every desktop load (#316 AC1/AC2).
  const [isLargeViewport, setIsLargeViewport] = useState(matchesLargeViewport)
  const [isSideNavExpanded, setIsSideNavExpanded] = useState(matchesLargeViewport)

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return
    }

    const query = window.matchMedia(LARGE_VIEWPORT_QUERY)
    // Fires only when the viewport CROSSES 66rem. Each crossing resettles the nav to that viewport's
    // default — expanded at lg+, collapsed below — so a window dragged narrow never leaves a 16rem
    // panel covering the page. A manual toggle holds until the next crossing (#316 AC8).
    const handleViewportChange = (event: MediaQueryListEvent) => {
      setIsLargeViewport(event.matches)
      setIsSideNavExpanded(event.matches)
    }

    query.addEventListener('change', handleViewportChange)
    return () => query.removeEventListener('change', handleViewportChange)
  }, [])

  const value = useMemo(
    () => ({
      closeSideNav: () => setIsSideNavExpanded(false),
      isLargeViewport,
      isSideNavExpanded,
      toggleSideNav: () => setIsSideNavExpanded((current) => !current),
    }),
    [isLargeViewport, isSideNavExpanded],
  )

  return <LayoutContext value={value}>{children}</LayoutContext>
}
