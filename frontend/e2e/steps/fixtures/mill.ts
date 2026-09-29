import { test as base } from 'playwright-bdd';

import { MillsPage } from '../../pages/mill/millsPage';
import { type AdminMillAnchor } from '../../fixtures/mill/mills-test-data';
import {
  dropAssociation,
  forgetEnrolment,
  forgetImport,
  restoreMillContacts,
  restoreMillStatus,
} from '../mill/millsApi';

/**
 * Mill administration (mill) fixtures — page object and cleanup registry owned by UC-MILL-001. Nothing
 * here is referenced by another domain, so this file can be edited without touching anyone else's
 * coverage. Cross-domain things (`world`, `homePage`, `appShell`) live in `./global`.
 */

export type MillFixtures = {
  millsPage: MillsPage;
  /**
   * Cleanup registry for mills whose editable panel (head-office indicator + the two contacts) a
   * scenario saves. Each registered anchor is put back to its pinned `atRest` values afterwards.
   *
   * Register the anchor the moment the scenario knows it will save — in its precondition, BEFORE the
   * Save click — so a failure between the click and the assertion still restores the row. Restoring an
   * already-at-rest row is a no-op, so registering early costs nothing. Fails loud on residue.
   *
   * It restores the three editable columns only: the audit stamp and REVISION_COUNT that every save
   * moves have no endpoint that writes them (see fixtures/mill/mills-test-data.ts).
   */
  millContactsCleanup: AdminMillAnchor[];
  /**
   * Cleanup registry for status anchors (S03 / S04 / S12) whose ACT/CLS status or user assignments a
   * scenario changes. Each registered anchor is put back to its pinned `statusCode` and then to its
   * pinned active users — mill first, because an assignment cannot be activated on a closed mill.
   * Same contract as above: register in the precondition, before the click; restoring an at-rest mill
   * is a no-op; fails loud on residue.
   */
  millStatusCleanup: AdminMillAnchor[];
  /**
   * Cleanup registry for the two writes NO endpoint undoes, put back at the DB through
   * scripts/mill_db_restore.py (guarded to the dedicated seeded mills):
   *  - `imports`: mill ids a scenario may IMPORT (S02 / S14) — their xref and current-year records
   *    are deleted, returning the mill to importable;
   *  - `enrolments`: mill ids a scenario may ACTIVATE while they have no current-year records (GAP-6)
   *    — the report rows the activation enrols are deleted;
   *  - `associations`: (mill, user) pairs a scenario may ADD (S05, GAP-7) — that association row is
   *    deleted, and the account too when the add provisioned it.
   * Register before the click, as above; both deletes are idempotent, so a scenario that failed first
   * costs nothing. Fails loud.
   */
  millDbCleanup: {
    imports: number[];
    enrolments: number[];
    associations: { millId: number; userGuid: string }[];
  };
};

export const millTest = base.extend<MillFixtures>({
  millsPage: async ({ page }, use) => {
    await use(new MillsPage(page));
  },

  millContactsCleanup: async ({ request }, use) => {
    const registrations: AdminMillAnchor[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad restore cannot hide the others.
    const residue: string[] = [];
    for (const anchor of registrations) {
      try {
        await restoreMillContacts(request, anchor);
      } catch (err) {
        residue.push(`${anchor.millId} (${anchor.millNumber}): ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] mill head-office/contact panel not restored — the seeded DB is left mutated: '
          + `${residue.join('; ')}. Put it back before re-running (preflight/mill-anchors.setup.ts `
          + 'will name the mill).',
      );
    }
  },

  millStatusCleanup: async ({ request }, use) => {
    const registrations: AdminMillAnchor[] = [];
    await use(registrations);

    const residue: string[] = [];
    for (const anchor of registrations) {
      try {
        await restoreMillStatus(request, anchor);
      } catch (err) {
        residue.push(`${anchor.millId} (${anchor.millNumber}): ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] mill status / user assignments not restored — the seeded DB is left mutated: '
          + `${residue.join('; ')}. Put it back before re-running (preflight/mill-anchors.setup.ts `
          + 'will name the mill).',
      );
    }
  },

  // Depends on millStatusCleanup ONLY for ordering: Playwright tears a fixture down before the ones it
  // depends on, so the DB deletes run FIRST. That matters for S05 — the status restore verifies the
  // mill's association set, and the added row must already be gone when it looks.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  millDbCleanup: async ({ millStatusCleanup }, use) => {
    const registrations = {
      imports: [] as number[],
      enrolments: [] as number[],
      associations: [] as { millId: number; userGuid: string }[],
    };
    await use(registrations);

    const residue: string[] = [];
    for (const millId of registrations.imports) {
      try {
        forgetImport(millId);
      } catch (err) {
        residue.push(`import of ${millId}: ${(err as Error).message}`);
      }
    }
    for (const millId of registrations.enrolments) {
      try {
        forgetEnrolment(millId);
      } catch (err) {
        residue.push(`enrolment of ${millId}: ${(err as Error).message}`);
      }
    }
    for (const { millId, userGuid } of registrations.associations) {
      try {
        dropAssociation(millId, userGuid);
      } catch (err) {
        residue.push(`association ${millId}/${userGuid}: ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        `[cleanup] mill DB restore failed — the seeded DB is left mutated: ${residue.join('; ')}. `
          + 'Run scripts/mill_db_restore.py by hand (its docstring names both actions).',
      );
    }
  },
});
