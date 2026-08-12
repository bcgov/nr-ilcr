import { createFileRoute } from '@tanstack/react-router'
import Schedule5 from '@/components/schedule5'

// The expense sub-page level is driven by URL search params (camp = CAMP_REPORT_ID, sub = which
// list) so the browser Back button steps back from a sub-page to the camp list, and a sub-page is
// refreshable and shareable. Schedule 4's shape (routes/schedule-4.tsx) — NOT a second route file,
// which would need its own nav entry and would refetch the camp context the parent already holds.
// `validateSearch` coerces and whitelists, so unknown or garbage values fall back to the camp list.
export type Schedule5Search = {
  camp?: number
  sub?: 'CAMP' | 'ACCESS'
}

export const Route = createFileRoute('/schedule-5')({
  validateSearch: (search: Record<string, unknown>): Schedule5Search => {
    const rawCamp = search.camp
    const camp = rawCamp == null || rawCamp === '' ? undefined : Number(rawCamp)
    const sub = search.sub
    const validSub = sub === 'CAMP' || sub === 'ACCESS' ? sub : undefined
    return {
      // A camp id is a positive integer; Number.isFinite alone admits 3.5 or -1, which would mount
      // the sub-page and 400 at the server instead of falling back to the camp list as promised.
      camp: camp != null && Number.isInteger(camp) && camp > 0 ? camp : undefined,
      sub: validSub,
    }
  },
  component: Schedule5,
})
