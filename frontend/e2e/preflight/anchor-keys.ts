import fs from 'fs';
import path from 'path';

/**
 * Reading (mill, year) anchor keys out of the fixture files — the ONE implementation, shared by every
 * guard that needs the union.
 *
 * WHY THIS IS A MODULE AND NOT A REGEX COPIED TWICE. Anchors are declared in several shapes, and a scan
 * that knows only some of them under-counts SILENTLY:
 *
 *  - Until 2026-08-24 the cross-domain guard in `sch4-anchors.setup.ts` matched only the object-literal
 *    forms, so it saw sch4's four guard anchors and none of its 48 table anchors, and none of sch3's 17
 *    either.
 *  - Both of the regexes it then used required `millId` and `year` to be ADJACENT properties. The `sec`
 *    fixture interleaves `millNumber` / `millName` between them, so all five of its anchors were
 *    invisible to that guard from the day it was written until 2026-08-28 — including 13/2017 and
 *    16050/2016, which sec shares with three other domains. Found by the per-domain non-emptiness
 *    assertion in `ci-seed-parity.setup.ts`, which is there precisely because an under-scanning guard
 *    reports success.
 *
 * Both are the dead-guard class `defects.md` VER-8 records, and a second consumer re-deriving these
 * patterns would reopen it — so both guards import from here.
 */

/** `${millId}/${year}` — how every guard, allow-list and failure message names an anchor. */
export type AnchorKey = string;

/** SQL/TS line and block comments, in one left-to-right alternation so whichever opens first wins. */
const COMMENT = /--[^\n]*|\/\/[^\n]*|\/\*[\s\S]*?\*\//g;

/** `year: 2017`, but never the `Year` of `reportYear` / `millYear`. */
const YEAR_PROPERTY = /(?<![A-Za-z0-9_])year:\s*(\d+)/;

/**
 * Every anchor key a fixture file declares, in source order, duplicates included.
 *
 * Two shapes cover the tree:
 *
 *  1. An OBJECT LITERAL carrying both properties, in either order and NOT necessarily adjacent —
 *     `{ millId: 16050, year: 2015 }`, `{ year: 2015, millId: 16050 }`, and sec's
 *     `{ millId: 12050, millNumber: '987', millName: 'TURTLE DOVE', year: 2017, … }`. Each `millId` is
 *     paired with the `year` in its OWN enclosing braces rather than with whatever is nearest, so a
 *     list of sibling literals cannot cross-pair into a phantom anchor.
 *  2. The POSITIONAL builder sch3 and sch4 use for their anchor tables — `at(MILL_514, 16050, 2017, …)`.
 *     The `\s*` is load-bearing: several entries are wrapped across lines
 *     (`at(\n  MILL_987,\n  12050,\n  2015,`) and are invisible to a line-oriented match.
 */
export function scanAnchorKeys(content: string): AnchorKey[] {
  const source = content.replace(COMMENT, ' ');
  const keys: AnchorKey[] = [];

  for (const match of source.matchAll(/millId:\s*(\d+)/g)) {
    const year = enclosingBlock(source, match.index!).match(YEAR_PROPERTY);
    if (year) {
      keys.push(`${match[1]}/${year[1]}`);
    }
  }
  for (const match of source.matchAll(/at\(\s*MILL_\w+\s*,\s*(\d+)\s*,\s*(\d{4})/g)) {
    keys.push(`${match[1]}/${match[2]}`);
  }
  return keys;
}

/**
 * The innermost `{ … }` containing `at`, or the whole source if the braces do not resolve.
 *
 * Falling back to the whole source rather than to an empty string is deliberate: an over-wide block can
 * only mis-pair (which the per-domain and cross-domain guards then surface as an unexplained key),
 * whereas an empty one drops the anchor and reports nothing at all.
 */
function enclosingBlock(source: string, at: number): string {
  let depth = 0;
  let open = -1;
  for (let i = at; i >= 0; i -= 1) {
    if (source[i] === '}') {
      depth += 1;
    } else if (source[i] === '{') {
      if (depth === 0) {
        open = i;
        break;
      }
      depth -= 1;
    }
  }
  if (open < 0) {
    return source;
  }
  depth = 0;
  for (let i = open; i < source.length; i += 1) {
    if (source[i] === '{') {
      depth += 1;
    } else if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(open, i + 1);
      }
    }
  }
  return source.slice(open);
}

/**
 * The one `*-test-data.ts` per domain directory, discovered by EXTENSION.
 *
 * Throws when a domain has none rather than skipping it: deriving the filename from the directory name
 * is what made the cross-domain guard scan a single file and pass vacuously (VER-8). A domain that
 * cannot be scanned must stop the run, not be quietly dropped.
 */
export function fixtureFiles(fixturesDir: string): { domain: string; file: string }[] {
  const domains = fs
    .readdirSync(fixturesDir, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name !== 'common');

  return domains.map((d) => {
    const dir = path.join(fixturesDir, d.name);
    const files = fs.readdirSync(dir).filter((n) => n.endsWith('-test-data.ts'));
    if (files.length === 0) {
      throw new Error(
        `fixtures/${d.name} contains no *-test-data.ts — an anchor guard cannot scan it, so it would `
          + 'pass without checking that domain. Add the fixture or remove the directory.',
      );
    }
    // Throwing on TWO matters as much as throwing on zero, and was missed until 2026-08-28 (raised in
    // review). `const [file] = …` silently took the first, so a domain that split its
    // fixtures would have every anchor in the second file invisible to both guards — the same
    // "passes while blind" shape this module's header is about, one level down again. Scanning both
    // would also work; throwing is chosen because it forces a decision about which file is
    // authoritative rather than quietly unioning two.
    if (files.length > 1) {
      throw new Error(
        `fixtures/${d.name} contains ${files.length} *-test-data.ts files (${files.join(', ')}). The `
          + 'anchor guards assume one per domain and would silently scan only the first. Merge them, or '
          + 'teach fixtureFiles() to return all of them.',
      );
    }
    return { domain: d.name, file: path.join(dir, files[0]) };
  });
}

/** Every anchor key across every domain fixture → the domains that declare it. */
export function collectAnchorKeys(fixturesDir: string): Map<AnchorKey, string[]> {
  const byKey = new Map<AnchorKey, string[]>();
  for (const { domain, file } of fixtureFiles(fixturesDir)) {
    for (const key of scanAnchorKeys(fs.readFileSync(file, 'utf8'))) {
      const domains = byKey.get(key) ?? [];
      if (!domains.includes(domain)) {
        domains.push(domain);
      }
      byKey.set(key, domains);
    }
  }
  return byKey;
}

/** Sorts keys the way a human reads them (mill ascending, then year), for stable messages. */
export function byMillThenYear(a: AnchorKey, b: AnchorKey): number {
  const [am, ay] = a.split('/').map(Number);
  const [bm, by] = b.split('/').map(Number);
  return am - bm || ay - by;
}
