// Mirrors the backend Schedule2Request DTO — the pinned write contract. FLAT fields; only the ENTERED
// values are sent. Derived/carried/read-only figures are server-owned and must NOT be sent.
// `revisionCount` is the optimistic-lock token from the last loaded/returned document (a new/unsaved
// schedule sends 0).

export default interface Schedule2Request {
  readonly revisionCount: number
  readonly comments: string | null
  readonly purchasedLogCostCost: number | null // item 25 cost
  readonly lessLogSalesVolume: number | null // item 26 volume
  readonly lessLogSalesCost: number | null // item 26 cost
}

/**
 * The Check Status body — the one value Schedule 2's check reads, as it is currently ON SCREEN,
 * mirroring the backend `Schedule2CheckRequest` (issue #359). Legacy's Check Status was a full form
 * postback, so the verdict described the screen and not the saved record.
 *
 * ⚠ `null` means "no usable value on screen" and MUST stay null — the server's check is a pure null
 * test (a stored `0` passes), so coercing a blank field to `0` turns a missing value into a pass.
 */
export interface Schedule2CheckRequest {
  readonly purchasedLogCostCost: number | null // item 25 cost
}
