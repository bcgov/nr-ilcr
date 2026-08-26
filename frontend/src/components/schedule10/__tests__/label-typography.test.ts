import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over the TWO rules that fix #366, following the pattern
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
 * What it is for: #366 had two causes, and the second one is why the first fix did not close it.
 *
 *   1. `schedule10/index.scss` hand-copied four of the five properties of Carbon's `label-01` token
 *      and dropped `letter-spacing`. Fixed by including the token instead of transcribing it.
 *   2. `styles/index.scss` sizes EVERY `.cds--label` inside `.schedule-page` at 0.875rem. A
 *      hand-rolled <span> label is not a `.cds--label`, so it stayed at the token's 12px and read
 *      2px smaller than the six labels beside it — the difference AMB actually saw. Fixed by listing
 *      the class in that rule, alongside `.schedule-2__comments-label`, which was already there for
 *      the same reason.
 *
 * Cause 2 is the one a reader is most likely to undo, because the two halves live in different files
 * and neither looks incomplete on its own.
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

  test('the page-level label size rule still lists this class (#366 cause 2)', () => {
    // `.schedule-page` sets every `.cds--label` to 0.875rem, so on a schedule page a Carbon label is
    // 14px and the token's 12px is not what any neighbour renders at. A hand-rolled <span> has to be
    // named in that rule or it silently reads 2px smaller — which is the defect, and which the
    // schedule10 stylesheet alone cannot express.
    const appStyles = readFileSync(resolve(process.cwd(), 'src/styles/index.scss'), 'utf8')
    const at = appStyles.indexOf('.schedule-10__field-label,')
    expect(at, '.schedule-10__field-label is no longer in src/styles/index.scss').toBeGreaterThan(
      -1,
    )
    expect(
      appStyles.lastIndexOf('.schedule-page {', at),
      'the rule naming it is outside the .schedule-page block',
    ).toBeGreaterThan(-1)
    // The selector group it sits in has to be the one that SETS the size, not a neighbouring rule.
    // Matched forward from the class to the group's opening brace rather than by slicing to the next
    // `}`: the sibling selector is `.#{$bcgov-prefix}--label`, whose interpolation contains braces of
    // its own, so a naive brace scan stops inside the selector list and never sees the declaration.
    expect(appStyles).toMatch(/\.schedule-10__field-label,[\s\S]{0,200}?\{\s*font-size: 0\.875rem;/)
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
