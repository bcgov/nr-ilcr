import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'node:url';
import {
  byMillThenYear,
  collectAnchorKeys,
  fixtureFiles,
  scanAnchorKeys,
  type AnchorKey,
} from './anchor-keys';

/**
 * PREFLIGHT — the CI seed carries every anchor the fixtures pin.
 *
 * ---------------------------------------------------------------------------------------------------
 * WHY THIS EXISTS
 * ---------------------------------------------------------------------------------------------------
 * The same e2e test data now lives in five places: the fixture anchor tables that DECLARE what we pin,
 * the domain preflights that ASSERT it against a live database, the real-data extract, the local
 * `real-test-data-patches/**.sql`, and `backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql` —
 * the Flyway repeatable that rebuilds the anchors for CI, which has no extract image and no sqlplus
 * step and therefore cannot apply a patch at all.
 *
 * A mismatch between the last two has a nasty signature: the suite passes in whichever environment you
 * happen to be using and fails in the other, and the failure reads as a broken test rather than as
 * missing data. It has already happened twice — the sch3 patch (17 Schedule 3 summaries and 115 detail
 * rows) and the three BR-12 patches were never folded into the seed, so the whole sch3 domain would
 * have failed in CI on a 404 that looks exactly like an app defect.
 *
 * So this runs in the `setup` project, on every local and CI run, and needs NO database: it reads the
 * fixture files and the migration SQL off disk and compares them. Prose in a skill file or a header
 * comment would not have caught either miss — `defects.md` VER-8 records two guards in this repo that
 * sat dead for an unknown stretch because nothing executed them.
 *
 * ---------------------------------------------------------------------------------------------------
 * ABSENCE IS SOMETIMES THE FIXTURE — why this is not a set-difference
 * ---------------------------------------------------------------------------------------------------
 * Four pinned anchors exist to make a GET FAIL in a specific way, and having no report-status row is
 * precisely what produces the failure. They are absent from BOTH databases — the extract does not carry
 * them either (verified 2026-08-28) — so parity is already satisfied; there is nothing to fix.
 *
 * A bare "every fixture key must be seeded" check would nonetheless report all four as missing, every
 * run, forever. That matters for two reasons, and neither is the one an earlier version of this comment
 * gave: four permanent false alarms train people to ignore the gate, and the obvious way to silence
 * them is to seed the rows — which breaks the very scenarios the anchors exist for. So each is
 * enumerated in DELIBERATELY_ABSENT below WITH its reason, and the gate checks BOTH directions: an
 * unlisted missing key fails, and a listed key that someone HAS seeded fails too. Only the first half
 * is something a diff could do.
 *
 * CORRECTION, and worth keeping so the value of the reverse check is not overstated: this used to say
 * seeding one would "silently disable" its scenarios. It would not. Each of the four asserts a POSITIVE
 * observable — a verbatim error message, or a banner with its status lines suppressed — so a seeded row
 * makes them FAIL, loudly. What the reverse check actually buys is failing FAST and AT THE CAUSE: two
 * seconds into the `setup` project, naming the anchor and the reason, instead of twelve minutes later as
 * scenarios in two different domains complaining about a missing error banner. That is worth having. It
 * is not the same as catching something that would otherwise pass unnoticed.
 *
 * ---------------------------------------------------------------------------------------------------
 * WHAT THIS DOES NOT CATCH — read before trusting it
 * ---------------------------------------------------------------------------------------------------
 *  1. Whether a seeded anchor holds the right VALUES. This compares openability (a mill, an active/
 *     closed status, a report-status row, a reporting year), not stored amounts. The per-domain
 *     preflights do that against the live database, which is strictly better — they see what the app
 *     actually reads. If an anchor is openable but its figures drifted, this passes and they fail.
 *
 *     A REAL ESCAPE OF THIS KIND, found the day after this gate was written (sch1 `defects.md` VER-1):
 *     the delete target 25052/2016 has a Schedule 3 carrying a Crown Timber volume in the extract and
 *     NO category-3 summary in the seed. Schedule 1 pre-fills its nine volume codes from that value
 *     when nothing is saved (BR-09), so a `lineItems.length === 0` assertion on the deleted schedule
 *     PASSED in CI and FAILED locally. Both databases were "openable and identical" by everything this
 *     file checks. The class to remember: a NEIGHBOURING schedule's data on the same (mill, year) can
 *     change the served document of the schedule under test, and the seed's parity claim covers
 *     report-status states, not that.
 *
 *     A SECOND ESCAPE, same day, different mechanism: sch3's two read-only render anchors were given
 *     report-status rows with tracks 'S' and 'V' but no category-3 summary, which is a
 *     Submitted-and-UNSAVED schedule. Everything here passed (mill, status row, year all present) and
 *     so did the domain preflight, because since defect #296 an unsaved schedule still answers 200 and
 *     its track is still right — the only visible symptom was one sub-page scenario failing in CI, four
 *     steps from the cause. Fixed in the seed, and `sch3-anchors.setup.ts` now asserts that a pinned
 *     TRACK also implies a SAVED schedule. Generalise it: "the row exists" is weaker than "the state
 *     the fixture means", and this file only ever checks the former.
 *  2. Track codes. The fixtures declare an expected track ('S'/'V'/Draft) in prose and in one typed
 *     table only, so deriving it from source needs its own allow-list. The runtime preflights assert
 *     it. Only the PARSER's grip on the two track columns is checked here, by the probes below.
 *  3. Anchors created at run time by a scenario's own Given. Those are the suite's business, not the
 *     seed's.
 *  4. The real-data extract. The seed claims parity with it; nothing here can verify that claim
 *     without the extract. That remains a human check on re-extract.
 */

// This package is `"type": "module"`, so the CommonJS `__dirname` global is not defined — referencing
// it throws a ReferenceError, and a failing `setup` test SKIPS every scenario in the dependent
// `chromium` project. Same ESM-safe idiom as sch4-/sch11-anchors.setup.ts.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const FIXTURES_DIR = path.join(HERE, '../fixtures');
const DB_DIR = path.join(HERE, '../../../backend/src/test/resources/db');
const DB_E2E_DIR = path.join(HERE, '../../../backend/src/test/resources/db-e2e');

const SEED = 'backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql';

const FIX_IT =
  `Add the row to ${SEED}, following that file's own conventions — plain INSERTs with pre-claimed ids `
  + 'against an empty schema, NOT the guarded PL/SQL the patches use — and extend its ID CLAIMS header. '
  + 'If the anchor is meant to have NO row, list it in DELIBERATELY_ABSENT in this file with the reason.';

/**
 * Pinned (mill, year) keys that must NOT have an ILCR_MILL_REPORT_STATUS row, and what each absence buys.
 * Every entry is a live fixture; the gate fails if one stops being referenced (a dead exemption is
 * cover for an anchor nobody is looking at).
 */
const DELIBERATELY_ABSENT = new Map<AnchorKey, string>([
  [
    '16050/2016',
    'sch11 S13 (GET 404 "Schedule not found.") + sec S07 (Home saves, and the banner shows the mill '
      + 'line ONLY — both track-status lines suppressed). Both read the absence directly. NOTE: an '
      + "earlier version of this entry also credited sch1 S21, copied from #327's seed comment without "
      + "checking. sch1's 'no-schedule' anchor is 17052/2021, which expects HTTP 200 (the #296 "
      + 'blank-form case) and IS seeded — a different fixture entirely.',
  ],
  [
    '16050/2015',
    "sch3 'not-found' (S16) — the same 404, on sch3's own mill-year so the two domains do not share a key.",
  ],
  ['13/2016', 'sch2 "not-found" — 404 on a closed mill that has no row for this year.'],
  ['25051/2018', 'sch4 "not-found" — 404. Mill 25051 IS seeded (CLS) and has a 2015 row for the 409 arm.'],
]);

/**
 * Positive controls on the SQL parser. These are not redundant with the checks below: every assertion
 * here compares two things this file derived, so a parser that silently stopped matching would make
 * most of them pass vacuously. Each probe pins a specific way the parse can go wrong.
 */
const PARSER_PROBES: { key: AnchorKey; codes: string; why: string }[] = [
  {
    key: '1/2016',
    codes: 'D/D',
    why:
      'ILCR_MILL_REPORT_STATUS is written (REPORT_YEAR, ILCR_MILL_ID, …) — YEAR FIRST. Read mill-first '
      + 'this row parses as 2016/1 and every anchor looks missing. Mill 1 / year 2016 cannot be '
      + 'transposed by accident, which is why it is the probe.',
  },
  {
    key: '23050/2016',
    codes: 'S/D',
    why: 'the two track codes DIFFER here — the only way to catch them being read in the wrong order.',
  },
  {
    key: '13050/2015',
    codes: 'D/V',
    why: 'they differ the other way round, so a swap cannot pass both probes.',
  },
  { key: '12050/2016', codes: 'S/S', why: 'a non-Draft pair, so the parser is not just matching Ds.' },
];

/** Floors, not counts: a vacuity guard that does not need editing every time an anchor is added. */
const MIN_FIXTURE_KEYS = 100;
const MIN_SEED_STATUS_ROWS = 100;

// ---------------------------------------------------------------------------------------------------
// A very small SQL reader — enough for `INSERT INTO THE.T (cols) VALUES (vals)`, and no more
// ---------------------------------------------------------------------------------------------------

/** Line and block comments in one alternation, so a `/*` inside a `--` line cannot swallow real SQL. */
const SQL_COMMENT = /--[^\n]*|\/\*[\s\S]*?\*\//g;

/**
 * Splits a VALUES list on top-level commas.
 *
 * Needed rather than a `[^)]*` match because real values contain both: `'EVANS FOR. PROD. (DIV. OF
 * LOUISIANA PACIFIC)'` carries parentheses inside a string literal, and `DATE '2015-01-01'` carries a
 * space-separated prefix. A depth counter that ignores anything inside quotes handles both.
 */
function splitValues(list: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let quoted = false;
  let current = '';
  for (const ch of list) {
    if (quoted) {
      quoted = ch !== "'";
      current += ch;
      continue;
    }
    if (ch === "'") {
      quoted = true;
      current += ch;
    } else if (ch === '(') {
      depth += 1;
      current += ch;
    } else if (ch === ')') {
      depth -= 1;
      current += ch;
    } else if (ch === ',' && depth === 0) {
      parts.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  parts.push(current);
  return parts.map((p) => p.trim());
}

/** `'x'` -> `x`, `NULL` -> null, anything else verbatim. */
function unwrap(value: string): string | null {
  if (/^null$/i.test(value)) {
    return null;
  }
  const quoted = value.match(/^'(.*)'$/s);
  return quoted ? quoted[1] : value;
}

/**
 * Names the reason an INSERT cannot be zipped by column name, or null if the shape is unrecognised.
 *
 * Both forms are valid SQL and neither carries a positional column list this gate can pair with its
 * values, so reading them would need a real parser. Naming them is what keeps the skip honest.
 */
function unreadableForm(rest: string): 'insert-select' | 'no-column-list' | null {
  if (/^\s*\([^)]*\)\s*SELECT\b/i.test(rest)) return 'insert-select';
  if (/^\s*VALUES\s*\(/i.test(rest)) return 'no-column-list';
  return null;
}

/**
 * The statements this gate genuinely cannot read, with the reason each is harmless.
 *
 * Keyed `TABLE:form`. Enumerated rather than skipped so a NEW unreadable INSERT into a parsed table
 * fails — the DELIBERATELY_ABSENT pattern applied to the parser instead of the data.
 */
const KNOWN_UNREADABLE = new Map<string, string>([
  [
    'BIOGEOCLIMATIC_CATALOGUE:insert-select',
    'db/V29__seed_schedule11_biogeo_cap_fixtures.sql generates 51 filler rows with '
      + "`SELECT 8900 + LEVEL … CONNECT BY LEVEL <= 51`, i.e. ids 8901-8951. This gate reads this table "
      + "ONLY for explicit-id collisions, and the seed's biogeo claims stop at 8855, so the generated "
      + 'band cannot collide. Re-check that if either range moves.',
  ],
]);

/**
 * Every INSERT into one table, as column-name -> value maps.
 *
 * Columns are zipped BY NAME, never by position: the seed's own statements vary their column lists
 * (REVISION_COUNT and CROWN_VOLUME appear on some rows and not others), and a positional read of
 * ILCR_MILL_REPORT_STATUS — whose first two columns are year then mill — is exactly the mistake this
 * whole file exists to stop shipping.
 */
function parseInserts(sql: string, table: string): Record<string, string | null>[] {
  const source = sql.replace(SQL_COMMENT, ' ');
  const start = new RegExp(`INSERT\\s+INTO\\s+THE\\.${table}(?![A-Z0-9_])`, 'gi');
  const rows: Record<string, string | null>[] = [];

  for (const match of source.matchAll(start)) {
    const rest = source.slice(match.index! + match[0].length);
    const shape = rest.match(/^\s*\(([^)]*)\)\s*VALUES\s*\(/i);
    if (!shape) {
      // NOTHING IS SKIPPED SILENTLY — that used to be a bare `continue`, which is the very thing the
      // arity check below refuses to do: an unread statement is a row this gate believes is missing,
      // and the MIN_* floors would only notice at scale. Raised in review.
      //
      // Two legitimate SQL forms cannot be zipped by name and so cannot be read here at all:
      // `INSERT INTO t (cols) SELECT …` (set-based) and `INSERT INTO t VALUES (…)` with no column
      // list. Both exist on the tree. Rather than hard-fail on long-standing SQL or wave them
      // through, they are RECOGNISED and enumerated — see KNOWN_UNREADABLE and the test that asserts
      // the set, the same shape as DELIBERATELY_ABSENT. Anything the classifier cannot even name
      // still throws immediately.
      if (unreadableForm(rest) === null) {
        const preview = rest.slice(0, 120).replace(/\s+/g, ' ').trim();
        throw new Error(
          `could not parse an INSERT INTO THE.${table}, and could not classify the form either. `
            + `Teach parseInserts about it rather than leaving the statement unread. Saw: ${preview}…`,
        );
      }
      continue;
    }
    const columns = shape[1].split(',').map((c) => c.trim().toUpperCase());

    // Walk to the matching close paren of the VALUES list, respecting quotes and nesting.
    const from = shape[0].length;
    let depth = 1;
    let quoted = false;
    let end = from;
    while (end < rest.length && depth > 0) {
      const ch = rest[end];
      if (quoted) {
        quoted = ch !== "'";
      } else if (ch === "'") {
        quoted = true;
      } else if (ch === '(') {
        depth += 1;
      } else if (ch === ')') {
        depth -= 1;
      }
      end += 1;
    }
    const values = splitValues(rest.slice(from, end - 1));
    if (values.length !== columns.length) {
      throw new Error(
        `could not parse an INSERT INTO THE.${table}: ${columns.length} column(s) but `
          + `${values.length} value(s). Teach parseInserts about the statement rather than leaving it `
          + `unread — an unparsed row is an anchor this gate believes is missing. Values: ${values.join(' | ')}`,
      );
    }
    rows.push(Object.fromEntries(columns.map((c, i) => [c, unwrap(values[i])])));
  }
  return rows;
}

function sqlFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...sqlFiles(full));
    } else if (entry.name.toLowerCase().endsWith('.sql')) {
      out.push(full);
    }
  }
  return out;
}

/** All migration SQL the e2e Flyway chain applies: `db/` (versioned + repeatable) then `db-e2e/`. */
function readMigrations(): { base: string; e2eOnly: string; all: string } {
  const read = (dir: string): string =>
    sqlFiles(dir)
      .map((f) => fs.readFileSync(f, 'utf8'))
      .join('\n');
  const base = read(DB_DIR);
  const e2eOnly = read(DB_E2E_DIR);
  return { base, e2eOnly, all: `${base}\n${e2eOnly}` };
}

const statusKeys = (sql: string): Map<AnchorKey, string> =>
  new Map(
    parseInserts(sql, 'ILCR_MILL_REPORT_STATUS').map((r) => [
      `${r.ILCR_MILL_ID}/${r.REPORT_YEAR}`,
      `${r.ILCR_MILL_REPORT_STATUS_CODE ?? '-'}/${r.MILL_SILVICULTUR_STATUS_CODE ?? '-'}`,
    ]),
  );

// ---------------------------------------------------------------------------------------------------
// The checks
// ---------------------------------------------------------------------------------------------------

test('seed parity: the scan sees every domain, and the SQL parser still binds', async () => {
  // Assert the INPUTS before asserting the property — silent under-scanning is how the sibling guard
  // passed while blind (VER-8), and it is the only failure mode this file cannot report on itself.
  const files = fixtureFiles(FIXTURES_DIR);
  const keys = collectAnchorKeys(FIXTURES_DIR);

  expect(
    files.length,
    `scanned ${files.length} fixture file(s): ${files.map((f) => f.domain).join(', ')}`,
  ).toBeGreaterThanOrEqual(6);
  expect(
    keys.size,
    `the fixture scan found ${keys.size} (mill, year) keys, which is below the ${MIN_FIXTURE_KEYS} floor `
      + '— it has stopped matching one of the three anchor shapes (see preflight/anchor-keys.ts)',
  ).toBeGreaterThanOrEqual(MIN_FIXTURE_KEYS);

  // Every domain must contribute keys. A fixture that was renamed or restructured otherwise reads as a
  // domain with nothing pinned, which passes every check below.
  const silent = files.filter((f) => scanAnchorKeys(fs.readFileSync(f.file, 'utf8')).length === 0);
  expect(
    silent.map((f) => f.domain),
    'these domains contributed NO anchor keys — their fixture no longer declares anchors in a shape '
      + 'anchor-keys.ts recognises, so nothing about them is being checked',
  ).toEqual([]);

  const seeded = statusKeys(readMigrations().all);
  expect(
    seeded.size,
    `parsed ${seeded.size} ILCR_MILL_REPORT_STATUS row(s), below the ${MIN_SEED_STATUS_ROWS} floor — the `
      + 'parser has stopped matching',
  ).toBeGreaterThanOrEqual(MIN_SEED_STATUS_ROWS);

  for (const probe of PARSER_PROBES) {
    expect(
      seeded.get(probe.key),
      `parser probe ${probe.key} should read ${probe.codes} — ${probe.why}`,
    ).toBe(probe.codes);
  }
});

test('seed parity: every pinned anchor is seeded, or listed as deliberately absent', async () => {
  const keys = collectAnchorKeys(FIXTURES_DIR);
  const seeded = statusKeys(readMigrations().all);

  const missing = [...keys.keys()]
    .filter((key) => !seeded.has(key) && !DELIBERATELY_ABSENT.has(key))
    .sort(byMillThenYear)
    .map((key) => `${key} (pinned by ${keys.get(key)!.join(', ')})`);

  expect(
    missing,
    'these pinned anchors have NO report-status row in the migration chain, so they 404 in CI while '
      + `passing locally against the patched extract: ${missing.join('; ')}.\n${FIX_IT}`,
  ).toEqual([]);
});

test('seed parity: every deliberately-absent anchor really is absent, and still used', async () => {
  const keys = collectAnchorKeys(FIXTURES_DIR);
  const seeded = statusKeys(readMigrations().all);

  // The half a set-difference cannot do. These anchors' whole purpose is the missing row; seeding one
  // makes its scenario pass for the wrong reason instead of failing.
  const wronglySeeded = [...DELIBERATELY_ABSENT.entries()]
    .filter(([key]) => seeded.has(key))
    .map(([key, why]) => `${key} is now seeded (${seeded.get(key)}) but must NOT be — ${why}`);

  expect(
    wronglySeeded,
    'a guard anchor has been given a report-status row. Its scenario no longer proves what it was '
      + `written to prove: ${wronglySeeded.join('; ')}`,
  ).toEqual([]);

  // And the list cannot rot: an exemption for an anchor no fixture pins any more is cover, not a rule.
  const dead = [...DELIBERATELY_ABSENT.keys()].filter((key) => !keys.has(key));
  expect(
    dead,
    `DELIBERATELY_ABSENT names anchors no fixture pins any more — delete these lines: ${dead.join(', ')}`,
  ).toEqual([]);
});

test('seed parity: every anchor mill and reporting year exists', async () => {
  const keys = collectAnchorKeys(FIXTURES_DIR);
  const { all } = readMigrations();

  const mills = new Set(parseInserts(all, 'MILL').map((r) => r.MILL_ID));
  const xref = new Set(parseInserts(all, 'ILCR_MILL_STATUS_XREF').map((r) => r.ILCR_MILL_STATUS_XREF_ID));
  const years = new Set(parseInserts(all, 'ILCR_REPORTING_PERIOD').map((r) => r.REPORT_YEAR));

  const anchorMills = [...new Set([...keys.keys()].map((k) => k.split('/')[0]))];
  const anchorYears = [...new Set([...keys.keys()].map((k) => k.split('/')[1]))];

  // A mill needs BOTH rows: MILL carries the number and name the Home dropdown option text asserts,
  // ILCR_MILL_STATUS_XREF carries ACT/CLS, which is what the 409 closed-mill guards turn on.
  const noMill = anchorMills.filter((id) => !mills.has(id)).sort((a, b) => Number(a) - Number(b));
  const noXref = anchorMills.filter((id) => !xref.has(id)).sort((a, b) => Number(a) - Number(b));
  expect(
    noMill,
    `these anchor mills have no THE.MILL row, so their Home option text cannot render: ${noMill.join(', ')}.\n${FIX_IT}`,
  ).toEqual([]);
  expect(
    noXref,
    'these anchor mills have no THE.ILCR_MILL_STATUS_XREF row, so they have no ACT/CLS status and the '
      + `closed-mill guards cannot resolve: ${noXref.join(', ')}.\n${FIX_IT}`,
  ).toEqual([]);

  // Home only offers the years in ILCR_REPORTING_PERIOD, so an anchor outside them is unselectable —
  // and the scenario fails on a dropdown that has no such option, which reads as a UI defect.
  const noYear = anchorYears.filter((y) => !years.has(y)).sort();
  expect(
    noYear,
    `these anchor years have no THE.ILCR_REPORTING_PERIOD row, so Home cannot offer them: ${noYear.join(', ')}.\n${FIX_IT}`,
  ).toEqual([]);
});

/**
 * The tables the e2e seed inserts with EXPLICIT primary keys, and the column carrying each.
 *
 * A duplicate here, or a collision with an id `db/` already claims, is `ORA-00001` at `flyway:migrate`
 * time — which takes the whole CI job down before a single test runs, with a message about a constraint
 * rather than about the row you just added. The backend's `FlywayMigrationConventionTest` states this
 * class as explicitly OUT of its scope ("Seed-data ID collisions (P2) … Governed only by the ID-range
 * registry … Nothing here covers it"), and the seed's ID CLAIMS header is that registry — hand-verified.
 * This is the machine half of it, and it matters most right after a transcription: the Schedule 3 fold-in
 * was 133 rows of hand-allocated ids.
 */
const EXPLICIT_ID_COLUMNS: Record<string, string> = {
  MILL: 'MILL_ID',
  ILCR_MILL_STATUS_XREF: 'ILCR_MILL_STATUS_XREF_ID',
  ILCR_REPORT_SUMMARY: 'ILCR_REPORT_SUMMARY_ID',
  ILCR_COST_REPORT_DETAIL: 'ILCR_COST_REPORT_DETAIL_ID',
  TRANSPORTATION_REPORT: 'TRANSPORTATION_REPORT_ID',
  BASIC_SILVICULTURE_REPORT: 'BASIC_SILVICULTURE_REPORT_ID',
  BIOGEOCLIMATIC_CATALOGUE: 'BIOGEOCLIMATIC_CATALOGUE_ID',
};

test('seed parity: the seed’s explicit ids are unique, unclaimed, and parented', async () => {
  const { base, e2eOnly } = readMigrations();
  const problems: string[] = [];
  let parsed = 0;

  for (const [table, idColumn] of Object.entries(EXPLICIT_ID_COLUMNS)) {
    const ids = parseInserts(e2eOnly, table)
      .map((r) => r[idColumn])
      .filter((v): v is string => v !== null && /^\d+$/.test(v));
    parsed += ids.length;

    // `Set.add` returns the SET, not a boolean, so the tempting `!seen.add(id)` is ALWAYS false and
    // detects nothing. This check shipped that way for one commit-less minute and was caught only by
    // running it against a deliberately duplicated id — which is why "prove it fails" is in the DoD.
    const seen = new Set<string>();
    const duplicated: string[] = [];
    for (const id of ids) {
      if (seen.has(id) && !duplicated.includes(id)) {
        duplicated.push(id);
      }
      seen.add(id);
    }
    if (duplicated.length > 0) {
      problems.push(`${table}.${idColumn} repeats ${duplicated.join(', ')} inside the e2e seed`);
    }

    const claimedByBase = new Set(parseInserts(base, table).map((r) => r[idColumn]));
    const collisions = [...new Set(ids)].filter((id) => claimedByBase.has(id));
    if (collisions.length > 0) {
      problems.push(`${table}.${idColumn} reuses ${collisions.join(', ')}, already claimed in db/`);
    }
  }

  // Parent references, which the Flyway test schema does NOT enforce for these two — an orphan detail
  // row inserts happily and then simply never appears in any response, so the scenario fails on a value
  // that "should be there" with nothing pointing at the cause.
  const summaries = new Set(
    parseInserts(e2eOnly, 'ILCR_REPORT_SUMMARY').map((r) => r.ILCR_REPORT_SUMMARY_ID),
  );
  const reports = new Set(
    parseInserts(e2eOnly, 'TRANSPORTATION_REPORT').map((r) => r.TRANSPORTATION_REPORT_ID),
  );
  const orphans = parseInserts(e2eOnly, 'ILCR_COST_REPORT_DETAIL')
    .filter((r) => {
      const summary = r.ILCR_REPORT_SUMMARY_ID;
      const report = r.TRANSPORTATION_REPORT_ID;
      if (summary !== null && summary !== undefined) return !summaries.has(summary);
      if (report !== null && report !== undefined) return !reports.has(report);
      return true; // neither parent named at all
    })
    .map((r) => `detail ${r.ILCR_COST_REPORT_DETAIL_ID}`);
  if (orphans.length > 0) {
    problems.push(`${orphans.length} detail row(s) have no parent in the seed: ${orphans.join(', ')}`);
  }

  expect(parsed, 'parsed no explicit ids at all — the parser is not matching').toBeGreaterThan(200);
  expect(problems, `${SEED} would fail at flyway:migrate or seed unreachable rows:\n${problems.join('\n')}`)
    .toEqual([]);
});

test('seed parity: every INSERT this gate cannot read is a known, harmless one', async () => {
  // The other half of "nothing is skipped silently": parseInserts recognises two unreadable SQL forms
  // and walks past them, so this asserts the set it walked past is exactly the enumerated one. A new
  // INSERT…SELECT into a parsed table — the form that would make this gate under-count without saying
  // so — fails here. Raised in review.
  const { all } = readMigrations();
  const source = all.replace(SQL_COMMENT, ' ');
  const found = new Map<string, number>();

  for (const table of Object.keys(EXPLICIT_ID_COLUMNS).concat([
    'ILCR_MILL_REPORT_STATUS',
    'ILCR_REPORTING_PERIOD',
  ])) {
    const start = new RegExp(`INSERT\\s+INTO\\s+THE\\.${table}(?![A-Z0-9_])`, 'gi');
    for (const match of source.matchAll(start)) {
      const rest = source.slice(match.index! + match[0].length);
      if (/^\s*\([^)]*\)\s*VALUES\s*\(/i.test(rest)) continue;
      const form = unreadableForm(rest);
      const key = `${table}:${form ?? 'UNCLASSIFIED'}`;
      found.set(key, (found.get(key) ?? 0) + 1);
    }
  }

  const unexpected = [...found.keys()].filter((k) => !KNOWN_UNREADABLE.has(k)).sort();
  expect(
    unexpected,
    'these INSERTs cannot be zipped by column name, so this gate would not see their rows, and they '
      + 'are not enumerated as harmless. Either teach parseInserts the form or add it to '
      + `KNOWN_UNREADABLE with the reason it cannot matter: ${unexpected.join(', ')}`,
  ).toEqual([]);

  // Shrink-only, like DELIBERATELY_ABSENT: an allowance for a statement that no longer exists is cover.
  const dead = [...KNOWN_UNREADABLE.keys()].filter((k) => !found.has(k)).sort();
  expect(
    dead,
    `KNOWN_UNREADABLE allows forms that are no longer on the tree — delete these: ${dead.join(', ')}`,
  ).toEqual([]);
});

test('seed parity: the e2e-only seed carries no anchor no fixture pins', async ({}, testInfo) => {
  // ADVISORY, not a failure. An unreferenced row is harmless headroom, and the sec domain leans on the
  // Home lists these rows populate. But a row left behind by a retired anchor is also how the seed
  // grows a state nobody can explain, so it is surfaced rather than ignored.
  const keys = collectAnchorKeys(FIXTURES_DIR);
  const orphans = [...statusKeys(readMigrations().e2eOnly).keys()]
    .filter((key) => !keys.has(key))
    .sort(byMillThenYear);

  if (orphans.length > 0) {
    const msg =
      `[preflight] ${SEED} seeds report-status rows for ${orphans.length} (mill, year) pair(s) that no `
      + `fixture pins: ${orphans.join(', ')}. Headroom is fine; a retired anchor's leftovers are not.`;
    console.warn(msg);
    testInfo.annotations.push({ type: 'warning', description: msg });
  }
});
