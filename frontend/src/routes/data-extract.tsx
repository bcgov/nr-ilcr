import { createFileRoute } from '@tanstack/react-router'
import DataExtract from '@/components/dataExtract'

export const Route = createFileRoute('/data-extract')({
  component: DataExtract,
})
