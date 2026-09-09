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
 * THE ONE REQUEST THE SUITE CANNOT MAKE AS ITS DECLARED ROLE — a deliberate, narrow workaround for
 * an APP-SIDE gap, not a test convenience. Read this before adding another.
 *
 * `GET /api/v1/mills` is the Home mill dropdown. `MillContextController.currentUserGuid()` resolves
 * to `""` for the security-off dev principal (a `UsernamePasswordAuthenticationToken`, not a `Jwt`),
 * and `MillContextService.listMills` fail-closes a submitter with a blank GUID to `List.of()`. So a
 * mock submitter is offered NO mill and cannot reach any schedule — while `validateMillAccess`, two
 * methods above it, EXEMPTS that same principal, so the very same identity may WRITE to any mill.
 * The two gates disagree, and under mock auth that leaves no identity able to do the suite's work:
 * the admin (the only one Home offers a mill to) is read-only at Draft under the Story 16.1 matrix,
 * and the submitter sees nothing to select.
 *
 * The app-side fix is to make the two gates agree (exempt the mock principal in `listMills` as
 * `validateMillAccess` already does), which also un-breaks local dev, where neither mock user can
 * currently enter schedule data. That is deliberately NOT taken here: this change is test-only. So
 * the suite borrows the administrator for exactly this one READ and stays the submitter for
 * everything else — every schedule GET, every write, every check-status.
 *
 * WHY A HEADER REWRITE AND NOT A LOCALSTORAGE SWITCH: the identity would then depend on WHEN Home is
 * visited. Any remount or re-navigation refetches the list, so a switch-back would have to be
 * threaded through every entry point and would break the moment someone added another. Keying on the
 * URL makes it independent of navigation order — one handler, one request, no ordering rule to
 * remember.
 *
 * WHAT IT COSTS, stated plainly: the dropdown the suite sees is the ADMIN's list (`findAllMills`,
 * all listable mills including closed). A real submitter's SCOPED list — `findMillsForUser`, the
 * S06 "closed associated mills still appear" shape — is therefore NOT covered by this suite and
 * cannot be while mock auth has no directory GUID; it is covered by the backend's own tests. That
 * is unchanged from the status quo (the whole suite was an administrator until now), and it is
 * recorded as a gap in `features/sec/uc-sec-001-working-context/defects.md`.
 *
 * Registered by the global `page` fixture, so it applies to every scenario. Playwright matches
 * handlers in reverse registration order, so a later, broader handler still wins where a scenario
 * wants one — `appShell.openWithoutBackend`'s `**\/api\/**` abort keeps aborting this too.
 */
export async function grantAdminOnMillList(page: Page): Promise<void> {
  await page.route(/\/api\/v1\/mills(\?|$)/, (route) =>
    route.continue({
      headers: { ...route.request().headers(), [MOCK_GROUPS_HEADER.toLowerCase()]: 'ILCR_ADMIN' },
    }),
  );
}
