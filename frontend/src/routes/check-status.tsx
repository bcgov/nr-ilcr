import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

export const Route = createFileRoute('/check-status')({
  component: CheckStatus,
})

function CheckStatus() {
  return (
    <PlaceholderPage
      title="Check Status"
      description="Run reporting-requirement checks across the schedules for the selected mill and year."
    />
  )
}
