// Mirrors the backend Schedule8Response DTO (Story 14.1 read + 14.2–14.4 write echoes + 14.6
// check-status). Jackson omits nulls (non_null), so absent members simply won't be in the JSON.
// Every derived value (percentTotal, actualHarvested, additionsTotal, deductionsTotal, finalRate,
// counts, the *Label companions, editable) is computed server-side — never recomputed here (AD-5).

export interface MessageInfo {
  key: string
  text: string
}

// One rate-adjustment row (TREE_TO_TRUCK_RATE_DETAIL). Whether it is an addition or a deduction is
// decided server-side by the cost item's subcategory; the row itself carries no add/deduct flag.
export interface RateRow {
  id: number | null
  revisionCount: number | null
  costItemCode: number | null
  itemDescription: string | null
  costingRate: number | null
  costTypeCode: string | null
  costTypeDescription: string | null
}

// One Tree-to-Truck sample. The six skidding %s are stored as entered; percentTotal is their
// server-side sum. actualHarvested = coniferous + deciduous. additionsTotal/deductionsTotal are the
// sums of the respective rate rows and finalRate = originalRate + additionsTotal − deductionsTotal —
// all read-only server-computed.
export interface Sample {
  id: number | null
  revisionCount: number | null
  contractId: string | null
  cutBlock: string | null
  groundBasePct: number | null
  grapplePct: number | null
  skylinePct: number | null
  highleadPct: number | null
  helicopterPct: number | null
  otherSkiddingPct: number | null
  percentTotal: number | null
  skylineSlopeDistance: number | null
  skylineSupportNumber: number | null
  supportAvgDistance: number | null
  distance: number | null
  cycleTime: number | null
  uphillDirection: boolean
  waterDumpDestination: boolean
  skidTypeCode: string | null
  skidTypeDescription: string | null
  coniferousVolume: number | null
  deciduousVolume: number | null
  actualHarvested: number | null
  originalRate: number | null
  additionsTotal: number | null
  deductionsTotal: number | null
  finalRate: number | null
  additionCount: number
  deductionCount: number
  additions: RateRow[]
  deductions: RateRow[]
}

// One report page (TREE_TO_TRUCK_REPORT). The six code fields carry both the stored code and its
// resolved *Label (looked up server-side); a code with no matching row leaves its label null.
export interface Page {
  id: number | null
  revisionCount: number | null
  division: string | null
  license: string | null
  contact: string | null
  phone: string | null
  cuttingPermit: string | null
  supportCentre: string | null
  supportCentreLabel: string | null
  region: string | null
  regionLabel: string | null
  becZone: string | null
  becZoneLabel: string | null
  tsaNumber: string | null
  tsaNumberLabel: string | null
  tflNumber: string | null
  tflNumberLabel: string | null
  supplyBlock: string | null
  supplyBlockLabel: string | null
  comments: string | null
  sampleCount: number
  samples: Sample[]
}

export default interface Schedule8Response {
  millId: number
  year: number
  trackStatus: string | null
  editable: boolean
  pages: Page[]
  message?: MessageInfo | null
}

// Check Status (Story 14.6). Read-only, mutates nothing. outcome is 'MET' only when every in-scope
// page (and its samples) passes. The all-pages sweep and the single-page scope share this shape (the
// single-page result carries one page entry).
export interface CheckFieldIssue {
  field: string
  message: MessageInfo
}

export interface SampleCheckResult {
  id: number | null
  met: boolean
  issues: CheckFieldIssue[]
}

export interface PageCheckResult {
  id: number | null
  met: boolean
  issues: CheckFieldIssue[]
  samples: SampleCheckResult[]
}

export interface Schedule8CheckStatusResponse {
  outcome: 'MET' | 'ISSUES'
  messages: MessageInfo[]
  pages: PageCheckResult[]
}
