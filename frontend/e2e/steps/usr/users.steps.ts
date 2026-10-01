import { Given, When, Then, expect } from '../fixtures';
import {
  type UsrAnchor,
  NO_DIRECTORY_MATCH,
  USR_ANCHORS,
  directoryRecord,
  millOptionLabel,
  usrMillByNumber,
} from '../../fixtures/usr/users-test-data';
import { MOCK_GROUPS_HEADER } from '../../pages/common/mockUser';
import { openApp } from '../../pages/common/authNav';
import { stubUserDirectory } from '../../pages/usr/usersPage';
import { accountUrl, assignmentState, assignmentsUrl, readAccount, readAssignments } from './usersApi';

/**
 * UC-USR-001 / UC-USR-002 steps — the Users page (`/mill-associations`). No DOM selectors here: they
 * live in pages/usr/usersPage.ts. The users are `world.usrAnchors`, set by "the <key> user is at rest";
 * the one on the page is `world.usrSelected`, set when a user is picked or arrives by carry.
 *
 * Mills are named by NUMBER in the feature files, as the page shows them; the fixture resolves the id.
 */

const anchorFor = (world: { usrAnchors?: Record<string, UsrAnchor> }, key: string): UsrAnchor => {
  const a = world.usrAnchors?.[key];
  expect(a, `"the ${key} user is at rest" must run first`).toBeTruthy();
  return a!;
};

const selected = (world: { usrSelected?: UsrAnchor }): UsrAnchor => {
  expect(world.usrSelected, 'a user must be selected on the Users page first').toBeTruthy();
  return world.usrSelected!;
};

// ---- preconditions ----

Given('the {word} user is at rest', async ({ request, world, usrCleanup }, key) => {
  const a = USR_ANCHORS[key];
  expect(a, `"${key}" is not a seeded user-admin user (fixtures/usr/users-test-data.ts)`).toBeTruthy();
  // Registered FIRST, before the at-rest check: a leftover from a crashed run is then restored by this
  // scenario's own teardown, instead of failing every run until someone fixes it by hand.
  usrCleanup.push(a);
  expect(await assignmentState(request, a.userGuid), `${key} user's assignments at rest`).toEqual({
    active: [...a.active].sort((x, y) => x - y),
    ended: [...a.ended].sort((x, y) => x - y),
  });
  expect(readAccount(a.userGuid), `${key} user's account at rest`).toBe(a.account);
  world.usrAnchors = { ...world.usrAnchors, [key]: a };
});

Given('the directory can find the seeded licensees', async ({ page, world }) => {
  // Installed before the page opens, so the carry's lookup is answered too. The directory holds EVERY
  // seeded licensee — including the two with no ILCR account yet, which is what makes them first-time
  // imports — and nobody else, so any other term is a miss.
  world.usrLookups = await stubUserDirectory(page, Object.values(USR_ANCHORS).map(directoryRecord));
});

// ---- opening the page ----

When('I open Users from the Administration menu', async ({ usersPage }) => {
  await usersPage.open();
});

When('I open the Users page directly with {string}', async ({ usersPage }, search) => {
  await usersPage.goto(search);
});

// ---- the picker (S04, S07, S13) ----

When('I search the BCeID directory with nothing entered', async ({ usersPage, page }) => {
  await usersPage.chooseBceid();
  await usersPage.userSearch.click();
  await usersPage.typeUserId('');
  // Proving an ABSENCE needs time to pass: the picker debounces 250 ms before it would send, so wait
  // well past that before the Then counts requests.
  await page.waitForTimeout(1_000);
});

Then('no directory search is sent and no user is offered', async ({ usersPage, world }) => {
  // Deviation (B): the legacy blank search listed everyone; the NR User Lookup API refuses a blank
  // search (400), so the picker never sends one. Asserted as shipped — no request, no candidate, and
  // no message either way (S13's "no error or success message is shown").
  expect(world.usrLookups?.length, 'directory lookups sent for a blank search').toBe(0);
  await expect(usersPage.candidates).toHaveCount(0);
  await expect(usersPage.pickerNote).toHaveCount(0);
});

When('I search the BCeID directory for user ID {string}', async ({ usersPage }, term) => {
  if (!(await usersPage.identityProvider.textContent())?.includes('BCeID Business')) {
    await usersPage.chooseBceid();
  }
  await usersPage.typeUserId(term);
});

Then('the picker reports that no user matches', async ({ usersPage, world }) => {
  await expect(usersPage.pickerNote).toHaveText(NO_DIRECTORY_MATCH);
  await expect(usersPage.candidates).toHaveCount(0);
  // The search DID reach the directory — the note is its answer, not a client-side guard.
  expect(world.usrLookups?.some((p) => p.get('userId') !== null), 'a userId lookup was sent').toBe(true);
});

When('I find and select the {word} user', async ({ usersPage, world }, key) => {
  const a = anchorFor(world, key);
  const record = directoryRecord(a);
  if (!(await usersPage.identityProvider.textContent())?.includes('BCeID Business')) {
    await usersPage.chooseBceid();
  }
  await usersPage.typeUserId(record.idpUsername);
  await usersPage.candidate(record).click();
  await expect(usersPage.userDetails).toContainText(record.idpUsername);
  world.usrSelected = a;
});

Then('the picker offers the {word} user', async ({ usersPage, world }, key) => {
  await expect(usersPage.candidate(directoryRecord(anchorFor(world, key)))).toBeVisible();
});

// ---- the selected user ----

Then('the {word} user is selected', async ({ usersPage, world }, key) => {
  const a = anchorFor(world, key);
  const record = directoryRecord(a);
  const cells = usersPage.selectedUserRow.getByRole('cell');
  await expect(cells.nth(0)).toHaveText(record.idpUsername);
  await expect(cells.nth(1)).toHaveText(record.firstName);
  await expect(cells.nth(2)).toHaveText(record.lastName);
  world.usrSelected = a;
});

Then(
  "the selected user's role and active flag are not shown, and both account actions are offered",
  async ({ usersPage }) => {
    // Deviation (O): no endpoint READS the account, so until a write answers, Role and Active are
    // genuinely unknown and render "—", and neither action can be ruled out.
    const cells = usersPage.selectedUserRow.getByRole('cell');
    await expect(cells.nth(3)).toHaveText('—');
    await expect(cells.nth(4)).toHaveText('—');
    await expect(usersPage.activateAccountButton).toBeVisible();
    await expect(usersPage.deactivateAccountButton).toBeVisible();
  },
);

When("I activate the selected user's account", async ({ usersPage }) => {
  await usersPage.activateAccountButton.click();
});

When("I deactivate the selected user's account", async ({ usersPage }) => {
  await usersPage.deactivateAccountButton.click();
});

Then(
  "the selected user's account is persisted as {string}",
  async ({ world }, flag) => {
    const a = selected(world);
    // Polled: the banner renders from the PATCH's response; the DB read is a second, later look.
    await expect.poll(() => readAccount(a.userGuid)).toBe(flag);
  },
);

Then(
  "the selected user's account shows Active {string}, role {string}, and offers only {string}",
  async ({ usersPage }, flag, role, action) => {
    const cells = usersPage.selectedUserRow.getByRole('cell');
    await expect(cells.nth(3)).toHaveText(role);
    await expect(cells.nth(4)).toHaveText(flag);
    const offered = action === 'Activate' ? usersPage.activateAccountButton : usersPage.deactivateAccountButton;
    const absent = action === 'Activate' ? usersPage.deactivateAccountButton : usersPage.activateAccountButton;
    await expect(offered).toBeVisible();
    await expect(absent).toHaveCount(0);
  },
);

// ---- the assignments ----

Then('the selected user has no associated mills', async ({ usersPage, request, world }) => {
  expect(await readAssignments(request, selected(world).userGuid)).toEqual([]);
  await expect(usersPage.associatedMills).toBeVisible();
  await expect(usersPage.assignmentRows).toHaveCount(0);
});

Then(
  "the selected user's associated mills are exactly {string}",
  async ({ usersPage, request, world }, list) => {
    const numbers = list.split(',').map((s) => s.trim()).sort();
    const served = (await readAssignments(request, selected(world).userGuid)).map((r) => r.millNumber).sort();
    expect(served, 'the served assignment set').toEqual(numbers);
    await expect(usersPage.assignmentRows).toHaveCount(numbers.length);
    for (const n of numbers) await expect(usersPage.assignmentRow(n)).toHaveCount(1);
  },
);

When('I add mill {string} to the selected user', async ({ usersPage }, millNumber) => {
  await usersPage.addMill(millOptionLabel(usrMillByNumber(millNumber)));
});

When('I deactivate the assignment to mill {string}', async ({ usersPage }, millNumber) => {
  await expect(usersPage.deactivateMillButton(millNumber)).toBeEnabled();
  await usersPage.deactivateMillButton(millNumber).click();
});

When('I activate the assignment to mill {string}', async ({ usersPage }, millNumber) => {
  await expect(usersPage.activateMillButton(millNumber)).toBeEnabled();
  await usersPage.activateMillButton(millNumber).click();
});

Then(
  "the selected user's assignment to mill {string} is persisted as {string}",
  async ({ request, world }, millNumber, status) => {
    const a = selected(world);
    const m = usrMillByNumber(millNumber);
    const row = async () => (await readAssignments(request, a.userGuid)).find((r) => r.millId === m.millId);
    await expect.poll(async () => (await row())?.status).toBe(status);
    // BR-07: activating records an activation date and clears the deactivation date; ending does the
    // reverse. (Both dates are the server's SYSDATE — no client-supplied effective date exists.)
    const r = (await row())!;
    if (status === 'ACTIVE') {
      expect(r.activeDate, 'activation date recorded').toBeTruthy();
      expect(r.inactiveDate ?? null, 'deactivation date cleared').toBeNull();
    } else {
      expect(r.inactiveDate, 'deactivation date recorded').toBeTruthy();
      expect(r.activeDate ?? null, 'activation date cleared').toBeNull();
    }
    // Exactly one row for the pair, whatever its state: a revive or a duplicate never inserts another.
    const rows = (await readAssignments(request, a.userGuid)).filter((x) => x.millId === m.millId);
    expect(rows, `rows for ${a.userGuid} on ${m.millNumber}`).toHaveLength(1);
  },
);

Then(
  'the row of mill {string} shows {string} and offers {string}',
  async ({ usersPage }, millNumber, label, action) => {
    expect(['Active', 'Inactive']).toContain(label);
    await expect(usersPage.statusCell(millNumber, label as 'Active' | 'Inactive')).toBeVisible();
    const offered =
      action === 'Activate' ? usersPage.activateMillButton(millNumber) : usersPage.deactivateMillButton(millNumber);
    const absent =
      action === 'Activate' ? usersPage.deactivateMillButton(millNumber) : usersPage.activateMillButton(millNumber);
    await expect(offered).toBeVisible();
    await expect(absent).toHaveCount(0);
  },
);

Then('no row of the selected user is active', async ({ usersPage, request, world }) => {
  const state = await assignmentState(request, selected(world).userGuid);
  expect(state.active, 'active assignments').toEqual([]);
  await expect(usersPage.associatedMills.getByRole('cell', { name: 'Active', exact: true })).toHaveCount(0);
});

// ---- administrators only (UC-USR-001 access, 2.5 AC3) ----

When("I open the Users page directly as the suite's default submitter", async ({ page }) => {
  // No seeded mock user: the suite's default principal is the submitter (pages/common/mockUser.ts).
  await openApp(page);
  await page.goto('/mill-associations');
});

Then('the Administration menu and its Users link are not offered', async ({ page }) => {
  await expect(page.getByRole('button', { name: 'Administration', exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Users', exact: true })).toHaveCount(0);
});

Then('the Users page does not render for them', async ({ usersPage }) => {
  await expect(usersPage.userDetailsPanel).toHaveCount(0);
});

Then('the user administration API refuses a submitter', async ({ request }) => {
  const asSubmitter = { [MOCK_GROUPS_HEADER]: 'ILCR_SUBMITTER' };
  const guid = USR_ANCHORS['read-only'].userGuid;
  const read = await request.get(assignmentsUrl(guid), { headers: asSubmitter });
  expect(read.status(), 'GET /submitters/{guid}/mills as a submitter').toBe(403);
  const write = await request.patch(accountUrl(guid), { headers: asSubmitter, data: { active: false } });
  expect(write.status(), 'PATCH /submitters/{guid} as a submitter').toBe(403);
  // And the refused write wrote nothing.
  expect(readAccount(guid)).toBe('Y');
});
