import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

export const Route = createFileRoute('/generate-reports')({
  component: GenerateReports,
})

function GenerateReports() {
  return (
    <PlaceholderPage
      title="Generate Reports"
      description="Data extracts and the Mill Information / Mill Status reports."
    />
  )
}
