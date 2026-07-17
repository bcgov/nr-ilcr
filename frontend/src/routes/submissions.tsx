import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

export const Route = createFileRoute('/submissions')({
  component: Submissions,
})

function Submissions() {
  return (
    <PlaceholderPage
      title="Submissions"
      description="Submission intake and review workflows for ILCR modernization."
    />
  )
}
