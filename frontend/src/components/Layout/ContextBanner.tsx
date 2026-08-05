import type { FC } from 'react'
import useMillYear from '@/context/millYear/useMillYear'
import useWorkingContext from '@/components/core/WorkingContext/useWorkingContext'
import WorkingContextLines from '@/components/core/WorkingContext/WorkingContextLines'

// The working-context banner — the modern equivalent of the legacy `#subMenu` strip that rendered on
// every page between the main menu and the page body [submenu.xhtml:10-23]. It shows the current
// mill/year and each report track's status. Passive chrome only: it owns no interaction, surfaces no
// errors, and displays no text of its own beyond the legacy-ported labels.
//
// Data source (Story 1.4 Pinned Decision 1): the shared useWorkingContext hook fetches
// GET /v1/mill-context keyed on the MillYearContext (millId/year), so a Home Save that changes the
// context reloads the banner automatically (AC7) without extending MillYearContext. It never reads or
// displays the `message` the endpoint carries on every 200 — that belongs to the Home Save action
// alone (AC6). The line text lives in the shared WorkingContextLines (also used by the tombstone).
const ContextBanner: FC = () => {
  const { millId, year } = useMillYear()
  const context = useWorkingContext(millId, year)

  // Null context (AC5) / failed fetch (AC8) / stale in-flight window (AC7) → render nothing.
  if (!context) {
    return null
  }

  return (
    <section className="context-banner" aria-label="Working context">
      <WorkingContextLines context={context} lineClassName="context-banner__line" />
    </section>
  )
}

export default ContextBanner
