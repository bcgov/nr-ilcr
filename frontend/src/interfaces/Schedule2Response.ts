// Mirrors the backend Schedule2Response DTO (Schedule 2 read slice). Blocks are nested `CostBlock`s;
// Jackson omits nulls (non_null), so an absent block member simply won't be in the JSON. perUnit and
// the derived blocks are computed server-side (read-only) — never recompute them client-side.

export interface CostBlock {
  readonly volume: number | null
  readonly cost: number | null
  readonly perUnit: number | null
}

// Success message carried on a mutating response (AD-8): the frontend renders `text` verbatim and
// never hardcodes SUC-* strings. Null/absent on the GET document.
export interface MessageInfo {
  readonly key: string
  readonly text: string
}

export default interface Schedule2Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly revisionCount: number | null
  readonly comments: string | null
  readonly purchasedLogCost: CostBlock
  readonly purchasedWoodOverhead: CostBlock
  readonly subtotal: CostBlock
  readonly lessLogSales: CostBlock
  readonly netPurchased: CostBlock
  readonly totalCompanyLogging: CostBlock
  readonly totalAverage: CostBlock
  // The API returns null on GET and a MessageInfo on mutations — one nullable field, not also
  // optional, so callers only ever check `message?.text`.
  readonly message: MessageInfo | null
}

// Read-only CheckStatus evaluation (POST check-status, no body). `MET` when item-25 cost present,
// else `ISSUES`. `messages[].text` is rendered verbatim; nothing is mutated.
export interface CheckStatusResponse {
  readonly outcome: 'MET' | 'ISSUES'
  readonly messages: readonly MessageInfo[]
}
