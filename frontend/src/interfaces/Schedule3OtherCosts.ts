// Mirrors the backend Story 4.4 Other Acceptable Costs sub-resource DTOs (OtherAcceptableDocument /
// OtherAcceptableRow / OtherAcceptableRequest). Numbers are nullable; Jackson omits nulls. Crown and
// the subtotal are server-computed (read-only) — never recompute client-side.

import type { MessageInfo, ThreeColumnTotal } from '@/interfaces/Schedule3Response'

export interface OtherAcceptableRow {
  id: number
  description: string
  total: number | null
  pop: number | null
  crown: number | null
}

export interface OtherAcceptableDocument {
  editable: boolean
  count: number
  subtotal: ThreeColumnTotal
  rows: OtherAcceptableRow[]
  // Verbatim success message on a mutation echo (AD-8); null/absent on GET.
  message?: MessageInfo | null
}

export interface OtherAcceptableRequest {
  description: string
  total: number | null
  pop: number | null
}
