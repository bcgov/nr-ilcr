import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * Source-level tripwire for the control-height contract (Story 30.2 / #312, amended by #411).
 *
 * Vitest runs with `css: false`, so nothing in this repo evaluates `_overrides.scss`. This pins the
 * SOURCED CSP/ISP heights so a silent revert (a Carbon token rename, or a "cleanup" that drops a
 * rule) fails here rather than only in a browser:
 *   - every field + button = 48px (Carbon `lg` / 3rem) — CSP/ISP default (report-form-size mixin)
 *   - ONE height app-wide: no data-table compaction scope (#411 Overall 1 replaced the former 40px
 *     compact — the client asked for the taller CSP field on the dense schedule screens too)
 *   - Dropdown is pinned by `block-size` because Carbon hardcodes it and ignores the token
 *   - tables cap at 80rem rather than running the full screen width (#411 Overall 2)
 *
 * It PARSES the stylesheet rather than pattern-matching its text: comments are stripped, rules are
 * split on balanced braces, and a height is looked up as "the rule that lists this selector AND
 * declares this property". So the assertions survive selector-list reordering, rules moving relative
 * to one another, added comments/properties, and SCSS reformatting — only an actual change to the
 * contracted value fails. It is not a substitute for visual QA.
 *
 * Resolved from this file's own directory, not `process.cwd()`, so it passes wherever Vitest is
 * invoked from (repo root as well as `frontend/`).
 */
const SOURCE = readFileSync(join(import.meta.dirname, '../_overrides.scss'), 'utf8')
  // Strip comments first: prose mentioning a selector (`.cds--search-input`) must not be mistaken
  // for that selector being in a rule's selector list.
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/\/\/.*$/gm, '')
  // Flatten SCSS interpolations (`.#{variables.$bcgov-prefix}--btn` → `.cds--btn`) so the literal
  // `#{ }` braces don't confuse the brace-balancer; the prefix resolves to `cds`.
  .replace(/#\{[^}]*\}/g, 'cds')

interface Rule {
  /** The rule's selector list, split and trimmed. */
  selectors: string[]
  /** The rule's body, verbatim, including any nested rules. */
  body: string
}

/** Split one nesting level of SCSS into its rules, pairing each selector list with its body. */
function rulesIn(scss: string): Rule[] {
  const rules: Rule[] = []
  let depth = 0
  let selectorsFrom = 0
  let bodyFrom = 0
  for (let i = 0; i < scss.length; i++) {
    if (scss[i] === '{') {
      depth += 1
      if (depth === 1) bodyFrom = i
    } else if (scss[i] === '}') {
      depth -= 1
      if (depth === 0) {
        rules.push({
          selectors: scss
            .slice(selectorsFrom, bodyFrom)
            .split(',')
            .map((selector) => selector.trim())
            .filter(Boolean),
          body: scss.slice(bodyFrom + 1, i),
        })
        selectorsFrom = i + 1
      }
    }
  }
  return rules
}

/**
 * The value `property` is set to for `selector` at this nesting level — from the LAST rule that both
 * lists the selector and declares the property, which is the one the cascade actually applies.
 * `undefined` if no rule does, which fails the assertion with the selector named.
 */
function declaredValue(scss: string, selector: string, property: RegExp): string | undefined {
  const matches = rulesIn(scss).filter(
    (rule) => rule.selectors.includes(selector) && property.test(rule.body),
  )
  return matches.at(-1)?.body.match(property)?.[1]
}

const height = (scss: string, selector: string) =>
  declaredValue(scss, selector, /--cds-layout-size-height-local:\s*([\d.]+)rem/)
const blockSize = (scss: string, selector: string) =>
  declaredValue(scss, selector, /[^-]block-size:\s*([\d.]+)rem/)
const maxInlineSize = (scss: string, selector: string) =>
  declaredValue(scss, selector, /max-inline-size:\s*([\d.]+)rem/)

/**
 * The CSS variable `property` reads for `selector` — `block-size: var(--x)` yields `--x`. Separate
 * from {@link blockSize} because the Dropdown rule pins itself to the height TOKEN rather than
 * restating a rem literal, so the two stay in step if the app standard changes.
 */
const pinnedToVar = (scss: string, selector: string, property: RegExp) =>
  declaredValue(scss, selector, property)

/** Every height the stylesheet declares, in source order, whatever selector it sits under. */
const declaredHeights = [...SOURCE.matchAll(/--cds-layout-size-height-local:\s*([\d.]+)rem/g)].map(
  (match) => match[1],
)

describe('control-height contract (_overrides.scss / Story 30.2 / #312, amended by #411)', () => {
  test('buttons default to 48px (lg / 3rem)', () => {
    expect(height(SOURCE, '.cds--btn')).toBe('3')
  })

  test('single-line fields default to 48px (lg / 3rem)', () => {
    for (const field of [
      '.cds--text-input',
      '.cds--select-input',
      '.cds--list-box',
      '.cds--list-box__field',
      '.cds--search',
    ]) {
      expect(height(SOURCE, field), field).toBe('3')
    }
  })

  test('DatePicker default block-size is 48px (3rem)', () => {
    // Carbon's DatePicker ignores the layout height token, so it is pinned by block-size instead.
    expect(blockSize(SOURCE, '.cds--date-picker__input')).toBe('3')
  })

  test('one height app-wide — no rule declares a second control height', () => {
    // #411 Overall 1: the client asked for the taller CSP field on the dense schedule screens too,
    // so the former `.cds--data-table` 40px scope is gone and a table control inherits the 48px
    // default like any other. Asserted over EVERY declaration rather than the absence of one
    // selector, so re-introducing two heights fails here however it is scoped.
    expect(declaredHeights).not.toHaveLength(0)
    expect([...new Set(declaredHeights)]).toEqual(['3'])
  })

  test('Dropdown is pinned to the height token, since Carbon hardcodes its block-size', () => {
    // `.cds--dropdown` hardcodes `block-size: 2.5rem` (and `--sm` 2rem) and never reads the layout
    // token, winning on source order over the `.cds--list-box` rule above — so without this rule a
    // Dropdown renders 40px (32px at `size="sm"`) beside its 48px neighbours. Assert it reads the
    // token rather than a literal so it follows the app standard if that value ever changes.
    const selector = '.cds--list-box.cds--dropdown'
    expect(pinnedToVar(SOURCE, selector, /[^-]block-size:\s*var\(\s*(--[\w-]+)\s*\)/)).toBe(
      '--cds-layout-size-height-local',
    )
    expect(pinnedToVar(SOURCE, selector, /max-block-size:\s*var\(\s*(--[\w-]+)\s*\)/)).toBe(
      '--cds-layout-size-height-local',
    )
  })

  test('tables cap at 80rem instead of running the full screen width', () => {
    // #411 Overall 2. Capped on the container so a toolbar is bounded too, and in rem so the cap
    // does not bind on the narrow screens where a dense schedule needs every pixel — a percentage
    // would shrink hardest exactly there.
    expect(maxInlineSize(SOURCE, '.cds--data-table-container')).toBe('80')
  })

  test('accordion editors cap tighter than the tables, and reclaim Carbon 25% gutter', () => {
    // #411: the accordion record editors (Schedules 6, 7A, 7B, 9) hold a three-across field grid
    // rather than a data grid, so they cap below the tables. The gutter override is what makes that
    // cap usable — Carbon reserves 25% of every accordion body above 640px, so a capped-but-guttered
    // editor would still leave a quarter of its narrower box empty.
    expect(maxInlineSize(SOURCE, '.cds--accordion')).toBe('64')
    // The paginator is part of the same block — rendered above and below each list — so it takes the
    // accordion's width, not the screen's. Asserted separately: capping one and not the other is the
    // regression, and a shared selector list makes that easy to undo by half.
    expect(maxInlineSize(SOURCE, '.cds--pagination')).toBe('64')
    expect(
      declaredValue(SOURCE, '.cds--accordion__content', /padding-inline-end:\s*var\(\s*(--[\w-]+)/),
    ).toBe('--cds-layout-density-padding-inline-local')
  })

  test('the two summary bands are defined once, and the subtotal is the lighter of them', () => {
    // #411 Overall 5. Nine stylesheets each restated one of these two values before, which is how the
    // same grey came to mean "grand total" on one schedule and "intermediate subtotal" on another.
    // The subtotal band must stay a MIX of the total band with the page background — that is what
    // makes it lighter in both themes, and collapsing it to a flat colour is the regression here.
    const root = rulesIn(SOURCE).find((rule) => rule.selectors.includes(':root'))?.body ?? ''
    expect(root).toMatch(/--ilcr-band-total:\s*var\(--cds-layer-accent-01\)/)
    expect(root).toMatch(
      /--ilcr-band-subtotal:\s*color-mix\([^)]*var\(--cds-layer-accent-01\)[^;]*var\(--cds-background\)/,
    )
  })

  test('tables pack to their content rather than stretching to fill', () => {
    // Carbon's `inline-size: 100%` is released so a table sizes to what it holds, as CSP does —
    // capping a stretched table only moves its slack inside the table. Pinned because the release is
    // what makes the cap read as narrowing rather than as reflow.
    expect(declaredValue(SOURCE, '.cds--data-table', /inline-size:\s*(auto)/)).toBe('auto')
  })
})
