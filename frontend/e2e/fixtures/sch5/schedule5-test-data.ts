/**
 * UC-SCH5-001 (Schedule 5 — Report Camp and Access Expenses) pinned test data.
 * DB-grounded through the app's own API, never fabricated.
 *
 * ANCHOR SCARCITY — read this before adding the remaining slices.
 * Surveyed 2026-09-08 against the seeded local delivery DB (THE/…@localhost:1525/DBDOCK_01) with
 * sqlplus, then confirmed through `GET /api/v1/schedule5?millId=<m>&year=<y>`:
 *   - THE.ILCR_MILL_REPORT_STATUS holds 123 rows (107 "D", 9 "S", 7 "V"); opened reporting years are
 *     2015–2021 only, and there are 17 ACT + 4 CLS listable mills.
 *   - `preflight/anchor-keys.ts` already counts 119 (mill, year) keys pinned by the sch1/sch2/sch3/
 *     sch4/sch11/sec fixtures.
 *   - Exactly THREE Draft mill-years were pinned by nobody — 1/2017, 14050/2018, 25051/2017 — and all
 *     three are CLS mills that answer HTTP 409. (The probe is sound: 16050/2018, 23052/2019 and
 *     13050/2018 answer 200 editable with camps=0.)
 * So the 17×7 grid had just three empty cells — 9050/2016, 16050/2015, 16050/2016 — and two were
 * already pinned (16050/2015 is sch3's `never-started` 404 guard, whose fixture IS the absence of a
 * row; 16050/2016 is shared by sch11 and sec). `real-test-data-patches/sch5/draft-anchors.sql` opens
 * the last one. Every FURTHER mutating anchor must MINT capacity — see that file's FAN-OUT NOTE
 * (preferred: open reporting year 2022+, which makes "year >= 2022 belongs to sch5" a structural
 * invariant since every key pinned today is <= 2021).
 *
 * WHY SCHEDULE 5 NEEDS NO SUMMARY ROW, unlike Schedule 3: a valid ACTIVE mill-year holding no camps is
 * the legitimate empty state and answers 200 `camps: []`, never a 404 (Schedule5Service.java:46-47,
 * deviation (a)). The scarcity here is anchor EXCLUSIVITY, not missing schedule data.
 *
 * CROSS-SCHEDULE COUPLING: Schedule 5 is one of the domains that reads Schedule 3 (`Schedule3Service`'s
 * own consumer list is schedule1, schedule2, schedule5, reporting), so a sch3 scenario on the same
 * (mill, year) could move figures under a Schedule 5 assertion. 9050/2016 is pinned by no other
 * fixture at all, which settles it: sch4 owns mill 9050 in 2015 and 2018–2021 and sch1 in 2017, but
 * anchors are (mill, year) PAIRS and the cross-domain guard compares pairs, not mills.
 *
 * PARALLEL SAFETY: the suite runs `fullyParallel`, and a Schedule 5 save creates a real CAMP_REPORT
 * row, so every MUTATING scenario owns a DEDICATED (mill, year) no other scenario writes to.
 *
 * CLEANUP CONTRACT (confirmed by probe 2026-09-08 on this exact anchor):
 *   POST   /api/v1/schedule5/camps?millId=9050&year=2016            -> 200 "Data saved successfully"
 *   DELETE /api/v1/schedule5/camps/{campId}?millId=&year=&revisionCount=
 *                                                                   -> 200 "Data deleted successfully"
 * and the re-GET returns camps: [] — the anchor's at-rest state. That DELETE is what the cleanup
 * registry calls, so the anchor is left as found.
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch5-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch5 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

export interface Sch5Anchor {
  key: ScheduleKey;
  mill: MillRef;
}

const MILL_760: MillRef = { millNumber: '760', millName: 'WESTEROS' }; // millId 9050, ACT
const MILL_2121: MillRef = { millNumber: '2121', millName: 'SESAME STREET' }; // millId 10050, ACT
const MILL_987: MillRef = { millNumber: '987', millName: 'TURTLE DOVE' }; // millId 12050, ACT
const MILL_999: MillRef = { millNumber: '999', millName: 'ISP TEST' }; // millId 13050, ACT
const MILL_514: MillRef = { millNumber: '514', millName: 'AAA MILLING' }; // millId 16050, ACT
const MILL_727: MillRef = { millNumber: '727', millName: 'Updated Mill E2E' }; // millId 17052, ACT
const MILL_20171: MillRef = { millNumber: '20171', millName: 'MILES MILLING' }; // millId 22050, ACT
const MILL_20172: MillRef = { millNumber: '20172', millName: 'COVEY CUSTOM CUT' }; // millId 22051, ACT
const MILL_20173: MillRef = { millNumber: '20173', millName: 'TOMTESTMILL042017' }; // millId 23050, ACT
const MILL_20174: MillRef = { millNumber: '20174', millName: 'AO CUSTOM' }; // millId 23051, ACT
const MILL_20176: MillRef = { millNumber: '20176', millName: 'TANNER LOGS' }; // millId 23052, ACT
const MILL_7777: MillRef = { millNumber: '7777', millName: 'CGT TEST MILL7' }; // millId 24050, ACT
const MILL_8888: MillRef = { millNumber: '8888', millName: 'CGI TEST MILL8' }; // millId 24051, ACT
const MILL_9171: MillRef = { millNumber: '9171', millName: 'BCOVEY-TEST' }; // millId 25050, ACT
const MILL_9172: MillRef = { millNumber: '9172', millName: 'MDOBIE-TEST' }; // millId 25051, CLS
const MILL_9173: MillRef = { millNumber: '9173', millName: 'MRICE-TEST' }; // millId 25052, ACT
const MILL_9174: MillRef = { millNumber: '9174', millName: 'AOLSON-TEST' }; // millId 25053, ACT
const MILL_9175: MillRef = { millNumber: '9175', millName: 'TCASEY-TEST' }; // millId 25054, ACT

// ---------------------------------------------------------------------------------------------------
// MUTATING anchors — one per scenario that saves. Every one is an ACT mill, trackStatus "D",
// editable:true and holds NO camps at rest; `preflight/sch5-anchors.setup.ts` asserts all of that,
// because the scenarios open by asserting a blank panel or an empty Existing Camps table.
//
// ALL SEEDED. The extract had no free Draft mill-year left for Schedule 5 (see the header), so
// `real-test-data-patches/sch5/draft-anchors.sql` opens reporting years 2022-2023 and claims 21 cells
// in them, plus 9050/2016 which was the last free cell of the old grid. Each is folded into the CI
// seed `db-e2e/R__80_e2e_anchor_seed.sql` in the same change (a patch not folded in does not exist in
// CI) and the patch also seeds the eleven ILCR_REPORT_CATEGORY rows delivery's composite FK
// CMP_RPT_ILCR_RCAT_FK requires — without them the page opens but the first save 500s.
//
// Every one of the 22 was confirmed through `GET /api/v1/schedule5` on 2026-09-09: 200, camps=0, and
// the expected track code.
// ---------------------------------------------------------------------------------------------------

/** S01 — Add a New Camp With Descriptors and Fixed-Category Expenses (Happy Path). */
export const ADD_ANCHOR: Sch5Anchor = { key: { millId: 9050, year: 2016 }, mill: MILL_760 };

/** S02 — Edit an Existing Camp. */
export const EDIT_ANCHOR: Sch5Anchor = { key: { millId: 9050, year: 2022 }, mill: MILL_760 };
/** S03 — Copy an Existing Camp and Save With a New Name. */
export const COPY_ANCHOR: Sch5Anchor = { key: { millId: 10050, year: 2022 }, mill: MILL_2121 };
/** S04 — Other Camp/Access Expenses sub-page for an EXISTING camp. */
export const SUBPAGE_EXISTING_ANCHOR: Sch5Anchor = { key: { millId: 12050, year: 2022 }, mill: MILL_987 };
/** S05 — Other Camp/Access Expenses sub-page for a NEW, unsaved camp (the save-first gate). */
export const SUBPAGE_NEW_ANCHOR: Sch5Anchor = { key: { millId: 13050, year: 2022 }, mill: MILL_999 };
/** S06 — Check Status, all requirements met. */
export const CHECK_MET_ANCHOR: Sch5Anchor = { key: { millId: 17052, year: 2022 }, mill: MILL_727 };
/** S07 — Delete an Existing Camp. */
export const DELETE_ANCHOR: Sch5Anchor = { key: { millId: 22050, year: 2022 }, mill: MILL_20171 };
/** S08 — Same camp name allowed in a DIFFERENT mill/year: the first of the pair. */
export const SAME_NAME_A_ANCHOR: Sch5Anchor = { key: { millId: 22051, year: 2022 }, mill: MILL_20172 };
/** S08 — ...and the second. Two anchors by construction: the slice IS the cross-mill-year comparison. */
export const SAME_NAME_B_ANCHOR: Sch5Anchor = { key: { millId: 23050, year: 2022 }, mill: MILL_20173 };
/** S09 — Recoveries reduces the Camp Total (the volume-less twelfth category). */
export const RECOVERIES_ANCHOR: Sch5Anchor = { key: { millId: 23051, year: 2022 }, mill: MILL_20174 };
/** S10 — Close/navigate away with unsaved changes prompts a discard confirm. */
export const DISCARD_CLOSE_ANCHOR: Sch5Anchor = { key: { millId: 23052, year: 2022 }, mill: MILL_20176 };
/** S11 — Switching to a different camp while editing prompts a discard confirm (needs two camps). */
export const CAMP_SWITCH_ANCHOR: Sch5Anchor = { key: { millId: 24050, year: 2022 }, mill: MILL_7777 };
/** S13 — Duplicate camp name on save, case-insensitive (BR-02). */
export const DUPLICATE_NAME_ANCHOR: Sch5Anchor = { key: { millId: 24051, year: 2022 }, mill: MILL_8888 };
/** S14 — Save a copied camp without renaming it. */
export const COPY_DUPLICATE_ANCHOR: Sch5Anchor = { key: { millId: 25050, year: 2022 }, mill: MILL_9171 };
/** S20 — Check Status finds missing required values. */
export const CHECK_MISSING_ANCHOR: Sch5Anchor = { key: { millId: 25052, year: 2022 }, mill: MILL_9173 };
/** S21 — Other ACCESS expense description left blank. */
export const ACCESS_DESC_BLANK_ANCHOR: Sch5Anchor = { key: { millId: 25053, year: 2022 }, mill: MILL_9174 };
/** S22 — Other CAMP expense description blank, blocked at sub-page save. */
export const CAMP_DESC_BLANK_ANCHOR: Sch5Anchor = { key: { millId: 25054, year: 2022 }, mill: MILL_9175 };
/** S23 — Invalid cost on the Other Camp/Access Expense sub-page. */
export const SUBPAGE_COST_ANCHOR: Sch5Anchor = { key: { millId: 9050, year: 2023 }, mill: MILL_760 };
/** S24 — Check Status includes unsaved edits: a violation entered but not saved (BR-12 family). */
export const CHECK_UNSAVED_VIOLATION_ANCHOR: Sch5Anchor = { key: { millId: 10050, year: 2023 }, mill: MILL_2121 };
/** S25 — Check Status includes unsaved edits: a correction not yet saved clears the error. */
export const CHECK_UNSAVED_FIX_ANCHOR: Sch5Anchor = { key: { millId: 12050, year: 2023 }, mill: MILL_987 };

/**
 * S12 / S15 — VALIDATE-ONLY. Nothing is ever saved here: both slices prove entry is REJECTED, so the
 * anchor must be one no scenario creates on. Deliberately not any mutating key above — a validate-only
 * assertion sharing a happy-path anchor is the classic way a "nothing was written" claim goes green
 * for the wrong reason.
 */
export const VALIDATION_ANCHOR: Sch5Anchor = { key: { millId: 13050, year: 2023 }, mill: MILL_999 };

// ---------------------------------------------------------------------------------------------------
// READ-ONLY and GUARD anchors — no exclusivity needed, because nothing writes to them.
// ---------------------------------------------------------------------------------------------------

/**
 * S19 — Schedule not editable, report not in Draft. Seeded Submitted ("S"), so `editable` is false and
 * the page renders its read-only view. Confirmed 2026-09-09: 200, trackStatus "S", editable false.
 */
export const READ_ONLY_ANCHOR: Sch5Anchor = { key: { millId: 16050, year: 2023 }, mill: MILL_514 };

/**
 * S17 — Selected mill not active for the reporting year (ERR-002 -> HTTP 409).
 *
 * NOT seeded and NOT modified: 25051 is an existing CLS mill that already carries a 2017 report-status
 * row, which is what makes the 409 reachable (without a row MillContextService answers 404 first and
 * the 409 is never reached). No other fixture pins 25051/2017. Confirmed 2026-09-09: HTTP 409.
 */
export const CLOSED_MILL_ANCHOR: Sch5Anchor = { key: { millId: 25051, year: 2017 }, mill: MILL_9172 };

/**
 * S18 — No Schedule 5 record found for mill/year (HTTP 404).
 *
 * The ABSENCE is the fixture. The fan-out opens 2022 for sixteen of the seventeen ACT mills and skips
 * 16050 precisely so a 404 anchor exists inside sch5's own new year. Registered in
 * DELIBERATELY_ABSENT in `preflight/ci-seed-parity.setup.ts`, which fails the run if anyone seeds it.
 * Confirmed 2026-09-09: HTTP 404.
 */
export const NO_SCHEDULE_ANCHOR: Sch5Anchor = { key: { millId: 16050, year: 2022 }, mill: MILL_514 };

/** Every anchor that must be an empty, editable Draft — the list preflight iterates. */
export const EDITABLE_DRAFT_ANCHORS: ReadonlyArray<{ name: string; anchor: Sch5Anchor }> = [
  { name: 'add (S01)', anchor: ADD_ANCHOR },
  { name: 'edit (S02)', anchor: EDIT_ANCHOR },
  { name: 'copy (S03)', anchor: COPY_ANCHOR },
  { name: 'subpage-existing (S04)', anchor: SUBPAGE_EXISTING_ANCHOR },
  { name: 'subpage-new (S05)', anchor: SUBPAGE_NEW_ANCHOR },
  { name: 'check-met (S06)', anchor: CHECK_MET_ANCHOR },
  { name: 'delete (S07)', anchor: DELETE_ANCHOR },
  { name: 'same-name-a (S08)', anchor: SAME_NAME_A_ANCHOR },
  { name: 'same-name-b (S08)', anchor: SAME_NAME_B_ANCHOR },
  { name: 'recoveries (S09)', anchor: RECOVERIES_ANCHOR },
  { name: 'discard-close (S10)', anchor: DISCARD_CLOSE_ANCHOR },
  { name: 'camp-switch (S11)', anchor: CAMP_SWITCH_ANCHOR },
  { name: 'duplicate-name (S13)', anchor: DUPLICATE_NAME_ANCHOR },
  { name: 'copy-duplicate (S14)', anchor: COPY_DUPLICATE_ANCHOR },
  { name: 'check-missing (S20)', anchor: CHECK_MISSING_ANCHOR },
  { name: 'access-desc-blank (S21)', anchor: ACCESS_DESC_BLANK_ANCHOR },
  { name: 'camp-desc-blank (S22)', anchor: CAMP_DESC_BLANK_ANCHOR },
  { name: 'subpage-cost (S23)', anchor: SUBPAGE_COST_ANCHOR },
  { name: 'check-unsaved-violation (S24)', anchor: CHECK_UNSAVED_VIOLATION_ANCHOR },
  { name: 'check-unsaved-fix (S25)', anchor: CHECK_UNSAVED_FIX_ANCHOR },
  { name: 'validation (S12/S15)', anchor: VALIDATION_ANCHOR },
];

/** The guard anchors and the HTTP status each must still produce. */
export const GUARD_ANCHORS: ReadonlyArray<{ name: string; anchor: Sch5Anchor; expectHttp: number }> = [
  { name: 'closed-mill (S17)', anchor: CLOSED_MILL_ANCHOR, expectHttp: 409 },
  { name: 'no-schedule (S18)', anchor: NO_SCHEDULE_ANCHOR, expectHttp: 404 },
];

// ---------------------------------------------------------------------------------------------------
// S01 input values and the derived figures they produce.
// ---------------------------------------------------------------------------------------------------

/**
 * The camp S01 creates. The name is the Gherkin's own literal, kept verbatim for traceability and
 * confirmed unused: `SELECT ... FROM THE.CAMP_REPORT WHERE ILCR_CATEGORY_ID='5' AND (UPPER(CAMP_NAME)
 * LIKE '%CEDAR%' OR UPPER(CAMP_NAME) LIKE '%E2E%')` returned zero rows across the whole extract
 * (2026-09-08). CAMP_NAME is VARCHAR2(30) in delivery, so 16 characters is well inside the cap.
 */
export const NEW_CAMP_NAME = 'Cedar Creek Camp';

/** The descriptors, transcribed from UC-SCH5-001-S01.feature and valid against `validation.ts`. */
export const NEW_CAMP_DESCRIPTORS = {
  roadDistanceToOperatingArea: '12.5',
  sizeOfCamp: '40',
  associatedCampVolume: '5000',
  isolatedCamp: 'Yes',
} as const;

/**
 * The nine fixed-category costs S01 enters, keyed by the category's VERBATIM grid label (including the
 * trailing ": " where the app has one — `components/schedule5/validation.ts` GRID_ROWS is the single
 * source of row order and labels, and the accessible name of each input is `<label without ": "> cost`
 * / `… volume`).
 *
 * `recoveries` is deliberately absent: the Gherkin never enters it, and it is the volume-less twelfth
 * category. Leaving it null keeps Camp Total equal to Camp Sub-Total, which is what S01 asserts.
 */
export const NEW_CAMP_COSTS: ReadonlyArray<{ label: string; cost: string }> = [
  { label: 'Catering and Food', cost: '1000' },
  { label: 'Wages and Benefits', cost: '2000' },
  { label: 'Depreciation/Lease', cost: '500' },
  { label: 'General Camp Expenses', cost: '300' },
  { label: 'Crew Transportation', cost: '700' },
  { label: 'Land', cost: '400' },
  { label: 'Rail', cost: '0' },
  { label: 'Air', cost: '0' },
  { label: 'Water', cost: '0' },
];

/**
 * The eleven volume-bearing categories BR-03 propagates `associatedCampVolume` into. TWELVE categories
 * exist; `recoveries` has no volume cell at all (GRID_ROWS `hasVolume: false`), which is exactly why
 * the Gherkin says "every one of the 11 expense-category Volume fields".
 */
export const VOLUME_BEARING_CATEGORY_LABELS: readonly string[] = [
  'Catering and Food',
  'Wages and Benefits',
  'Depreciation/Lease',
  'General Camp Expenses',
  'Other Camp Expenses',
  'Crew Transportation',
  'Land',
  'Rail',
  'Air',
  'Water',
  'Other Access Expenses',
];

/**
 * The server-derived totals the costs above produce. MEASURED, not computed by hand: POSTed to
 * `/api/v1/schedule5/camps?millId=9050&year=2016` on 2026-09-08 and read back off the 200 response,
 * then the probe camp was deleted again.
 *
 *   campSubTotal        1000 + 2000 + 500 + 300            = 3800   (+ Other Camp Expenses, 0 rows)
 *   campTotal           3800 − recoveries (null)           = 3800
 *   accessExpenseTotal  700 + 400 + 0 + 0 + 0              = 1100   (+ Other Access Expenses, 0 rows)
 *   campAndAccessTotal  3800 + 1100                        = 4900
 * and the $/m³ column against the 5000 camp volume: 3800/5000 = 0.76, 4900/5000 = 0.98.
 */
export const NEW_CAMP_EXPECTED_TOTALS = {
  campSubTotalCost: 3800,
  campTotalCost: 3800,
  accessExpenseTotalCost: 1100,
  campAndAccessTotalCost: 4900,
  campTotalCostPerVolume: 0.76,
  campAndAccessTotalCostPerVolume: 0.98,
} as const;

/** Carbon Dropdown option text for a mill — mirrors Home's `millItemToString` ("760 - WESTEROS"). */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

/** The Schedule 5 read endpoint for a (mill, year) — the one place the query shape is spelled out. */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule5?millId=${millId}&year=${year}`;

/** The camp DELETE the cleanup registry calls. `revisionCount` is required — a falsy 0 is valid. */
export const campDeleteUrl = (
  campId: number,
  millId: number,
  year: number,
  revisionCount: number,
): string =>
  `/api/v1/schedule5/camps/${campId}?millId=${millId}&year=${year}&revisionCount=${revisionCount}`;

// ---------------------------------------------------------------------------------------------------
// S02 — Edit an Existing Camp.
// ---------------------------------------------------------------------------------------------------

/**
 * The camp S02 edits. Created by the scenario's own Given through the app's POST (not SQL), so the
 * precondition is exactly the shape a user's first save produces. Distinct from S01's name so the two
 * can never be confused in a failure message, and confirmed unused across the extract.
 */
export const EDIT_CAMP_NAME = 'North Camp';

/**
 * The request body that seeds S02's baseline camp — S01's values, so the two slices share one
 * arithmetic story and the edit's effect is isolated to the two fields it changes.
 *
 * All TWELVE categories are present because an omitted `CategoryEntry` CLEARS both halves server-side
 * (Schedule5Request.ts) — there is no PATCH semantic. `otherCampExpenses`/`otherAccessExpenses` are
 * volume-only (their cost is the sub-page row sum) and `recoveries` is cost-only.
 */
export const EDIT_CAMP_BASELINE = {
  campName: EDIT_CAMP_NAME,
  roadDistanceToOperatingArea: 12.5,
  sizeOfCamp: 40,
  associatedCampVolume: 5000,
  isolatedCamp: true,
  cateringAndFood: { volume: 5000, cost: 1000 },
  wagesAndBenefits: { volume: 5000, cost: 2000 },
  depreciationLease: { volume: 5000, cost: 500 },
  generalCampExpenses: { volume: 5000, cost: 300 },
  otherCampExpenses: { volume: 5000 },
  recoveries: { cost: 0 },
  crewTransportation: { volume: 5000, cost: 700 },
  equipAndSuppliesLand: { volume: 5000, cost: 400 },
  equipAndSuppliesRail: { volume: 5000, cost: 0 },
  equipAndSuppliesAir: { volume: 5000, cost: 0 },
  equipAndSuppliesWater: { volume: 5000, cost: 0 },
  otherAccessExpenses: { volume: 5000 },
} as const;

/**
 * How the baseline above RENDERS when the camp is reopened — the grouped display form, not the raw one.
 *
 * WHY THESE DIFFER FROM THE NUMBERS ABOVE, and why it is not a bug. A freshly typed panel holds the raw
 * strings the user entered (S01 asserts "5000" on a propagated volume and passes). A REOPENED panel is
 * seeded from the served document through `components/schedule5/masks.ts`, whose `fmtVolume` and
 * `fmtCost` are `toLocaleString('en-CA')` with no decimals — so the same 1000 comes back as "1,000".
 * Both masks are transcribed from the legacy JSF converters (ILCRVolumeConverter `#,###,###`,
 * ILCRCostConverter `##,###,###`), so the grouping is legacy parity, not a rewrite artefact.
 *
 * Measured against the running app on 2026-09-09 — the first version of S02 asserted the raw "1000" and
 * failed with `Received: "1,000"`, which is exactly the re-grounding this suite exists to do.
 */
export const EDIT_CAMP_DISPLAY = {
  campName: EDIT_CAMP_NAME,
  roadDistanceToOperatingArea: '12.5',
  sizeOfCamp: '40',
  /** The `Select`'s VALUE, not its label — the option text is "Yes". */
  isolatedCamp: 'true',
  cateringAndFoodCost: '1,000',
  cateringAndFoodVolume: '5,000',
} as const;

/** What S02 changes on screen: one descriptor and one category cost. */
export const EDIT_CAMP_CHANGES = {
  roadDistanceToOperatingArea: '15.0',
  cateringAndFoodCost: '1200',
} as const;

/**
 * The server-derived figures after S02's edit. MEASURED on 2026-09-09 by POSTing the baseline to
 * 9050/2022, PUTting the two changes, reading the 200 back, then deleting the camp.
 *
 *   campSubTotal        1200 + 2000 + 500 + 300 = 4000   (catering 1000 -> 1200)
 *   campTotal           4000 − recoveries (0)   = 4000
 *   accessExpenseTotal  unchanged               = 1100
 *   campAndAccessTotal  4000 + 1100             = 5100
 *
 * NOTE the per-row `$/m³` are each computed against the 5000 camp volume, NOT against the camp total —
 * accessExpenseTotal is 1100/5000 = 0.22 and catering is 1200/5000 = 0.24. Measured rather than
 * derived by hand precisely because that is easy to get wrong.
 */
export const EDIT_CAMP_EXPECTED_TOTALS = {
  campSubTotalCost: 4000,
  campTotalCost: 4000,
  accessExpenseTotalCost: 1100,
  campAndAccessTotalCost: 5100,
  campTotalCostPerVolume: 0.8,
  accessExpenseTotalCostPerVolume: 0.22,
  campAndAccessTotalCostPerVolume: 1.02,
  cateringAndFoodCostPerVolume: 0.24,
} as const;

/** A saved camp starts at revisionCount 0; the first edit takes it to 1 (observed on the same probe). */
export const EDIT_CAMP_EXPECTED_REVISION = 1;

// ---------------------------------------------------------------------------------------------------
// S03 — Copy an Existing Camp and Save With a New Name.
// ---------------------------------------------------------------------------------------------------

/** The camp S03 copies FROM. Created by the scenario's own Given, same baseline as S02. */
export const COPY_SOURCE_CAMP_NAME = 'North Camp';
/** The unique name the copy is saved under. */
export const COPY_NEW_CAMP_NAME = 'North Camp Annex';

/**
 * WRN-001, resolved. The source Gherkin carried this as an `[UNKNOWN]` — it assumed literal `{0}`
 * substitution but no live app existed to confirm it. Now confirmed in both directions:
 *   * the template is `sch5.copy.msg=To complete copy of Camp: {0}, provide a new Camp Name and invoke
 *     save.` (backend `messages.properties:253`);
 *   * the app resolves it over HTTP rather than hardcoding it — `openCopy` GETs `/v1/messages` with
 *     `{ key, arg: camp.campName }` (components/schedule5/index.tsx:713-718), so `{0}` really is the
 *     SOURCE camp's name.
 * The `[UNKNOWN]` marker in UC-SCH5-001's gherkin README can be retired for WRN-001 on this evidence.
 */
export const COPY_WARNING = `To complete copy of Camp: ${COPY_SOURCE_CAMP_NAME}, provide a new Camp Name and invoke save.`;

// ---------------------------------------------------------------------------------------------------
// S04 / S05 — the Other Camp/Access Expense sub-pages.
//
// There is NO second route: the sub-page level is driven by search params on `/schedule-5`
// (`camp` = CAMP_REPORT_ID, `sub` = 'CAMP' | 'ACCESS'), mirroring Schedule 4. So the legacy
// `schedule5CampExpenses.xhtml` / `schedule5AccessExpenses.xhtml` URLs re-ground to a query string, and
// the browser Back button steps back to the camp list.
// ---------------------------------------------------------------------------------------------------

/** The two sub-pages, keyed by the vocabulary the feature files use. Verbatim from SUB_PAGE_DEFS. */
export const SUB_PAGES = {
  camp: {
    sub: 'CAMP',
    /** The label on the grid row that navigates there — the live count is interpolated into it. */
    gridLabel: 'Other Camp Expenses',
    addHeader: 'Add Other Camp Expense',
    listHeader: 'Other Camp Expenses',
  },
  access: {
    sub: 'ACCESS',
    gridLabel: 'Other Access Expenses',
    addHeader: 'Add Other Access Expense',
    listHeader: 'Other Access Expenses',
  },
} as const;

/** S04's row: added to an EXISTING camp's Other Camp Expenses list. */
export const SUBPAGE_CAMP_ROW = { description: 'Generator Fuel', cost: '350' } as const;
/** S05's row: added to a NEW camp's Other Access Expenses list, after the save-first confirm. */
export const SUBPAGE_ACCESS_ROW = { description: 'Ferry Crossing', cost: '220' } as const;

/** The camp S05 creates on screen and then auto-saves through the confirm. */
export const SUBPAGE_NEW_CAMP_NAME = 'Elk Ridge Camp';

/**
 * CFM-004 — the save-first confirm, verbatim from `components/schedule5/index.tsx:85-86`. Matches the
 * source Gherkin's text exactly, so nothing was re-grounded here beyond the control type: it is a
 * Carbon `Modal` headed "Save camp report" with Yes/No buttons, not a PrimeFaces confirmDialog.
 */
export const CONFIRM_SAVE_NEW_CAMP =
  'The information for the New Camp must be saved before you can add other expenses. '
  + 'Would you like to save the information now?';

// ---------------------------------------------------------------------------------------------------
// S06 — Check Status, all requirements met.  |  S07 — Delete an existing camp.
// ---------------------------------------------------------------------------------------------------

/**
 * Check Status texts, verbatim from `messages.properties`.
 *
 * `campMet` is deliberately kept even though S06 asserts its ABSENCE — the negative needs the exact
 * string to be meaningful, and S20 (issues found) will assert its siblings. See SPEC-3: on a PASS the
 * app emits the schedule banner alone, matching legacy `Schedule5MB.java:324-326`, where the per-camp
 * loop lives in the `else` branch and is unreachable when the schedule passes.
 */
export const CHECK_STATUS_MESSAGES = {
  /** `scheduleRequirementsMetMsg` (messages.properties:179). Note: no trailing full stop. */
  scheduleMet: 'All requirements for this schedule have been met',
  /** `campRequirementsMetMsg` (messages.properties:246) with {0} = camp name. WITH a full stop. */
  campMet: (campName: string) => `All requirements for ${campName} have been met.`,
} as const;

/**
 * S08 — the name deliberately reused across two mill-years.
 *
 * BR-02 scopes camp-name uniqueness to a single (mill, year): `Schedule5Service` excludes by campId
 * within the served mill/year, never globally. S08 proves that scoping by saving the SAME name on
 * `SAME_NAME_B_ANCHOR` while `SAME_NAME_A_ANCHOR` already holds it — which is why this slice needs two
 * dedicated anchors rather than one.
 */
export const SAME_NAME_CAMP_NAME = 'North Camp';

/** CFM-001, verbatim from `components/schedule5/index.tsx:74`. Modal heading is "Delete camp". */
export const CONFIRM_DELETE_CAMP = 'This will delete the current record. Do you want to continue?';

/** ERR/SUC message text, verbatim from backend `messages.properties`. */
export const MESSAGES = {
  /** `dataSavedSuccesfullyInfoMsg` (messages.properties:168) — the Gherkin's expected text, unchanged. */
  saved: 'Data saved successfully',
  /** `dataDeletedSuccesfullyInfoMsg` — observed on the cleanup DELETE during the 2026-09-08 probe. */
  deleted: 'Data deleted successfully',
} as const;
