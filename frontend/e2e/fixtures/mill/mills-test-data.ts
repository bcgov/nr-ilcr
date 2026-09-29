/**
 * UC-MILL-001 (Maintain Mills, Status, and User Associations) pinned test data.
 * DB-grounded through the app's own API and the seeded local delivery DB, never fabricated.
 *
 * ---------------------------------------------------------------------------------------------------
 * THIS DOMAIN IS KEYED BY MILL, NOT BY (MILL, YEAR)
 * ---------------------------------------------------------------------------------------------------
 * Every other domain pins (mill, reporting-year) cells because a schedule lives on one. The Mills page
 * works on the MILL itself — its ILCR_MILL_STATUS_XREF row (status, head-office indicator, the two
 * contact ids) and its user associations — and no reporting year is involved anywhere on the screen.
 * So nothing here is a (mill, year) key, and this file deliberately declares NO `year` property at all:
 * `preflight/anchor-keys.ts` pairs a `millId` with the `year` in its enclosing braces, and a stray
 * `year` here would mint a phantom anchor for the schedule guards to chase.
 *
 * `preflight/ci-seed-parity.setup.ts` knows this domain is mill-keyed (MILL_KEYED_DOMAINS) and checks
 * its anchors against the CI seed with a check of their own, rather than exempting them.
 *
 * ---------------------------------------------------------------------------------------------------
 * S01'S MILL — WHY 25050, AND WHY SHARING IT IS SAFE
 * ---------------------------------------------------------------------------------------------------
 * Surveyed 2026-09-28 against the seeded local delivery DB (THE/…@localhost:1525/DBDOCK_01), through
 * `GET /api/v1/admin/mills` and `GET /api/v1/admin/mills/{id}/contact-options` as ILCR_ADMIN:
 *   - 21 tracked mills (17 ACT, 4 CLS).
 *   - Only FIVE carry any contact option at all — 25050..25054, the "*-TEST" mills. The other sixteen
 *     have no client-location contacts, so their two contact dropdowns offer only "(None)" and S01's
 *     "select a contact" step would be unperformable on them.
 *   - All five are schedule anchors for other domains (25050: sch1, sch4, sch5, sch6).
 * So S01 SHARES a mill with the schedule domains. That is safe because the two never touch the same
 * row: S01 writes ONLY the xref row's HEAD_OFFICE_CONTACT_IND / HEAD_OFFICE_CONTACT_ID /
 * DIVISION_CONTACT_ID (+ its audit stamp and REVISION_COUNT), and no schedule read or write path
 * touches those columns — the schedules key on ILCR_MILL_REPORT_STATUS and their own report tables,
 * and the only thing a schedule reads off the xref is the ACT/CLS status, which S01 never changes.
 * 25050 is chosen over its four siblings because it is ACT (the happy path's natural state) and its
 * two contacts have unmistakable names.
 *
 * WHAT IS NOT SAFE, for the slices still to come: any slice that changes a mill's STATUS (S03 deactivate,
 * S04 activate, S12 the blocked deactivate) must NOT use a schedule-anchored mill — a closed mill answers
 * 409 on every schedule, which would fail the schedule domains' scenarios mid-run. Those slices need
 * mills of their own.
 *
 * PARALLEL SAFETY: S01 is the ONLY scenario that writes 25050's xref row. A second mills scenario that
 * saves contacts must take a different mill, or the two would race on REVISION_COUNT and one would 409.
 *
 * ---------------------------------------------------------------------------------------------------
 * CLEANUP CONTRACT, AND THE ONE THING IT CANNOT UNDO
 * ---------------------------------------------------------------------------------------------------
 * `PUT /api/v1/admin/mills/{id}/contacts` REPLACES the whole editable panel (an omitted field is a 400,
 * a null contact id clears the column), so the at-rest values below are restored with one PUT carrying
 * the revision the cleanup has just READ. That puts the indicator and both contacts back exactly.
 *
 * It cannot put back UPDATE_USERID / UPDATE_TIMESTAMP or REVISION_COUNT — every save stamps the acting
 * administrator and increments the revision, and no endpoint writes them. So those three columns DRIFT
 * run over run, and nothing here pins them: the preflight asserts the at-rest status, indicator and
 * contacts only, and the scenario reads the audit line and revision off the API at run time rather
 * than expecting a literal. Recorded in the UC's defects.md as a known, harmless residue.
 *
 * A re-extract can renumber this data — re-grounding these values is part of any re-extract, and
 * `preflight/mill-anchors.setup.ts` fails the whole run fast with one clear message if it drifts.
 * Change values HERE only (single source of truth for the mill specs).
 */

/** One selectable contact, exactly as `GET /contact-options` serves it. */
export interface MillContact {
  clientContactId: number;
  contactName: string;
}

/**
 * A tracked mill a mills scenario operates on, with the at-rest state the preflight asserts and the
 * cleanup restores. `clientNumber` / `clientLocnCode` are the mill's client location — the BR-09 join
 * that decides which contacts it is offered — and are pinned so the CI seed can be checked against them.
 */
export interface AdminMillAnchor {
  millId: number;
  millNumber: string;
  millName: string;
  /** `ACT` or `CLS` — the code, which is what the page branches on. */
  statusCode: 'ACT' | 'CLS';
  /** The code table's own label, rendered verbatim ("Active" / "Close"). */
  statusDescription: 'Active' | 'Close';
  clientNumber: string;
  clientLocnCode: string;
  /** Every contact on the mill's client location, in the server's CONTACT_NAME order. */
  contacts: readonly MillContact[];
  /** The at-rest editable panel — what the preflight asserts and the cleanup restores. */
  atRest: {
    headOfficeContactInd: 'Y' | 'N';
    headOfficeContactId: number | null;
    divisionContactId: number | null;
  };
  /**
   * Present ONLY on a mill whose STATUS a scenario changes (S03 / S04 / S12). Such a mill's
   * `statusCode` is restored by the status cleanup, and its fixture is also who is actively
   * assigned to it — the deactivate is refused while anyone is — so that set is pinned exactly.
   * Absent on S01's mill on purpose: 25050 is a schedule anchor whose associations differ between
   * the extract (three real users) and CI (the mock submitter only), and S01 never reads them.
   */
  status?: {
    /** Every user GUID with an ACTIVE assignment at rest, sorted. Empty for "no active users". */
    activeUserGuids: readonly string[];
    /**
     * Every user GUID with an ENDED assignment at rest, sorted. The S09 / S13 activate targets —
     * the cleanup ends them again, and the preflight fails if one is found active.
     */
    endedUserGuids: readonly string[];
  };
  /**
   * The mill's current-year report-record set at rest — what activate branches on. Defaults to
   * `complete` (status row + eleven categories): activate then writes the status alone. Only the two
   * GAP-6 mills differ: `none` is what activate ENROLS, `partial` (the status row alone) what it REFUSES.
   */
  currentYearRecords?: 'complete' | 'none' | 'partial';
}

/** The contacts on 25050's client location, 00001500/00 (REVELSTOKE DIVISION). */
const CONTACT_ADMN_1: MillContact = { clientContactId: 2609, contactName: 'ADMN CONTACT 1' };
const CONTACT_OPERATIONS_2: MillContact = {
  clientContactId: 2617,
  contactName: 'OPERATIONS - CONTACT 2',
};

/**
 * S01 — "Update an Existing Mill's Head-Office Indicator and Contacts".
 *
 * At rest: head office Y, head-office contact 2609, division contact 2617 (read from
 * THE.ILCR_MILL_STATUS_XREF where ILCR_MILL_STATUS_XREF_ID = 25050, 2026-09-28).
 * `S01_EDIT` below inverts all three, so every one of the three columns demonstrably moves.
 */
export const S01_MILL: AdminMillAnchor = {
  millId: 25050,
  millNumber: '9171',
  millName: 'BCOVEY-TEST',
  statusCode: 'ACT',
  statusDescription: 'Active',
  clientNumber: '00001500',
  clientLocnCode: '00',
  // The server orders options by CONTACT_NAME (22.1 D4a): "ADMN…" < "OPERATIONS…".
  contacts: [CONTACT_ADMN_1, CONTACT_OPERATIONS_2],
  atRest: {
    headOfficeContactInd: 'Y',
    headOfficeContactId: CONTACT_ADMN_1.clientContactId,
    divisionContactId: CONTACT_OPERATIONS_2.clientContactId,
  },
};

/**
 * The edit S01 saves: the indicator flipped and the two contacts SWAPPED. Swapping, rather than
 * picking fresh values, is what lets one mill with exactly two contacts prove both dropdowns write
 * their own column — a save that wrote one selection into both slots would fail the read-back.
 */
export const S01_EDIT = {
  headOfficeLabel: 'No',
  headOfficeContactInd: 'N',
  headOfficeContact: CONTACT_OPERATIONS_2,
  divisionContact: CONTACT_ADMN_1,
} as const;

/*
 * ---------------------------------------------------------------------------------------------------
 * THE STATUS ANCHORS — S03 / S04 / S12, one mill each, all SEEDED
 * ---------------------------------------------------------------------------------------------------
 * No extract mill can take a status change (surveyed 2026-09-28): every ACT mill is a schedule anchor
 * with active users, and the one unpinned mill (14050, CLS) has no current-year report records, so
 * activating it would enrol it — rows no endpoint removes. So these come from
 * real-test-data-patches/mill/mill-status-anchors.sql, folded into R__80 in the same change.
 *
 * Each carries a COMPLETE current-year record set (status row + eleven category rows). That is the
 * load-bearing property: `activate` then writes the status alone, so every cleanup is an exact API
 * round trip. The preflight checks the status row is still there for the current year.
 *
 * One mill per scenario, so parallel workers never share a REVISION_COUNT. The client location is
 * 25050's (the delivery THE.MILL requires one), which is why the contacts below are S01's.
 */

/**
 * The seeded licensees — synthetic GUIDs, never real directory identifiers, all role LICENSEE.
 * `licensee(n)` is `E2E0000000000000000000000000000<n>`, exactly as the patch builds them.
 * They MUST be 32 hex characters, like real directory GUIDs: the Users page route drops any
 * `?userGuid=` that is not (`routes/mill-associations.tsx` USER_GUID_PATTERN), and S10's View would
 * then land with nobody carried. "E2E" is hex, which keeps them recognisable.
 */
const licensee = (n: number): string => `E2E0000000000000000000000000000${n}`;

/** S12's one active licensee. */
export const S12_LICENSEE_GUID = licensee(1);
/** S08 deactivates this one (26053, ACTIVE at rest). */
export const S08_LICENSEE_GUID = licensee(2);
/** S09 activates this one (26058, ENDED at rest). */
export const S09_LICENSEE_GUID = licensee(3);
/** S13 tries to activate this one on a closed mill (26054, ENDED at rest). */
export const S13_LICENSEE_GUID = licensee(4);
/** S07 re-adds and S10 views this one (26055, ACTIVE at rest) — read-only. */
export const READONLY_LICENSEE_GUID = licensee(5);
/** S05 adds this one to 26056. Seeded as an account with NO association anywhere. */
export const S05_LICENSEE_GUID = licensee(6);
/** GAP-7 adds this one to 26063. NOT seeded at all — no ILCR_USER row — so the add provisions it. */
export const NEW_ACCOUNT_GUID = licensee(7);

const statusAnchor = (
  millId: number,
  millNumber: string,
  millName: string,
  statusCode: 'ACT' | 'CLS',
  activeUserGuids: readonly string[],
  endedUserGuids: readonly string[] = [],
  extra: Partial<Pick<AdminMillAnchor, 'atRest' | 'currentYearRecords'>> = {},
): AdminMillAnchor => ({
  millId,
  millNumber,
  millName,
  statusCode,
  statusDescription: statusCode === 'ACT' ? 'Active' : 'Close',
  clientNumber: '00001500',
  clientLocnCode: '00',
  contacts: [CONTACT_ADMN_1, CONTACT_OPERATIONS_2],
  // Seeded with the indicator set and no contact; no status slice touches the panel.
  atRest: { headOfficeContactInd: 'Y', headOfficeContactId: null, divisionContactId: null },
  status: { activeUserGuids, endedUserGuids },
  ...extra,
});

/** S03 — "Deactivate (Close) an Active Mill With No Active Users". */
export const S03_MILL = statusAnchor(26050, '9181', 'E2E-DEACTIVATE-TEST', 'ACT', []);

/** S04 — "Activate a Closed Mill". */
export const S04_MILL = statusAnchor(26051, '9182', 'E2E-ACTIVATE-TEST', 'CLS', []);

/** S12 — "Deactivation Blocked by Active Users", then deactivated once its one user is. */
export const S12_MILL = statusAnchor(26052, '9183', 'E2E-BLOCKED-TEST', 'ACT', [S12_LICENSEE_GUID]);

/*
 * THE ASSOCIATION-PANEL MILLS — same patch, same shape, and one mill per WRITER again. S08 and S09
 * each have their own: they touch different rows, but every precondition and cleanup checks the mill's
 * WHOLE user set, so one's activated user would fail the other's check mid-run (seen 2026-09-29 as a
 * cleanup red under parallel repeats). S07, S10 and the a11y sweeps share 26055 because none writes.
 */

/** S08 — deactivate an active association on an Active mill. */
export const USERS_MILL = statusAnchor(26053, '9184', 'E2E-USERS-TEST', 'ACT', [S08_LICENSEE_GUID]);

/** S09 — activate an ended association on an Active mill. */
export const ACTIVATE_USER_MILL = statusAnchor(
  26058,
  '9189',
  'E2E-ACTIVATE-USER-TEST',
  'ACT',
  [],
  [S09_LICENSEE_GUID],
);

/** S13 — activating an association on a CLOSED mill is refused; activating the mill first lets it. */
export const CLOSED_USERS_MILL = statusAnchor(
  26054,
  '9185',
  'E2E-CLOSED-USERS-TEST',
  'CLS',
  [],
  [S13_LICENSEE_GUID],
);

/** S07 / S10 — READ-ONLY: the duplicate add writes nothing, and View only navigates. */
export const READONLY_USERS_MILL = statusAnchor(
  26055,
  '9186',
  'E2E-READONLY-USERS-TEST',
  'ACT',
  [READONLY_LICENSEE_GUID],
);

/** S05 — add a user. No users at rest; the scenario's one new row is deleted by its cleanup. */
export const ADD_USER_MILL = statusAnchor(26056, '9187', 'E2E-ADD-USER-TEST', 'ACT', []);

/*
 * THE COVERAGE-GAP MILLS (defects.md GAP-1, -4, -6, -7) — same patch, one per concern.
 */

/** GAP-1 — clear a contact to "(None)". Both contacts set at rest; the contacts cleanup restores them. */
export const CONTACTS_MILL = statusAnchor(26059, '9190', 'E2E-CONTACTS-TEST', 'ACT', [], [], {
  atRest: {
    headOfficeContactInd: 'Y',
    headOfficeContactId: CONTACT_ADMN_1.clientContactId,
    divisionContactId: CONTACT_OPERATIONS_2.clientContactId,
  },
});

/** GAP-4 — a Closed mill NO scenario writes, so a Status search can rely on it staying Closed. */
export const STATUS_SEARCH_MILL = statusAnchor(26060, '9191', 'E2E-STATUS-SEARCH-TEST', 'CLS', []);

/** GAP-6 — Closed with NO current-year records: activate enrols it (rows the DB cleanup deletes). */
export const ENROL_MILL = statusAnchor(26061, '9192', 'E2E-ENROL-TEST', 'CLS', [], [], {
  currentYearRecords: 'none',
});

/** GAP-6 — Closed with the current-year status row ALONE: activate refuses it and rolls back. */
export const PARTIAL_MILL = statusAnchor(26062, '9193', 'E2E-PARTIAL-TEST', 'CLS', [], [], {
  currentYearRecords: 'partial',
});

/** GAP-7 — add a user who has no ILCR account yet. No users at rest. */
export const ADD_NEW_USER_MILL = statusAnchor(26063, '9194', 'E2E-ADD-NEW-USER-TEST', 'ACT', []);

/** `error.mill.activate.partialrecords` (messages.properties:616), verbatim. */
export const PARTIAL_RECORDS_ERROR =
  "The selected mill's report records for the current year are incomplete, so it cannot be activated. "
  + 'Please refer to logs.';

/** The mills whose editable PANEL a scenario saves, by the id the feature names them with. */
export const CONTACTS_MILLS: Readonly<Record<string, AdminMillAnchor>> = {
  S01: S01_MILL,
  'GAP-1': CONTACTS_MILL,
};

/** The dedicated mills by the slice id the feature names them with. */
export const STATUS_MILLS: Readonly<Record<string, AdminMillAnchor>> = {
  S03: S03_MILL,
  S04: S04_MILL,
  S12: S12_MILL,
  S05: ADD_USER_MILL,
  S07: READONLY_USERS_MILL,
  S08: USERS_MILL,
  S09: ACTIVATE_USER_MILL,
  S10: READONLY_USERS_MILL,
  S13: CLOSED_USERS_MILL,
  'GAP-1': CONTACTS_MILL,
  'GAP-4': STATUS_SEARCH_MILL,
  'GAP-6-enrol': ENROL_MILL,
  'GAP-6-partial': PARTIAL_MILL,
  'GAP-7': ADD_NEW_USER_MILL,
};

/** Every mill a mills scenario operates on — the preflight's and the CI-seed gate's input. */
export const ADMIN_MILL_ANCHORS: readonly AdminMillAnchor[] = [
  S01_MILL,
  S03_MILL,
  S04_MILL,
  S12_MILL,
  USERS_MILL,
  ACTIVATE_USER_MILL,
  CLOSED_USERS_MILL,
  READONLY_USERS_MILL,
  ADD_USER_MILL,
  CONTACTS_MILL,
  STATUS_SEARCH_MILL,
  ENROL_MILL,
  PARTIAL_MILL,
  ADD_NEW_USER_MILL,
];

/**
 * S02 / S14 — the IMPORTABLE mill: a THE.MILL row and nothing else, so it is listed by the importable
 * search and by no tracked-mill surface. NOT in ADMIN_MILL_ANCHORS, whose members are all tracked; the
 * preflight and the parity gate check it has NO xref instead. Importing it creates the xref (CLS, head
 * office Y — BR-03) and its current-year records, which `scripts/mill_db_restore.py forget-import`
 * deletes afterwards.
 */
export const IMPORT_MILL = { millId: 26057, millNumber: '9188', millName: 'E2E-IMPORT-TEST' } as const;

/**
 * A directory record for the Find and Add User stub. The directory (NR User Lookup, DL-27) is OFF in
 * every environment (`ilcr.user-lookup.enabled: false`, so `/api/v1/users/lookup` 404s), so S05 / S07 /
 * S10 answer that ONE browser request with a record shaped exactly like the backend's `DirectoryUser`.
 * Everything after the pick — the add, the association list, the messages — is the real backend.
 */
export interface DirectoryUserRecord {
  userGuid: string;
  displayName: string;
  idpUsername: string;
  identityProvider: string;
  firstName: string;
  lastName: string;
}

export const directoryUser = (userGuid: string, n: number): DirectoryUserRecord => ({
  userGuid,
  displayName: `E2E Licensee ${n}`,
  idpUsername: `e2elicensee${n}`,
  identityProvider: 'BCEIDBUSINESS',
  firstName: 'E2E',
  lastName: `Licensee ${n}`,
});

/** The picker's option label (DirectoryPicker.tsx `candidateLabel`): `displayName (idpUsername)`. */
export const candidateLabel = (u: DirectoryUserRecord): string => `${u.displayName} (${u.idpUsername})`;

/**
 * The page's own "no contact" option label (components/mills/index.tsx NO_CONTACT). Every contact
 * dropdown leads with it, so the full option list is `["(None)", ...contacts]`.
 */
export const NO_CONTACT_LABEL = '(None)';

/** SUC-001, `mill.updated` (messages.properties:586) — `Mill {0} - {1} has been saved.` */
export const millSavedMessage = (mill: Pick<AdminMillAnchor, 'millNumber' | 'millName'>): string =>
  `Mill ${mill.millNumber} - ${mill.millName} has been saved.`;

/** SUC-002, `mill.expired` (messages.properties:587) — `Mill {0} - {1} has been deactivated.` */
export const millDeactivatedMessage = (mill: Pick<AdminMillAnchor, 'millNumber' | 'millName'>): string =>
  `Mill ${mill.millNumber} - ${mill.millName} has been deactivated.`;

/** SUC-003, `mill.activated` (messages.properties:588) — `Mill {0} - {1} has been activated.` */
export const millActivatedMessage = (mill: Pick<AdminMillAnchor, 'millNumber' | 'millName'>): string =>
  `Mill ${mill.millNumber} - ${mill.millName} has been activated.`;

/** S12, `error.mill.deactivate.hasactiveusers` (messages.properties:597), verbatim. */
export const MILL_HAS_ACTIVE_USERS_ERROR =
  'The selected mill has active users, you must deactivate them first.';

/** ERR-002, `millNotActiveForCurrentYearMsg` (messages.properties:10) — what BR-06's 409 carries. */
export const MILL_NOT_ACTIVE_MESSAGE =
  'This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.';

/** The admin mill surface, through Vite's /api proxy. */
export const ADMIN_MILLS_URL = '/api/v1/admin/mills';
export const millUrl = (millId: number): string => `${ADMIN_MILLS_URL}/${millId}`;
export const contactOptionsUrl = (millId: number): string => `${millUrl(millId)}/contact-options`;
export const saveContactsUrl = (millId: number): string => `${millUrl(millId)}/contacts`;
export const millStatusUrl = (millId: number, action: 'activate' | 'deactivate'): string =>
  `${millUrl(millId)}/${action}`;
export const millUsersUrl = (millId: number): string => `${millUrl(millId)}/users`;
export const millUserStatusUrl = (
  millId: number,
  userGuid: string,
  action: 'activate' | 'deactivate',
): string => `${millUsersUrl(millId)}/${userGuid}/${action}`;
/** The working-context resolve — its `millViewable` IS BR-06, and a status row makes it report tracks. */
export const millContextUrl = (millId: number, year: number): string =>
  `/api/v1/mill-context?millId=${millId}&year=${year}`;
/** Opened reporting years; the current one is the highest (ReportingYearService.currentReportingYear). */
export const REPORTING_YEARS_URL = '/api/v1/admin/reporting-years';
