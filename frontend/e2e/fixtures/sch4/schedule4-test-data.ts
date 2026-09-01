/**
 * UC-SCH4-001 (Schedule 4 — Report Special Log Transportation Costs) pinned test data.
 * DB-grounded through the app's own API, never fabricated.
 *
 * ---------------------------------------------------------------------------------------------------
 * ANCHOR DISCOVERY (2026-08-17, seeded local delivery DB THE/…@localhost:1525/DBDOCK_01)
 * ---------------------------------------------------------------------------------------------------
 * `GET /api/v1/mills` (21 mills) × reporting years 2010–2026 = 357 probes of
 * `GET /api/v1/schedule4?millId=<m>&year=<y>`, each classified by HTTP status / trackStatus / editable /
 * location count. Result:
 *   -  86 editable Draft rows with NO locations              → the mutating anchors below
 *   -  13 editable Draft rows that already hold locations    → deliberately NOT used (see PARALLEL SAFETY)
 *   -   9 non-Draft rows (Submitted "S" / Verified "V") with locations → the read-only anchors
 *   - 239 pairs with no report-status row                    → HTTP 404 "Schedule not found."
 *   -   7 closed-mill pairs                                  → HTTP 409 mill-not-active
 *
 * THE EXTRACT CARRIES NO SCHEDULE 4 AMOUNTS. All 68 visible locations across the whole DB have
 * `categories: []` and `subPageRows: []`; confirmed at the DB (`ILCR_COST_REPORT_DETAIL` holds 1316 rows,
 * NONE with a Schedule 4 cost item 40–55, against 289 category-"4" `TRANSPORTATION_REPORT` rows). This is
 * a DATA gap, not an app defect — the read model is correct, there is simply nothing to read. Two
 * consequences, and they shape this whole fixture:
 *   1. every Draft scenario SEEDS ITS OWN state through the app's own PUT/POST and deletes it again (no
 *      patch, no SQL) — which is also why the anchors below are pinned as EMPTY at rest; and
 *   2. the read-only (S18 / STA-001) arm cannot do that — the Draft gate answers 409
 *      `scheduleNotEditableErrorMsg` to any write on a Submitted/Verified mill-year — so the two
 *      READ_ONLY_ANCHORS get their amounts from the one seed patch,
 *      `real-test-data-patches/sch4/view-mode-amounts.sql` (that file documents why).
 *
 * PARALLEL SAFETY: the suite runs `fullyParallel`, and a Schedule 4 save creates real category-"4"
 * `TRANSPORTATION_REPORT` rows, so every MUTATING scenario owns a DEDICATED (mill, year) that no other
 * scenario writes to — including each row of a `Scenario Outline`, which runs as its own test (the
 * sub-page validation outline therefore carries an `anchor` column). Anchors that already hold seeded
 * locations are avoided on purpose: a scenario asserting "the list shows exactly what I created" would
 * otherwise depend on pre-existing rows, and cleanup would have to tell its own rows from the extract's.
 *
 * CROSS-DOMAIN: none of the (mill, year) pairs below is pinned by the sch1, sch2, sch11 or sec fixtures
 * (checked pair-by-pair, not mill-by-mill — those suites pin specific years). Schedule 4 writes only
 * category-"4" transportation rows and no other schedule derives a figure from them (Schedule 2's carried
 * figures come from Schedules 1/3), so the isolation requirement here is ordinary parallel hygiene rather
 * than the hard cross-schedule coupling Schedule 2 has.
 *
 * CLEANUP CONTRACT: a location is a FAMILY of reports (primary + one child per distance category + one per
 * sub-page row) and `DELETE /api/v1/schedule4/locations?id=<primaryId>` removes the whole family incl.
 * cascaded details (confirmed by probe 2026-08-17: PUT → 2 categories + 3 sub-page rows, DELETE → 200,
 * re-GET → 0 locations, byte-identical to the pre-probe document). So cleanup deletes every location on
 * the scenario's anchor and PROVES the anchor is back to empty.
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch4-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch4 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

export interface Sch4Anchor {
  key: ScheduleKey;
  mill: MillRef;
}

/** An anchor plus what it is for — the `purpose` is what a preflight failure message quotes. */
export interface Sch4AnchorSpec extends Sch4Anchor {
  purpose: string;
}

// Every mill below is ACT unless marked; ids are the API's `millId`, numbers/names its option text.
const MILL_514: MillRef = { millNumber: '514', millName: 'AAA MILLING' }; // millId 16050
const MILL_727: MillRef = { millNumber: '727', millName: 'Updated Mill E2E' }; // millId 17052
const MILL_760: MillRef = { millNumber: '760', millName: 'WESTEROS' }; // millId 9050
const MILL_987: MillRef = { millNumber: '987', millName: 'TURTLE DOVE' }; // millId 12050
const MILL_999: MillRef = { millNumber: '999', millName: 'ISP TEST' }; // millId 13050
const MILL_7777: MillRef = { millNumber: '7777', millName: 'CGT TEST MILL7' }; // millId 24050
const MILL_9171: MillRef = { millNumber: '9171', millName: 'BCOVEY-TEST' }; // millId 25050
const MILL_9172: MillRef = { millNumber: '9172', millName: 'MDOBIE-TEST' }; // millId 25051, CLS
const MILL_9173: MillRef = { millNumber: '9173', millName: 'MRICE-TEST' }; // millId 25052
const MILL_9175: MillRef = { millNumber: '9175', millName: 'TCASEY-TEST' }; // millId 25054
// millId 25053 belongs to sch2, which pins years 2016-2020 there; 2021 is free, and Schedule 4 writes only
// category-"4" transportation rows, which no other schedule derives a figure from. Pair-by-pair isolation
// holds — see the CROSS-DOMAIN note above.
const MILL_9174: MillRef = { millNumber: '9174', millName: 'AOLSON-TEST' }; // millId 25053
// millId 23051 also belongs to sch2 (which pins other years there); 2021 is free. Same pair-by-pair rule.
const MILL_20174: MillRef = { millNumber: '20174', millName: 'AO CUSTOM' }; // millId 23051
const MILL_20171: MillRef = { millNumber: '20171', millName: 'MILES MILLING' }; // millId 22050
const MILL_20172: MillRef = { millNumber: '20172', millName: 'COVEY CUSTOM CUT' }; // millId 22051
const MILL_20173: MillRef = { millNumber: '20173', millName: 'TOMTESTMILL042017' }; // millId 23050

const at = (mill: MillRef, millId: number, year: number, purpose: string): Sch4AnchorSpec => ({
  key: { millId, year },
  mill,
  purpose,
});

/**
 * ---------------------------------------------------------------------------------------------------
 * THE ANCHOR TABLE — one row per scenario that needs its own (mill, year).
 * ---------------------------------------------------------------------------------------------------
 * A `.feature` names an anchor by its KEY here ( `Given the Schedule 4 anchor "happy-path" …` ), so this
 * table is the single place a (mill, year) is chosen, documented and re-grounded. Every entry was
 * confirmed 2026-08-17 as: ACT mill, Schedules 1-10 track "D", `editable: true`, and NO Schedule 4
 * locations at rest — all four re-asserted by `preflight/sch4-anchors.setup.ts` before a browser opens,
 * because the "the list shows only what I created" and "delete returns the list to empty" assertions
 * depend on the empty-at-rest state.
 *
 * A table rather than 48 individual exports on purpose: at this count individual consts stop being
 * readable, and the preflight/cleanup guards need to iterate them anyway.
 */
export const ANCHORS: Record<string, Sch4AnchorSpec> = {
  // --- the core create/edit journeys -----------------------------------------------------------------
  'happy-path': at(MILL_514, 16050, 2017, 'S01 — the happy path: fixed + distance category, then Check Status'),
  'name-only': at(MILL_514, 16050, 2019, 'S08 — save a location with only its name'),
  'name-boundary': at(MILL_514, 16050, 2020, 'S09 — a 30-character name is accepted and stored in full'),
  edit: at(MILL_514, 16050, 2021, 'S02 — change a saved category cost and re-save'),
  'twice-saved': at(MILL_9173, 25052, 2018, 'S02 — two consecutive saves on one open panel (lock token refresh)'),
  rename: at(MILL_9173, 25052, 2019, 'S02 — renaming a location keeps its distance-category children'),
  'clear-category': at(MILL_9173, 25052, 2020, 'S02 — clearing a distance category deletes its child report'),
  persistence: at(MILL_727, 17052, 2021, 'a saved location survives a full page reload'),

  // --- copy (AF3 / BR-09 / WRN-001) ------------------------------------------------------------------
  copy: at(MILL_727, 17052, 2018, 'S07 — copy a location, rename it, save it as a second location'),
  'copy-duplicate': at(MILL_9173, 25052, 2021, 'S07/S13 — saving a copy without naming it is refused'),
  'copy-comments': at(MILL_20172, 22051, 2018, 'S07 — a copy carries the source comments'),

  // --- delete (BR-08 / NAV-004) ----------------------------------------------------------------------
  delete: at(MILL_727, 17052, 2019, 'S10 — delete a location with categories AND a sub-page row'),
  'cancel-delete': at(MILL_727, 17052, 2020, 'S10 — cancelling the confirm leaves the location untouched'),
  'delete-one': at(MILL_20172, 22051, 2019, 'S10 — deleting one location leaves the others in place'),

  // --- name validation ------------------------------------------------------------------------------
  'duplicate-name': at(MILL_760, 9050, 2018, 'S14 — a case-insensitive duplicate is refused, then corrected'),
  'self-rename': at(MILL_20172, 22051, 2016, 'S14 — a no-op save and a case-only self-rename are allowed'),
  'validation-recovery': at(MILL_760, 9050, 2019, 'S13/S19 — correcting the highlighted fields lets the save through'),

  // --- the sub-pages (AF2) --------------------------------------------------------------------------
  'towing-from-new': at(MILL_760, 9050, 2020, 'S03 — NAV-003 save-first from an unsaved new location'),
  'towing-from-saved': at(MILL_760, 9050, 2021, 'S04 — NAV-002 from a saved location (edits discarded)'),
  'truck-rehaul': at(MILL_987, 12050, 2018, 'S05 — a Truck Rehaul row carries its Cycle'),
  'other-transportation': at(MILL_987, 12050, 2019, 'S06 — an Other Transportation row (no Cycle field)'),
  'nav-cancel': at(MILL_9175, 25054, 2017, 'S04 — cancelling NAV-002 stays on the panel with the edit'),

  // --- sub-page rows (S11 + the in-place edit spec gap + app-only sort) ------------------------------
  'delete-row': at(MILL_987, 12050, 2020, 'S11 — delete a row, totals recompute'),
  'delete-last-row': at(MILL_20172, 22051, 2020, 'S11 — deleting the last row empties the table'),
  'row-edit': at(MILL_987, 12050, 2021, 'in-place row edit then Save (Spec gap SG-1)'),
  'row-edit-reject': at(MILL_20172, 22051, 2021, 'an out-of-range in-place edit blocks the sub-page save'),
  'row-totals': at(MILL_9175, 25054, 2018, 'running totals across several rows, Cycle included'),
  'row-sort': at(MILL_999, 13050, 2018, 'the app-only three-state column sort'),

  // --- sub-page add-row validation (S24-S27; one anchor PER outline example) -------------------------
  'subpage-validation-volume': at(MILL_9175, 25054, 2019, 'S24 — row volume above the size-6 band'),
  'subpage-validation-cost': at(MILL_9175, 25054, 2020, 'S25 — row cost above the size-7 band'),
  'subpage-validation-cycle': at(MILL_9175, 25054, 2021, 'S26 — Truck Rehaul cycle above its band'),
  'subpage-validation-desc': at(MILL_999, 13050, 2019, 'S27 — a row with a blank Description'),
  'subpage-recovery': at(MILL_999, 13050, 2020, 'S24/S27 — correcting the rejected row adds it'),
  'subpage-band': at(MILL_20173, 23050, 2017, 'S24 — a volume the grid accepts is refused on a row'),
  'subpage-bounds': at(MILL_20173, 23050, 2018, 'S24-S26 — row bounds are inclusive at both ends'),

  // --- Check Status (BR-07 / EF3) -------------------------------------------------------------------
  'check-missing-cost': at(MILL_999, 13050, 2021, 'S28 — a missing category Cost is flagged, then passes'),
  'check-zero-cost': at(MILL_20173, 23050, 2019, 'S28 — a stored Cost of zero counts as present'),
  'check-mixed': at(MILL_7777, 24050, 2018, 'S31 — one complete and one incomplete location'),
  'check-row-cost': at(MILL_20171, 22050, 2018, 'S28 — a sub-page row with no Cost fails its location'),
  'check-distance': at(MILL_7777, 24050, 2019, 'S29 (re-grounded) — Distance is not enforced'),
  'check-comments': at(MILL_7777, 24050, 2020, 'S30 (re-grounded) — Comments are a soft gate'),
  'check-issue-label': at(MILL_7777, 24050, 2021, 'Divergence #2 — the issue does not name the category'),
  // BR-12 / #359 — Check Status must judge the OPEN PANEL, not the last saved location.
  //
  // SEEDED, not discovered: this mill-year has no report-status row in the extract, so
  // `real-test-data-patches/sch4/unsaved-check-anchors.sql` adds one (Draft). That patch's header records
  // why nothing else was available — 114 (mill, year) keys are already pinned across the six fixtures,
  // Home only offers reporting years 2015-2021, and every unclaimed openable pair in that range is
  // non-Draft, which disables Check Status. A first attempt reused 12050/2015 and preflight caught it:
  // that pair is `nav-subpage-back`, declared across four lines, which a line-based search misses.
  'check-unsaved': at(MILL_760, 9050, 2015, 'S33/S34 — Check Status vs an unsaved panel edit (#359)'),

  // --- the unsaved-change / recompute divergences ---------------------------------------------------
  'nav-dirty-panel': at(MILL_9171, 25050, 2016, 'S12 / Divergence #3 — closing a dirty panel must warn'),
  'nav-dirty-switch': at(MILL_20171, 22050, 2020, 'S12 / Divergence #3 — opening a new location over a dirty panel'),
  'nav-subpage-back': at(
    MILL_987,
    12050,
    2015,
    'S12 / DIV-3 — Back on a sub-page must warn before discarding typed row input',
  ),
  'discard-safe': at(MILL_20173, 23050, 2020, 'S12 — a discarded panel edit is never written (green)'),
  'per-unit-after-save': at(MILL_20171, 22050, 2019, 'Divergence #4 — $/m³ on the panel that saved it'),
  'per-unit-reopen': at(MILL_20173, 23050, 2021, 'reopening shows the recomputed $/m³ (green)'),
  'stale-edit': at(
    MILL_9174,
    25053,
    2021,
    'the optimistic-lock conflict — a save carrying a token another session already moved',
  ),
  'clear-fixed': at(
    MILL_20174,
    23051,
    2021,
    'BUG-4 — clearing a FIXED category to fully-empty must persist (partial clears already do)',
  ),

  // --- accessibility --------------------------------------------------------------------------------
  a11y: at(MILL_9171, 25050, 2018, 'the editable location-list axe sweep'),
  'a11y-subpage': at(MILL_9171, 25050, 2020, 'the editable sub-page axe sweep'),
  'a11y-panel': at(MILL_9171, 25050, 2021, 'Bug #1 — the open Edit panel axe sweep (editing-row contrast)'),
  'a11y-hover': at(MILL_20171, 22050, 2021, 'Bug #2 — the deliberate hover-state axe sweep'),

  // --- NON-MUTATING (see NON_MUTATING_ANCHOR_NAMES) --------------------------------------------------
  /**
   * S13 / S19–S23 and every other client-side rejection. Save never leaves the browser, so nothing is
   * persisted here; the scenarios prove that with the mutation spy AND an API read-back showing the
   * anchor still holds no locations. Shared by several scenarios ON PURPOSE — it is the "key nothing
   * creates on" the parallel-safety rule calls for.
   */
  validation: at(MILL_9171, 25050, 2019, 'validate-only: client-blocked rejections, never written to'),
};

/** Anchors nothing may ever write to — excluded from the cleanup registry and its allow-list. */
export const NON_MUTATING_ANCHOR_NAMES = ['validation'] as const;

/** Every anchor this suite is allowed to write to — the cleanup guard's allow-list. */
export const MUTATING_ANCHORS: { name: string; anchor: Sch4AnchorSpec }[] = Object.entries(ANCHORS)
  .filter(([name]) => !(NON_MUTATING_ANCHOR_NAMES as readonly string[]).includes(name))
  .map(([name, anchor]) => ({ name, anchor }));

/** The Draft anchors that must be empty at rest but are never written to. */
export const READ_ONLY_DRAFT_ANCHORS: { name: string; anchor: Sch4AnchorSpec }[] = Object.entries(
  ANCHORS,
)
  .filter(([name]) => (NON_MUTATING_ANCHOR_NAMES as readonly string[]).includes(name))
  .map(([name, anchor]) => ({ name, anchor }));

/** Resolve a `.feature`'s anchor name, failing loud on a typo. */
export const namedAnchor = (name: string): Sch4AnchorSpec => {
  const anchor = ANCHORS[name];
  if (!anchor) {
    throw new Error(
      `unknown Schedule 4 anchor "${name}". Add it to ANCHORS in fixtures/sch4/schedule4-test-data.ts. Known: ${Object.keys(ANCHORS).join(', ')}.`,
    );
  }
  return anchor;
};

/** True when `key` is one of the anchors this suite owns — the cleanup guard (see fixtures/sch4.ts). */
export const isMutatingAnchor = (key: ScheduleKey): boolean =>
  MUTATING_ANCHORS.some(
    ({ anchor }) => anchor.key.millId === key.millId && anchor.key.year === key.year,
  );

/**
 * S18 / STA-001 — the schedule is read-only outside Draft. BOTH non-Draft codes are pinned so the
 * read-only render is proven from both sides of the mirror rather than one (symmetry).
 *
 * `location` + `stored` describe the location the seed patch adds
 * (`real-test-data-patches/sch4/view-mode-amounts.sql`) — the ONLY non-Draft location in the DB with
 * amounts to render. `preflight/sch4-anchors.setup.ts` fails fast telling you to run
 * `./scripts/apply-patches.sh` if it is missing, and re-checks these figures so a drifted patch can never
 * surface as an opaque mid-scenario table mismatch.
 *
 * `otherLocationName` is a REAL extract location on the same anchor, used to prove the list still renders
 * the pre-existing rows (and their View action) alongside the patched one.
 */
export const READ_ONLY_ANCHORS: Record<
  'submitted' | 'verified',
  Sch4Anchor & {
    trackStatus: string;
    location: string;
    otherLocationName: string;
    stored: {
      /** Category 40 Lakeside Dry Dump (FIXED). */
      fixedVolume: number;
      fixedCost: number;
      /** Category 47 Truck Barge/Ferry (DISTANCE). */
      distanceKm: number;
      distanceVolume: number;
      distanceCost: number;
      /** The single Towing Total (43) sub-page row. */
      rowDescription: string;
      rowDistance: number;
      rowVolume: number;
      rowCost: number;
    };
    comments: string;
  }
> = {
  submitted: {
    key: { millId: 22050, year: 2015 },
    mill: MILL_20171,
    trackStatus: 'S',
    location: 'E2E View Location',
    otherLocationName: 'test 2',
    stored: {
      fixedVolume: 1200,
      fixedCost: 3600,
      distanceKm: 50,
      distanceVolume: 800,
      distanceCost: 4000,
      rowDescription: 'Camp haul',
      rowDistance: 12.5,
      rowVolume: 500,
      rowCost: 1500,
    },
    comments: 'Read-only sample comments (E2E_SEED).',
  },
  verified: {
    key: { millId: 23050, year: 2015 },
    mill: MILL_20173,
    trackStatus: 'V',
    location: 'E2E View Location',
    otherLocationName: 'loc 1',
    stored: {
      fixedVolume: 1200,
      fixedCost: 3600,
      distanceKm: 50,
      distanceVolume: 800,
      distanceCost: 4000,
      rowDescription: 'Camp haul',
      rowDistance: 12.5,
      rowVolume: 500,
      rowCost: 1500,
    },
    comments: 'Read-only sample comments (E2E_SEED).',
  },
};

/**
 * Context-guard anchors — the document GET itself fails, so the page renders a block message instead of a
 * list. Both confirmed 2026-08-17. Mill 9172 (MDOBIE-TEST) is CLS, and the Home mill dropdown lists closed
 * mills, so the guard is reachable from the UI exactly as a reporter would hit it.
 */
export const GUARD_ANCHORS: Record<string, Sch4Anchor & { expectHttp: number; detail: string }> = {
  // S16 / EF2-002 — mill 9172 is closed for 2015 → GET 409, millNotActiveForCurrentYearMsg verbatim.
  'closed-mill': {
    key: { millId: 25051, year: 2015 },
    mill: MILL_9172,
    expectHttp: 409,
    detail:
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  },
  // S17 / EF2-003 — mill 9172 has NO ILCR_MILL_REPORT_STATUS row for 2018 → GET 404. The 404 guard runs
  // BEFORE the closed-mill check, which is why a closed mill can still produce this branch.
  'not-found': {
    key: { millId: 25051, year: 2018 },
    mill: MILL_9172,
    expectHttp: 404,
    detail: 'Schedule not found.',
  },
};

// ---------------------------------------------------------------------------------------------------
// URLs + helpers
// ---------------------------------------------------------------------------------------------------

/** The Schedule 4 aggregate document for a (mill, year) — GET only (locations are their own resource). */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule4?millId=${millId}&year=${year}`;

/** The location collection — PUT saves (create or edit); DELETE takes `&id=`. */
export const locationsUrl = (millId: number, year: number): string =>
  `/api/v1/schedule4/locations?millId=${millId}&year=${year}`;

/** One location's delete URL (targets the primary report id — the whole family goes). */
export const locationDeleteUrl = (millId: number, year: number, id: number): string =>
  `${locationsUrl(millId, year)}&id=${id}`;

/** A location's sub-page rows — POST adds one row. */
export const rowsUrl = (millId: number, year: number, locationId: number): string =>
  `/api/v1/schedule4/locations/${locationId}/rows?millId=${millId}&year=${year}`;

/** One sub-page row — PUT edits it, DELETE removes it. */
export const rowUrl = (millId: number, year: number, locationId: number, rowId: number): string =>
  `/api/v1/schedule4/locations/${locationId}/rows/${rowId}?millId=${millId}&year=${year}`;

/** The read-only Check Status evaluation endpoint (POST, no body — mutates nothing). */
export const checkStatusUrl = (millId: number, year: number): string =>
  `/api/v1/schedule4/check-status?millId=${millId}&year=${year}`;

/** The Home Mill-dropdown option text for a mill ("514 - AAA MILLING"). */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

/** The in-memory MillYearContext localStorage key (context/millYear/MillYearProvider.tsx). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

// ---------------------------------------------------------------------------------------------------
// The transportation category grid (components/schedule4/validation.ts ALL_CATEGORIES + subPageDefs).
//
// Codes ARE the legacy `ILCR_REPORT_COST_ITEM_ID`s, and the grid renders every line in legacy code order
// (40-55) with the 3 list sub-pages interleaved at 43 / 46 / 55. Code 54 is dead (registry-only, never in
// detail data) and is deliberately absent.
// ---------------------------------------------------------------------------------------------------

/** The 9 fixed (no-distance) categories, in legacy order. */
export const FIXED_CATEGORIES = [
  { code: 40, label: 'Lakeside Dry Dump' },
  { code: 41, label: 'Water Dump' },
  { code: 42, label: 'Water Boom' },
  { code: 44, label: 'Williston Lake Dewater Only' },
  { code: 45, label: 'Dewater and Reload' },
  { code: 49, label: 'Hydro Dam Log Transfer' },
  { code: 50, label: 'Truck to Truck Transfer' },
  { code: 51, label: 'Truck to Rail Transfer' },
  { code: 53, label: 'Low Water Bridge' },
] as const;

/** The 3 distance-based categories (each carrying its own Distance — BR-04 applies to these only). */
export const DISTANCE_CATEGORIES = [
  { code: 47, label: 'Truck Barge/Ferry' },
  { code: 48, label: 'Crew Barge/Ferry' },
  { code: 52, label: 'Rail Haul' },
] as const;

/** The 3 list sub-pages, by their grid label. Only Truck Rehaul carries a Cycle. */
export const SUB_PAGES = [
  { type: 'TOWING', code: 43, label: 'Towing Total', hasCycle: false },
  { type: 'TRUCK_REHAUL', code: 46, label: 'Truck Rehaul-Dewater/Transfer', hasCycle: true },
  { type: 'OTHER', code: 55, label: 'Other Transportation', hasCycle: false },
] as const;

export type SubPageType = (typeof SUB_PAGES)[number]['type'];

/** Grid row label → category code, for both category kinds (the grid renders `${label}:`). */
export const CATEGORY_CODE_BY_LABEL: Record<string, number> = Object.fromEntries(
  [...FIXED_CATEGORIES, ...DISTANCE_CATEGORIES].map((c) => [c.label, c.code]),
);

/** A sub-page's label → its type/code/cycle facts. */
export const subPageByLabel = (label: string): (typeof SUB_PAGES)[number] => {
  const found = SUB_PAGES.find((s) => s.label === label);
  if (!found) {
    throw new Error(
      `unknown Schedule 4 sub-page "${label}". Use one of: ${SUB_PAGES.map((s) => s.label).join(', ')}.`,
    );
  }
  return found;
};

/** Every grid row label in the order the page renders them (legacy code order, sub-pages interleaved). */
export const GRID_ROW_LABELS: string[] = [...FIXED_CATEGORIES, ...DISTANCE_CATEGORIES]
  .map((c) => ({ code: c.code, label: `${c.label}:` }))
  .concat(SUB_PAGES.map((s) => ({ code: s.code, label: `${s.label} (0):` })))
  .sort((a, b) => a.code - b.code)
  .map((entry) => entry.label);

// ---------------------------------------------------------------------------------------------------
// Element ids (components/schedule4/index.tsx + SubPage.tsx) — used for Carbon inline-error scoping and
// for the numeric grid cells, which are `hideLabel` inputs addressed by their stable id.
//
// NOTE the category-grid ids START WITH A DIGIT (the legacy cost-item code), so a bare `#40-volume` is an
// INVALID CSS selector. Both page objects resolve these through `byId()` in pages/common/carbonHelpers.ts,
// which uses the attribute form; the `#`-prefixed value here is what the Carbon field-error helper takes.
// ---------------------------------------------------------------------------------------------------

export const FIELD_ID = {
  locationName: '#location-name',
  comments: '#location-comments',
  /** A category grid cell: `#{code}-{volume|cost|distance}`. */
  category: (code: number, field: 'volume' | 'cost' | 'distance'): string => `#${code}-${field}`,
  /** A sub-page add-row field: `#subpage-{field}`. */
  subPage: (field: 'description' | 'distance' | 'volume' | 'cost' | 'cycle'): string =>
    `#subpage-${field}`,
  /** An existing sub-page row's in-place edit cell: `#row-{rowId}-{field}`. */
  row: (rowId: number, field: 'description' | 'distance' | 'volume' | 'cost' | 'cycle'): string =>
    `#row-${rowId}-${field}`,
} as const;

// ---------------------------------------------------------------------------------------------------
// Verbatim contract strings.
//
// The success / Check Status / guard / server-validation strings are API-OWNED (AD-8): the backend resolves
// them from `messages.properties` and the frontend renders `message.text` / `ProblemDetail.detail`
// verbatim, never hardcoding them. Pinning them here makes the specs a contract test of that wiring — if
// the bundle text changes, these fail rather than silently drifting.
//
// The CLIENT-side strings are marked as such: they have no request behind them (an advisory pre-flight
// gate, a confirm dialog, a suppression banner), so components/schedule4/index.tsx, validation.ts and
// subPageDefs.ts are their source of truth.
// ---------------------------------------------------------------------------------------------------

/** API-owned success/status text (backend `messages.properties`). */
export const MSG = {
  /** SUC-001 / SUC-002 — `dataSavedSuccesfullyInfoMsg` (location save AND sub-page row add/edit). */
  saved: 'Data saved successfully',
  /** SUC-003 / SUC-004 — `dataDeletedSuccesfullyInfoMsg` (location delete AND sub-page row delete). */
  deleted: 'Data deleted successfully',
  /** SUC-006 — `scheduleRequirementsMetMsg`, only when EVERY location passes. */
  scheduleMet: 'All requirements for this schedule have been met',
  /** SUC-005 — `locationRequirementsMetMsg`, {0} = the location name. */
  locationMet: (name: string): string => `All requirements for ${name} have been met.`,
  /** EF3 — `missingRequiredFieldMsg`, one per stored category / sub-page row whose Cost is null. */
  valueRequired: 'Value Required',
} as const;

/** API-owned rejection text (`ProblemDetail.detail`, rendered verbatim in the error banner). */
export const SERVER_ERR = {
  /** ERR-002 — `locationAlreadyExists`, 409. Case-insensitive, excludes the location's own family. */
  duplicateName: 'Location Name already exists.',
  /** ERR-001 — `locationEmptyOrNull`, 400 (only reachable via the API; the client gates Save first). */
  nameEmpty: 'Location Name can not be empty. Please enter a description.',
  /** 400 — `@Size(max=30)`. Unreachable from the UI: the field carries `maxLength={30}`. */
  nameTooLong: 'Location Name can not exceed 30 characters.',
  /** EF2-002 — `millNotActiveForCurrentYearMsg`, 409 on the document GET. */
  millNotActive:
    'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  /** EF2-003 — `scheduleNotFoundErrorMsg`, 404 on the document GET. */
  notFound: 'Schedule not found.',
  /** AD-9 — `scheduleNotEditableErrorMsg`, 409 on any write outside Draft. */
  notEditable: 'This schedule cannot be edited in its current status.',
  /** `scheduleRevisionConflictErrorMsg`, 409 on a stale optimistic-lock token. */
  staleRevision: 'This schedule was changed by another user. Please reload and try again.',
} as const;

/**
 * FLD-001/002/003/004 — the range messages. CLIENT-side advisory copies that deliberately mirror the
 * backend bundle word for word (components/schedule4/validation.ts VALIDATION_MESSAGES and
 * subPageDefs.ts SUB_PAGE_MESSAGES), confirmed identical to the API's own 400 detail by probe 2026-08-17.
 * The category grid and the sub-pages use DIFFERENT bands, which is the whole point of the size-6/size-7
 * legacy validator variants.
 */
export const FLD = {
  /** FLD-001 — category Volume, `volumeValidatorErrorMsg`. */
  categoryVolume: 'Entered volume must be between 0 and 9,999,999.',
  /** FLD-002 — category Cost, `costValidatorErrorMsg`. */
  categoryCost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  /** FLD-003 — Distance, `distanceValidatorErrorMsg` (same band on the grid and the sub-pages). */
  distance: 'Entered distance must be between 0 and 999,999.9.',
  /** Sub-page Volume, `volume6DigitValidatorErrorMsg` (the legacy `volSize="6"` variant). */
  rowVolume: 'Entered volume must be between 0 and 999,999.',
  /** Sub-page Cost, `costSize7ValidatorErrorMsg` (the legacy `costSize="7"` variant). */
  rowCost: 'Entered cost must be between -9,999,999 and 9,999,999.',
  /** FLD-004 — Truck Rehaul Cycle, `cycleValidatorErrorMsg`. */
  cycle: 'Entered cycle time must be between 0 and 999,999.',
} as const;

/** CLIENT-side chrome — no request behind it (components/schedule4/index.tsx + SubPage.tsx). */
export const CLIENT = {
  /** ERR-001, inline under Location Name — shown once a Save has been attempted and failed. */
  nameEmpty: 'Location Name can not be empty. Please enter a description.',
  /** BR-04's inline marker on whichever of Distance/Volume/Cost is missing. */
  valueRequired: 'Value Required',
  /** The advisory gate that blocks a doomed Save round-trip when a field is invalid. */
  correctBeforeSaving: 'Please correct the highlighted fields before saving.',
  /** EF2-001 — the mill/year suppression banner. */
  millYearNotSelected: 'Please Select Mill and Reporting Year in the Home Page.',
  millYearNotSelectedTitle: 'Mill and Reporting Year required',
  /** WRN-001, {0} = the source location's name. */
  copyNudge: (name: string): string =>
    `To complete copy of Location: ${name}, provide a new Location Name and invoke save.`,
  /** NAV-004 / NAV-005 — both confirm modals carry the same body text. */
  confirmDelete: 'This will delete the current record. Do you want to continue?',
  /** NAV-002 — leaving a SAVED location's panel for a sub-page (edits discarded). */
  navUnsavedLost: 'Any unsaved data will be lost. Are you sure you would like to continue?',
  /** NAV-003 — leaving an UNSAVED new location for a sub-page (must save first). */
  navSaveFirst:
    'The information for the New Location must be saved before you can add other Transportation. Would you like to save the information now?',
  /** Notification titles (severity carried by an explicit word, never colour alone — WCAG 2.1 AA). */
  titleSuccess: 'Success',
  titleActionFailed: 'Action failed',
  titleCopy: 'Copy location',
  titleCheckStatus: 'Check Status',
  titleLoadFailed: 'Unable to load Schedule 4',
  /** The per-location Check Status issue notification's title: `${location.name} — required`. */
  titleLocationRequired: (name: string): string => `${name} — required`,
} as const;

/** The Carbon confirm/nav modals (components/schedule4/index.tsx + SubPage.tsx). */
export const MODAL = {
  deleteLocation: { heading: 'Delete location', primary: 'Delete', secondary: 'Cancel' },
  deleteRow: { heading: 'Delete row', primary: 'Delete', secondary: 'Cancel' },
  navExisting: { heading: 'Unsaved changes', primary: 'Continue', secondary: 'Cancel' },
  navNew: { heading: 'Save before continuing', primary: 'Save and continue', secondary: 'Cancel' },
} as const;

/** The page's action buttons. */
export const ACTION = {
  addNewLocation: 'Add New Location',
  checkStatus: 'Check Status',
  save: 'Save',
  back: 'Back',
  close: 'Close',
  edit: 'Edit',
  view: 'View',
  copy: 'Copy',
  delete: 'Delete',
  addRow: 'Add row',
} as const;

/** A Location Name at exactly the 30-character `maxLength` boundary (S09). */
export const NAME_AT_BOUNDARY = 'AbcdefghijAbcdefghijAbcdefg304';

/** 31 characters — what S09 types to prove `maxLength` truncates rather than rejects. */
export const NAME_OVER_BOUNDARY = `${NAME_AT_BOUNDARY}X`;
