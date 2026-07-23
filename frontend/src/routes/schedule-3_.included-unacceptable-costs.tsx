import { createFileRoute } from '@tanstack/react-router'
import UnacceptableCostsPage from '@/components/schedule3UnacceptableCosts'

// Flat route (`schedule-3_` opts out of nesting) at /schedule-3/included-unacceptable-costs — the
// Included Unacceptable Costs sub-page (Story 4.4).
export const Route = createFileRoute('/schedule-3_/included-unacceptable-costs')({
  component: UnacceptableCostsPage,
})
