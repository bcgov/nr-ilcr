import { useEffect, useState } from 'react'
import apiService from '@/service/api-service'
import type WorkingContext from '@/interfaces/WorkingContext'

// Shared working-context fetch (GET /v1/mill-context), keyed on the MillYearContext (millId/year).
// Extracted from ContextBanner (Story 1.4) so the global banner and the per-schedule tombstone share
// one fetch and one set of stale-response guards. Returns the context ONLY when it matches the current
// (millId, year): a null context, a failed fetch, or an in-flight PREVIOUS context all resolve to null
// (the caller renders nothing), so a stale mill's data never lingers while a newer fetch is in flight.
//
// `reloadToken` re-fetches the SAME context after an action has moved a track's status, so the status
// lines catch up in place — the previous lines keep rendering until the new response lands. A re-fetch
// that fails leaves a context that was already correct standing: blanking Mill/Year and both status
// lines under a "successfully submitted" banner would take away a display nothing has invalidated.
export default function useWorkingContext(
  millId: number | null,
  year: number | null,
  reloadToken = 0,
): WorkingContext | null {
  const [context, setContext] = useState<WorkingContext | null>(null)

  useEffect(() => {
    // No working context → fire no request (legacy getSubmenuInfo returns empty when either is null —
    // UserSessionMB.java:356). The stale/null guard below already blanks the display, so just skip.
    if (millId == null || year == null) {
      return
    }

    // The `active` cleanup guard prevents a late/stale resolve from overwriting a newer context
    // (a Story 1.3 review lesson) and matches the established schedule1/home idiom.
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
        // Passive chrome: a fetch failure suppresses the display silently (AC8). No error surface.
        // Only a context already held for THIS mill/year survives it — the initial load has none.
        if (active) {
          setContext((previous) =>
            previous != null && previous.millId === millId && previous.reportYear === year
              ? previous
              : null,
          )
        }
      })
    return () => {
      active = false
    }
  }, [millId, year, reloadToken])

  // A context fetched for a PREVIOUS (millId, year) must not linger while the current fetch is in
  // flight. Derived in render — not cleared via set-state in the effect — which also suppresses an
  // out-of-contract 200 body whose millId/reportYear won't match the request.
  const stale = context != null && (context.millId !== millId || context.reportYear !== year)
  if (millId == null || year == null || !context || stale) {
    return null
  }
  return context
}
