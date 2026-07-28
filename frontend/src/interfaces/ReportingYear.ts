// Mirrors the backend millcontext ReportingYear DTO (Story 1.1 pinned wire contract,
// GET /v1/reporting-years). Returned ordered by year descending.
export default interface ReportingYear {
  readonly reportYear: number
}
