// Mirrors the backend Story 2.4 Other-Costs sub-resource DTOs (OtherCostsDocument / OtherCostRow /
// OtherCostRequest). Numbers are nullable; Jackson omits nulls, so an absent cost/perUnit simply is
// not in the JSON. perUnit and totals are server-computed (read-only) — never recompute client-side.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

export interface OtherCostRow {
  id: number
  description: string
  cost: number | null
  perUnit: number | null
}

export interface OtherCostsDocument {
  volume: number | null
  costSubtotal: number | null
  perUnit: number | null
  count: number
  rows: OtherCostRow[]
  editable: boolean
  // Verbatim success message on a mutation echo (AD-8); null/absent on GET.
  message?: MessageInfo | null
}

export interface OtherCostRequest {
  description: string
  cost: number | null
}
