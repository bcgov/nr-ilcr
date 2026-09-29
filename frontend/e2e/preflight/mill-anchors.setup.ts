import { test, expect } from '@playwright/test';
import {
  ADD_NEW_USER_MILL,
  ADMIN_MILL_ANCHORS,
  IMPORT_MILL,
  NEW_ACCOUNT_GUID,
} from '../fixtures/mill/mills-test-data';
import {
  activeUserGuids,
  endedUserGuids,
  millContextStatus,
  readImportable,
  trackedStatus,
  atRest,
  currentReportingYear,
  readContactOptions,
  readMill,
  readMillContext,
} from '../steps/mill/millsApi';

/**
 * Mill administration preflight — asserts every pinned mill still resolves, at rest, BEFORE the suite
 * runs, so a re-extracted / drifted DB (or a previous run that died mid-save) fails fast with one
 * actionable message instead of a confusing mid-scenario red.
 *
 * READ-ONLY: it never writes, so it is safe to run against the seeded DB every time. It does NOT restore
 * a drifted row — putting data back is a decision for whoever reads the message, not for a preflight.
 *
 * What it deliberately does NOT assert: the audit stamp and REVISION_COUNT. Every save moves both and
 * no endpoint can put them back, so they drift run over run by design (fixtures/mill/mills-test-data.ts).
 */

const HINT = 'Re-ground fixtures/mill/mills-test-data.ts.';

for (const anchor of ADMIN_MILL_ANCHORS) {
  test(`mill anchor ${anchor.millId} (${anchor.millNumber} - ${anchor.millName}) is tracked, at rest, and offers its contacts${anchor.status ? ' (status anchor)' : ''}`, async ({ request }) => {
    const mill = await readMill(request, anchor.millId);

    expect(
      { millNumber: mill.millNumber, millName: mill.millName, status: mill.millStatusCode },
      `mill ${anchor.millId}'s identity or status moved. ${HINT}`,
    ).toEqual({ millNumber: anchor.millNumber, millName: anchor.millName, status: anchor.statusCode });
    expect(mill.statusDescription, `the status label moved. ${HINT}`).toBe(anchor.statusDescription);

    expect(
      atRest(mill, anchor),
      `mill ${anchor.millId}'s editable panel is not at rest — a run that died between its Save and its `
        + `cleanup leaves it edited. Served: indicator=${mill.headOfficeContactInd} `
        + `headOffice=${mill.headOfficeContactId ?? null} division=${mill.divisionContactId ?? null}; `
        + `expected ${JSON.stringify(anchor.atRest)}. Restore it with PUT /api/v1/admin/mills/`
        + `${anchor.millId}/contacts (as ILCR_ADMIN, with the served revisionCount) before re-running.`,
    ).toBe(true);

    expect(
      await readContactOptions(request, anchor.millId),
      `mill ${anchor.millId}'s client-location contacts moved. In CI this means `
        + 'db-e2e/R__80_e2e_anchor_seed.sql lost its CLIENT_LOCATION / CLIENT_CONTACT rows for this '
        + `mill. ${HINT}`,
    ).toEqual(anchor.contacts);

    if (!anchor.status) return;

    // A status anchor's fixture is also WHO is active on it: S03's deactivate is refused if anyone is,
    // and S12 is written against exactly its one licensee. The usual way this goes wrong is a re-apply
    // of an association patch that forgot to skip the E2E_SEED_MILLSTAT sentinel.
    expect(
      await activeUserGuids(request, anchor.millId),
      `mill ${anchor.millId}'s ACTIVE user assignments moved. If a run died mid-scenario, restore them `
        + 'on the Mills page; if a patch associated a user, it must skip the E2E_SEED_MILLSTAT sentinel '
        + '(real-test-data-patches/common/mock-submitter-associations.sql).',
    ).toEqual([...anchor.status.activeUserGuids]);
    expect(
      await endedUserGuids(request, anchor.millId),
      `mill ${anchor.millId}'s ENDED user assignments moved. A run that died mid-scenario leaves an `
        + 'S09/S13 user active: deactivate it on the Mills page (on the S13 mill, before closing it).',
    ).toEqual([...anchor.status.endedUserGuids]);

    // And its current-year report set must exist, or `activate` ENROLS the mill — rows no endpoint
    // removes, so the cleanup could never put the database back. The status row is what the working
    // context reports; the eleven category rows travel with it in the patch and the seed.
    const year = await currentReportingYear(request);
    if (anchor.currentYearRecords === 'none') {
      // GAP-6's enrol mill is defined by having NO records: a run that died before its DB cleanup
      // leaves it enrolled, and its activate would then write the status alone and prove nothing.
      expect(
        await millContextStatus(request, anchor.millId, year),
        `mill ${anchor.millId} has ${year} report records but is pinned as having none — run `
          + `scripts/mill_db_restore.py forget-enrolment ${anchor.millId}.`,
      ).toBe(404);
      return;
    }
    // Complete AND partial both carry the status row (the context cannot see the category rows; the
    // parity gate checks those in the seed, and GAP-6's partial scenario proves the refusal itself).
    const context = await readMillContext(request, anchor.millId, year);
    expect(
      context.schedules1To10Status,
      `mill ${anchor.millId} has no report records for the current reporting year ${year}. A newer `
        + 'year was opened after the anchor was seeded: re-apply '
        + 'real-test-data-patches/mill/mill-status-anchors.sql (it derives the year), and move the '
        + 'R__80 rows to that year.',
    ).toBeTruthy();
  });
}

test(`GAP-7's user ${NEW_ACCOUNT_GUID} has no association anywhere (so the add provisions its account)`, async ({ request }) => {
  // No endpoint lists accounts, so "no account" is read as "no association on the mill it is added
  // to"; the parity gate checks the seed carries no ILCR_USER row for it at all.
  expect(await activeUserGuids(request, ADD_NEW_USER_MILL.millId)).toEqual([]);
  expect(await endedUserGuids(request, ADD_NEW_USER_MILL.millId)).toEqual([]);
});

test(`import mill ${IMPORT_MILL.millId} (${IMPORT_MILL.millNumber} - ${IMPORT_MILL.millName}) is importable and not tracked`, async ({ request }) => {
  // S02/S14 import it. A run that died between the import and its cleanup leaves it tracked, and the
  // importable search would then (correctly) list nothing.
  expect(
    await trackedStatus(request, IMPORT_MILL.millId),
    `mill ${IMPORT_MILL.millId} is tracked — a previous import was not cleaned up. Run `
      + `scripts/mill_db_restore.py forget-import ${IMPORT_MILL.millId}.`,
  ).toBe(404);
  expect(
    await readImportable(request, IMPORT_MILL.millNumber),
    `mill ${IMPORT_MILL.millId} is not offered for import. In CI this means R__80 lost its THE.MILL row; `
      + 'locally, real-test-data-patches/mill/mill-status-anchors.sql is not applied.',
  ).toContainEqual(IMPORT_MILL);
});
