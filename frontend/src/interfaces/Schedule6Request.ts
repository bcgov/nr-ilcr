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
