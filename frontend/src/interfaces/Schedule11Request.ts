// Mirrors the backend SilvicultureLocationRequest write DTO (Story 25.2). Entered fields only —
// derived figures (`totalCost`, `costPerNetArea`, `becLabel`) and read-only document fields are never
// client-supplied. The server is authoritative for validation, force-selection, the Draft gate, and
// the per-row optimistic lock; `revisionCount` is the token echoed from the served row, required only
// on the PUT.

export default interface SilvicultureLocationRequest {
  readonly location: string
  readonly enhancedIndicator: boolean
  readonly biogeoclimaticCatalogueId: number
  readonly netArea: number
  // Optional at entry (legacy); null clears that cost row.
  readonly actualCost?: number | null
  readonly plannedCost?: number | null
  readonly comments?: string | null
  // Required on UPDATE only (read from the loaded document, never hardcoded/coerced).
  readonly revisionCount?: number
}

// The page-level Save (legacy Schedule11MB.save(), Story 26.2): every location still on the page,
// each with its own revisionCount, plus the ids the user flagged with Delete since the last save. One
// transaction on the server; any refusal rolls the whole save back.
export interface LocationSaveAllRequest {
  readonly locations: readonly {
    readonly basicSilvicultureReportId: number
    readonly location: SilvicultureLocationRequest
  }[]
  readonly deletedIds: readonly number[]
}
