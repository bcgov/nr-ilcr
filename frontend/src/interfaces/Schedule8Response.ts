// Mirrors the backend Schedule8Response DTO (Story 14.1 read + 14.2–14.4 write echoes + 14.6
// check-status). Jackson omits nulls (non_null), so absent members simply won't be in the JSON.
// Every derived value (percentTotal, actualHarvested, additionsTotal, deductionsTotal, finalRate,
// counts, the *Label companions, editable) is computed server-side — never recomputed here (AD-5).

export interface MessageInfo {
  readonly key: string
  readonly text: string
}

// One rate-adjustment row (TREE_TO_TRUCK_RATE_DETAIL). Whether it is an addition or a deduction is
// decided server-side by the cost item's subcategory; the row itself carries no add/deduct flag.
export interface RateRow {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly costItemCode: number | null
  readonly itemDescription: string | null
  readonly costingRate: number | null
  readonly costTypeCode: string | null
  readonly costTypeDescription: string | null
}

// One Tree-to-Truck sample. The six skidding %s are stored as entered; percentTotal is their
// server-side sum. actualHarvested = coniferous + deciduous. additionsTotal/deductionsTotal are the
// sums of the respective rate rows and finalRate = originalRate + additionsTotal − deductionsTotal —
// all read-only server-computed.
export interface Sample {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly contractId: string | null
  readonly cutBlock: string | null
  readonly groundBasePct: number | null
  readonly grapplePct: number | null
  readonly skylinePct: number | null
  readonly highleadPct: number | null
  readonly helicopterPct: number | null
  readonly otherSkiddingPct: number | null
  readonly percentTotal: number | null
  readonly skylineSlopeDistance: number | null
  readonly skylineSupportNumber: number | null
  readonly supportAvgDistance: number | null
  readonly distance: number | null
  readonly cycleTime: number | null
  readonly uphillDirection: boolean
  readonly waterDumpDestination: boolean
  readonly skidTypeCode: string | null
  readonly skidTypeDescription: string | null
  readonly coniferousVolume: number | null
  readonly deciduousVolume: number | null
  readonly actualHarvested: number | null
  readonly originalRate: number | null
  readonly additionsTotal: number | null
  readonly deductionsTotal: number | null
  readonly finalRate: number | null
  readonly additionCount: number
  readonly deductionCount: number
  readonly additions: RateRow[]
  readonly deductions: RateRow[]
}

// One report page (TREE_TO_TRUCK_REPORT). The six code fields carry both the stored code and its
// resolved *Label (looked up server-side); a code with no matching row leaves its label null.
export interface Page {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly division: string | null
  readonly license: string | null
  readonly contact: string | null
  readonly phone: string | null
  readonly cuttingPermit: string | null
  readonly supportCentre: string | null
  readonly supportCentreLabel: string | null
  readonly region: string | null
  readonly regionLabel: string | null
  readonly becZone: string | null
  readonly becZoneLabel: string | null
  readonly tsaNumber: string | null
  readonly tsaNumberLabel: string | null
  readonly tflNumber: string | null
  readonly tflNumberLabel: string | null
  readonly supplyBlock: string | null
  readonly supplyBlockLabel: string | null
  readonly comments: string | null
  readonly sampleCount: number
  readonly samples: Sample[]
}

export default interface Schedule8Response {
  readonly millId: number
  readonly year: number
  readonly trackStatus: string | null
  readonly editable: boolean
  readonly pages: Page[]
  readonly message?: MessageInfo | null
}

// Check Status (Story 14.6). Read-only, mutates nothing. outcome is 'MET' only when every in-scope
// page (and its samples) passes. The all-pages sweep and the single-page scope share this shape (the
// single-page result carries one page entry).
export interface CheckFieldIssue {
  readonly field: string
  readonly message: MessageInfo
}

export interface SampleCheckResult {
  readonly id: number | null
  readonly met: boolean
  readonly issues: CheckFieldIssue[]
}

export interface PageCheckResult {
  readonly id: number | null
  readonly met: boolean
  readonly issues: CheckFieldIssue[]
  readonly samples: SampleCheckResult[]
}

export interface Schedule8CheckStatusResponse {
  readonly outcome: 'MET' | 'ISSUES'
  readonly messages: MessageInfo[]
  readonly pages: PageCheckResult[]
}
