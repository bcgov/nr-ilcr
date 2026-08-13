// Mirrors the backend Schedule 9 (Miscellaneous & Unique Logging Costs) DTOs — the wire contract
// pinned in Story 9.1 (read) and extended in Story 9.3 with `codeLists`. Jackson omits nulls
// (non_null), so absent members simply won't be in the JSON; every optional value and the derived
// `costPerUnit` are `number | null`. `costPerUnit` = cost ÷ units, computed server-side (AD-5), and
// is NEVER recomputed nor posted here — it is null when units are 0/blank (S14) and must render blank.

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

// One code-table option / a resolved code+label on a stored record.
export interface CodeDescription {
  readonly code: string
  readonly description: string
}

// The four dropdown option lists carried on the document (Story 9.3), so the page renders its selects
// without a second request. Contractual Items are the fixed category-'9' catalogue (108-114, BR-09);
// the unit/BEC/source lists are the reference tables. A record's stored code matches an option here.
export interface Schedule9CodeLists {
  readonly contractualItems: readonly CodeDescription[]
  readonly unitTypes: readonly CodeDescription[]
  readonly biogeoclimaticZones: readonly CodeDescription[]
  readonly sources: readonly CodeDescription[]
}

// One stored contractual-work record. `revisionCount` is the per-record optimistic-lock token echoed
// back on update (the document carries no token of its own). The four code-list selections arrive as
// resolved `{code, description}` pairs (null when unset); the three "Other" descriptions and the
// numeric fields are nullable in storage — Check Status flags the gaps, so they arrive ABSENT and the
// page seeds blanks rather than assuming a value.
export interface ContractualWorkRecord {
  readonly id: number
  readonly revisionCount: number
  readonly contractorId: string | null
  readonly contractualItem: CodeDescription | null
  readonly itemDescription: string | null
  readonly unitType: CodeDescription | null
  readonly unitDescription: string | null
  readonly numberOfUnits: number | null
  readonly biogeoclimaticZone: CodeDescription | null
  readonly cost: number | null
  // Server-derived (AD-5): cost ÷ units at scale 2; null when units are 0/blank. Read-only.
  readonly costPerUnit: number | null
  readonly sideSlopePct: number | null
  readonly source: CodeDescription | null
  readonly sourceDescription: string | null
  readonly comments: string | null
}

// Check Status (S09) result — read-only validation, no status transition, mutates nothing. `errors`
// carries one verbatim line per outstanding value ("Contractual Work Report Id : {row} {Field}: ..."),
// in record then legacy field order; `requirementsMetMessage` is the SUC-002 banner when all pass.
export interface Schedule9CheckStatusResponse {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

export default interface Schedule9Response {
  readonly millId: number
  readonly year: number
  // The Schedules 1-10 track code; Schedule 9 has no track of its own (AD-9).
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly records: readonly ContractualWorkRecord[]
  readonly codeLists?: Schedule9CodeLists
  // Write echoes only (AD-8); absent on the GET.
  readonly message?: MessageInfo | null
}
