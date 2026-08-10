import { test as base } from 'playwright-bdd';

import { Schedule1Page } from '../../pages/sch1/schedule1Page';
import { OtherCostsPage } from '../../pages/sch1/otherCostsPage';
import {
  type ScheduleKey,
  emptyScheduleRequest,
  scheduleUrl,
} from '../../fixtures/sch1/schedule1-test-data';
import { blankGuardedSchedule1Volumes, restoreSchedule1 } from '../sch1/schedule1DbRestore';
import { deleteOtherCostsByMarker } from '../sch1/otherCostsApi';

/**
 * Schedule 1 (sch1) fixtures — page objects, cleanup registries, and route spies owned by UC-SCH1-001.
 * Nothing here is referenced by another domain, so this file can be edited without touching anyone
 * else's coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

/** A registered Other-Costs cleanup: delete every row carrying `marker` under (millId, year). */
export type OtherCostsCleanup = { millId: number; year: number; marker: string };

export type Sch1Fixtures = {
  schedule1Page: Schedule1Page;
  otherCostsPage: OtherCostsPage;
  /**
   * Cleanup registry for Schedule 1 mutations. Push a (mill,year) whose Schedule 1 a scenario mutates;
   * on teardown each is restored to its EMPTY baseline via the app's own PUT (blank all writable
   * fields). Schedule 1 has no per-row id — the (mill,year) record pre-exists — so cleanup RESTORES
   * rather than DELETEs (a DELETE would drop a pre-existing seeded summary). Fails loud on residue.
   */
  schedule1Restore: ScheduleKey[];
  /**
   * Cleanup registry for scenarios whose changes the blank-fields PUT cannot undo (S13 delete, S24
   * retry-save, S02 crown pre-fill, clear-amounts). Each snapshots its (mill,year) to the
   * E2E_BAK_SCH1_* tables first, then pushes the key here; on teardown each is re-inserted verbatim
   * from its snapshot (scripts/sch1_db_restore.py). Separate from `schedule1Restore` because delete
   * removes the summary itself, which the blank-fields PUT cannot recreate — only a row-level
   * re-insert restores it. Fails loud on residue.
   */
  schedule1DeleteRestore: ScheduleKey[];
  /**
   * Save-endpoint spy. Counts PUT calls to `/api/v1/schedule1` so a rejected-entry scenario can PROVE
   * the negative — that a blocked Save fires NO mutating request — rather than merely observing that
   * the page did not move. Only PUT is a mutation; the GET read-backs / open-load are ignored.
   */
  schedule1SaveSpy: { puts: number };
  /**
   * Save fault-injection for the persistence scenarios (S23/S24). Set `failTimes` to N and the next N
   * main-page save PUTs are answered with a 500 ProblemDetail *without reaching the backend* (so no
   * data is written); subsequent PUTs pass through untouched. Only the main `/schedule1` PUT is faulted
   * — the Other-Costs / Check-Status calls and every GET pass through.
   */
  schedule1SaveFault: { failTimes: number };
  /**
   * Other-Costs mutation spy. Add/remove now persist the WHOLE row set via one PUT to
   * `/api/v1/schedule1/other-costs?…&intent=save|delete` (bcgov's EditableSubPage rewrite), not a
   * per-row POST. Counts that base PUT so a client-rejected add (S10/S11) can PROVE the negative — a
   * blocked Add validates and returns BEFORE persisting, firing NO mutating request.
   */
  otherCostsSpy: { mutations: number };
  /**
   * Cleanup registry for itemized Other-Costs rows a scenario adds (S09 adds one; S12 seeds one to
   * remove). On teardown every row carrying the registered marker is deleted via the app's DELETE
   * endpoint (a 404 = already removed by the UI). Fails loud on residue.
   */
  otherCostsCleanup: OtherCostsCleanup[];
};

export const sch1Test = base.extend<Sch1Fixtures>({
  schedule1Page: async ({ page }, use) => {
    await use(new Schedule1Page(page));
  },

  otherCostsPage: async ({ page }, use) => {
    await use(new OtherCostsPage(page));
  },

  schedule1Restore: async ({ request }, use) => {
    const keys: ScheduleKey[] = [];
    await use(keys);

    // Restore FAILS LOUD: a schedule left mutated poisons the shared seed and causes phantom failures
    // elsewhere. All keys are attempted first; any GET/PUT that doesn't restore surfaces immediately.
    const residue: string[] = [];
    for (const { millId, year } of keys) {
      const q = scheduleUrl(millId, year);
      try {
        const getRes = await request.get(q);
        if (!getRes.ok()) {
          residue.push(`${millId}/${year} GET -> HTTP ${getRes.status()}`);
          continue;
        }
        const doc = (await getRes.json()) as { revisionCount: number };
        const putRes = await request.put(q, { data: emptyScheduleRequest(doc.revisionCount) });
        if (!putRes.ok()) {
          residue.push(`${millId}/${year} restore PUT -> HTTP ${putRes.status()}`);
        }
        // The blanking PUT cannot clear 143/144/139/140 — the server reads a null there as "field
        // omitted" (BUG-2), so S01's writes to them would survive teardown and the pinned
        // empty baseline would drift a little further every run. Finish the job at the DB.
        //
        // TWO CONSEQUENCES, both deliberate (raised in review of PR #6):
        //  1. This puts a python-oracledb call in the MAIN mutating sch1 teardown, not just the
        //     destructive S13/S24 path. In practice any full run already required it — S13 snapshots on
        //     every run — so the delta is that a `--grep @S01`-only run now needs it too. Accepted:
        //     the alternative (snapshot/restore for S01) needs the same dependency and is heavier.
        //  2. It normalises exactly the drift `preflight/anchors.setup.ts` classifies as ADVISORY. That
        //     is intended: the advisory exists to surface drift the suite did NOT cause, so removing our
        //     own contribution makes it a sharper signal, not a blunter one. If S01 ever stops writing
        //     these fields, drop this call rather than leaving it to mask someone else's residue.
        blankGuardedSchedule1Volumes(millId, year);
      } catch (err) {
        residue.push(`${millId}/${year} threw: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 1 not restored to empty — the seeded DB is left mutated: ${residue.join(
          ', ',
        )}. Investigate the schedule1 GET/PUT before re-running.`,
      );
    }
  },

  schedule1DeleteRestore: async ({}, use) => {
    const keys: ScheduleKey[] = [];
    await use(keys);

    // Re-insert every deleted schedule from its snapshot. Fails loud: a schedule left deleted removes
    // rows from the shared seed and breaks any later run. All keys are attempted; the first failure
    // surfaces after the loop so one bad restore doesn't hide the others.
    const residue: string[] = [];
    for (const { millId, year } of keys) {
      try {
        restoreSchedule1(millId, year);
      } catch (err) {
        residue.push(`${millId}/${year}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Schedule 1 not restored after delete — the seeded DB is left missing rows: ${residue.join(
          '; ',
        )}. Re-run scripts/sch1_db_restore.py restore <mill> <year> before re-running the suite.`,
      );
    }
  },

  otherCostsCleanup: async ({ request }, use) => {
    const registrations: OtherCostsCleanup[] = [];
    await use(registrations);

    const residue: string[] = [];
    for (const { millId, year, marker } of registrations) {
      try {
        await deleteOtherCostsByMarker(request, millId, year, marker);
      } catch (err) {
        residue.push(`${millId}/${year} "${marker}": ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] Other Costs rows not removed — the seeded DB is left mutated: ${residue.join(
          '; ',
        )}. Delete the marked rows before re-running.`,
      );
    }
  },

  otherCostsSpy: async ({ page }, use) => {
    const spy = { mutations: 0 };
    await page.route('**/api/v1/schedule1/other-costs**', async (route) => {
      const req = route.request();
      // The UI mutates via the base whole-set PUT (…/other-costs?…&intent=…); count that, never the
      // per-row PUT /{id} or the GET read-backs. A client-rejected add fires none of these.
      if (req.method() === 'PUT' && !/\/other-costs\/\d+/.test(req.url())) {
        spy.mutations += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },

  schedule1SaveFault: async ({ page }, use) => {
    const fault = { failTimes: 0 };
    await page.route('**/api/v1/schedule1**', async (route) => {
      const req = route.request();
      const url = req.url();
      const isMainSave =
        req.method() === 'PUT' && !url.includes('/other-costs') && !url.includes('/check-status');
      if (isMainSave && fault.failTimes > 0) {
        fault.failTimes -= 1;
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({
            detail: 'Schedule could not be saved.',
            status: 500,
            title: 'Internal Server Error',
          }),
        });
        return;
      }
      await route.fallback();
    });
    await use(fault);
  },

  schedule1SaveSpy: async ({ page }, use) => {
    // Lazy + per-scenario: only a scenario that references this fixture installs the route, so the
    // happy-path save (S01) is unaffected. The spy sits in front of Vite's /api proxy and lets every
    // request through untouched — it only tallies mutations (PUT) to the Schedule 1 endpoint.
    // route.fallback() (not continue()) so overlapping schedule1 handlers stay composable: it defers
    // to any earlier-registered matching handler instead of short-circuiting straight to the network.
    const spy = { puts: 0 };
    await page.route('**/api/v1/schedule1**', async (route) => {
      if (route.request().method() === 'PUT') {
        spy.puts += 1;
      }
      await route.fallback();
    });
    await use(spy);
  },
});
