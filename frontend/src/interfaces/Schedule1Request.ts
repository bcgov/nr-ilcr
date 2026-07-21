// Mirrors the backend Schedule1Request DTO (Story 2.1) — the pinned AD-12 write contract. ENTERED
// fields only; derived/pulled/read-only fields are server-owned and must NOT be sent. `revisionCount`
// is the optimistic-lock token from the last loaded/returned document (AR11).

export interface EntryAmount {
  volume: number | null
  cost: number | null
}

export interface Schedule1LineItemInput {
  costItemCode: number
  volume: number | null
  cost: number | null
}

export interface SilvicultureInput {
  actualSpent: EntryAmount | null
  accruedLessActual: EntryAmount | null
}

export default interface Schedule1Request {
  revisionCount: number
  comments: string | null
  lineItems: Schedule1LineItemInput[] // writable fixed codes only: 12,13,14,15,16,17,18
  silviculture: SilvicultureInput // codes 1 & 2
  otherCostsVolume: number | null // shared Subtotal Other Costs volume (code-19 null-description row)
}

/** Writable fixed line-item codes (AC1). Pulled (139/143) and derived (140/144) are excluded. */
export const WRITABLE_LINE_ITEM_CODES = [12, 13, 14, 15, 16, 17, 18] as const
