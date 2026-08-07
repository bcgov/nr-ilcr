import { test as base } from 'playwright-bdd';

import { HomePage } from '../../pages/common/homePage';
import { AppShellPage } from '../../pages/common/appShell';
import { type ScheduleKey } from '../../fixtures/sch1/schedule1-test-data';

/**
 * Global (cross-domain) fixtures for the ILCR BDD suite.
 *
 * Only things more than one domain touches live here: the per-scenario `world` scratch, and the page
 * objects that are not owned by a subject area (`homePage` — every domain establishes a working context
 * through it; `appShell` — the data-independent shell smoke). Anything a single domain owns belongs in
 * that domain's file instead, so two people adding coverage to two domains never edit the same file.
 *
 * `world` is a per-scenario scratch object for state passed between steps (a key set by a precondition,
 * a value a later Then reads back). Its shape `World` is the UNION of every domain's scratch fields —
 * kept in one place because a single `world` object is shared across all steps of a scenario regardless
 * of which domain contributed them. This is the one deliberate cross-domain coupling left; keep the
 * fields grouped and commented by domain so additions stay conflict-cheap.
 */
export type World = {
  // --- sch1 / sec (shared: both drive a Schedule 1 through the Home working context) ---
  /** The (mill, year) the scenario operates on — set by the precondition step, read by read-back/cleanup. */
  scheduleKey?: ScheduleKey;
  /** The Home Mill-dropdown option text for `scheduleKey`'s mill. */
  millOption?: string;
  // --- sch1 ---
  /** Optimistic-lock token captured when a read-only anchor opens, re-checked to prove no write. */
  revisionAtOpen?: number | null;
  /** Itemized Other-Costs count read on the main page before a round-trip, re-checked after (S09). */
  otherCostsCountBefore?: number;
  /** Other-Costs mutating-PUT tally captured mid-scenario, so a later reject can prove NO FURTHER write
   * was sent even when an earlier step in the same scenario legitimately saved (SG-1 inline edit). */
  otherCostsMutationsBefore?: number;
  // --- sec ---
  /** The URL of the Schedule 1 GET fired on nav — proves the SAVED Home context drove the request (UC-SEC-001). */
  schedule1RequestUrl?: string;
};

export type GlobalFixtures = {
  homePage: HomePage;
  appShell: AppShellPage;
  world: World;
};

export const globalTest = base.extend<GlobalFixtures>({
  homePage: async ({ page }, use) => {
    await use(new HomePage(page));
  },

  appShell: async ({ page }, use) => {
    await use(new AppShellPage(page));
  },

  world: async ({}, use) => {
    await use({});
  },
});
