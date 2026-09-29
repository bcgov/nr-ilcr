import { type APIRequestContext, expect } from '@playwright/test';
import { MOCK_GROUPS_HEADER } from '../../pages/common/mockUser';
import {
  type AdminMillAnchor,
  type MillContact,
  REPORTING_YEARS_URL,
  contactOptionsUrl,
  millContextUrl,
  millStatusUrl,
  millUrl,
  millUserStatusUrl,
  millUsersUrl,
  saveContactsUrl,
} from '../../fixtures/mill/mills-test-data';

/**
 * Mill administration API reads and writes — the black-box read-back the Thens assert against, and the
 * restore the cleanup registry calls.
 *
 * EVERY CALL CARRIES `X-Mock-Groups: ILCR_ADMIN` EXPLICITLY. The whole surface sits behind the
 * ADMIN-only MAINTAIN_MILLS action, and Playwright's `request` fixture is not the browser: it has no
 * localStorage, so the mock user the page was seeded with does not reach it, and the backend's
 * security-off default principal is not an administrator (a bare GET answers 403). Stating the role on
 * the call is the same "declare the identity, never inherit it" rule `pages/common/mockUser.ts` makes
 * for the browser.
 *
 * WHY READ BACK AT ALL, when the panel already shows the saved values: after Save the page installs the
 * RESPONSE's mill and resets its form to it (components/mills/index.tsx save → applyMillWrite +
 * formFor), so the panel looks identical whether the row reached the database or the response was
 * merely echoed. A UI-only assertion proves rendering, not persistence.
 */

const ADMIN = { [MOCK_GROUPS_HEADER]: 'ILCR_ADMIN' };

/** One tracked mill as `GET /api/v1/admin/mills/{id}` serves it. Nulls arrive ABSENT (non_null). */
export interface AdminMillRecord {
  millId: number;
  revisionCount: number;
  millNumber?: string;
  millName?: string;
  millStatusCode?: string;
  statusDescription?: string;
  headOfficeContactInd?: string;
  headOfficeContactId?: number;
  divisionContactId?: number;
  updateUserid?: string;
  updateTimestamp?: string;
}

/** GET one tracked mill, failing with the status when it does not resolve. */
export async function readMill(request: APIRequestContext, millId: number): Promise<AdminMillRecord> {
  const res = await request.get(millUrl(millId), { headers: ADMIN });
  await expect(res, `GET admin mill ${millId} -> HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as AdminMillRecord;
}

/** GET the contacts a mill's two dropdowns are offered (BR-09: its own client location only). */
export async function readContactOptions(
  request: APIRequestContext,
  millId: number,
): Promise<MillContact[]> {
  const res = await request.get(contactOptionsUrl(millId), { headers: ADMIN });
  await expect(res, `GET contact options for mill ${millId} -> HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as MillContact[];
}

/**
 * Put a mill's editable panel back to its pinned at-rest values — the cleanup.
 *
 * Reads first, then writes with the revision it just READ: the save is optimistically locked, and the
 * scenario's own save has already moved the revision, so any remembered value would 409. A no-op when
 * the row is already at rest (a scenario that failed before its Save), so the cleanup never adds a
 * spurious audit stamp.
 */
export async function restoreMillContacts(
  request: APIRequestContext,
  anchor: AdminMillAnchor,
): Promise<void> {
  const current = await readMill(request, anchor.millId);
  if (atRest(current, anchor)) {
    return;
  }
  const res = await request.put(saveContactsUrl(anchor.millId), {
    headers: ADMIN,
    data: { ...anchor.atRest, revisionCount: current.revisionCount },
  });
  await expect(res, `restore PUT for mill ${anchor.millId} -> HTTP ${res.status()}`).toBeOK();

  const after = await readMill(request, anchor.millId);
  expect(
    atRest(after, anchor),
    `mill ${anchor.millId} did not return to its at-rest panel: ${JSON.stringify(after)}`,
  ).toBe(true);
}

/** Whether a served mill holds exactly the anchor's at-rest indicator and contacts. */
export function atRest(mill: AdminMillRecord, anchor: AdminMillAnchor): boolean {
  return (
    mill.headOfficeContactInd === anchor.atRest.headOfficeContactInd
    && (mill.headOfficeContactId ?? null) === anchor.atRest.headOfficeContactId
    && (mill.divisionContactId ?? null) === anchor.atRest.divisionContactId
  );
}

// ---- status (S03 / S04 / S12) ----

/** One user assignment on a mill, as `GET /api/v1/admin/mills/{id}/users` serves it. */
export interface MillUserRecord {
  userGuid: string;
  status: 'ACTIVE' | 'ENDED';
  revisionCount: number;
}

/** GET a mill's user assignments (the Associated Licensee User table's rows). */
export async function readMillUsers(
  request: APIRequestContext,
  millId: number,
): Promise<MillUserRecord[]> {
  const res = await request.get(millUsersUrl(millId), { headers: ADMIN });
  await expect(res, `GET users of mill ${millId} -> HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as MillUserRecord[];
}

/** The GUIDs with an ACTIVE assignment, sorted — the shape `AdminMillAnchor.status` pins. */
export async function activeUserGuids(request: APIRequestContext, millId: number): Promise<string[]> {
  return (await readMillUsers(request, millId))
    .filter((u) => u.status === 'ACTIVE')
    .map((u) => u.userGuid)
    .sort();
}

/**
 * Activate or deactivate a mill through the API, with the revision just READ (the write is
 * optimistically locked). Fails on anything but 2xx — the cleanup must not paper over a refusal.
 */
export async function changeMillStatus(
  request: APIRequestContext,
  millId: number,
  action: 'activate' | 'deactivate',
): Promise<void> {
  const { revisionCount } = await readMill(request, millId);
  const res = await request.post(millStatusUrl(millId, action), {
    headers: ADMIN,
    data: { revisionCount },
  });
  await expect(res, `${action} mill ${millId} -> HTTP ${res.status()}: ${await res.text()}`).toBeOK();
}

/** Activate or deactivate one user's assignment on a mill, with that row's revision just READ. */
export async function changeUserStatus(
  request: APIRequestContext,
  millId: number,
  userGuid: string,
  action: 'activate' | 'deactivate',
): Promise<void> {
  const row = (await readMillUsers(request, millId)).find((u) => u.userGuid === userGuid);
  expect(row, `mill ${millId} has no assignment for user ${userGuid}`).toBeTruthy();
  const res = await request.post(millUserStatusUrl(millId, userGuid, action), {
    headers: ADMIN,
    data: { revisionCount: row!.revisionCount },
  });
  await expect(
    res,
    `${action} user ${userGuid} on mill ${millId} -> HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
}

/**
 * Put a status anchor back: its ACT/CLS status, then every user it pins as active — in that order,
 * because the users surface refuses to activate an assignment on a closed mill
 * (`error.user.activate.millinactive`). Each step is a no-op when already at rest, so a scenario that
 * failed before its click adds no audit stamp. Users the anchor does NOT pin are never touched.
 */
export async function restoreMillStatus(
  request: APIRequestContext,
  anchor: AdminMillAnchor,
): Promise<void> {
  expect(anchor.status, `mill ${anchor.millId} is not a status anchor`).toBeTruthy();
  const current = await readMill(request, anchor.millId);
  if (current.millStatusCode !== anchor.statusCode) {
    // Restoring to ACT needs a mill that may have active users: activate has no guard. Restoring to
    // CLS needs none active — true of every CLS anchor, whose pinned set is empty.
    await changeMillStatus(
      request,
      anchor.millId,
      anchor.statusCode === 'ACT' ? 'activate' : 'deactivate',
    );
  }
  const active = new Set(await activeUserGuids(request, anchor.millId));
  for (const guid of anchor.status!.activeUserGuids) {
    if (!active.has(guid)) {
      await changeUserStatus(request, anchor.millId, guid, 'activate');
    }
  }

  const after = await readMill(request, anchor.millId);
  expect(after.millStatusCode, `mill ${anchor.millId} did not return to ${anchor.statusCode}`).toBe(
    anchor.statusCode,
  );
  expect(
    await activeUserGuids(request, anchor.millId),
    `mill ${anchor.millId}'s active users did not return to their at-rest set`,
  ).toEqual([...anchor.status!.activeUserGuids]);
}

/** The current reporting year — the highest opened one, which is the year activate enrols. */
export async function currentReportingYear(request: APIRequestContext): Promise<number> {
  const res = await request.get(REPORTING_YEARS_URL, { headers: ADMIN });
  await expect(res, `GET reporting years -> HTTP ${res.status()}`).toBeOK();
  const { openYears } = (await res.json()) as { openYears: number[] };
  expect(openYears.length, 'no reporting year is open').toBeGreaterThan(0);
  return Math.max(...openYears);
}

/** The working context a (mill, year) resolves to — the read-back for BR-06 and BR-07. */
export interface MillContextRecord {
  millViewable: boolean;
  /** Present only when the (mill, year) has its ILCR_MILL_REPORT_STATUS row. */
  schedules1To10Status?: { code: string };
  schedule11Status?: { code: string };
}

export async function readMillContext(
  request: APIRequestContext,
  millId: number,
  year: number,
): Promise<MillContextRecord> {
  const res = await request.get(millContextUrl(millId, year), { headers: ADMIN });
  await expect(res, `GET mill-context ${millId}/${year} -> HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as MillContextRecord;
}
