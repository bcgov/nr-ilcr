// Mirrors the backend Schedule1Response DTO (Story 1.2). Numbers are nullable; Jackson omits nulls,
// so an absent line item / block member simply won't be in the JSON. perUnit and the subtotals are
// computed server-side and are read-only here — never sent on a write, and the server is the sole
// authority for every stored figure.
//
// They ARE mirrored for display while the schedule is being edited, so the read-only cells track entry
// before Save the way legacy did (defect #291; spine AD-5 amended 2026-08-20). That mirror lives in
// `components/schedule1/derived.ts` and nowhere else, and the Save echo supersedes it. Note Schedule 1
// rounds $/m³ with the LEGACY scale-2 rule (divide at scale 10, then scale 2), not the scale-4 rule
// Schedules 2 and 4 use.

export interface LineItem {
  readonly costItemCode: number
  readonly volume: number | null
  readonly cost: number | null
  readonly perUnit: number | null
}

export interface SilvicultureBlock {
  readonly actualSpent: LineItem | null
  readonly accruedLessActual: LineItem | null
  readonly lessAdmin: LineItem | null
  readonly total: LineItem | null
}

export interface OtherCostsSummary {
  readonly volume: number | null
  readonly costSubtotal: number | null
  readonly perUnit: number | null
  readonly count: number
}

// Success message carried on a mutating response (AD-8): the frontend renders `text` verbatim and
// never hardcodes SUC-* strings. Null/absent on the GET document.
export interface MessageInfo {
  readonly key: string
  readonly text: string
}

export default interface Schedule1Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly crownVolume: number | null
  // The Schedule 3 Crown Timber volume (BR-03 pre-fill source), read-only. Story 2.3.
  readonly schedule3CrownVolume: number | null
  // Optional AND nullable (defect #292): null until the schedule is saved, and the app-wide Jackson
  // `default-property-inclusion: non_null` then omits the key — so readers must use a loose `!= null`.
  // Unreachable through this page today (the GET 404s when unsaved), typed honestly so it stays safe.
  readonly revisionCount?: number | null
  readonly comments: string | null
  readonly lineItems: readonly LineItem[]
  readonly silviculture: SilvicultureBlock
  // BR-04 admin costs pulled from Schedule 3 (read-only): Forest Mgmt Admin (143) / Less Silv Admin (139).
  readonly forestMgmtAdminCost: number | null
  readonly lessSilvAdminCost: number | null
  readonly otherCosts: OtherCostsSummary
  // Derived read-only figures (legacy Schedule1MB getters) — server-computed; mirrored for display
  // during entry by `components/schedule1/derived.ts` only (defect #291).
  readonly forestMgmtAdminPerUnit: number | null // 143 $/m³
  readonly lessSilvAdminPerUnit: number | null // 139 $/m³
  readonly totalSilvicultureCost: number | null // 140 cost (silv actual − Sch3 silv admin + accrued)
  readonly totalSilviculturePerUnit: number | null // 140 $/m³
  readonly subtotalCompanyLoggingCost: number | null // 144 cost (Σ logging + FMA + other costs)
  readonly subtotalCompanyLoggingPerUnit: number | null // 144 $/m³
  readonly totalCompanyLoggingCost: number | null // grand total (subtotal + total silviculture)
  readonly totalCompanyLoggingPerUnit: number | null // grand total $/m³ (÷ Sch3 harvested crown volume)
  // Advisory, non-blocking messages carried on the GET (WRN-001 crown pre-fill rides here). Story 2.3.
  readonly warnings?: readonly MessageInfo[]
  readonly message?: MessageInfo | null
}
