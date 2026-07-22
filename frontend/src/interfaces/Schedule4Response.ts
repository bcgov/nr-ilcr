// Mirrors the backend Schedule4Response DTO (Story 4.1/4.3 read + 4.4 check-status). Jackson omits
// nulls (non_null), so absent members simply won't be in the JSON. `perUnit`, `kind`, `editable`,
// per-category `distance`, and every derived value are computed server-side — never recomputed here.

export interface MessageInfo {
  key: string
  text: string
}

// One transportation-category amount. `kind` is FIXED (9 no-distance codes) or DISTANCE (47/48/52,
// each carrying its own `distance`). `perUnit` ($/m³) is read-only server-derived.
export interface CategoryAmount {
  code: number
  kind: 'FIXED' | 'DISTANCE'
  volume: number | null
  cost: number | null
  distance: number | null
  perUnit: number | null
}

// One sub-page list row (Towing 43 / Truck Rehaul 46 [cycle] / Other 55) — its own report sharing
// the location name. `id` is the row's report id (delete target). Rendered/added on the sub-pages
// (Story 4-6); the main page shows only the per-type counts.
export interface SubPageRow {
  id: number
  code: number
  description: string | null
  distance: number | null
  volume: number | null
  cost: number | null
  cycle: number | null
  perUnit: number | null
}

// One dump location = a family of TRANSPORTATION_REPORT rows. `id` is the primary report id (the
// rename-safe write handle); `revisionCount` is that report's optimistic-lock token echoed for edits.
export interface Location {
  id: number | null
  revisionCount: number | null
  name: string
  categories: CategoryAmount[]
  subPageRows: SubPageRow[]
}

export default interface Schedule4Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  locations: Location[]
  message?: MessageInfo | null
}

// Check Status (POST check-status, no body): per-location breakdown, read-only, mutates nothing.
export interface FieldIssue {
  code: number
  message: MessageInfo
}

export interface LocationCheckResult {
  id: number | null
  name: string
  met: boolean
  messages: MessageInfo[]
  issues: FieldIssue[]
}

export interface Schedule4CheckStatusResponse {
  outcome: 'MET' | 'ISSUES'
  messages: MessageInfo[]
  locations: LocationCheckResult[]
}
