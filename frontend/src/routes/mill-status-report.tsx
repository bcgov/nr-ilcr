import { createFileRoute } from '@tanstack/react-router'
import MillReportStatus from '@/components/millReportStatus'

export const Route = createFileRoute('/mill-status-report')({
  component: MillReportStatus,
})
