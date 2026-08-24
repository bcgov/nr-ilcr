// Mirrors the backend Schedule2Response DTO (Schedule 2 read slice). Blocks are nested `CostBlock`s;
// Jackson omits nulls (non_null), so an absent block member simply won't be in the JSON. perUnit and
// the derived blocks are computed server-side and are read-only here — never sent on a write, and the
// server is the sole authority for every stored figure.
//
// They ARE mirrored for display while the schedule is being edited, so the read-only cells track entry
// before Save the way legacy did (defect #291; spine AD-5 amended 2026-08-20). That mirror lives in
// `components/schedule2/derived.ts` and nowhere else, and the Save echo supersedes it.

// Every member is optional AND nullable, because that is what the wire does: under the app-wide
// Jackson `default-property-inclusion: non_null` an all-null block serialises as `{}`, so an unsaved
// Schedule 2 sends `"subtotal":{}` rather than three nulls (captured 2026-08-24, defect #292 review).
// The readers already cope — `fmtNumber`/`fmtCurrency`/`numStr` all treat null and undefined alike —
// so this only stops the NEXT presence check from being written against a shape the server never
// sends. Do not "simplify" these back to required.
export interface CostBlock {
  readonly volume?: number | null
  readonly cost?: number | null
  readonly perUnit?: number | null
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
  // Optional AND nullable on purpose (defect #292): the server leaves this null until the schedule
  // is saved, and the app-wide Jackson `default-property-inclusion: non_null` then OMITS the key —
  // so an unsaved (or just-deleted) Schedule 2 serves NO `revisionCount` and readers see `undefined`.
  // Test it with a loose `!= null`, never `!== null`, and build fixtures by omitting the key.
  readonly revisionCount?: number | null
  // Optional, same reason as the blocks: an unsaved (or comment-less) schedule sends no `comments`
  // key at all. Readers use `?? ''` / `?? '—'`, which is undefined-safe.
  readonly comments?: string | null
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
