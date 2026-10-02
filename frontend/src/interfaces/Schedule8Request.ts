// Mirrors the backend Schedule8PageRequest / Schedule8SampleRequest / Schedule8RateRequest write DTOs
// (Stories 14.2–14.4). The server is authoritative for validation, the Draft gate, the TFL⇄supply-block
// normalization, and the optimistic lock; derived/read-only fields (percentTotal, actualHarvested,
// totals, labels, counts) are never sent (AD-5).

// Report-page save (create-or-edit). id null = create, present = edit (rename-safe). revisionCount is
// the optimistic-lock token from the read (null on create). TFL vs Supply Block are mutually exclusive
// server-side; the client sends both fields and the service clears the inapplicable one.
export interface Schedule8PageRequest {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly license: string
  readonly supportCentre: string
  readonly region: string
  readonly becZone: string
  readonly tsaNumber: string | null
  readonly tflNumber: string | null
  readonly supplyBlock: string | null
  readonly division: string | null
  readonly contact: string | null
  readonly phone: string | null
  readonly cuttingPermit: string | null
  readonly comments: string | null
}

// Sample save (create-or-edit) under a page. uphillDirection/waterDumpDestination are nullable so
// "not provided" is distinguishable when the Helicopter conditional requires them.
export interface Schedule8SampleRequest {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly contractId: string
  readonly cutBlock: string | null
  readonly groundBasePct: number | null
  readonly grapplePct: number | null
  readonly skylinePct: number | null
  readonly highleadPct: number | null
  readonly helicopterPct: number | null
  readonly otherSkiddingPct: number | null
  readonly skylineSlopeDistance: number | null
  readonly skylineSupportNumber: number | null
  readonly supportAvgDistance: number | null
  readonly cycleTime: number | null
  readonly distance: number | null
  readonly uphillDirection: boolean | null
  readonly waterDumpDestination: boolean | null
  readonly skidTypeCode: string | null
  readonly coniferousVolume: number | null
  readonly deciduousVolume: number | null
  readonly originalRate: number | null
}

// Rate-detail add-or-edit under a sample. Whether the row lands in additions or deductions is derived
// server-side from the cost item's subcategory. costTypeDescription is read-only, never sent.
export interface Schedule8RateRequest {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly costItemCode: number | null
  readonly costingRate: number | null
  readonly costTypeCode: string
  readonly itemDescription: string | null
}

// The all-pages Check Status body, mirroring the backend Schedule8CheckRequest (#359): the page panel
// currently ON SCREEN, overlaid by the server onto the stored page with the same id. Only an EXISTING
// page is sent (legacy built a new page outside the checked list); null when no such panel is open.
// Samples are never sent — they are edited on another view, so the server reads them from storage.
// A blank field is sent as null, never as a placeholder: reporting it is the check's purpose.
export interface Schedule8CheckRequest {
  readonly page: {
    readonly id: number
    readonly division: string | null
    readonly contact: string | null
    readonly phone: string | null
    readonly tsaNumber: string | null
    readonly tflNumber: string | null
    readonly supplyBlock: string | null
    readonly cuttingPermit: string | null
  } | null
}

// The single-page Check Status body, mirroring the backend Schedule8PageCheckRequest (#359): the
// sample panel currently ON SCREEN under that page. A NEW sample is sent too, with id null — legacy's
// Add put the unsaved row straight into the checked list, so the server appends it as the page's next
// sample. null when no panel is open. The page header is never sent (read-only on this view). Null
// stays null: a blank figure is reported as missing, never coerced to 0.
export interface Schedule8PageCheckRequest {
  readonly sample: {
    readonly id: number | null
    readonly contractId: string | null
    readonly cutBlock: string | null
    readonly groundBasePct: number | null
    readonly grapplePct: number | null
    readonly skylinePct: number | null
    readonly highleadPct: number | null
    readonly helicopterPct: number | null
    readonly otherSkiddingPct: number | null
    readonly skylineSlopeDistance: number | null
    readonly skylineSupportNumber: number | null
    readonly supportAvgDistance: number | null
    readonly coniferousVolume: number | null
    readonly deciduousVolume: number | null
    readonly originalRate: number | null
  } | null
}
