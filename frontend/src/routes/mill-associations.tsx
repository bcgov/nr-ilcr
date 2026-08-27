import { createFileRoute } from '@tanstack/react-router'
import MillAssociations from '@/components/millAssociations'

export const Route = createFileRoute('/mill-associations')({
  component: MillAssociations,
})
