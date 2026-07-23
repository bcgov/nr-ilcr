// Mirrors the backend Story 4.4 Included Unacceptable Costs sub-resource DTOs (UnacceptableDocument /
// UnacceptableRow / UnacceptableRequest). Numbers are nullable; Jackson omits nulls. `annualRentsTotal`
// is the read-only Annual Rents (Forest Act, S111) figure pulled from the item-29 Harvest.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

export interface UnacceptableRow {
  id: number
  description: string
  total: number | null
}

export interface UnacceptableDocument {
  editable: boolean
  count: number
  subtotalTotal: number | null
  annualRentsTotal: number | null
  rows: UnacceptableRow[]
  // Verbatim success message on a mutation echo (AD-8); null/absent on GET.
  message?: MessageInfo | null
}

export interface UnacceptableRequest {
  description: string
  total: number | null
}
