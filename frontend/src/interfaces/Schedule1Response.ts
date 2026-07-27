// Mirrors the backend Schedule1Response DTO (Story 1.2). Numbers are nullable; Jackson omits nulls,
// so an absent line item / block member simply won't be in the JSON. perUnit and subtotals are
// computed server-side (read-only) — never recompute them client-side.

export interface LineItem {
  costItemCode: number
  volume: number | null
  cost: number | null
  perUnit: number | null
}

export interface SilvicultureBlock {
  actualSpent: LineItem | null
  accruedLessActual: LineItem | null
  lessAdmin: LineItem | null
  total: LineItem | null
}

export interface OtherCostsSummary {
  volume: number | null
  costSubtotal: number | null
  perUnit: number | null
  count: number
}

// Success message carried on a mutating response (AD-8): the frontend renders `text` verbatim and
// never hardcodes SUC-* strings. Null/absent on the GET document.
export interface MessageInfo {
  key: string
  text: string
}

export default interface Schedule1Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  crownVolume: number | null
  // The Schedule 3 Crown Timber volume (BR-03 pre-fill source), read-only. Story 2.3.
  schedule3CrownVolume: number | null
  revisionCount: number | null
  comments: string | null
  lineItems: LineItem[]
  silviculture: SilvicultureBlock
  // BR-04 admin costs pulled from Schedule 3 (read-only): Forest Mgmt Admin (143) / Less Silv Admin (139).
  forestMgmtAdminCost: number | null
  lessSilvAdminCost: number | null
  otherCosts: OtherCostsSummary
  // Derived read-only figures (legacy Schedule1MB getters) — server-computed, never recompute client-side.
  forestMgmtAdminPerUnit: number | null // 143 $/m³
  lessSilvAdminPerUnit: number | null // 139 $/m³
  totalSilvicultureCost: number | null // 140 cost (silv actual − Sch3 silv admin + accrued)
  totalSilviculturePerUnit: number | null // 140 $/m³
  subtotalCompanyLoggingCost: number | null // 144 cost (Σ logging + FMA + other costs)
  subtotalCompanyLoggingPerUnit: number | null // 144 $/m³
  totalCompanyLoggingCost: number | null // grand total (subtotal + total silviculture)
  totalCompanyLoggingPerUnit: number | null // grand total $/m³ (÷ Sch3 harvested crown volume)
  // Advisory, non-blocking messages carried on the GET (WRN-001 crown pre-fill rides here). Story 2.3.
  warnings?: MessageInfo[]
  message?: MessageInfo | null
}
