/**
 * UC-SCH3-001 (Schedule 3 — Report Forest Management Administration Costs) pinned test data.
 * DB-grounded through the app's own API, never fabricated.
 *
 * ---------------------------------------------------------------------------------------------------
 * ANCHOR DISCOVERY (2026-08-24, seeded local delivery DB THE/…@localhost:1525/DBDOCK_01)
 * ---------------------------------------------------------------------------------------------------
 * `GET /api/v1/mills` (21 mills) × reporting years 2010–2026 = 357 probes of
 * `GET /api/v1/schedule3?millId=<m>&year=<y>`, classified by HTTP status / trackStatus / editable /
 * revisionCount / stored values:
 *   -  28 pairs answer 200                     (a category-3 summary exists)
 *   - 322 pairs answer 404 "Schedule not found."
 *   -   7 pairs answer 409 (closed mill)
 * Of the 98 pairs carrying a Draft Schedules 1–10 track, only **15** open Schedule 3 at all — and every
 * one of the 15 is already pinned by the sch1 or sch2 fixtures.
 *
 * WHY SCHEDULE 3 NEEDS A SEED PATCH AT ALL. Schedule 3 has NO create path in the rewrite:
 * `Schedule3Service` resolves the category-3 `ILCR_REPORT_SUMMARY` first on every operation and 404s
 * when it is absent, where legacy created that row on the first Save. The extract carries 118
 * `ILCR_REPORT_CATEGORY` rows for category 3 (the schedule is REQUIRED) but only 31 summaries (it was
 * STARTED), so 87 Draft mill-years are un-enterable — that gap is itself a finding, tracked as
 * `defects.md` **DIV-1** with a deliberately-failing scenario. It also means a Schedule 3 anchor cannot
 * be created through the API, so `real-test-data-patches/sch3/draft-anchors.sql` adds the summary
 * legacy's first Save would have written (and nothing else) on 16 such mill-years. That file documents
 * every row it inserts.
 *
 * PARALLEL SAFETY. The suite runs `fullyParallel`, so every MUTATING scenario owns a DEDICATED
 * (mill, year) that no other scenario writes to — including each row of a `Scenario Outline`, which runs
 * as its own test. The read-only anchors are shared freely between scenarios precisely because nothing
 * writes to them.
 *
 * CROSS-DOMAIN. Sharing a (mill, year) with sch1 or sch2 would not be ordinary parallel hygiene — it
 * would be a genuine data race: Schedule 1 pulls its item-143 / item-139 costs FROM Schedule 3,
 * Schedule 2 carries its purchased-log volume and wood overhead FROM Schedule 3, and the BR-09 Crown
 * Timber push WRITES Schedule 1's own volume rows. So no MUTATING anchor below is pinned by sch1, sch2
 * or sec (checked pair-by-pair with `preflight/anchor-keys.ts scanAnchorKeys`, not by eye). Some are
 * also pinned by **sch4** or **sch11**, which is safe structurally rather than by convention: no backend
 * path links Schedule 3 to Schedule 4 or 11
 * (`grep -rl Schedule3Service backend/src/main/java` → schedule1, schedule2, schedule5, reporting only),
 * and those suites write category-"4"/"11" rows this one never reads.
 *
 * THE ONE sch1 SHARE, AND WHY IT IS THE READ-ONLY ANCHOR (adjudicated 2026-08-31, PR #402 review).
 * `check-empty` sits on 17052/2021, which is also sch1's `no-schedule` render-state anchor
 * (`fixtures/sch1/schedule1-test-data.ts`) and sch4's `persistence`. That pair CANNOT be avoided
 * cheaply — 17052/2021 is one of the few mill-years this suite can seed a Schedule 3 on, and sch1 pins
 * it deliberately because it is the mill-year defect #296 reproduces against. It is safe only because
 * the sch3 side is READ-ONLY and PERMANENTLY EMPTY: with no stored Schedule 3 amounts,
 * `Schedule1Service` derives a null `forestMgmtAdminCost` / `silvicultureAdminCrownCost` and its BR-09
 * volume pre-fill never arms (`sch3CrownVolume != null`), so sch1's "every Schedule 1 amount is blank"
 * (S21) and its never-saved save-first gate (S08) both stay true.
 *
 * `retry` USED TO SIT HERE and was moved to 22050/2019 in the same change. It is mutating, and its
 * scenario ends with a successful save of the full happy-path set INCLUDING a Crown Timber volume of
 * 150,000 — which arms exactly that pre-fill and would have reddened sch1 S21/S08 whenever the two ran
 * concurrently under `fullyParallel`. The two anchors were swapped rather than re-grounded onto a new
 * mill-year because both 17052/2021 and 22050/2019 are already seeded, Draft and empty, so the swap
 * needs no new data on either database. `preflight/sch3-anchors.setup.ts` now also asserts that `retry`
 * has NO saved Schedule 1, so a future move onto a Schedule-1-bearing mill-year fails before a browser
 * opens instead of writing rows this suite's teardown does not restore.
 *
 * CLEANUP CONTRACT. A mutating scenario is restored by PUTting the EMPTY document back (all line items
 * null, both volumes null, comments null, Override "N") and re-reading to prove it — see
 * `steps/sch3/schedule3Api.ts` `restoreAnchor`. Sub-page rows are removed by the same batch-save
 * endpoint with an empty row set. The one exception is the destructive `delete` scenario: DELETE removes
 * the summary itself and the app cannot recreate it, so cleanup re-runs the seed patch
 * (`steps/sch3/schedule3DbRestore.ts`).
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch3-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch3 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

/** An anchor plus what it is for — the `purpose` is what a preflight failure message quotes. */
export interface Sch3Anchor {
  key: ScheduleKey;
  mill: MillRef;
  purpose: string;
}

// Every mill below is ACT unless marked; ids are the API's `millId`, numbers/names its option text.
const MILL_514: MillRef = { millNumber: '514', millName: 'AAA MILLING' }; // millId 16050
const MILL_727: MillRef = { millNumber: '727', millName: 'Updated Mill E2E' }; // millId 17052
const MILL_2121: MillRef = { millNumber: '2121', millName: 'SESAME STREET' }; // millId 10050
const MILL_8888: MillRef = { millNumber: '8888', millName: 'CGI TEST MILL8' }; // millId 24051
const MILL_20171: MillRef = { millNumber: '20171', millName: 'MILES MILLING' }; // millId 22050
const MILL_20172: MillRef = { millNumber: '20172', millName: 'COVEY CUSTOM CUT' }; // millId 22051
const MILL_20173: MillRef = { millNumber: '20173', millName: 'TOMTESTMILL042017' }; // millId 23050
const MILL_9175: MillRef = { millNumber: '9175', millName: 'TCASEY-TEST' }; // millId 25054
const MILL_987: MillRef = { millNumber: '987', millName: 'TURTLE DOVE' }; // millId 12050
const MILL_11: MillRef = { millNumber: '11', millName: 'EVANS FOR. PROD. (DIV. OF LOUISIANA PACIFIC)' }; // millId 1, CLS

const at = (mill: MillRef, millId: number, year: number, purpose: string): Sch3Anchor => ({
  key: { millId, year },
  mill,
  purpose,
});

/**
 * ---------------------------------------------------------------------------------------------------
 * THE ANCHOR TABLE — one row per scenario (or per group of read-only scenarios).
 * ---------------------------------------------------------------------------------------------------
 * A `.feature` names an anchor by its KEY here (`Given the Schedule 3 anchor "happy-path" …`), so this
 * table is the single place a (mill, year) is chosen, documented and re-grounded.
 *
 * All 17 entries below carry a patched (empty or seeded) category-3 summary — the same 17 mill-years
 * `real-test-data-patches/sch3/draft-anchors.sql` and the CI seed's Schedule 3 block list, one for one.
 * `preflight` re-asserts each one's Draft track, `editable: true` and at-rest contents before a
 * browser opens. (The `RENDER_STATE_ANCHORS` below are NOT patched — they are real extract rows.)
 */
export const ANCHORS: Record<string, Sch3Anchor> = {
  // --- MUTATING: one dedicated (mill, year) per scenario ---------------------------------------------
  'happy-path': at(MILL_514, 16050, 2017, 'S01/S03 — enter every fixed line + both volumes, save, reload'),
  'check-met': at(MILL_514, 16050, 2019, 'S09 — Check Status on a complete schedule (seeded via the API)'),
  delete: at(MILL_514, 16050, 2020, 'S08 — delete the whole Schedule 3 (destructive; patch-restored)'),
  'crown-applied': at(MILL_514, 16050, 2021, 'S06 — BR-09 crown push WITH a Schedule 1 (WRN-001)'),
  'crown-not-opened': at(MILL_727, 17052, 2018, 'S07 — BR-09 crown push with NO Schedule 1 (WRN-002)'),
  'other-acceptable': at(MILL_727, 17052, 2019, 'S04 — itemize other-acceptable costs on the sub-page'),
  unacceptable: at(MILL_727, 17052, 2020, 'S05 — itemize included-unacceptable costs on the sub-page'),
  retry: at(MILL_20171, 22050, 2019, 'S17 — a failed save, then a successful retry'),
  'row-delete-confirm': at(
    MILL_9175,
    25054,
    2017,
    'DIV-5 — removing a sub-page row must ask for confirmation first (deliberately RED)',
  ),
  'stale-edit': at(
    MILL_987,
    12050,
    2018,
    'GAP-2 (AR11) — a save carrying a token another session already moved is refused with 409',
  ),

  // --- READ-ONLY: never written to, so scenarios may share them -------------------------------------
  validate: at(MILL_20171, 22050, 2018, 'S02/S20-S24 — client-rejected entry and the S111 alert; never written'),
  // 17052/2021 is ALSO sch1's `no-schedule` anchor and sch4's `persistence` — see "THE ONE sch1
  // SHARE" above. Safe here and nowhere else in this table, because nothing ever writes to it.
  'check-empty': at(MILL_727, 17052, 2021, 'S10 — Check Status on an empty schedule (every field missing)'),
  'check-harvest-pop': at(MILL_20171, 22050, 2020, 'S11 — seeded Wages/Salaries Harvest 40,000 < PO&P 50,000'),
  'check-override': at(MILL_20171, 22050, 2021, 'S12 — the same violations with Override "Y" (suppressed)'),
  'check-oa-pop': at(MILL_20173, 23050, 2017, 'S12 mirror — a seeded other-acceptable Total 1,000 < PO&P 2,500'),
  a11y: at(MILL_20173, 23050, 2018, 'NFR1 — the axe sweeps (populated page + both populated sub-pages)'),
  'check-subpage-missing': at(
    MILL_20173,
    23050,
    2019,
    'S10 (sub-page half) — a seeded other-acceptable group with no description and no PO&P, and an '
      + 'included-unacceptable row with no Total',
  ),
};

/**
 * Anchors this suite is allowed to WRITE to. `restoreAnchor` refuses to clean up anything else, so a
 * future copy-paste can never point the teardown at an anchor holding real extract data.
 */
export const MUTATING_ANCHOR_KEYS = [
  'happy-path',
  'check-met',
  'delete',
  'crown-applied',
  'crown-not-opened',
  'other-acceptable',
  'unacceptable',
  'retry',
  'row-delete-confirm',
  'stale-edit',
] as const;

/** The `crown-applied` anchor is the only one carrying a (patched) Schedule 1 to push into. */
export const CROWN_APPLIED_KEY = 'crown-applied';

/**
 * Render-state / guard anchors — REAL extract rows, held read-only (the Home Save is a resolve GET that
 * writes no report data, so no cleanup and parallel-safe). `expectHttp` is the schedule3 GET status the
 * state depends on; the precondition step asserts it, so a data drift fails as a re-ground rather than a
 * confusing UI timeout. (S13 "no context selected" needs no anchor — it is driven by seeding an empty
 * MillYearContext into localStorage.)
 */
export const RENDER_STATE_ANCHORS: Record<
  string,
  { key: ScheduleKey; mill: MillRef; expectHttp: number; track?: string; detail?: string }
> = {
  // S15 — a Submitted track: every amount renders read-only, every action disabled.
  'readonly-submitted': {
    key: { millId: 17052, year: 2015 },
    mill: MILL_727,
    expectHttp: 200,
    track: 'S',
  },
  // S15 — the other arm of the mirror: a Verified track.
  'readonly-verified': {
    key: { millId: 22051, year: 2015 },
    mill: MILL_20172,
    expectHttp: 200,
    track: 'V',
  },
  // S14 — mill 1 is CLS (closed) and 2016 carries a report-status row → schedule3 GET 409.
  'closed-mill': {
    key: { millId: 1, year: 2016 },
    mill: MILL_11,
    expectHttp: 409,
    detail: 'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  },
  // S16 — mill 514 has NO report-status row for 2015 → guard 1 → 404 "Schedule not found."
  'not-found': {
    key: { millId: 16050, year: 2015 },
    mill: MILL_514,
    expectHttp: 404,
    detail: 'Schedule not found.',
  },
  // ex-DIV-1, RE-GROUNDED 2026-08-26 — Draft, ILCR_REPORT_CATEGORY says Schedule 3 IS required here, and
  // no summary was ever started. The rewrite used to 404 on this state (that WAS DIV-1); since the
  // defect #296 fix it serves a 200 empty EDITABLE document and the first Save creates the summary, which
  // is what legacy did. Deliberately NOT patched: this anchor's whole point is the un-seeded state, and
  // it is the only anchor that proves the create-on-save path. Read-only for this suite — no scenario
  // saves here, so the summary stays absent and the anchor keeps working.
  'never-started': {
    key: { millId: 24051, year: 2015 },
    mill: MILL_8888,
    expectHttp: 200,
  },
};

/** Carbon Dropdown option text for a mill (mirrors Home's `millItemToString`). */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

// ---------------------------------------------------------------------------------------------------
// URLs
// ---------------------------------------------------------------------------------------------------

/** The Schedule 3 aggregate endpoint for a (mill, year) — GET read-back / PUT save / DELETE. */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule3?millId=${millId}&year=${year}`;

/** POST-only, mutates nothing by contract (AD-5) — never counted by the mutation spy. */
export const checkStatusUrl = (millId: number, year: number): string =>
  `/api/v1/schedule3/check-status?millId=${millId}&year=${year}`;

/** The Other Acceptable Costs sub-resource (item-124 TOT+PO&P groups). */
export const otherAcceptableUrl = (millId: number, year: number): string =>
  `/api/v1/schedule3/other-acceptable-costs?millId=${millId}&year=${year}`;

/** The Included Unacceptable Costs sub-resource (item-38 rows). */
export const unacceptableUrl = (millId: number, year: number): string =>
  `/api/v1/schedule3/included-unacceptable-costs?millId=${millId}&year=${year}`;

/** The Schedule 1 aggregate endpoint — read only, to prove the BR-09 push landed (and to restore it). */
export const schedule1Url = (millId: number, year: number): string =>
  `/api/v1/schedule1?millId=${millId}&year=${year}`;

/** The app routes (TanStack flat routes; `schedule-3_` opts out of nesting). */
export const ROUTE_SCHEDULE_3 = '/schedule-3';
export const ROUTE_OTHER_ACCEPTABLE = '/schedule-3/other-acceptable-costs';
export const ROUTE_UNACCEPTABLE = '/schedule-3/included-unacceptable-costs';

/** localStorage key the app persists the working context under (MillYearProvider). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

// ---------------------------------------------------------------------------------------------------
// Contract strings — API-owned unless marked, verbatim (AD-8)
// ---------------------------------------------------------------------------------------------------

/** SUC-001 `dataSavedSuccesfullyInfoMsg`. */
export const MSG_SAVED = 'Data saved successfully';
/** SUC-002 `dataDeletedSuccesfullyInfoMsg`. */
export const MSG_DELETED = 'Data deleted successfully';
/** SUC-003 `scheduleRequirementsMetMsg`. */
export const MSG_REQUIREMENTS_MET = 'All requirements for this schedule have been met';
/** BR-11 `missingRequiredFieldMsg` — rendered as `<label>: <this>`. */
export const MSG_VALUE_REQUIRED = 'Value Required';
/** BR-03 `harvestNotGreaterThanPopErrorMsg` — rendered as `<label>: <this>`. */
export const MSG_HARVEST_NOT_GE_POP =
  'Value must be greater than or equal to the corresponding PO&P Cost';
/**
 * The optimistic-lock refusal (AR11), verbatim from `backend/.../messages.properties:21`
 * `scheduleRevisionConflictErrorMsg` — the server authors it, the client never does (AD-8). Asserted by
 * `concurrency.feature`; the same string the sch4 and sch11 suites pin.
 */
export const MSG_REVISION_CONFLICT =
  'This schedule was changed by another user. Please reload and try again.';
/** WRN-001 `crownVolumeChangeSchedule1`. */
export const MSG_CROWN_APPLIED =
  'The new Crown Timber volume has been applied to Schedule 1 volume fields. Please check.';
/** WRN-002 `crownVolumeNotSetSchedule1` — ungrammatical in the source bundle; copied verbatim. */
export const MSG_CROWN_NOT_APPLIED =
  "The new Crown Timber volume couldn't been applied to Schedule 1 volume fields as it has not been opened.";
/** ERR-001 `scheduleNotSavedErrorMsg` — also the page's own fallback when a save fails with no detail. */
export const MSG_NOT_SAVED = 'Schedule could not be saved.';
/** ERR-004 `scheduleNotFoundErrorMsg` (404 detail). */
export const MSG_NOT_FOUND = 'Schedule not found.';

/** ERR-002 — client-side (no request is made), `components/schedule3/index.tsx`. */
export const MSG_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.';
/** ALT-001 — a `window.alert` fired by the Annual Rents Harvest field's blur handler. */
export const ALERT_S111 = 'Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost.';
/** Client chrome — the delete confirm Modal ("Delete schedule") body. */
export const CONFIRM_DELETE_BODY = 'This will delete the current record. Do you want to continue?';
/** Client chrome — the leave-page confirm Modal body (main page and both sub-pages). */
export const CONFIRM_NAVIGATION_BODY =
  'Any unsaved data will be lost. Are you sure you would like to continue?';
/** Client gate — Save is refused locally while a field is invalid. */
export const MSG_CORRECT_BEFORE_SAVING = 'Please correct the highlighted fields before saving.';
/**
 * ALT-002 — the save-first gate on the **Other Costs** sub-page, RESTORED by the defect #296 fix and
 * verbatim in `components/schedule3/index.tsx:47`. Before that fix the state could not be reached (an
 * unsaved Schedule 3 404'd), which is why S18/S19 were dispositioned `not-applicable`; S18 is covered now
 * (`save-first-gate.feature`).
 *
 * PROVENANCE, corrected 2026-08-27: this is NOT a message-bundle key. Legacy hardcodes it in the link's
 * own `onclick` — `schedule3.xhtml:267`, on the `subtotalOtherCostsEditsEnabledAlert` variant rendered
 * only when `!schedule3MB.isScheduleOpen()`. Nothing matching it exists in `messages.properties`
 * (searched whole-file 2026-08-27), so an earlier version of this comment citing
 * `saveScheduleBeforeOpeningOtherCostsMsg` named a key that has never existed.
 */
export const MSG_SAVE_BEFORE_SUB_PAGE = 'The schedule has to be saved before opening other costs';

/**
 * ALT-003 — legacy's SEPARATE, differently-worded gate on the **Included Unacceptable Costs** link
 * (`schedule3.xhtml:293`, the `includedUnacceptableCostsEditsEnabledAlert` variant). Note the capital U
 * and "Unacceptable costs" rather than "other costs": legacy wrote two distinct strings, one per link.
 *
 * The rewrite has only ONE — `ALT_SAVE_BEFORE_SUB_PAGE` is shown for BOTH links from the single generic
 * `openSubPage` handler (`components/schedule3/index.tsx:272`), so opening Included Unacceptable Costs
 * from a never-saved schedule shows the *Other Costs* wording. That is **defects.md DIV-7**, asserted by
 * the deliberately-red S19 in `save-first-gate.feature`. Schedule 1 is NOT affected: legacy gives it one
 * such link and one string (`schedule1.xhtml:497`), which the app matches verbatim. Verified by sweeping
 * every legacy `.xhtml` for "saved before opening" — exactly three hits, all accounted for here.
 */
export const MSG_SAVE_BEFORE_UNACCEPTABLE =
  'The schedule has to be saved before opening Unacceptable costs';

/** FLD-001 (`costValidatorErrorMsg`) — mirrored client-side in `schedule3/validation.ts`. */
export const MSG_COST_RANGE = 'Entered cost must be between -99,999,999 and 99,999,999.';
/** FLD-002 (`volumeValidatorErrorMsg`) — Schedule 3 volumes are NON-negative. */
export const MSG_VOLUME_RANGE = 'Entered volume must be between 0 and 9,999,999.';
/** Rewrite-only wording, no legacy counterpart (a legacy converter error had no confirmed text). */
export const MSG_COST_INVALID = 'Entered cost is invalid.';
export const MSG_VOLUME_INVALID = 'Entered volume entry is invalid.';
/** FLD-003 — the sub-page required-description message the rewrite RESOLVED (legacy: `[UNKNOWN]`). */
export const MSG_DESCRIPTION_REQUIRED = 'Description: Value is required.';
/**
 * Sub-page description cap (30 chars, BR-06/BR-07) and its message. The message is DOCUMENTED here but
 * deliberately not asserted by any scenario: the input carries `maxLength={30}`, so the browser caps
 * entry and the validator's over-length branch is unreachable through the UI (the same reason the
 * legacy catalogue excluded the comments 3,500-character limit). Recorded as `not-applicable` in
 * coverage.md rather than silently dropped.
 */
export const DESCRIPTION_MAX_LENGTH = 30;
export const MSG_DESCRIPTION_TOO_LONG = 'Description must be 30 characters or fewer.';

// ---------------------------------------------------------------------------------------------------
// The fixed admin-cost lines
// ---------------------------------------------------------------------------------------------------

/**
 * The 11 fixed lines: the app's own row label (`LINE_LABELS` in `components/schedule3/index.tsx`, itself
 * verbatim from the legacy `schedule3.xhtml` outputLabels), the Harvest cost-item code that identifies
 * the line, its PO&P item code, and how the PO&P column behaves.
 *
 *   `entry`   — both Harvest and PO&P are enterable (`#harvest-<code>` / `#pop-<code>`)
 *   `derived` — Scaling (33): PO&P is computed server-side from the timber-volume ratio and shown
 *               read-only (`Schedule3Constants.scalingPop`)
 *   `hidden`  — Annual Rents (29) / Silviculture Admin (37): legacy captured no PO&P at all, so the cell
 *               renders an em dash rather than the backend's 0 (BR-04)
 */
export interface LineSpec {
  label: string;
  code: number;
  popCode: number | null;
  pop: 'entry' | 'derived' | 'hidden';
}

export const LINES: readonly LineSpec[] = [
  { label: 'Licenses, Fees, Insurance', code: 27, popCode: 125, pop: 'entry' },
  { label: 'Taxes, Leases, Rentals', code: 28, popCode: 126, pop: 'entry' },
  { label: 'Annual Rents', code: 29, popCode: null, pop: 'hidden' },
  { label: 'Wages/Salaries, incl Benefits', code: 30, popCode: 128, pop: 'entry' },
  { label: 'Vehicle Expense', code: 31, popCode: 129, pop: 'entry' },
  { label: 'Office Expense', code: 32, popCode: 130, pop: 'entry' },
  { label: 'Scaling Expense', code: 33, popCode: null, pop: 'derived' },
  { label: 'Cruising & Layout Expense', code: 34, popCode: 132, pop: 'entry' },
  { label: 'Residue & Waste Expense', code: 35, popCode: 133, pop: 'entry' },
  { label: 'Depreciation Expense', code: 36, popCode: 134, pop: 'entry' },
  { label: 'Silviculture Admin Costs', code: 37, popCode: null, pop: 'hidden' },
];

/** A line by its app row label, failing loud on a typo in a `.feature`. */
export const lineByLabel = (label: string): LineSpec => {
  const found = LINES.find((l) => l.label === label);
  if (!found) {
    throw new Error(
      `Unknown Schedule 3 line label: "${label}". Known: ${LINES.map((l) => l.label).join(' | ')}`,
    );
  }
  return found;
};

/** A line by its Harvest cost-item code — the direction the stored-value assertions read in. */
export const lineByCode = (code: number): LineSpec => {
  const found = LINES.find((l) => l.code === code);
  if (!found) {
    throw new Error(`Unknown Schedule 3 line code: ${code}`);
  }
  return found;
};

/**
 * The check-status field labels the backend prefixes onto its two messages (verbatim
 * `Schedule3Service` — legacy `checkStatusSchedule3.xhtml` wording). Kept here so a `.feature` can name
 * the rendered notification exactly.
 */
export const CHECK_LABEL_POP_TIMBER =
  'Privately Owned & Purchased (PO&P) Timber Harvest (Volume m³)';
export const CHECK_LABEL_CROWN_TIMBER = 'Crown Timber Harvest (Volume m³)';
export const CHECK_LABEL_OA_TOTAL = 'Subtotal Other Costs (Harvest Total $)';
export const CHECK_LABEL_OA_POP = 'Subtotal Other Costs (PO&P $)';
export const CHECK_LABEL_OA_DESCRIPTION = 'Subtotal Other Costs (Description)';
export const CHECK_LABEL_UNACCEPTABLE_TOTAL = 'Included Unacceptable Costs (Total $)';

/** `<field label>: <message>` — how a Check Status notification actually reads. */
export const checkMessage = (label: string, message: string): string => `${label}: ${message}`;

// ---------------------------------------------------------------------------------------------------
// The S01 happy-path entry set and every figure it implies
// ---------------------------------------------------------------------------------------------------

/**
 * What S01 types, and every derived figure that follows from it — computed BY HAND here so the
 * assertions are a real specification of the arithmetic rather than an echo of the app.
 *
 * Entered (Harvest / PO&P), whole dollars:
 *   27 Licenses          100,000 / 10,000     31 Vehicle          30,000 /  3,000
 *   28 Taxes              20,000 /  2,000     32 Office           25,000 /  2,500
 *   29 Annual Rents        5,000 /   (none)   33 Scaling          15,000 /  (derived)
 *   30 Wages              60,000 /  6,000     34 Cruising         12,000 /  1,200
 *                                             35 Residue          8,000 /    800
 *                                             36 Depreciation      6,000 /    600
 *                                             37 Silviculture      4,000 /  (none)
 *   PO&P Timber volume 50,000 m³ · Crown Timber volume 150,000 m³ · Override "N"
 *
 * Derivations (all server-side, `Schedule3Service`):
 *   Scaling PO&P   = round(50,000 / 200,000 × 15,000)            =   3,750
 *   crown(line)    = harvest − PO&P   (null if either is absent)
 *   Subtotal Actual  harvest 285,000 · PO&P 29,850 · crown 255,150
 *   Included Unacceptable = Annual Rents harvest only            =   5,000 (PO&P forced 0)
 *   Total Costs      harvest 280,000 · PO&P 29,850 · crown 250,150
 *   PO&P Timber   cost = Total Costs PO&P  = 29,850 → $/m³ 29,850 / 50,000  = 0.60
 *   Crown Timber  cost = Total Costs crown = 250,150 → $/m³ 250,150 / 150,000 = 1.67
 *   Total Overhead volume 200,000 · cost 280,000 → $/m³ 1.40
 */
export interface EnteredLine {
  code: number;
  harvest: number;
  pop: number | null;
}

export const HAPPY_PATH_LINES: readonly EnteredLine[] = [
  { code: 27, harvest: 100_000, pop: 10_000 },
  { code: 28, harvest: 20_000, pop: 2_000 },
  { code: 29, harvest: 5_000, pop: null },
  { code: 30, harvest: 60_000, pop: 6_000 },
  { code: 31, harvest: 30_000, pop: 3_000 },
  { code: 32, harvest: 25_000, pop: 2_500 },
  { code: 33, harvest: 15_000, pop: null },
  { code: 34, harvest: 12_000, pop: 1_200 },
  { code: 35, harvest: 8_000, pop: 800 },
  { code: 36, harvest: 6_000, pop: 600 },
  { code: 37, harvest: 4_000, pop: null },
];

export const HAPPY_PATH_POP_TIMBER_VOLUME = 50_000;
export const HAPPY_PATH_CROWN_TIMBER_VOLUME = 150_000;
export const HAPPY_PATH_COMMENTS = 'E2E S01 — fixed admin lines and timber volumes.';

/** The PO&P the server derives for Scaling (33) from the two volumes above. */
export const HAPPY_PATH_SCALING_POP = 3_750;

/** The stored figures S01 reads back through the API (server-computed, never sent). */
export const HAPPY_PATH_DERIVED = {
  subtotalActualCosts: { harvest: 285_000, pop: 29_850, crown: 255_150 },
  includedUnacceptableCosts: { harvest: 5_000, pop: 0, crown: 5_000 },
  totalCosts: { harvest: 280_000, pop: 29_850, crown: 250_150 },
  popTimber: { volume: 50_000, cost: 29_850, perUnit: 0.6 },
  crownTimber: { volume: 150_000, cost: 250_150, perUnit: 1.67 },
  totalOverhead: { volume: 200_000, cost: 280_000, perUnit: 1.4 },
} as const;

// The `check-met` anchor is seeded with exactly HAPPY_PATH_LINES (every Harvest, the 8 PO&P amounts the
// check requires, and both volumes) — through the API rather than typed, because the scenario under test
// is the Check Status outcome, not the entry. No separate alias: one name for one set.

// ---------------------------------------------------------------------------------------------------
// Seeded amounts on the read-only check-status / a11y anchors
// (real-test-data-patches/sch3/draft-anchors.sql — keep the two in step)
// ---------------------------------------------------------------------------------------------------

/** Wages/Salaries on `check-harvest-pop` and `check-override`: Harvest 40,000 < PO&P 50,000. */
export const SEEDED_WAGES_VIOLATION = { harvest: 40_000, pop: 50_000 } as const;
/** The seeded other-acceptable group on `check-oa-pop`, `check-override` and `a11y`. */
export const SEEDED_OTHER_ACCEPTABLE = {
  description: 'E2E seeded group',
  total: 1_000,
  pop: 2_500,
} as const;
/** The seeded included-unacceptable row on `a11y`. */
export const SEEDED_UNACCEPTABLE = { description: 'E2E seeded unacceptable', total: 7_500 } as const;
/**
 * The deliberately INCOMPLETE sub-page rows on `check-subpage-missing`: an other-acceptable group with
 * no description and no PO&P cost, and an included-unacceptable row with no Total. Neither state can be
 * produced through the UI (the Add panel requires a description, and it always writes both rows of a
 * group), so the BR-11 sub-page checks are only reachable from seeded data.
 */
export const SEEDED_INCOMPLETE = {
  otherAcceptable: { description: null, total: 1_000, pop: null },
  unacceptable: { description: 'E2E seeded unacceptable', total: null },
} as const;

/**
 * The at-rest stored figures the four seeded anchors share (everything except the Wages line and the
 * sub-page rows). Asserted by preflight so a drifted patch fails fast.
 */
export const SEEDED_BASE_LINES: readonly EnteredLine[] = [
  { code: 27, harvest: 100_000, pop: 10_000 },
  { code: 28, harvest: 20_000, pop: 2_000 },
  { code: 29, harvest: 5_000, pop: null },
  { code: 31, harvest: 30_000, pop: 3_000 },
  { code: 32, harvest: 25_000, pop: 2_500 },
  { code: 33, harvest: 15_000, pop: null },
  { code: 34, harvest: 12_000, pop: 1_200 },
  { code: 35, harvest: 8_000, pop: 800 },
  { code: 36, harvest: 6_000, pop: 600 },
  { code: 37, harvest: 4_000, pop: null },
];
export const SEEDED_POP_TIMBER_VOLUME = 50_000;
export const SEEDED_CROWN_TIMBER_VOLUME = 150_000;

// ---------------------------------------------------------------------------------------------------
// Values the mutating scenarios write (fixed literals — each anchor is owned by one scenario, so they
// cannot collide; no Math.random()/Date.now() anywhere in this suite)
// ---------------------------------------------------------------------------------------------------

/** The row a sub-page scenario adds, and the value it is then edited to. */
export const OA_ROW = { description: 'E2E S04 group', total: 9_000, pop: 1_500 } as const;
export const OA_ROW_EDITED_TOTAL = 12_000;
export const UNACCEPTABLE_ROW = { description: 'E2E S05 row', total: 6_500 } as const;

/** The crown volumes the BR-09 scenarios change TO (they start from an at-rest null). */
export const CROWN_PUSH_VOLUME = 123_456;

/**
 * The out-of-range / boundary probes live in `validation.feature`'s own `Examples:` tables rather than
 * here: each row pairs the value with the message it must raise, so keeping a second copy in the fixture
 * would be a pinned constant no assertion reads. The bounds themselves are documented above
 * (MSG_COST_RANGE / MSG_VOLUME_RANGE).
 */
