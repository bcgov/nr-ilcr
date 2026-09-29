import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import Mills from '@/components/mills'

// The Users page's per-row `View` hands a mill over here (UC-MILL-002 S01, legacy users.xhtml:85),
// the reverse leg of the userGuid hand-off in routes/mill-associations.tsx and on the same
// coerce-and-whitelist, consume-and-clear shape.
export type MillsSearch = {
  millId?: number
}

const millsRoute = getRouteApi('/mills')

/**
 * Reads the carried mill and CONSUMES it, so a later open of this page never reuses a stale
 * selection (UC-MILL-002 BR-02). The router read lives here, not inside `Mills`, for the reason
 * routes/mill-associations.tsx gives: as an optional prop the hand-off is inert by construction
 * when absent.
 */
const MillsRoute: FC = () => {
  const { millId } = millsRoute.useSearch()
  const navigate = millsRoute.useNavigate()

  // Captured at first render and never re-read: the effect below strips the param immediately.
  const [carried] = useState(millId)

  useEffect(() => {
    if (millId == null) return
    // `replace`, so Back returns to the Users page rather than re-entering this one and
    // re-selecting a mill the administrator has already moved away from.
    navigate({ search: {}, replace: true })
  }, [millId, navigate])

  return <Mills carriedMillId={carried} />
}

export const Route = createFileRoute('/mills')({
  validateSearch: (search: Record<string, unknown>): MillsSearch => {
    const raw = search.millId
    // A positive whole number, or nothing: the page spends it on GET /v1/admin/mills/{millId},
    // whose path variable is a `long`, so anything else is a request that can only 400 — dropped
    // here instead, which lands the page in its ordinary Select/Import state.
    const isId = typeof raw === 'number' && Number.isSafeInteger(raw) && raw > 0
    return { millId: isId ? raw : undefined }
  },
  component: MillsRoute,
})
