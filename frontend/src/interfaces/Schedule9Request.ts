// Mirrors the backend ContractualWorkRecordRequest write DTO (Story 9.2). Entered fields only — the
// derived `costPerUnit` is deliberately ABSENT so a client cannot supply it (AD-5). The server is
// authoritative for validation (FLD-001..005), the Draft gate, and the per-record optimistic lock;
// `revisionCount` is the token echoed from the served record, required on UPDATE only.
//
// Required at Save (legacy `required="true"`): contractorId, contractualItemCode, unitCode,
// biogeoclimaticZone, sourceCode. numberOfUnits, cost, sideSlopePct, and the three "Other"
// descriptions are OPTIONAL at Save — legacy leaves the descriptions un-required (the shipped 9.2
// parity fix); Check Status flags blank units/cost. Do NOT tighten the descriptions to required.
//
// Schedule 9 is PER-RECORD: each add is a POST and each edit a PUT of one record. There is NO
// save-all batch (unlike Schedule 7B) — the backend exposes only per-record write endpoints.

export default interface ContractualWorkRecordRequest {
  readonly contractorId: string
  // The Contractual Item cost-item id (108-114, BR-09).
  readonly contractualItemCode: number
  // "Other" item free text — kept only when the item is 114, else stored NULL by the server.
  readonly itemDescription?: string | null
  readonly unitCode: string
  // "Other" unit free text — kept only when the unit is "O".
  readonly unitDescription?: string | null
  // Units performed (0.0-99,999.9); optional at Save.
  readonly numberOfUnits?: number | null
  readonly biogeoclimaticZone: string
  // Whole dollars (0-9,999,999); optional at Save.
  readonly cost?: number | null
  // 0-100 at Save; kept only when the item is 111/112 (road deactivation), else stored NULL.
  readonly sideSlopePct?: number | null
  readonly sourceCode: string
  // "Other" source free text — kept only when the source is "O" or "S".
  readonly sourceDescription?: string | null
  readonly comments?: string | null
  // Required on UPDATE only (read from the loaded record, never hardcoded or coerced).
  readonly revisionCount?: number
}

/**
 * The Check Status body — every record as it is ON SCREEN, mirroring the backend
 * `Schedule9CheckRequest` (#359). Legacy's check read the bean's in-memory document, which every row
 * input wrote into on change, so the verdict described the screen (rows on other paginator pages
 * included) rather than the saved record. Rows are numbered server-side by their ORDINAL here, so they
 * must be sent in document order. The Add panel's draft is never part of it (Add saves at once).
 *
 * ⚠ Every member is nullable and `null` MUST stay null: the server's check is a pure null test (a
 * typed `0` passes), so coercing a blank field to `0` turns a missing value into a pass.
 */
export interface ContractualWorkCheckEntry {
  readonly contractorId: string | null
  readonly contractualItemCode: number | null
  readonly sideSlopePct: number | null
  readonly numberOfUnits: number | null
  readonly unitCode: string | null
  readonly biogeoclimaticZone: string | null
  readonly cost: number | null
  readonly sourceCode: string | null
}

export interface Schedule9CheckRequest {
  readonly records: readonly ContractualWorkCheckEntry[]
}
