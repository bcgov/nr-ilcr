import { Given, expect } from '../fixtures';

/**
 * Establishing the Home working context — cross-domain, no domain vocabulary.
 *
 * PROMOTED TO common/ when Schedule 11 landed: this step drives only `HomePage` (itself a common page
 * object) and reads only the shared `world.scheduleKey` / `world.millOption` fields, so every schedule
 * domain needs exactly this. It previously lived in `steps/sch1/schedule1.steps.ts`; a second copy in
 * `steps/sch11/` would have been the duplicate-definition error playwright-bdd (rightly) rejects. The
 * step TEXT is unchanged, so all existing `.feature` usages keep working.
 *
 * A domain's precondition step is responsible for setting `world.scheduleKey` + `world.millOption`
 * before this runs.
 */

Given(
  'I have selected that mill and reporting year on the Home page',
  async ({ homePage, world }) => {
    expect(
      world.scheduleKey && world.millOption,
      'a domain precondition must set world.scheduleKey and world.millOption before selecting the context',
    ).toBeTruthy();
    await homePage.open();
    await homePage.selectContextAndSave(world.millOption!, world.scheduleKey!.year);
  },
);
