/**
 * UC-SCH6-001 (Schedule 6 — Report Road Management Costs) pinned test data.
 * DB-grounded through the app's own API, never fabricated.
 *
 * ANCHOR SCARCITY — THE GRID IS FULL. Read this before adding the remaining slices.
 * Surveyed 2026-09-17 against the seeded local delivery DB (THE/…@localhost:1525/DBDOCK_01) using the
 * suite's OWN scanner (`preflight/anchor-keys.ts` collectAnchorKeys — NOT a fresh regex; re-deriving
 * those patterns is the dead-guard class VER-8 records), then confirmed through
 * `GET /api/v1/schedule6?millId=<m>&year=<y>`:
 *   - THE.ILCR_MILL_REPORT_STATUS holds 153 rows; opened reporting years were 2015–2023.
 *   - The other seven domains pin 151 (mill, year) keys
 *     (sch4 60, sch5 32, sch11 20, sch2 22, sch3 22, sch1 19, sec 5) against 136 Draft cells.
 *   - Draft + pinned by NOBODY: exactly TWO — 1/2017 and 14050/2018 — and BOTH answer HTTP 409
 *     (closed mills). Verified; 13050/2017 answers 200 editable with `roadRecords: []`, so the probe
 *     itself is sound.
 *   - Usable free cells: ZERO.
 *   - All TEN Draft cells that hold road records are pinned by another domain (eight by sch1), so none
 *     can be borrowed for a read-only scenario either.
 * So Schedule 6 MINTS its capacity: `real-test-data-patches/sch6/draft-anchors.sql` opens reporting
 * year 2024. Because every key any other fixture pins is <= 2023 (sch5 took 2022–2023), **"year >= 2024
 * belongs to sch6" is a STRUCTURAL invariant** rather than a convention — a cross-domain collision is
 * not even expressible. Each cell is folded into the CI seed `db-e2e/R__80_e2e_anchor_seed.sql` in the
 * SAME change (a patch not folded in does not exist in CI).
 *
 * WHY SCHEDULE 6 NEEDS NO SUMMARY ROW: a valid ACTIVE mill-year holding no road records is the
 * legitimate empty state and answers 200 `roadRecords: []`, never a 404 (Schedule6Api.java:37 — "zero
 * road records is a valid 200"). There is no category-'6' ILCR_REPORT_SUMMARY row at all, so
 * `trackStatus` comes straight from ILCR_MILL_REPORT_STATUS (Schedule6Repository.java:24-25). The
 * scarcity here is anchor EXCLUSIVITY, not missing schedule data.
 *
 * THE PATCH MUST SEED CATEGORY ROWS, and for this table that is verified rather than inherited:
 * ROAD_MAINTENANCE_REPORT carries the composite FK RM_RPT_ILCR_RCAT_FK -> ILCR_RCAT_PK, confirmed
 * ENABLED in all_constraints on 2026-09-17. With the report-status row alone the page opens but the
 * first record save fails DataIntegrityViolationException — how sch4 found it on 9050/2015 and sch5 on
 * 9050/2016.
 *
 * PARALLEL SAFETY: the suite runs `fullyParallel`, and adding a record creates a real
 * ROAD_MAINTENANCE_REPORT row, so every MUTATING scenario owns a DEDICATED (mill, year) that no other
 * scenario writes to.
 *
 * CLEANUP CONTRACT (confirmed by probe 2026-09-17 on this exact anchor):
 *   POST   /api/v1/schedule6/records?millId=9050&year=2024        -> 200 "Data saved successfully"
 *   DELETE /api/v1/schedule6/records/{recordId}?millId=&year=     -> 200
 * and the re-GET returns `roadRecords: []` with totals back at 0 — the anchor's at-rest state. That
 * DELETE is what the cleanup registry calls, so the anchor is left as found. NOTE it carries NO
 * `revisionCount`, unlike Schedule 5's camp DELETE: legacy's row Delete had no revision token either
 * (Schedule6MB.remove :208-218, deviation (c2)), so do not add one.
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch6-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch6 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

export interface Sch6Anchor {
  key: ScheduleKey;
  mill: MillRef;
}

const MILL_760: MillRef = { millNumber: '760', millName: 'WESTEROS' }; // millId 9050, ACT
const MILL_2121: MillRef = { millNumber: '2121', millName: 'SESAME STREET' }; // millId 10050, ACT
const MILL_987: MillRef = { millNumber: '987', millName: 'TURTLE DOVE' }; // millId 12050, ACT
const MILL_999: MillRef = { millNumber: '999', millName: 'ISP TEST' }; // millId 13050, ACT
const MILL_727: MillRef = { millNumber: '727', millName: 'Updated Mill E2E' }; // millId 17052, ACT
const MILL_20171: MillRef = { millNumber: '20171', millName: 'MILES MILLING' }; // millId 22050, ACT

// ---------------------------------------------------------------------------------------------------
// MUTATING anchors — one per scenario that saves. Every one is an ACT mill, trackStatus "D",
// editable:true and holds NO road records at rest; `preflight/sch6-anchors.setup.ts` asserts all of
// that, because the scenarios open by asserting a blank Add panel or an empty record list.
//
// ALL SEEDED (see the header — the extract had no usable free Draft mill-year left for ANY domain).
// ---------------------------------------------------------------------------------------------------

/** S01 — Add a Road Maintenance Record by TSA and Supply Block (Happy Path). */
export const ADD_ANCHOR: Sch6Anchor = { key: { millId: 9050, year: 2024 }, mill: MILL_760 };

/**
 * S02 — Edit an Existing Road Maintenance Record.
 *
 * EMPTY AT REST, like every other mutating anchor: the scenario's own Given creates the record it then
 * edits, through the app's own POST. Seeding a road record into the patch instead would have to be
 * mirrored into the CI seed as an explicit-id ROAD_MAINTENANCE_REPORT row PLUS its
 * ILCR_COST_REPORT_DETAIL children — and `ROAD_MAINTENANCE_REPORT_ID` is not yet a parent column in
 * `preflight/ci-seed-parity.setup.ts`, so those detail rows would be reported as parentless. Creating
 * through the API avoids all of that and is the pattern sch4's and sch5's own "empty at rest; the
 * scenarios' Givens save the state they then edit" anchors already use.
 */
export const EDIT_ANCHOR: Sch6Anchor = { key: { millId: 10050, year: 2024 }, mill: MILL_2121 };

/**
 * Every anchor the preflight asserts is an editable, record-free Draft.
 *
 * Grows with each slice. Kept as a NAMED list rather than derived from the exports so the preflight's
 * failure messages can say which slice an anchor belongs to.
 */
/** S03 — Record a TFL Instead of a TSA. Mutating; the scenario adds a record and deletes it again. */
export const TFL_ANCHOR: Sch6Anchor = { key: { millId: 12050, year: 2024 }, mill: MILL_987 };

/**
 * S04 — Enter or Update the Schedule's General Comment.
 *
 * Saving a comment on an otherwise EMPTY schedule makes the backend insert a bare BR-09 PLACEHOLDER
 * row to carry it (`Schedule6Service:425`) — there is no record to hang it on. The anchor is still
 * empty at rest, because clearing the comment when it is the only stored thing REMOVES the placeholder
 * (`Schedule6Service:428-437`, legacy `generalCommentRemovedLastRecord`), which is exactly what this
 * scenario's cleanup PUT does. So cleanup here is not a record DELETE at all.
 */
export const GENERAL_COMMENT_ANCHOR: Sch6Anchor = { key: { millId: 13050, year: 2024 }, mill: MILL_999 };

/** S05 arm 2 — the corrected TFL number is accepted and saved. Its own cell because it WRITES. */
export const TFL_CORRECTION_ANCHOR: Sch6Anchor = { key: { millId: 17052, year: 2024 }, mill: MILL_727 };

/**
 * The VALIDATE-ONLY anchor: nothing is ever saved here, which is what lets non-writing scenarios
 * SHARE it under `fullyParallel`. S05's reject arm is the first tenant; the numeric and required-field
 * rejections (S12–S16) belong here too.
 *
 * KEEP IT WRITER-FREE. The moment a scenario saves on this key it needs its own cell instead — sch5
 * learned that when its S12 correction arm started saving and had to be given one.
 */
export const VALIDATE_ONLY_ANCHOR: Sch6Anchor = { key: { millId: 22050, year: 2024 }, mill: MILL_20171 };

export const EDITABLE_DRAFT_ANCHORS: { name: string; anchor: Sch6Anchor }[] = [
  { name: 'add (S01)', anchor: ADD_ANCHOR },
  { name: 'edit (S02)', anchor: EDIT_ANCHOR },
  { name: 'tfl (S03)', anchor: TFL_ANCHOR },
  { name: 'general-comment (S04)', anchor: GENERAL_COMMENT_ANCHOR },
  { name: 'tfl-correction (S05)', anchor: TFL_CORRECTION_ANCHOR },
  { name: 'validate-only (S05, S12-S16)', anchor: VALIDATE_ONLY_ANCHOR },
];

// ---------------------------------------------------------------------------------------------------
// S01's record — every value read back from the real write path, not chosen on paper.
//
// PROVENANCE (probe 2026-09-17, POST /api/v1/schedule6/records on 9050/2024, then DELETEd):
//   { areaType: "01", supplyBlock: "01B", volume: 12500, cost: 48000 }
//     -> rmg "15", costPerVolume 3.84, totalVolume 12500, totalCost 48000, totalCostPerVolume 3.84
//   message.key dataSavedSuccesfullyInfoMsg, message.text "Data saved successfully"
//
// WHY THESE FIGURES: 48000 / 12500 is EXACTLY 3.84, so the assertion cannot be made to pass or fail by
// a rounding decision. Cost is whole-dollar on the wire anyway (roundCost), and the screen re-groups
// cost to 0 decimals on blur, so a fractional cost would render as something the field never stores.
//
// WHY TSA "01" + BLOCK "01B": the Supply Block list is FILTERED to blocks whose code starts with the
// chosen TSA (utils/codes.ts supplyBlocksFor), so the pair must be consistent or the block is not
// offered at all. RMG is SERVER-derived from the block (Schedule6Service) and is not mirrored into the
// Add panel — see the note in happy-path.feature.
// ---------------------------------------------------------------------------------------------------

/** The S01 record as the user types it, and as the API echoes it back. */
export const S01_RECORD = {
  /** Area-type code. The combo renders the code's DESCRIPTION, so the option text is `areaTypeOption`. */
  areaTypeCode: '01',
  areaTypeOption: 'Arrow TSA',
  /** Supply-block code, consistent with the TSA above (see supplyBlocksFor). */
  supplyBlockCode: '01B',
  supplyBlockOption: 'Arrow TSA Block B',
  /** Typed into the Volume m³ field; re-grouped to "12,500" on blur (volumeMask, 0 decimals). */
  volumeInput: '12500',
  volumeDisplay: '12,500',
  /** Typed into the Cost $ field; re-grouped to "48,000" on blur (moneyMask, 0 decimals). */
  costInput: '48000',
  costDisplay: '48,000',
  /** Server-derived Resource Management Grouping for block 01B. */
  rmg: '15',
  /** 48000 / 12500 = 3.84 exactly; rendered by ratioMask as ###,##0.00. */
  costPerVolumeDisplay: '3.84',
  comments: 'E2E S01 road record',
} as const;

/** The totals a single S01 record produces — one record, so they equal its own figures. */
export const S01_TOTALS = {
  volume: '12,500',
  cost: '48,000',
  costPerVolume: '3.84',
} as const;

// ---------------------------------------------------------------------------------------------------
// S02's record — created by the scenario's own Given through POST /records, then EDITED on screen and
// persisted with the page-level Save (PUT). Two sets of figures, both exact divisions so neither the
// before nor the after assertion can turn on a rounding decision:
//   seeded : 30,000 / 10,000 = 3.00
//   edited : 90,000 / 20,000 = 4.50
// Same TSA/Supply Block pair as S01 (and therefore the same server-derived RMG "15") because S02's
// subject is the AMOUNTS, not the classification — changing the area type on an existing record is
// S19's subject and deliberately not mixed in here.
// ---------------------------------------------------------------------------------------------------

/** The S02 record as its Given creates it, and as the row renders before the edit. */
export const S02_SEEDED = {
  areaTypeCode: '01',
  supplyBlockCode: '01B',
  volume: 10000,
  cost: 30000,
  volumeDisplay: '10,000',
  costDisplay: '30,000',
  costPerVolumeDisplay: '3.00',
  rmg: '15',
  comments: 'E2E S02 road record',
} as const;

/** The values S02 types over the seeded ones, and the figures they must produce. */
export const S02_EDITED = {
  volumeInput: '20000',
  costInput: '90000',
  volumeDisplay: '20,000',
  costDisplay: '90,000',
  costPerVolumeDisplay: '4.50',
} as const;

// ---------------------------------------------------------------------------------------------------
// THE TFL BRANCH (S03, S05)
//
// "TFL" is a SYNTHETIC SENTINEL the control adds to the area-type list, not a served code — the
// backend does not serve it (LookUpCacheDAO.java:229-230, mirrored by `areaTypeOptions`). Choosing it
// switches the form: the TFL number field activates and Supply Block disables, because BR-02 keeps
// exactly one side of the classification populated.
//
// WHICH TFL NUMBERS ARE VALID IS A FIXED TABLE IN CODE, not a DB lookup: RoadGroupLookup
// .rmgByTflNumberCode is a verbatim port of legacy RoadGroupUtil.setRmgByTflNumberCode, and a TFL is
// valid IFF it resolves there (Schedule6Service:625 — "iff the RMG lookup resolves it"). The accepted
// set is: 01 41 18 35 08 15 59 48 05 30 52 53 03 23 14 49 33 55 56 62.
//
// WHY 48 AND NOT 01: "48" derives RMG "10", which differs from the TSA path's "15" (block 01B), so a
// scenario that confused the two branches fails instead of passing on a coincidentally equal value.
//
// WHY 99 IS THE INVALID ONE, and NOT "42": 42 appears in the ported table as a DELIBERATELY
// commented-out case ("not used as described in TFL list v2, ILCR-161"), so a future reader could
// reasonably think it ought to resolve. 99 is in no list anywhere and cannot be mistaken for a
// regression. Both are two characters, which matters — see the note on S05 below.
// ---------------------------------------------------------------------------------------------------

/** The area-type option text for the TFL branch. The sentinel's description IS the sentinel. */
export const TFL_OPTION = 'TFL';

/** A TFL number that resolves to an RMG, with the RMG it resolves to. */
export const VALID_TFL = { number: '48', rmg: '10' } as const;

/**
 * A two-character TFL number that resolves to NOTHING, so the server rejects it.
 *
 * TWO CHARACTERS IS THE POINT. The client-side gate only catches BLANK and OVER-WIDE entries
 * (`validation.ts:170-176`) and the input is `maxLength={2}`, so a 2-char invalid code passes the
 * client untouched and is rejected by the SERVER — which is the only thing that can decide validity,
 * since "valid" means "resolves to an RMG". That is why S05's error appears on SUBMIT rather than as
 * you type, unlike legacy's ajax-validated field. See tfl-validation.feature's header.
 */
export const INVALID_TFL = { number: '99' } as const;

/** `tflNumberInvalidErrorMsg` — verbatim, and identical whether the client or the server rejects. */
export const TFL_INVALID_MESSAGE = 'Entered TFL number is not valid for Interior Regions.';

/** S03's record: the TFL branch of the happy path. 60,000 / 15,000 = 4.00 exactly. */
export const S03_RECORD = {
  tflNumber: VALID_TFL.number,
  rmg: VALID_TFL.rmg,
  volumeInput: '15000',
  costInput: '60000',
  volumeDisplay: '15,000',
  costDisplay: '60,000',
  costPerVolumeDisplay: '4.00',
  comments: 'E2E S03 TFL road record',
} as const;

/** S05 arm 2's record — the corrected TFL, saved. 25,000 / 10,000 = 2.50 exactly. */
export const S05_RECORD = {
  volumeInput: '10000',
  costInput: '25000',
  costPerVolumeDisplay: '2.50',
  comments: 'E2E S05 corrected TFL record',
} as const;

// ---------------------------------------------------------------------------------------------------
// S04 — the schedule-level GENERAL COMMENT.
//
// A DIFFERENT COLUMN from the per-record comment, and the two caps differ: the general comment lands
// in ROAD_MAINTENANCE_REPORT.COMMENTS (4000 wide, capped at 3500 in the UI) while a record's comment
// lands in ILCR_COST_REPORT_DETAIL.COMMENTS (400). Conflating them is easy and wrong (deviation E).
// ---------------------------------------------------------------------------------------------------

/** The text S04 enters. Deliberately unlike any record comment so a cleanup cannot confuse the two. */
export const S04_GENERAL_COMMENT =
  'E2E S04 schedule-level general comment for the reporting year.';

/** The General Comments textarea's id (components/schedule6/index.tsx:1061). */
export const GENERAL_COMMENTS_FIELD = '#general-comments';

// ---------------------------------------------------------------------------------------------------
// THE THREE CONTEXT GUARDS (S06, S07, S08)
//
// None of them writes anything, so none needs a mutating anchor. S06 needs no anchor at all — it is
// about the absence of a working context, which lives in the browser.
//
// The messages come from the SHARED `core/ScheduleLoadState`, so most of them are identical across
// schedules; only the generic load-failure title carries the schedule's own name
// (`scheduleName: 'Schedule 6'`, index.tsx:576).
// ---------------------------------------------------------------------------------------------------

export const GUARD_MESSAGES = {
  /**
   * ERR-001 — the CLIENT-ONLY banner (`ERR_MILL_YEAR_NOT_SELECTED`,
   * components/core/ScheduleLoadState/index.tsx:12).
   *
   * NOTE THE MISSING TRAILING SPACE. The legacy Gherkin quotes
   * "Please Select Mill and Reporting Year in the Home Page. " WITH one, because that is the SERVER's
   * ERR-001 literal. This guard never reaches the server — the page suppresses the request entirely —
   * so what renders is the client literal, which has no trailing space by sibling convention
   * (index.tsx:52-55 says so explicitly). The server's spaced version still renders verbatim when a
   * request genuinely returns it.
   */
  millYearNotSelected: 'Please Select Mill and Reporting Year in the Home Page.',
  /** Its notification TITLE. Severity is carried by a word, never by colour alone (WCAG 2.1 AA). */
  millYearNotSelectedTitle: 'Mill and Reporting Year required',
  /** ERR-002 — the 409 detail, served by the API and echoed unchanged. */
  millNotActive:
    'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  /**
   * ERR-002's own TITLE. A mill closed for the reporting year is a CONTEXT the reporter changes on the
   * Home page, not the app failing to load, so ScheduleLoadState titles it separately rather than
   * letting it fall through to the generic failure below.
   */
  millNotActiveTitle: 'Mill not active for Reporting Year',
  /** ERR-003 — the 404 detail. */
  scheduleNotFound: 'Schedule not found.',
  /**
   * The GENERIC load-failure title, which ERR-003 still renders under — but ERR-002 does NOT.
   * Asserting its ABSENCE on the closed-mill guard is what keeps the two framings apart: the detail
   * alone reads identically either way.
   */
  loadFailedTitle: 'Unable to load Schedule 6',
} as const;

const MILL_11: MillRef = {
  millNumber: '11',
  millName: 'EVANS FOR. PROD. (DIV. OF LOUISIANA PACIFIC)',
}; // millId 1, CLS

const MILL_20173: MillRef = { millNumber: '20173', millName: 'TOMTESTMILL042017' }; // millId 23050, ACT

/**
 * S07 — a mill that is CLOSED for the reporting year, so the document GET answers 409.
 *
 * REUSED FROM THE EXTRACT RATHER THAN MINTED. 1/2017 is one of only two Draft cells the whole suite
 * left unpinned (see the header survey), and it is unusable as a mutating anchor for exactly the
 * reason that makes it perfect here: mill 1 is CLS, so it answers 409. Verified 2026-09-17 through the
 * API, detail byte-for-byte.
 *
 * NOT minted in 2024, deliberately: opening 2024 for a closed mill would mean adding a report-status
 * row for a CLS mill, and flipping any mill's ILCR_MILL_STATUS_XREF is what sch5's patch header warns
 * would silently redden the closed-mill guards sch2/sch3/sch4/sch5 already pin. Reusing a cell that is
 * ALREADY closed touches nothing.
 *
 * Mill 1 is seeded in the CI seed with its CLS xref; only the 2017 report-status row had to be added
 * (a row must EXIST for the year, or MillContextService answers 404 first and the 409 is never
 * reached — the same trap sch3's own comment records for 1/2016).
 */
export const CLOSED_MILL_ANCHOR: Sch6Anchor = { key: { millId: 1, year: 2017 }, mill: MILL_11 };

/**
 * S08 — an ACTIVE mill with NO Schedule 6 report-status row, so the GET answers 404.
 *
 * THE FIXTURE IS THE ABSENCE. 23050/2024 is a hole CARVED in sch6's own minted year: the patch opens
 * 2024 for six mills and deliberately skips this one, so a 404 anchor exists inside the range sch6
 * controls. Seeding it would DELETE the fixture, not fix it — which is why it is registered in
 * DELIBERATELY_ABSENT in `preflight/ci-seed-parity.setup.ts`, whose reverse check fails if anyone ever
 * gives it a row. Same construction as sch5's 16050/2022.
 */
export const NO_SCHEDULE_ANCHOR: Sch6Anchor = { key: { millId: 23050, year: 2024 }, mill: MILL_20173 };

/** The two guard anchors keyed for the step, with the status each must still answer. */
export const GUARDS: Record<string, { anchor: Sch6Anchor; expectHttp: number; detail: string }> = {
  'closed-mill': {
    anchor: CLOSED_MILL_ANCHOR,
    expectHttp: 409,
    detail: GUARD_MESSAGES.millNotActive,
  },
  'not-found': {
    anchor: NO_SCHEDULE_ANCHOR,
    expectHttp: 404,
    detail: GUARD_MESSAGES.scheduleNotFound,
  },
};

// ---------------------------------------------------------------------------------------------------
// Verbatim app messages. Rendered from the API's `message.text` / ProblemDetail.detail (AD-8), so these
// are transcriptions of what the server sends, confirmed by the probes above — never invented copy.
// ---------------------------------------------------------------------------------------------------

/** `dataSavedSuccesfullyInfoMsg` — the add/save success banner. */
export const DATA_SAVED = 'Data saved successfully';

/** `scheduleRequirementsMetMsg`. NO trailing period — confirmed byte-for-byte from the endpoint. */
export const REQUIREMENTS_MET = 'All requirements for this schedule have been met';

/** PrimeFaces' default empty-list text, which legacy inherited (schedule6.xhtml:459-464). */
export const EMPTY_LIST = 'No records found.';

/** The Add panel's heading and aria-label (ADD_PANEL_HEADING in components/schedule6/index.tsx). */
export const ADD_PANEL_HEADING = 'Add Road Maintenance report';

// ---------------------------------------------------------------------------------------------------
// URLs and small helpers — the one place each request shape is spelled out.
// ---------------------------------------------------------------------------------------------------

/** The Schedule 6 read endpoint for a (mill, year). */
export const scheduleUrl = (millId: number, year: number): string =>
  `/api/v1/schedule6?millId=${millId}&year=${year}`;

/** The add endpoint — `Add Report` posts immediately (add-is-save). */
export const addRecordUrl = (millId: number, year: number): string =>
  `/api/v1/schedule6/records?millId=${millId}&year=${year}`;

/**
 * The record DELETE the cleanup registry calls.
 *
 * Deliberately NO `revisionCount` parameter: this endpoint carries no revision token (legacy's row
 * Delete had none — Schedule6MB.remove :208-218, deviation (c2)). Adding one would be inventing a
 * contract.
 */
export const recordDeleteUrl = (recordId: number, millId: number, year: number): string =>
  `/api/v1/schedule6/records/${recordId}?millId=${millId}&year=${year}`;

/** The Check Status endpoint. Takes the ON-SCREEN values, not the stored ones (Schedule6CheckRequest). */
export const checkStatusUrl = (millId: number, year: number): string =>
  `/api/v1/schedule6/check-status?millId=${millId}&year=${year}`;

/** Carbon Dropdown option text for a mill — mirrors Home's `millItemToString` ("760 - WESTEROS"). */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

/** The in-memory MillYearContext localStorage key (context/millYear/MillYearProvider.tsx). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

// ---------------------------------------------------------------------------------------------------
// Stable control ids. The legacy Gherkin names JSF ids (`schedule6AddForm:vol`); the React page builds
// every field id from an `idPrefix` (components/schedule6/index.tsx:247-349), which is `add` for the
// Add panel and `row-<recordId>` for a record's row.
//
// THESE IDS ARE WHY THE PAGE OBJECT DOES NOT USE getByLabel. The Add panel and EVERY row render the
// same six labels ("TSA or TFL", "TFL", "Supply Block", "Volume m³", "Cost $", "Comments"), so a
// label-based locator is a strict-mode violation the moment one record exists — the class that cost
// sch5 a latent violation found in review.
// ---------------------------------------------------------------------------------------------------

/** Field ids inside the Add panel. */
export const ADD_FIELD = {
  areaType: '#add-area-type',
  tflNumber: '#add-tfl-number',
  supplyBlock: '#add-supply-block',
  volume: '#add-volume',
  cost: '#add-cost',
  comments: '#add-comments',
} as const;

/** Field ids inside a saved record's row, by `ROAD_MAINTENANCE_REPORT_ID`. */
export const rowField = (recordId: number) =>
  ({
    areaType: `#row-${recordId}-area-type`,
    tflNumber: `#row-${recordId}-tfl-number`,
    supplyBlock: `#row-${recordId}-supply-block`,
    volume: `#row-${recordId}-volume`,
    cost: `#row-${recordId}-cost`,
    comments: `#row-${recordId}-comments`,
  }) as const;
