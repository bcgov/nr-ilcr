// Mirrors the backend CulvertRequest write DTO. Entered fields only — `totalCost` is derived and is
// deliberately ABSENT so a client cannot supply it (BR-05), as is `rowCounter`. The server is
// authoritative for validation, the Draft gate, and the per-row optimistic lock; `revisionCount` is
// the token echoed from the served row, required on update only.
//
// Only TWO fields are required at Save (legacy `required="true"` sits on Type and No of Pieces
// alone): span, rise, length, both costs and comments are optional, and their absence is flagged
// only by Check Status (BR-07). Do not tighten these to match Schedule 7A's twelve required
// fields — a reporter must be able to save a partially-measured culvert and come back to it.

export default interface CulvertRequest {
  readonly culvertTypeCode: string
  // Millimetres, whole numbers (0-9,999,999).
  readonly spanSize?: number | null
  readonly riseSize?: number | null
  // Metres (0.0-999,999.9); the server rounds extra decimals to one place rather than rejecting.
  readonly length?: number | null
  readonly culvertPieceCount: number
  // Whole dollars (±99,999,999). A null stores NULL in the cost's detail row — the row itself is
  // always written, so the legacy app (same delivery database) can still edit that cost. Only Check
  // Status flags a missing cost.
  readonly materialCost?: number | null
  readonly installCost?: number | null
  readonly comments?: string | null
  // Required on UPDATE only (read from the loaded row, never hardcoded or coerced).
  readonly revisionCount?: number
}

/**
 * Mirrors the backend CulvertSaveAllRequest — the page-level Save, which persists EVERY culvert of
 * the schedule in one transaction (legacy `Schedule7bMB.save()`). Each entry carries its own
 * `revisionCount`, so a stale row aborts the whole batch rather than saving around it.
 */
export interface CulvertSaveAllRequest {
  readonly culverts: readonly {
    readonly culvertReportId: number
    readonly culvert: CulvertRequest
  }[]
}
