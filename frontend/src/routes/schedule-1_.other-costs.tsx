import { createFileRoute } from '@tanstack/react-router'
import OtherCostsPage from '@/components/schedule1OtherCosts'

// Flat route (`schedule-1_` opts out of nesting under the Schedule 1 layout) at
// /schedule-1/other-costs — the Subtotal Other Costs sub-page (Story 2.5).
export const Route = createFileRoute('/schedule-1_/other-costs')({
  component: OtherCostsPage,
})
