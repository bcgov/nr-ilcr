// Mirrors the backend Schedule4Response DTO (Story 4.1/4.3 read + 4.4 check-status). Jackson omits
// nulls (non_null), so absent members simply won't be in the JSON. `perUnit`, `kind`, `editable`,
// per-category `distance`, and every derived value are computed server-side — never recomputed here.

export interface MessageInfo {
  readonly key: string
  readonly text: string
}

// One transportation-category amount. `kind` is FIXED (9 no-distance codes) or DISTANCE (47/48/52,
// each carrying its own `distance`). `perUnit` ($/m³) is read-only server-derived.
export interface CategoryAmount {
  readonly code: number
  readonly kind: 'FIXED' | 'DISTANCE'
  readonly volume: number | null
  readonly cost: number | null
  readonly distance: number | null
  readonly perUnit: number | null
}

// One sub-page list row (Towing 43 / Truck Rehaul 46 [cycle] / Other 55) — its own report sharing
// the location name. `id` is the row's report id (delete target). Rendered/added on the sub-pages
// (Story 4-6); the main page shows only the per-type counts.
export interface SubPageRow {
  readonly id: number
  readonly code: number
  readonly description: string | null
  readonly distance: number | null
  readonly volume: number | null
  readonly cost: number | null
  readonly cycle: number | null
  readonly perUnit: number | null
}

// One dump location = a family of TRANSPORTATION_REPORT rows. `id` is the primary report id (the
// rename-safe write handle); `revisionCount` is that report's optimistic-lock token echoed for edits.
export interface Location {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly name: string
  // Per-location free-text comments (TRANSPORTATION_REPORT.COMMENTS, ≤ 2000). Jackson non_null omits
  // it when absent, so render defensively.
  readonly comments?: string | null
  readonly categories: CategoryAmount[]
  readonly subPageRows: SubPageRow[]
}

export default interface Schedule4Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly locations: Location[]
  readonly message?: MessageInfo | null
}

// Check Status (POST check-status, no body): per-location breakdown, read-only, mutates nothing.
export interface FieldIssue {
  readonly code: number
  readonly message: MessageInfo
}

export interface LocationCheckResult {
  readonly id: number | null
  readonly name: string
  readonly met: boolean
  readonly messages: MessageInfo[]
  readonly issues: FieldIssue[]
}

export interface Schedule4CheckStatusResponse {
  readonly outcome: 'MET' | 'ISSUES'
  readonly messages: MessageInfo[]
  readonly locations: LocationCheckResult[]
}
