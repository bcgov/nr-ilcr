// Mirrors the backend Schedule 7A (Bridge Costs) DTOs. Jackson omits nulls (non_null), so absent
// members simply won't be in the JSON — which is why every optional cost and all four totals are
// `number | null`. The totals are computed server-side from the ten costs (BR-06) and are NEVER
// recomputed here (AD-5); a total with no contributing costs is omitted and must render blank, not 0.

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

// One code-table option for the five bridge dropdowns.
export interface BridgeCodeOption {
  readonly code: string
  readonly description: string
}

// The five code option lists, carried on the document so the page renders its dropdowns without a
// second request.
export interface BridgeCodeLists {
  readonly constructionTypes: readonly BridgeCodeOption[]
  readonly superstructureTypes: readonly BridgeCodeOption[]
  readonly deckTypes: readonly BridgeCodeOption[]
  readonly abutmentTypes: readonly BridgeCodeOption[]
  readonly loadRatings: readonly BridgeCodeOption[]
}

// One stored bridge. `rowCounter` is the 1-based list ordinal the server uses to compose
// check-status message lines — display it, never derive message text from it. `revisionCount` is the
// per-row optimistic-lock token echoed back on update (the document carries no token of its own).
export interface Bridge {
  readonly bridgeReportId: number
  readonly rowCounter: number
  // Every attribute below is nullable in storage even though the write DTO requires it: legacy rows
  // predate the validation, and Check Status exists precisely to flag the gaps (the backend's
  // REQUIRED_CHECKS tests each of these for null). Jackson omits nulls, so these arrive ABSENT — the
  // page must seed blanks rather than assume a string.
  readonly locationName: string | null
  readonly builtDate: string | null
  readonly constructionTypeCode: string | null
  readonly superstructureTypeCode: string | null
  readonly deckTypeCode: string | null
  readonly abutmentTypeCode: string | null
  readonly loadRatingCode: string | null
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
  readonly comments: string | null
  // Server-derived (BR-06): material/deliver/install are superstructure + abutment; the grand total
  // adds site plan, approach, certification and other on top of those three.
  readonly totalMaterial: number | null
  readonly totalDeliver: number | null
  readonly totalInstall: number | null
  readonly grandTotal: number | null
  readonly revisionCount: number
}

// Check Status (BR-08) result — read-only validation, no status transition. Unlike the Schedule 11
// equivalent there is NO top-level `message`: `errors` carries one verbatim line per missing value,
// `bridgeMessages` one per fully-populated bridge, and `requirementsMetMessage` is present only when
// every bridge passes (mixed results ⇒ no schedule-wide banner).
export interface Schedule7aCheckStatusResponse {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly bridgeMessages: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

export default interface Schedule7aResponse {
  readonly millId: number
  readonly year: number
  // The Schedules 1-10 track code; Schedule 7A has no track of its own (BR-01).
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly bridges: readonly Bridge[]
  readonly codeLists: BridgeCodeLists
  // Write echoes only (AD-8); absent on the GET.
  readonly message?: MessageInfo | null
}
