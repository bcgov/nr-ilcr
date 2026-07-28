import type { FC } from 'react'
import { useEffect, useState } from 'react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import type WorkingContext from '@/interfaces/WorkingContext'
import type { TrackStatus } from '@/interfaces/WorkingContext'

// The working-context banner — the modern equivalent of the legacy `#subMenu` strip that rendered on
// every page between the main menu and the page body [submenu.xhtml:10-23]. It shows the current
// mill/year and each report track's status. Passive chrome only: it owns no interaction, surfaces no
// errors, and displays no text of its own beyond the legacy-ported labels below.
//
// Data source (Story 1.4 Pinned Decision 1): the banner fetches GET /v1/mill-context itself, keyed on
// the MillYearContext (millId/year), so a Home Save that changes the context reloads the banner
// automatically (AC7) without extending MillYearContext. It never reads or displays the `message` the
// endpoint carries on every 200 — that belongs to the Home Save action alone (AC6).

// Legacy-ported labels. These are the verbatim strings the legacy banner emitted, not new UI copy:
//   "Mill: … - Year: …"              [UserSessionMB.java:353-362]
//   "Sch 1-10 - Status: … - Date: …" [UserSessionMB.java:364-383]
//   "Sch 11 - Status: … - Date: …"   [UserSessionMB.java:385-402]
// "Not Initiated" is likewise the legacy fallback for a blank date [UserSessionMB.java:374].
const NOT_INITIATED = 'Not Initiated'

// One track's status line. Per AR6/AC2 each track renders independently by its OWN status; the two
// legacy cross-track bugs (shared 1-10 guard + crossed date pick) are deliberately not reproduced
// (recorded in Story 1.2). `description || code` is a defensive fallback for a code with no lookup row.
// `||` (not `??`) so a blank string also collapses to the fallback — legacy-faithful (legacy blank
// date → "Not Initiated", UserSessionMB.java:374); the 1.2 backend already collapses blank→null, so
// this only guards the degenerate out-of-contract case.
const statusLine = (label: 'Sch 1-10' | 'Sch 11', status: TrackStatus): string => {
  const description = status.description || status.code || ''
  const date = status.date || NOT_INITIATED
  return `${label} - Status: ${description} - Date: ${date}`
}

const ContextBanner: FC = () => {
  const { millId, year } = useMillYear()
  const [context, setContext] = useState<WorkingContext | null>(null)

  useEffect(() => {
    // No working context → fire no request (AC5; legacy getSubmenuInfo returns empty when either mill
    // or year is null — UserSessionMB.java:356). The render guard below already blanks the banner on a
    // null context, so no state change is needed here — just skip the fetch.
    if (millId == null || year == null) {
      return
    }

    // The `active` cleanup guard prevents a late/stale resolve from overwriting a newer context
    // (a 1.3 review lesson) and matches the established schedule1/home idiom.
    let active = true
    apiService
      .getAxiosInstance()
      .get<WorkingContext>(`/v1/mill-context?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setContext(response.data)
        }
      })
      .catch(() => {
        // Passive chrome: any fetch failure suppresses the banner silently (AC8). No error surface,
        // no sensitive logging (the apiService interceptor already logs status only).
        if (active) {
          setContext(null)
        }
      })
    return () => {
      active = false
    }
  }, [millId, year])

  // A context fetched for a PREVIOUS (millId, year) must not linger while the current context's
  // response is in flight (review finding: the stale-display window; house rule "clear stale UI on
  // input change"). Derived in render — not cleared via set-state in the effect — which also
  // suppresses an out-of-contract 200 body (its millId/reportYear won't match).
  const stale = context != null && (context.millId !== millId || context.reportYear !== year)

  if (millId == null || year == null || !context || stale) {
    return null
  }

  const { millNumber, millName, reportYear, schedules1To10Status, schedule11Status } = context

  return (
    <section className="context-banner" aria-label="Working context">
      {/* Defensive `?? ''` on the nullable mill columns (1.1 contract note). */}
      <p className="context-banner__line">
        {`Mill: ${millNumber ?? ''} ${millName ?? ''} - Year: ${reportYear}`}
      </p>
      {schedules1To10Status && (
        <p className="context-banner__line">{statusLine('Sch 1-10', schedules1To10Status)}</p>
      )}
      {schedule11Status && (
        <p className="context-banner__line">{statusLine('Sch 11', schedule11Status)}</p>
      )}
    </section>
  )
}

export default ContextBanner
