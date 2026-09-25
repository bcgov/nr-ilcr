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
const MILL_20172: MillRef = { millNumber: '20172', millName: 'COVEY CUSTOM CUT' }; // millId 22051, ACT
const MILL_20174: MillRef = { millNumber: '20174', millName: 'AO CUSTOM' }; // millId 23051, ACT
const MILL_20176: MillRef = { millNumber: '20176', millName: 'TANNER LOGS' }; // millId 23052, ACT
const MILL_7777: MillRef = { millNumber: '7777', millName: 'CGT TEST MILL7' }; // millId 24050, ACT
const MILL_8888: MillRef = { millNumber: '8888', millName: 'CGI TEST MILL8' }; // millId 24051, ACT
const MILL_9171: MillRef = { millNumber: '9171', millName: 'BCOVEY-TEST' }; // millId 25050, ACT
const MILL_9173: MillRef = { millNumber: '9173', millName: 'MRICE-TEST' }; // millId 25052, ACT
const MILL_9174: MillRef = { millNumber: '9174', millName: 'AOLSON-TEST' }; // millId 25053, ACT
const MILL_514: MillRef = { millNumber: '514', millName: 'AAA MILLING' }; // millId 16050, ACT
const MILL_9175: MillRef = { millNumber: '9175', millName: 'TCASEY-TEST' }; // millId 25054, ACT

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

/** S09 — Check Status reports a missing cost. Mutating: the Given saves a cost-less record. */
export const CHECK_MISSING_COST_ANCHOR: Sch6Anchor = { key: { millId: 22051, year: 2024 }, mill: MILL_20172 };

/**
 * S10 — Check Status reports a missing TFL number.
 *
 * Mutating, and it reaches its state in TWO steps for a reason worth knowing: a TFL record with no TFL
 * number CANNOT be saved (the add endpoint answers 400 FLD-002 — verified by probe). So the Given saves
 * a VALID TFL record and the scenario then blanks the field on screen without saving. Check Status
 * evaluates the on-screen payload, so the verdict describes that.
 */
export const CHECK_MISSING_TFL_ANCHOR: Sch6Anchor = { key: { millId: 23051, year: 2024 }, mill: MILL_20174 };

/**
 * S11 — Check Status reports a missing Supply Block.
 *
 * A TSA record with no Supply Block IS savable (verified by probe): a missing Supply Block is a Check
 * Status finding, never a save failure — only its width is enforced on write. So this one is a stored
 * state, unlike S10.
 */
export const CHECK_MISSING_SUPPLY_BLOCK_ANCHOR: Sch6Anchor = { key: { millId: 23052, year: 2024 }, mill: MILL_20176 };

/**
 * S12 arm 2 — the area type is selected and the record then SAVES.
 *
 * Its own cell for the one reason that recurs through this fixture: a writer cannot share a
 * (mill, year) under `fullyParallel`. S12 is the ONLY slice in the S12-S16 validation block that
 * saves anything — the four numeric slices reject client-side and their correction arms only prove the
 * $ / m³ recomputes, so all of those stay on the shared validate-only cell. Exactly the split S05 made
 * (and sch5 before it, for its own S12).
 *
 * MINTED 2026-09-18, the tenth cell in sch6's 2024 range. Mill 24050 ("7777 CGT TEST MILL7") is ACT and
 * already seeded with its ACT xref for sch1/sch4/sch5, which pin it at 2016-2023 — so the MILL is one
 * the suite already exercises and only the YEAR is new. Confirmed free before minting with the suite's
 * own scanner (`preflight/anchor-keys.ts` collectAnchorKeys over every domain fixture): the only
 * 2024-or-later keys anywhere are sch6's own, so "year >= 2024 belongs to sch6" still holds
 * structurally. `GET /api/v1/schedule6?millId=24050&year=2024` answered 404 before the patch, i.e. the
 * mill resolves and only the report-status row was missing.
 */
export const AREA_TYPE_CORRECTION_ANCHOR: Sch6Anchor = { key: { millId: 24050, year: 2024 }, mill: MILL_7777 };

/**
 * S18 — the schedule stores ONLY a general comment.
 *
 * Empty at rest, and for the same reason S04's anchor is: the comment lives on a bare BR-09
 * PLACEHOLDER row, and clearing the comment when it is the only stored thing removes the placeholder
 * again (`Schedule6Service:428-437`). So its cleanup is the comment-clearing PUT, not a record DELETE.
 * Its own cell rather than S04's because both WRITE, and a writer cannot share a (mill, year) under
 * `fullyParallel`.
 */
export const COMMENT_ONLY_ANCHOR: Sch6Anchor = { key: { millId: 25050, year: 2024 }, mill: MILL_9171 };

/** S19 — reclassify an existing record from TSA to TFL. An ordinary record writer. */
export const RECLASSIFY_ANCHOR: Sch6Anchor = { key: { millId: 25052, year: 2024 }, mill: MILL_9173 };

/**
 * S20 — Check Status mixed results, which needs TWO records on ONE cell.
 *
 * THIS IS WHY IT COULD NOT BORROW ANY EXISTING ANCHOR, and the note is worth keeping: the per-record
 * "met" line is emitted only when the SCHEDULE fails while some individual record passes, so the state
 * requires at least two records that disagree. Every other anchor is asserted to hold NO records at
 * rest, and two scenarios writing to one cell races under `fullyParallel`. Still empty at rest — the
 * scenario's own Given creates both records through the app's POST and cleans both up by comment.
 */
export const MIXED_CHECK_ANCHOR: Sch6Anchor = { key: { millId: 25053, year: 2024 }, mill: MILL_9174 };

/** S21 — one record missing BOTH its supply block and its cost. */
export const MULTI_MISSING_ANCHOR: Sch6Anchor = { key: { millId: 16050, year: 2024 }, mill: MILL_514 };

/**
 * S22 — the false-GREEN arm of BR-10: a COMPLETE stored record, broken on screen without saving.
 *
 * ITS STORED STATE IS THE OPPOSITE OF S23's, AND THAT IS THE WHOLE POINT. A Check Status that read the
 * DATABASE instead of the screen would answer MET here — the schedule looks ready while a value in
 * front of the reporter is wrong. S23 catches the mirror failure. Neither arm can detect the other's,
 * so they cannot be collapsed onto one stored state, which is why they hold separate cells rather than
 * merely separate scenarios.
 */
export const UNSAVED_BREAK_ANCHOR: Sch6Anchor = { key: { millId: 25054, year: 2024 }, mill: MILL_9175 };

/**
 * S23 — the false-RED arm of BR-10: an INCOMPLETE stored record, corrected on screen without saving.
 *
 * THE FIRST ANCHOR IN 2025, and by arithmetic rather than choice: the extract holds 17 ACT mills and
 * sch6 pins 15 of them at 2024 (plus 23050, whose ABSENCE is S08's fixture), so 16050 and 25054 were
 * the last two 2024 cells and S21/S22 took them. "Year >= 2024 belongs to sch6" is the structural
 * invariant the whole fan-out rests on — every other domain pins <= 2023 — so 2025 collides with
 * nothing by construction. `draft-anchors.sql` now derives its ILCR_REPORTING_PERIOD row from the
 * anchor table, so the 2025 period exists and Home's year dropdown offers it.
 */
export const UNSAVED_FIX_ANCHOR: Sch6Anchor = { key: { millId: 9050, year: 2025 }, mill: MILL_760 };

// ---------------------------------------------------------------------------------------------------
// THE ACCESSIBILITY SWEEPS (@a11y, NFR1 — WCAG 2.1 AA)
//
// MOST SWEEPS NEED NO ANCHOR OF THEIR OWN, and that is deliberate rather than thrifty:
//   * the empty-Draft, blank-Add-panel and validation-error sweeps all ride VALIDATE_ONLY_ANCHOR,
//     whose whole contract is that nothing is ever written there — three more pure readers cannot
//     collide with each other or with S05/S12-S16;
//   * the read-only sweep reads READ_ONLY_ANCHOR's seeded records, which S17 also only reads;
//   * the context-suppressed sweep needs no anchor at all — it is about the ABSENCE of a working
//     context, which lives in the browser.
// Only the two sweeps that need a SAVED RECORD on screen have to write, so only those two get cells.
//
// BOTH ARE IN 2025, because 2024 is full: the extract has 17 ACT mills and sch6 pins 15 of them at
// 2024 (plus 23050, whose absence is S08's fixture), and S21/S22 took the last two. Same structural
// invariant as S23 — "year >= 2024 belongs to sch6" — and `draft-anchors.sql` derives its
// reporting-period row from the anchor table, so 2025 needs no separate bookkeeping.
// ---------------------------------------------------------------------------------------------------

/** @a11y — a saved record's row expanded, so the row editor's own fields are in the scan. */
export const A11Y_ROW_ANCHOR: Sch6Anchor = { key: { millId: 10050, year: 2025 }, mill: MILL_2121 };

/** @a11y — a Check Status verdict with findings on screen (the notification list). */
export const A11Y_CHECK_ANCHOR: Sch6Anchor = { key: { millId: 12050, year: 2025 }, mill: MILL_987 };

export const EDITABLE_DRAFT_ANCHORS: { name: string; anchor: Sch6Anchor }[] = [
  { name: 'add (S01)', anchor: ADD_ANCHOR },
  { name: 'edit (S02)', anchor: EDIT_ANCHOR },
  { name: 'tfl (S03)', anchor: TFL_ANCHOR },
  { name: 'general-comment (S04)', anchor: GENERAL_COMMENT_ANCHOR },
  { name: 'tfl-correction (S05)', anchor: TFL_CORRECTION_ANCHOR },
  { name: 'validate-only (S05, S12-S16)', anchor: VALIDATE_ONLY_ANCHOR },
  { name: 'check-missing-cost (S09)', anchor: CHECK_MISSING_COST_ANCHOR },
  { name: 'check-missing-tfl (S10)', anchor: CHECK_MISSING_TFL_ANCHOR },
  { name: 'check-missing-supply-block (S11)', anchor: CHECK_MISSING_SUPPLY_BLOCK_ANCHOR },
  { name: 'area-type-correction (S12)', anchor: AREA_TYPE_CORRECTION_ANCHOR },
  { name: 'comment-only (S18)', anchor: COMMENT_ONLY_ANCHOR },
  { name: 'reclassify (S19)', anchor: RECLASSIFY_ANCHOR },
  { name: 'mixed-check (S20)', anchor: MIXED_CHECK_ANCHOR },
  { name: 'multi-missing (S21)', anchor: MULTI_MISSING_ANCHOR },
  { name: 'unsaved-break (S22)', anchor: UNSAVED_BREAK_ANCHOR },
  { name: 'unsaved-fix (S23)', anchor: UNSAVED_FIX_ANCHOR },
  { name: 'a11y-row', anchor: A11Y_ROW_ANCHOR },
  { name: 'a11y-check', anchor: A11Y_CHECK_ANCHOR },
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
// THE ADD-PANEL FIELD VALIDATIONS (S12-S16)
//
// ALL FIVE MESSAGES ARE TRANSCRIBED FROM `ROAD_MESSAGES` in
// src/components/schedule6/validation.ts:60-70, which is itself declared verbatim from the BACKEND
// bundle ("Verbatim from the backend bundle (messages.properties) so an advisory message is
// byte-identical to the server's rejection for the same field"). So each literal below is the text a
// reporter sees whether the client or the server refused the entry.
//
// S12 CLOSES SPEC-2. The source Gherkin could not recover the required-field message and carried
// `[UNKNOWN]` rather than fabricating one — it was looking for a custom JSF literal, and legacy had
// none (the framework default filled it in at runtime). The rebuilt app does NOT inherit a framework
// default: it declares the message itself, `areaTypeRequired`. That literal is therefore the answer to
// SPEC-2, taken from the running app exactly as the spec-gap entry said it would have to be, and it is
// flagged for BA/QA confirmation against legacy rather than treated as settled.
//
// WHEN THE FIVE ERRORS APPEAR — THE ONE MATERIAL RE-GROUNDING, and it applies to every slice here.
// The legacy Gherkin reads "When I enter a non-numeric value ... Then the system displays the error",
// i.e. per-field ajax validation as you leave the field. In the React app NOTHING validates on blur:
// `onBlur` calls `commitRate`, which runs `validateRoadRecord` only to decide whether to advance the
// $ / m³ baseline and then DISCARDS the errors (index.tsx:533-542). The errors a reporter sees are set
// in `handleAdd` (index.tsx:682-686) — so all five surface on **Add Report**. Recorded as defects.md
// VER-5; it is the same class as VER-3 (the TFL number) but a wider one, and for a different reason:
// the TFL number needs the server, whereas these five are decided entirely on the client.
//
// WHICH MAKES THE NEGATIVE STRONGER HERE THAN IN S05, and this is the contrast worth keeping.
// `handleAdd` early-returns the moment `validateRoadRecord` reports anything, BEFORE the POST is built,
// so a rejected entry costs **zero** mutating requests — where S05's invalid TFL cost exactly one
// (only the server knows the RMG table). So these scenarios assert `mutations === 0`, the no-write
// proof the suite's own guidance asks for, AND still read the anchor back: a spy proves no request was
// sent, only a read proves nothing was stored.
// ---------------------------------------------------------------------------------------------------

/** `areaTypeRequired` — the answer to SPEC-2, taken from the running app. Awaiting BA/QA sign-off. */
export const AREA_TYPE_REQUIRED_MESSAGE = 'TSA or TFL: Value is required.';

/** `volumeInvalid` — S13. Matches the source Gherkin byte-for-byte. */
export const VOLUME_INVALID_MESSAGE = 'Entered volume entry is invalid.';

/** `volumeRange` — S14. Matches the source Gherkin byte-for-byte. */
export const VOLUME_RANGE_MESSAGE = 'Entered volume must be between 0 and 9,999,999.';

/** `costInvalid` — S15. Matches the source Gherkin byte-for-byte. */
export const COST_INVALID_MESSAGE = 'Entered cost is invalid.';

/** `costRange` — S16. Matches the source Gherkin byte-for-byte. Note the NEGATIVE lower bound. */
export const COST_RANGE_MESSAGE = 'Entered cost must be between -99,999,999 and 99,999,999.';

/**
 * The VALID partner amounts every S12-S16 scenario carries, and the rate a correction must produce.
 *
 * WHY A VALID PARTNER AT ALL: each slice rejects exactly ONE field, so the other numeric field is
 * deliberately valid — otherwise a scenario asserting "Entered cost is invalid." could be satisfied by
 * a form that was refused for the volume instead, and the two messages would be interchangeable.
 *
 * 56,000 / 16,000 = 3.50 EXACTLY, so no assertion here turns on a rounding decision — and 3.50 is
 * unused by every other sch6 slice (S01 3.84, S02 3.00/4.50, S03 4.00, S05 2.50), so a scenario that
 * somehow read another slice's derived cell fails instead of passing on a coincidence.
 */
export const VALIDATION_AMOUNTS = {
  volumeInput: '16000',
  costInput: '56000',
  volumeDisplay: '16,000',
  costDisplay: '56,000',
  costPerVolumeDisplay: '3.50',
} as const;

/**
 * What the Add panel's `$ / m³` cell reads when no VALID entry has ever been committed on it.
 *
 * The empty string, not a zero and not a dash: `ratioMask(null)` returns `''` (index.tsx:86-95) and a
 * fresh panel starts at `EMPTY_RATE_INPUTS`. This is the assertion that proves the ruled 2026-08-21
 * behaviour — an unparseable or out-of-range entry must NOT drive the derived cell, because that would
 * show a rate no Save could ever persist. On a fresh Add panel "held at its last valid figure" and
 * "still blank" are the same claim.
 */
export const NO_COMPUTED_RATE = '';

/**
 * S12 — the blank area type. The reject arm types valid amounts and submits with the combo untouched.
 *
 * `correction` saves on AREA_TYPE_CORRECTION_ANCHOR, so it carries the full classification: the same
 * TSA/Supply Block pair (and therefore the same server-derived RMG "15") as S01, because S12's subject
 * is the REQUIRED-FIELD rule, not the classification — varying the codes as well would make a failure
 * ambiguous between the two.
 */
export const S12_RECORD = {
  rejectComments: 'E2E S12 rejected blank area type',
  correction: {
    areaTypeCode: S01_RECORD.areaTypeCode,
    areaTypeOption: S01_RECORD.areaTypeOption,
    supplyBlockCode: S01_RECORD.supplyBlockCode,
    supplyBlockOption: S01_RECORD.supplyBlockOption,
    rmg: S01_RECORD.rmg,
    comments: 'E2E S12 corrected area type record',
  },
} as const;

// ---------------------------------------------------------------------------------------------------
// S17 — THE READ-ONLY ANCHOR (the report is not in Draft)
//
// THE ONLY sch6 ANCHOR THAT IS NEITHER DRAFT NOR EMPTY, so it is deliberately absent from
// EDITABLE_DRAFT_ANCHORS above — that list is what preflight asserts is Draft, editable and
// record-free, and this cell is none of the three. It gets its own preflight check instead.
//
// WHY 'S' MAKES IT READ-ONLY, which depends on WHO the suite is: ILCR_SUBMITTER edits at Draft only,
// while ADMIN edits at Submitted/Verified (ScheduleEditability:63-64 — administrator is deliberately
// NOT a superset, because the statuses form a hand-off chain). The suite runs as ILCR_SUBMITTER
// (pages/common/mockUser.ts), matching every feature file's "As a Licensee", so a Submitted document
// answers `editable: false`. Were the suite ever switched to ADMIN, this anchor would silently become
// EDITABLE and S17 would test the opposite of its subject — which is why the scenario re-asserts
// `editable: false` at scenario time rather than trusting the status code alone.
//
// ITS CONTENT IS SEEDED IN SQL, and has to be: S17 must render EXISTING records, totals and a general
// comment, and none of them can be created through the app because every write to a non-Draft document
// is refused — the very condition the slice is about. See
// `real-test-data-patches/sch6/view-mode-road-records.sql` (mirrored into the CI seed in the same
// change, with `ROAD_MAINTENANCE_REPORT_ID` registered in the parity gate's `parentsByColumn`).
//
// VERIFIED 2026-09-18 through the app's own API after applying the patch:
//   GET /api/v1/schedule6?millId=24051&year=2024
//     -> 200, trackStatus "S", editable false,
//        generalComments "E2E S17 read-only schedule general comment.",
//        totals 30000 / 120000 / 4.0,
//        records: 01/01B rmg 15 -> 10000/30000 rate 3.0, and TFL 48 rmg 10 -> 20000/90000 rate 4.5.
// Every value below is transcribed from that response, not computed on paper.
// ---------------------------------------------------------------------------------------------------

export const READ_ONLY_ANCHOR: Sch6Anchor = { key: { millId: 24051, year: 2024 }, mill: MILL_8888 };

/**
 * The two seeded records S17 renders, in the order the document serves them.
 *
 * ORDER IS THE READ QUERY'S, not the fixture's whim: `findRoadRecords` sorts by
 * `ROAD_MAINTENANCE_REPORT_ID` (Schedule6Repository:97), and the patch inserts the TSA row first, so
 * the TSA row is display ordinal 1. The scenario nonetheless MATCHES BY COMMENT rather than assuming
 * the order — see below.
 *
 * NO recordId IS PINNED, deliberately. The local patch draws ids from `ILCR_REPORT_COMMON_SEQ` while
 * the CI seed uses explicit 3100/3101, so the ids genuinely differ between environments — and the row
 * field ids are `row-<recordId>-*`. The scenario therefore reads the document and resolves each
 * record's id and ordinal from its COMMENT, which is stable everywhere. Pinning an id here would pass
 * locally and fail in CI for a reason that looks nothing like the cause.
 */
export const S17_RECORDS = [
  {
    /** The TSA / Supply Block branch. */
    comments: 'E2E S17 read-only TSA record',
    areaTypeCode: '01',
    supplyBlockCode: '01B',
    rmg: '15',
    volumeDisplay: '10,000',
    costDisplay: '30,000',
    costPerVolumeDisplay: '3.00',
  },
  {
    /** The TFL branch — a stored TFL row is served with the sentinel as its area type (as S03 pins). */
    comments: 'E2E S17 read-only TFL record',
    areaTypeCode: TFL_OPTION,
    tflNumber: '48',
    rmg: '10',
    volumeDisplay: '20,000',
    costDisplay: '90,000',
    costPerVolumeDisplay: '4.50',
  },
] as const;

/**
 * The totals the two records produce.
 *
 * 4.00 IS THE LOAD-BEARING NUMBER: it is 120,000 / 30,000, and it equals NEITHER record's own rate
 * (3.00 and 4.50). So a page that echoed a single row's figures into the totals strip fails here
 * instead of passing — which one record could never have caught.
 */
export const S17_TOTALS = {
  volume: '30,000',
  cost: '120,000',
  costPerVolume: '4.00',
} as const;

/** The schedule-level general comment seeded on the read-only anchor (BR-09: on every cat-6 row). */
export const S17_GENERAL_COMMENT = 'E2E S17 read-only schedule general comment.';

// ---------------------------------------------------------------------------------------------------
// S18 — THE SCHEDULE STORES ONLY A GENERAL COMMENT
//
// The state S04 creates as a side effect, now as a subject in its own right: saving a comment on an
// otherwise empty schedule makes the backend insert a bare BR-09 PLACEHOLDER row to carry it
// (`Schedule6Service:425`), and the read side excludes any row whose classification is entirely blank
// (:470). So the document comes back with the comment, NO records, and totals at rest.
//
// THE ONE RE-GROUNDING, AND IT IS A REAL ONE. The Gherkin says `totalVol`, `totalCos` and `totalCal`
// "show zero". Probed 2026-09-18 on this very anchor:
//     PUT {generalComments: "...", records: []}  ->  200
//     GET -> generalComments set, roadRecords [], totalVolume 0, totalCost 0,
//            totalCostPerVolume NULL
// Two of the three are real zeros; the RATE is null, because 0/0 is undefined — and `ratioMask(null)`
// renders the EMPTY STRING, not "0" (index.tsx:86-95, whose comment states the distinction outright:
// "null (0/0 is undefined) while totalVolume/totalCost are real zeros that must still show"). So the
// third total shows BLANK. Asserting "0" there would fail, and asserting it loosely would hide a real
// behaviour. Recorded as defects.md VER-7.
// ---------------------------------------------------------------------------------------------------

/** The comment S18 stores. Distinct from S04's so a cleanup cannot confuse the two anchors. */
export const S18_GENERAL_COMMENT = 'E2E S18 comment-only schedule general comment.';

/**
 * The totals a comment-only schedule shows.
 *
 * `costPerVolume` is the EMPTY STRING deliberately — see the re-grounding note above. It is the one
 * value here that is not a zero, and it is the reason this fixture spells the three out separately
 * instead of reusing a "zero totals" helper.
 */
export const S18_TOTALS = {
  volume: '0',
  cost: '0',
  costPerVolume: '',
} as const;

// ---------------------------------------------------------------------------------------------------
// S19 — RECLASSIFY AN EXISTING RECORD FROM TSA TO TFL
//
// The slice S02 deliberately left alone ("S02's subject is the amounts; the TSA->TFL switch on a saved
// record has its own slice, and mixing them would make a failure ambiguous"). The record is created
// through the app's own POST as a TSA record, then reclassified ON THE ROW and saved with the
// page-level Save (PUT).
//
// THE FIGURES ARE HELD CONSTANT ACROSS THE SWITCH, on purpose: 40,000 / 10,000 = 4.00 exactly, before
// and after. Only the CLASSIFICATION changes, so the RMG moving from "15" to "10" cannot be confused
// with an amounts recalculation — and the unchanged rate doubles as proof the PUT did not disturb the
// figures it was not asked to touch.
// ---------------------------------------------------------------------------------------------------

/** The record S19 creates as a TSA record, and the TFL it is then reclassified to. */
export const S19_RECORD = {
  comments: 'E2E S19 reclassified record',
  /** As created: the TSA / Supply Block branch, RMG derived from the block. */
  before: {
    areaTypeCode: '01',
    supplyBlockCode: '01B',
    rmg: '15',
  },
  /** After the switch: the TFL branch, RMG derived from the fixed RoadGroupLookup table. */
  after: {
    areaTypeOption: TFL_OPTION,
    tflNumber: VALID_TFL.number,
    rmg: VALID_TFL.rmg,
  },
  /** Unchanged by the reclassification — 40,000 / 10,000 = 4.00 exactly, before and after. */
  volume: 10000,
  cost: 40000,
  volumeDisplay: '10,000',
  costDisplay: '40,000',
  costPerVolumeDisplay: '4.00',
} as const;

// ---------------------------------------------------------------------------------------------------
// S20 — CHECK STATUS, MIXED RESULTS ACROSS TWO RECORDS
//
// The only state in which the PER-RECORD "met" line appears at all: it is emitted when the SCHEDULE
// fails while some individual record passes, so it needs at least two records that disagree. That is
// why S20 has its own anchor — every other cell is asserted record-free at rest.
//
// VERIFIED AGAINST THE RUNNING ENDPOINT 2026-09-18 (two records posted to 25053/2024, then
// POST /check-status on the served payload, then both records deleted):
//     outcome  : "ISSUES"
//     messages : []                                   <- no schedule-level banner
//     record 1 : met true,  metMessage "All requirements for 1 have been met."
//     record 2 : met false, issue cost "Road : 2 - TSA or TFL (Cost $) : Value Required"
// Both literals are byte-identical to the source Gherkin. `roadRequirementsMetMsg` is
// `All requirements for {0} have been met.` with the 1-based DISPLAY ORDINAL substituted as a STRING
// (never an int — MessageFormat would group it, so ordinal 1000 rendered "1,000";
// Schedule6CheckStatusResolver:89-96).
//
// NOTE THE TRAILING PERIOD, and that it is the opposite of the schedule-level message: the per-record
// line ENDS in a period while `scheduleRequirementsMetMsg` does NOT ("All requirements for this
// schedule have been met"). Both are pinned verbatim, and the difference is real rather than a
// transcription slip — messages.properties:151 vs :191.
// ---------------------------------------------------------------------------------------------------

/** S20's two records: row 1 complete, row 2 identical but for an ABSENT cost. */
export const S20_RECORDS = {
  /** Row 1 — complete, so it passes and emits the per-record met line. */
  complete: {
    areaTypeCode: '01',
    supplyBlockCode: '01B',
    volume: 10000,
    cost: 30000,
    comments: 'E2E S20 complete record',
  },
  /**
   * Row 2 — the SAME classification, missing only the cost.
   *
   * Identical but for the one absent field on purpose: it makes the finding attributable to the cost
   * and nothing else. An ABSENT cost, not a zero — the check is null-only (D2 precedent), so a cost of
   * 0 would PASS and this scenario would have no failing record at all.
   */
  missingCost: {
    areaTypeCode: '01',
    supplyBlockCode: '01B',
    volume: 20000,
    comments: 'E2E S20 cost-less record',
  },
} as const;

/**
 * The per-record "requirements met" line for a given display ordinal.
 *
 * A function rather than a literal because the ordinal is substituted into the message
 * (`roadRequirementsMetMsg`), and S20's whole point is WHICH row passed — hardcoding "1" would let a
 * bug that reported the wrong ordinal slip through when the numbers happened to line up.
 */
export const roadRequirementsMet = (ordinal: number): string =>
  `All requirements for ${String(ordinal)} have been met.`;

/** S20's failing line — the second row's absent cost, carrying the legacy mislabel verbatim. */
export const S20_MISSING_COST_LINE = 'Road : 2 - TSA or TFL (Cost $) : Value Required';

// ---------------------------------------------------------------------------------------------------
// S21 — ONE RECORD MISSING SEVERAL VALUES AT ONCE
//
// EVERY LINE THIS SLICE NEEDS IS ALREADY PINNED in CHECK_LINES above, both for row 1 — so nothing new
// is transcribed here, only the record that produces them.
//
// VERIFIED BY PROBE 2026-09-18 on this anchor: a TSA record with NO supply block and NO cost STORES
// fine (HTTP 200, `rmg` null because no block means nothing to derive from), and check-status returns
// BOTH findings for row 1, supply block first then cost:
//     "Road : 1 - Supply Block : Value Required"
//     "Road : 1 - TSA or TFL (Cost $) : Value Required"
// which is what the slice claims and matches the source Gherkin's order. `evaluateRecord` accumulates
// into a LIST rather than returning on the first failure (Schedule6Service:873-890), which is the
// behaviour that makes "list every missing value, not just the first" true.
// ---------------------------------------------------------------------------------------------------

/** S21's record: a TSA record missing both its supply block and its cost. Storable — probed. */
export const S21_RECORD = {
  areaTypeCode: '01',
  volume: 9000,
  comments: 'E2E S21 doubly-incomplete record',
} as const;

// ---------------------------------------------------------------------------------------------------
// S22 / S23 — BR-10: CHECK STATUS JUDGES THE SCREEN, NOT THE DATABASE
//
// THESE ARE THE TWO SLICES SPEC-1 RECOVERED (the catalogue had counted 21 and named neither), and on
// Schedule 6 they are expected GREEN — a REGRESSION GUARD, not a defect tracker. Every other schedule
// still reads the database here (Schedule 5's S24/S25 is the live divergence, issue #476 / app-wide
// #359). Schedule 6 already ships the fix: `POST /check-status` takes the ON-SCREEN values
// (`Schedule6CheckRequest`, whose javadoc calls itself "the fix"), `checkStatus` evaluates
// `payloadCandidates(request)` and nothing else, and an earlier DB-reading implementation was
// deliberately RETIRED in Task 8. So a red here is a real regression. Do NOT "align" Schedule 6 with
// the others.
//
// THE TWO ARMS FAIL IN OPPOSITE DIRECTIONS, which is why both exist and why they cannot share a
// stored state:
//   S22 (false-GREEN): stored COMPLETE, broken on screen. A DB-reading implementation answers MET and
//                      the reporter is told the schedule is ready while a wrong value is in front of
//                      them.
//   S23 (false-RED)  : stored INCOMPLETE, fixed on screen. A DB-reading implementation keeps
//                      reporting the problem and the reporter is told to fix what they just fixed.
// An implementation with either fault passes the other arm, so one arm alone proves nothing.
//
// "AND NO SCHEDULE RECORDS ARE CHANGED" IS THE THIRD CLAIM IN BOTH, and it is not incidental: the
// whole point is that Check Status is a READ. If it wrote the on-screen payload through on its way to
// a verdict, both arms would still pass their message assertions while silently saving edits the
// reporter never committed. So each arm snapshots the stored record first and re-reads it afterwards.
//
// ONE SOURCE SCENARIO IS NOT COVERED, and it is not reachable rather than not attempted — S22's second
// arm, "an in-range amount that still fails its Check Status requirement". Schedule 6 has no such
// value: every Check Status rule on this page is a PRESENCE check (`isBlank(areaType)`,
// `isBlank(tflNumber)` / `isBlank(supplyBlock)`, `cost == null` — Schedule6Service:873-890). There is
// no range bound and no cross-field relationship, and volume is not checked at all. So any well-formed
// in-range cost satisfies the requirement, including 0 (the null-only rule, D2 precedent). The only
// failing cost is an ABSENT one, which is S22's first arm. Recorded as a coverage gap (not-applicable)
// in defects.md rather than dropped.
// ---------------------------------------------------------------------------------------------------

/**
 * S22's stored record — COMPLETE, so the stored schedule genuinely satisfies every requirement before
 * the scenario breaks it on screen. 55,000 / 11,000 = 5.00 exactly.
 */
export const S22_RECORD = {
  areaTypeCode: '01',
  supplyBlockCode: '01B',
  volume: 11000,
  cost: 55000,
  volumeDisplay: '11,000',
  costDisplay: '55,000',
  costPerVolumeDisplay: '5.00',
  comments: 'E2E S22 complete record',
} as const;

/**
 * S23's stored record — INCOMPLETE (no cost), plus the cost the scenario supplies on screen.
 *
 * 78,000 / 13,000 = 6.00 exactly, and 6.00 is used by no other sch6 slice, so a rate read from the
 * wrong row or the wrong anchor fails rather than matching by coincidence.
 */
export const S23_RECORD = {
  areaTypeCode: '01',
  supplyBlockCode: '01B',
  volume: 13000,
  comments: 'E2E S23 cost-less record',
  /** Typed into the row's Cost field, never saved. */
  suppliedCostInput: '78000',
  suppliedCostDisplay: '78,000',
  suppliedCostPerVolumeDisplay: '6.00',
} as const;

// THE REJECTED ENTRIES THEMSELVES ARE NOT PINNED HERE — they live in each slice's `Scenario Outline`
// Examples table in `amount-validation.feature`, with the reason for each row in that file's header.
// They are the slice's own MATRIX (max + 1, min - 1, too many decimals), which is what a Scenario
// Outline is for, and duplicating them here would give every value two homes and a way to drift. What
// stays in this fixture is what is app-GROUNDED and shared: the five verbatim messages above, the valid
// partner amounts, and the blank-rate literal.

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
// CHECK STATUS "Value Required" LINES (S09, S10, S11)
//
// Composed server-side, byte-for-byte as legacy did: `"Road : " + rowCounter + segment + ": " + text`
// (Schedule6CheckStatusResolver:112-125). The ROW COUNTER is the 1-based DISPLAY ordinal, not the
// recordId — the same number the accordion titles carry.
//
// "TSA or TFL (Cost $)" IS THE LEGACY MISLABEL, PRESERVED ON PURPOSE. The missing-COST line names the
// TSA/TFL field (Schedule6MB.checkStatus :172), which is a labelling quirk in the original source.
// Schedule6Service's own header calls it out as a pinned quirk, and the source Gherkin's note says it
// is "reproduced exactly as found (not corrected here)". Do not tidy it: the text is the requirement.
//
// WHAT CHECK STATUS DOES **NOT** REQUIRE, so nobody adds an assertion for it later:
//   * VOLUME is never checked — commented out in legacy (Schedule6CheckStatus:19), ported verbatim.
//   * A cost of ZERO PASSES. The check is null-only (D2 precedent), so 0 is MET, not a finding.
// ---------------------------------------------------------------------------------------------------

export const CHECK_LINES = {
  /** S09. Note the legacy mislabel — this is the COST line. */
  missingCost: 'Road : 1 - TSA or TFL (Cost $) : Value Required',
  /** S10. */
  missingTflNumber: 'Road : 1 - TFL Number : Value Required',
  /** S11. */
  missingSupplyBlock: 'Road : 1 - Supply Block : Value Required',
} as const;

/** S09's record: a complete TSA record except that the cost is absent. */
export const S09_RECORD = {
  areaTypeCode: '01',
  supplyBlockCode: '01B',
  volume: 9000,
  comments: 'E2E S09 cost-less record',
} as const;

/** S10's record: a VALID TFL record, whose number the scenario then blanks on screen. */
export const S10_RECORD = {
  tflNumber: VALID_TFL.number,
  volume: 9000,
  cost: 5000,
  comments: 'E2E S10 TFL record',
} as const;

/** S11's record: a TSA record saved with no Supply Block, which the write path permits. */
export const S11_RECORD = {
  areaTypeCode: '01',
  volume: 9000,
  cost: 5000,
  comments: 'E2E S11 supply-block-less record',
} as const;

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
