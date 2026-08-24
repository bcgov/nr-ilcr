import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import {
  ANCHORS,
  GUARD_ANCHORS,
  MUTATING_ANCHORS,
  READ_ONLY_ANCHORS,
  READ_ONLY_DRAFT_ANCHORS,
  scheduleUrl,
} from '../fixtures/sch4/schedule4-test-data';

/**
 * Schedule 4 preflight — asserts every pinned anchor still resolves BEFORE the suite runs, so a
 * re-extracted / drifted DB (or a missing seed patch) fails fast with one actionable message instead of a
 * dozen confusing mid-suite failures.
 *
 * This is a READ-ONLY check: it never writes, so it is safe to run against the seeded DB every time.
 */

const HINT = 'Re-ground fixtures/sch4/schedule4-test-data.ts.';

/** Every Draft anchor — the mutating ones and the validate-only one — must be an editable Draft. */
const DRAFT_ANCHORS = [...MUTATING_ANCHORS, ...READ_ONLY_DRAFT_ANCHORS];

for (const { name, anchor } of DRAFT_ANCHORS) {
  test(`preflight: Schedule 4 anchor ${name} resolves (editable Draft)`, async ({ request }) => {
    const url = scheduleUrl(anchor.key.millId, anchor.key.year);
    const res = await request.get(url);
    await expect(
      res,
      `Schedule 4 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}, ${anchor.purpose}) GET -> HTTP ${res.status()}. ${HINT}`,
    ).toBeOK();
    const doc = (await res.json()) as { trackStatus: string | null; editable: boolean };
    expect(
      doc.trackStatus,
      `Schedule 4 anchor "${name}" 1-10 track must be Draft ("D"). ${HINT}`,
    ).toBe('D');
    expect(doc.editable, `Schedule 4 anchor "${name}" must be editable. ${HINT}`).toBe(true);
  });
}

/**
 * Every Draft anchor must hold NO LOCATIONS at rest.
 *
 * WHY THIS IS ASSERTED RATHER THAN ASSUMED: the suite's "the list shows only what I created", "the list is
 * empty", location-count and delete-returns-to-empty assertions are only true of an empty anchor. One
 * escaped location (a killed run, a partial cleanup, a manual poke at the seeded DB) turns those into
 * confusing reds that point at the wrong thing. Listing the offenders here makes the failure
 * self-diagnosing, and each scenario's own cleanup read-back is the per-test backstop.
 */
test('preflight: Schedule 4 Draft anchors hold no locations at rest', async ({ request }) => {
  const dirty: string[] = [];
  for (const { name, anchor } of DRAFT_ANCHORS) {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(res, `Schedule 4 anchor "${name}" GET -> HTTP ${res.status()}`).toBeOK();
    const doc = (await res.json()) as { locations: { name: string }[] };
    if (doc.locations.length > 0) {
      dirty.push(
        `${name} (${anchor.key.millId}/${anchor.key.year}): ${doc.locations.map((l) => l.name).join(', ')}`,
      );
    }
  }
  expect(
    dirty,
    `Schedule 4 anchors already hold locations: ${dirty.join('; ')}. DELETE /api/v1/schedule4/locations?millId=&year=&id= for each, then re-run.`,
  ).toEqual([]);
});

/**
 * Every anchor must be a DISTINCT (mill, year) — the suite runs `fullyParallel`, so two scenarios sharing
 * a mutating anchor would race each other's writes AND each other's cleanup sweeps. Checked across the
 * WHOLE table (mutating + validate-only + read-only + guards) because a validate-only anchor colliding
 * with a mutating one would be just as broken.
 */
test('preflight: Schedule 4 anchors are all distinct', async () => {
  const keys = [
    ...Object.values(ANCHORS).map((a) => `${a.key.millId}/${a.key.year}`),
    ...Object.values(READ_ONLY_ANCHORS).map((a) => `${a.key.millId}/${a.key.year}`),
    ...Object.values(GUARD_ANCHORS).map((a) => `${a.key.millId}/${a.key.year}`),
  ];
  const duplicates = keys.filter((key, index) => keys.indexOf(key) !== index);
  expect(
    duplicates,
    `Schedule 4 anchors must be distinct so parallel scenarios never write to the same schedule — duplicated: ${duplicates.join(', ')}`,
  ).toEqual([]);
});

/**
 * The read-only anchors must still be non-Draft, must still carry the seed patch's location, and that
 * location must still hold the exact figures the `@S18` outline asserts on screen.
 *
 * The figures come from `real-test-data-patches/sch4/view-mode-amounts.sql` and are the ONLY non-Draft
 * Schedule 4 amounts in the DB (the extract has none), so a missing patch or a hand-edit would otherwise
 * surface as an opaque table mismatch deep inside a browser test.
 */
for (const [name, anchor] of Object.entries(READ_ONLY_ANCHORS)) {
  test(`preflight: Schedule 4 read-only anchor ${name} is still non-Draft (${anchor.trackStatus}) and patched`, async ({
    request,
  }) => {
    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    await expect(
      res,
      `read-only anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) GET -> HTTP ${res.status()}. ${HINT}`,
    ).toBeOK();
    const doc = (await res.json()) as {
      trackStatus: string | null;
      editable: boolean;
      locations: {
        name: string;
        comments?: string | null;
        categories: { code: number; volume?: number | null; cost?: number | null; distance?: number | null }[];
        subPageRows: {
          code: number;
          description?: string | null;
          distance?: number | null;
          volume?: number | null;
          cost?: number | null;
        }[];
      }[];
    };
    expect(
      doc.trackStatus,
      `read-only anchor "${name}" must still be track "${anchor.trackStatus}". ${HINT}`,
    ).toBe(anchor.trackStatus);
    expect(doc.editable, `read-only anchor "${name}" must NOT be editable. ${HINT}`).toBe(false);

    const patched = doc.locations.find((l) => l.name === anchor.location);
    expect(
      patched,
      `the seed patch location "${anchor.location}" is missing from ${anchor.key.millId}/${anchor.key.year} — run ./scripts/apply-patches.sh (see real-test-data-patches/sch4/view-mode-amounts.sql).`,
    ).toBeTruthy();

    // The extract's own location the read-only list assertions name alongside the patched one.
    expect(
      doc.locations.map((l) => l.name),
      `read-only anchor "${name}" no longer lists "${anchor.otherLocationName}". ${HINT}`,
    ).toContain(anchor.otherLocationName);

    const fixed = patched!.categories.find((c) => c.code === 40);
    const distance = patched!.categories.find((c) => c.code === 47);
    const row = patched!.subPageRows.find((r) => r.code === 43);
    expect(
      {
        comments: patched!.comments ?? null,
        fixedVolume: fixed?.volume ?? null,
        fixedCost: fixed?.cost ?? null,
        distanceKm: distance?.distance ?? null,
        distanceVolume: distance?.volume ?? null,
        distanceCost: distance?.cost ?? null,
        rowDescription: row?.description ?? null,
        rowDistance: row?.distance ?? null,
        rowVolume: row?.volume ?? null,
        rowCost: row?.cost ?? null,
      },
      `the seed patch figures on "${anchor.location}" (${name}) drifted — the @S18 outline no longer describes it. Re-apply ./scripts/apply-patches.sh, or re-ground the fixture.`,
    ).toEqual({
      comments: anchor.comments,
      fixedVolume: anchor.stored.fixedVolume,
      fixedCost: anchor.stored.fixedCost,
      distanceKm: anchor.stored.distanceKm,
      distanceVolume: anchor.stored.distanceVolume,
      distanceCost: anchor.stored.distanceCost,
      rowDescription: anchor.stored.rowDescription,
      rowDistance: anchor.stored.rowDistance,
      rowVolume: anchor.stored.rowVolume,
      rowCost: anchor.stored.rowCost,
    });
  });
}

/** The context-guard anchors must still produce their pinned HTTP status and verbatim detail. */
for (const [name, guard] of Object.entries(GUARD_ANCHORS)) {
  test(`preflight: Schedule 4 guard anchor ${name} still produces HTTP ${guard.expectHttp}`, async ({
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

/** Helper function to find Gherkin feature files recursively. */
function getFeatureFiles(dir: string): string[] {
  let files: string[] = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files = files.concat(getFeatureFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith('.feature')) {
      files.push(fullPath);
    }
  }
  return files;
}

test('preflight: Schedule 4 mutating anchors are used in at most one feature file', async () => {
  const dir = path.join(__dirname, '../features/sch4');
  const files = getFeatureFiles(dir);
  const anchorUsage = new Map<string, string[]>();

  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8');
    const matches = content.matchAll(/Schedule 4 anchor "([^"]+)"/g);
    for (const match of matches) {
      const name = match[1];
      if (name === 'validation') {
        // "validation" is the designated non-mutating anchor and is safely shared.
        continue;
      }
      if (!anchorUsage.has(name)) {
        anchorUsage.set(name, []);
      }
      const list = anchorUsage.get(name)!;
      if (!list.includes(file)) {
        list.push(file);
      }
    }
  }

  const duplicates: string[] = [];
  for (const [name, usages] of anchorUsage.entries()) {
    if (usages.length > 1) {
      duplicates.push(`"${name}" used in: ${usages.map((f) => path.basename(f)).join(', ')}`);
    }
  }

  expect(
    duplicates,
    `Each mutating Schedule 4 anchor must be used by at most one scenario file to maintain parallel safety — duplicated: ${duplicates.join('; ')}`,
  ).toEqual([]);
});

test('preflight: Cross-domain anchors are globally distinct', async () => {
  const fixturesDir = path.join(__dirname, '../fixtures');
  const fixtureFiles = fs.readdirSync(fixturesDir, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name !== 'common')
    .map((e) => path.join(fixturesDir, e.name, `${e.name === 'sec' ? 'working-context' : e.name === 'sch7a' ? 'schedule7a' : e.name === 'sch7b' ? 'schedule7b' : e.name === 'sch9' ? 'schedule9' : e.name === 'sch6' ? 'schedule6' : e.name === 'sch5' ? 'schedule5' : e.name === 'sch10' ? 'schedule10' : e.name === 'sch8' ? 'schedule8' : e.name}-test-data.ts`))
    .filter((f) => fs.existsSync(f));

  const allKeys = new Map<string, string[]>();

  for (const file of fixtureFiles) {
    const domainName = path.basename(path.dirname(file));
    const content = fs.readFileSync(file, 'utf8');
    const matches = content.matchAll(/millId:\s*(\d+),\s*year:\s*(\d+)/g);
    for (const match of matches) {
      const key = `${match[1]}/${match[2]}`;
      if (!allKeys.has(key)) {
        allKeys.set(key, []);
      }
      const list = allKeys.get(key)!;
      if (!list.includes(domainName)) {
        list.push(domainName);
      }
    }
  }

  const duplicates: string[] = [];
  for (const [key, domains] of allKeys.entries()) {
    if (domains.length > 1) {
      duplicates.push(`anchor "${key}" shared across: ${domains.join(', ')}`);
    }
  }

  expect(
    duplicates,
    `Cross-domain anchors must be globally distinct to prevent test runner parallel collision — duplicated: ${duplicates.join('; ')}`,
  ).toEqual([]);
});
