// Mirrors the backend Story 2.4 Other-Costs sub-resource DTOs (OtherCostsDocument / OtherCostRow /
// OtherCostRequest). Numbers are nullable; Jackson omits nulls, so an absent cost/perUnit simply is
// not in the JSON. perUnit and totals are server-computed (read-only) — never recompute client-side.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

export interface OtherCostRow {
  readonly id: number
  readonly description: string
  readonly cost: number | null
  readonly perUnit: number | null
}

export interface OtherCostsDocument {
  readonly volume: number | null
  readonly costSubtotal: number | null
  readonly perUnit: number | null
  readonly count: number
  readonly rows: readonly OtherCostRow[]
  readonly editable: boolean
  // Verbatim success message on a mutation echo (AD-8); null/absent on GET.
  readonly message?: MessageInfo | null
}

export interface OtherCostRequest {
  readonly description: string
  readonly cost: number | null
}
