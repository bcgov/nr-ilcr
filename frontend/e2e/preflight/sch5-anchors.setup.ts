import { test, expect } from '@playwright/test';
import {
  ADD_ANCHOR,
  EDITABLE_DRAFT_ANCHORS,
  GUARD_ANCHORS,
  NEW_CAMP_NAME,
  READ_ONLY_ANCHOR,
  VIEW_CAMP_NAME,
  scheduleUrl,
} from '../fixtures/sch5/schedule5-test-data';

/**
 * Schedule 5 preflight — asserts every pinned anchor still resolves BEFORE the suite runs, so a
 * re-extracted / drifted DB fails fast with one actionable "re-ground the fixtures" message instead of
 * a dozen confusing mid-suite timeouts.
 *
 * This is a READ-ONLY check: it never writes, so it is safe to run against the seeded DB every time.
 */

const HINT = 'Re-ground fixtures/sch5/schedule5-test-data.ts.';

/**
 * EVERY sch5 anchor is a SEEDED one (`real-test-data-patches/sch5/draft-anchors.sql`), because the
 * extract had no free Draft mill-year left — see that file and the fixture header. So these checks
 * double as the patch's own applied-ness guard: if the patch was never applied, the 2022/2023 anchors
 * answer 404 here with one clear message rather than failing inside twenty different scenarios.
 *
 * The anchor tables themselves live in the fixture, so this file cannot drift from the specs' own
 * source of truth.
 */

interface Schedule5Doc {
  trackStatus: string | null;
  editable: boolean;
  camps: { campId: number; campName: string }[];
}

for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
  test(`preflight: Schedule 5 anchor ${name} resolves (editable Draft)`, async ({ request }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(
      res,
      `Schedule 5 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. `
        + 'A 404 here almost always means real-test-data-patches/sch5/draft-anchors.sql was not applied '
        + `— run frontend/e2e/scripts/apply-patches.sh. ${HINT}`,
    ).toBeOK();

    const doc = (await res.json()) as Schedule5Doc;
    expect(doc.trackStatus, `Schedule 5 anchor "${name}" 1-10 track must be Draft ("D"). ${HINT}`).toBe(
      'D',
    );
    expect(doc.editable, `Schedule 5 anchor "${name}" must be editable. ${HINT}`).toBe(true);
  });
}

/**
 * Every editable anchor must hold NO CAMPS at rest.
 *
 * WHY THIS IS ASSERTED RATHER THAN ASSUMED: S01 opens by asserting the New Camp panel's fields are
 * blank and closes by asserting its camp is the row in the Existing Camps table. One escaped camp (a
 * killed run, a partial cleanup, a manual poke at the seeded DB) turns both into confusing reds that
 * point at the wrong thing. Listing the offenders here makes the failure self-diagnosing, and the
 * cleanup registry's own read-back is the per-scenario backstop.
 */
test('preflight: Schedule 5 anchors hold no camps at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(res, `Schedule 5 anchor "${name}" GET -> HTTP ${res.status()}`).toBeOK();
    const doc = (await res.json()) as Schedule5Doc;
    for (const camp of doc.camps ?? []) {
      dirty.push(
        `${name} (${anchor.key.millId}/${anchor.key.year}) holds campId=${camp.campId} "${camp.campName}"`,
      );
    }
  }
  expect(
    dirty,
    'Schedule 5 anchors already hold camps: '
      + `${dirty.join('; ')}. DELETE /api/v1/schedule5/camps/{campId}?millId=&year=&revisionCount= for `
      + 'each, then re-run.',
  ).toEqual([]);
});

/**
 * The name S01 creates must not already exist on its anchor.
 *
 * Distinct from the empty-at-rest check above and NOT redundant with it once the suite fans out: BR-02
 * makes camp names unique per mill/year case-insensitively, so a leftover "Cedar Creek Camp" would turn
 * S01's happy-path save into a duplicate-name error — S13's expected outcome, arriving in the wrong
 * scenario. Naming it here is the difference between "clean up this row" and debugging a passing
 * validator.
 */
test('preflight: the S01 camp name is free on its anchor', async ({ request }) => {
  const res = await request.get(scheduleUrl(ADD_ANCHOR.key.millId, ADD_ANCHOR.key.year));
  await expect(res, `Schedule 5 add anchor GET -> HTTP ${res.status()}`).toBeOK();
  const doc = (await res.json()) as Schedule5Doc;

  const clash = (doc.camps ?? []).filter(
    (c) => c.campName?.toUpperCase() === NEW_CAMP_NAME.toUpperCase(),
  );
  expect(
    clash.map((c) => `campId=${c.campId}`),
    `"${NEW_CAMP_NAME}" already exists on ${ADD_ANCHOR.key.millId}/${ADD_ANCHOR.key.year}. BR-02 makes `
      + 'camp names unique per mill/year (case-insensitive), so S01 would fail as a duplicate rather than '
      + 'saving. Delete the leftover camp and re-run.',
  ).toEqual([]);
});

/** Every mutating anchor must be a DISTINCT (mill, year) — the suite runs fullyParallel. */
test('preflight: Schedule 5 anchors are all distinct', async () => {
  const keys = EDITABLE_DRAFT_ANCHORS.map(({ anchor }) => `${anchor.key.millId}/${anchor.key.year}`);
  const duplicates = keys.filter((k, i) => keys.indexOf(k) !== i);
  expect(
    duplicates,
    `Schedule 5 mutating anchors must not share a (mill, year): ${duplicates.join(', ')}. ${HINT}`,
  ).toEqual([]);
});

/**
 * The read-only anchor must still be NON-Draft.
 *
 * S19's whole point is the read-only render, which only exists while `editable` is false. If a future
 * re-extract or a stray write flipped this row back to "D" the scenario would silently start asserting
 * a read-only view of an editable page — passing or failing for reasons unrelated to the slice.
 */
test('preflight: Schedule 5 read-only anchor is still non-Draft', async ({ request }) => {
  const res = await request.get(scheduleUrl(READ_ONLY_ANCHOR.key.millId, READ_ONLY_ANCHOR.key.year));
  await expect(res, `Schedule 5 read-only anchor GET -> HTTP ${res.status()}. ${HINT}`).toBeOK();

  const doc = (await res.json()) as Schedule5Doc & { trackStatus: string | null };
  expect(
    doc.trackStatus,
    `Schedule 5 read-only anchor (${READ_ONLY_ANCHOR.key.millId}/${READ_ONLY_ANCHOR.key.year}) must be `
      + `non-Draft for S19; it is "${doc.trackStatus}". ${HINT}`,
  ).not.toBe('D');
  expect(doc.editable, 'the read-only anchor must not be editable').toBe(false);
});

/**
 * The read-only anchor must hold EXACTLY the one seeded camp.
 *
 * This is the mirror of the "no camps at rest" check above, and it is the one anchor exempt from it:
 * S19 asserts a rendered camp row, its single `View` action and the stored amounts, none of which
 * exist on an empty schedule. Two things can go wrong and they need different messages —
 *   * ZERO camps  -> `view-mode-camp.sql` was never applied (or was torn down and not re-applied);
 *   * TWO or more -> "the row-action column shows a single View button" is no longer unambiguous.
 * Asserting the whole list rather than "at least one" catches both, and pinning the NAME catches a
 * re-extract that renumbered or renamed the row.
 *
 * Deliberately does NOT assert the amounts: those are the scenario's own subject, and duplicating
 * them here would mean two places to update for one change. The row's existence is the fixture; the
 * figures are the test.
 */
test('preflight: Schedule 5 read-only anchor holds exactly the seeded view camp', async ({
  request,
}) => {
  const res = await request.get(scheduleUrl(READ_ONLY_ANCHOR.key.millId, READ_ONLY_ANCHOR.key.year));
  await expect(res, `Schedule 5 read-only anchor GET -> HTTP ${res.status()}. ${HINT}`).toBeOK();

  const doc = (await res.json()) as Schedule5Doc;
  expect(
    (doc.camps ?? []).map((c) => c.campName),
    `Schedule 5's read-only anchor (${READ_ONLY_ANCHOR.key.millId}/${READ_ONLY_ANCHOR.key.year}) must `
      + `hold exactly ["${VIEW_CAMP_NAME}"] for S19. An EMPTY list almost always means `
      + 'real-test-data-patches/sch5/view-mode-camp.sql was not applied — run '
      + `frontend/e2e/scripts/apply-patches.sh. ${HINT}`,
  ).toEqual([VIEW_CAMP_NAME]);
});

/**
 * The two guard anchors must still produce their exact status.
 *
 * These are the cheapest checks here and the most load-bearing: both are anchors whose fixture is a
 * FAILURE response, so nothing about them is self-evident from a passing suite. 25051/2017 must stay a
 * closed mill (409) and 16050/2022 must stay row-less (404) — seeding the latter would delete S18's
 * fixture rather than fix it, which is why it is also registered in DELIBERATELY_ABSENT in
 * ci-seed-parity.setup.ts.
 */
for (const { name, anchor, expectHttp } of GUARD_ANCHORS) {
  test(`preflight: Schedule 5 guard anchor ${name} still produces HTTP ${expectHttp}`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    expect(
      res.status(),
      `Schedule 5 guard anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) must answer `
        + `HTTP ${expectHttp}; it answered ${res.status()}. ${HINT}`,
    ).toBe(expectHttp);
  });
}
