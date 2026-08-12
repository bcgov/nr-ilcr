import { test as base } from 'playwright-bdd';

import { SchedulePage } from '../../pages/common/schedulePage';

/**
 * Working-context (sec) fixtures — UC-SEC-001. `schedulePage` is the generic schedule-page wrapper the
 * tombstone scenarios use to read the "Working context" landmark on whichever schedule they land on;
 * only this domain drives it, so it is owned here rather than in `./global`.
 */
export type SecFixtures = {
  schedulePage: SchedulePage;
};

export const secTest = base.extend<SecFixtures>({
  schedulePage: async ({ page }, use) => {
    await use(new SchedulePage(page));
  },
});
