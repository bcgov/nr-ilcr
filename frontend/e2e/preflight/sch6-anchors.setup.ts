import { test, expect } from '@playwright/test';
import {
  ADD_ANCHOR,
  EDITABLE_DRAFT_ANCHORS,
  GUARDS,
  scheduleUrl,
} from '../fixtures/sch6/schedule6-test-data';

/**
 * Schedule 6 preflight — asserts every pinned anchor still resolves BEFORE the suite runs, so a
 * re-extracted / drifted DB fails fast with one actionable "re-ground the fixtures" message instead of
 * a dozen confusing mid-suite timeouts.
 *
 * This is a READ-ONLY check: it never writes, so it is safe to run against the seeded DB every time.
 *
 * EVERY sch6 anchor is a SEEDED one (`real-test-data-patches/sch6/draft-anchors.sql`), because by the
 * time Schedule 6 was authored the grid held NO usable free Draft mill-year for any domain — see that
 * file and the fixture header for the survey. So these checks double as the patch's own applied-ness
 * guard: if the patch was never applied, the 2024 anchors answer 404 here with one clear message rather
 * than failing inside a dozen different scenarios.
 *
 * The anchor tables themselves live in the fixture, so this file cannot drift from the specs' own
 * source of truth.
 */

const HINT = 'Re-ground fixtures/sch6/schedule6-test-data.ts.';

const PATCH_HINT =
  'A 404 here almost always means real-test-data-patches/sch6/draft-anchors.sql was not applied — run '
  + 'frontend/e2e/scripts/apply-patches.sh (from WSL if your Oracle container lives there; there is no '
  + 'sqlplus on the Windows side).';

interface Schedule6Doc {
  trackStatus: string | null;
  editable: boolean;
  roadRecords: { recordId: number; areaType: string | null }[];
  totalVolume: number | null;
  totalCost: number | null;
}

for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
  test(`preflight: Schedule 6 anchor ${name} resolves (editable Draft)`, async ({ request }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(
      res,
      `Schedule 6 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. `
        + `${PATCH_HINT} ${HINT}`,
    ).toBeOK();

    const doc = (await res.json()) as Schedule6Doc;
    expect(doc.trackStatus, `Schedule 6 anchor "${name}" 1-10 track must be Draft ("D"). ${HINT}`).toBe(
      'D',
    );
    expect(doc.editable, `Schedule 6 anchor "${name}" must be editable. ${HINT}`).toBe(true);
  });
}

/**
 * Every editable anchor must hold NO ROAD RECORDS at rest.
 *
 * WHY THIS IS ASSERTED RATHER THAN ASSUMED: S01 opens by asserting the Add panel's fields are blank and
 * the list shows the empty-list placeholder, then closes by asserting its record is the only row and
 * that the totals equal its own figures. One escaped record (a killed run, a partial cleanup, a manual
 * poke at the seeded DB) turns all of those into confusing reds that point at the wrong thing. Listing
 * the offenders here makes the failure self-diagnosing, and the cleanup registry's own read-back is the
 * per-scenario backstop.
 */
test('preflight: Schedule 6 anchors hold no road records at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(res, `Schedule 6 anchor "${name}" GET -> HTTP ${res.status()}`).toBeOK();
    const doc = (await res.json()) as Schedule6Doc;
    if (doc.roadRecords.length > 0) {
      dirty.push(
        `${name} (${anchor.key.millId}/${anchor.key.year}) holds ${doc.roadRecords.length} record(s): `
          + `ids ${doc.roadRecords.map((r) => r.recordId).join(', ')}`,
      );
    }
  }

  expect(
    dirty,
    'these Schedule 6 anchors are NOT empty, so the scenarios that assert an empty list will fail for '
      + `the wrong reason: ${dirty.join('; ')}. Remove the leftovers with `
      + 'DELETE /api/v1/schedule6/records/{recordId}?millId=<m>&year=<y> (no revisionCount — that '
      + 'endpoint carries no revision token), then re-run.',
  ).toEqual([]);
});

/**
 * The totals must be at zero too, not merely the record list empty.
 *
 * These are two different claims: the totals are recomputed server-side from the served records, so a
 * non-zero total on a record-free anchor would mean the document is carrying figures from somewhere the
 * screen does not show — exactly the "openable but not the state the fixture means" class that
 * ci-seed-parity.setup.ts documents as the thing it CANNOT check.
 */
test('preflight: Schedule 6 anchor totals are at rest', async ({ request }) => {
  const res = await request.get(scheduleUrl(ADD_ANCHOR.key.millId, ADD_ANCHOR.key.year));
  await expect(res, `Schedule 6 add anchor GET -> HTTP ${res.status()}`).toBeOK();
  const doc = (await res.json()) as Schedule6Doc;

  expect(doc.totalVolume, `the add anchor's totalVolume must be 0 at rest. ${HINT}`).toBe(0);
  expect(doc.totalCost, `the add anchor's totalCost must be 0 at rest. ${HINT}`).toBe(0);
});

/**
 * No two sch6 scenarios may share a (mill, year).
 *
 * The suite runs `fullyParallel` and every mutating scenario creates a real ROAD_MAINTENANCE_REPORT
 * row, so a shared key lets two scenarios race — and sch5 learned the cost concretely: its S24 green
 * companion first shared S24's own anchor, both seeded a record, the loser 409'd and its cleanup then
 * deleted the winner's row mid-run. Dedication is per SCENARIO, always.
 */
test('preflight: Schedule 6 anchors are all distinct', async () => {
  const seen = new Map<string, string>();
  const shared: string[] = [];
  for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
    const key = `${anchor.key.millId}/${anchor.key.year}`;
    const owner = seen.get(key);
    if (owner) {
      shared.push(`${key} is claimed by both "${owner}" and "${name}"`);
    } else {
      seen.set(key, name);
    }
  }

  expect(
    shared,
    'these Schedule 6 anchors are shared between scenarios, which races under `fullyParallel`: '
      + `${shared.join('; ')}. Mint another cell in reporting year 2024+ instead (see the patch header).`,
  ).toEqual([]);
});

/**
 * The two guard anchors still produce the EXACT status their scenario reads.
 *
 * Asserted here as well as in the scenario because a guard's whole fixture is a FAILURE mode, and
 * failure modes rot quietly: if 1/2017's mill were ever reopened, or if 23050 gained a 2024
 * report-status row, the GET would start answering 200 and the scenario would fail on a missing error
 * banner — which reads as a UI defect rather than as drifted data. This names the cause in the setup
 * project instead.
 *
 * The DETAIL is checked too, not just the status: both scenarios assert the message byte-for-byte, and
 * the API is where that text comes from (AD-8 — the page echoes it, never substitutes its own).
 */
for (const [name, guard] of Object.entries(GUARDS)) {
  test(`preflight: Schedule 6 guard anchor ${name} still answers HTTP ${guard.expectHttp}`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(guard.anchor.key.millId, guard.anchor.key.year));
    expect(
      res.status(),
      `Schedule 6 guard "${name}" (${guard.anchor.key.millId}/${guard.anchor.key.year}) must answer `
        + `HTTP ${guard.expectHttp}. A 200 means the data drifted — a reopened mill, or a report-status `
        + `row that should not exist. ${HINT}`,
    ).toBe(guard.expectHttp);

    const body = (await res.json()) as { detail?: string };
    expect(
      body.detail,
      `Schedule 6 guard "${name}" must carry its verbatim detail — the page echoes the API's text `
        + `rather than substituting its own (AD-8). ${HINT}`,
    ).toBe(guard.detail);
  });
}
