import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

export const Route = createFileRoute('/mill-associations')({
  component: MillAssociations,
})

function MillAssociations() {
  return (
    <PlaceholderPage
      title="Mill Associations"
      description="ILCR user and mill association workflows for later BA assessment and FAM alignment."
    />
  )
}
