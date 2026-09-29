import { When, Then, expect } from '../fixtures';
import {
  type UsrAnchor,
  CARRIED_USER_FAILED,
  directoryRecord,
  usrMillByNumber,
} from '../../fixtures/usr/users-test-data';

/**
 * The mill <-> user hand-offs (UC-USR-002 S01/S09, UC-USR-001 S06 / UC-MILL-002 S01). No DOM selectors
 * here: the Mills page's live in pages/mill/millsPage.ts (the `millsPage` fixture), the Users page's in
 * pages/usr/usersPage.ts.
 *
 * The carry rides the URL (`/mill-associations?userGuid=`) and the Users route CONSUMES it on arrival —
 * a replace-navigation to the bare URL (routes/mill-associations.tsx), which is the rebuild's version of
 * legacy clearing its session hand-off (BR-02). So the URL cannot be the evidence of the carry: the
 * evidence is the page's one exact lookup of that GUID, and then the user it selects.
 */

const anchorFor = (world: { usrAnchors?: Record<string, UsrAnchor> }, key: string): UsrAnchor => {
  const a = world.usrAnchors?.[key];
  expect(a, `"the ${key} user is at rest" must run first`).toBeTruthy();
  return a!;
};

// ---- mill -> user (UC-USR-002 S01) ----

When(
  'I view the {word} user from mill {string} on the Mills page',
  async ({ millsPage, usersPage, page, world }, key, millNumber) => {
    const a = anchorFor(world, key);
    usrMillByNumber(millNumber); // a seeded user-admin mill, or fail here rather than in the search
    await millsPage.open();
    await millsPage.openSearch();
    await millsPage.searchByNumber(millNumber);
    await millsPage.resultRow(millNumber).click();
    await expect(millsPage.searchDialog).toHaveCount(0);

    const carried = page.waitForRequest((req) => {
      const url = new URL(req.url());
      return url.pathname.endsWith('/api/v1/users/lookup') && url.searchParams.get('userGuid') === a.userGuid;
    });
    await millsPage.userViewButton(a.userGuid).click();
    await carried;
    await usersPage.expectLoaded();
    // Arrived WITH the user: their details render. Only then is the carried user the selected one.
    await expect(usersPage.userDetails).toContainText(directoryRecord(a).idpUsername);
    world.usrSelected = a;
  },
);

Then('the carry has been consumed from the URL', async ({ page }) => {
  await expect(page).toHaveURL(/\/mill-associations$/);
});

// ---- no carry, a bad carry, a stale carry (UC-USR-002 S09, UC-MILL-002 S05/S06 ruling (N)) ----

Then('no user is selected', async ({ usersPage }) => {
  await expect(usersPage.userDetails).toHaveCount(0);
  await expect(usersPage.associatedMillsPanel).toHaveCount(0);
  // The default state is search-first: the picker is there, empty.
  await expect(usersPage.userSearch).toBeVisible();
  await expect(usersPage.userSearch).toHaveValue('');
});

Then('no directory lookup was sent', async ({ world }) => {
  expect(world.usrLookups?.map((p) => p.toString()), 'directory lookups').toEqual([]);
});

Then('the page reports that the carried user could not be looked up', async ({ usersPage }) => {
  await expect(usersPage.notification('Error')).toContainText(CARRIED_USER_FAILED);
});

// ---- user -> mill (UC-USR-001 S06, UC-MILL-002 S01) ----

When(
  "I open mill {string} from the selected user's associated mills",
  async ({ usersPage }, millNumber) => {
    // Story 23.3 AC10's user -> mill hand-off: each assignment row's way to open its mill on the Mills
    // page, pre-selected. Looked for as a button OR a link named for the mill, so the test does not
    // dictate the control. Asserted visible first so the red names what is missing rather than timing out.
    const row = usersPage.assignmentRow(millNumber);
    await expect(row, `the assignment row for mill ${millNumber}`).toHaveCount(1);
    const open = row.getByRole('link', { name: new RegExp(`\\b(view|open)\\b.*\\b${millNumber}\\b`, 'i') })
      .or(row.getByRole('button', { name: new RegExp(`\\b(view|open)\\b.*\\b${millNumber}\\b`, 'i') }));
    await expect(
      open,
      `the Associated mills row for ${millNumber} offers no way to open the mill (Story 23.3 AC10, `
        + 'defects.md DIV-1)',
    ).toBeVisible({ timeout: 5_000 });
    await open.click();
  },
);

Then('I am on the Mills page with mill {string} selected', async ({ millsPage, page }, millNumber) => {
  const m = usrMillByNumber(millNumber);
  await expect(page).toHaveURL(/\/mills(\?.*)?$/);
  await expect(millsPage.detailsPanel).toContainText(m.millName);
  await expect(millsPage.changeMillButton).toBeVisible();
});

When('I open the Users page again from the Administration menu', async ({ usersPage }) => {
  await usersPage.open();
});
