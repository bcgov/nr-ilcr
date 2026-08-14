import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { test, expect } from '@playwright/test';
import {
  A11Y_ANCHOR,
  ADD_ANCHOR,
  BEC_POPULATED_FIRST_OPTION,
  BEC_POPULATED_PREFIX,
  BEC_PRIMARY,
  BEC_SECONDARY,
  CANCEL_DELETE_ANCHOR,
  CHECK_MET_ANCHOR,
  CHECK_MISSING_ACTUAL_ANCHOR,
  CHECK_MISSING_PLANNED_ANCHOR,
  CORRECTION_ANCHOR,
  DELETE_ANCHOR,
  ERR,
  ERR_STALE_EDIT,
  FLD,
  GUARD_ANCHORS,
  INLINE_EDIT_ANCHOR,
  MARKER,
  MSG,
  MULTI_ADD_ANCHOR,
  PERSIST_ANCHOR,
  STALE_EDIT_ANCHOR,
  type Sch11Anchor,
  TRACK_INDEPENDENCE_ANCHOR,
  VALIDATION_ANCHOR,
  missingCostMessage,
  scheduleUrl,
} from '../fixtures/sch11/schedule11-test-data';

/**
 * Schedule 11 preflight — asserts every pinned anchor still resolves BEFORE the suite runs, so a
 * re-extracted / drifted DB fails fast with one actionable "re-ground the fixtures" message instead of
 * a dozen confusing mid-suite timeouts.
 *
 * This is a READ-ONLY check: it never writes, so it is safe to run against the seeded DB every time.
 */

/** Anchors that must be an EDITABLE Draft (the mutating + validate-only set). */
const EDITABLE_DRAFT_ANCHORS: { name: string; anchor: Sch11Anchor }[] = [
  { name: 'add (S01)', anchor: ADD_ANCHOR },
  { name: 'multi-add (S02)', anchor: MULTI_ADD_ANCHOR },
  { name: 'inline-edit (S03)', anchor: INLINE_EDIT_ANCHOR },
  { name: 'check-met (S04)', anchor: CHECK_MET_ANCHOR },
  { name: 'check-missing-actual (S05)', anchor: CHECK_MISSING_ACTUAL_ANCHOR },
  { name: 'check-missing-planned (S06)', anchor: CHECK_MISSING_PLANNED_ANCHOR },
  { name: 'delete (S07)', anchor: DELETE_ANCHOR },
  { name: 'cancel-delete (S08)', anchor: CANCEL_DELETE_ANCHOR },
  { name: 'persist (S09)', anchor: PERSIST_ANCHOR },
  { name: 'track-independence (S10)', anchor: TRACK_INDEPENDENCE_ANCHOR },
  { name: 'validate-only (S14-S19)', anchor: VALIDATION_ANCHOR },
  { name: 'correction', anchor: CORRECTION_ANCHOR },
  { name: 'stale-edit (GAP-3)', anchor: STALE_EDIT_ANCHOR },
  { name: 'a11y', anchor: A11Y_ANCHOR },
];

for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
  test(`preflight: Schedule 11 anchor ${name} resolves (editable Draft)`, async ({ request }) => {
    const url = scheduleUrl(anchor.key.millId, anchor.key.year);
    const res = await request.get(url);
    expect(
      res.ok(),
      `Schedule 11 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. Re-ground fixtures/sch11/schedule11-test-data.ts.`,
    ).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string | null; editable: boolean };
    expect(
      doc.trackStatus,
      `Schedule 11 anchor "${name}" silviculture track must be Draft ("D"). Re-ground fixtures/sch11/schedule11-test-data.ts.`,
    ).toBe('D');
    expect(
      doc.editable,
      `Schedule 11 anchor "${name}" must be editable. Re-ground fixtures/sch11/schedule11-test-data.ts.`,
    ).toBe(true);
  });
}

/**
 * Anchors that must hold NO locations at rest.
 *
 * The suite's footer-total, row-count and empty-table assertions are only true of an otherwise-empty
 * anchor — happy-path's "the anchor starts pristine, so the single row IS the totals",
 * multiple-locations' `lists 2 locations`, delete's `table is empty` plus blank footer. Checking that here
 * turns one escaped row into a single actionable preflight message instead of several confusing mid-suite
 * reds that point at the wrong thing.
 *
 * EXCLUDES the S10 track-independence anchor, which legitimately arrives with seeded rows ("20173",
 * "20173-2"): it is the only (mill, year) in the extract with a past-Draft 1-10 track AND a Draft
 * silviculture track, so there was no empty alternative to pick. That scenario is written for it — it
 * asserts the added row's stored record and the live editing surface, never a count or a total.
 * EXCLUDES the validate-only anchor too: nothing is ever written there and no scenario reads its row
 * count, so a row appearing on it would break nothing and must not fail the gate.
 */
const PRISTINE_ANCHORS = EDITABLE_DRAFT_ANCHORS.filter(
  ({ anchor }) =>
    anchor !== TRACK_INDEPENDENCE_ANCHOR && anchor !== VALIDATION_ANCHOR,
);

test('preflight: Schedule 11 pristine anchors hold no locations at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const { name, anchor } of PRISTINE_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    expect(res.ok(), `Schedule 11 anchor "${name}" GET -> HTTP ${res.status()}`).toBeTruthy();
    const doc = (await res.json()) as { locations: { location: string }[] };
    if (doc.locations.length > 0) {
      dirty.push(
        `${name} (${anchor.key.millId}/${anchor.key.year}) holds ${String(doc.locations.length)}: ${doc.locations
          .map((l) => `"${l.location}"`)
          .join(', ')}`,
      );
    }
  }
  expect(
    dirty,
    `Schedule 11 anchors must be EMPTY before the suite runs — the totals/row-count/empty-table assertions depend on it. Delete the rows listed here (leftover E2E markers from an interrupted run, or a hand-edit of the seeded DB) and re-run:\n  ${dirty.join('\n  ')}`,
  ).toEqual([]);
});

test('preflight: no leftover E2E marker rows on any Schedule 11 anchor', async ({ request }) => {
  // Residue check across EVERY anchor, including the two the emptiness check above excludes: a row
  // carrying one of our own markers is always a failed teardown, never seeded data. Catches it on the
  // anchors where it would otherwise be invisible.
  const markers = new Set<string>(Object.values(MARKER));
  const residue: string[] = [];
  for (const { name, anchor } of EDITABLE_DRAFT_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    if (!res.ok()) continue; // the per-anchor test above already reports a bad GET
    const doc = (await res.json()) as { locations: { location: string }[] };
    for (const row of doc.locations.filter((l) => markers.has(l.location))) {
      residue.push(`${name} (${anchor.key.millId}/${anchor.key.year}): "${row.location}"`);
    }
  }
  expect(
    residue,
    `leftover E2E rows found — a previous run's teardown did not complete. Delete them and re-run:\n  ${residue.join('\n  ')}`,
  ).toEqual([]);
});

test('preflight: Schedule 11 mutating anchors are all distinct', async () => {
  // Parallel safety is a property of the DATA, so it is asserted here rather than trusted to review: two
  // mutating scenarios sharing a key would clobber each other's rows under fullyParallel.
  const mutating = EDITABLE_DRAFT_ANCHORS.filter((a) => !a.name.startsWith('validate-only'));
  const keys = mutating.map(({ anchor }) => `${anchor.key.millId}/${anchor.key.year}`);
  expect(
    new Set(keys).size,
    `every mutating Schedule 11 anchor must own a DISTINCT (mill, year) — got ${keys.join(', ')}`,
  ).toBe(keys.length);
  // The validate-only anchor must not be any mutating pair (it is reached but never written to).
  expect(
    keys,
    'the validate-only anchor must NOT be one of the mutating anchors',
  ).not.toContain(`${VALIDATION_ANCHOR.key.millId}/${VALIDATION_ANCHOR.key.year}`);
});

test('preflight: Schedule 11 track-independence anchor still has a past-Draft 1-10 track', async ({
  request,
}) => {
  // The S10 anchor is the only row in the seed carrying this combination, so its drift would silently
  // turn the track-independence scenario into a plain add. Assert BOTH statuses explicitly.
  const { millId, year } = TRACK_INDEPENDENCE_ANCHOR.key;
  const res = await request.get(`/api/v1/mill-context?millId=${millId}&year=${year}`);
  expect(res.ok(), `mill-context ${millId}/${year} -> HTTP ${res.status()}`).toBeTruthy();
  const ctx = (await res.json()) as {
    schedules1To10Status?: { code?: string } | null;
    schedule11Status?: { code?: string } | null;
  };
  expect(
    ['S', 'V'],
    `S10 anchor ${millId}/${year}: the Schedule 1-10 track must be past Draft, got ${String(ctx.schedules1To10Status?.code)}. Re-ground the S10 anchor.`,
  ).toContain(ctx.schedules1To10Status?.code);
  expect(
    ctx.schedule11Status?.code,
    `S10 anchor ${millId}/${year}: the silviculture track must still be Draft. Re-ground the S10 anchor.`,
  ).toBe('D');
});

for (const [name, anchor] of Object.entries(GUARD_ANCHORS)) {
  test(`preflight: Schedule 11 guard anchor ${name} still produces HTTP ${anchor.expectHttp}`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    expect(
      res.status(),
      `guard anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) should GET HTTP ${anchor.expectHttp}. Re-ground fixtures/sch11/schedule11-test-data.ts.`,
    ).toBe(anchor.expectHttp);
    if (anchor.expectHttp === 200) {
      const doc = (await res.json()) as { trackStatus: string | null; editable: boolean };
      expect(doc.editable, `guard anchor "${name}" must be read-only (editable:false)`).toBe(false);
      expect(doc.trackStatus, `guard anchor "${name}" must be non-Draft`).not.toBe('D');
    }
  });
}

test('preflight: pinned biogeoclimatic catalogue options still resolve', async ({ request }) => {
  // The BEC ComboBox submits the option ID, so a renumbered catalogue would make every add fail with a
  // confusing 400. Assert each pinned option is returned by its own search term, id AND label.
  for (const option of [BEC_PRIMARY, BEC_SECONDARY]) {
    const res = await request.get(
      `/api/v1/schedule11/biogeoclimatic-catalogue?q=${encodeURIComponent(option.query)}`,
    );
    expect(res.ok(), `BEC catalogue search "${option.query}" -> HTTP ${res.status()}`).toBeTruthy();
    const options = (await res.json()) as { id: number; label: string }[];
    expect(
      options.find((o) => o.id === option.id && o.label === option.label),
      `pinned BEC option ${option.id} "${option.label}" no longer returned by q="${option.query}". Re-ground fixtures/sch11/schedule11-test-data.ts.`,
    ).toBeTruthy();
    // EXACTLY one option may carry this label. The page object picks the suggestion with
    // getByRole('option', { name, exact: true }); a duplicate label would sail through the check above and
    // then strict-mode-kill every write scenario with a far less obvious error.
    expect(
      options.filter((o) => o.label === option.label).length,
      `BEC label "${option.label}" is no longer unique in the q="${option.query}" results — the suggestion picker would hit a strict-mode violation. Re-ground to a unique label.`,
    ).toBe(1);
  }
});

/**
 * The `.feature` files, read as text — Gherkin must stay literal to be readable by a BA, so every verbatim
 * contract string and row marker is typed into the specs rather than interpolated from the fixtures file.
 * That leaves the fixtures file documenting strings nothing checks, which is what the next two tests fix.
 */
const FEATURE_DIR = fileURLToPath(
  new URL('../features/sch11/uc-sch11-001-report-costs/', import.meta.url),
);

const featureText = (): string =>
  readdirSync(FEATURE_DIR)
    .filter((f) => f.endsWith('.feature'))
    .map((f) => readFileSync(join(FEATURE_DIR, f), 'utf8'))
    .join('\n');

test('preflight: every pinned verbatim contract string is actually asserted by a feature', () => {
  // WHY: these constants claim to be the single source of truth for the API-owned strings (AD-8), but the
  // specs assert their own literals, so a drifted constant would sit there looking authoritative while
  // every test stayed green — and the next person to reuse one would assert the wrong text. This makes the
  // claim load-bearing in the only direction that matters: no constant may fall out of sync with the specs.
  const text = featureText();
  const pinned: Record<string, string> = {
    'MSG.saved': MSG.saved,
    'MSG.deleted': MSG.deleted,
    'MSG.statusChecked': MSG.statusChecked,
    'MSG.requirementsMet': MSG.requirementsMet,
    'ERR.millYearNotSelected': ERR.millYearNotSelected,
    'ERR.millNotActive': ERR.millNotActive,
    'ERR.scheduleNotFound': ERR.scheduleNotFound,
    ERR_STALE_EDIT,
    'FLD.locationRequired': FLD.locationRequired,
    'FLD.enhancedRequired': FLD.enhancedRequired,
    'FLD.biogeoRequired': FLD.biogeoRequired,
    'FLD.netAreaRequired': FLD.netAreaRequired,
    'FLD.netAreaRange': FLD.netAreaRange,
    'FLD.costRange': FLD.costRange,
    'missingCostMessage()': missingCostMessage(MARKER.checkMissingActual, 'Actual'),
    // NOT listed, deliberately: FLD.locationMaxLength and FLD.commentsMaxLength. Carbon's `maxLength`
    // stops the keystroke, so neither cap is reachable from a browser at all — they belong as backend
    // bean-validation cases (defects.md GAP-1/GAP-2, carried in deferred-work.md). They stay in the
    // fixtures file as the pinned wording for whoever writes those tests.
  };
  const missing = Object.entries(pinned)
    .filter(([, literal]) => !text.includes(literal))
    .map(([name, literal]) => `${name} -> ${JSON.stringify(literal)}`);
  expect(
    missing,
    `these pinned strings appear in NO .feature file, so nothing asserts them. Either the constant drifted from the spec (fix whichever is wrong) or the coverage was dropped:\n  ${missing.join('\n  ')}`,
  ).toEqual([]);
});

test('preflight: every row marker is used by a feature', () => {
  // The cleanup registry deletes by MARKER value while the specs type the marker text by hand. If the two
  // drift, teardown deletes nothing, the row survives, and it then poisons the totals / Check-Status
  // assertions on that anchor — a slow, confusing failure a long way from its cause.
  const text = featureText();
  const unused = Object.entries(MARKER)
    .filter(([, marker]) => !text.includes(marker))
    .map(([name, marker]) => `MARKER.${name} -> ${JSON.stringify(marker)}`);
  expect(
    unused,
    `these markers are registered for cleanup but typed nowhere in the specs, so teardown would delete nothing and the row would survive:\n  ${unused.join('\n  ')}`,
  ).toEqual([]);
});

test('preflight: the S16 populated-prefix returns real suggestions but is not itself a label', async ({
  request,
}) => {
  // S16 must reject free text typed WHILE suggestions exist. That needs a prefix that (a) returns
  // options, and (b) is not itself selectable — otherwise the scenario would be testing an empty list.
  const res = await request.get(
    `/api/v1/schedule11/biogeoclimatic-catalogue?q=${encodeURIComponent(BEC_POPULATED_PREFIX)}`,
  );
  expect(res.ok(), `BEC catalogue search "${BEC_POPULATED_PREFIX}" -> HTTP ${res.status()}`).toBeTruthy();
  const options = (await res.json()) as { id: number; label: string }[];
  expect(
    options.length,
    `S16 prefix "${BEC_POPULATED_PREFIX}" must return suggestions so forced selection is actually exercised`,
  ).toBeGreaterThan(0);
  expect(
    options.some((o) => o.label === BEC_POPULATED_FIRST_OPTION),
    `S16 expects "${BEC_POPULATED_FIRST_OPTION}" among the "${BEC_POPULATED_PREFIX}" suggestions. Re-ground BEC_POPULATED_FIRST_OPTION.`,
  ).toBe(true);
  expect(
    options.some((o) => o.label === BEC_POPULATED_PREFIX),
    `S16 prefix "${BEC_POPULATED_PREFIX}" must NOT itself be a catalogue label, or the typed text would be selectable`,
  ).toBe(false);
});
