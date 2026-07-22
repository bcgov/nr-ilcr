// Mirrors the backend Schedule8PageRequest / Schedule8SampleRequest / Schedule8RateRequest write DTOs
// (Stories 14.2–14.4). The server is authoritative for validation, the Draft gate, the TFL⇄supply-block
// normalization, and the optimistic lock; derived/read-only fields (percentTotal, actualHarvested,
// totals, labels, counts) are never sent (AD-5).

// Report-page save (create-or-edit). id null = create, present = edit (rename-safe). revisionCount is
// the optimistic-lock token from the read (null on create). TFL vs Supply Block are mutually exclusive
// server-side; the client sends both fields and the service clears the inapplicable one.
export interface Schedule8PageRequest {
  id: number | null
  revisionCount: number | null
  license: string
  supportCentre: string
  region: string
  becZone: string
  tsaNumber: string | null
  tflNumber: string | null
  supplyBlock: string | null
  division: string | null
  contact: string | null
  phone: string | null
  cuttingPermit: string | null
  comments: string | null
}

// Sample save (create-or-edit) under a page. uphillDirection/waterDumpDestination are nullable so
// "not provided" is distinguishable when the Helicopter conditional requires them.
export interface Schedule8SampleRequest {
  id: number | null
  revisionCount: number | null
  contractId: string
  cutBlock: string | null
  groundBasePct: number | null
  grapplePct: number | null
  skylinePct: number | null
  highleadPct: number | null
  helicopterPct: number | null
  otherSkiddingPct: number | null
  skylineSlopeDistance: number | null
  skylineSupportNumber: number | null
  supportAvgDistance: number | null
  cycleTime: number | null
  distance: number | null
  uphillDirection: boolean | null
  waterDumpDestination: boolean | null
  skidTypeCode: string | null
  coniferousVolume: number | null
  deciduousVolume: number | null
  originalRate: number | null
}

// Rate-detail add-or-edit under a sample. Whether the row lands in additions or deductions is derived
// server-side from the cost item's subcategory. costTypeDescription is read-only, never sent.
export interface Schedule8RateRequest {
  id: number | null
  revisionCount: number | null
  costItemCode: number | null
  costingRate: number | null
  costTypeCode: string
  itemDescription: string | null
}
