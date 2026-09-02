import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over the five load-bearing rules in the Schedule 7A stylesheet, added by the defect #295
 * code review. It follows the pattern `styles/__tests__/overrides.test.ts` established for the same
 * problem: SCSS this suite cannot render.
 *
 * READ THIS BEFORE TRUSTING IT. This is not coverage of the defect:
 *
 *   - vitest runs in jsdom, which implements no box model, and `vitest.config.ts` sets `css: false`, so
 *     the stylesheet is never compiled or applied here. Nothing in this repo can assert that a field is
 *     wide enough for its value.
 *   - It therefore asserts the RULE TEXT, not behaviour. It fails if someone deletes or renames one of
 *     these rules; it CANNOT fail on a wrong number. Change `65rem` to `20rem`, or the money floor to
 *     `1ch`, and this file stays green while #295 comes straight back.
 *
 * What it is for: #295 was a layout regression that reached production behind a green suite, its fix is
 * five rules that only work together, and the browser guard that proved the fix was withdrawn (see
 * `defect-295-schedule7a-field-widths.md` § D4 superseded). This is the cheapest thing that fails loudly
 * when one of the five disappears. Widths are verified by a human against the running app; the recipe is
 * in that record's Appendix.
 */
/**
 * Every TOP-LEVEL rule whose selector list names `selector`, with its body, found by tracking brace
 * depth. Replaces the non-greedy `/\.selector \{[\s\S]*?\n\}/` regex the #295 PR review flagged
 * (SScholefield, seconded by gpascucci).
 *
 * The review's stated failure — a nested rule truncating the match — does NOT reproduce as written,
 * and it is worth saying why rather than leaving the next reader to re-derive it. That regex ends on
 * `\n}`: a newline followed by a brace in COLUMN 0. A conventionally indented nested close (`  }`)
 * does not match, so the match runs on to the top-level close and an `@media` after it IS caught.
 * The regex was right — but right by accident, resting on Prettier's indentation rather than on
 * anything it states. Close a nested rule at column 0 and it silently goes green.
 *
 * Two holes it had that the review did not name, and that this closes with it:
 *
 *   - `?? ''` on no match meant a RENAME passed vacuously: `''.not.toContain('@media')` is true.
 *     A tripwire whose whole job is to fail when a rule disappears was the one thing that could not.
 *     The caller now asserts the count first.
 *   - `.schedule-7a__field-grid` names TWO rules in this stylesheet, and the regex only ever saw the
 *     standalone one. A viewport query added to the grouped
 *     `.schedule-7a__field-grid, .schedule-7a__cost-secondary { ... }` rule breaks the same
 *     container-driven reflow and went unchecked. Both are returned and both are asserted.
 *
 * Matching the selector as a WHOLE comma-separated entry is what makes that possible, and is also
 * why this is not the `indexOf(selector)` scan the review suggested: `indexOf` lands on the GROUPED
 * rule (it comes first in the file), which does not even contain the `grid-template-columns` this
 * test exists to guard — so that suggestion would have swapped one false-green for another.
 *
 * `//` comments are stripped before counting, since this file has one containing braces (`sm={4}`).
 * Deliberately not a CSS parser: it only has to be right about this file and obvious when it is not.
 */
function topLevelRulesNaming(scss: string, selector: string): string[] {
  const source = scss.replace(/\/\/[^\n]*/g, '')
  const rules: string[] = []
  let depth = 0
  let preludeStart = 0
  let ruleStart = -1

  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    if (char === '{') {
      if (depth === 0) {
        const names = source.slice(preludeStart, i).split(',')
        ruleStart = names.some((name) => name.trim() === selector) ? preludeStart : -1
      }
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        if (ruleStart !== -1) {
          rules.push(source.slice(ruleStart, i + 1).trim())
          ruleStart = -1
        }
        preludeStart = i + 1
      }
    }
  }

  return rules
}

describe('Schedule 7A layout rules (source tripwire, not a behaviour test)', () => {
  const source = readFileSync(
    resolve(process.cwd(), 'src/components/schedule7a/index.scss'),
    'utf8',
  )

  // The accordion-body gutter override (#295 R1) is no longer asserted here: #411 promoted the rule
  // to styles/_overrides.scss so Schedules 6, 7B and 9 stop losing the same 25%, and the tripwire
  // went with it — see 'accordion editors cap tighter than the tables, and reclaim Carbon 25% gutter'
  // in styles/__tests__/controlHeightContract.test.ts, which still pins it to Carbon's density token
  // rather than a literal. Re-asserting it against THIS file would only re-fail the move.

  test('the money inputs keep a floor, capped at their own track (#295 R2)', () => {
    // The floor stops a `1fr` track crushing a cost box to its padding; the `min(..., 100%)` cap stops
    // the floor itself overflowing a phone-width column.
    expect(source).toMatch(
      /\.schedule-7a__num\s+\.cds--text-input\s*\{[^}]*min-inline-size:\s*min\(/,
    )
    expect(source).toContain('min-inline-size: min(calc(11ch + 3.5rem), 100%)')
  })

  test('the editor columns are the NAMED query container the cost row asks for (#295 R4)', () => {
    // Both halves, or neither works: the container is declared on the Columns, the query names it. An
    // anonymous container would be silently orphaned by a rename.
    expect(source).toContain('container: bridge-editor / inline-size')
    expect(source).toMatch(/@container bridge-editor \(max-width: \d+(\.\d+)?rem\)/)
  })

  test('the cost row still stacks on a browser without container queries', () => {
    // Without this, a non-supporting UA keeps three fixed tracks and the money boxes go back to ~54px.
    expect(source).toMatch(
      /@supports not \(container-type: inline-size\)[\s\S]*?@media \(max-width: 66rem\)[\s\S]*?\.schedule-7a__cost-row/,
    )
  })

  test('the attribute grid reflows on its own width and is capped at three columns (#295 R3)', () => {
    // `auto-fit` alone gave 4 columns at 1920 and 8 at 3840, breaking legacy's four-rows-of-three
    // transcription shape; the `(100% - 6rem) / 3` term is what caps it. No viewport media query may
    // come back here — the width that shrinks this grid is its container's.
    expect(source).toContain(
      'grid-template-columns: repeat(auto-fit, minmax(max(min(24.5rem, 100%), (100% - 6rem) / 3), 1fr))',
    )
    // Brace-balanced, and asserted non-empty first: the regex this replaces degraded to '' on a
    // rename, and `''.not.toContain('@media')` passes. Both rules that name the grid are checked —
    // the grouped `.schedule-7a__field-grid, .schedule-7a__cost-secondary` one and this standalone
    // one — because a viewport query in either would break the same container-driven reflow.
    const fieldGridRules = topLevelRulesNaming(source, '.schedule-7a__field-grid')
    expect(
      fieldGridRules.length,
      'expected exactly the grouped and standalone .schedule-7a__field-grid rules: fewer means one ' +
        'was renamed or deleted, more means a third was added and should be checked here too',
    ).toBe(2)
    for (const rule of fieldGridRules) {
      expect(
        rule,
        'a .schedule-7a__field-grid rule reintroduced a viewport media query',
      ).not.toContain('@media')
    }
  })

  test('every label/field wrapper gives its field track a zero minimum (#295 R5)', () => {
    // A bare `1fr` takes its minimum from the content, and a Carbon Dropdown's selected option is
    // `nowrap` — which is how a 49-character description grew the control over the field beside it.
    const bareOneFr = source.match(/grid-template-columns: var\(--s7a-label-col\) 1fr;/g)
    expect(bareOneFr, 'a label/field grid still uses a bare 1fr field track').toBeNull()
  })
})
