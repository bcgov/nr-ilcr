// Mirrors the backend Schedule1Request DTO (Story 2.1) — the pinned AD-12 write contract. ENTERED
// fields only; derived/pulled/read-only fields are server-owned and must NOT be sent. `revisionCount`
// is the optimistic-lock token from the last loaded/returned document (AR11).

export interface EntryAmount {
  readonly volume: number | null
  readonly cost: number | null
}

export interface Schedule1LineItemInput {
  readonly costItemCode: number
  readonly volume: number | null
  readonly cost: number | null
}

export interface SilvicultureInput {
  readonly actualSpent: EntryAmount | null
  readonly accruedLessActual: EntryAmount | null
  // 139 / 140 VOLUME only — their cost is pulled from Sch 3 (139) or derived (140), never sent.
  readonly lessAdminVolume: number | null
  readonly totalVolume: number | null
}

export default interface Schedule1Request {
  readonly revisionCount: number
  readonly comments: string | null
  readonly lineItems: readonly Schedule1LineItemInput[] // writable fixed codes with volume+cost: 12,13,14,15,16,17,18
  readonly silviculture: SilvicultureInput // codes 1 & 2 (vol+cost); 139 & 140 (vol only)
  readonly otherCostsVolume: number | null // shared Subtotal Other Costs volume (code-19 null-description row)
  // 143 / 144 VOLUME only — their cost is pulled (143) / derived (144), never sent.
  readonly forestMgmtAdminVolume: number | null
  readonly subtotalCompanyLoggingVolume: number | null
}

/** Fixed line-item codes writable with volume + cost. */
export const WRITABLE_LINE_ITEM_CODES = [12, 13, 14, 15, 16, 17, 18] as const

/** Codes whose VOLUME is user-entered but whose cost is pulled/derived (read-only). */
export const VOLUME_ONLY_8_DIGIT_CODES = [143, 144] as const
export const VOLUME_ONLY_7_DIGIT_CODES = [139, 140] as const

/**
 * The Check Status body — the values currently ON SCREEN, mirroring the backend
 * `Schedule1CheckRequest` (issue #359).
 *
 * Legacy's Check Status was a full form postback, so the verdict described the screen and not the
 * saved record. The itemized Other Costs rows are NOT sent: they are edited on the Other Costs
 * sub-page, never on this screen, and the server reads them from the database.
 *
 * ⚠ `null` means "no usable value on screen" and MUST stay null — the server's check is a pure null
 * test (a stored `0` passes), so coercing a blank field to `0` turns a missing value into a pass.
 */
export interface Schedule1CheckRequest {
  // 12–18, 1, 2 carry volume + cost; 143, 144, 139, 140 carry a volume only (cost null).
  readonly lineItems: readonly Schedule1LineItemInput[]
  readonly otherCostsVolume: number | null
}
