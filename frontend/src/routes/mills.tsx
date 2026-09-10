import { createFileRoute } from '@tanstack/react-router'
import Mills from '@/components/mills'

export const Route = createFileRoute('/mills')({
  component: Mills,
})
