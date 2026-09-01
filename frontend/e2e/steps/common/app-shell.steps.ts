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
    // Generate Reports is a GROUP, not a link: story 19.1 gave it the Mill Information Report as a
    // sub-item, so Carbon renders it as an expandable toggle (a button) like Schedules. It is also
    // admin-gated now — visible here because the default mock user (MOCK_USERS[0]) is the admin.
    await expect(appShell.navGroup('Generate Reports')).toBeVisible();
    await expect(appShell.navLink('Print Schedules')).toBeVisible();
    await expect(appShell.navLink('Submissions')).toBeVisible();
  },
);
