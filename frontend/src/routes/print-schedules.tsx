import { createFileRoute } from '@tanstack/react-router'
import PrintSchedules from '@/components/printSchedules'

export const Route = createFileRoute('/print-schedules')({
  component: PrintSchedules,
})
