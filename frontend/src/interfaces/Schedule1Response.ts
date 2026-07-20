// Mirrors the backend Schedule1Response DTO (Story 1.2). Numbers are nullable; Jackson omits nulls,
// so an absent line item / block member simply won't be in the JSON. perUnit and subtotals are
// computed server-side (read-only) — never recompute them client-side.

export interface LineItem {
  costItemCode: number
  volume: number | null
  cost: number | null
  perUnit: number | null
}

export interface SilvicultureBlock {
  actualSpent: LineItem | null
  accruedLessActual: LineItem | null
  lessAdmin: LineItem | null
  total: LineItem | null
}

export interface OtherCostsSummary {
  volume: number | null
  costSubtotal: number | null
  perUnit: number | null
  count: number
}

// Success message carried on a mutating response (AD-8): the frontend renders `text` verbatim and
// never hardcodes SUC-* strings. Null/absent on the GET document.
export interface MessageInfo {
  key: string
  text: string
}

export default interface Schedule1Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  crownVolume: number | null
  revisionCount: number | null
  comments: string | null
  lineItems: LineItem[]
  silviculture: SilvicultureBlock
  forestMgmtAdminCost: number | null
  lessSilvAdminCost: number | null
  otherCosts: OtherCostsSummary
  message?: MessageInfo | null
}
