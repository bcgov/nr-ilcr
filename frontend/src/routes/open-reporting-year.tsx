import { createFileRoute } from '@tanstack/react-router'
import OpenReportingYear from '@/components/openReportingYear'

// Story 24.1 (UC-RY-001) Open Reporting Year. Reachable via the admin-gated Administration menu; the
// API independently enforces the ADMIN-only OPEN_REPORTING_YEAR action (403), which is the boundary.
export const Route = createFileRoute('/open-reporting-year')({
  component: OpenReportingYear,
})
