// Mirrors the backend Schedule3Response DTO (Stories 4.1/4.2), the AD-12 aggregate document. Numbers
// are nullable; Jackson omits nulls, so an absent line/block member simply won't be in the JSON. Every
// crown value, timber cost/perUnit, subtotal, total and Total Overhead figure is computed server-side
// and is read-only here — never sent on a write, and the server is the sole authority for every stored
// figure.
//
// They ARE mirrored for display while the schedule is being edited, so the read-only cells track entry
// before Save the way legacy did (defect #291; spine AD-5 amended 2026-08-20). That mirror lives in
// `components/schedule3/derived.ts` and nowhere else, and the Save echo supersedes it. Note the
// Scaling (33) line's `pop` is itself derived from the two timber volumes, and that Schedule 3 rounds
// $/m³ with the LEGACY scale-2 rule (divide at scale 10, then scale 2), not Schedules 2/4's scale 4.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

// One fixed admin-cost line in the three-column model. `crown` is derived (harvest − pop), read-only.
export interface CostLine {
  costItemCode: number
  harvest: number | null
  pop: number | null
  crown: number | null
}

// A timber / overhead block: PO&P Timber, Crown Timber, Total Overhead. `volume` is entered (except
// Total Overhead's, which is derived); `cost` and `perUnit` are server-computed read-only.
export interface TimberBlock {
  volume: number | null
  cost: number | null
  perUnit: number | null
}

// A derived three-column money total (Subtotal Other Costs, Subtotal Actual Costs, Included
// Unacceptable Costs, Total Costs). All fields server-computed read-only.
export interface ThreeColumnTotal {
  harvest: number | null
  pop: number | null
  crown: number | null
}

export default interface Schedule3Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  // Optional AND nullable (defect #292): null until the schedule is saved, and the app-wide Jackson
  // `default-property-inclusion: non_null` then omits the key — so readers must use a loose `!= null`.
  // Unreachable through this page today (the GET 404s when unsaved), typed honestly so it stays safe.
  revisionCount?: number | null
  // Override Harvest ⁄ Total PO&P indicator ("Y"/"N"), from the summary LOCATION column; defaults "N".
  overrideHarvestTotalPop: string | null
  comments: string | null
  // The 11 fixed admin-cost lines (codes 27–37), each with harvest/pop/derived crown.
  lineItems: CostLine[]
  popTimber: TimberBlock
  crownTimber: TimberBlock
  totalOverhead: TimberBlock
  subtotalOtherCosts: ThreeColumnTotal
  subtotalActualCosts: ThreeColumnTotal
  includedUnacceptableCosts: ThreeColumnTotal
  totalCosts: ThreeColumnTotal
  // Sub-page row-group counts (CNT-001) shown on the two sub-page links (Story 4.4 owns the sub-pages).
  otherAcceptableCount: number
  unacceptableCount: number
  // Advisory, non-blocking messages on a mutation echo (BR-09 crown-push WRN-001/002 ride here). Story 4.2.
  warnings?: MessageInfo[]
  // Success message on a mutation echo (AD-8), else null/absent on the GET.
  message?: MessageInfo | null
}
