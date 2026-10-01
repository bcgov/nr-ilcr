import { test, expect } from '@playwright/test';
import { MOCK_GROUPS_HEADER } from '../pages/common/mockUser';
import {
  UNKNOWN_GUID,
  USR_ANCHORS,
  USR_MILLS,
  millOptionLabel,
} from '../fixtures/usr/users-test-data';
import { assignmentState, millLabel, readAccount, readAssignments } from '../steps/usr/usersApi';

/**
 * PREFLIGHT — the user-administration anchors (fixtures/usr/users-test-data.ts) resolve, and are at
 * rest, in the LIVE database the suite is about to run against.
 *
 * Every Users-page scenario starts from its user's pinned state and its cleanup returns there, so a run
 * that died between a click and its teardown leaves a user that every later run would fail on in a way
 * that reads as an app defect. This names the user instead. The CI seed's copy of the same rows is
 * checked, without a database, by ci-seed-parity.setup.ts.
 *
 * Read-back is the API wherever one exists (the assignments) and the DB bridge for the one thing that
 * has no read endpoint (the account flag — scripts/usr_db_restore.py read-account).
 */

const HINT = 'Re-apply real-test-data-patches/usr/user-admin-anchors.sql, or re-ground the fixture.';
const ADMIN = { [MOCK_GROUPS_HEADER]: 'ILCR_ADMIN' };

test('usr mills are all listed by the Add-mill dropdown, as ACT', async ({ request }) => {
  // The Users page's Add-mill dropdown is GET /v1/mills: an admin sees a tracked mill only if it has a
  // report-status row. A mill missing here cannot be added, and the scenarios fail on a missing option.
  const res = await request.get('/api/v1/mills', { headers: ADMIN });
  expect(res.status()).toBe(200);
  const listed = new Map(
    ((await res.json()) as { millId: number; millNumber: string; millName: string; millStatusCode: string }[])
      .map((m) => [m.millId, `${m.millNumber} - ${m.millName} (${m.millStatusCode})`]),
  );
  for (const m of USR_MILLS) {
    expect(listed.get(m.millId), `mill ${millLabel(m.millId)} is not offered as expected. ${HINT}`).toBe(
      millOptionLabel(m),
    );
  }
});

for (const a of Object.values(USR_ANCHORS)) {
  test(`usr anchor "${a.key}" (${a.userGuid}) is at rest`, async ({ request }) => {
    const state = await assignmentState(request, a.userGuid);
    expect(
      state,
      `the ${a.key} user's assignments moved (active / ended mill ids). A run that died mid-scenario `
        + 'leaves them changed: the scenario\'s own "is at rest" step restores it on the next run only if '
        + `nothing else does first — restore it through the Users page, or ${HINT}`,
    ).toEqual({
      active: [...a.active].sort((x, y) => x - y),
      ended: [...a.ended].sort((x, y) => x - y),
    });

    // An ENDED row must be ended the way the app ends one — ACTIVE_DATE null. With both dates set the
    // Users page renders it Active and a Deactivate 409s (the mill-admin anchors' shape; see the patch).
    for (const row of await readAssignments(request, a.userGuid)) {
      if (row.status === 'ENDED') {
        expect(row.activeDate ?? null, `${a.key} on ${millLabel(row.millId)} is ended but keeps an ACTIVE_DATE`).toBeNull();
      }
    }

    expect(
      readAccount(a.userGuid),
      a.account === null
        ? `the ${a.key} user has an ILCR account, so it is no longer a first-time import — run `
            + `scripts/usr_db_restore.py drop-account ${a.userGuid} (after its assignments)`
        : `the ${a.key} user's account flag moved. ${HINT}`,
    ).toBe(a.account);
  });
}

test('the unresolvable carried user is known to nobody', async ({ request }) => {
  // The "departed user" guard carries a well-formed GUID that resolves nowhere.
  expect(readAccount(UNKNOWN_GUID), `${UNKNOWN_GUID} has an ILCR account`).toBeNull();
  expect(await readAssignments(request, UNKNOWN_GUID)).toEqual([]);
});
