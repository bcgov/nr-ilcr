import { test, expect, type APIRequestContext } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { collectAnchorKeys } from './anchor-keys';

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
 * KNOWN LIMIT, stated so this is not over-trusted: it cannot tell a submitter's list from an
 * admin's, because the fixtures associate EVERY mill deliberately (so the dropdown is identical
 * either way — see `sec` GAP-5). If someone set `ilcr.security.mock-role=ILCR_ADMIN` the checks
 * below would pass while the scoped query was bypassed. `preflight/mock-user.setup.ts` guards the
 * browser half of the identity; the backend half is `MillContextControllerTest` /
 * `MillContextServiceTest`, which pin all three `listMills` branches directly.
 */

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FIXTURES_DIR = path.join(HERE, '../fixtures');

/** `GET /api/v1/mills` — one entry per mill the CALLER may select on Home. */
type MillSummary = {
  millId: number;
  millNumber: string;
  millName: string;
  millStatusCode: string;
};

const FIX =
  'FIX: against an extract-backed database run `./scripts/apply-patches.sh` from `frontend/e2e` '
  + '(it is idempotent); against the CI/Flyway database re-run '
  + '`mvn -P e2e-db flyway:clean flyway:migrate`, which applies the association INSERT at the end '
  + 'of `db-e2e/R__80_e2e_anchor_seed.sql`. If the mills are present but still not offered, the '
  + 'identity is the problem rather than the data: check `ilcr.security.mock-user-guid` names a '
  + 'GUID that database associates (see `MockPrincipalFilter`).';

async function millsOffered(request: APIRequestContext): Promise<MillSummary[]> {
  const res = await request.get('/api/v1/mills');
  expect(
    res.ok(),
    `[preflight] GET /api/v1/mills returned HTTP ${res.status()}. The backend is up (other `
      + `preflights reached it), so this is the endpoint itself, not the environment.`,
  ).toBeTruthy();
  return (await res.json()) as MillSummary[];
}

test("preflight: the suite's identity is offered at least one mill", async ({ request }) => {
  const mills = await millsOffered(request);
  expect(
    mills.length,
    '[preflight] The Home mill dropdown is EMPTY for the identity this suite runs as, so NO '
      + 'scenario can establish a working context and every browser test would fail at "select '
      + `the mill". This is missing DATA, not a broken app. ${FIX}`,
  ).toBeGreaterThan(0);
});

test('preflight: every mill the fixtures pin is offered to that identity', async ({ request }) => {
  const mills = await millsOffered(request);
  const offered = new Set(mills.map((m) => m.millId));

  // Anchors are declared in three shapes and two of them defeat a line-based search, so this reuses
  // the shared scanner rather than re-deriving the patterns (`defects.md` VER-8 / the
  // real-test-data-patches README both record what under-scanning here has already cost).
  const pinned = new Set(
    [...collectAnchorKeys(FIXTURES_DIR).keys()].map((key) => Number(key.split('/')[0])),
  );

  const missing = [...pinned].filter((millId) => !offered.has(millId)).sort((a, b) => a - b);

  expect(
    missing,
    `[preflight] ${missing.length} mill(s) pinned by the fixtures are NOT offered to the identity `
      + `this suite runs as: ${missing.join(', ')}. Scenarios on those mills would fail at "select `
      + `the mill" with a locator timeout that reads as an app defect. Either the association rows `
      + `are missing for these mills, or the mills themselves are absent from this database — `
      + `\`ci-seed-parity.setup.ts\` distinguishes the two (it checks the mills exist at all). `
      + `${FIX}`,
  ).toEqual([]);
});
