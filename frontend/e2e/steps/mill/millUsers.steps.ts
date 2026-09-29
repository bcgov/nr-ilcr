import { Given, When, Then, expect } from '../fixtures';
import {
  type AdminMillAnchor,
  ADMIN_MILLS_URL,
  READONLY_USERS_MILL,
  candidateLabel,
  directoryUser,
  millStatusUrl,
} from '../../fixtures/mill/mills-test-data';
import { openApp } from '../../pages/common/authNav';
import { MOCK_GROUPS_HEADER } from '../../pages/common/mockUser';
import { MillAssociationsPage, stubDirectory } from '../../pages/mill/millsPage';
import { readMillUsers } from './millsApi';

/**
 * UC-MILL-001 association-panel steps (S05, S07, S08, S09, S10, S13) and the search guards (S11, S15).
 * No DOM selectors here — they live in pages/mill/millsPage.ts. The mill is `world.millAnchor`, set by
 * the shared "the {word} mill is at rest" precondition in mills.steps.ts.
 */

const anchorOf = (world: { millAnchor?: AdminMillAnchor }): AdminMillAnchor => {
  expect(world.millAnchor, 'a mills precondition must set world.millAnchor first').toBeTruthy();
  return world.millAnchor!;
};

/** The seeded licensees are `E2E0000000000000000000000000000<n>`; `n` names their directory record. */
const directoryFor = (guid: string) => {
  const n = Number(guid.slice(-1));
  expect(guid, `"${guid}" is not one of the seeded mill-admin licensees`).toMatch(
    /^E2E0000000000000000000000000000\d$/,
  );
  return directoryUser(guid, n);
};

// ---- the directory stand-in ----

Given('the directory can find user {string}', async ({ page }, guid) => {
  // Installed before the page opens, so the carried-user lookup on View is answered too.
  await stubDirectory(page, [directoryFor(guid)]);
});

// ---- per-row activate / deactivate (S08, S09, S13) ----

When('I deactivate the association of user {string}', async ({ millsPage }, guid) => {
  await expect(millsPage.userDeactivateButton(guid)).toBeEnabled();
  await millsPage.userDeactivateButton(guid).click();
});

When('I activate the association of user {string}', async ({ millsPage }, guid) => {
  await expect(millsPage.userActivateButton(guid)).toBeEnabled();
  await millsPage.userActivateButton(guid).click();
});

Then(
  'the association of user {string} is persisted as {string}',
  async ({ request, world }, guid, status) => {
    const anchor = anchorOf(world);
    // Polled: the banner renders from the POST's response, and the read is a second request.
    await expect
      .poll(async () => (await readMillUsers(request, anchor.millId)).find((u) => u.userGuid === guid)?.status)
      .toBe(status);
  },
);

Then(
  'the row of user {string} shows {string} and offers {string}',
  async ({ millsPage }, guid, label, action) => {
    // The status cell's label AND the single applicable action, which is chosen by the wire status.
    expect(['Active', 'Inactive']).toContain(label);
    // The status CELL, matched exactly: the row's text runs its cells together, so a row-level text
    // match cannot tell "Active" from the "Deactivate" button beside it.
    await expect(millsPage.userStatusCell(guid, label as 'Active' | 'Inactive')).toBeVisible();
    const offered = action === 'Activate' ? millsPage.userActivateButton(guid) : millsPage.userDeactivateButton(guid);
    const absent = action === 'Activate' ? millsPage.userDeactivateButton(guid) : millsPage.userActivateButton(guid);
    await expect(offered).toBeVisible();
    await expect(absent).toHaveCount(0);
  },
);

// ---- add a user (S05, S07) ----

When(
  'I add user {string} from the Find and Add User dialog',
  async ({ millsPage, world, millDbCleanup }, guid) => {
    const anchor = anchorOf(world);
    // Registered BEFORE the pick: an add that lands and then fails an assertion still leaves a row that
    // no endpoint can delete. Deleting a row that was never created is a no-op, so S07 (whose add writes
    // nothing) registers nothing — only the S05 pair is on the bridge's allow-list anyway.
    if (!anchor.status!.activeUserGuids.includes(guid) && !anchor.status!.endedUserGuids.includes(guid)) {
      millDbCleanup.associations.push({ millId: anchor.millId, userGuid: guid });
    }
    const record = directoryFor(guid);
    await millsPage.addUserByBceid(record.idpUsername, candidateLabel(record));
  },
);

Then(
  'user {string} is associated with the mill, created inactive',
  async ({ request, world }, guid) => {
    const anchor = anchorOf(world);
    // BR-08: the add creates the association INACTIVE — the explicit Activate is the only way to use it.
    await expect
      .poll(async () => (await readMillUsers(request, anchor.millId)).find((u) => u.userGuid === guid)?.status)
      .toBe('ENDED');
    const rows = (await readMillUsers(request, anchor.millId)).filter((u) => u.userGuid === guid);
    expect(rows, 'exactly one association row for the pair').toHaveLength(1);
  },
);

Then("the mill's user associations are unchanged", async ({ millsPage, request, world }) => {
  const anchor = anchorOf(world);
  const pinned = [...anchor.status!.activeUserGuids, ...anchor.status!.endedUserGuids].sort();
  // BR-08 from both ends: the server holds exactly the at-rest pairs (no duplicate row, no revive),
  // and the panel lists exactly those.
  const served = await readMillUsers(request, anchor.millId);
  expect(served.map((u) => u.userGuid).sort()).toEqual(pinned);
  expect(served.filter((u) => u.status === 'ACTIVE').map((u) => u.userGuid).sort()).toEqual([
    ...anchor.status!.activeUserGuids,
  ]);
  expect(await millsPage.listedUserGuids()).toEqual(pinned);
});

When('I open the Find and Add User dialog', async ({ millsPage }) => {
  await millsPage.addUserButton.click();
  await expect(millsPage.addUserDialog).toBeVisible();
});

// ---- view the user (S10) ----

When('I view user {string}', async ({ millsPage, page }, guid) => {
  // Legacy carried the user in the session (`userSelected`); here it rides `?userGuid=`, which the
  // Users page CONSUMES on arrival (a replace-navigation to the bare URL, UC-MILL-002 BR-02) — so the
  // URL cannot be the evidence. The evidence is the page resolving exactly this GUID: its one exact
  // lookup, awaited from before the click so it cannot be missed.
  const carried = page.waitForRequest((req) => {
    const url = new URL(req.url());
    return url.pathname.endsWith('/api/v1/users/lookup') && url.searchParams.get('userGuid') === guid;
  });
  await millsPage.userViewButton(guid).click();
  await carried;
});

Then(
  'I am on the Users page with user {string} selected',
  async ({ page }, guid) => {
    const record = directoryFor(guid);
    // The param is consumed (see the When), and the carried user is SELECTED: their details render.
    await expect(page).toHaveURL(/\/mill-associations$/);
    const users = new MillAssociationsPage(page);
    await expect(users.userDetails).toContainText(record.idpUsername);
  },
);

Then(
  "the selected user's associated mills include mill {string}",
  async ({ page }, millNumber) => {
    await expect(new MillAssociationsPage(page).associatedMill(millNumber)).toBeVisible();
  },
);

// ---- the search guards (S11, S15) ----

When('I open the "Find and select Mill" dialog', async ({ millsPage }) => {
  await millsPage.openSearch();
});

When('I search the mill dialog by name {string}', async ({ millsPage }, name) => {
  await millsPage.searchByName(name);
});

When('I search the mill dialog by number {string}', async ({ millsPage }, number) => {
  await millsPage.searchByNumber(number);
});

Then('the mill dialog shows {string}', async ({ millsPage }, text) => {
  // Inside the dialog, not just anywhere: the page banner sits behind the Carbon overlay.
  await expect(millsPage.searchDialog.getByText(text, { exact: true })).toBeVisible();
});

Then('the {string} dialog is still open', async ({ page }, name) => {
  await expect(page.getByRole('dialog', { name })).toBeVisible();
});

Then('the mill search lists no mill', async ({ millsPage }) => {
  await expect(millsPage.searchResults).toHaveCount(0);
});

When('I choose mill {string} from the search results', async ({ millsPage }, millNumber) => {
  await millsPage.resultRow(millNumber).click();
  await expect(millsPage.searchDialog).toHaveCount(0);
  await expect(millsPage.changeMillButton).toBeVisible();
});

Then('the mill search lists mill {string}', async ({ millsPage }, millNumber) => {
  await expect(millsPage.searchResults).toBeVisible();
  await expect(millsPage.resultRow(millNumber)).toBeVisible();
});

// ---- administrators only (BR-10, GAP-3) ----

When("I open the app as the suite's default submitter", async ({ page }) => {
  await openApp(page);
  // The nav is open by default at lg+; open it otherwise, so an absent group means absent, not hidden.
  const openMenu = page.getByRole('button', { name: 'Open menu', exact: true });
  if (await openMenu.isVisible()) await openMenu.click();
  // A group the submitter IS offered, so the nav is proven rendered before its absences are read.
  await expect(page.getByRole('button', { name: 'Schedules', exact: true })).toBeVisible();
});

Then('the Administration menu and its Mills link are not offered', async ({ page }) => {
  await expect(page.getByRole('button', { name: 'Administration', exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Mills', exact: true })).toHaveCount(0);
});

Then('the mill administration API refuses a submitter', async ({ request }) => {
  const asSubmitter = { [MOCK_GROUPS_HEADER]: 'ILCR_SUBMITTER' };
  const read = await request.get(ADMIN_MILLS_URL, { headers: asSubmitter });
  expect(read.status(), 'GET /admin/mills as a submitter').toBe(403);
  const write = await request.post(millStatusUrl(READONLY_USERS_MILL.millId, 'deactivate'), {
    headers: asSubmitter,
    data: { revisionCount: 0 },
  });
  expect(write.status(), 'POST /admin/mills/{id}/deactivate as a submitter').toBe(403);
});
