// Mirrors the backend Schedule 5 sub-page DTOs (Story 7.4) — the itemized Other Camp (item 62) and
// Other Access (item 68) expense rows. Jackson omits nulls (non_null), so every nullable member is
// typed `| null` AND must be treated as possibly absent.
//
// The footer `totals` and every `costPerVolume` are computed server-side (AD-5) and are never
// recomputed here. There is deliberately no client-side sum anywhere in this feature.

import type { CategoryAmount, MessageInfo } from './Schedule5Response'

export type { CategoryAmount, MessageInfo }

/** Which sub-page — decides the endpoint, the cost band, and the required-timing behaviour. */
export type SubPageKind = 'CAMP' | 'ACCESS'

/**
 * One itemized expense row as SERVED.
 *
 * `volume` is stamped at read from the camp's item-141/142 amount and is NEVER a stored per-row
 * value — legacy's DAO never writes one (`Schedule5DAO.java:617-633`). It is therefore identical on
 * every row of a page, and changing the Associated Camp Volume retroactively changes what every
 * existing row displays.
 *
 * `description` is genuinely optional: a blank description is a legal, storable state that Check
 * Status flags — it is not a validation failure (deviation (F)).
 */
export interface SubPageRow {
  readonly rowId: number
  readonly description?: string | null
  readonly volume?: number | null
  readonly cost?: number | null
  readonly costPerVolume?: number | null
}

/**
 * One row as SUBMITTED. `rowId` decides the operation: null inserts, a known id updates in place,
 * and a stored row simply left out of the array is deleted.
 *
 * There is no `volume` — the server never accepts one.
 */
export interface SubPageRowRequest {
  readonly rowId: number | null
  readonly description: string | null
  readonly cost: number | null
}

/** The whole list, submitted as one batch — this endpoint is the sole writer of its item id. */
export interface SubPageSaveRequest {
  readonly rows: readonly SubPageRowRequest[]
}

/**
 * One sub-page's document: the rows plus the camp context the page renders around them.
 *
 * `editable` is server-authoritative (AD-9) — never derived here from `trackStatus` or the role.
 *
 * ⚠ `totals` is NOT the camp panel's figure for the same category, and the two PAGES compute it
 * differently (deviation (C)): the CAMP footer's volume is the sum of the row volumes (= n × camp
 * volume), the ACCESS footer's is the single camp volume. They look identical on screen and are not.
 */
export interface SubPageDocument {
  readonly campId: number
  readonly campName: string | null
  readonly associatedCampVolume?: number | null
  readonly editable: boolean
  readonly rows: readonly SubPageRow[]
  readonly totals?: CategoryAmount
  readonly message?: MessageInfo | null
}

/** One row being edited in the grid — strings, because an in-progress input is not yet a number. */
export interface SubPageRowForm {
  /** null for a row that has not been saved yet. */
  readonly rowId: number | null
  readonly description: string
  readonly cost: string
}
