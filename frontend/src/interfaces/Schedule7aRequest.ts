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
  // Optional at save (legacy). A null stores NULL in the cost's detail row — the row itself is
  // always written, so the legacy app (same delivery database) can still edit that cost. Only Check
  // Status flags a missing cost.
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

/**
 * Mirrors the backend BridgeSaveAllRequest — the page-level Save, which persists EVERY bridge of the
 * schedule in one transaction (legacy `Schedule7aMB.save()`). Each entry carries its own
 * `revisionCount`, so a stale row aborts the whole batch rather than saving around it.
 */
export interface BridgeSaveAllRequest {
  readonly bridges: readonly {
    readonly bridgeReportId: number
    readonly bridge: BridgeRequest
  }[]
}

/**
 * The Check Status body — every bridge as it is ON SCREEN, mirroring the backend
 * `Schedule7aCheckRequest` (#359). Legacy's check read the bean's in-memory document, which every row
 * input wrote into on change, so the verdict described the screen (rows on other paginator pages
 * included) rather than the saved record. Rows are numbered server-side by their ORDINAL here, so they
 * must be sent in document order. The Add panel's draft is never part of it (Add saves at once).
 *
 * ⚠ Every member is nullable and `null` MUST stay null: the server's check is a pure null test (a
 * typed `0` passes), so coercing a blank field to `0` turns a missing value into a pass. The five
 * codes and the comments are not checked and are not sent.
 */
export interface BridgeCheckEntry {
  readonly locationName: string | null
  /** `yyyy-MM`, as typed. */
  readonly builtDate: string | null
  readonly lifeSpan: number | null
  readonly abutmentHeight: number | null
  readonly length: number | null
  readonly width: number | null
  readonly distance: number | null
  readonly sitePlanCost: number | null
  readonly superstructureMaterialCost: number | null
  readonly superstructureDeliverCost: number | null
  readonly superstructureInstallCost: number | null
  readonly abutmentMaterialCost: number | null
  readonly abutmentDeliverCost: number | null
  readonly abutmentInstallCost: number | null
  readonly approachCost: number | null
  readonly afterInstallCost: number | null
  readonly otherCost: number | null
}

export interface Schedule7aCheckRequest {
  readonly bridges: readonly BridgeCheckEntry[]
}
