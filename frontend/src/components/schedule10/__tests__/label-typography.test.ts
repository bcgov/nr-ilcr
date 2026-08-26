import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over the one rule that fixes #366, following the pattern
 * `styles/__tests__/overrides.test.ts` established for SCSS this suite cannot render, and the
 * Schedule 7A `layout-rules.test.ts` tripwire added by the defect #295 review.
 *
 * READ THIS BEFORE TRUSTING IT. It is not coverage of the defect:
 *
 *   - vitest runs in jsdom and `vitest.config.ts` sets `css: false`, so the stylesheet is never
 *     compiled or applied here. NOTHING in this repo can assert a computed letter-spacing. The 158
 *     Schedule 10 tests were green over #366 and would be green again if it came back.
 *   - It therefore asserts RULE TEXT. It fails if the token include is deleted, or the rule renamed,
 *     or the colour swapped to Carbon's `$text-secondary`. It cannot fail on a wrong rendered value.
 *
 * What it is for: #366 was caused by hand-copying four of the five properties of Carbon's `label-01`
 * token and dropping the fifth, so the whole fix is "stop transcribing the token". The cheapest thing
 * that fails loudly when someone transcribes it again is to assert the include is still there.
 * Parity is verified in a browser; the recipe is in
 * `_bmad-output/implementation-artifacts/styling-366-schedule10-label-typography.md` § Appendix.
 */
describe('Schedule 10 read-only label typography (source tripwire, not a behaviour test)', () => {
  const source = readFileSync(
    resolve(process.cwd(), 'src/components/schedule10/index.scss'),
    'utf8',
  )

  test('the read-only label takes its metrics from the label-01 token, not a hand-copy', () => {
    // Anchored on the selector, so a rename fails this rather than passing vacuously. The include is
    // what carries `letter-spacing: 0.32px` — the property the original transcription dropped, and
    // the only one that differed from the six Carbon labels beside it in the Add/Edit Page panel.
    expect(source).toMatch(/\.schedule-10__field-label\s*\{[^}]*@include type-style\('label-01'\)/)
    // A re-transcription would look like this. Either literal back inside the rule means the token is
    // being restated by hand again, which is the mistake, not the fix.
    const rule = /\.schedule-10__field-label\s*\{[^}]*\}/.exec(source)?.[0] ?? ''
    expect(rule, '.schedule-10__field-label rule not found').not.toBe('')
    expect(rule).not.toContain('font-size:')
    expect(rule).not.toContain('letter-spacing:')
  })

  test('the label keeps the PRIMARY colour, which Carbon itself does not use', () => {
    // Carbon ships `.cds--label { color: $text-secondary }`, but `styles/_overrides.scss` repaints
    // labels app-wide through `label.cds--label, legend.cds--label` — ELEMENT-qualified, so it reaches
    // the six real <label>s in this panel and cannot reach this <span>. Copying Carbon's own colour
    // here looks like the faithful fix and would put the mismatch straight back, in colour instead of
    // tracking. This assertion exists to stop that.
    expect(source).toMatch(/\.schedule-10__field-label\s*\{[^}]*color: var\(--cds-text-primary\)/)
  })
})
