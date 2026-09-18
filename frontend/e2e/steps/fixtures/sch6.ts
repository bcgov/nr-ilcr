import { test as base } from 'playwright-bdd';

import { Schedule6Page } from '../../pages/sch6/schedule6Page';
import { type ScheduleKey } from '../../fixtures/sch6/schedule6-test-data';
import { removeRecordsByComment } from '../sch6/schedule6Api';

/**
 * Schedule 6 (sch6) fixtures — page object and cleanup registry owned by UC-SCH6-001. Nothing here is
 * referenced by another domain, so this file can be edited without touching anyone else's coverage.
 * Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

/**
 * A registered Schedule 6 cleanup: remove every record carrying `comments` from (millId, year).
 *
 * KEYED ON THE COMMENT, not on an id, because the registration has to happen BEFORE the record exists
 * — there is no recordId to register until the POST has already succeeded, and a failure between the
 * click and the response is exactly when cleanup matters most. A road record has no unique natural key
 * of its own (the same TSA/Supply Block pair may legitimately repeat), so each scenario writes a
 * comment it owns and cleans up by that.
 *
 * Per-RECORD, not per-schedule: Schedule 6 has no "delete the schedule" endpoint, records are
 * individually addressable sub-resources, and the anchor's at-rest state is simply "no records".
 */
export type Sch6Cleanup = { key: ScheduleKey; comments: string };

export type Sch6Fixtures = {
  schedule6Page: Schedule6Page;
  /**
   * Cleanup registry for records a scenario creates.
   *
   * Register the comment the moment a scenario knows it will save — BEFORE clicking `Add Report`, not
   * after asserting success — so a mid-scenario failure still tears down. Fails loud on residue.
   */
  schedule6Cleanup: Sch6Cleanup[];
};

export const sch6Test = base.extend<Sch6Fixtures>({
  schedule6Page: async ({ page }, use) => {
    await use(new Schedule6Page(page));
  },

  schedule6Cleanup: async ({ request }, use) => {
    const registrations: Sch6Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad removal cannot hide the others.
    const residue: string[] = [];
    for (const { key, comments } of registrations) {
      try {
        await removeRecordsByComment(request, key, comments);
      } catch (err) {
        residue.push(`${key.millId}/${key.year} "${comments}": ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] Schedule 6 road records not removed — the seeded DB is left mutated: '
          + `${residue.join('; ')}. Delete them before re-running (preflight will name the anchor).`,
      );
    }
  },
});
