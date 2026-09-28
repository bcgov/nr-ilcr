import { test as base } from 'playwright-bdd';

import { Schedule6Page } from '../../pages/sch6/schedule6Page';
import { type ScheduleKey } from '../../fixtures/sch6/schedule6-test-data';
import { clearGeneralComment, removeRecordsByComment } from '../sch6/schedule6Api';

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
  /**
   * Cleanup registry for the SCHEDULE-LEVEL general comment (S04).
   *
   * Separate from `schedule6Cleanup` because the undo is a different operation on a different column:
   * a general comment saved on an empty schedule lives on a bare BR-09 placeholder row, and the way to
   * remove it is to clear the comment (which drops the placeholder), not to delete a record. Sharing
   * one registry would have meant one entry shape carrying two unrelated teardowns.
   */
  schedule6CommentCleanup: ScheduleKey[];
  /**
   * Counts MUTATING Schedule 6 requests, so a rejection scenario can prove NO WRITE was attempted
   * rather than merely that an error appeared.
   *
   * `POST /check-status` is excluded by contract — it is a POST that mutates nothing (it is read-only
   * and not editability-gated), so counting it would make every Check Status look like a write.
   */
  schedule6MutationSpy: { mutations: number };
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

  schedule6CommentCleanup: async ({ request }, use) => {
    const registrations: ScheduleKey[] = [];
    await use(registrations);

    const residue: string[] = [];
    for (const key of registrations) {
      try {
        await clearGeneralComment(request, key);
      } catch (err) {
        residue.push(`${key.millId}/${key.year}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] Schedule 6 general comment not cleared — the seeded DB is left mutated: '
          + `${residue.join('; ')}. A leftover comment also leaves its BR-09 placeholder row behind, `
          + 'which preflight reports as a non-empty anchor.',
      );
    }
  },

  schedule6MutationSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the
    // happy paths are untouched. The spy sits in front of Vite's /api proxy and lets every request
    // through unchanged — it only tallies. `route.fallback()` rather than `continue()` so overlapping
    // schedule6 handlers stay composable. Same shape as sch2's own spy.
    const spy = { mutations: 0 };
    await page.route('**/api/v1/schedule6**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      // POST /records, PUT (document save) and DELETE /records/{id} are the three write paths.
      // /check-status is a POST that mutates nothing by contract — never counted as a write.
      if (
        (method === 'POST' || method === 'PUT' || method === 'DELETE')
        && !url.includes('/check-status')
      ) {
        spy.mutations += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },
});
