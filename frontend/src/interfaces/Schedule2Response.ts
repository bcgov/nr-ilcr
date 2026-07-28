// Mirrors the backend Schedule2Response DTO (Schedule 2 read slice). Blocks are nested `CostBlock`s;
// Jackson omits nulls (non_null), so an absent block member simply won't be in the JSON. perUnit and
// the derived blocks are computed server-side (read-only) — never recompute them client-side.

export interface CostBlock {
  volume: number | null
  cost: number | null
  perUnit: number | null
}

// Success message carried on a mutating response (AD-8): the frontend renders `text` verbatim and
// never hardcodes SUC-* strings. Null/absent on the GET document.
export interface MessageInfo {
  key: string
  text: string
}

export default interface Schedule2Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  revisionCount: number | null
  comments: string | null
  purchasedLogCost: CostBlock
  purchasedWoodOverhead: CostBlock
  subtotal: CostBlock
  lessLogSales: CostBlock
  netPurchased: CostBlock
  totalCompanyLogging: CostBlock
  totalAverage: CostBlock
  message?: MessageInfo | null
}

// Read-only CheckStatus evaluation (POST check-status, no body). `MET` when item-25 cost present,
// else `ISSUES`. `messages[].text` is rendered verbatim; nothing is mutated.
export interface CheckStatusResponse {
  outcome: 'MET' | 'ISSUES'
  messages: MessageInfo[]
}
