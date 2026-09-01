/**
 * UC-SEC-001 (Home — Establish Working Context) pinned test data — DB-grounded, never fabricated.
 *
 * Anchors confirmed 2026-07-30 against the seeded local delivery DB (THE/…@localhost:1525/DBDOCK_01)
 * via the app's own API. Home "Save" is a READ/RESOLVE — GET /api/v1/mill-context — so these anchors
 * are read-only and no scenario mutates anything (no teardown needed; parallel-safe by construction).
 * A re-extract can renumber this data, so re-grounding these values is part of any re-extract — change
 * them HERE only (single source of truth for the sec specs).
 *
 * Finding queries (all GET /api/v1/mill-context?millId=<m>&year=<y>):
 *   12050/2017 -> 200 {millNumber:"987",millName:"TURTLE DOVE",millViewable:true,
 *                      schedules1To10Status:{Draft,2018-05-09}, schedule11Status:{Draft,null}}
 *    9050/2019 -> 200 {"760","WESTEROS",viewable:true, s1-10:{Draft,null}, s11:{Draft,null}}
 *      13/2017 -> 200 {"45","GORMAN BROS. - DOWNIE STREET",millViewable:FALSE (closed),
 *                      s1-10:{Draft,null}, s11:{Draft,null}}   (Save still succeeds — home boundary)
 *   16050/2016 -> 200 {"514","AAA MILLING",viewable:true, s1-10:null, s11:null}  (no report-status row)
 *   13050/2017 -> 200 {"999","ISP TEST", both Draft dated}     (the mount DEFAULT context)
 * Required-field contract (validation is BACKEND-authoritative — components/home/index.tsx sends empty
 * params verbatim, the server returns the 400):
 *   ?millId=&year=2017    -> 400 messages[0].text "Mill: Value is required."
 *   ?millId=13050&year=   -> 400 messages[0].text "Reporting Year: Value is required."
 *   ?millId=&year=        -> 400 BOTH messages
 */

export interface TrackStatusFixture {
  description: string;
  /** Omitted → the banner renders the date as "Not Initiated". */
  date?: string;
}

export interface MillYearFixture {
  millId: number;
  millNumber: string;
  millName: string;
  year: number;
  /** Expected banner status lines from the confirmed DB rows. Omitted → the banner suppresses that line. */
  sch1To10?: TrackStatusFixture;
  sch11?: TrackStatusFixture;
}

// S01 happy path: an OPEN (viewable) mill/year with report-status rows, one of which (Sch 1-10)
// carries a real date — proves the dated status line renders.
export const OPEN_WITH_STATUS: MillYearFixture = {
  millId: 12050,
  millNumber: '987',
  millName: 'TURTLE DOVE',
  year: 2017,
  sch1To10: { description: 'Draft', date: '2018-05-09' },
  sch11: { description: 'Draft' }, // undated → "Not Initiated"
};

// S03: a DIFFERENT open (mill, year) to switch to — both track statuses undated here.
export const OPEN_ALT: MillYearFixture = {
  millId: 9050,
  millNumber: '760',
  millName: 'WESTEROS',
  year: 2019,
  sch1To10: { description: 'Draft' },
  sch11: { description: 'Draft' },
};

// S06: a CLOSED (millViewable:false) mill. Per the UC-SEC-001 screen boundary the Home Save STILL
// succeeds and the banner renders exactly like an open mill; the closed-mill schedule-page BLOCK is a
// separate concern owned by UC-SCH1-001 S20 (mill not active) — not asserted here.
export const CLOSED_MILL: MillYearFixture = {
  millId: 13,
  millNumber: '45',
  millName: 'GORMAN BROS. - DOWNIE STREET',
  year: 2017,
  sch1To10: { description: 'Draft' },
  sch11: { description: 'Draft' },
};

// S07: an open, viewable mill/year with NO ILCRMillReportStatus row — the banner shows the Mill line
// only, both status lines suppressed, no error.
export const NO_STATUS: MillYearFixture = {
  millId: 16050,
  millNumber: '514',
  millName: 'AAA MILLING',
  year: 2016,
  // deliberately no sch1To10/sch11 — that absence IS the scenario
};

// The 13050/2017 pair (context/millYear/millYearDefaults.ts: DEFAULT_MILL_ID / DEFAULT_YEAR). NO LONGER
// the app's mount default: MillYearProvider used to seed it whenever local storage was empty, so Home
// always had a context to reflect and landed pre-selected — commit e37649b dropped that, and the app now
// starts with no context (see the landing scenario). The pair survives as a DB-PINNED ANCHOR: both keys
// are present in /v1/mills and /v1/reporting-years, and preflight/sec-anchors.setup.ts asserts as much,
// so unit suites and preflight can seed a known-good context. Pinned here so a re-ground is one line.
export const DEFAULT_CONTEXT: MillYearFixture = {
  millId: 13050,
  millNumber: '999',
  millName: 'ISP TEST',
  year: 2017,
};

/** Carbon Dropdown option text for a mill — mirrors Home's `millItemToString` (components/home/index.tsx). */
export const millOptionText = (m: MillYearFixture): string => `${m.millNumber} - ${m.millName}`;

/** Scenario-facing keys → the DB-pinned fixtures (single source of truth, shared across sec step files). */
export const BY_KEY: Record<string, MillYearFixture> = {
  'open with status': OPEN_WITH_STATUS,
  'open alternate': OPEN_ALT,
  closed: CLOSED_MILL,
  'no status': NO_STATUS,
  default: DEFAULT_CONTEXT,
};

export function fixtureByKey(key: string): MillYearFixture {
  const f = BY_KEY[key];
  if (!f) {
    throw new Error(`Unknown sec working-context fixture "${key}". Known: ${Object.keys(BY_KEY).join(', ')}.`);
  }
  return f;
}

// Verbatim contract strings (AD-8 — API-owned, asserted exactly; never re-typed by the app).
export const MSG = {
  saved: 'Data saved successfully', // SUC-001 (200 message.text)
  millRequired: 'Mill: Value is required.', // FLD-001 (400 messages) — S04/S08
  yearRequired: 'Reporting Year: Value is required.', // FLD-002 (400 messages) — S05/S08
} as const;

// Carbon Dropdown placeholders (label props in components/home/index.tsx) — used to assert the
// no-context landing state, where Home asks the user to choose.
export const PLACEHOLDER = {
  mill: 'Select Mill',
  year: 'Select Reporting Year',
} as const;

// Banner line builders — mirror ContextBanner.tsx EXACTLY (Layout/ContextBanner.tsx):
//   Mill line:   `Mill: ${millNumber} ${millName} - Year: ${reportYear}`
//   Status line: `${label} - Status: ${description||code} - Date: ${date || 'Not Initiated'}`
export const NOT_INITIATED = 'Not Initiated';
export const bannerMillLine = (m: MillYearFixture): string =>
  `Mill: ${m.millNumber} ${m.millName} - Year: ${m.year}`;
export const bannerStatusLine = (
  label: 'Sch 1-10' | 'Sch 11',
  status: TrackStatusFixture,
): string => `${label} - Status: ${status.description} - Date: ${status.date ?? NOT_INITIATED}`;

/** Every status line the banner is expected to show for a fixture, in render order (Sch 1-10 then 11). */
export const expectedStatusLines = (m: MillYearFixture): string[] => {
  const lines: string[] = [];
  if (m.sch1To10) lines.push(bannerStatusLine('Sch 1-10', m.sch1To10));
  if (m.sch11) lines.push(bannerStatusLine('Sch 11', m.sch11));
  return lines;
};
