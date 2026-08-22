// Mirrors the backend Schedule 6 (Road Management Costs) DTOs, frozen by Stories 8.1/8.2 — consumed
// here, never re-shaped (AD-12). Jackson omits nulls (non_null), so every nullable member is typed
// `| null` AND may simply be absent from the JSON; read them defensively. `rmg`, `costPerVolume` and
// the three totals are DERIVED server-side (BR-04/BR-07) and are response-only — never recomputed
// here (AD-5) and never sent back.

import type { CodeDescription } from '@/utils/codes'
import type { MessageInfo } from './Schedule1Response'

export type { MessageInfo }

// The TSA-number and Supply-Block dropdown lists, served in-document (Schedule6CodeLists.java).
// Both are year-filtered server-side and always include any code a stored record already references.
// There is no TFL list: TFL stays a free-text entry (schedule6.xhtml:102,290), and the synthetic
// "TFL" sentinel is not a code-table row, so the control — not the document — adds it.
export interface Schedule6CodeLists {
  readonly tsaNumbers: readonly CodeDescription[]
  readonly supplyBlocks: readonly CodeDescription[]
}

// One road-maintenance record. A record is either a Timber Supply Area (`areaType` = the TSA code,
// `supplyBlock` = the TSB code) or a Tree Farm Licence (`areaType` = the literal "TFL", `tflNumber`
// = the TFL code) — BR-02, mutually exclusive, so the counterpart field is always null.
// `revisionCount` is THIS ROW's optimistic-lock token: Schedule 6 has no schedule-level summary row,
// so concurrency is keyed per record (the AR11 delta recorded in Story 8.1).
export interface RoadRecord {
  readonly recordId: number
  readonly revisionCount: number | null
  readonly areaType: string | null
  readonly tflNumber: string | null
  readonly supplyBlock: string | null
  readonly rmg: string | null
  readonly volume: number | null
  // Whole dollars (Integer on the wire).
  readonly cost: number | null
  // Null when volume is zero/absent — 0/0 is undefined, not zero (Story 8.1 deviation (c)).
  readonly costPerVolume: number | null
  // The PER-RECORD comment: ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE), a different and
  // much narrower column than the schedule-level `generalComments` below.
  readonly comments: string | null
}

// One missing-field finding. `field` names the request field the user must supply (`areaType`,
// `tflNumber`, `supplyBlock`, `cost`); `message.text` is the composed verbatim line, e.g.
// "Road : 1 - TSA or TFL (Cost $) : Value Required".
export interface FieldIssue {
  readonly field: string
  readonly message: MessageInfo
}

// One record's Check Status result. `rowCounter` is the 1-based ordinal the user sees in the message
// text (and the accordion title). `recordId` carries that SAME 1-based payload ordinal, not a
// database id — a payload row addresses no stored record, so there is no id to echo; it is a React
// list key only, never a correlation back to a stored row. `metMessage` is present only when this
// record is met AND the schedule outcome is ISSUES, and Jackson may omit it entirely rather than send
// null (Story 8.2 deviation (i)).
export interface RoadRecordCheckResult {
  readonly recordId: number
  readonly rowCounter: number
  readonly met: boolean
  readonly metMessage?: MessageInfo | null
  readonly issues: readonly FieldIssue[]
}

// Check Status (S09–S11, S20, S21) — read-only readiness validation that mutates nothing. A MET
// outcome carries the single schedule banner in `messages` and NO per-record results at all (the
// legacy pass branch never enters the per-record loop).
export interface Schedule6CheckStatusResponse {
  readonly outcome: 'MET' | 'ISSUES'
  readonly messages: readonly MessageInfo[]
  readonly records: readonly RoadRecordCheckResult[]
}

export default interface Schedule6Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  // SERVER-AUTHORITATIVE (AD-9): EDIT_SCHEDULE ∧ trackStatus == "D". Never derived on the client.
  readonly editable: boolean
  // The single schedule-level comment (ROAD_MAINTENANCE_REPORT.COMMENTS, 4000 wide; capped at the
  // legacy UI's 3500). Saved independently of any record (BR-09).
  readonly generalComments: string | null
  // Placeholder rows (a lone general-comment holder) are excluded server-side, so an empty list with
  // a non-empty general comment is the valid S18 state.
  readonly roadRecords: readonly RoadRecord[]
  readonly codeLists: Schedule6CodeLists
  readonly totalVolume: number | null
  readonly totalCost: number | null
  // Null in the S18 lone-comment state (0/0), where totalVolume/totalCost are 0 — renders BLANK.
  readonly totalCostPerVolume: number | null
  // Write echoes only (AD-8); absent on the GET.
  readonly message?: MessageInfo | null
}
