// Mirrors the backend Schedule 5 (Camp and Access Expenses) DTOs. Jackson omits nulls (non_null),
// so every nullable member is typed `| null` AND must be treated as possibly absent — `camp.recoveries`
// is undefined on a camp that never had one, not `{ cost: null }`.
//
// Every `costPerVolume`, the four totals (campSubTotal, campTotal, accessExpenseTotal,
// campAndAccessTotal), both counts, and the `cost` halves of the two `Other …` rows are computed
// server-side per BR-04 — never recomputed here (AD-5).

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

/**
 * One volume/cost/$-per-m³ triple — the single sub-shape every category row and every derived total
 * is rendered through.
 *
 * `cost` is whole dollars, widened to `Long` server-side so a sum cannot overflow; `costPerVolume`
 * is derived at scale 2 and is null when either side is null OR the volume is zero (no
 * divide-by-zero). `recoveries` is the one volume-less category and arrives as `{ cost }` alone.
 *
 * null is not 0: a stored null stays null and renders BLANK, never `0`/`0.00`.
 */
export interface CategoryAmount {
  readonly volume?: number | null
  readonly cost?: number | null
  readonly costPerVolume?: number | null
}

/**
 * One logging camp: its five descriptors, its twelve stored category amounts, the four derived
 * totals, and the two sub-page row counts. Member order mirrors the legacy screen
 * (`schedule5ExistingCamp.xhtml`) so the grid reads top-to-bottom as a licensee sees it.
 *
 * `revisionCount` is THIS camp's own optimistic-lock token (Schedule 5 has no schedule-level
 * revision row) and is a required primitive — a falsy `0` is a VALID token and must never be
 * coerced away.
 *
 * `isolatedCamp` is tri-state on the wire — true / false / null. It is required on save, so a null
 * from a legacy row must render as "nothing selected" and block the save, never default to No.
 */
export interface Camp {
  readonly campId: number
  readonly revisionCount: number
  readonly campName: string | null
  readonly roadDistanceToOperatingArea: number | null
  readonly sizeOfCamp: number | null
  readonly associatedCampVolume: number | null
  readonly isolatedCamp: boolean | null
  readonly comments: string | null

  // --- Camp Expenses ---
  readonly cateringAndFood?: CategoryAmount
  readonly wagesAndBenefits?: CategoryAmount
  readonly depreciationLease?: CategoryAmount
  readonly generalCampExpenses?: CategoryAmount
  /** Volume is entered; the cost half is the item-62 row sum (server-derived). */
  readonly otherCampExpenses?: CategoryAmount
  readonly campSubTotal?: CategoryAmount
  /** Cost only — no volume and no $/m³ cell exists for Recoveries, ever. */
  readonly recoveries?: CategoryAmount
  readonly campTotal?: CategoryAmount

  // --- Access Expenses ---
  readonly crewTransportation?: CategoryAmount
  readonly equipAndSuppliesLand?: CategoryAmount
  readonly equipAndSuppliesRail?: CategoryAmount
  readonly equipAndSuppliesAir?: CategoryAmount
  readonly equipAndSuppliesWater?: CategoryAmount
  /** Volume is entered; the cost half is the item-68 row sum (server-derived). */
  readonly otherAccessExpenses?: CategoryAmount
  readonly accessExpenseTotal?: CategoryAmount
  readonly campAndAccessTotal?: CategoryAmount

  /** Item-62 row count — drives the `Other Camp Expenses (n): ` link label. */
  readonly otherCampExpenseCount: number
  /** Item-68 row count — drives the `Other Access Expenses (n): ` link label. */
  readonly otherAccessExpenseCount: number
}

/**
 * The Schedule 5 read document. There are NO document-level totals (every total is per camp) and no
 * document-level `revisionCount`. A valid, active mill/year with no camps returns `camps: []` — a
 * 404 means the mill/year context row itself is missing.
 *
 * `editable` is server-authoritative (AD-9): never derive it from `trackStatus` or the role.
 */
export default interface Schedule5Response {
  readonly millId: number
  readonly year: number
  /** The Schedules 1–10 track — never the silviculture track. */
  readonly trackStatus: string
  readonly editable: boolean
  readonly camps: readonly Camp[]
  /** Absent on GET; carries the success echo on every write. */
  readonly message?: MessageInfo | null
}

/** One check-status line: the bundle key, the request field it points at, and the composed text. */
export interface CampCheckMessage {
  readonly key: string
  /** The CampRequest field this finding points at — absent on a camp's met message. */
  readonly field?: string | null
  readonly text: string
}

/**
 * One camp's check-status result. `messages` carries EITHER a single met message OR one
 * `Value Required` line per missing field — never both.
 */
export interface CampCheckResult {
  readonly campId: number
  readonly campName: string | null
  readonly requirementsMet: boolean
  readonly messages: readonly CampCheckMessage[]
}

/**
 * The check-status result — a read-only evaluation that mutates nothing.
 *
 * On `MET` the schedule banner is emitted ALONE and `camps` is EMPTY; on `ISSUES` `messages` is
 * empty and every camp reports. There is no `severity` field: severity is derived from `outcome`
 * and each camp's `requirementsMet`, which is what keeps it in sync with them.
 *
 * `MET` cannot distinguish "no camps" from "all camps complete" — the two responses are identical,
 * and a zero-camp schedule is vacuously met. Read `camps[]` from the GET document to tell them
 * apart, never from this response.
 */
export interface Schedule5CheckStatusResponse {
  readonly outcome: 'MET' | 'ISSUES'
  readonly messages: readonly MessageInfo[]
  readonly camps: readonly CampCheckResult[]
}
