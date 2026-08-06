// Mirrors the backend BridgeRequest write DTO. Entered fields only — the four derived totals and
// `rowCounter` are server-owned and are never client-supplied. The server is authoritative for
// validation, the Draft gate, and the per-row optimistic lock; `revisionCount` is the token echoed
// from the served row, required on update only.

export default interface BridgeRequest {
  readonly locationName: string
  // yyyy-MM, non-lenient.
  readonly builtDate: string
  readonly constructionTypeCode: string
  readonly superstructureTypeCode: string
  readonly deckTypeCode: string
  readonly abutmentTypeCode: string
  readonly loadRatingCode: string
  readonly lifeSpan: number
  readonly abutmentHeight: number
  readonly length: number
  readonly width: number
  readonly distance: number
  // Optional at save (legacy); null clears that cost row. Only Check Status flags a missing cost.
  readonly sitePlanCost?: number | null
  readonly superstructureMaterialCost?: number | null
  readonly superstructureDeliverCost?: number | null
  readonly superstructureInstallCost?: number | null
  readonly abutmentMaterialCost?: number | null
  readonly abutmentDeliverCost?: number | null
  readonly abutmentInstallCost?: number | null
  readonly approachCost?: number | null
  readonly afterInstallCost?: number | null
  readonly otherCost?: number | null
  readonly comments?: string | null
  // Required on UPDATE only (read from the loaded row, never hardcoded or coerced).
  readonly revisionCount?: number
}
