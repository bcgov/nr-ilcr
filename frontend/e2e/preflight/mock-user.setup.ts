import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'node:url';
import {
  MOCK_GROUPS_HEADER,
  MOCK_USER_STORAGE_KEY,
  type MockUserId,
} from '../pages/common/mockUser';

/**
 * PREFLIGHT — the mock-user lever the suite pulls is still connected to the app.
 *
 * ---------------------------------------------------------------------------------------------------
 * WHY THIS EXISTS
 * ---------------------------------------------------------------------------------------------------
 * With security off, the acting ROLE is chosen in the browser: the suite seeds an id into
 * `localStorage['nr-ilcr.mock-user']`, `api-service` turns the matching user's roles into the
 * `X-Mock-Groups` header, and `MockPrincipalFilter` prefers that header over its configured default.
 * Three separate app-side facts have to hold for that to mean anything, and `findMockUser`'s
 * `?? MOCK_USERS[0]` fallback makes ALL of them fail SILENTLY: a renamed id, a re-pointed role, or a
 * changed storage key each hand the suite whichever user is listed first, with no error anywhere.
 *
 * That is not a hypothetical. From #265 (which added the header) until the fix that added this file,
 * the entire suite ran as `ILCR_ADMIN` while all twelve feature files declared "As a Licensee". The
 * old write gate was `callerMayEdit && Draft`, which an administrator satisfied, so nothing showed —
 * until Story 16.1's role x status matrix made an administrator read-only at Draft and ~200 scenarios
 * failed at once, reading as an app regression rather than as a wrong identity. Three e2e documents
 * still asserted the selector was "frontend-only" and reached no API.
 *
 * So this asserts the contract off disk, in the `setup` project, with no database and no browser —
 * the same shape as `ci-seed-parity.setup.ts`. A rename now fails HERE, in two seconds, naming the
 * constant that moved.
 *
 * WHAT IT DOES NOT CATCH: whether the backend honours the header (that is MockPrincipalFilter's own
 * unit test), and whether the ROLE grants what a scenario needs (that is ScheduleEditability's truth
 * table plus the scenarios themselves). This checks only that the id the suite seeds still names the
 * user it means, so the identity cannot silently revert to the default.
 */

// `"type": "module"`, so no CommonJS `__dirname` — same ESM-safe idiom as the sibling setups.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const MOCK_USERS_TS = path.join(HERE, '../../src/context/auth/mockUsers.ts');
const API_SERVICE_TS = path.join(HERE, '../../src/service/api-service.ts');

/** Every id `seedMockUser` accepts, and the FAM role each MUST resolve to. */
const EXPECTED: { id: MockUserId; role: string; why: string }[] = [
  {
    id: 'submitter',
    role: 'ILCR_SUBMITTER',
    why: "the suite's default identity — the legacy ILCR_LICENSEE, and the only role that may edit a Draft",
  },
  {
    id: 'admin',
    role: 'ILCR_ADMIN',
    why: 'the @smoke shell scenario, whose nav assertion covers the adminOnly "Generate Reports" group',
  },
];

test('mock-user preflight: every seeded id still names the role the suite means', async () => {
  const source = fs.readFileSync(MOCK_USERS_TS, 'utf8');

  // Assert the INPUT before the property: a moved/renamed file would otherwise make every regex
  // below miss and the whole check pass vacuously.
  expect(
    source,
    `${MOCK_USERS_TS} no longer declares MOCK_USERS — this preflight is reading the wrong file`,
  ).toContain('MOCK_USERS');

  // `ILCR_ROLES` is the id -> FAM role name map the entries reference (roles: [ILCR_ROLES.admin]).
  const roleNames = new Map<string, string>();
  for (const [, key, value] of source.matchAll(/^\s*(\w+):\s*'(ILCR_\w+)',/gm)) {
    roleNames.set(key, value);
  }
  expect(
    [...roleNames.keys()].sort(),
    'ILCR_ROLES no longer maps the two role keys this preflight resolves entries through',
  ).toEqual(['admin', 'submitter']);

  const missing: string[] = [];
  for (const expected of EXPECTED) {
    // The entry's own `roles:` line, taken from the object that declares `id: '<id>'`.
    const entry = source.match(
      new RegExp(`id:\\s*'${expected.id}'[\\s\\S]*?roles:\\s*\\[([^\\]]*)\\]`),
    );
    const roleKey = entry?.[1].match(/ILCR_ROLES\.(\w+)/)?.[1];
    const resolved = roleKey ? roleNames.get(roleKey) : undefined;
    if (resolved !== expected.role) {
      missing.push(
        `'${expected.id}' should resolve to ${expected.role} (${expected.why}) but reads `
          + `${resolved ?? 'NOTHING — no such id in MOCK_USERS'}`,
      );
    }
  }

  expect(
    missing,
    'seedMockUser() names ids that mockUsers.ts no longer declares as the suite expects. Because '
      + "findMockUser falls back to `?? MOCK_USERS[0]`, the suite would NOT fail here — it would "
      + 'quietly act as whichever user is listed first, exactly the way it ran as an administrator '
      + `for a month. Fix the id/role in pages/common/mockUser.ts or in the app:\n${missing.join('\n')}`,
  ).toEqual([]);
});

test('mock-user preflight: the seeded id still reaches the backend as a role', async () => {
  const source = fs.readFileSync(API_SERVICE_TS, 'utf8');

  // The two halves of the lever: the interceptor reads the SAME storage key the suite writes, and
  // sends it as the header MockPrincipalFilter reads. Either one moving makes the seed inert — the
  // backend then falls back to `ilcr.security.mock-role`, which is a DIFFERENT identity chosen by a
  // property nobody editing this suite would think to look at.
  expect(
    source,
    `${API_SERVICE_TS} no longer reads MOCK_USER_STORAGE_KEY ('${MOCK_USER_STORAGE_KEY}'), so seeding `
      + 'it no longer chooses the acting role — find how the SPA now picks the mock identity and '
      + 'update pages/common/mockUser.ts to drive that instead',
  ).toContain('MOCK_USER_STORAGE_KEY');
  expect(
    source,
    `${API_SERVICE_TS} no longer sends the ${MOCK_GROUPS_HEADER} header, so the browser no longer `
      + "drives the backend principal at all: every request falls back to the backend's mock-role "
      + 'property, AND `grantAdminOnMillList` — which rewrites that header to reach the Home mill '
      + 'list — silently stops having any effect, leaving the dropdown empty',
  ).toContain(MOCK_GROUPS_HEADER);
});
