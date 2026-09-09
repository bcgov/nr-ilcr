import { type Page } from '@playwright/test';

/**
 * WHO THE SUITE IS. ILCR runs with security OFF for e2e (`ilcr.security.enabled=false`), and the
 * acting role is chosen IN THE BROWSER: `service/api-service.ts` sends the selected mock user's
 * roles as the `X-Mock-Groups` header, and the backend's `MockPrincipalFilter` prefers that header
 * over its configured `ilcr.security.mock-role` default. So the value seeded here — not a backend
 * property, not a Playwright project — decides what role every request in a scenario carries.
 *
 * WHY IT IS SEEDED EXPLICITLY rather than inherited. `findMockUser` falls back to `MOCK_USERS[0]`
 * for an absent or unrecognised id, so a suite that seeds nothing silently acts as whichever user
 * happens to be listed first. That is not hypothetical: from #265 (which added the header) until
 * this file, the whole suite ran as `ILCR_ADMIN` while all twelve feature files declared "As a
 * Licensee". It stayed invisible because the old write gate was `callerMayEdit && Draft`, which an
 * administrator satisfied. Story 16.1's role x status matrix — where an administrator is read-only
 * at Draft — turned it into ~200 failures reading as an app regression. Declaring the identity
 * costs one line per entry point and cannot drift; `preflight/mock-user.setup.ts` checks the ids
 * and roles named here against `mockUsers.ts` on disk so a rename fails loudly instead of falling
 * back to the default.
 *
 * WHY localStorage AND NOT `use.storageState`: storageState matches its recorded ORIGIN, so a
 * trailing slash or a redirect on `E2E_BASE_URL` would drop the entry and hand the suite the
 * default user with no error at all — the exact silent-fallback failure this exists to prevent.
 * An init script runs on every navigation regardless of origin.
 */

/** The key `MockAuthProvider` reads at mount and `api-service` re-reads on every request. */
export const MOCK_USER_STORAGE_KEY = 'nr-ilcr.mock-user';

/** The header `api-service` sends the selected user's roles in, and `MockPrincipalFilter` reads. */
export const MOCK_GROUPS_HEADER = 'X-Mock-Groups';

/**
 * The mock users' ids, as `context/auth/mockUsers.ts` declares them.
 *
 *  - `submitter` → `ILCR_SUBMITTER`, the legacy ILCR_LICENSEE: edits at Draft. Every feature file
 *    says "As a Licensee", so this is the suite's default.
 *  - `admin` → `ILCR_ADMIN`: edits at Submitted/Verified, and is the only role the admin-gated nav
 *    (Administration, Generate Reports) renders for.
 */
export type MockUserId = 'submitter' | 'admin';

/**
 * Act as the given mock user for the rest of this page's life. Call BEFORE the navigation that
 * should carry the identity — an init script only applies to documents opened after it is added.
 *
 * Init scripts run in the order they were added, so a later call wins: the global `page` fixture
 * seeds the submitter for every scenario, and a scenario that needs the administrator overrides it
 * by calling this again.
 */
export async function seedMockUser(page: Page, id: MockUserId): Promise<void> {
  await page.addInitScript(
    ({ key, value }: { key: string; value: string }) => {
      try {
        window.localStorage.setItem(key, value);
      } catch {
        // `about:blank` and friends have an opaque origin, where touching localStorage throws.
        // The seed only has to survive on the app's own origin, so swallowing this is correct —
        // and a throw here would fail the scenario in the fixture, before its first step.
      }
    },
    { key: MOCK_USER_STORAGE_KEY, value: id },
  );
}

/**
 * HISTORY, kept because it is the reason this file exists and the reason not to re-add the shortcut.
 *
 * `grantAdminOnMillList` used to live here: a `page.route` handler that rewrote `X-Mock-Groups` to
 * `ILCR_ADMIN` on `GET /api/v1/mills` alone, because a mock SUBMITTER was offered no mill at all.
 * `MillContextController.currentUserGuid()` returned `""` for the security-off principal (a
 * `UsernamePasswordAuthenticationToken`, not a `Jwt`) and `MillContextService.listMills`
 * fail-closes a submitter with a blank GUID to `List.of()` — correctly, so a submitter can never
 * see mills that are not theirs. So the suite had to borrow the administrator for that one read.
 *
 * It is GONE because the app-side gap is fixed (bcgov/nr-ilcr#385): `MockPrincipalFilter` now
 * presents a stand-in directory GUID (`ilcr.security.mock-user-guid`), and that GUID is associated
 * with the mills in BOTH e2e databases — `db-e2e/R__80_e2e_anchor_seed.sql` for CI, and
 * `real-test-data-patches/common/mock-submitter-associations.sql` for the extract. The dropdown is
 * therefore the submitter's OWN scoped list (`findMillsForUser`) now, not the admin's
 * (`findAllMills`), which also closes the coverage gap the workaround cost — `sec` GAP-5.
 *
 * If a mill dropdown ever comes back empty, the cause is data, not identity: check that this GUID
 * has active `ILCR_MILL_USER_XREF` rows in whichever database you are pointed at.
 */
