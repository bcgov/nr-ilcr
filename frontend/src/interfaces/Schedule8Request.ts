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
