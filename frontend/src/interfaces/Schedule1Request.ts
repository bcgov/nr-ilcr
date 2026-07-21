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
  // 139 / 140 VOLUME only — their cost is pulled from Sch 3 (139) or derived (140), never sent.
  lessAdminVolume: number | null
  totalVolume: number | null
}

export default interface Schedule1Request {
  revisionCount: number
  comments: string | null
  lineItems: Schedule1LineItemInput[] // writable fixed codes with volume+cost: 12,13,14,15,16,17,18
  silviculture: SilvicultureInput // codes 1 & 2 (vol+cost); 139 & 140 (vol only)
  otherCostsVolume: number | null // shared Subtotal Other Costs volume (code-19 null-description row)
  // 143 / 144 VOLUME only — their cost is pulled (143) / derived (144), never sent.
  forestMgmtAdminVolume: number | null
  subtotalCompanyLoggingVolume: number | null
}

/** Fixed line-item codes writable with volume + cost. */
export const WRITABLE_LINE_ITEM_CODES = [12, 13, 14, 15, 16, 17, 18] as const

/** Codes whose VOLUME is user-entered but whose cost is pulled/derived (read-only). */
export const VOLUME_ONLY_8_DIGIT_CODES = [143, 144] as const
export const VOLUME_ONLY_7_DIGIT_CODES = [139, 140] as const
