import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { createFileRoute, getRouteApi } from '@tanstack/react-router'
import MillAssociations from '@/components/millAssociations'

// The Mills page's per-row `View` hands a user over here (UC-MILL-001 S10) through a URL search
// param, on the coerce-and-whitelist shape of routes/schedule-5.tsx:14-26. A search param rather
// than TanStack's `state:` option because cross-page state has no other precedent in this repo and
// a param survives a reload and a shared link, which `state` does not.
//
// This is the half Story 23.3 deferred as AC10 because the Mills page did not exist yet; the
// REVERSE leg (this page handing a mill over to Mills) stays with 23.3's own AC10 work.
export type MillAssociationsSearch = {
  userGuid?: string
}

/**
 * The directory GUID's actual shape — 32 hex characters — not merely the backend's
 * @Size(min = 32, max = 32) width. Length alone would pass any 32 characters through to a lookup
 * that can only miss, producing exactly the unrequested error banner this whitelist exists to
 * prevent. Case-insensitive because GUID casing is not canonical upstream.
 */
const USER_GUID_PATTERN = /^[0-9A-Fa-f]{32}$/

const millAssociationsRoute = getRouteApi('/mill-associations')

/**
 * Reads the carried user and CONSUMES it, so a later open of this page never reuses a stale
 * selection (UC-MILL-002 BR-02).
 *
 * <p>The router read lives here rather than inside `MillAssociations` on purpose. That component is
 * a shipped surface with ~30 tests that render it bare, outside any router; a `useSearch()` inside
 * it would throw in every one of them. As an optional prop the hand-off is inert by construction
 * when it is absent — which is the whole no-param contract — instead of inert by a mock.
 */
const MillAssociationsRoute: FC = () => {
  const { userGuid } = millAssociationsRoute.useSearch()
  const navigate = millAssociationsRoute.useNavigate()

  // Captured at first render and never re-read: the effect below strips the param immediately, and
  // a prop that followed the URL would hand the page an undefined the instant it was consumed.
  const [carried] = useState(userGuid)

  useEffect(() => {
    if (!userGuid) return
    // `replace`, so Back returns to the Mills page rather than re-entering this one with the param
    // still on it and re-selecting the user the administrator has already navigated away from.
    navigate({ search: {}, replace: true })
  }, [userGuid, navigate])

  return <MillAssociations carriedUserGuid={carried} />
}

export const Route = createFileRoute('/mill-associations')({
  validateSearch: (search: Record<string, unknown>): MillAssociationsSearch => {
    const raw = search.userGuid
    // Whitelisted, not merely non-empty: the page spends the value on a directory lookup whose
    // GUID criterion is an exact match, so anything else is a request that can only fail —
    // dropped here instead, which lands the page in its ordinary no-selection state.
    const isGuid = typeof raw === 'string' && USER_GUID_PATTERN.test(raw)
    return { userGuid: isGuid ? raw : undefined }
  },
  component: MillAssociationsRoute,
})
