import { createFileRoute } from '@tanstack/react-router'
import CodeTables from '@/components/codeTables'

// Story 24.3 (UC-CODE-001) Table Maintenance. Reachable via the admin-gated Administration menu; the
// API independently enforces the ADMIN-only MAINTAIN_CODE_TABLES action (403), which is the boundary.
export const Route = createFileRoute('/code-tables')({
  component: CodeTables,
})
