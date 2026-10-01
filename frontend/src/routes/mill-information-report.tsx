import { createFileRoute } from '@tanstack/react-router'
import MillInformationReport from '@/components/millInformationReport'

export const Route = createFileRoute('/mill-information-report')({
  component: MillInformationReport,
})
