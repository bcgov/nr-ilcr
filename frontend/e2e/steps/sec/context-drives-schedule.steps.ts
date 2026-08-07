import { When, Then, expect } from '../fixtures';
import { fixtureByKey } from '../../fixtures/sec/working-context-test-data';

/**
 * HOME-1.5 headline outcome (AC2) + S06/S20 consequence: a working context SAVED on Home actually
 * DRIVES the schedule pages — Schedule 1 operates on the selected mill/year (not the scaffold default),
 * and a closed mill is blocked from viewing its schedule. Cross-cuts SEC (Home) → SCH1 (Schedule 1):
 * uses the shared HomePage + Schedule1Page fixtures. Read-only (no writes), so still teardown-free.
 */

When('I open Schedule 1 for the current context', async ({ page, schedule1Page, world }) => {
  // Capture the schedule GET the page fires on mount BEFORE navigating — its query must carry the
  // context just saved on Home (client-side TanStack Router nav preserves the in-memory MillYearContext;
  // a full page.goto would reset it to the default and defeat the proof).
  const requestPromise = page.waitForRequest('**/api/v1/schedule1*');
  await schedule1Page.gotoViaNav();
  world.schedule1RequestUrl = (await requestPromise).url();
});

Then('the Schedule 1 request used the {string} context', async ({ world }, key) => {
  const f = fixtureByKey(key);
  const params = new URL(world.schedule1RequestUrl!).searchParams;
  // Exact equality rules out BOTH the legacy 514/2021 default AND the current 13050/2017 mount default:
  // only the saved selection produces these values.
  expect(params.get('millId')).toBe(String(f.millId));
  expect(params.get('year')).toBe(String(f.year));
});

When('I try to open Schedule 1 for the current context', async ({ schedule1Page }) => {
  // Closed mill → the schedule GET returns 409 and no form renders, so do NOT wait for the table.
  await schedule1Page.gotoViaNavRaw();
});

Then('the schedule page is blocked with {string}', async ({ schedule1Page }, message) => {
  await expect(schedule1Page.blocked(message)).toBeVisible();
});
