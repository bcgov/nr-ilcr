export const baseURL = process.env.E2E_BASE_URL || 'http://localhost:3000/'

// Live-data gate (Story 1.5 Task 5): the Home/schedule specs need the app running on the seeded
// delivery DB. Unset, empty, '0', and 'false' all mean OFF, so an operator "disabling" the gate
// with E2E_LIVE_DATA=0 gets skips, not a live run against a target that lacks the pinned fixtures.
export const liveDataEnabled = !['', '0', 'false'].includes(
  (process.env.E2E_LIVE_DATA ?? '').trim().toLowerCase(),
)

// ---------------------------------------------------------------------------
// Story 1.5 — Home E2E scenario fixtures.
//
// Bound to CONFIRMED rows in the seeded delivery-extract Oracle
// (THE/…@localhost:1525/DBDOCK_01, loaded from ilcr_test_data.sql via load.sql).
// Verified 2026-07-27 with GET /api/v1/mill-context (Story 1.5 Task 1). A re-seed /
// re-extract can renumber this data, so a fixture re-grounding pass is part of any
// re-extract — change the values HERE only (single source of truth for the specs),
// including each fixture's expected track-status description/date.
// ---------------------------------------------------------------------------

export interface TrackStatusFixture {
  description: string
  date?: string // omitted → the banner renders "Not Initiated"
}

export interface MillFixture {
  millId: number
  millNumber: string
  millName: string
  year: number
  // Expected banner status lines for this (mill, year), from the confirmed DB rows.
  // Omitted entirely → the banner suppresses that track's line (the S07 shape).
  sch1To10?: TrackStatusFixture
  sch11?: TrackStatusFixture
}

// S01 / S03: an OPEN (ACT) mill that IS viewable and has report-status rows, one of
// which (Sch 1-10) carries a real date — proves the dated status line renders.
export const OPEN_MILL_WITH_STATUS: MillFixture = {
  millId: 12050,
  millNumber: '987',
  millName: 'TURTLE DOVE',
  year: 2017,
  sch1To10: { description: 'Draft', date: '2018-05-09' },
  sch11: { description: 'Draft' },
}

// S03: a DIFFERENT open (mill, year) to switch to — both track statuses undated here.
export const OPEN_MILL_ALT: MillFixture = {
  millId: 9050,
  millNumber: '760',
  millName: 'WESTEROS',
  year: 2019,
  sch1To10: { description: 'Draft' },
  sch11: { description: 'Draft' },
}

// S06: a CLOSED (CLS) mill. It saves and banners exactly like an open mill, but its
// Schedule 1 page is blocked from viewing (millViewable:false → 409 on the schedule GET).
export const CLOSED_MILL: MillFixture = {
  millId: 13,
  millNumber: '45',
  millName: 'GORMAN BROS. - DOWNIE STREET',
  year: 2017,
  sch1To10: { description: 'Draft' },
  sch11: { description: 'Draft' },
}

// S07: an open, viewable mill for a year with NO ILCR_MILL_REPORT_STATUS row — the banner
// shows the Mill line only, both status lines suppressed, no error.
export const MILL_NO_STATUS: MillFixture = {
  millId: 16050,
  millNumber: '514',
  millName: 'AAA MILLING',
  year: 2016,
  // deliberately no sch1To10/sch11 — that absence IS the scenario
}

// The exact text of a Mill dropdown option — mirrors Home's `millItemToString`
// (`${millNumber} - ${millName}`, components/home/index.tsx).
export const millOptionText = (m: MillFixture): string => `${m.millNumber} - ${m.millName}`

// Verbatim contract strings (AD-8 — API-owned, asserted exactly; never re-typed by the app).
export const MSG = {
  saved: 'Data saved successfully', // SUC-001
  millRequired: 'Mill: Value is required.', // S04/S08
  yearRequired: 'Reporting Year: Value is required.', // S05/S08
  // The 409 detail a closed mill's Schedule 1 GET returns (S06 block).
  closedScheduleBlocked:
    'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
} as const

// Working-context line builders — mirror WorkingContextLines.tsx exactly (the Mill line and
// statusLine formats). Shared by the ScheduleTombstone (schedule headers) and the former banner.
export const NOT_INITIATED = 'Not Initiated'
export const bannerMillLine = (m: MillFixture): string =>
  `Mill: ${m.millNumber} ${m.millName} - Year: ${m.year}`
export const bannerStatusLine = (
  label: 'Sch 1-10' | 'Sch 11',
  status: TrackStatusFixture,
): string => `${label} - Status: ${status.description} - Date: ${status.date ?? NOT_INITIATED}`

// Every status line the banner is expected to show for a fixture (in render order).
export const expectedStatusLines = (m: MillFixture): string[] => {
  const lines: string[] = []
  if (m.sch1To10) lines.push(bannerStatusLine('Sch 1-10', m.sch1To10))
  if (m.sch11) lines.push(bannerStatusLine('Sch 11', m.sch11))
  return lines
}
