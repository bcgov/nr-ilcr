import { createFileRoute } from '@tanstack/react-router'
import CheckStatus from '@/components/checkStatus'

export const Route = createFileRoute('/check-status')({
  component: CheckStatus,
})
