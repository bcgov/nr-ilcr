import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import LayoutContext from './LayoutContext'

// Carbon's `lg` breakpoint (66rem / 1056px — @carbon/grid/scss/_config.scss). Kept in rem, and named,
// so this query and the `breakpoint('lg')` block in components/Layout/index.scss cannot drift apart.
// Exported so the SCSS tripwire test can assert the two really do still agree.
export const LARGE_VIEWPORT_QUERY = '(min-width: 66rem)'

// No `typeof window.matchMedia !== 'function'` guard. One was here, justified as "a degraded nav
// beats a blank page" — but it protected nothing: Carbon's own SideNav calls `window.matchMedia`
// unguarded in this same tree (`@carbon/react/lib/internal/useMatchMedia.js:28`), so a browser
// without the API blanks the page regardless. The guard only bought false comfort and an untestable
// branch. The story's implementation note called this exactly right: prefer the test stub, because a
// runtime guard that exists only for tests hides real breakage.
function matchesLargeViewport(): boolean {
  return window.matchMedia(LARGE_VIEWPORT_QUERY).matches
}

// Collapsing the nav can strand keyboard focus: below `lg` Carbon marks the collapsed panel `inert`,
// and the browser then blurs whatever was focused inside it to <body> with nothing announced and no
// route back. Hand focus to the control that reopens it instead. Touching the DOM directly is
// deliberate — the provider sits above the header and holds no ref to that button, and this is a
// focus rescue rather than rendering state. A no-op when focus was not inside the nav.
function rescueFocusFromSideNav(): void {
  if (!document.activeElement?.closest('.cds--side-nav')) {
    return
  }

  document.querySelector<HTMLElement>('.cds--header__menu-toggle')?.focus()
}

export default function LayoutProvider({ children }: { children: ReactNode }) {
  // Lazy initialisers so the FIRST paint is already correct. Expanding in an effect instead would
  // render a collapsed nav and then open it, flashing on every desktop load (#316 AC1/AC2).
  const [isLargeViewport, setIsLargeViewport] = useState(matchesLargeViewport)
  const [isSideNavExpanded, setIsSideNavExpanded] = useState(matchesLargeViewport)
  // Set once the user states a preference (toggle, Escape, overlay click). From then on the nav is
  // their call and a breakpoint crossing stops overriding it. Without this, browser zoom — which
  // moves the CSS-px viewport with no window resize at all — reopens a nav somebody deliberately
  // collapsed to read a wide schedule table, and a scrollbar appearing near the breakpoint can
  // oscillate the nav open and shut.
  const hasManualPreferenceRef = useRef(false)

  useEffect(() => {
    const query = window.matchMedia(LARGE_VIEWPORT_QUERY)

    // Called both from the `change` listener and once immediately after subscribing. The
    // set-state-in-effect rule is suppressed rather than worked around: on the mount path the value
    // is almost always unchanged, so React bails out on Object.is and there is no extra render —
    // and when it HAS changed, re-rendering is precisely the point.
    const applyViewport = (matches: boolean) => {
      // eslint-disable-next-line @eslint-react/set-state-in-effect
      setIsLargeViewport(matches)

      if (hasManualPreferenceRef.current) {
        return
      }

      // eslint-disable-next-line @eslint-react/set-state-in-effect
      setIsSideNavExpanded(matches)
      if (!matches) {
        rescueFocusFromSideNav()
      }
    }

    const handleViewportChange = (event: MediaQueryListEvent) => applyViewport(event.matches)

    query.addEventListener('change', handleViewportChange)
    // Re-read AFTER subscribing: a crossing landing between the lazy initialisers (render phase) and
    // this effect (commit) would otherwise be lost until the next crossing. Carbon's own
    // useMatchMedia closes the same gap the same way.
    applyViewport(query.matches)

    return () => query.removeEventListener('change', handleViewportChange)
  }, [])

  const closeSideNav = useCallback(() => {
    setIsSideNavExpanded(false)
    rescueFocusFromSideNav()
  }, [])

  const dismissSideNav = useCallback(() => {
    hasManualPreferenceRef.current = true
    closeSideNav()
  }, [closeSideNav])

  // No focus rescue here: the toggle is the element the user just activated, so focus is already on
  // it rather than inside the panel, and at lg+ Carbon does not apply `inert` at all.
  const toggleSideNav = useCallback(() => {
    hasManualPreferenceRef.current = true
    setIsSideNavExpanded((current) => !current)
  }, [])

  // Carbon's own Escape handler sits on the <nav> element, so it only fires when focus is already
  // inside the panel. The ordinary path — open with the hamburger, then press Escape — leaves focus
  // on the button, where Carbon never sees the key. Handle it at the window instead.
  //
  // Scoped to focus within the header (the nav is a child of it, as is the toggle) so this cannot
  // steal Escape from a Carbon Modal, which traps focus inside its own dialog.
  useEffect(() => {
    if (!isSideNavExpanded) {
      return
    }

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || !document.activeElement?.closest('.cds--header')) {
        return
      }
      dismissSideNav()
    }

    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [dismissSideNav, isSideNavExpanded])

  const value = useMemo(
    () => ({
      closeSideNav,
      dismissSideNav,
      isLargeViewport,
      isSideNavExpanded,
      toggleSideNav,
    }),
    [closeSideNav, dismissSideNav, isLargeViewport, isSideNavExpanded, toggleSideNav],
  )

  return <LayoutContext value={value}>{children}</LayoutContext>
}
