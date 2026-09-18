import { test as base } from 'playwright-bdd';

import { Schedule5Page } from '../../pages/sch5/schedule5Page';
import { type ScheduleKey } from '../../fixtures/sch5/schedule5-test-data';
import { removeCampByName } from '../sch5/schedule5Api';

/**
 * Schedule 5 (sch5) fixtures — page object and cleanup registry owned by UC-SCH5-001. Nothing here is
 * referenced by another domain, so this file can be edited without touching anyone else's coverage.
 * Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

/**
 * A registered Schedule 5 cleanup: remove the named camp from (millId, year).
 *
 * PER-CAMP, not per-schedule, unlike Schedule 2's whole-aggregate DELETE. Schedule 5 has no
 * "delete the schedule" endpoint — camps are individually addressable sub-resources and the anchor's
 * at-rest state is simply "no camps" — so cleanup names exactly what the scenario created and cannot
 * remove a camp it did not make.
 */
export type Sch5Cleanup = { key: ScheduleKey; campName: string };

export type Sch5Fixtures = {
  schedule5Page: Schedule5Page;
  /**
   * Cleanup registry for camps a scenario creates.
   *
   * Register the name the moment a scenario knows it will save — BEFORE clicking Save, not after
   * asserting success — so a mid-scenario failure still tears down. Fails loud on residue.
   */
  schedule5Cleanup: Sch5Cleanup[];
};

export const sch5Test = base.extend<Sch5Fixtures>({
  schedule5Page: async ({ page }, use) => {
    await use(new Schedule5Page(page));
  },

  schedule5Cleanup: async ({ request }, use) => {
    const registrations: Sch5Cleanup[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad removal cannot hide the others.
    const residue: string[] = [];
    for (const { key, campName } of registrations) {
      try {
        await removeCampByName(request, key, campName);
      } catch (err) {
        residue.push(`${key.millId}/${key.year} "${campName}": ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] Schedule 5 camps not removed — the seeded DB is left mutated: '
          + `${residue.join('; ')}. Delete the camps before re-running (preflight will name them).`,
      );
    }
  },
});
