import { test as base } from 'playwright-bdd';

import { Schedule4Page } from '../../pages/sch4/schedule4Page';
import { Schedule4SubPage } from '../../pages/sch4/schedule4SubPage';
import { type ScheduleKey } from '../../fixtures/sch4/schedule4-test-data';
import { restoreAnchor } from '../sch4/schedule4Api';

/**
 * Schedule 4 (sch4) fixtures — the two page objects, the cleanup registry and the mutation spy owned by
 * UC-SCH4-001. Nothing here is referenced by another domain, so this file can be edited without touching
 * anyone else's coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

/** A registered Schedule 4 cleanup: return this (mill, year) to its at-rest state — NO locations. */
export type Sch4Cleanup = { key: ScheduleKey };

export type Sch4Fixtures = {
  schedule4Page: Schedule4Page;
  schedule4SubPage: Schedule4SubPage;
  /**
   * Cleanup registry for anchors a scenario writes to.
   *
   * Unlike Schedule 2 (one summary per mill/year), a Schedule 4 write creates a whole FAMILY of
   * transportation reports — a primary, one child per distance category, and one per sub-page row — and
   * a scenario may create several locations. So cleanup is per-ANCHOR rather than per-record: it deletes
   * every location on the registered (mill, year), which is exactly how each mutating anchor is pinned at
   * rest (empty). Register the key the moment a scenario knows it will write, so a mid-scenario failure
   * still tears down. Fails loud on residue.
   *
   * Keying on the anchor rather than the created id is also what makes a UI-created location cleanable:
   * the test never has to predict the id the app minted, and a rename mid-scenario (S02 covers one)
   * cannot orphan a name-keyed teardown.
   */
  schedule4Cleanup: Sch4Cleanup[];
  /**
   * Schedule 4 mutation spy. Counts ONLY the mutating calls — PUT/POST/DELETE on
   * `/v1/schedule4/...` — so a client-rejected Save or Add row can PROVE no write was attempted rather
   * than merely observing that no success banner appeared.
   *
   * Deliberately EXCLUDES the check-status POST (`/check-status` mutates nothing by contract, AD-5) and
   * every GET, including the document load and the re-read after a delete.
   */
  schedule4MutationSpy: { mutations: number };
};

export const sch4Test = base.extend<Sch4Fixtures>({
  schedule4Page: async ({ page }, use) => {
    await use(new Schedule4Page(page));
  },

  schedule4SubPage: async ({ page }, use) => {
    await use(new Schedule4SubPage(page));
  },

  schedule4Cleanup: async ({ request }, use) => {
    const registrations: Sch4Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad restore cannot hide the others.
    const residue: string[] = [];
    // De-duplicated: a scenario that registers the same anchor twice (e.g. a precondition and then the
    // step that saves) must not race two concurrent delete sweeps over the same rows.
    const seen = new Set<string>();
    for (const { key } of registrations) {
      const id = `${key.millId}/${key.year}`;
      if (seen.has(id)) continue;
      seen.add(id);
      try {
        await restoreAnchor(request, key);
      } catch (err) {
        residue.push(`${id}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 4 anchors not restored — the seeded DB is left mutated: ${residue.join(
          '; ',
        )}. DELETE the leftover locations before re-running.`,
      );
    }
  },

  schedule4MutationSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the happy
    // paths are unaffected. The spy sits in front of Vite's /api proxy and lets every request through
    // untouched — it only tallies. `route.fallback()` (not `continue()`) so overlapping schedule4
    // handlers stay composable.
    const spy = { mutations: 0 };
    await page.route('**/api/v1/schedule4**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      const isWrite = method === 'PUT' || method === 'POST' || method === 'DELETE';
      // /check-status is a POST that mutates nothing by contract — never counted as a write.
      if (isWrite && !url.includes('/check-status')) {
        spy.mutations += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },
});
