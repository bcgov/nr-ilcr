/**
 * UC-SCH2-001 (Schedule 2 — Report Purchased and Private Log Costs and Sales) pinned test data.
 * DB-grounded through the app's own API, never fabricated.
 *
 * Anchors discovered + confirmed 2026-08-13 against the seeded local delivery DB
 * (THE/…@localhost:1525/DBDOCK_01) by enumerating `GET /api/v1/mills`, then probing
 * `GET /api/v1/schedule2?millId=<m>&year=<y>` over every mill × reporting year 2010–2026
 * (357 probes, 21 mills) and classifying each response by trackStatus / editable / revisionCount.
 *
 * Survey result (111 mill/year rows carrying a Schedules 1–10 track):
 *   - 86 editable Draft rows with NO Schedule 2 summary yet (revisionCount absent) → the unsaved anchors
 *   - 12 editable Draft rows that already hold a saved Schedule 2
 *   - 13 non-Draft rows (Submitted "S" / Verified "V") → the read-only anchors
 *   - only 4 unsaved editable rows also carry CARRIED Schedule 3 figures → the derived-figure anchors
 *
 * CROSS-SCHEDULE COUPLING — the reason the keys below avoid the sch1 suite's keys, and why that is a
 * HARD requirement here rather than defence in depth:
 * Schedule 2's `totalCompanyLogging` is computed from SCHEDULE 1's own figures, and
 * `purchasedLogCost.volume` / `purchasedWoodOverhead` are carried from SCHEDULE 3
 * (Schedule2Service javadoc). So a sch1 scenario writing to the same (mill, year) would move the very
 * numbers a Schedule 2 derived-figure assertion pins — a genuine cross-domain flake, not a theoretical
 * one. Every anchor below is on a mill the sch1, sch11 and sec suites never touch:
 *   sch2 owns mills 22050 (#20171), 23051 (#20174), 23052 (#20176), 25053 (#9174).
 *
 * PARALLEL SAFETY: the suite runs `fullyParallel`, and a Schedule 2 save creates a real category-"2"
 * summary row, so every MUTATING scenario owns a DEDICATED (mill, year) that no other scenario writes
 * to. The validate-only / render-state anchors are deliberately NOT any of the mutating keys — those
 * scenarios prove a write is BLOCKED, so nothing is ever persisted there.
 *
 * CLEANUP CONTRACT: Schedule 2 has no per-row sub-resource — the whole schedule is one summary plus its
 * two detail rows — so a scenario that saves is undone by `DELETE /api/v1/schedule2`, which restores the
 * anchor to its at-rest unsaved state (revisionCount absent). Confirmed by probe on 2026-08-13:
 * PUT → revisionCount 1, DELETE → 200, re-GET → byte-identical to the pre-probe document.
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch2-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch2 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

export interface Sch2Anchor {
  key: ScheduleKey;
  mill: MillRef;
}

const MILL_20171: MillRef = { millNumber: '20171', millName: 'MILES MILLING' }; // millId 22050, ACT
const MILL_20174: MillRef = { millNumber: '20174', millName: 'AO CUSTOM' }; // millId 23051, ACT
const MILL_20176: MillRef = { millNumber: '20176', millName: 'TANNER LOGS' }; // millId 23052, ACT
const MILL_9174: MillRef = { millNumber: '9174', millName: 'AOLSON-TEST' }; // millId 25053, ACT

// ---------------------------------------------------------------------------------------------------
// MUTATING anchors — one per scenario that saves. All confirmed ACT mill, trackStatus "D",
// editable:true and NO Schedule 2 summary at rest (revisionCount absent). `preflight/sch2-anchors.setup.ts`
// asserts that unsaved-at-rest state, because the "empty document" and derived-figure assertions depend
// on it.
// ---------------------------------------------------------------------------------------------------

/**
 * S01 — the happy path, and the ONLY anchor whose derived arithmetic is asserted end-to-end.
 *
 * Chosen because it is one of just four unsaved editable Draft rows that also carry real Schedule 3
 * figures, so Subtotal / Net Purchased / Total Average are genuinely computed rather than all-zero.
 * At rest (confirmed 2026-08-13): purchasedLogCost.volume 10, purchasedWoodOverhead {volume 10, cost 0},
 * totalCompanyLogging {volume 10, cost 10}.
 */
export const HAPPY_PATH_ANCHOR: Sch2Anchor = { key: { millId: 22050, year: 2016 }, mill: MILL_20171 };

/** S02 — save once, reopen, change the values and save again (update-over-insert). */
export const UPDATE_ANCHOR: Sch2Anchor = { key: { millId: 22050, year: 2017 }, mill: MILL_20171 };

/** S03 — save with the Purchased/Private Log Costs cost left blank (accepted; Check Status catches it). */
export const BLANK_COST_ANCHOR: Sch2Anchor = { key: { millId: 23051, year: 2016 }, mill: MILL_20174 };

/** S04 — save with both (less) Log Sales fields left blank. */
export const BLANK_SALES_ANCHOR: Sch2Anchor = { key: { millId: 23051, year: 2017 }, mill: MILL_20174 };

/** S05 — save, then delete behind the confirm modal and land back on the empty editable document. */
export const DELETE_ANCHOR: Sch2Anchor = { key: { millId: 23051, year: 2018 }, mill: MILL_20174 };

/**
 * BR-07's saved-but-incomplete state: a schedule that HAS been saved yet carries no item-25 cost, so
 * Check Status still reports the issue. This is the observable follow-on of S03 and the reason BR-07
 * exists as a separate check rather than a Save-time requirement. It is a write (the state is seeded),
 * so it owns its own key rather than borrowing the delete anchor.
 */
export const SAVED_INCOMPLETE_ANCHOR: Sch2Anchor = {
  key: { millId: 23051, year: 2020 },
  mill: MILL_20174,
};

/** S07 — save an item-25 cost, then Check Status reports all requirements met. */
export const CHECK_MET_ANCHOR: Sch2Anchor = { key: { millId: 23051, year: 2019 }, mill: MILL_20174 };

/** Persistence — a saved schedule must survive a full page reopen (not merely a client-state repaint). */
export const PERSIST_ANCHOR: Sch2Anchor = { key: { millId: 23052, year: 2017 }, mill: MILL_20176 };

/**
 * S17/S18 — BR-12 / #359: Check Status must judge what is on screen, not the last saved schedule.
 *
 * SEEDED, not discovered. Both scenarios save their own precondition, so each needs a mill-year no other
 * scenario writes to — and the extract has none left: 114 (mill, year) keys are already pinned across the
 * six fixtures, Home only offers reporting years 2015-2021, and every unclaimed openable pair in that range
 * is non-Draft (which disables Check Status). `real-test-data-patches/sch2/unsaved-check-anchors.sql`
 * therefore creates these two, and that file's header records the measurement.
 *
 * Reusing `check-met` / `saved-incomplete` was tried FIRST and is not safe: their Givens seed through the
 * API, so a second scenario on either collides with S07/S08 under `fullyParallel` — observed as four red
 * tests on 2026-08-27.
 */
export const CHECK_UNSAVED_VIOLATION_ANCHOR: Sch2Anchor = {
  key: { millId: 23052, year: 2015 },
  mill: MILL_20176,
};
export const CHECK_UNSAVED_FIX_ANCHOR: Sch2Anchor = {
  key: { millId: 23052, year: 2016 },
  mill: MILL_20176,
};

/**
 * Legacy rendered Save and Check Status TWICE (a top and a bottom action bar) and the rewrite keeps
 * both, so the bottom Save must genuinely save. That is a write, so it owns its own key rather than
 * sharing the persistence anchor.
 */
export const BOTTOM_BAR_ANCHOR: Sch2Anchor = { key: { millId: 23052, year: 2020 }, mill: MILL_20176 };

/** Cancelling the delete confirmation must leave the saved schedule untouched. */
export const CANCEL_DELETE_ANCHOR: Sch2Anchor = {
  key: { millId: 23052, year: 2019 },
  mill: MILL_20176,
};

// ---------------------------------------------------------------------------------------------------
// NON-MUTATING anchors — nothing is ever written to these. Each scenario using them proves a write is
// blocked (client-side validation, a disabled control, an intercepted failure) or only reads.
// ---------------------------------------------------------------------------------------------------

/** S06 — a never-saved schedule: Delete must not be available (BR-08's other side). */
export const DELETE_UNAVAILABLE_ANCHOR: Sch2Anchor = {
  key: { millId: 25053, year: 2017 },
  mill: MILL_9174,
};

/** S08 — Check Status with the item-25 cost absent → ISSUES. Check Status mutates nothing by contract. */
export const CHECK_MISSING_ANCHOR: Sch2Anchor = {
  key: { millId: 25053, year: 2018 },
  mill: MILL_9174,
};

/** S13–S16 — client-side range/format rejection. Save is blocked, so nothing reaches the server. */
export const VALIDATION_ANCHOR: Sch2Anchor = { key: { millId: 25053, year: 2019 }, mill: MILL_9174 };

/** S12 — the PUT is intercepted and failed in the browser, so no write ever reaches the database. */
export const SAVE_ERROR_ANCHOR: Sch2Anchor = { key: { millId: 25053, year: 2020 }, mill: MILL_9174 };

/**
 * S12's recovery arm — the first Save is failed at the browser edge, the retry reaches the real backend
 * and succeeds. That second save is a genuine write, so it owns its own key rather than sharing the
 * save-error anchor (whose scenario asserts NOTHING was stored, and would fail if a parallel retry wrote
 * to the same schedule).
 */
export const RETRY_ANCHOR: Sch2Anchor = { key: { millId: 23052, year: 2021 }, mill: MILL_20176 };

/** Accessibility sweep of the EDITABLE page (form + table + comments), values typed but never saved. */
export const A11Y_ANCHOR: Sch2Anchor = { key: { millId: 23052, year: 2018 }, mill: MILL_20176 };

/**
 * S11 — the schedule is not editable outside Draft. Both non-Draft codes are pinned so the read-only
 * render is proven from BOTH sides of the mirror rather than just one (symmetry check).
 *
 * Values at rest (confirmed 2026-08-13) — asserted verbatim by the read-only render scenarios, which is
 * why they are pinned here rather than read from the document under test:
 *   submitted 25053/2016 → item25 cost 500 / volume 10, item26 volume 9 / cost 450, comments absent
 *   verified  23051/2015 → item25 cost 300 / volume 3,000, item26 volume 300 / cost 300, comments absent
 */
export const READ_ONLY_ANCHORS: Record<
  'submitted' | 'verified',
  Sch2Anchor & {
    trackStatus: string;
    /**
     * The STORED item-25/26 numbers the `@S11` outline asserts verbatim on screen. Pinned so
     * `preflight/sch2-anchors.setup.ts` can prove they have not drifted before a browser opens — these
     * come from another schedule's data set and are not written by this suite, so a re-extract or a
     * hand-edit would otherwise surface as a confusing mid-scenario table mismatch.
     */
    stored: {
      item25Volume: number;
      item25Cost: number;
      item26Volume: number;
      item26Cost: number;
    };
  }
> = {
  submitted: {
    key: { millId: 25053, year: 2016 },
    mill: MILL_9174,
    trackStatus: 'S',
    stored: { item25Volume: 10, item25Cost: 500, item26Volume: 9, item26Cost: 450 },
  },
  verified: {
    key: { millId: 23051, year: 2015 },
    mill: MILL_20174,
    trackStatus: 'V',
    stored: { item25Volume: 3000, item25Cost: 300, item26Volume: 300, item26Cost: 300 },
  },
};

/**
 * Context-guard anchors — the GET itself fails, so the page renders a block message instead of a form.
 *
 * `not-found` is a DELIBERATE ADDITION to the legacy slice catalogue, not a transcription of it. The
 * legacy UC excluded ERR-004 ("Schedule not found.") as unreachable — legacy traced
 * `Schedule2DAO.getReportSummaryID()` as never returning null. In the REWRITE it is plainly reachable:
 * `MillContextService.validateMillYearActive` 404s when the mill/year carries no report-status row at
 * all. Confirmed 2026-08-13: GET schedule2 mill 13 / 2016 → HTTP 404 "Schedule not found.".
 * See coverage.md (re-grounding gains).
 */
export const GUARD_ANCHORS: Record<string, Sch2Anchor & { expectHttp: number; detail: string }> = {
  // S10 — mill 13 is CLS (closed) for 2017 → GET 409, ERR-002 rendered verbatim.
  'closed-mill': {
    key: { millId: 13, year: 2017 },
    mill: { millNumber: '45', millName: 'GORMAN BROS. - DOWNIE STREET' },
    expectHttp: 409,
    detail:
      'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  },
  // Mill 13 for 2016 has NO ILCR_MILL_REPORT_STATUS row → GET 404.
  'not-found': {
    key: { millId: 13, year: 2016 },
    mill: { millNumber: '45', millName: 'GORMAN BROS. - DOWNIE STREET' },
    expectHttp: 404,
    detail: 'Schedule not found.',
  },
};

// ---------------------------------------------------------------------------------------------------
// URLs + helpers
// ---------------------------------------------------------------------------------------------------

/** The Schedule 2 aggregate endpoint for a (mill, year) — GET read-back / PUT save / DELETE cleanup. */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule2?millId=${millId}&year=${year}`;

/** The read-only Check Status evaluation endpoint (POST, no body — mutates nothing). */
export const checkStatusUrl = (millId: number, year: number): string =>
  `/api/v1/schedule2/check-status?millId=${millId}&year=${year}`;

/** The Home Mill-dropdown option text for a mill ("20171 - MILES MILLING"). */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

/** The in-memory MillYearContext localStorage key (context/millYear/MillYearProvider.tsx). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

// ---------------------------------------------------------------------------------------------------
// Verbatim contract strings.
//
// The success / Check Status / guard strings are API-OWNED (AD-8): the backend resolves them from
// `messages.properties` and the frontend renders `message.text` / `ProblemDetail.detail` verbatim,
// never hardcoding them. Pinning them here is what makes the specs a contract test of that wiring —
// if the bundle text changes, these fail rather than silently drifting.
//
// The CLIENT-side strings are marked as such: they have no request behind them (a suppression, a
// confirm dialog, an advisory pre-flight gate), so components/schedule2/index.tsx and
// components/schedule2/validation.ts are their source of truth.
// ---------------------------------------------------------------------------------------------------

/** API-owned success/status text (backend `messages.properties`). */
export const MSG = {
  /** SUC-001 — `dataSavedSuccesfullyInfoMsg`. */
  saved: 'Data saved successfully',
  /** `dataDeletedSuccesfullyInfoMsg`. */
  deleted: 'Data deleted successfully',
  /** SUC-003 — `scheduleRequirementsMetMsg` (Check Status, outcome MET). */
  requirementsMet: 'All requirements for this schedule have been met',
} as const;

/**
 * FLD-004 — Check Status with the item-25 cost missing. The controller prefixes the resolved
 * `missingRequiredFieldMsg` ("Value Required") with the legacy field label, so the rendered line is
 * "<label>: Value Required" (Schedule2Controller:88-95, Schedule2Service LABEL_PURCHASED_LOG_COST).
 */
export const MISSING_COST_MESSAGE = 'Purchased/Private Log Costs - Cost: Value Required';

/**
 * FLD-001/002/003 — the range messages. CLIENT-side advisory copies that deliberately mirror the
 * backend bundle word for word (components/schedule2/validation.ts VALIDATION_MESSAGES).
 */
export const FLD = {
  /** FLD-001 — item 25 cost, default costValidator range. */
  item25Cost: 'Entered cost must be between -99,999,999 and 99,999,999.',
  /** FLD-002 — item 26 volume, volumeValidator range (min 0, NOT signed). */
  item26Volume: 'Entered volume must be between 0 and 9,999,999.',
  /** FLD-003 — item 26 cost, costValidator with costSize="9" (the wider range). */
  item26Cost: 'Entered cost must be between -999,999,999 and 999,999,999.',
  /** A cost that is not a signed integer (costs are whole dollars — legacy costConverter). */
  costInvalid: 'Entered cost is invalid.',
} as const;

/** CLIENT-side chrome — no request behind it (components/schedule2/index.tsx). */
export const CLIENT = {
  /** ERR-001 — the mill/year suppression banner. */
  millYearNotSelected: 'Please Select Mill and Reporting Year in the Home Page.',
  millYearNotSelectedTitle: 'Mill and Reporting Year required',
  /** The Carbon confirm-delete modal body. */
  confirmDelete: 'This will delete the current record. Do you want to continue?',
  /** Title of the notification the page shows when the delete request itself fails. */
  couldNotDelete: 'Unable to delete Schedule 2.',
  /** The advisory gate that blocks a doomed Save round-trip when a field is invalid. */
  correctBeforeSaving: 'Please correct the highlighted fields before saving.',
  /** The same gate on Check Status (legacy Check Status was validateClient="true"). */
  correctBeforeChecking: 'Please correct the highlighted fields before checking status.',
  /** ERR-003 — the Save-failed fallback when the API returns no ProblemDetail.detail. */
  couldNotSave: 'Schedule could not be saved.',
  /** Notification titles (severity is carried by an explicit word, never colour alone — WCAG 2.1 AA). */
  titleSuccess: 'Success',
  titleActionFailed: 'Action failed',
  titleCheckStatus: 'Check Status',
  titleLoadFailed: 'Unable to load Schedule 2',
} as const;

// ---------------------------------------------------------------------------------------------------
// The happy-path arithmetic (S01), pinned as DISPLAY strings.
//
// These are the values the page actually renders — `fmtNumber` (en-CA grouped) for the Volume/Cost
// columns and `fmtCurrency` (grouped, exactly 2 decimals) for $/m³ (utils/number.ts). They are recorded
// from a REAL probe of the anchor on 2026-08-13, not predicted:
//   PUT {purchasedLogCostCost: 50000, lessLogSalesVolume: 4, lessLogSalesCost: 1000}
//   → purchasedLogCost {volume 10, cost 50000, perUnit 5000}
//     subtotal        {volume 10, cost 50000, perUnit 5000}      (overhead cost is 0 on this anchor)
//     lessLogSales    {volume 4,  cost 1000,  perUnit 250}
//     netPurchased    {volume 6,  cost 49000, perUnit 8166.6667} (subtotal − log sales)
//     totalCompanyLogging {volume 10, cost 10, perUnit 1}        (carried from Schedule 1/3, unchanged)
//     totalAverage    {volume 16, cost 49010, perUnit 3063.125}  (net purchased + total company)
// The anchor was restored with DELETE immediately afterwards and re-GET matched the pre-probe document.
//
// Asserting these PROVES the server recomputed the derived blocks from the entered values — the whole
// point of BR-06 — rather than merely proving a success toast appeared.
// ---------------------------------------------------------------------------------------------------

export const HAPPY_PATH_ENTRY = {
  purchasedLogCostCost: '50000',
  lessLogSalesVolume: '4',
  lessLogSalesCost: '1000',
  comments: 'E2E happy path — Schedule 2',
} as const;

/** Row label → [Volume, Cost, $/m³] as DISPLAYED once HAPPY_PATH_ENTRY is saved. */
export const HAPPY_PATH_DISPLAY: Record<string, [string, string, string]> = {
  'Purchased/Private Log Costs:': ['10', '50,000', '5,000.00'],
  'Purchased/Private Wood Overhead:': ['10', '0', '0.00'],
  'Subtotal:': ['10', '50,000', '5,000.00'],
  '(less) Log Sales:': ['4', '1,000', '250.00'],
  'Net Purchased/Private Log Cost:': ['6', '49,000', '8,166.67'],
  'Total Company Logging Costs(Sch 1):': ['10', '10', '1.00'],
  'Total Average Logging Costs:': ['16', '49,010', '3,063.13'],
};

/** The same anchor's AT-REST document (nothing saved) — what the page shows before any entry. */
export const HAPPY_PATH_AT_REST: Record<string, [string, string, string]> = {
  'Purchased/Private Log Costs:': ['10', '', '—'],
  'Purchased/Private Wood Overhead:': ['10', '0', '0.00'],
  'Subtotal:': ['10', '0', '0.00'],
  '(less) Log Sales:': ['', '', '—'],
  'Net Purchased/Private Log Cost:': ['10', '0', '0.00'],
  'Total Company Logging Costs(Sch 1):': ['10', '10', '1.00'],
  'Total Average Logging Costs:': ['20', '10', '0.50'],
};

/** Out-of-range values that trip each validator (one just past each documented bound). */
export const OUT_OF_RANGE = {
  item25Cost: '100000000', // 100,000,000 > 99,999,999
  item26Volume: '10000000', // 10,000,000 > 9,999,999
  item26VolumeNegative: '-1', // volume is unsigned — min 0
  item26Cost: '1000000000', // 1,000,000,000 > 999,999,999
  costFraction: '50.5', // costs are whole dollars
} as const;

/**
 * The Carbon confirm-delete Modal — NOT a PrimeFaces `confirmDialog` (`.ui-confirmdialog-yes`) as the
 * legacy Gherkin describes, and not a native browser dialog (components/schedule2/index.tsx).
 */
export const CONFIRM_DELETE = {
  heading: 'Delete schedule',
  body: CLIENT.confirmDelete,
  primary: 'Delete',
  secondary: 'Cancel',
} as const;

/** The three action buttons, which the page renders TWICE (a top and a bottom action bar). */
export const ACTION = {
  save: 'Save',
  checkStatus: 'Check Status',
  delete: 'Delete',
} as const;

/** The editable field ids (components/schedule2/index.tsx) — used for Carbon inline-error scoping. */
export const FIELD_ID = {
  item25Cost: '#purchasedLogCostCost',
  item26Volume: '#lessLogSalesVolume',
  item26Cost: '#lessLogSalesCost',
  comments: '#comments',
} as const;
