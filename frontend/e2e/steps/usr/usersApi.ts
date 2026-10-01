import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { type APIRequestContext, expect } from '@playwright/test';
import { MOCK_GROUPS_HEADER } from '../../pages/common/mockUser';
import { type UsrAnchor, USR_MILLS } from '../../fixtures/usr/users-test-data';

/**
 * User administration API reads and writes — the black-box read-back the Thens assert against, and the
 * restore the cleanup registry calls. The surface is AssignmentApi (`/api/v1/submitters/...`,
 * `/api/v1/mills/{id}/submitters`), the one the Users page drives; the Mills page's
 * `/api/v1/admin/mills/{id}/users` is UC-MILL-001's and is not used here.
 *
 * EVERY CALL CARRIES `X-Mock-Groups: ILCR_ADMIN` EXPLICITLY, for the reason steps/mill/millsApi.ts
 * gives: the `request` fixture is not the browser and inherits no mock user, and every endpoint here is
 * behind the ADMIN-only MAINTAIN_USERS action (a bare call answers 403).
 *
 * THE ACCOUNT FLAG HAS NO READ ENDPOINT (Story 23.3 deviation (O)), so `readAccount` goes to the DB
 * through scripts/usr_db_restore.py — the one read here that is not black-box.
 */

const ADMIN = { [MOCK_GROUPS_HEADER]: 'ILCR_ADMIN' };

/** One assignment as `GET /api/v1/submitters/{guid}/mills?includeEnded=true` serves it. */
export interface Assignment {
  userGuid: string;
  millId: number;
  millNumber: string;
  millName: string;
  status: 'ACTIVE' | 'ENDED';
  activeDate?: string | null;
  inactiveDate?: string | null;
  revisionCount: number;
}

export const assignmentsUrl = (guid: string): string =>
  `/api/v1/submitters/${guid}/mills?includeEnded=true`;
const assignUrl = (millId: number): string => `/api/v1/mills/${millId}/submitters`;
const endUrl = (millId: number, guid: string): string => `/api/v1/mills/${millId}/submitters/${guid}`;
export const accountUrl = (guid: string): string => `/api/v1/submitters/${guid}`;

export async function readAssignments(request: APIRequestContext, guid: string): Promise<Assignment[]> {
  const res = await request.get(assignmentsUrl(guid), { headers: ADMIN });
  expect(res.status(), `GET ${assignmentsUrl(guid)}`).toBe(200);
  return (await res.json()) as Assignment[];
}

/** `{ active: [...millIds], ended: [...millIds] }`, each sorted. */
export async function assignmentState(
  request: APIRequestContext,
  guid: string,
): Promise<{ active: number[]; ended: number[] }> {
  const rows = await readAssignments(request, guid);
  const ids = (s: Assignment['status']) =>
    rows.filter((r) => r.status === s).map((r) => r.millId).sort((a, b) => a - b);
  return { active: ids('ACTIVE'), ended: ids('ENDED') };
}

/** POST — add a new assignment, or revive an ended one. */
export async function assign(request: APIRequestContext, millId: number, guid: string): Promise<void> {
  const res = await request.post(assignUrl(millId), { headers: ADMIN, data: { userGuid: guid } });
  expect(res.status(), `POST ${assignUrl(millId)} for ${guid}`).toBe(200);
}

/** PATCH — end an active assignment, at its current revision. */
export async function endAssignment(
  request: APIRequestContext,
  millId: number,
  guid: string,
): Promise<void> {
  const row = (await readAssignments(request, guid)).find((r) => r.millId === millId);
  expect(row, `${guid} has no assignment on mill ${millId} to end`).toBeTruthy();
  const res = await request.patch(endUrl(millId, guid), {
    headers: ADMIN,
    data: { revisionCount: row!.revisionCount },
  });
  expect(res.status(), `PATCH ${endUrl(millId, guid)}`).toBe(200);
}

/** PATCH — switch the account flag. Deactivate is refused (409) while any assignment is active. */
export async function setAccount(
  request: APIRequestContext,
  guid: string,
  active: boolean,
): Promise<void> {
  const res = await request.patch(accountUrl(guid), { headers: ADMIN, data: { active } });
  expect(res.status(), `PATCH ${accountUrl(guid)} {active: ${active}}`).toBe(200);
}

// ---- the DB bridge (scripts/usr_db_restore.py) ----

const BRIDGE = fileURLToPath(new URL('../../scripts/usr_db_restore.py', import.meta.url));

/** The reproducible venv first (`npm run setup:python`), as schedule1DbRestore.ts does. */
function resolvePython(): string {
  if (process.env.PYTHON) return process.env.PYTHON;
  const venvPosix = fileURLToPath(new URL('../../scripts/.venv/bin/python', import.meta.url));
  const venvWin = fileURLToPath(new URL('../../scripts/.venv/Scripts/python.exe', import.meta.url));
  if (existsSync(venvPosix)) return venvPosix;
  if (existsSync(venvWin)) return venvWin;
  return 'python';
}

const bridge = (...args: string[]): string =>
  execFileSync(resolvePython(), [BRIDGE, ...args], { stdio: 'pipe' }).toString().trim();

/** ILCR_USER.ACTIVE_IND, or `null` when the user has no account. */
export const readAccount = (guid: string): 'Y' | 'N' | null => {
  const out = bridge('read-account', guid);
  if (out === 'NONE') return null;
  expect(['Y', 'N'], `read-account ${guid} printed ${out}`).toContain(out);
  return out as 'Y' | 'N';
};

export const dropAssignment = (millId: number, guid: string): void => {
  bridge('drop-assignment', String(millId), guid);
};

export const dropAccount = (guid: string): void => {
  bridge('drop-account', guid);
};

// ---- the restore ----

/**
 * Put a seeded user back to its pinned at-rest state and assert it. Order matters:
 *  1. assignments the scenario ADDED (not pinned at all) are deleted at the DB — no endpoint can;
 *  2. pinned-ended rows that are active are ended, pinned-active rows that are ended are revived;
 *  3. the account flag is put back — deactivation last, because it is refused while any row is
 *     active, and an account the scenario PROVISIONED is deleted at the DB.
 * Every step is a no-op on an at-rest user, so registering early costs nothing.
 */
export async function restoreUser(request: APIRequestContext, a: UsrAnchor): Promise<void> {
  const pinned = new Set([...a.active, ...a.ended]);
  for (const row of await readAssignments(request, a.userGuid)) {
    if (!pinned.has(row.millId)) dropAssignment(row.millId, a.userGuid);
  }
  const now = await assignmentState(request, a.userGuid);
  for (const millId of a.ended) {
    if (now.active.includes(millId)) await endAssignment(request, millId, a.userGuid);
  }
  for (const millId of a.active) {
    if (now.ended.includes(millId)) await assign(request, millId, a.userGuid);
  }

  const account = readAccount(a.userGuid);
  if (a.account === null) {
    if (account !== null) dropAccount(a.userGuid);
  } else if (account !== a.account) {
    await setAccount(request, a.userGuid, a.account === 'Y');
  }

  const after = await assignmentState(request, a.userGuid);
  expect(after, `${a.key} user's assignments after restore`).toEqual({
    active: [...a.active].sort((x, y) => x - y),
    ended: [...a.ended].sort((x, y) => x - y),
  });
  expect(readAccount(a.userGuid), `${a.key} user's account after restore`).toBe(a.account);
}

/** Names a mill id for a message: `26064 (9195 E2E-USR-JOURNEY-A)`. */
export const millLabel = (millId: number): string => {
  const m = USR_MILLS.find((x) => x.millId === millId);
  return m ? `${millId} (${m.millNumber} ${m.millName})` : String(millId);
};
