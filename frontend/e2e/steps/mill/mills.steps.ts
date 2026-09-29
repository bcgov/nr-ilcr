import { Given, When, Then, expect } from '../fixtures';
import {
  type AdminMillAnchor,
  MILL_NOT_ACTIVE_MESSAGE,
  NO_CONTACT_LABEL,
  S01_EDIT,
  S01_MILL,
  STATUS_MILLS,
} from '../../fixtures/mill/mills-test-data';
import { scheduleUrl } from '../../fixtures/sch1/schedule1-test-data';
import { MOCK_GROUPS_HEADER } from '../../pages/common/mockUser';
import {
  activeUserGuids,
  atRest,
  currentReportingYear,
  readContactOptions,
  readMill,
  readMillContext,
} from './millsApi';

/**
 * UC-MILL-001 (Maintain Mills) steps. No DOM selectors here — every one lives in pages/mill/millsPage.ts.
 * The mill a scenario works on is carried in `world.millAnchor`, set by its precondition.
 */

const anchorOf = (world: { millAnchor?: AdminMillAnchor }): AdminMillAnchor => {
  expect(world.millAnchor, 'a mills precondition must set world.millAnchor first').toBeTruthy();
  return world.millAnchor!;
};

/** The label a contact id renders as in its dropdown — the mill's own contact, or "(None)". */
const contactLabel = (anchor: AdminMillAnchor, id: number | null): string =>
  id === null
    ? NO_CONTACT_LABEL
    : (anchor.contacts.find((c) => c.clientContactId === id)?.contactName
      ?? `Contact ${id} (not in list)`);

// ---- preconditions ----

Given(
  'the S01 mill is at rest with its head-office indicator and contacts',
  async ({ request, world, millContactsCleanup }) => {
    // Re-checked per scenario, not only in preflight: a previous run that died between its Save and its
    // cleanup leaves the row edited, and this scenario would then "change" values to what they already
    // are and pass without proving anything.
    const mill = await readMill(request, S01_MILL.millId);
    expect(
      atRest(mill, S01_MILL),
      `mill ${S01_MILL.millId} is not at rest — a previous run left it edited: ${JSON.stringify(mill)}. `
        + 'preflight/mill-anchors.setup.ts names the values to restore.',
    ).toBe(true);
    world.millAnchor = S01_MILL;
    // Registered BEFORE the Save, so a failure after the click still restores the row.
    millContactsCleanup.push(S01_MILL);
  },
);

// ---- navigation and selection ----

When('I open Mills from the Administration menu', async ({ millsPage }) => {
  await millsPage.open();
});

Then(
  'no mill is selected and only {string} and {string} are offered',
  async ({ millsPage }, first, second) => {
    // STA-001 state 1: the two entry controls, and nothing that belongs to a selected mill.
    expect([first, second]).toEqual(['Select Mill', 'Import Mill']);
    await expect(millsPage.selectMillButton).toBeVisible();
    await expect(millsPage.importMillButton).toBeVisible();
    await expect(millsPage.changeMillButton).toHaveCount(0);
    await expect(millsPage.saveButton).toHaveCount(0);
    await expect(millsPage.headOfficeDropdown).toHaveCount(0);
  },
);

When('I search for the mill by its number and select it', async ({ millsPage, world }) => {
  const anchor = anchorOf(world);
  await millsPage.openSearch();
  await millsPage.searchByNumber(anchor.millNumber);
  // An exact-number search can still match more than one row (the search is a prefix/LIKE match on the
  // number), so the row is chosen by its own control, never by position.
  await millsPage.resultRow(anchor.millNumber).click();
});

Then(
  "the Mill Details panel shows the mill's number, name, status and last-edited details",
  async ({ millsPage, request, world }) => {
    const anchor = anchorOf(world);
    await expect(millsPage.identity(anchor.millNumber, anchor.millName)).toBeVisible();
    await expect(millsPage.detailsText(anchor.statusDescription)).toBeVisible();
    // The audit line is read off the API, never pinned: every save re-stamps it (see the fixture).
    const served = await readMill(request, anchor.millId);
    expect(served.updateUserid, 'the anchor mill should carry an audit stamp').toBeTruthy();
    await expect(millsPage.detailsText(served.updateUserid!)).toBeVisible();
    await expect(millsPage.detailsText(served.updateTimestamp!)).toBeVisible();
  },
);

Then(
  '{string} is offered in place of {string} and {string}',
  async ({ millsPage }, offered, gone1, gone2) => {
    expect([offered, gone1, gone2]).toEqual(['Change Mill', 'Select Mill', 'Import Mill']);
    await expect(millsPage.changeMillButton).toBeVisible();
    await expect(millsPage.selectMillButton).toHaveCount(0);
    await expect(millsPage.importMillButton).toHaveCount(0);
  },
);

Then(
  "the head-office indicator and both contacts show the mill's saved values",
  async ({ millsPage, world }) => {
    const anchor = anchorOf(world);
    const { headOfficeContactInd, headOfficeContactId, divisionContactId } = anchor.atRest;
    await expect(millsPage.headOfficeDropdown).toContainText(headOfficeContactInd === 'Y' ? 'Yes' : 'No');
    await expect(millsPage.headOfficeContactDropdown).toContainText(
      contactLabel(anchor, headOfficeContactId),
    );
    await expect(millsPage.divisionContactDropdown).toContainText(
      contactLabel(anchor, divisionContactId),
    );
  },
);

Then(
  "both contact lists offer only the mill's own client-location contacts",
  async ({ millsPage, request, world }) => {
    const anchor = anchorOf(world);
    // BR-09, from both ends: the API serves exactly the pinned location's contacts, and each dropdown
    // offers exactly those behind the explicit "(None)" choice — nothing more, nothing reordered.
    const served = await readContactOptions(request, anchor.millId);
    expect(served).toEqual(anchor.contacts);
    const expected = [NO_CONTACT_LABEL, ...anchor.contacts.map((c) => c.contactName)];
    expect(
      await millsPage.optionsOf(millsPage.headOfficeContactDropdown, 'Head Office Contact :'),
    ).toEqual(expected);
    expect(
      await millsPage.optionsOf(millsPage.divisionContactDropdown, 'Division Contact :'),
    ).toEqual(expected);
  },
);

// ---- the edit ----

When(
  'I set Head Office to {string} and swap the head-office and division contacts',
  async ({ millsPage }, headOffice) => {
    expect(headOffice, 'S01_EDIT pins the head-office choice this step makes').toBe(
      S01_EDIT.headOfficeLabel,
    );
    await millsPage.choose(millsPage.headOfficeDropdown, S01_EDIT.headOfficeLabel);
    await millsPage.choose(millsPage.headOfficeContactDropdown, S01_EDIT.headOfficeContact.contactName);
    await millsPage.choose(millsPage.divisionContactDropdown, S01_EDIT.divisionContact.contactName);
  },
);

When('I save the mill', async ({ millsPage }) => {
  await expect(millsPage.saveButton).toBeEnabled();
  await millsPage.saveButton.click();
});

Then(
  "the mill's head-office indicator and contacts are persisted as edited",
  async ({ request, world }) => {
    const anchor = anchorOf(world);
    // Polled: the success banner renders from the PUT's response, so a single-shot GET could in
    // principle race the commit the response reports.
    await expect
      .poll(async () => {
        const mill = await readMill(request, anchor.millId);
        return {
          headOfficeContactInd: mill.headOfficeContactInd,
          headOfficeContactId: mill.headOfficeContactId ?? null,
          divisionContactId: mill.divisionContactId ?? null,
        };
      })
      .toEqual({
        headOfficeContactInd: S01_EDIT.headOfficeContactInd,
        headOfficeContactId: S01_EDIT.headOfficeContact.clientContactId,
        divisionContactId: S01_EDIT.divisionContact.clientContactId,
      });
    // The save is the only write in the scenario, and it is optimistically locked: the status and
    // identity must be untouched by it.
    const mill = await readMill(request, anchor.millId);
    expect(mill.millStatusCode).toBe(anchor.statusCode);
    expect(mill.millNumber).toBe(anchor.millNumber);
    expect(mill.millName).toBe(anchor.millName);
  },
);

Then(
  "the Mill Details panel shows the saved mill's last-edited details",
  async ({ millsPage, request, world }) => {
    const anchor = anchorOf(world);
    const served = await readMill(request, anchor.millId);
    // The panel re-renders from the save's response; the stamp it shows must be the one now stored.
    await expect(millsPage.detailsText(served.updateUserid!)).toBeVisible();
    await expect(millsPage.detailsText(served.updateTimestamp!)).toBeVisible();
    // And the form was reset to exactly what was saved, not left holding the staged selection.
    await expect(millsPage.headOfficeDropdown).toContainText(S01_EDIT.headOfficeLabel);
    await expect(millsPage.headOfficeContactDropdown).toContainText(
      S01_EDIT.headOfficeContact.contactName,
    );
    await expect(millsPage.divisionContactDropdown).toContainText(S01_EDIT.divisionContact.contactName);
  },
);

// ---- status (S03 / S04 / S12) ----

Given(
  'the {word} mill is at rest with its status and active users',
  async ({ request, world, millStatusCleanup }, slice) => {
    const anchor = STATUS_MILLS[slice];
    expect(anchor, `no status anchor is pinned for ${slice} (fixtures/mill/mills-test-data.ts)`).toBeTruthy();
    // Re-checked per scenario, as S01 does: a run that died between its click and its cleanup leaves the
    // mill at the TARGET status, and the scenario would then "change" it to what it already is.
    const mill = await readMill(request, anchor.millId);
    expect(
      mill.millStatusCode,
      `mill ${anchor.millId} is not at rest — a previous run left it ${mill.millStatusCode}. `
        + 'preflight/mill-anchors.setup.ts names what to restore.',
    ).toBe(anchor.statusCode);
    expect(
      await activeUserGuids(request, anchor.millId),
      `mill ${anchor.millId}'s active users are not at rest`,
    ).toEqual([...anchor.status!.activeUserGuids]);
    world.millAnchor = anchor;
    // Registered BEFORE the click, so a failure after it still restores the mill.
    millStatusCleanup.push(anchor);
  },
);

Then('{string} is offered and {string} is not', async ({ millsPage }, offered, absent) => {
  // STA-001: exactly one of the pair is rendered, chosen by the status code — never disabled.
  const button = (name: string) => {
    expect(['Deactivate', 'Activate'], `"${name}" is not a mill status action`).toContain(name);
    return name === 'Deactivate' ? millsPage.deactivateButton : millsPage.activateButton;
  };
  await expect(button(offered)).toBeVisible();
  await expect(button(absent)).toHaveCount(0);
});

When('I deactivate the mill', async ({ millsPage }) => {
  await expect(millsPage.deactivateButton).toBeEnabled();
  await millsPage.deactivateButton.click();
});

When('I activate the mill', async ({ millsPage }) => {
  await expect(millsPage.activateButton).toBeEnabled();
  await millsPage.activateButton.click();
});

Then("the mill's status is persisted as {string}", async ({ request, world }, code) => {
  const anchor = anchorOf(world);
  // Polled for the same reason as the contacts read-back: the banner renders from the response.
  await expect.poll(async () => (await readMill(request, anchor.millId)).millStatusCode).toBe(code);
});

Then('the Mill Details panel shows the status {string}', async ({ millsPage }, label) => {
  await expect(millsPage.detailsText(label)).toBeVisible();
});

Then('schedule viewing for the mill is blocked for every role', async ({ request, world }) => {
  const anchor = anchorOf(world);
  const year = await currentReportingYear(request);
  // BR-06 from both ends. The working context reports the mill not viewable, and the schedule endpoint
  // itself refuses — for the submitter who reports on it AND the administrator, whom no scoping
  // exempts. The anchor answered 200 here before the deactivate (it is a Draft with its report set).
  expect((await readMillContext(request, anchor.millId, year)).millViewable).toBe(false);
  for (const role of ['ILCR_SUBMITTER', 'ILCR_ADMIN']) {
    const res = await request.get(scheduleUrl(anchor.millId, year), {
      headers: { [MOCK_GROUPS_HEADER]: role },
    });
    expect(res.status(), `Schedule 1 GET as ${role} on a closed mill`).toBe(409);
    expect(((await res.json()) as { detail?: string }).detail).toBe(MILL_NOT_ACTIVE_MESSAGE);
  }
});

Then(
  'the mill has its report records for the current reporting year and its schedules open',
  async ({ request, world }) => {
    const anchor = anchorOf(world);
    const year = await currentReportingYear(request);
    // BR-07. The (mill, year) resolves with both tracks, which it does only with its status row; and a
    // schedule now opens, for the submitter, where it answered 409 while the mill was closed. The
    // eleven category rows are not visible through any endpoint — activate's own COMPLETE/PARTIAL
    // check is what guarantees them, and a PARTIAL set would have refused the activation outright.
    const context = await readMillContext(request, anchor.millId, year);
    expect(context.millViewable).toBe(true);
    expect(context.schedules1To10Status, 'no report-status row for the current year').toBeTruthy();
    expect(context.schedule11Status, 'no Schedule 11 track for the current year').toBeTruthy();
    const res = await request.get(scheduleUrl(anchor.millId, year), {
      headers: { [MOCK_GROUPS_HEADER]: 'ILCR_SUBMITTER' },
    });
    expect(res.status(), 'Schedule 1 GET on the re-activated mill').toBe(200);
  },
);

Then('every associated user is shown as active', async ({ millsPage, world }) => {
  const anchor = anchorOf(world);
  for (const guid of anchor.status!.activeUserGuids) {
    await expect(millsPage.userRow(guid)).toContainText('Active');
    await expect(millsPage.userDeactivateButton(guid)).toBeVisible();
  }
});

When('I deactivate every active associated user', async ({ millsPage, request, world }) => {
  const anchor = anchorOf(world);
  // Driven by what the SERVER says is active, not by the pin: the refusal this follows is about the
  // stored assignments, so those are the ones to end.
  const active = await activeUserGuids(request, anchor.millId);
  expect(active.length, 'the refusal step expects at least one active user').toBeGreaterThan(0);
  for (const guid of active) {
    await millsPage.userDeactivateButton(guid).click();
    // Each click is one write; the next row's revision is only valid once this one has landed.
    await expect(millsPage.userActivateButton(guid)).toBeVisible();
  }
});

Then('no associated user is active any more', async ({ millsPage, request, world }) => {
  const anchor = anchorOf(world);
  await expect.poll(() => activeUserGuids(request, anchor.millId)).toEqual([]);
  for (const guid of anchor.status!.activeUserGuids) {
    await expect(millsPage.userRow(guid)).toContainText('Inactive');
  }
});
