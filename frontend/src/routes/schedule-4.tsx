import { createFileRoute } from '@tanstack/react-router'
import Schedule4 from '@/components/schedule4'

// The sub-page level is driven by URL search params (loc = location id, sub = sub-page type) so the
// browser Back button steps back from a sub-page to the location list, and a sub-page is refreshable /
// shareable. `validateSearch` coerces + whitelists the values (unknown/garbage → undefined = list).
export type Schedule4Search = {
  loc?: number
  sub?: 'TOWING' | 'TRUCK_REHAUL' | 'OTHER'
}

export const Route = createFileRoute('/schedule-4')({
  validateSearch: (search: Record<string, unknown>): Schedule4Search => {
    const rawLoc = search.loc
    const loc = rawLoc == null || rawLoc === '' ? undefined : Number(rawLoc)
    const sub = search.sub
    const validSub = sub === 'TOWING' || sub === 'TRUCK_REHAUL' || sub === 'OTHER' ? sub : undefined
    return {
      loc: loc != null && Number.isFinite(loc) ? loc : undefined,
      sub: validSub,
    }
  },
  component: Schedule4,
})
