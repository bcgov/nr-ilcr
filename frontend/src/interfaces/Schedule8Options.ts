// Mirrors the backend Schedule8Options DTO: the reference-data option lists that back the Schedule 8
// page-editor dropdowns. Each choice carries the stored `code` and its resolved `description` — the
// editor shows the description and writes back the code. Fetched once from GET /v1/schedule8/options.

export interface CodeOption {
  readonly code: string
  readonly description: string
}

export default interface Schedule8Options {
  readonly supportCentres: CodeOption[]
  readonly regions: CodeOption[]
  readonly becZones: CodeOption[]
  readonly tsaNumbers: CodeOption[]
  readonly tflNumbers: CodeOption[]
  readonly supplyBlocks: CodeOption[]
  readonly skidTypes: CodeOption[]
  readonly additionCostItems: CodeOption[]
  readonly deductionCostItems: CodeOption[]
  readonly costTypes: CodeOption[]
}
