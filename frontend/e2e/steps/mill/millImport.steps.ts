import { Given, When, Then, expect } from '../fixtures';
import { IMPORT_MILL } from '../../fixtures/mill/mills-test-data';
import {
  currentReportingYear,
  readImportable,
  readMill,
  readMillContext,
  trackedStatus,
} from './millsApi';

/**
 * UC-MILL-001 import steps (S02, S14). The mill is the one seeded importable mill, IMPORT_MILL — a
 * THE.MILL row with no ILCR tracking at all. Importing it writes rows no endpoint removes, so every
 * scenario here registers it with `millDbCleanup.imports` before it clicks.
 */

/** `failImportingMillMsg` (messages.properties), verbatim — what MillMaintenanceException.importFailed carries. */
const IMPORT_FAILED = 'ILCR cannot import the Mill. Please refer to logs.';

Given('the import mill is not tracked in ILCR', async ({ request, millDbCleanup }) => {
  // Re-checked per scenario: a run that died between its import and its cleanup leaves the mill
  // tracked, and the importable search would then correctly list nothing.
  expect(
    await trackedStatus(request, IMPORT_MILL.millId),
    `mill ${IMPORT_MILL.millId} is already tracked — a previous run left it imported. Run `
      + `scripts/mill_db_restore.py forget-import ${IMPORT_MILL.millId}.`,
  ).toBe(404);
  // Registered now, before any click: the DB delete is idempotent, so a scenario that never imports
  // costs nothing.
  millDbCleanup.imports.push(IMPORT_MILL.millId);
});

Given('the next import request fails on the server', async ({ page }) => {
  // S14's trigger is "the import transaction throws". No real data makes the service's integrity
  // failure happen on demand (it needs a concurrent import or corrupt leftover rows), so the ONE
  // request is answered in flight with exactly what the service sends when it does: HTTP 500 and the
  // ERR-003 ProblemDetail. It fires once; the retry reaches the real backend. What this proves is the
  // page's side of S14 — the message, and that a retry works. The rollback itself is pinned below e2e
  // (MillMaintenanceIT, Story 22.1).
  await page.route(
    `**/api/v1/admin/mills/${IMPORT_MILL.millId}/import`,
    (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        json: { title: 'Internal Server Error', status: 500, detail: IMPORT_FAILED },
      }),
    { times: 1 },
  );
});

When('I open Import Mill and search for the import mill by its number', async ({ millsPage }) => {
  await millsPage.openImport();
  await millsPage.searchImportByNumber(IMPORT_MILL.millNumber);
});

Then('the importable results list the import mill', async ({ millsPage, request }) => {
  // BR-04 from both ends: the server lists it as importable, and the dialog offers its Import control.
  expect(await readImportable(request, IMPORT_MILL.millNumber)).toContainEqual(IMPORT_MILL);
  await expect(millsPage.importRow(IMPORT_MILL.millNumber)).toBeVisible();
  await expect(millsPage.importDialog.getByText(IMPORT_MILL.millName, { exact: true })).toBeVisible();
});

When('I import the mill and confirm {string}', async ({ millsPage }, question) => {
  await millsPage.importRow(IMPORT_MILL.millNumber).click();
  // CNF-001 — the screen's only confirmation, its text served by the backend (confirmImportMill).
  await expect(millsPage.confirmDialog).toBeVisible();
  await expect(millsPage.confirmDialog.getByText(question, { exact: true })).toBeVisible();
  await millsPage.confirmDialog.getByRole('button', { name: 'Yes', exact: true }).click();
});

Then(
  "the Mill Details panel shows the imported mill with status {string}",
  async ({ millsPage }, label) => {
    // No success message exists for import (legacy had none, and none is invented): the imported
    // mill's own details appearing IS the confirmation.
    await expect(millsPage.identity(IMPORT_MILL.millNumber, IMPORT_MILL.millName)).toBeVisible();
    await expect(millsPage.detailsText(label)).toBeVisible();
  },
);

Then(
  'the imported mill is tracked as Closed with head-office indicator {string}',
  async ({ request }, indicator) => {
    // BR-03: the cross-reference is created initialized Closed, head office set.
    await expect.poll(() => trackedStatus(request, IMPORT_MILL.millId)).toBe(200);
    const mill = await readMill(request, IMPORT_MILL.millId);
    expect({ status: mill.millStatusCode, headOffice: mill.headOfficeContactInd }).toEqual({
      status: 'CLS',
      headOffice: indicator,
    });
  },
);

Then(
  'the imported mill has its report records for the current reporting year',
  async ({ request }) => {
    // BR-03: enrolled in the current year. The working context resolves both tracks only with the
    // report-status row; the mill is closed, so it is not viewable.
    const year = await currentReportingYear(request);
    const context = await readMillContext(request, IMPORT_MILL.millId, year);
    expect(context.schedules1To10Status, `no ${year} report-status row after import`).toBeTruthy();
    expect(context.schedule11Status).toBeTruthy();
    expect(context.millViewable).toBe(false);
  },
);

Then('the import dialog shows the error {string}', async ({ millsPage }, message) => {
  // Kept IN the dialog (22.3): a refusal leaves the search results standing for the retry.
  await expect(millsPage.importDialog.getByText(message, { exact: true })).toBeVisible();
});

Then('the import mill is still not tracked in ILCR', async ({ request }) => {
  expect(await trackedStatus(request, IMPORT_MILL.millId)).toBe(404);
  expect(await readImportable(request, IMPORT_MILL.millNumber)).toContainEqual(IMPORT_MILL);
});
