import { test as base } from 'playwright-bdd';

import { Schedule3Page } from '../../pages/sch3/schedule3Page';
import { Schedule3SubPage } from '../../pages/sch3/schedule3SubPage';
import { type ScheduleKey } from '../../fixtures/sch3/schedule3-test-data';
import { restoreAnchor } from '../sch3/schedule3Api';

/**
 * Schedule 3 (sch3) fixtures — the two page objects, the cleanup registry and the mutation spy owned by
 * UC-SCH3-001. Nothing here is referenced by another domain, so this file can be edited without touching
 * anyone else's coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

/**
 * A registered Schedule 3 cleanup: return this (mill, year) to its at-rest state — an EMPTY schedule.
 * `withSchedule1` marks the BR-09 `crown-applied` anchor, whose cleanup must also clear the Schedule 1
 * volumes the crown push wrote.
 */
export type Sch3Cleanup = { key: ScheduleKey; withSchedule1?: boolean };

export type Sch3Fixtures = {
  schedule3Page: Schedule3Page;
  schedule3SubPage: Schedule3SubPage;
  /**
   * Cleanup registry for anchors a scenario writes to.
   *
   * Per-ANCHOR rather than per-record: a Schedule 3 save touches up to 21 detail rows plus the summary,
   * and every mutating anchor is pinned as EMPTY at rest, so "put this (mill, year) back to empty" is
   * both the correct and the total restore — it also cleans up a sub-page row the UI created without the
   * test ever having to predict the id the app minted. Register the key the moment a scenario knows it
   * will write, so a mid-scenario failure still tears down. Fails loud on residue.
   */
  schedule3Cleanup: Sch3Cleanup[];
  /**
   * Schedule 3 mutation spy. Counts ONLY the mutating calls — PUT/POST/DELETE on `/v1/schedule3...`,
   * including both sub-resources — so a client-rejected Save or Add row can PROVE no write was attempted
   * rather than merely observing that no success banner appeared.
   *
   * Deliberately EXCLUDES the check-status POST (`/check-status` mutates nothing by contract, AD-5) and
   * every GET, including the document load and the re-read after a save.
   */
  schedule3MutationSpy: { mutations: number };
};

export const sch3Test = base.extend<Sch3Fixtures>({
  schedule3Page: async ({ page }, use) => {
    await use(new Schedule3Page(page));
  },

  schedule3SubPage: async ({ page }, use) => {
    await use(new Schedule3SubPage(page));
  },

  schedule3Cleanup: async ({ request }, use) => {
    const registrations: Sch3Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad restore cannot hide the others.
    const residue: string[] = [];
    // De-duplicated: a scenario that registers the same anchor twice (a precondition and then the step
    // that saves) must not race two concurrent restores over the same rows.
    const seen = new Set<string>();
    for (const { key, withSchedule1 } of registrations) {
      const id = `${key.millId}/${key.year}`;
      if (seen.has(id)) continue;
      seen.add(id);
      try {
        await restoreAnchor(request, key, { alsoRestoreSchedule1: withSchedule1 === true });
      } catch (err) {
        residue.push(`${id}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 3 anchors not restored — the seeded DB is left mutated: ${residue.join(
          '; ',
        )}. Blank the schedule (or re-run scripts/apply-patches.sh) before re-running.`,
      );
    }
  },

  schedule3MutationSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the happy
    // paths are unaffected. The spy sits in front of Vite's /api proxy and lets every request through
    // untouched — it only tallies. `route.fallback()` (not `continue()`) so overlapping schedule3
    // handlers (e.g. the S17 forced-500) stay composable.
    const spy = { mutations: 0 };
    await page.route('**/api/v1/schedule3**', async (route) => {
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
