import { test as base } from 'playwright-bdd';

import { Schedule11Page } from '../../pages/sch11/schedule11Page';
import { type ScheduleKey } from '../../fixtures/sch11/schedule11-test-data';
import { deleteLocationsByMarker } from '../sch11/schedule11Api';

/**
 * Schedule 11 (sch11) fixtures — page object, cleanup registry, and the mutation spy owned by
 * UC-SCH11-001. Nothing here is referenced by another domain, so this file can be edited without
 * touching anyone else's coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in
 * `./global`.
 */

/** A registered Schedule 11 cleanup: delete every location carrying `marker` under (millId, year). */
export type Sch11Cleanup = { key: ScheduleKey; marker: string };

export type Sch11Fixtures = {
  schedule11Page: Schedule11Page;
  /**
   * Cleanup registry for locations a scenario creates. Unlike Schedule 1 (whose summary PRE-EXISTS, so
   * cleanup restores it to empty), a Schedule 11 location is a row the scenario itself creates — so
   * cleanup DELETES via the app's own endpoint, which is the skill's preferred route and needs no DB
   * fallback. Register the (key, marker) the moment a scenario knows it will write. Fails loud on
   * residue: a location left behind would break the next run's pristine-anchor assumption and, worse,
   * make a later Check Status scenario see a row it never created.
   */
  schedule11Cleanup: Sch11Cleanup[];
  /**
   * Location-mutation spy. Counts ONLY the mutating calls on the locations sub-resource — POST
   * /locations, PUT /locations/{id}, DELETE /locations/{id} — so a client-rejected Add or inline edit
   * can PROVE no write was attempted rather than merely observing that the page did not move.
   *
   * Deliberately EXCLUDES the check-status POST (`/check-status` mutates nothing by contract) and every
   * GET, including the BEC catalogue lookups the type-ahead fires.
   */
  schedule11MutationSpy: { mutations: number };
};

export const sch11Test = base.extend<Sch11Fixtures>({
  schedule11Page: async ({ page }, use) => {
    await use(new Schedule11Page(page));
  },

  schedule11Cleanup: async ({ request }, use) => {
    const registrations: Sch11Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad delete cannot hide the others.
    const residue: string[] = [];
    for (const { key, marker } of registrations) {
      try {
        await deleteLocationsByMarker(request, key, marker);
      } catch (err) {
        residue.push(`${key.millId}/${key.year} "${marker}": ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 11 locations not removed — the seeded DB is left mutated: ${residue.join(
          '; ',
        )}. Delete the marked locations before re-running.`,
      );
    }
  },

  schedule11MutationSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the
    // happy paths are unaffected. The spy sits in front of Vite's /api proxy and lets every request
    // through untouched — it only tallies. `route.fallback()` (not `continue()`) so overlapping
    // schedule11 handlers stay composable.
    const spy = { mutations: 0 };
    await page.route('**/api/v1/schedule11/locations**', async (route) => {
      const method = route.request().method();
      if (method === 'POST' || method === 'PUT' || method === 'DELETE') {
        spy.mutations += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },
});
