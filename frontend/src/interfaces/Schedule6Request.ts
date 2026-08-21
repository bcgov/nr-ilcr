// Mirrors the backend Schedule 6 write DTOs (Story 8.2). Entered fields only — the derived `rmg`,
// `costPerVolume` and the document totals are never client-supplied (AD-5/AD-12). The server is
// authoritative for validation, the BR-02 counterpart clear, the Draft gate and the per-record
// optimistic lock.

export interface RoadRecordRequest {
  // A TSA code (≤2) or the literal "TFL" — the only field required at save (FLD-001).
  readonly areaType: string
  // Sent only on the TFL branch; the client clears the counterpart so a disabled-but-populated field
  // can never serialize (BR-02), which the server would otherwise silently absorb.
  readonly tflNumber?: string | null
  readonly supplyBlock?: string | null
  readonly volume?: number | null
  // Whole dollars: the wire type is Integer, so a fractional entry is rounded before send.
  readonly cost?: number | null
  // ≤400 — the detail column's width, NOT the general comment's 3500 (Story 8.2 code review).
  readonly comments?: string | null
  // Required on UPDATE only, read from the loaded row (never hardcoded, never coerced to 0).
  readonly revisionCount?: number
}

export interface GeneralCommentsRequest {
  // ≤3500; null/blank clears the comment (BR-09).
  readonly generalComments: string | null
}

// One row of the whole-document PUT (Task 5/Task 7 correction 4, retiring deviation (C)). Every field
// RoadRecordRequest carries plus the two identifiers the batch endpoint needs to place and lock each
// row: `recordId` so the server knows WHICH row, `revisionCount` so it can detect a concurrent edit.
// Required (not optional) on both — an omitted row 400s, and a missing token must surface as a client
// error rather than being coerced to a value that would silently bypass the stale-edit check.
export interface RoadRecordEntry extends Omit<RoadRecordRequest, 'revisionCount'> {
  readonly recordId: number
  readonly revisionCount: number
}

// PUT /api/v1/schedule6 body (Task 5): the whole document in one transaction. EVERY served row must
// be present — the server refuses to guess what the user meant to leave alone.
export interface Schedule6SaveRequest {
  readonly generalComments: string | null
  readonly records: readonly RoadRecordEntry[]
}

// POST /api/v1/schedule6/check-status body (Task 6): read-only, on-screen values only. No `recordId`,
// no `revisionCount` — rows are identified by their PAYLOAD ORDINAL, so this reuses the exact
// RoadRecordRequest shape (with its optional revisionCount simply never populated) that POST /records
// already sends, rather than declaring a near-duplicate type.
export interface Schedule6CheckRequest {
  readonly generalComments: string | null
  readonly records: readonly RoadRecordRequest[]
}
