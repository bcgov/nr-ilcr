import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

export const Route = createFileRoute('/print-schedules')({
  component: PrintSchedules,
})

function PrintSchedules() {
  return (
    <PlaceholderPage
      title="Print Schedules"
      description="Printable views of the submitted schedules for the selected mill and year."
    />
  )
}
