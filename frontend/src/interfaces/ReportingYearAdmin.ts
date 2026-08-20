// Open Reporting Year admin wire contract (Story 24.1 / UC-RY-001).

/** The state the Open Reporting Year page renders (GET /api/v1/admin/reporting-years). */
export interface ReportingYearAdminView {
  openYears: number[]
  /** The year the recurring path would create (max + 1), or null on first-time setup. */
  nextYear: number | null
  firstTime: boolean
  /** The bounded starting-year options for first-time setup (BR-07); empty otherwise. */
  selectableStartYears: number[]
}

/** The outcome of opening a year (POST /api/v1/admin/reporting-years). */
export interface OpenReportingYearResponse {
  year: number
  millsInitialized: number
  messageKey: string
  message: string
}
