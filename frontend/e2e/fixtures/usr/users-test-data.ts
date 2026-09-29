/**
 * User administration (UC-USR-001 / UC-USR-002) — the ONE place the Users-page anchors are pinned.
 *
 * Every row here is SEEDED: real-test-data-patches/usr/user-admin-anchors.sql locally, folded into
 * db-e2e/R__80_e2e_anchor_seed.sql for CI ("User administration — the dedicated users and mills"), and
 * checked against that seed by preflight/ci-seed-parity.setup.ts and against the live DB by
 * preflight/usr-anchors.setup.ts. Why real data fell short is in the patch header: every extract
 * account is some other scenario's fixture, and the account flag has no read endpoint, so a shared
 * account is a race no read-back can see.
 *
 * Finding query (the at-rest state below, one row per seeded user):
 *   SELECT u.USER_GUID, u.ACTIVE_IND,
 *          (SELECT LISTAGG(x.ILCR_MILL_ID || NVL2(x.INACTIVE_DATE, ':E', ':A'), ',')
 *             FROM THE.ILCR_MILL_USER_XREF x WHERE x.USER_GUID = u.USER_GUID) assignments
 *     FROM THE.ILCR_USER u WHERE u.ENTRY_USERID = 'E2E_SEED_USRADM' ORDER BY 1;
 *
 * Users are LICENSEES only (DL-23 retired the Auditor row) and their GUIDs are synthetic — 32 hex
 * characters, because the Users route drops a carried `?userGuid=` that is not (routes/
 * mill-associations.tsx USER_GUID_PATTERN). They are never real directory identifiers: the directory is
 * off in every environment (DL-27), so each scenario answers the lookup itself (`directoryRecord`).
 */

/** The seeded mills — all ACT, all listed by the Add-mill dropdown (a current-year report-status row). */
export type UsrMill = {
  readonly millId: number;
  readonly millNumber: string;
  readonly millName: string;
};

const mill = (millId: number, millNumber: string, millName: string): UsrMill => ({
  millId,
  millNumber,
  millName,
});

export const JOURNEY_MILL_A = mill(26064, '9195', 'E2E-USR-JOURNEY-A');
export const JOURNEY_MILL_B = mill(26065, '9196', 'E2E-USR-JOURNEY-B');
export const REACTIVATE_MILL = mill(26066, '9197', 'E2E-USR-REACTIVATE');
export const ADD_TARGET_MILL = mill(26067, '9198', 'E2E-USR-ADD-TARGET');
export const READONLY_MILL = mill(26068, '9199', 'E2E-USR-READONLY');
export const ACCOUNT_MILL = mill(26069, '9200', 'E2E-USR-ACCOUNT');
export const ADD_FROM_MILL = mill(26070, '9201', 'E2E-USR-ADD-FROM');
export const ROWS_MILL = mill(26071, '9202', 'E2E-USR-ROWS');
export const BLOCK_MILL_A = mill(26072, '9203', 'E2E-USR-BLOCK-A');
export const BLOCK_MILL_B = mill(26073, '9204', 'E2E-USR-BLOCK-B');

export const USR_MILLS: readonly UsrMill[] = [
  JOURNEY_MILL_A,
  JOURNEY_MILL_B,
  REACTIVATE_MILL,
  ADD_TARGET_MILL,
  READONLY_MILL,
  ACCOUNT_MILL,
  ADD_FROM_MILL,
  ROWS_MILL,
  BLOCK_MILL_A,
  BLOCK_MILL_B,
];

/** `E2E000000000000000000000000000<nn>` — 30 characters of prefix, two of index. */
export const usrGuid = (n: number): string => `E2E000000000000000000000000000${n}`;

/**
 * One seeded user and its at-rest state. `account` is the ILCR_USER.ACTIVE_IND, or `null` for a user
 * with NO account (the first-time-import cases). `active` / `ended` are mill ids.
 */
export type UsrAnchor = {
  readonly key: string;
  readonly n: number;
  readonly userGuid: string;
  readonly account: 'Y' | 'N' | null;
  readonly active: readonly number[];
  readonly ended: readonly number[];
};

const anchor = (
  key: string,
  n: number,
  account: 'Y' | 'N' | null,
  active: readonly UsrMill[] = [],
  ended: readonly UsrMill[] = [],
): UsrAnchor => ({
  key,
  n,
  userGuid: usrGuid(n),
  account,
  active: active.map((m) => m.millId),
  ended: ended.map((m) => m.millId),
});

/** Keyed by the name a feature file uses: "Given the <key> user is at rest". */
export const USR_ANCHORS: Record<string, UsrAnchor> = {
  journey: anchor('journey', 11, 'N'),
  reactivate: anchor('reactivate', 12, 'Y', [], [REACTIVATE_MILL]),
  'first-activate': anchor('first-activate', 13, null),
  'first-add': anchor('first-add', 14, null),
  'read-only': anchor('read-only', 15, 'Y', [READONLY_MILL]),
  'no-mills': anchor('no-mills', 16, 'Y'),
  'account-inactive': anchor('account-inactive', 17, 'N', [], [ACCOUNT_MILL]),
  'account-active': anchor('account-active', 18, 'Y', [], [ACCOUNT_MILL]),
  'add-from-mill': anchor('add-from-mill', 19, 'Y', [ADD_FROM_MILL]),
  'row-ended': anchor('row-ended', 20, 'Y', [], [ROWS_MILL]),
  'row-active': anchor('row-active', 21, 'Y', [ROWS_MILL]),
  blocked: anchor('blocked', 22, 'Y', [BLOCK_MILL_A, BLOCK_MILL_B]),
};

export const usrMillByNumber = (millNumber: string): UsrMill => {
  const found = USR_MILLS.find((m) => m.millNumber === millNumber);
  if (!found) throw new Error(`mill ${millNumber} is not one of the seeded user-admin mills`);
  return found;
};

export const usrAnchorByGuid = (userGuid: string): UsrAnchor => {
  const found = Object.values(USR_ANCHORS).find((a) => a.userGuid === userGuid);
  if (!found) throw new Error(`${userGuid} is not one of the seeded user-admin licensees`);
  return found;
};

/**
 * The assignment pairs a scenario may ADD, and the accounts an add or activate may PROVISION — the two
 * writes no endpoint undoes. scripts/usr_db_restore.py refuses anything else, so keep the two in step.
 */
export const ADDABLE_ASSIGNMENTS: readonly { millId: number; userGuid: string }[] = [
  { millId: JOURNEY_MILL_A.millId, userGuid: USR_ANCHORS.journey.userGuid },
  { millId: JOURNEY_MILL_B.millId, userGuid: USR_ANCHORS.journey.userGuid },
  { millId: ADD_TARGET_MILL.millId, userGuid: USR_ANCHORS['first-add'].userGuid },
  { millId: ADD_TARGET_MILL.millId, userGuid: USR_ANCHORS['add-from-mill'].userGuid },
];
export const PROVISIONABLE_ACCOUNTS: readonly string[] = [
  USR_ANCHORS['first-activate'].userGuid,
  USR_ANCHORS['first-add'].userGuid,
];

/**
 * A GUID that is well-formed (32 hex) but that neither the directory stand-in nor the DB knows —
 * the "departed user" a stale carry names. Checked absent by preflight/usr-anchors.setup.ts.
 */
export const UNKNOWN_GUID = usrGuid(99);

// ---- the directory stand-in ----

/** One directory record, in the backend's `DirectoryUser` shape (userlookup/api/UserLookupApi). */
export type DirectoryUser = {
  userGuid: string;
  displayName: string;
  idpUsername: string;
  identityProvider: 'BCEIDBUSINESS';
  firstName: string;
  lastName: string;
};

/** The record the stand-in serves for a seeded user: BCeID Business, as every licensee is. */
export const directoryRecord = (a: UsrAnchor): DirectoryUser => ({
  userGuid: a.userGuid,
  displayName: `E2E User ${a.n}`,
  idpUsername: `e2euser${a.n}`,
  identityProvider: 'BCEIDBUSINESS',
  firstName: 'E2E',
  lastName: `User ${a.n}`,
});

/** How the picker lists a candidate (DirectoryPicker.tsx candidateLabel). */
export const candidateLabel = (u: DirectoryUser): string => `${u.displayName} (${u.idpUsername})`;

/** How the Add-mill dropdown lists a mill (components/millAssociations/index.tsx). */
export const millOptionLabel = (m: UsrMill): string => `${m.millNumber} - ${m.millName} (ACT)`;

// ---- the verbatim messages (messages.properties; the name positions are empty — deviation (H)) ----

export const userActivated = (guid: string): string => `User ${guid} -   has been activated.`;
export const userDeactivated = (guid: string): string => `User ${guid} -   has been deactivated.`;
export const millActivatedFor = (m: UsrMill, guid: string): string =>
  `Mill ${m.millNumber} - ${m.millName} has been activated for user ${guid} -  .`;
export const millDeactivatedFor = (m: UsrMill, guid: string): string =>
  `Mill ${m.millNumber} - ${m.millName} has been deactivated for user ${guid} -  .`;
export const alreadyAssigned = (guid: string, m: UsrMill): string =>
  `User ${guid} is already associated to mill ${m.millName}. Please verify.`;
export const deactivateBlocked = (guid: string): string =>
  `User ${guid} -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. `
  + "All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted.";

/** The picker's no-match note — recast from the legacy ADAM wording, deviation (C). */
export const NO_DIRECTORY_MATCH =
  'No user matching this criteria has been found. Please ensure the user you are looking for has been '
  + 'granted the ILCR Submitter role.';
export const CARRIED_USER_FAILED =
  'The user carried over from the Mills page could not be looked up, so no user is selected.';

export const USERS_URL = '/mill-associations';
