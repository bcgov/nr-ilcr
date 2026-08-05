import { createFileRoute } from '@tanstack/react-router'
import Schedule8 from '@/components/schedule8'

// The samples/rates levels are URL-driven: `pageId` alone = a page's samples, `pageId` + `sampleId` =
// a sample's additions/deductions. Reflecting them in the URL lets the browser Back button step back
// through the levels (and makes them refreshable/shareable). Garbage coerces to undefined = the list.
export type Schedule8Search = {
  pageId?: number
  sampleId?: number
}

const toNum = (value: unknown): number | undefined => {
  if (value == null || value === '') return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

export const Route = createFileRoute('/schedule-8')({
  validateSearch: (search: Record<string, unknown>): Schedule8Search => ({
    pageId: toNum(search.pageId),
    sampleId: toNum(search.sampleId),
  }),
  component: Schedule8,
})
