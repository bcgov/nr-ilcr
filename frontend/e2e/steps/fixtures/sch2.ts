import { test as base } from 'playwright-bdd';

import { Schedule2Page } from '../../pages/sch2/schedule2Page';
import { type ScheduleKey } from '../../fixtures/sch2/schedule2-test-data';
import { restoreSchedule2 } from '../sch2/schedule2Api';

/**
 * Pages on which the Schedule 2 mutation spy has installed its route, so `schedule2FailNextSave` can
 * refuse to stack on top of it (see the ordering hazard documented at its `install()`). A WeakSet keyed by
 * `page` — per-scenario by construction, and it cannot leak between parallel workers.
 */
const spyInstalled = new WeakSet<import('@playwright/test').Page>();

/**
 * Schedule 2 (sch2) fixtures — page object, cleanup registry and the mutation spy owned by
 * UC-SCH2-001. Nothing here is referenced by another domain, so this file can be edited without
 * touching anyone else's coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in
 * `./global`.
 */

/** A registered Schedule 2 cleanup: restore (millId, year) to its at-rest UNSAVED state. */
export type Sch2Cleanup = { key: ScheduleKey };

export type Sch2Fixtures = {
  schedule2Page: Schedule2Page;
  /**
   * Cleanup registry for anchors a scenario saves to.
   *
   * Unlike Schedule 11 (whose rows are individually addressable), Schedule 2 is ONE summary per
   * mill/year, so cleanup is a whole-schedule DELETE that returns the anchor to unsaved — which is
   * exactly how every mutating anchor is pinned at rest. Register the key the moment a scenario knows
   * it will write, so a mid-scenario failure still tears down. Fails loud on residue.
   */
  schedule2Cleanup: Sch2Cleanup[];
  /**
   * Schedule 2 mutation spy. Counts ONLY the mutating calls on the aggregate — PUT and DELETE
   * /v1/schedule2 — so a client-rejected Save can PROVE no write was attempted rather than merely
   * observing that no success banner appeared.
   *
   * Deliberately EXCLUDES the check-status POST (`/check-status` mutates nothing by contract) and every
   * GET, including the document load itself.
   */
  schedule2MutationSpy: { mutations: number };
  /**
   * Forces the NEXT Schedule 2 PUT — and only that one — to fail with a 500 carrying no ProblemDetail
   * body, so the page falls back to its own ERR-003 text (S12 — persistence error).
   *
   * This is the honest way to exercise a server-side save failure from a browser test: the real
   * database cannot be made to fail on demand from out here, and the thing under test is the PAGE's
   * behaviour on failure (message shown, entered values kept, nothing persisted). Because the request
   * is fulfilled at the browser edge, it never reaches the backend — so nothing is written and the
   * anchor stays pristine, which the scenario then proves by API read-back.
   *
   * Failing exactly ONCE is what makes S12's recovery arm testable: it models "the underlying
   * persistence error has been resolved", so a second Save proceeds to the real backend and succeeds.
   */
  schedule2FailNextSave: { install: () => Promise<void> };
};

export const sch2Test = base.extend<Sch2Fixtures>({
  schedule2Page: async ({ page }, use) => {
    await use(new Schedule2Page(page));
  },

  schedule2Cleanup: async ({ request }, use) => {
    const registrations: Sch2Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad restore cannot hide the others.
    const residue: string[] = [];
    for (const { key } of registrations) {
      try {
        await restoreSchedule2(request, key);
      } catch (err) {
        residue.push(`${key.millId}/${key.year}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 2 anchors not restored — the seeded DB is left mutated: ${residue.join(
          '; ',
        )}. Delete the saved schedules before re-running.`,
      );
    }
  },

  schedule2MutationSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the
    // happy paths are unaffected. The spy sits in front of Vite's /api proxy and lets every request
    // through untouched — it only tallies. `route.fallback()` (not `continue()`) so overlapping
    // schedule2 handlers stay composable.
    const spy = { mutations: 0 };
    spyInstalled.add(page);
    await page.route('**/api/v1/schedule2**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      // /check-status is a POST that mutates nothing by contract — never counted as a write.
      if ((method === 'PUT' || method === 'DELETE') && !url.includes('/check-status')) {
        spy.mutations += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },

  schedule2FailNextSave: async ({ page }, use) => {
    // Armed once and disarmed by the first PUT it intercepts, so a retry in the same scenario reaches
    // the real backend. Per-scenario state (the fixture is rebuilt for every test), so this is not
    // shared mutable state between parallel scenarios.
    let armed = true;
    await use({
      install: async () => {
        // ORDERING HAZARD — do not combine this fixture with `schedule2MutationSpy` in one scenario.
        // Playwright runs route handlers LAST-REGISTERED-FIRST, and this one ends the chain with
        // `route.fulfill()` rather than `route.fallback()`. If the spy's handler were registered first,
        // it would never run for the failed PUT, so `mutations` would under-count and a
        // "should not have been sent" assertion could pass while a mutation HAD been attempted.
        //
        // No scenario combines them today (the save-error and retry arms prove the negative by API
        // read-back instead, which is immune to route ordering), so this is a trap for the future rather
        // than a live defect — raised in review on CGI-BC/nr-ilcr#8. The check below makes it impossible
        // to hit silently: combining them fails the scenario with this explanation instead of quietly
        // mis-tallying.
        if (spyInstalled.has(page)) {
          throw new Error(
            'Schedule 2: "the next Schedule 2 save will fail on the server" cannot be combined with the ' +
              'mutation spy in one scenario — this fixture fulfills the PUT, so it would shadow the spy\'s ' +
              'route and leave `mutations` under-counted. Prove the negative by API read-back instead ' +
              '("no Schedule 2 record is stored").',
          );
        }
        await page.route('**/api/v1/schedule2?**', async (route) => {
          if (route.request().method() !== 'PUT' || !armed) {
            await route.fallback();
            return;
          }
          armed = false;
          // No body at all: the page's `extractDetail` finds nothing and falls back to its own
          // ERR-003 wording, which is precisely what the slice pins.
          await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
        });
      },
    });
  },
});
