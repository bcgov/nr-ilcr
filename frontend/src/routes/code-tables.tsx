import { createFileRoute } from '@tanstack/react-router'
import PlaceholderPage from '@/components/PlaceholderPage'

// Story 24.3 (UC-CODE-001) Table Maintenance. Route scaffolded here so the admin-gated menu item
// works; the selector + grid + add/inline-edit UI is filled in by the Story 24.3 frontend slices.
export const Route = createFileRoute('/code-tables')({
  component: CodeTables,
})

function CodeTables() {
  return (
    <PlaceholderPage
      title="Table Maintenance"
      description="Add and edit entries in the lookup/reference code tables that feed the schedule dropdowns."
    />
  )
}
