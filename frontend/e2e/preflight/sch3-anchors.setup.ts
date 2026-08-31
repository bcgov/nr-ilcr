import { test, expect, type APIRequestContext } from '@playwright/test';
import {
  ANCHORS,
  MUTATING_ANCHOR_KEYS,
  RENDER_STATE_ANCHORS,
  SEEDED_BASE_LINES,
  SEEDED_CROWN_TIMBER_VOLUME,
  SEEDED_OTHER_ACCEPTABLE,
  SEEDED_INCOMPLETE,
  SEEDED_POP_TIMBER_VOLUME,
  SEEDED_UNACCEPTABLE,
  SEEDED_WAGES_VIOLATION,
  otherAcceptableUrl,
  schedule1Url,
  scheduleUrl,
  unacceptableUrl,
} from '../fixtures/sch3/schedule3-test-data';

/**
 * PREFLIGHT — asserts every pinned Schedule 3 anchor still resolves BEFORE a browser opens, so a stale
 * or re-extracted DB fails fast with one clear "re-ground the fixtures" message rather than a dozen
 * confusing mid-suite failures.
 *
 * Schedule 3 needs this more than most domains: 14 of its anchors are created by
 * `real-test-data-patches/sch3/draft-anchors.sql` (Schedule 3 has no create path, so the summary legacy's
 * first Save would have written has to be seeded — that file explains why). If the patch was never
 * applied to a fresh container, EVERY sch3 scenario would otherwise fail on a 404 that looks like an app
 * bug. The first check below turns that into one actionable message.
 *
 * Nothing here writes.
 */

type Doc = {
  trackStatus: string | null;
  editable: boolean;
  revisionCount?: number | null;
  overrideHarvestTotalPop?: string | null;
  comments?: string | null;
  lineItems: { costItemCode: number; harvest?: number | null; pop?: number | null }[];
  popTimber: { volume?: number | null };
  crownTimber: { volume?: number | null };
  otherAcceptableCount: number;
  unacceptableCount: number;
};

const APPLY_PATCH_HINT =
  'Apply the seed patch first: cd frontend/e2e && ./scripts/apply-patches.sh ' +
  '(real-test-data-patches/sch3/draft-anchors.sql creates the Schedule 3 summaries this suite pins — ' +
  'the app cannot create them, see that file and defects.md DIV-1).';

async function getDoc(
  request: APIRequestContext,
  key: { millId: number; year: number },
  label: string,
): Promise<Doc> {
  const res = await request.get(scheduleUrl(key.millId, key.year));
  expect(
    res.status(),
    `${label} (${key.millId}/${key.year}) answered HTTP ${res.status()} on GET schedule3. ${APPLY_PATCH_HINT}`,
  ).toBe(200);
  return (await res.json()) as Doc;
}

/** The stored (harvest, pop) pairs a document holds, for a compact one-line comparison. */
const storedHarvests = (doc: Doc): Record<number, number | null> =>
  Object.fromEntries(doc.lineItems.map((li) => [li.costItemCode, li.harvest ?? null]));

test('every Schedule 3 anchor is an editable Draft that opens', async ({ request }) => {
  const broken: string[] = [];
  for (const [key, anchor] of Object.entries(ANCHORS)) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    if (res.status() !== 200) {
      broken.push(`${key} (${anchor.key.millId}/${anchor.key.year}) -> HTTP ${res.status()}`);
      continue;
    }
    const doc = (await res.json()) as Doc;
    if (doc.trackStatus !== 'D' || !doc.editable) {
      broken.push(
        `${key} (${anchor.key.millId}/${anchor.key.year}) -> track ${doc.trackStatus}, editable ${doc.editable}`,
      );
    }
  }
  expect(
    broken,
    `Schedule 3 anchors no longer resolve as editable Drafts: ${broken.join('; ')}. ${APPLY_PATCH_HINT}`,
  ).toEqual([]);
});

test('every mutating Schedule 3 anchor is EMPTY at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const key of MUTATING_ANCHOR_KEYS) {
    const anchor = ANCHORS[key];
    const doc = await getDoc(request, anchor.key, `mutating anchor "${key}"`);
    const findings: string[] = [];
    for (const li of doc.lineItems) {
      if ((li.harvest ?? null) !== null) findings.push(`line ${li.costItemCode} harvest=${li.harvest}`);
      // Scaling (33) PO&P is DERIVED from the volumes, so it can only be non-null if a volume is —
      // which the volume checks below already catch. Every other PO&P is a stored value.
      if ((li.pop ?? null) !== null && li.costItemCode !== 33) {
        findings.push(`line ${li.costItemCode} pop=${li.pop}`);
      }
    }
    if ((doc.popTimber.volume ?? null) !== null) findings.push(`popTimber=${doc.popTimber.volume}`);
    if ((doc.crownTimber.volume ?? null) !== null) findings.push(`crownTimber=${doc.crownTimber.volume}`);
    if (doc.comments) findings.push('comments');
    if (doc.overrideHarvestTotalPop === 'Y') findings.push('override=Y');
    if (doc.otherAcceptableCount !== 0) findings.push(`otherAcceptableCount=${doc.otherAcceptableCount}`);
    if (doc.unacceptableCount !== 0) findings.push(`unacceptableCount=${doc.unacceptableCount}`);
    if (findings.length > 0) {
      dirty.push(`${key} (${anchor.key.millId}/${anchor.key.year}): ${findings.join(', ')}`);
    }
  }
  expect(
    dirty,
    'these mutating Schedule 3 anchors are NOT empty at rest — a previous run left residue, or the ' +
      `fixture is pinned to the wrong (mill, year): ${dirty.join(' | ')}`,
  ).toEqual([]);
});

test('every mutating Schedule 3 anchor is a distinct (mill, year)', async () => {
  const keys = MUTATING_ANCHOR_KEYS.map((k) => `${ANCHORS[k].key.millId}/${ANCHORS[k].key.year}`);
  expect(
    new Set(keys).size,
    `two mutating Schedule 3 scenarios share an anchor, which is not parallel-safe: ${keys.join(', ')}`,
  ).toBe(keys.length);
});

test('the BR-09 crown anchors have the Schedule 1 state their outcome depends on', async ({
  request,
}) => {
  // WRN-001 needs Schedule 1 "opened" (a category-1 summary); WRN-002 needs it absent. Both are pinned
  // states, not incidental ones, so a drift here would silently invert the two scenarios.
  // ASSERTED ON SAVED-NESS, NOT ON A 404 (re-grounded 2026-08-26, defect #296). An unsaved Schedule 1
  // now answers 200 with an empty EDITABLE document, so the old `=== 404` proxy for "never opened"
  // inverted the moment the fix landed. `revisionCount` is the token the server issues only once the
  // summary exists — the same signal the app's own `utils/schedule.ts isScheduleSaved` uses.
  const saved = async (key: { millId: number; year: number }): Promise<boolean> => {
    const res = await request.get(schedule1Url(key.millId, key.year));
    if (res.status() !== 200) {
      return false;
    }
    const doc = (await res.json()) as { revisionCount?: number | null };
    return doc.revisionCount != null;
  };

  const applied = ANCHORS['crown-applied'];
  expect(
    await saved(applied.key),
    `the crown-applied anchor (${applied.key.millId}/${applied.key.year}) needs a SAVED Schedule 1 for ` +
      `WRN-001. ${APPLY_PATCH_HINT}`,
  ).toBe(true);

  const notOpened = ANCHORS['crown-not-opened'];
  expect(
    await saved(notOpened.key),
    `the crown-not-opened anchor (${notOpened.key.millId}/${notOpened.key.year}) must have NO SAVED ` +
      'Schedule 1 for WRN-002',
  ).toBe(false);
});

test('the seeded check-status anchors still carry their pinned amounts', async ({ request }) => {
  const baseHarvests = Object.fromEntries(SEEDED_BASE_LINES.map((l) => [l.code, l.harvest]));

  for (const key of [
    'check-harvest-pop',
    'check-override',
    'check-oa-pop',
    'a11y',
    'check-subpage-missing',
  ] as const) {
    const anchor = ANCHORS[key];
    const doc = await getDoc(request, anchor.key, `seeded anchor "${key}"`);
    const harvests = storedHarvests(doc);
    for (const [code, expected] of Object.entries(baseHarvests)) {
      expect(
        harvests[Number(code)],
        `seeded anchor "${key}" (${anchor.key.millId}/${anchor.key.year}) line ${code} Harvest drifted`,
      ).toBe(expected);
    }
    expect(
      [doc.popTimber.volume ?? null, doc.crownTimber.volume ?? null],
      `seeded anchor "${key}" timber volumes drifted`,
    ).toEqual([SEEDED_POP_TIMBER_VOLUME, SEEDED_CROWN_TIMBER_VOLUME]);
  }

  // The BR-03 lever: Wages/Salaries violates Harvest >= PO&P on two anchors and satisfies it on the
  // other two. Inverting either would silently invert the S11/S12 outcomes.
  const wages = async (key: string): Promise<[number | null, number | null]> => {
    const doc = await getDoc(request, ANCHORS[key].key, `seeded anchor "${key}"`);
    const line = doc.lineItems.find((li) => li.costItemCode === 30);
    return [line?.harvest ?? null, line?.pop ?? null];
  };
  expect(await wages('check-harvest-pop'), 'check-harvest-pop must VIOLATE Harvest >= PO&P').toEqual([
    SEEDED_WAGES_VIOLATION.harvest,
    SEEDED_WAGES_VIOLATION.pop,
  ]);
  expect(await wages('check-override'), 'check-override must VIOLATE Harvest >= PO&P').toEqual([
    SEEDED_WAGES_VIOLATION.harvest,
    SEEDED_WAGES_VIOLATION.pop,
  ]);
  const oaPopWages = await wages('check-oa-pop');
  expect(
    oaPopWages[0]! >= oaPopWages[1]!,
    'check-oa-pop must SATISFY Harvest >= PO&P on every fixed line, so its only check error is the ' +
      'other-acceptable one',
  ).toBe(true);

  // The Override flag is what the S12 suppression turns on.
  const override = async (key: string): Promise<string | null> =>
    (await getDoc(request, ANCHORS[key].key, `seeded anchor "${key}"`)).overrideHarvestTotalPop ?? null;
  expect(await override('check-override'), 'the check-override anchor must carry Override "Y"').toBe('Y');
  expect(await override('check-harvest-pop'), 'the check-harvest-pop anchor must carry Override "N"').toBe(
    'N',
  );
  expect(await override('check-oa-pop'), 'the check-oa-pop anchor must carry Override "N"').toBe('N');
});

test('the seeded sub-page rows are present where the specs expect them', async ({ request }) => {
  for (const key of ['check-override', 'check-oa-pop', 'a11y'] as const) {
    const anchor = ANCHORS[key];
    const res = await request.get(otherAcceptableUrl(anchor.key.millId, anchor.key.year));
    expect(res.status(), `GET other-acceptable for "${key}"`).toBe(200);
    const doc = (await res.json()) as {
      rows?: { description?: string | null; total?: number | null; pop?: number | null }[];
    };
    expect(
      (doc.rows ?? []).map((r) => [r.description, r.total ?? null, r.pop ?? null]),
      `the seeded other-acceptable group on "${key}" drifted. ${APPLY_PATCH_HINT}`,
    ).toEqual([
      [SEEDED_OTHER_ACCEPTABLE.description, SEEDED_OTHER_ACCEPTABLE.total, SEEDED_OTHER_ACCEPTABLE.pop],
    ]);
  }

  const a11y = ANCHORS['a11y'];
  const res = await request.get(unacceptableUrl(a11y.key.millId, a11y.key.year));
  expect(res.status(), 'GET included-unacceptable for "a11y"').toBe(200);
  const doc = (await res.json()) as { rows?: { description?: string | null; total?: number | null }[] };
  expect(
    (doc.rows ?? []).map((r) => [r.description, r.total ?? null]),
    `the seeded included-unacceptable row on the a11y anchor drifted. ${APPLY_PATCH_HINT}`,
  ).toEqual([[SEEDED_UNACCEPTABLE.description, SEEDED_UNACCEPTABLE.total]]);

  // The deliberately INCOMPLETE rows the BR-11 sub-page checks depend on. Their emptiness IS the
  // fixture: if a future patch "tidied" them, the check-status scenario would silently stop proving
  // anything, so each missing field is asserted as missing.
  const missing = ANCHORS['check-subpage-missing'];
  const oaRes = await request.get(otherAcceptableUrl(missing.key.millId, missing.key.year));
  expect(oaRes.status(), 'GET other-acceptable for "check-subpage-missing"').toBe(200);
  const oaDoc = (await oaRes.json()) as {
    rows?: { description?: string | null; total?: number | null; pop?: number | null }[];
  };
  expect(
    (oaDoc.rows ?? []).map((r) => [r.description ?? null, r.total ?? null, r.pop ?? null]),
    `the INCOMPLETE other-acceptable group on "check-subpage-missing" drifted. ${APPLY_PATCH_HINT}`,
  ).toEqual([
    [
      SEEDED_INCOMPLETE.otherAcceptable.description,
      SEEDED_INCOMPLETE.otherAcceptable.total,
      SEEDED_INCOMPLETE.otherAcceptable.pop,
    ],
  ]);
  const unRes = await request.get(unacceptableUrl(missing.key.millId, missing.key.year));
  expect(unRes.status(), 'GET included-unacceptable for "check-subpage-missing"').toBe(200);
  const unDoc = (await unRes.json()) as { rows?: { description?: string | null; total?: number | null }[] };
  expect(
    (unDoc.rows ?? []).map((r) => [r.description ?? null, r.total ?? null]),
    `the INCOMPLETE included-unacceptable row on "check-subpage-missing" drifted. ${APPLY_PATCH_HINT}`,
  ).toEqual([[SEEDED_INCOMPLETE.unacceptable.description, SEEDED_INCOMPLETE.unacceptable.total]]);
});

test('every Schedule 3 render-state anchor still produces its guard response', async ({ request }) => {
  const drifted: string[] = [];
  for (const [key, anchor] of Object.entries(RENDER_STATE_ANCHORS)) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    if (res.status() !== anchor.expectHttp) {
      drifted.push(
        `${key} (${anchor.key.millId}/${anchor.key.year}) -> HTTP ${res.status()}, expected ${anchor.expectHttp}`,
      );
      continue;
    }
    if (anchor.detail !== undefined) {
      const detail = ((await res.json()) as { detail?: string }).detail ?? '';
      if (detail !== anchor.detail) {
        drifted.push(`${key} detail "${detail}" != "${anchor.detail}"`);
      }
    }
    if (anchor.track !== undefined) {
      const doc = (await res.json()) as Doc;
      if (doc.trackStatus !== anchor.track || doc.editable) {
        drifted.push(`${key} -> track ${doc.trackStatus}, editable ${doc.editable}`);
      }
      // A pinned track ALSO implies a SAVED schedule, and that has to be asserted separately.
      // Added 2026-08-28 after this check passed against a seed where these two anchors had the right
      // report-status row and no category-3 summary at all: since defect #296 an unsaved schedule
      // answers 200, so `expectHttp` and `trackStatus` were both satisfied by a
      // Submitted-and-UNSAVED schedule. It renders read-only exactly as S15 expects, and then its
      // Other Costs link hits the save-first gate (ALT-002) instead of opening the sub-page — so only
      // the sub-page scenario failed, in CI only, four steps away from the cause.
      // `revisionCount` is the server's saved marker (loose `== null`, as `isScheduleSaved` reads it).
      if (doc.revisionCount == null) {
        drifted.push(
          `${key} is track '${anchor.track}' but UNSAVED (no revisionCount) — it needs a category-3 `
            + 'summary, not just a report-status row, or its sub-pages hit the save-first gate',
        );
      }
    }
  }
  expect(
    drifted,
    `Schedule 3 render-state anchors drifted: ${drifted.join('; ')}. Re-discover them (see the ` +
      'ANCHOR DISCOVERY note in fixtures/sch3/schedule3-test-data.ts).',
  ).toEqual([]);
});
