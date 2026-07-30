// Mirrors the backend Schedule3Request DTO (Story 4.2), the pinned AD-12 write contract. ENTERED fields
// only; derived values (crown, subtotals/totals, timber costs, perUnit), read-only metadata
// (trackStatus/editable/counts) and the sub-page rows (items 124/38, Story 4.4) are server-owned and
// must NOT be sent. `revisionCount` is the optimistic-lock token from the last loaded/returned document.

// One fixed line's entered amounts, keyed by its Harvest cost-item code (27–37). `pop` is null for the
// Harvest-only lines (Annual Rents 29, Scaling 33, Silviculture Admin 37) — the server ignores it there.
export interface Schedule3LineItemInput {
  costItemCode: number
  harvest: number | null
  pop: number | null
}

export default interface Schedule3Request {
  revisionCount: number
  comments: string | null
  // "Y"/"N"; persisted to ILCR_REPORT_SUMMARY.LOCATION. BR-10 suppresses Harvest≥PO&P when "Y".
  overrideHarvestTotalPop: string | null
  lineItems: Schedule3LineItemInput[]
  // Schedule 3 volumes are NON-NEGATIVE [0, 9,999,999] (distinct from Schedule 1's signed range).
  popTimberVolume: number | null
  crownTimberVolume: number | null // drives the BR-09 push into Schedule 1 on save
}

/** The 8 lines carrying BOTH a Harvest and a PO&P entry column. */
export const HARVEST_POP_LINE_CODES = [27, 28, 30, 31, 32, 34, 35, 36] as const

/** Harvest-only lines: Annual Rents (29), Scaling (33), Silviculture Admin (37). PO&P is not entered. */
export const HARVEST_ONLY_LINE_CODES = [29, 33, 37] as const

/** All 11 fixed lines in legacy display order (schedule3.xhtml). */
export const ALL_LINE_CODES = [27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37] as const
