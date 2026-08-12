import { Given, Then, expect } from '../fixtures';

/**
 * App-shell smoke steps (cross-domain, `@smoke`). Data-independent: the app is opened with `/api`
 * aborted, so these run in CI against a frontend-only deploy with no seeded delivery DB — the coverage
 * that keeps every PR guarded without the full Oracle stack.
 */

Given('I open the app with no backend available', async ({ appShell }) => {
  await appShell.openWithoutBackend();
});

Then('the app header and mock-user selector are visible', async ({ appShell }) => {
  await expect(appShell.header).toBeVisible();
  await expect(appShell.mockUserSelector).toBeVisible();
});

Then(
  'the primary navigation shows Home, Schedules, Check Status, Generate Reports, Print Schedules, and Submissions',
  async ({ appShell }) => {
    await appShell.ensureNavOpen();
    await expect(appShell.navLink('Home')).toBeVisible();
    await expect(appShell.navGroup('Schedules')).toBeVisible();
    await expect(appShell.navLink('Check Status')).toBeVisible();
    await expect(appShell.navLink('Generate Reports')).toBeVisible();
    await expect(appShell.navLink('Print Schedules')).toBeVisible();
    await expect(appShell.navLink('Submissions')).toBeVisible();
  },
);
