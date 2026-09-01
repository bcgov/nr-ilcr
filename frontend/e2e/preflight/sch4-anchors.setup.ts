import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'node:url';

// This package is `"type": "module"`, so the CommonJS directory global is not defined here.
// Referencing it threw a ReferenceError in the two static checks below, and because the `chromium`
// project declares `dependencies: ['setup']`, a failing setup test SKIPS every dependent scenario —
// so this one-file bug took 39 scenarios out of the run on any branch, not just Schedule 4's.
// Same ESM-safe idiom already used by `sch11-anchors.setup.ts`.
const HERE = path.dirname(fileURLToPath(import.meta.url));
import { collectAnchorKeys, fixtureFiles } from './anchor-keys';
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
  const dir = path.join(HERE, '../features/sch4');
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

/**
 * (mill, year) keys that ARE shared across domains on purpose, with the reason. Anything shared and NOT
 * listed here fails the guard below.
 *
 * WHY AN ALLOW-LIST AND NOT A BARE "no key twice" RULE: the anchor key is a (mill, year) REPORT, but a
 * report holds every schedule. Two domains sharing a key only actually contend when they write the same
 * schedule's rows, or when one changes the track status the other depends on. A flat rule would forbid
 * cases that cannot collide, and the fixtures carry no uniform mutating/read-only flag to derive it from —
 * so the exemptions are enumerated with their justification, the same shape as the designated-shared
 * `validation` anchor in the guard above.
 *
 * Each entry was adjudicated 2026-08-24 when this guard was first made to run (see VER-8):
 */
/**
 * Why every sch3 anchor sits on a report sch4 also pins, and why that is safe. Adjudicated 2026-08-24
 * when UC-SCH3-001 was authored.
 *
 * Schedule 3 has NO create path in the rewrite — it can only be opened where a category-3
 * `ILCR_REPORT_SUMMARY` already exists — and all 15 mill-years that DO open it in the extract are
 * already pinned by sch1/sch2 (see `fixtures/sch3/schedule3-test-data.ts`). Those two domains genuinely
 * contend with Schedule 3 (Schedule 1 pulls items 143/139 FROM Schedule 3, Schedule 2 carries figures
 * FROM it, and the BR-09 crown push WRITES Schedule 1's volume rows), so sch3 seeds its own anchors on
 * mill-years pinned by sch4 instead.
 *
 * That share cannot collide, structurally rather than by convention: no backend path links Schedule 3 to
 * Schedule 4 (`grep -rl Schedule3Service backend/src/main/java` → schedule1, schedule2, schedule5,
 * reporting only). Schedule 4 writes category-"4" `TRANSPORTATION_REPORT` rows; Schedule 3 writes
 * category-"3" summary/detail rows. Neither can move a figure the other asserts on, and neither changes
 * the Draft track status they both require.
 */
const SCH3_SCH4_SHARED =
  'sch3 anchor + sch4 anchor on the same report, different schedules — see the note above this map.';

const SCH3_SCH4_KEYS = [
  '16050/2017',
  '16050/2019',
  '16050/2020',
  '16050/2021',
  '17052/2018',
  '17052/2019',
  '17052/2020',
  // 17052/2021 is deliberately NOT here — it is shared with sch1 as well, so the blanket
  // "different schedules" reason above does not cover it. It has its own entry in the map below.
  '22050/2018',
  // sch3 'retry' (S17) + sch4 'per-unit-after-save'. Both MUTATING, same shape as 12050/2018 below —
  // except that sch3's save here DOES carry a Crown Timber volume, so the BR-09 push runs. It can only
  // WRITE into a Schedule 1 that has been saved, and `sch3-anchors.setup.ts` asserts this mill-year has
  // none (so the push answers WRN-002 and stores nothing). Schedule 4 is untouched by it either way: it
  // owns category-"4" TRANSPORTATION_REPORT rows, which BR-09 never reaches.
  '22050/2019',
  '22050/2020',
  '22050/2021',
  '23050/2017',
  '23050/2018',
  '23050/2019',
  '25054/2017',
  // sch3 'stale-edit' (GAP-2, the optimistic-lock scenario) + sch4 'truck-rehaul'. Same reason as the
  // rest of this list, and the narrowest case of it: BOTH anchors are mutating. Still structurally
  // safe — sch3 writes category-3 summary/detail rows and sch4 writes category-4
  // TRANSPORTATION_REPORT rows, and the sch3 scenario deliberately touches no Crown Timber volume, so
  // it cannot reach Schedule 1 either.
  '12050/2018',
];

/**
 * Why every `sec` anchor is safe to share, and why five of them appeared here only on 2026-08-28.
 *
 * The `sec` (Home / working context) fixture interleaves `millNumber` and `millName` between `millId`
 * and `year`, and both regexes this guard used until then required the two to be ADJACENT — so all five
 * sec anchors were invisible to it from the day it was written. Nothing was wrong with the anchors; the
 * scan could not see them. Fixed in `preflight/anchor-keys.ts` (it now pairs `millId` with the `year` in
 * its own enclosing braces), which surfaced these five for adjudication.
 *
 * All five are safe, and structurally rather than by convention. sec writes NOTHING: Home's "Save" is a
 * resolve — `GET /api/v1/mill-context` — so no sec scenario can move anything. And nothing the other
 * side writes can move what sec reads: sec asserts the mill number/name and the two TRACK STATUS banner
 * lines, and the only code in the whole backend that writes `ILCR_MILL_REPORT_STATUS` is
 * `ReportingYearRepository` (the admin open-year flow, Story 24.1) — no schedule save touches it, and
 * nothing writes `ILCR_MILL_REPORT_STATUS_RPT_VW` at all, which is where the banner DATE comes from
 * (verified 2026-08-28 by sweeping backend/src/main/java for writes to both).
 */
const SEC_READ_ONLY =
  'sec asserts only the mill number/name and the two track-status banner lines; it writes nothing, and '
  + 'no schedule save can move a track code or a banner date — see the note above this map.';

const SHARED_ACROSS_DOMAINS = new Map<string, string>([
  // The three guard anchors. Each exists to make a GET fail in a specific way and is held read-only by
  // construction — a closed mill and a missing schedule cannot be written to at all.
  ['13/2017', 'closed-mill guard (HTTP 409) — read-only in sch1, sch2, sch11 and sec'],
  ['16050/2016', 'no-schedule guard (HTTP 404) — read-only in sch1, sch11 and sec'],
  ['12050/2016', 'submitted/non-Draft guard — read-only in sch1 and sch11'],
  // The three sec shares where the other side DOES write. Same reason for all three.
  ['13050/2017', `sch1 MUTABLE_DRAFT (the S01 write target) + sec DEFAULT_CONTEXT. ${SEC_READ_ONLY}`],
  ['12050/2017', `sch1 Other-Costs inline-edit anchor + sec OPEN_WITH_STATUS. ${SEC_READ_ONLY}`],
  ['9050/2019', `sch4 'validation-recovery' (mutating) + sec OPEN_ALT. ${SEC_READ_ONLY}`],
  // The two mixed pairs: a read-only Check Status fixture in sch1 alongside a mutating anchor in another
  // domain. Safe because the writer writes a DIFFERENT schedule's rows than the reader reads.
  [
    '24051/2016',
    "sch1 'missing-line-item-volume' (read-only S15 Check Status fixture) + sch11 MULTI_ADD_ANCHOR "
      + '(mutating). Schedule 11 is the independent silviculture track: its writes cannot alter the '
      + 'Schedule 1 line items the fixture asserts on.',
  ],
  [
    '22050/2016',
    "sch1 'other-costs-volume-without-cost' (read-only S15/S16 Check Status fixture) + sch2 "
      + 'HAPPY_PATH_ANCHOR (mutating). Schedule 2 writes its own cost items; the fixture reads Schedule 1 '
      + 'line items and Other Costs. This is the narrowest margin of the five — both sit on the 1-10 '
      + 'track — so if a Schedule 1 scenario ever starts WRITING this anchor, this exemption must go.',
  ],
  // The sch3 <-> sch4 shares (16 keys, one reason — see the note above; 17052/2021 is the 17th sch3
  // anchor on a sch4 report and is adjudicated separately, immediately below).
  ...SCH3_SCH4_KEYS.map((key) => [key, SCH3_SCH4_SHARED] as [string, string]),
  // The one three-domain share, and the only sch1 <-> sch3 share in the suite.
  [
    '17052/2021',
    "sch1 'no-schedule' (S21 render-state + S08 save-first gate — read-only) + sch3 'check-empty' "
      + "(S10 — read-only) + sch4 'persistence' (mutating). ADJUDICATED 2026-08-31 (PR #402): sch1 and "
      + 'sch3 genuinely contend — Schedule 1 derives its Forest Mgmt Admin / Silviculture Admin costs '
      + 'FROM Schedule 3 and pre-fills its nine volume codes from a Schedule 3 Crown Timber volume '
      + '(BR-09) — so this share is safe ONLY because the sch3 side is read-only and permanently EMPTY: '
      + 'a Schedule 3 with no stored amounts leaves every Schedule 1 figure null and never arms the '
      + "pre-fill, which is exactly what sch1's two scenarios assert. sch3's MUTATING `retry` anchor "
      + 'used to sit here and was moved to 22050/2019 for that reason. If a sch3 scenario ever starts '
      + 'WRITING this anchor, this exemption must go.',
  ],
  // The one sch3 <-> sch11 share.
  [
    '24051/2015',
    "sch3 'never-started' (the DIV-1 divergence anchor — read-only, and deliberately NOT seeded: its "
      + 'whole point is that Schedule 3 does not exist there) + sch11 ADD_ANCHOR (mutating). Nothing '
      + 'sch11 does can create a category-3 summary, so the 404 the sch3 scenario asserts is invariant '
      + "under Schedule 11's writes.",
  ],
]);

test('preflight: Cross-domain anchors are globally distinct', async () => {
  const fixturesDir = path.join(HERE, '../fixtures');

  // The scan itself lives in `preflight/anchor-keys.ts`, shared with the CI-seed-parity gate.
  //
  // It handles both object-literal property orders AND the positional `at(MILL_x, millId, year, …)`
  // builder that sch3 and sch4 use for their anchor TABLES. Until 2026-08-24 this guard matched only
  // the object literals, so it saw sch4's four guard anchors and NONE of its 48 table anchors — and
  // none of sch3's 17 either: it ran, passed, and was blind to most of the keys it exists to compare
  // (the dead-check class VER-8 records, one level down). It also THROWS on a domain whose fixture it
  // cannot find, rather than skipping it, for the same reason. Two consumers re-deriving that regex
  // would reopen the hole, which is why it is one module.
  const files = fixtureFiles(fixturesDir);
  const allKeys = collectAnchorKeys(fixturesDir);

  // Prove the scan actually saw every domain — the failure this guard had was silent under-scanning, so
  // assert the inputs before asserting the property.
  expect(
    files.length,
    `the cross-domain guard scanned ${files.length} fixture file(s): ${files
      .map((f) => f.domain)
      .join(', ')}`,
  ).toBeGreaterThanOrEqual(6);
  expect(allKeys.size, 'the cross-domain guard found no (mill, year) keys at all — it is not scanning')
    .toBeGreaterThan(0);

  const unexpected: string[] = [];
  for (const [key, keyDomains] of allKeys.entries()) {
    if (keyDomains.length > 1 && !SHARED_ACROSS_DOMAINS.has(key)) {
      unexpected.push(`anchor "${key}" shared across: ${keyDomains.join(', ')}`);
    }
  }

  expect(
    unexpected,
    'Cross-domain anchors must be globally distinct to prevent test-runner parallel collision. A key that '
      + 'is genuinely safe to share belongs in SHARED_ACROSS_DOMAINS above WITH its reason — '
      + `unexpected: ${unexpected.join('; ')}`,
  ).toEqual([]);
});
