import { createFileRoute } from '@tanstack/react-router'
import HomeContent from '@/components/homeContent'

// Story 24.2 (UC-CNT-001) Content Editing. Reachable via the admin-gated Administration menu; the API
// independently enforces the ADMIN-only EDIT_HOME_CONTENT action (403), which is the boundary.
export const Route = createFileRoute('/home-content')({
  component: HomeContent,
})
