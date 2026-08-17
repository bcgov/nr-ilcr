import { test, expect } from '@playwright/test';
import {
  A11Y_ANCHOR,
  BLANK_COST_ANCHOR,
  BOTTOM_BAR_ANCHOR,
  BLANK_SALES_ANCHOR,
  CANCEL_DELETE_ANCHOR,
  CHECK_MET_ANCHOR,
  CHECK_MISSING_ANCHOR,
  DELETE_ANCHOR,
  DELETE_UNAVAILABLE_ANCHOR,
  GUARD_ANCHORS,
  HAPPY_PATH_ANCHOR,
  HAPPY_PATH_AT_REST,
  PERSIST_ANCHOR,
  READ_ONLY_ANCHORS,
  RETRY_ANCHOR,
  SAVED_INCOMPLETE_ANCHOR,
  SAVE_ERROR_ANCHOR,
  type Sch2Anchor,
  UPDATE_ANCHOR,
  VALIDATION_ANCHOR,
  scheduleUrl,
} from '../fixtures/sch2/schedule2-test-data';

/**
 * Schedule 2 preflight — asserts every pinned anchor still resolves BEFORE the suite runs, so a
 * re-extracted / drifted DB fails fast with one actionable "re-ground the fixtures" message instead of
 * a dozen confusing mid-suite timeouts.
 *
 * This is a READ-ONLY check: it never writes, so it is safe to run against the seeded DB every time.
 */

const HINT = 'Re-ground fixtures/sch2/schedule2-test-data.ts.';

/** Anchors that must be an EDITABLE Draft (every mutating + validate-only + a11y key). */
const EDITABLE_DRAFT_ANCHORS: { name: string; anchor: Sch2Anchor }[] = [
  { name: 'happy-path (S01)', anchor: HAPPY_PATH_ANCHOR },
  { name: 'update (S02)', anchor: UPDATE_ANCHOR },
  { name: 'blank-cost (S03)', anchor: BLANK_COST_ANCHOR },
  { name: 'blank-sales (S04)', anchor: BLANK_SALES_ANCHOR },
  { name: 'delete (S05)', anchor: DELETE_ANCHOR },
  { name: 'delete-unavailable (S06)', anchor: DELETE_UNAVAILABLE_ANCHOR },
  { name: 'check-met (S07)', anchor: CHECK_MET_ANCHOR },
  { name: 'check-missing (S08)', anchor: CHECK_MISSING_ANCHOR },
  { name: 'save-error (S12)', anchor: SAVE_ERROR_ANCHOR },
  { name: 'retry (S12 recovery)', anchor: RETRY_ANCHOR },
  { name: 'validation (S13-S16)', anchor: VALIDATION_ANCHOR },
  { name: 'persist', anchor: PERSIST_ANCHOR },
  { name: 'bottom-bar', anchor: BOTTOM_BAR_ANCHOR },
  { name: 'cancel-delete', anchor: CANCEL_DELETE_ANCHOR },
  { name: 'saved-incomplete', anchor: SAVED_INCOMPLETE_ANCHOR },
  { name: 'a11y', anchor: A11Y_ANCHOR },
];

for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
  test(`preflight: Schedule 2 anchor ${name} resolves (editable Draft)`, async ({ request }) => {
    const url = scheduleUrl(anchor.key.millId, anchor.key.year);
    const res = await request.get(url);
    await expect(res, `Schedule 2 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. ${HINT}`).toBeOK();
    const doc = (await res.json()) as { trackStatus: string | null; editable: boolean };
    expect(
      doc.trackStatus,
      `Schedule 2 anchor "${name}" 1-10 track must be Draft ("D"). ${HINT}`,
    ).toBe('D');
    expect(
      doc.editable,
      `Schedule 2 anchor "${name}" must be editable. ${HINT}`,
    ).toBe(true);
  });
}

/**
 * Every editable anchor must hold NO SAVED Schedule 2 at rest (revisionCount absent).
 *
 * WHY THIS IS ASSERTED RATHER THAN ASSUMED: the suite's "the document opens empty", derived-figure and
 * delete-returns-to-empty assertions are only true of an unsaved anchor. One escaped save (a killed run,
 * a partial cleanup, a manual poke at the seeded DB) turns those into confusing reds that point at the
 * wrong thing. Listing the offenders here makes the failure self-diagnosing, and cleanup's own read-back
 * is the per-scenario backstop.
 */
test('preflight: Schedule 2 editable anchors hold no saved schedule at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(res, `Schedule 2 anchor "${name}" GET -> HTTP ${res.status()}`).toBeOK();
    const doc = (await res.json()) as { revisionCount?: number | null };
    if ((doc.revisionCount ?? null) !== null) {
      dirty.push(
        `${name} (${anchor.key.millId}/${anchor.key.year}) revisionCount=${doc.revisionCount}`,
      );
    }
  }
  expect(
    dirty,
    `Schedule 2 anchors already hold a saved schedule: ${dirty.join('; ')}. DELETE /api/v1/schedule2 for each, then re-run.`,
  ).toEqual([]);
});

/** Every mutating anchor must be a DISTINCT (mill, year) — the suite runs fullyParallel. */
test('preflight: Schedule 2 anchors are all distinct', async () => {
  const keys = EDITABLE_DRAFT_ANCHORS.map(({ anchor }) => `${anchor.key.millId}/${anchor.key.year}`);
  expect(
    new Set(keys).size,
    `Schedule 2 anchors must be distinct so parallel scenarios never write to the same schedule: ${keys.join(', ')}`,
  ).toBe(keys.length);
});

/**
 * The happy-path anchor's CARRIED figures must still match what HAPPY_PATH_AT_REST pins.
 *
 * These come from Schedule 1 and Schedule 3 for the same mill/year, so they are outside Schedule 2's own
 * control — a re-extract, or a stray write by another suite to this mill/year, would silently move them
 * and the derived-figure assertions would fail deep inside a browser test. Checking them here names the
 * cause instead.
 */
test('preflight: Schedule 2 happy-path anchor still carries its pinned Schedule 1/3 figures', async ({
  request,
}) => {
  const res = await request.get(
    scheduleUrl(HAPPY_PATH_ANCHOR.key.millId, HAPPY_PATH_ANCHOR.key.year),
  );
  await expect(res, `happy-path anchor GET -> HTTP ${res.status()}. ${HINT}`).toBeOK();
  const doc = (await res.json()) as {
    purchasedLogCost: { volume?: number | null };
    purchasedWoodOverhead: { volume?: number | null; cost?: number | null };
    totalCompanyLogging: { volume?: number | null; cost?: number | null };
  };
  const carried = {
    purchasedLogCostVolume: doc.purchasedLogCost.volume ?? null,
    overheadVolume: doc.purchasedWoodOverhead.volume ?? null,
    overheadCost: doc.purchasedWoodOverhead.cost ?? null,
    totalCompanyVolume: doc.totalCompanyLogging.volume ?? null,
    totalCompanyCost: doc.totalCompanyLogging.cost ?? null,
  };
  expect(
    carried,
    `the happy-path anchor's carried Schedule 1/3 figures moved — HAPPY_PATH_AT_REST / HAPPY_PATH_DISPLAY no longer describe this anchor. ${HINT}`,
  ).toEqual({
    purchasedLogCostVolume: 10,
    overheadVolume: 10,
    overheadCost: 0,
    totalCompanyVolume: 10,
    totalCompanyCost: 10,
  });
});

/** The read-only anchors must still be non-Draft, and each must be the track code it is pinned as. */
for (const [name, anchor] of Object.entries(READ_ONLY_ANCHORS)) {
  test(`preflight: Schedule 2 read-only anchor ${name} is still non-Draft (${anchor.trackStatus})`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(res, `read-only anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. ${HINT}`).toBeOK();
    const doc = (await res.json()) as {
      trackStatus: string | null;
      editable: boolean;
      revisionCount?: number | null;
      purchasedLogCost: { volume?: number | null; cost?: number | null };
      lessLogSales: { volume?: number | null; cost?: number | null };
    };
    expect(
      doc.trackStatus,
      `read-only anchor "${name}" must still be track "${anchor.trackStatus}". ${HINT}`,
    ).toBe(anchor.trackStatus);
    expect(
      doc.editable,
      `read-only anchor "${name}" must NOT be editable. ${HINT}`,
    ).toBe(false);
    // A read-only render scenario asserts stored values, so there must BE stored values.
    expect(
      doc.revisionCount ?? null,
      `read-only anchor "${name}" must hold a saved schedule to render read-only values. ${HINT}`,
    ).not.toBeNull();
    // …and they must be the SAME values the `@S11` outline asserts verbatim on screen. These belong to
    // another schedule's data set and are never written by this suite, so a re-extract or a hand-edit
    // would otherwise surface as an opaque table mismatch deep inside a browser test. Same reasoning as
    // the happy-path anchor's carried-figure check above.
    expect(
      {
        item25Volume: doc.purchasedLogCost.volume ?? null,
        item25Cost: doc.purchasedLogCost.cost ?? null,
        item26Volume: doc.lessLogSales.volume ?? null,
        item26Cost: doc.lessLogSales.cost ?? null,
      },
      `read-only anchor "${name}" stored figures moved — the @S11 outline's Examples row no longer describes it. ${HINT}`,
    ).toEqual(anchor.stored);
  });
}

/** The context-guard anchors must still produce their pinned HTTP status and verbatim detail. */
for (const [name, guard] of Object.entries(GUARD_ANCHORS)) {
  test(`preflight: Schedule 2 guard anchor ${name} still produces HTTP ${guard.expectHttp}`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(guard.key.millId, guard.key.year));
    expect(
      res.status(),
      `guard anchor "${name}" (${guard.key.millId}/${guard.key.year}) must still return HTTP ${guard.expectHttp}. ${HINT}`,
    ).toBe(guard.expectHttp);
    const body = (await res.json()) as { detail?: string };
    expect(
      body.detail,
      `guard anchor "${name}" detail text drifted from the pinned message. ${HINT}`,
    ).toBe(guard.detail);
  });
}

/**
 * The pinned at-rest display table must describe the same set of rows the page renders, in the same
 * order — a guard against HAPPY_PATH_AT_REST / HAPPY_PATH_DISPLAY drifting apart from each other.
 */
test('preflight: the pinned Schedule 2 display tables agree on their rows', async () => {
  const atRest = Object.keys(HAPPY_PATH_AT_REST);
  expect(atRest.length, 'HAPPY_PATH_AT_REST must cover all 7 legacy rows').toBe(7);
  // Imported lazily so the fixture stays the single source of truth for the row order.
  const { HAPPY_PATH_DISPLAY } = await import('../fixtures/sch2/schedule2-test-data');
  expect(
    Object.keys(HAPPY_PATH_DISPLAY),
    'HAPPY_PATH_DISPLAY and HAPPY_PATH_AT_REST must describe the same rows in the same order',
  ).toEqual(atRest);
});
