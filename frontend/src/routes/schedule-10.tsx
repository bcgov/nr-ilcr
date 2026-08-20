import { createFileRoute } from '@tanstack/react-router'
import Schedule10 from '@/components/schedule10'

// The road-detail level is driven by a URL search param (pageId = ROAD_CONSTRUCTION_REPRT_ID) so the
// browser Back button steps back to the page list, and a road level is refreshable and shareable.
// `validateSearch` coerces and whitelists, so an unknown or garbage value falls back to the list.
export type Schedule10Search = {
  pageId?: number
}

export const Route = createFileRoute('/schedule-10')({
  validateSearch: (search: Record<string, unknown>): Schedule10Search => {
    const raw = search.pageId
    const pageId = raw == null || raw === '' ? undefined : Number(raw)
    return {
      // A page id is a positive integer; Number.isFinite alone admits 3.5 or -1, which would mount
      // the road level and 404 at the server instead of falling back to the list as promised.
      pageId: pageId != null && Number.isInteger(pageId) && pageId > 0 ? pageId : undefined,
    }
  },
  component: Schedule10,
})
