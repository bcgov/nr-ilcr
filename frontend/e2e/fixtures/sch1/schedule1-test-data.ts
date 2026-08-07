/**
 * Schedule 1 (UC-SCH1-001) pinned test data — DB-grounded, never fabricated.
 *
 * Anchors confirmed 2026-07-29 against the seeded local delivery DB (THE/…@localhost:1525/DBDOCK_01)
 * via the app's own API:
 *   GET /api/v1/schedule1?millId=<m>&year=<y>
 * A re-extract can renumber this data, so re-grounding these values is part of any re-extract —
 * change them HERE only (single source of truth for the sch1 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

/**
 * The MUTATING happy-path target — an EMPTY, editable Draft (all line-item/silviculture values null).
 * The S01 save test writes here and the cleanup fixture restores it to empty afterwards, so nothing
 * else may lean on this combo. Found: GET .../schedule1?millId=13050&year=2017 ->
 * {trackStatus:"D", editable:true, lineItems all value-less, otherCosts.count:0} (2026-07-29).
 */
export const MUTABLE_DRAFT: ScheduleKey = { millId: 13050, year: 2017 };

/** Mill 13050 dropdown fields (GET /api/v1/mills) — option text is `${millNumber} - ${millName}`. */
export const MUTABLE_DRAFT_MILL = { millNumber: '999', millName: 'ISP TEST' } as const;

/**
 * The READ-ONLY preflight anchor — a stable, populated editable Draft that no scenario mutates, so
 * preflight can prove the seed is present independently of the mutable target. Found:
 * GET .../schedule1?millId=24050&year=2017 -> {trackStatus:"D", editable:true} (2026-07-29).
 */
export const READONLY_ANCHOR: ScheduleKey = { millId: 24050, year: 2017 };

/** Mill 24050 dropdown fields (GET /api/v1/mills, 2026-07-29) — option text `${millNumber} - ${millName}`. */
export const READONLY_ANCHOR_MILL = { millNumber: '7777', millName: 'CGT TEST MILL7' } as const;

/** Carbon Dropdown option text for a mill (mirrors Home's `millItemToString`). */
export const millOptionText = (m: { millNumber: string; millName: string }): string =>
  `${m.millNumber} - ${m.millName}`;

/** The Schedule 1 aggregate endpoint for a (mill, year) — GET read-back / PUT save / DELETE. */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule1?millId=${millId}&year=${year}`;

/**
 * Render-state / context-guard anchors (S20–S22), discovered 2026-07-30. Each is a REAL (mill, year)
 * whose Schedule 1 GET produces the guard state under test — held read-only (the Home Save is a
 * resolve GET that writes no report data, so no cleanup and parallel-safe). `expectHttp` is the
 * schedule1 GET status the state depends on; the precondition step asserts it so a data drift fails a
 * re-ground rather than a confusing UI timeout. (S19 "no context selected" needs no anchor — it is
 * driven by seeding an empty MillYearContext into localStorage; see the render-states steps.)
 */
export const RENDER_STATE_ANCHORS: Record<
  string,
  { key: ScheduleKey; mill: { millNumber: string; millName: string }; expectHttp: number }
> = {
  // S20 — mill 13 is CLS (closed) for 2017 → schedule1 GET 409 (MillClosedException); the page shows
  // the 409 detail and suppresses the form.
  'closed-mill': {
    key: { millId: 13, year: 2017 },
    mill: { millNumber: '45', millName: 'GORMAN BROS. - DOWNIE STREET' },
    expectHttp: 409,
  },
  // S21 — mill 16050 is active for 2016 but has no Schedule 1 summary → GET 404 "Schedule not found.",
  // which the frontend maps to "No Schedule 1 exists for Mill 16050 in Reporting Year 2016…".
  'no-schedule': {
    key: { millId: 16050, year: 2016 },
    mill: { millNumber: '514', millName: 'AAA MILLING' },
    expectHttp: 404,
  },
  // S22 — mill 12050 / 2016 track is Submitted ("S") → GET 200 but editable:false; the page renders
  // read-only (no input fields; Save / Check Status / Delete disabled).
  submitted: {
    key: { millId: 12050, year: 2016 },
    mill: { millNumber: '987', millName: 'TURTLE DOVE' },
    expectHttp: 200,
  },
};

/** The in-memory MillYearContext localStorage key (context/millYear/MillYearProvider.tsx). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

/**
 * Check Status anchors that need an itemized Other-Costs row seeded first (S17/S18). The row is seeded
 * at the DB (the app's own add is broken — Bug/Regression #1) and cleaned up via the app's DELETE.
 * `cost: null` seeds an empty-cost row (S18 warning); a number seeds a costed row (S17). Both anchors
 * carry a shared Other-Costs volume of exactly 0 (discovered 2026-07-30), which is what makes S17's
 * "cost > 0 with volume 0" mismatch fire.
 */
export const CHECK_STATUS_SEED_ANCHORS = {
  // S17 — costed row + shared volume 0 → "…: Volume: must be greater than 0 when Cost is greater than 0".
  'other-costs-cost-without-volume': {
    key: { millId: 17052, year: 2017 } as ScheduleKey,
    mill: { millNumber: '727', millName: 'Updated Mill E2E' },
    marker: 'E2E S17 seed',
    cost: 5000 as number | null,
  },
  // S18 — empty-cost row → the non-blocking empty-cost warning.
  'empty-cost-row': {
    key: { millId: 25054, year: 2016 } as ScheduleKey,
    mill: { millNumber: '9175', millName: 'TCASEY-TEST' },
    marker: 'E2E S18 seed',
    cost: null as number | null,
  },
} as const;

/**
 * SUC-002, API-owned (AD-8): the whole-schedule delete confirmation (bundle key
 * `dataDeletedSuccesfullyInfoMsg`). Confirmed live 2026-07-30 (DELETE /api/v1/schedule1).
 */
export const MSG_DELETED = 'Data deleted successfully';

/** Client-side confirm-Modal chrome for delete (components/schedule1/index.tsx, verbatim). */
export const CONFIRM_DELETE_HEADING = 'Delete schedule';
export const CONFIRM_DELETE_BODY = 'This will delete the current record. Do you want to continue?';

/**
 * S13 delete target — a dedicated, populated, editable Draft that ONLY the delete scenario touches.
 * Delete is destructive (removes the summary + every detail row, no create-on-open), so this schedule
 * is snapshotted to E2E_BAK_SCH1_* and re-inserted verbatim on teardown by scripts/sch1_db_restore.py
 * (round-trip proven byte-identical 2026-07-30). Never share this (mill,year) with another scenario.
 * Found: GET .../schedule1?millId=25052&year=2016 → {trackStatus:"D", editable:true, 9 line items}.
 */
export const DELETE_ANCHOR: ScheduleKey = { millId: 25052, year: 2016 };

/** Mill 25052 dropdown fields (GET /api/v1/mills, 2026-07-30). */
export const DELETE_ANCHOR_MILL = { millNumber: '9173', millName: 'MRICE-TEST' } as const;

/** Subtotal Other Costs sub-page path (components/schedule1OtherCosts + Schedule1OtherCostsApi). */
export const OTHER_COSTS_API = '/api/v1/schedule1/other-costs';

/**
 * S24 retry-save target — a dedicated, populated, editable Draft. The successful retry WRITES here, so
 * the scenario snapshots it to E2E_BAK_SCH1_* first and the exact rows are restored on teardown
 * (scripts/sch1_db_restore.py restore — delete-then-reinsert). Never shared with another scenario.
 * Found: GET .../schedule1?millId=24050&year=2016 → {trackStatus:"D", editable:true, 9 line items}.
 */
export const RETRY_ANCHOR: ScheduleKey = { millId: 24050, year: 2016 };

/** Mill 24050 dropdown fields (GET /api/v1/mills, 2026-07-30). */
export const RETRY_ANCHOR_MILL = { millNumber: '7777', millName: 'CGT TEST MILL7' } as const;

/**
 * Other Costs sub-page anchors (S09–S12), discovered 2026-07-30. Each mutating scenario OWNS a
 * dedicated editable Draft (distinct (mill,year)) so the suite stays parallel-safe; the validate-only
 * scenarios (S10/S11) reject client-side and never write, so they share a read-only editable Draft.
 * `marker` is the unique row description a scenario adds — the cleanup fixture finds and deletes any
 * rows carrying it (≤ 30 chars, the description cap).
 */
export const OTHER_COSTS_ANCHORS = {
  // S09 — add a row here, then delete it on teardown (starts oc_count 3).
  add: {
    key: { millId: 25050, year: 2017 } as ScheduleKey,
    mill: { millNumber: '9171', millName: 'BCOVEY-TEST' },
    marker: 'E2E S09 add',
  },
  // S12 — a row is added via the API in the precondition, then removed through the UI (starts oc_count 0).
  remove: {
    key: { millId: 9050, year: 2017 } as ScheduleKey,
    mill: { millNumber: '760', millName: 'WESTEROS' },
    marker: 'E2E S12 remove',
  },
  // S10/S11 — client-side reject only; no write, so this editable Draft is never mutated.
  validate: {
    key: { millId: 17052, year: 2016 } as ScheduleKey,
    mill: { millNumber: '727', millName: 'Updated Mill E2E' },
    marker: '',
  },
} as const;

/**
 * SUC-001, API-owned (AD-8): the exact success text the backend returns in `message.text` and the UI
 * echoes verbatim. Asserted exactly; never re-typed by the app. Confirmed live (key
 * `dataSavedSuccesfullyInfoMsg`).
 */
export const MSG_SAVED = 'Data saved successfully';

/**
 * SUC-003, API-owned (AD-8): the Check Status "all requirements met" text (bundle key
 * `scheduleRequirementsMetMsg`). Rendered as the success NotificationColumn subtitle
 * (title "Requirements met"). Confirmed live 2026-07-30 (POST /api/v1/schedule1/check-status).
 */
export const MSG_CHECK_STATUS_MET = 'All requirements for this schedule have been met';

/**
 * Read-only Check Status anchors (S14–S16), discovered 2026-07-30 against the seeded delivery DB via
 * `POST /api/v1/schedule1/check-status?millId=&year=`. Each is a stable, editable Draft that NO
 * scenario mutates — Check Status is a POST that changes nothing, so these need no restore/cleanup and
 * stay parallel-safe. Mill option text = `${millNumber} - ${millName}` (GET /api/v1/mills). The exact
 * check-status signature each yields is noted so a re-extract that shifts the data fails a re-ground,
 * not a silent mis-assertion.
 *
 * S17 (other-costs cost>0 with shared volume 0) and S18 (empty-cost-row warning) require an itemized
 * Other-Costs row, which is created through the Other Costs sub-page write path — they are authored
 * with the Other Costs cluster (S09–S12), tracked as `deferred` rows in coverage.md until then.
 */
export const CHECK_STATUS_ANCHORS: Record<
  string,
  { key: ScheduleKey; mill: { millNumber: string; millName: string } }
> = {
  // S14 — every mandatory field present + Other Costs consistent → requirementsMet:true, no errors.
  'requirements-met': {
    key: { millId: 24050, year: 2017 },
    mill: { millNumber: '7777', millName: 'CGT TEST MILL7' },
  },
  // S15 (missing line-item value) — all fields null → errors include
  // "Standing Tree to Loaded Truck - Volume: Value Required".
  'missing-line-item-volume': {
    key: { millId: 24051, year: 2016 },
    mill: { millNumber: '8888', millName: 'CGI TEST MILL8' },
  },
  // S15 (missing Other Costs volume) — line items present but shared Other-Costs volume null → the
  // single error "Subtotal Other Costs (0) - Volume: Value Required". (Mill 13050 year 2016 — a
  // DIFFERENT (mill,year) key than the mutable happy-path pair 13050/2017, so no parallel collision.)
  'missing-other-costs-volume': {
    key: { millId: 13050, year: 2016 },
    mill: { millNumber: '999', millName: 'ISP TEST' },
  },
  // S16 — shared Other-Costs volume > 0 (10) but no itemized rows (cost subtotal 0) → error
  // "Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater than 0".
  'other-costs-volume-without-cost': {
    key: { millId: 22050, year: 2016 },
    mill: { millNumber: '20171', millName: 'MILES MILLING' },
  },
};

/**
 * Build a Schedule 1 PUT body that blanks every writable field — used to restore a mutated (mill,year)
 * Draft to its empty baseline on teardown. Mirrors the request contract in
 * backend .../schedule1/dto/Schedule1Request.java (writable line-item codes 12–18; silviculture
 * actualSpent(1)/accruedLessActual(2); the 8-digit volumes). `revisionCount` is the optimistic-lock
 * token from a fresh GET.
 */
export function emptyScheduleRequest(revisionCount: number): Record<string, unknown> {
  return {
    revisionCount,
    comments: null,
    lineItems: [12, 13, 14, 15, 16, 17, 18].map((costItemCode) => ({
      costItemCode,
      volume: null,
      cost: null,
    })),
    silviculture: {
      actualSpent: { volume: null, cost: null },
      accruedLessActual: { volume: null, cost: null },
    },
    otherCostsVolume: null,
    forestMgmtAdminVolume: null,
    subtotalCompanyLoggingVolume: null,
  };
}
