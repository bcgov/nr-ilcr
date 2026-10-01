import { test as base } from 'playwright-bdd';

import { UsersPage } from '../../pages/usr/usersPage';
import { type UsrAnchor } from '../../fixtures/usr/users-test-data';
import { restoreUser } from '../usr/usersApi';

/**
 * User administration (usr) fixtures — page object and cleanup registry owned by UC-USR-001 / UC-USR-002.
 * Nothing here is referenced by another domain. Cross-domain things (`world`, `appShell`) live in
 * `./global`; the Mills page object a carry starts from is the mill domain's `millsPage`.
 */

export type UsrFixtures = {
  usersPage: UsersPage;
  /**
   * Cleanup registry for seeded users whose account or assignments a scenario changes. Each registered
   * user is put back to its pinned at-rest state (steps/usr/usersApi.ts restoreUser): added assignments
   * and provisioned accounts deleted at the DB (scripts/usr_db_restore.py — no endpoint can), ended or
   * revived rows put back through the API, and the account flag last.
   *
   * Register the user in its precondition, BEFORE the first click, so a failure between the click and
   * the assertion still restores it. Restoring an at-rest user is a no-op, so registering early costs
   * nothing. Fails loud on residue.
   */
  usrCleanup: UsrAnchor[];
};

export const usrTest = base.extend<UsrFixtures>({
  usersPage: async ({ page }, use) => {
    await use(new UsersPage(page));
  },

  usrCleanup: async ({ request }, use) => {
    const registrations: UsrAnchor[] = [];
    await use(registrations);

    // FAILS LOUD: every registration is attempted first so one bad restore cannot hide the others.
    const residue: string[] = [];
    for (const anchor of registrations) {
      try {
        await restoreUser(request, anchor);
      } catch (err) {
        residue.push(`${anchor.key} (${anchor.userGuid}): ${(err as Error).message}`);
      }
    }
    if (residue.length > 0) {
      throw new Error(
        '[cleanup] user account / assignments not restored — the seeded DB is left mutated: '
          + `${residue.join('; ')}. Put it back before re-running (preflight/usr-anchors.setup.ts `
          + 'will name the user).',
      );
    }
  },
});
