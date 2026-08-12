// Mirrors the backend Schedule 7B (Culvert Costs) DTOs — the wire contract pinned in Story 13.1.
// Jackson omits nulls (non_null), so absent members simply won't be in the JSON, which is why every
// optional value and `totalCost` are `number | null`. `totalCost` is computed server-side from the
// two costs (BR-05) and is NEVER recomputed here (AD-5); a total with no contributing cost is omitted
// and must render blank, not 0.

import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

// One code-table option for the Type dropdown.
export interface CulvertCodeOption {
  readonly code: string
  readonly description: string
}

// Schedule 7B has exactly ONE code list (unlike 7A's five): the Culvert Type rows effective for the
// reporting year. Carried on the document so the page renders its dropdown without a second request —
// a Table Maintenance addition (e.g. the 2026-08-11 `RP` Round Plastic) therefore reaches this form
// with no frontend change.
export interface CulvertCodeLists {
  readonly culvertTypes: readonly CulvertCodeOption[]
}

// One stored culvert. `rowCounter` is the 1-based list ordinal the server uses to compose
// check-status message lines — display it, never derive message text from it. `revisionCount` is the
// per-row optimistic-lock token echoed back on update (the document carries no token of its own).
export interface Culvert {
  readonly culvertReportId: number
  readonly rowCounter: number
  // Nullable in storage even where the write DTO requires it: legacy rows predate the validation and
  // Check Status exists precisely to flag the gaps. Jackson omits nulls, so these arrive ABSENT — the
  // page seeds blanks rather than assuming a string.
  readonly culvertTypeCode: string | null
  // Millimetres, whole numbers.
  readonly spanSize: number | null
  readonly riseSize: number | null
  // Metres, one decimal.
  readonly length: number | null
  readonly culvertPieceCount: number | null
  // Whole dollars (cost items 77 / 78).
  readonly materialCost: number | null
  readonly installCost: number | null
  // Server-derived (BR-05): materialCost + installCost, null when both are absent. Read-only — the
  // legacy Total field is `disabled="true"` and the write DTO has no such member.
  readonly totalCost: number | null
  readonly comments: string | null
  readonly revisionCount: number
}

// Check Status (BR-07) result — read-only validation, no status transition, mutates nothing.
// Deliberately has NO per-culvert all-met list (unlike its Schedule 7A twin's `bridgeMessages`):
// legacy 7B emits only the schedule-wide line, and inventing a per-culvert one would be a fabricated
// message. `errors` carries one verbatim line per missing value, in legacy emission order.
export interface Schedule7bCheckStatusResponse {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

export default interface Schedule7bResponse {
  readonly millId: number
  readonly year: number
  // The Schedules 1-10 track code; Schedule 7B has no track of its own (BR-01).
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly culverts: readonly Culvert[]
  readonly codeLists: CulvertCodeLists
  // Write echoes only (AD-8); absent on the GET.
  readonly message?: MessageInfo | null
}
