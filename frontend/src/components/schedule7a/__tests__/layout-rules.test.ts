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
describe('Schedule 7A layout rules (source tripwire, not a behaviour test)', () => {
  const source = readFileSync(
    resolve(process.cwd(), 'src/components/schedule7a/index.scss'),
    'utf8',
  )

  test('the accordion body keeps the width Carbon reserves for prose (#295 R1)', () => {
    // Carbon pads every accordion body 25% on the inline-end above 640px. A list row is 333px narrower
    // than the Add panel without this override.
    expect(source).toMatch(
      /\.schedule-7a__section\s+\.cds--accordion__content\s*\{[^}]*padding-inline-end:\s*1rem/,
    )
  })

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
    const fieldGrid = /\.schedule-7a__field-grid \{[\s\S]*?\n\}/.exec(source)?.[0] ?? ''
    expect(fieldGrid).not.toContain('@media')
  })

  test('every label/field wrapper gives its field track a zero minimum (#295 R5)', () => {
    // A bare `1fr` takes its minimum from the content, and a Carbon Dropdown's selected option is
    // `nowrap` — which is how a 49-character description grew the control over the field beside it.
    const bareOneFr = source.match(/grid-template-columns: var\(--s7a-label-col\) 1fr;/g)
    expect(bareOneFr, 'a label/field grid still uses a bare 1fr field track').toBeNull()
  })
})
