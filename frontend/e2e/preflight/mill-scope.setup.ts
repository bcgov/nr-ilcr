import { test, expect, type APIRequestContext } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { collectAnchorKeys } from './anchor-keys'
import { MOCK_GROUPS_HEADER } from '../pages/common/mockUser'

/**
 * Suite PREFLIGHT — the Home mill dropdown actually offers this suite's identity the mills its
 * scenarios select. Part of the `setup` project the `chromium` project depends on. API-only, so it
 * needs no browser and no frontend.
 *
 * WHY THIS EXISTS — it guards a dependency the suite did not used to have. The mill list is
 * IDENTITY-scoped (Story 5.5): `MillContextService.listMills` looks the caller's directory GUID up
 * in `ILCR_MILL_USER_XREF` and fail-closes a submitter with no association to an EMPTY list. Until
 * bcgov/nr-ilcr#385 the suite side-stepped that by borrowing `ILCR_ADMIN` for this one request
 * (`grantAdminOnMillList`, now deleted), which worked against ANY database because an admin is
 * scoped to nothing. Now that the suite runs as its declared submitter, a green run DEPENDS on
 * association rows existing in whichever database it is pointed at:
 *
 *   * the CI / Flyway database — the final INSERT in `db-e2e/R__80_e2e_anchor_seed.sql`;
 *   * an extract-backed database — `real-test-data-patches/common/mock-submitter-associations.sql`,
 *     applied by `./scripts/apply-patches.sh`.
 *
 * Forget either and the dropdown is EMPTY. Without this file the symptom is ~240 browser scenarios
 * failing at "select the mill" with a locator timeout, which reads as an app regression — the exact
 * failure mode that cost a day on Story 16.1's first CI run, and the reason `defects.md` VER-8
 * exists (a rule that lived only in prose). One request, checked before any browser starts, turns
 * that into one sentence naming the command to run.
 *
 * KNOWN LIMIT, stated so this is not over-trusted: the fixtures associate EVERY mill deliberately,
 * so the submitter and admin lists contain the same mills (see `sec` GAP-5). This preflight forces
 * the submitter role rather than relying on the backend's configurable default; the unit tests pin
 * the controller and all three `listMills` branches directly.
 */

const HERE = path.dirname(fileURLToPath(import.meta.url))
const FIXTURES_DIR = path.join(HERE, '../fixtures')

/** `GET /api/v1/mills` — one entry per mill the CALLER may select on Home. */
type MillSummary = {
  millId: number
  millNumber: string
  millName: string
  millStatusCode: string
}

const FIX =
  'FIX: against an extract-backed database run `./scripts/apply-patches.sh` from `frontend/e2e` ' +
  '(it is idempotent); against the CI/Flyway database re-run ' +
  '`mvn -P e2e-db flyway:clean flyway:migrate`, which applies the association INSERT at the end ' +
  'of `db-e2e/R__80_e2e_anchor_seed.sql`. If the mills are present but still not offered, the ' +
  'identity is the problem rather than the data: check `ilcr.security.mock-user-guid` names a ' +
  'GUID that database associates (see `MockPrincipalFilter`).'

async function millsOffered(request: APIRequestContext): Promise<MillSummary[]> {
  const res = await request.get('/api/v1/mills', {
    headers: { [MOCK_GROUPS_HEADER]: 'ILCR_SUBMITTER' },
  })
  expect(
    res.ok(),
    `[preflight] GET /api/v1/mills returned HTTP ${res.status()}. The backend is up (other ` +
      `preflights reached it), so this is the endpoint itself, not the environment.`,
  ).toBeTruthy()

  // Validate the SHAPE before trusting it. A blind cast would turn contract drift into a confusing
  // downstream failure: `mills.length` on an object is `undefined`, which fails the emptiness check
  // below with "expected undefined to be greater than 0" and sends the reader hunting for missing
  // data that is not missing.
  const body: unknown = await res.json()
  expect(
    Array.isArray(body),
    `[preflight] GET /api/v1/mills did not return a JSON array (got ${typeof body}). The endpoint's ` +
      `contract changed; this preflight and the Home dropdown both read it as MillSummary[].`,
  ).toBeTruthy()
  const mills = body as MillSummary[]
  const malformed = mills.filter((m) => !Number.isInteger(m?.millId))
  expect(
    malformed.length,
    `[preflight] ${malformed.length} entry/entries from GET /api/v1/mills have no integer millId, ` +
      `so the pinned-mill comparison below cannot be trusted. First: ${JSON.stringify(malformed[0])}`,
  ).toBe(0)
  return mills
}

/**
 * Every place the mock directory GUID is written down. It is duplicated by necessity — a Spring
 * default, a Compose default, and three SQL seeds cannot import a shared constant — so the only
 * protection against drift is asserting they agree. Change it in one place and this fails naming
 * both values, instead of a healthy backend serving an empty dropdown.
 */
const GUID_SOURCES = [
  {
    what: 'the Spring property default',
    file: 'backend/src/main/resources/application.yml',
    pattern: /mock-user-guid:\s*\$\{ILCR_SECURITY_MOCK_USER_GUID:([A-Z0-9]+)\}/,
  },
  {
    what: 'the @Value fallback',
    file: 'backend/src/main/java/ca/bc/gov/nrs/ilcr/configuration/SecurityConfiguration.java',
    pattern: /ilcr\.security\.mock-user-guid:([A-Z0-9]+)\}/,
  },
  {
    what: 'the Compose default',
    file: 'docker-compose.yml',
    pattern: /ILCR_SECURITY_MOCK_USER_GUID:\s*\$\{ILCR_SECURITY_MOCK_USER_GUID:-([A-Z0-9]+)\}/,
  },
  {
    what: "the CI seed's ILCR_USER row",
    file: 'backend/src/test/resources/db/R__70_test_scope_canonical_submitter.sql',
    pattern: /'([A-Z0-9]{32})'/,
  },
  {
    what: "the CI seed's mill associations",
    file: 'backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql',
    pattern: /'([A-Z0-9]{32})'/,
  },
  {
    what: "the extract patch's associations",
    file: 'frontend/e2e/real-test-data-patches/common/mock-submitter-associations.sql',
    pattern: /c_guid\s+CONSTANT\s+VARCHAR2\(32\)\s*:=\s*'([A-Z0-9]+)'/,
  },
]

test('preflight: the mock directory GUID is the same in every place it is written', async () => {
  const repoRoot = path.join(HERE, '../../..')
  const found = GUID_SOURCES.map((src) => {
    const source = fs.readFileSync(path.join(repoRoot, src.file), 'utf8')
    const guid = source.match(src.pattern)?.[1]
    // Assert the MATCH before comparing values, or a moved key makes every regex miss and the
    // whole check pass vacuously on an empty set — the trap `mock-user.setup.ts` also guards.
    expect(
      guid,
      `[preflight] Could not find the mock GUID in ${src.file} (${src.what}). Either it moved or ` +
        `its shape changed; this check is reading the wrong thing and would otherwise pass ` +
        `vacuously. Update GUID_SOURCES in this file.`,
    ).toBeTruthy()
    return { ...src, guid }
  })

  const distinct = [...new Set(found.map((f) => f.guid))]
  expect(
    distinct.length,
    `[preflight] The mock directory GUID DISAGREES across the places it is written, so the backend ` +
      `can present an identity that its database never associates — a healthy app with an empty ` +
      `Home dropdown. Found:\n` +
      found.map((f) => `  ${f.guid}  ${f.what} (${f.file})`).join('\n'),
  ).toBe(1)
})

test("preflight: the suite's identity is offered at least one mill", async ({ request }) => {
  const mills = await millsOffered(request)
  expect(
    mills.length,
    '[preflight] The Home mill dropdown is EMPTY for the identity this suite runs as, so NO ' +
      'scenario can establish a working context and every browser test would fail at "select ' +
      'the mill". The likely cause is missing association DATA, but a mismatched mock GUID/role ' +
      `or a mill-list regression can produce the same symptom. ${FIX}`,
  ).toBeGreaterThan(0)
})

test('preflight: every mill the fixtures pin is offered to that identity', async ({ request }) => {
  const mills = await millsOffered(request)
  const offered = new Set(mills.map((m) => m.millId))

  // Anchors are declared in three shapes and two of them defeat a line-based search, so this reuses
  // the shared scanner rather than re-deriving the patterns (`defects.md` VER-8 / the
  // real-test-data-patches README both record what under-scanning here has already cost).
  const pinned = new Set(
    [...collectAnchorKeys(FIXTURES_DIR).keys()].map((key) => Number(key.split('/')[0])),
  )

  const missing = [...pinned].filter((millId) => !offered.has(millId)).sort((a, b) => a - b)

  expect(
    missing,
    `[preflight] ${missing.length} mill(s) pinned by the fixtures are NOT offered to the identity ` +
      `this suite runs as: ${missing.join(', ')}. Scenarios on those mills would fail at "select ` +
      `the mill" with a locator timeout that reads as an app defect. Either the association rows ` +
      `are missing for these mills, or the mills themselves are absent from this database — ` +
      `\`ci-seed-parity.setup.ts\` distinguishes the two (it checks the mills exist at all). ` +
      `${FIX}`,
  ).toEqual([])
})
