import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over the two rules that fix #366, following the pattern
 * `styles/__tests__/overrides.test.ts` established for SCSS this suite cannot render, and the
 * Schedule 7A `layout-rules.test.ts` tripwire added by the defect #295 review.
 *
 * READ THIS BEFORE TRUSTING IT.
 *
 *   - vitest runs in jsdom and `vitest.config.ts` sets `css: false`, so the stylesheet is never
 *     compiled or applied here. No assertion in this repo can read a computed letter-spacing, and
 *     the Schedule 10 suite was green over #366 from end to end.
 *   - So these assert RULE TEXT. They catch the rules being deleted, renamed, moved out of scope, or
 *     re-transcribed by hand. They cannot catch a wrong rendered value. Parity is measured in a
 *     browser against a real `vite build`; the recipe lives with the planning record for #366 (in
 *     the `ilcr-bmad` repo, which is not checked out beside this one — ask the team if you need it).
 *
 * The ONE thing here that is a real behaviour assertion lives elsewhere on purpose: the class is
 * pinned to the rendered DOM in `Schedule10.test.tsx` ("Road Group ... toHaveClass"). Both halves
 * are needed — everything below passes happily while the markup stops using the class at all.
 *
 * What it is for: #366 had two causes, and the second is why the first fix did not close it.
 *
 *   1. `schedule10/index.scss` restated Carbon's `label-01` token by hand. The token declares FOUR
 *      properties (font-size, font-weight, line-height, letter-spacing); the copy carried TWO of
 *      them and dropped `letter-spacing`, with `font-weight` matching only by inheritance. Fixed by
 *      including the token.
 *   2. `styles/index.scss` sizes every `.cds--label` inside `.schedule-page` at 0.875rem. A
 *      hand-rolled <span> is not a `.cds--label`, so it stayed at the token's 12px and read 2px
 *      smaller than the six labels beside it — the difference AMB actually reported. Fixed by
 *      listing the class in that rule, beside `.schedule-2__comments-label`, which was already
 *      there for the same reason.
 *
 * Cause 2 is the one a reader is most likely to undo, because the two halves live in different
 * files and neither looks incomplete on its own.
 */

/**
 * The body of every rule whose selector list names `selector`, at any nesting depth, found by
 * brace-tracking. Returns every match, not the first.
 *
 * Nesting-aware on purpose: both rules this file guards are nested — the size rule inside
 * `.schedule-page`, and Carbon's `.cds--label` inside its `form` mixin.
 *
 * Written rather than regex'd for two reasons the #366 code review proved by mutation, both of
 * which had shipped in the first version of this file:
 *
 *   - A non-greedy `\{[^}]*\}` truncates at the first `}` of a NESTED block, so adding an `@media`
 *     or `&:hover` to the rule let a hand-copied `font-size` be re-added below it invisibly.
 *   - `exec` returns only the first match, so a second, more specific rule for the same class later
 *     in the file — which would win on specificity — was never looked at.
 *
 * `#{...}` interpolation is skipped explicitly: `.#{$bcgov-prefix}--label` sits in one of the
 * selector lists this walks, and its braces are not block braces.
 */
const stripComments = (scss: string): string => scss.replace(/\/\/[^\n]*/g, '')

function ruleBodiesNaming(scss: string, selector: string): string[] {
  const source = stripComments(scss)
  const bodies: string[] = []
  const stack: { wanted: boolean; bodyStart: number }[] = []
  let preludeStart = 0

  for (let i = 0; i < source.length; i += 1) {
    // `#{...}` is interpolation, not a block — `.#{$bcgov-prefix}--label` sits in one of the
    // selector lists this walks, and Carbon's own rules are written the same way.
    if (source[i] === '#' && source[i + 1] === '{') {
      const close = source.indexOf('}', i)
      if (close === -1) break
      i = close
      continue
    }
    if (source[i] === '{') {
      const names = source.slice(preludeStart, i).split(',')
      stack.push({
        wanted: names.some((name) => name.trim() === selector),
        bodyStart: i + 1,
      })
      preludeStart = i + 1
    } else if (source[i] === '}') {
      const frame = stack.pop()
      if (frame?.wanted) bodies.push(source.slice(frame.bodyStart, i).trim())
      preludeStart = i + 1
    } else if (source[i] === ';') {
      preludeStart = i + 1
    }
  }

  return bodies
}

/** The offset one past the closing brace of the first block whose selector is `selector`. */
function blockEnd(scss: string, selector: string): number {
  const open = scss.indexOf(`${selector} {`)
  if (open === -1) return -1
  let depth = 0
  for (let i = scss.indexOf('{', open); i < scss.length; i += 1) {
    if (scss[i] === '#' && scss[i + 1] === '{') {
      i = scss.indexOf('}', i)
      if (i === -1) return -1
      continue
    }
    if (scss[i] === '{') depth += 1
    else if (scss[i] === '}') {
      depth -= 1
      if (depth === 0) return i + 1
    }
  }
  return -1
}

describe('Schedule 10 read-only label typography (source tripwire, not a behaviour test)', () => {
  const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')
  const routeStyles = read('src/components/schedule10/index.scss')
  const appStyles = read('src/styles/index.scss')

  test('the read-only label takes its metrics from the label-01 token, not a hand-copy', () => {
    // Counted before anything is matched, and counted by MENTION rather than by rule: a second rule
    // reaching this class under a descendant combinator (`.schedule-10__fields &`) wins on
    // specificity and would quietly re-set the size the token include is supposed to own. The
    // negative lookahead lets a `--modifier` class exist without tripping this.
    const mentions = stripComments(routeStyles).match(/\.schedule-10__field-label(?![\w-])/g) ?? []
    expect(
      mentions.length,
      'the class is named more than once in this stylesheet — the most specific rule wins, not this one',
    ).toBe(1)

    const rules = ruleBodiesNaming(routeStyles, '.schedule-10__field-label')
    // A rename must fail HERE rather than pass vacuously against an empty string further down.
    expect(rules, 'expected exactly one .schedule-10__field-label rule').toHaveLength(1)
    expect(rules[0]).toContain("@include type-style('label-01')")
  })

  test('no type property is restated by hand alongside the token', () => {
    // The whole point of #366's cause 1 is that a hand-copy looks right and drifts. So the rule's
    // declaration list is pinned exactly: anything added has to be justified by changing this test.
    //
    // `line-height` is in the permitted list and is LOAD-BEARING, not a restatement — Carbon's own
    // `.cds--label` overrides the token's unitless 1.33333 with a hard 1rem, and at the 14px this
    // label renders at on a `.schedule-page` route, 1.33333 would give 18.67px.
    const [rule] = ruleBodiesNaming(routeStyles, '.schedule-10__field-label')
    const properties = (rule.match(/^\s*[a-z-]+(?=:)/gm) ?? []).map((p) => p.trim())
    expect(properties).toEqual(['display', 'color', 'line-height', 'margin-block-end'])
    // Its VALUE is pinned too, not just its presence: retuning it is the same class of hand-tuned
    // metric the token include exists to prevent, and 1rem is what Carbon itself declares.
    expect(rule).toContain('line-height: 1rem')
  })

  test('the label keeps the PRIMARY colour, which Carbon itself does not use', () => {
    // Carbon ships `.cds--label { color: $text-secondary }`, but `styles/_overrides.scss` repaints
    // labels app-wide through `label.#{$bcgov-prefix}--label, legend...` — ELEMENT-qualified, so it
    // reaches the six real <label>s in this panel and cannot reach this <span>. Copying Carbon's own
    // colour here looks like the faithful fix and would put the mismatch back, in colour instead of
    // tracking. (Schedules 4 and 6 do use secondary for their read-only labels; this is a decision
    // local to a `.schedule-page` route, not an app-wide rule.)
    const [rule] = ruleBodiesNaming(routeStyles, '.schedule-10__field-label')
    expect(rule).toContain('color: var(--cds-text-primary)')
  })

  test('the page-level size rule lists this class, INSIDE the .schedule-page block', () => {
    // `.schedule-page` sets every `.cds--label` to 0.875rem, so on a schedule page a Carbon label is
    // 14px and the token's 12px is not what any neighbour renders at. A hand-rolled <span> has to be
    // named in that rule or it silently reads 2px smaller — which is the defect.
    //
    // Containment is brace-tracked, not inferred from ordering. The first version of this test used
    // `lastIndexOf('.schedule-page {', at) > -1`, which is true for any position after that block
    // OPENS — so moving the whole selector group out to file scope (the exact cause-2 regression,
    // and one that would also leak 14px labels onto every route) left it green. Three review layers
    // reproduced that independently.
    const end = blockEnd(appStyles, '.schedule-page')
    expect(end, '.schedule-page block not found in src/styles/index.scss').toBeGreaterThan(-1)

    const inside = appStyles.slice(0, end)
    const rules = ruleBodiesNaming(inside, '.schedule-10__field-label')
    expect(
      rules,
      'no rule inside .schedule-page names .schedule-10__field-label — the label is back at 12px',
    ).not.toHaveLength(0)
    // Matched on the declaration, tolerant of whitespace and of the class's position in the selector
    // list. The first version required a literal trailing comma and `{`-adjacency, so an alphabetical
    // sort or a formatter pass failed the build with a message accusing the developer of deleting
    // the rule — and the comment above it invites exactly that ("Add new ones to this list").
    expect(rules.some((body) => /font-size:\s*0\.875rem/.test(body))).toBe(true)
  })

  test('Carbon still overrides the token line-height the way this rule assumes', () => {
    // The parity invariant is with Carbon's `.cds--label`, and `@carbon/react` is on a caret range
    // under automated Renovate bumps. Nothing else in this repo reads Carbon's side of it: if Carbon
    // drops its `line-height: 1rem` override, the six real labels go to 1.33333 x 14px = 18.67px and
    // this span stays at 16px — #366 again, in leading, with a green suite. This fails the bump PR,
    // which is the only moment it can break.
    //
    // Reading node_modules from a test is unusual and deliberate; it is the dependency, not our code,
    // that this asserts. If Carbon restructures its SCSS this goes red without anything being wrong —
    // that is the intended direction of failure for a drift guard.
    const carbonForm = read('node_modules/@carbon/styles/scss/components/form/_form.scss')
    const [label] = ruleBodiesNaming(carbonForm, '.#{$prefix}--label')
    expect(
      label,
      'Carbon .cds--label rule not found — check the @carbon/styles layout',
    ).toBeDefined()
    expect(label).toContain("@include type-style('label-01')")
    expect(label).toContain('line-height: 1rem')
  })
})
