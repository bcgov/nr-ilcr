// Mirrors the backend dataextract DataExtractRequest record (Story 21.1 pinned wire contract,
// POST /v1/reports/data-extract). The years travel as raw STRINGS, empty included: a blank year has
// to stay distinguishable from a chosen one all the way to the server, which is what lets it answer
// with the verbatim required-field message instead of a framework type error.
export default interface DataExtractRequest {
  readonly startYear: string
  readonly endYear: string
  readonly millIds: number[]
  readonly schedules: string[]
}
