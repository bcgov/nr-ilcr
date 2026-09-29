import { When, Then, expect } from '../fixtures';
import {
  type AdminMillAnchor,
  IMPORT_MILL,
  NO_CONTACT_LABEL,
} from '../../fixtures/mill/mills-test-data';
import { currentReportingYear, millContextStatus, readMill } from './millsApi';

/**
 * UC-MILL-001 steps for the coverage-gap scenarios (defects.md GAP-1, -4, -5, -6). Everything else they
 * need is shared with the slice steps. No DOM selectors here — they live in pages/mill/millsPage.ts.
 */

const anchorOf = (world: { millAnchor?: AdminMillAnchor }): AdminMillAnchor => {
  expect(world.millAnchor, 'a mills precondition must set world.millAnchor first').toBeTruthy();
  return world.millAnchor!;
};

// ---- GAP-1: a blank contact is a delete ----

When('I clear the Division Contact to {string}', async ({ millsPage }, label) => {
  expect(label, 'the no-contact option is the page’s own label').toBe(NO_CONTACT_LABEL);
  await millsPage.choose(millsPage.divisionContactDropdown, NO_CONTACT_LABEL);
});

Then(
  "the mill's division contact is persisted as cleared and nothing else on the panel moved",
  async ({ request, world }) => {
    const anchor = anchorOf(world);
    // Polled for the same reason as S01's read-back. A cleared id arrives ABSENT (non_null wire).
    await expect
      .poll(async () => (await readMill(request, anchor.millId)).divisionContactId ?? null)
      .toBeNull();
    const mill = await readMill(request, anchor.millId);
    expect({
      headOfficeContactInd: mill.headOfficeContactInd,
      headOfficeContactId: mill.headOfficeContactId ?? null,
      status: mill.millStatusCode,
    }).toEqual({
      headOfficeContactInd: anchor.atRest.headOfficeContactInd,
      headOfficeContactId: anchor.atRest.headOfficeContactId,
      status: anchor.statusCode,
    });
  },
);

Then('the Division Contact shows {string}', async ({ millsPage }, label) => {
  await expect(millsPage.divisionContactDropdown).toContainText(label);
});

// ---- GAP-4: the Status criterion, and the import dialog's Name ----

When(
  'I search the mill dialog by name {string} and status {string}',
  async ({ millsPage }, name, status) => {
    await millsPage.searchByNameAndStatus(name, status);
  },
);

Then('every listed mill reads {string}', async ({ millsPage }, label) => {
  const statuses = await millsPage.listedStatuses();
  expect(statuses.length, 'the status search listed nothing').toBeGreaterThan(0);
  expect(new Set(statuses)).toEqual(new Set([label]));
});

Then('the mill search does not list mill {string}', async ({ millsPage }, millNumber) => {
  await expect(millsPage.resultRow(millNumber)).toHaveCount(0);
});

When('I open Import Mill and search for the import mill by its name', async ({ millsPage }) => {
  await millsPage.openImport();
  await millsPage.searchImportByName(IMPORT_MILL.millName);
});

// ---- GAP-5: the Confirmation modal, opened and declined ----

When('I start importing the mill and am asked {string}', async ({ millsPage }, question) => {
  await millsPage.importRow(IMPORT_MILL.millNumber).click();
  await expect(millsPage.confirmDialog).toBeVisible();
  await expect(millsPage.confirmDialog.getByText(question, { exact: true })).toBeVisible();
});

When('I answer No to the confirmation', async ({ millsPage }) => {
  await millsPage.confirmDialog.getByRole('button', { name: 'No', exact: true }).click();
  await expect(millsPage.confirmDialog).toHaveCount(0);
});

// ---- GAP-6: the partial set is refused and left as it was ----

Then("the mill's current-year records are still only partial", async ({ request, world }) => {
  const anchor = anchorOf(world);
  // The refusal rolls the WHOLE activation back: the status row is still there (the context resolves)
  // and nothing was enrolled over it — a completed set would have let the activation through.
  const year = await currentReportingYear(request);
  expect(await millContextStatus(request, anchor.millId, year)).toBe(200);
  expect((await readMill(request, anchor.millId)).millStatusCode).toBe('CLS');
});
