import { createFileRoute } from '@tanstack/react-router'
import OtherAcceptableCostsPage from '@/components/schedule3OtherAcceptableCosts'

// Flat route (`schedule-3_` opts out of nesting) at /schedule-3/other-acceptable-costs — the Other
// Acceptable Costs sub-page (Story 4.4).
export const Route = createFileRoute('/schedule-3_/other-acceptable-costs')({
  component: OtherAcceptableCostsPage,
})
