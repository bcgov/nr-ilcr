// Mirrors the backend Schedule2Request DTO — the pinned write contract. FLAT fields; only the ENTERED
// values are sent. Derived/carried/read-only figures are server-owned and must NOT be sent.
// `revisionCount` is the optimistic-lock token from the last loaded/returned document (a new/unsaved
// schedule sends 0).

export default interface Schedule2Request {
  revisionCount: number
  comments: string | null
  purchasedLogCostCost: number | null // item 25 cost
  lessLogSalesVolume: number | null // item 26 volume
  lessLogSalesCost: number | null // item 26 cost
}
