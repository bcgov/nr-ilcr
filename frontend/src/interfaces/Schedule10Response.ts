// Mirrors the backend Schedule 10 (New Road Construction) DTOs. Jackson omits nulls (non_null), so
// every optional value arrives ABSENT rather than as null — each is typed `| null` and the page seeds
// blanks rather than assuming a value.
//
// Two shapes of number coexist deliberately: raw stored costs are whole-dollar integers, while every
// server-derived total carries two decimals. JSON.parse collapses the wire's trailing zeros, so a
// value that arrived as 3.000 or 147000.00 reaches this code as 3 and 147000 — display must go
// through the shared fixed-decimal formatters, never String(value).

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

/** One code-table option: the stored value plus the label shown in the menu. */
export interface CodeDescription {
  readonly code: string
  readonly description: string
}

/**
 * A biogeoclimatic classification. The components are served separately alongside a pre-built
 * `label` (zone + subzone + variant + phase) so the page never re-implements the concatenation.
 */
export interface BecClassification {
  readonly biogeoclimaticCatalogueId: number
  readonly becZoneCode: string | null
  readonly subzone: string | null
  readonly variant: string | null
  readonly phase: string | null
  readonly label: string | null
}

/**
 * The dropdown lists carried on the document, so the page renders its selects without a second
 * request. `rsmrClasses` descriptions already read `{code} - {description}`; the others are plain
 * descriptions.
 *
 * `becClassifications` holds only the OFFERABLE classifications. A stored road detail may carry one
 * that has since been de-listed, so a row's own `becClassification` can be absent from this list.
 *
 * `supplyBlocks` is the full list; the control narrows it to the blocks whose code starts with the
 * chosen TSA, which is where the chosen TSA is known.
 */
export interface Schedule10CodeLists {
  readonly forestRegions: readonly CodeDescription[]
  readonly tsaNumbers: readonly CodeDescription[]
  readonly supplyBlocks: readonly CodeDescription[]
  readonly roadLifetimes: readonly CodeDescription[]
  readonly ballastMethods: readonly CodeDescription[]
  readonly ballastMaterials: readonly CodeDescription[]
  readonly rsmrClasses: readonly CodeDescription[]
  readonly becClassifications: readonly BecClassification[]
}

/** Sub-grade dimensions, costs and the six deduction lines, plus the server's derived totals. */
export interface SubGrade {
  readonly length: number | null
  readonly surfaceWidth: number | null
  readonly actualCost: number | null
  readonly ttTransfer: number | null
  readonly otherTransfer: number | null
  readonly lessBridges: number | null
  readonly lessCulverts: number | null
  readonly lessLandings: number | null
  readonly lessOverland: number | null
  readonly lessOtherEng: number | null
  readonly lessEndHaul: number | null
  // Server-derived, read-only. A detail with no stored cost lines serves these as 0 while every
  // individual cost above is absent, so a blank input beside a 0.00 total is the normal shape.
  readonly totalCosts: number | null
  readonly totalDeductions: number | null
  readonly total: number | null
  // Absent when length is zero or blank — render blank, not 0.00.
  readonly costPerLength: number | null
}

/** Additional-stabilizing attributes and costs. There is no deduction leg on this side. */
export interface Stabilizing {
  readonly ballastMethodCode: string | null
  readonly ballastMaterialCode: string | null
  readonly length: number | null
  readonly surfaceWidth: number | null
  readonly depth: number | null
  readonly distanceToSource: number | null
  readonly actualCost: number | null
  readonly ttTransfer: number | null
  readonly otherTransfer: number | null
  readonly total: number | null
  readonly costPerLength: number | null
}

/**
 * The five material percentages and their total. `totalPct` is integer arithmetic that coerces
 * absent values to zero, so it is never absent — a brand-new road detail serves 0.
 */
export interface MaterialComposition {
  readonly solidRockPct: number | null
  readonly rippableRockPct: number | null
  readonly coarsePct: number | null
  readonly finePct: number | null
  readonly organicPct: number | null
  readonly totalPct: number
}

/**
 * One road detail. `revisionCount` is this row's own optimistic-lock token — separate from its
 * page's, which a road-detail write does not bump.
 */
export interface RoadDetail {
  readonly roadDetailId: number
  // Positional, assigned on read.
  readonly rowNumber: number
  readonly roadDetailLabel: string
  readonly roadName: string | null
  readonly roadLifetimeCode: string | null
  readonly becClassification: BecClassification | null
  readonly relSoilMoistRgmClsCode: string | null
  readonly sideSlopePct: number | null
  readonly subGrade: SubGrade
  readonly stabilizing: Stabilizing
  readonly materialComposition: MaterialComposition
  // Served as the string 'Y' or 'N', not a boolean.
  readonly detailedEngineeringCostInd: string | null
  readonly endHaulDistance: number | null
  readonly endHaulVolume: number | null
  readonly overlandDistance: number | null
  readonly overlandVolume: number | null
  readonly comments: string | null
  readonly revisionCount: number | null
}

/**
 * One construction page and its road details. `pageLabel` is built server-side and carries two
 * preserved legacy quirks — no space after `TFL:`, and the literal text `TSA: null` on a TFL-located
 * page. It is rendered verbatim.
 */
export interface ConstructionPage {
  readonly pageId: number
  // Positional, assigned on read; renumbers after a delete.
  readonly pageNumber: number
  readonly pageLabel: string
  readonly forestRegionCode: string | null
  readonly tsaNumber: string | null
  readonly tsbNumberCode: string | null
  readonly tflNumberCode: string | null
  // Derived from the location on every read and never stored. Absent when the combination maps to no
  // road group, which is a legitimate saved state — render blank, never an error.
  readonly roadGroup: string | null
  readonly divisionName: string | null
  readonly constructionPeriod: string | null
  // Backs the `Enter Road Data ({count})` link; 0 is served, not omitted.
  readonly roadDetailCount: number
  readonly revisionCount: number | null
  readonly roadDetails: readonly RoadDetail[]
}

/** One outstanding Check Status item. `message.text` is the fully composed line, ready to render. */
export interface FieldIssue {
  // Stable machine name, for correlating an issue back to its form control.
  readonly field: string
  readonly message: MessageInfo
}

export interface RoadDetailCheckResult {
  readonly roadDetailId: number
  readonly rowNumber: number
  readonly roadDetailLabel: string
  readonly met: boolean
  readonly issues: readonly FieldIssue[]
}

export interface PageCheckResult {
  readonly pageId: number
  readonly pageNumber: number
  readonly pageLabel: string
  readonly met: boolean
  readonly issues: readonly FieldIssue[]
  readonly roadDetails: readonly RoadDetailCheckResult[]
}

export const CHECK_STATUS_MET = 'MET'
export const CHECK_STATUS_ISSUES = 'ISSUES'

/**
 * Check Status result. The two outcomes are mutually exclusive: `MET` carries the all-met message
 * and an empty `pages`, while `ISSUES` carries an empty `messages` and lists EVERY page and road
 * detail — including those that passed. Filter on `met` to show only the failures.
 */
export interface Schedule10CheckStatusResponse {
  readonly outcome: string
  readonly messages: readonly MessageInfo[]
  readonly pages: readonly PageCheckResult[]
}

export default interface Schedule10Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  // The single authority for whether write controls are enabled; never re-derived from status or role.
  readonly editable: boolean
  readonly pages: readonly ConstructionPage[]
  readonly codeLists?: Schedule10CodeLists
  // Write echoes only; absent on the GET.
  readonly message?: MessageInfo | null
}
