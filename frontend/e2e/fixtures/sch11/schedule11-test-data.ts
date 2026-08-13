/**
 * UC-SCH11-001 (Schedule 11 — Report Basic Silviculture Costs) pinned test data. DB-grounded via the
 * app's own API, never fabricated.
 *
 * Anchors discovered + confirmed 2026-08-10 against the seeded local delivery DB
 * (THE/…@localhost:1525/DBDOCK_01) by enumerating `GET /api/v1/mill-context?millId=<m>&year=<y>` over
 * every mill × reporting year, then classifying each candidate with
 * `GET /api/v1/schedule11?millId=<m>&year=<y>` (trackStatus / editable / locations.length).
 *
 * Survey result (118 mill/year rows carrying a report-status row):
 *   - 99 ACT mills with silviculture track "D"; 89 of those hold ZERO locations (pristine)
 *   - exactly ONE row has Sch 1-10 non-Draft while silviculture stays Draft → the S10 anchor
 *   - 12 ACT rows have a non-Draft silviculture track → the S20 read-only anchors
 *
 * PARALLEL SAFETY: the suite runs `fullyParallel`, and a Schedule 11 location is a real child row, so
 * every MUTATING scenario owns a DEDICATED (mill, year) that no other scenario writes to, and seeds
 * whatever precondition rows it needs through the real API (registering cleanup at the same time). The
 * validate-only anchor is deliberately NOT one of the mutating pairs — those scenarios prove Add is
 * blocked client-side, so nothing is ever written there.
 *
 * Keys were also chosen to avoid every (mill, year) the sch1 and sec suites pin, so a re-ground of one
 * domain never silently moves another's ground truth. (Schedule 1 and Schedule 11 live in different
 * tables, so this is defence in depth rather than a hard requirement.)
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/sch11-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the sch11 specs).
 */

export interface ScheduleKey {
  millId: number;
  year: number;
}

export interface MillRef {
  millNumber: string;
  millName: string;
}

export interface Sch11Anchor {
  key: ScheduleKey;
  mill: MillRef;
}

// ---------------------------------------------------------------------------------------------------
// Mutating anchors — one per scenario that writes. All confirmed ACT mill, trackStatus "D",
// editable:true and ZERO locations at rest, WITH ONE EXCEPTION: the S10 track-independence anchor
// carries two seeded rows (see its own comment below). `preflight/sch11-anchors.setup.ts` asserts the
// emptiness of the others, because the footer-total / row-count / empty-table assertions depend on it.
// ---------------------------------------------------------------------------------------------------

const MILL_8888: MillRef = { millNumber: '8888', millName: 'CGI TEST MILL8' }; // millId 24051, ACT
const MILL_2121: MillRef = { millNumber: '2121', millName: 'SESAME STREET' }; // millId 10050, ACT
const MILL_514: MillRef = { millNumber: '514', millName: 'AAA MILLING' }; // millId 16050, ACT

/** S01 — add one location with both costs and read it back. */
export const ADD_ANCHOR: Sch11Anchor = { key: { millId: 24051, year: 2015 }, mill: MILL_8888 };

/** S02 — add two locations in one session and prove the totals accumulate. */
export const MULTI_ADD_ANCHOR: Sch11Anchor = { key: { millId: 24051, year: 2016 }, mill: MILL_8888 };

/** S03 — seed one location, then correct it through the per-row inline editor. */
export const INLINE_EDIT_ANCHOR: Sch11Anchor = {
  key: { millId: 24051, year: 2017 },
  mill: MILL_8888,
};

/** S07 — seed one location, delete it behind the confirm modal. */
export const DELETE_ANCHOR: Sch11Anchor = { key: { millId: 24051, year: 2018 }, mill: MILL_8888 };

/** S08 — seed one location, open the confirm modal and cancel; the row must survive unchanged. */
export const CANCEL_DELETE_ANCHOR: Sch11Anchor = {
  key: { millId: 24051, year: 2019 },
  mill: MILL_8888,
};

/** S09 — add-is-save: the row must survive a full page reopen with no separate Save click. */
export const PERSIST_ANCHOR: Sch11Anchor = { key: { millId: 24051, year: 2020 }, mill: MILL_8888 };

/** S04 — seed a location carrying BOTH costs, then Check Status reports all requirements met. */
export const CHECK_MET_ANCHOR: Sch11Anchor = { key: { millId: 24051, year: 2021 }, mill: MILL_8888 };

/** S05 — seed a location with a NULL actual cost, then Check Status flags it. */
export const CHECK_MISSING_ACTUAL_ANCHOR: Sch11Anchor = {
  key: { millId: 10050, year: 2017 },
  mill: MILL_2121,
};

/** S06 — seed a location with a NULL planned cost, then Check Status flags it. */
export const CHECK_MISSING_PLANNED_ANCHOR: Sch11Anchor = {
  key: { millId: 10050, year: 2018 },
  mill: MILL_2121,
};

/** Accessibility sweep of the fully-populated EDITABLE page (Add panel + a real row + Actions column). */
export const A11Y_ANCHOR: Sch11Anchor = { key: { millId: 10050, year: 2020 }, mill: MILL_2121 };

/**
 * GAP-3 — the per-row optimistic lock. A row is opened for edit (capturing its `revisionCount`), changed
 * out from under the browser through the API, then saved: the PUT carries a now-stale token and must be
 * rejected with the ERR conflict message rather than silently overwriting.
 *
 * Confirmed pristine 2026-08-10: `GET /v1/schedule11?millId=16050&year=2018` → {trackStatus:"D",
 * editable:true, locations:[]}. Distinct from the `no-schedule` guard anchor, which is the same mill in a
 * DIFFERENT year (16050/2016).
 */
export const STALE_EDIT_ANCHOR: Sch11Anchor = { key: { millId: 16050, year: 2018 }, mill: MILL_514 };

/**
 * The correct-and-retry loop (the recovery arm the S14–S19 legacy scenarios each carry). The recovery
 * MECHANISM is identical for every field — fix the offending input, click Add, the row is accepted — and
 * it is a write, so it is exercised once here on its own key rather than six times on six more anchors.
 * See coverage.md for the equivalence rationale.
 */
export const CORRECTION_ANCHOR: Sch11Anchor = { key: { millId: 10050, year: 2021 }, mill: MILL_2121 };

/**
 * S10 — track independence. The ONLY row in the seed where the Schedule 1–10 track has advanced past
 * Draft (Submitted, "S") while the silviculture track independently remains Draft ("D"). Confirmed
 * `GET /api/v1/schedule11?millId=23050&year=2016` → {trackStatus:"D", editable:TRUE}, i.e. the 1–10
 * status provably does not gate Schedule 11 (legacy `disableUserInputSchedule11()` reads only
 * `millSilviculturStatusCode`). This scenario ADDS a location, so it owns the key outright.
 *
 * THE ONE NON-EMPTY MUTATING ANCHOR: it arrives carrying two seeded rows ("20173" netArea 1.2, "20173-2"
 * netArea 1.1, both revisionCount 0 — confirmed 2026-08-12). Being the only (mill, year) in the extract
 * with this status combination, there was no empty alternative, so the scenario is written for it: it
 * asserts the added row's stored record and the live editing surface, and deliberately makes NO row-count
 * or footer-total claim. Its precondition therefore checks only that its OWN marker is absent, and the
 * preflight emptiness check skips this key by name.
 */
export const TRACK_INDEPENDENCE_ANCHOR: Sch11Anchor = {
  key: { millId: 23050, year: 2016 },
  mill: { millNumber: '20173', millName: 'TOMTESTMILL042017' },
};

// ---------------------------------------------------------------------------------------------------
// Validate-only anchor — reached, never written to.
// ---------------------------------------------------------------------------------------------------

/**
 * S14–S19 — every required-field / range rejection. The Add panel's client-side gate returns BEFORE
 * issuing the POST, so nothing is ever written here; deliberately NOT one of the mutating pairs above
 * and NOT registered for cleanup, which is what keeps these scenarios parallel-safe.
 */
export const VALIDATION_ANCHOR: Sch11Anchor = { key: { millId: 10050, year: 2019 }, mill: MILL_2121 };

// ---------------------------------------------------------------------------------------------------
// Guard-state anchors (S11–S13, S20) — read-only. The Home Save is a resolve GET that writes no
// schedule data, so these need no cleanup and are safe to share.
// `expectHttp` is the schedule11 GET status the state depends on; the precondition asserts it so a data
// drift fails as a re-ground rather than as a confusing UI timeout.
// ---------------------------------------------------------------------------------------------------

/**
 * A real row each read-only guard anchor already holds, so the S20 scenarios can make a POSITIVE
 * assertion instead of proving read-only entirely by absence.
 *
 * WHY THIS EXISTS: a regression that rendered an EMPTY table would satisfy every "…is not rendered" /
 * "…is disabled" check vacuously. Asserting real seeded content proves the table still displays data
 * while being read-only, which is the actual requirement. (This is the trap the 2026-07-30 review of the
 * earlier, since-removed 25.4 attempt caught; recorded in the story's Review Findings.)
 *
 * Confirmed 2026-08-10 via `GET /api/v1/schedule11`:
 *   12050/2016 (Submitted) -> 1 location: {location:"1", becLabel:"ESSFdk2", netArea:5.5, enhanced:false}
 *   13050/2015 (Verified)  -> 5 locations, first: {location:"add1", becLabel:"SBPSdc"}
 */
export const GUARD_ANCHOR_ROWS: Record<string, { location: string; becLabel: string }> = {
  submitted: { location: '1', becLabel: 'ESSFdk2' },
  verified: { location: 'add1', becLabel: 'SBPSdc' },
};

export const GUARD_ANCHORS: Record<string, Sch11Anchor & { expectHttp: number }> = {
  // S12 — mill 13 is CLS (closed) for 2017 → schedule11 GET 409, ERR-002 detail rendered verbatim.
  'closed-mill': {
    key: { millId: 13, year: 2017 },
    mill: { millNumber: '45', millName: 'GORMAN BROS. - DOWNIE STREET' },
    expectHttp: 409,
  },
  // S13 — mill 16050 is ACT for 2016 but has NO ILCR_MILL_REPORT_STATUS row → GET 404, ERR-003.
  'no-schedule': {
    key: { millId: 16050, year: 2016 },
    mill: { millNumber: '514', millName: 'AAA MILLING' },
    expectHttp: 404,
  },
  // S20 — silviculture track Submitted ("S") → GET 200 but editable:false → read-only render.
  submitted: {
    key: { millId: 12050, year: 2016 },
    mill: { millNumber: '987', millName: 'TURTLE DOVE' },
    expectHttp: 200,
  },
  // S20 — silviculture track Verified ("V") → the same read-only render from the other non-Draft code.
  verified: {
    key: { millId: 13050, year: 2015 },
    mill: { millNumber: '999', millName: 'ISP TEST' },
    expectHttp: 200,
  },
};

// ---------------------------------------------------------------------------------------------------
// Real biogeoclimatic catalogue options (BR-09 forced selection).
// Confirmed via GET /api/v1/schedule11/biogeoclimatic-catalogue?q=<term> on 2026-08-10.
// The ComboBox submits the OPTION ID, so a fabricated label could never resolve — these must be real.
// ---------------------------------------------------------------------------------------------------

export interface BecOption {
  id: number;
  label: string;
  /** A query prefix that provably returns this option (drives the type-ahead in the specs). */
  query: string;
}

/** `?q=E` → […{"id":40,"label":"ESSFdc1"}…] */
export const BEC_PRIMARY: BecOption = { id: 40, label: 'ESSFdc1', query: 'ESSFdc1' };

/** `?q=S` → […{"id":171,"label":"SBSdh"}…] — the second option, for the multi-add / edit specs. */
export const BEC_SECONDARY: BecOption = { id: 171, label: 'SBSdh', query: 'SBSdh' };

/**
 * A prefix that returns MANY real suggestions but is not itself a catalogue label — the correct trigger
 * for S16's forced-selection rejection.
 *
 * WHY A POPULATED PREFIX, NOT GIBBERISH: the slice's real condition is "free text typed **while
 * suggestions exist**, never chosen". Typing something that matches nothing (`ZZZZ…`) leaves the
 * suggestion list EMPTY and so never executes the forced-selection path at all — it only proves an empty
 * field is rejected, which S16 is not about. (The same trap the 2026-07-30 review of the earlier 25.4
 * attempt caught.) Confirmed 2026-08-10: `?q=IDF` returns 20 options, none labelled exactly "IDF".
 */
export const BEC_POPULATED_PREFIX = 'IDF';

/** A suggestion `BEC_POPULATED_PREFIX` provably returns — asserted visible so we know the list populated. */
export const BEC_POPULATED_FIRST_OPTION = 'IDFdc';

// ---------------------------------------------------------------------------------------------------
// API paths + URL builders.
// ---------------------------------------------------------------------------------------------------

export const SCHEDULE11_API = '/api/v1/schedule11';

export const scheduleUrl = (millId: number, year: number): string =>
  `${SCHEDULE11_API}?millId=${millId}&year=${year}`;

export const locationsUrl = (millId: number, year: number): string =>
  `${SCHEDULE11_API}/locations?millId=${millId}&year=${year}`;

export const locationUrl = (id: number, millId: number, year: number): string =>
  `${SCHEDULE11_API}/locations/${id}?millId=${millId}&year=${year}`;

// No `checkStatusUrl` builder: Check Status is only ever exercised through the button and asserted on the
// rendered result, so nothing needs the endpoint's URL (see the note in steps/sch11/schedule11Api.ts).

/** Carbon Dropdown option text for a mill — mirrors Home's `millItemToString`. */
export const millOptionText = (m: MillRef): string => `${m.millNumber} - ${m.millName}`;

/** The in-memory MillYearContext localStorage key (context/millYear/MillYearProvider.tsx). */
export const MILL_YEAR_STORAGE_KEY = 'ilcr:mill-year-context';

// ---------------------------------------------------------------------------------------------------
// Verbatim contract strings.
//
// Every one of these is API-OWNED (AD-8): the frontend renders the server's `message.text` /
// ProblemDetail `detail` verbatim rather than re-typing it, so these are asserted exactly. All were
// read from the backend bundle (backend/src/main/resources/messages.properties) AND confirmed live
// against the running app on 2026-08-10.
//
// The specs type these literals out rather than importing them — Gherkin has to stay readable to a BA, and
// a step argument cannot be a TypeScript reference. That would leave the constants below documenting
// strings nothing checks, so `preflight/sch11-anchors.setup.ts` asserts that each one appears verbatim in
// at least one `.feature` file. A constant that drifts from the specs (or a spec whose text drifts from
// the bundle) fails there instead of sitting green and authoritative-looking.
// ---------------------------------------------------------------------------------------------------

export const MSG = {
  /** SUC-001 `dataSavedSuccesfullyInfoMsg` — echoed by the add POST and the inline-edit PUT. */
  saved: 'Data saved successfully',
  /** SUC-002 `dataDeletedSuccesfullyInfoMsg` — echoed by the location DELETE. */
  deleted: 'Data deleted successfully',
  /** SUC-004 `checkStatusMessage` — present on EVERY check-status invocation, pass or fail. */
  statusChecked: 'Status has been checked',
  /** SUC-003 `scheduleRequirementsMetMsg` — present ONLY when requirementsMet. */
  requirementsMet: 'All requirements for this schedule have been met',
} as const;

/**
 * The per-row optimistic-lock rejection (GAP-3). Verbatim from a live 409 on 2026-08-10; the frontend
 * renders the ProblemDetail `detail` unchanged (AD-8).
 */
export const ERR_STALE_EDIT =
  'This schedule was changed by another user. Please reload and try again.';

export const ERR = {
  /**
   * ERR-001. The CLIENT-side literal has NO trailing space (components/schedule11/index.tsx
   * `ERR_MILL_YEAR_NOT_SELECTED`), while the SERVER bundle key `millYearNotSelectedErrorMsg` carries
   * one. S11 is guarded client-side (no request is issued), so the no-trailing-space form is what
   * renders — asserted here as a substring match, which is true of both forms either way.
   */
  millYearNotSelected: 'Please Select Mill and Reporting Year in the Home Page.',
  /** ERR-002 `millNotActiveForCurrentYearMsg` — the 409 detail, rendered verbatim (S12). */
  millNotActive:
    'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.',
  /** ERR-003 `scheduleNotFoundErrorMsg` — the 404 detail, rendered verbatim (S13). */
  scheduleNotFound: 'Schedule not found.',
} as const;

/**
 * Field-level validation messages. The frontend's advisory gate (components/schedule11/validation.ts
 * `SILV_MESSAGES`) mirrors the backend bundle exactly, so the SAME literal is correct whether the
 * rejection is raised in the browser or by the server.
 *
 * NOTE on the two strings validation.ts marks `PROVISIONAL (S15)` / `PROVISIONAL (S18)` pending "the
 * exact live-app text confirmed in Story 25.4" — i.e. pending THIS story. Both are now confirmed
 * against the backend bundle AND a live 400 response (see defects.md, Verified-not-a-defect #1/#2):
 *   enhancedIndicatorRequiredErrorMsg = Enhanced: Value is required.
 *   netAreaRangeErrorMsg              = Entered NAR (ha) must be between 0 and 999,999.9.
 * Both are DELIBERATE improvements on legacy, which rendered a raw JSF client id and an unoverridden
 * JSF DoubleRangeValidator default respectively.
 */
export const FLD = {
  locationRequired: 'Location: Value is required.',
  /**
   * NOT asserted by any scenario, and the preflight literal guard skips it: Carbon's `maxLength` stops the
   * keystroke, so the cap is unreachable from a browser. Kept as the pinned wording for the backend
   * bean-validation test that should cover it — defects.md GAP-1, carried in `deferred-work.md`.
   */
  locationMaxLength: 'Location must be 30 characters or fewer.',
  enhancedRequired: 'Enhanced: Value is required.',
  biogeoRequired: 'Biogeo/Subzone/Variant: Value is required.',
  netAreaRequired: 'NAR(ha): Value is required.',
  netAreaRange: 'Entered NAR (ha) must be between 0 and 999,999.9.',
  costRange: 'Entered cost must be between -99,999,999 and 99,999,999.',
  /** Unreachable from a browser for the same reason as `locationMaxLength` — defects.md GAP-2. */
  commentsMaxLength: 'Comments must be 3500 characters or fewer.',
} as const;

/**
 * FLD-004 — the Check Status missing-cost line. Composed server-side in
 * `Schedule11Service.missingCost()` as `"location  : " + location + " - " + costLabel + ": " + "Value
 * Required"`. The DOUBLE SPACE after "location" is a verbatim legacy literal, NOT a typo to correct —
 * confirmed live 2026-08-10: `'location  : North Ridge - Actual cost: Value Required'`.
 */
export const missingCostMessage = (location: string, which: 'Actual' | 'Planned'): string =>
  `location  : ${location} - ${which} cost: Value Required`;

/** Carbon Modal chrome for the delete confirm (components/schedule11/index.tsx). */
export const CONFIRM_DELETE = {
  heading: 'Delete location',
  body: 'This will delete the current record. Do you want to continue?',
  primary: 'Delete',
  secondary: 'Cancel',
} as const;

/** The empty-table placeholder rendered when a Schedule 11 has no locations. */
export const EMPTY_TABLE_TEXT = 'No silviculture locations have been added.';

/**
 * Per-scenario row markers. Each mutating scenario tags the rows it creates with its own marker so
 * teardown deletes exactly what that scenario made and nothing else (`location` is the only
 * user-supplied identifying text on a row, and it is capped at 30 chars — keep these short).
 */
export const MARKER = {
  add: 'E2E S01 add',
  multiFirst: 'E2E S02 first',
  multiSecond: 'E2E S02 second',
  inlineEdit: 'E2E S03 edit',
  delete: 'E2E S07 delete',
  cancelDelete: 'E2E S08 cancel',
  persist: 'E2E S09 persist',
  checkMet: 'E2E S04 met',
  checkMissingActual: 'E2E S05 noactual',
  checkMissingPlanned: 'E2E S06 noplanned',
  trackIndependence: 'E2E S10 indep',
  a11y: 'E2E a11y row',
  correction: 'E2E corrected',
  staleEdit: 'E2E stale edit',
  // The reject arms below are NOT cleanup registrations — no scenario that types them should ever write a
  // row, and none registers a delete. They are here so the residue preflight
  // (`no leftover E2E marker rows on any Schedule 11 anchor`, which scans Object.values(MARKER) across
  // EVERY anchor) can SEE them: if a regression ever POSTs after client validation fails, the row lands on
  // the validate-only anchor, which is deliberately excluded from the pristine/emptiness check, and would
  // otherwise sit in the delivery DB unnoticed. Listing them turns that into a loud preflight failure.
  rejectS15: 'E2E S15 reject',
  rejectS16: 'E2E S16 reject',
  rejectS17: 'E2E S17 reject',
  rejectS18: 'E2E S18 reject',
  rejectS19: 'E2E S19 reject',
} as const;
