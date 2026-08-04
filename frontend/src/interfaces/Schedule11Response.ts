// Mirrors the backend Schedule 11 (Basic Silviculture) DTOs (Stories 25.1/25.2). Jackson omits nulls
// (non_null), so absent members simply won't be in the JSON. `becLabel`, `totalCost`,
// `costPerNetArea` and every footer total are computed server-side per BR-08 — never recomputed here
// (AD-5). Cost totals are widened to `number` because the backend sums them as `Long` (a footer sum
// across enough locations exceeds 2.147e9; do not assume 32-bit).

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

// One Schedule 11 location row. `revisionCount` is the per-row optimistic-lock token echoed for the
// 25.2 PUT (never document-level; the document's revisionCount is always null).
export interface SilvicultureLocation {
  readonly locationId: number
  readonly location: string
  readonly enhancedIndicator: boolean
  readonly biogeoclimaticCatalogueId: number
  // Zone+subzone+variant+phase concat (nulls → ""); null only when the catalogue row is missing.
  readonly becLabel: string | null
  readonly netArea: number | null
  readonly actualCost: number | null
  readonly plannedCost: number | null
  readonly totalCost: number | null
  readonly costPerNetArea: number | null
  readonly comments: string | null
  readonly revisionCount: number
}

// Footer totals (BR-08). Any field with no contributors is null (omitted), NEVER zero — null-not-zero
// is meaningful and renders blank.
export interface SilvicultureTotals {
  readonly netArea: number | null
  readonly actualCost: number | null
  readonly plannedCost: number | null
  readonly totalCost: number | null
  readonly costPerNetArea: number | null
}

// One BEC catalogue suggestion for the forced-selection type-ahead (BR-09). `label` is the same
// concat as `SilvicultureLocation.becLabel`.
export interface BiogeoclimaticOption {
  readonly id: number
  readonly label: string
}

// Check Status (BR-07) result — read-only validation, no status transition. `message` (SUC-004) is
// present on every invocation; `requirementsMetMessage` (SUC-003) only when met; `errors` carries one
// verbatim FLD-004 entry per missing cost otherwise.
export interface Schedule11CheckStatusResponse {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
  readonly message: MessageInfo
}

export default interface Schedule11Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  readonly editable: boolean
  // ALWAYS null on the document — concurrency is per-row (SilvicultureLocation.revisionCount).
  readonly revisionCount: number | null
  readonly locations: readonly SilvicultureLocation[]
  readonly totals: SilvicultureTotals
  // Write echoes only (AD-8); absent on the GET.
  readonly message?: MessageInfo | null
}
