import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * Source-level tripwire for the Story 30.2 control-height contract (#312).
 *
 * Vitest runs with `css: false`, so nothing in this repo evaluates `_overrides.scss`. This pins the
 * SOURCED CSP/ISP heights so a silent revert (a Carbon token rename, or a "cleanup" that drops a
 * rule) fails here rather than only in a browser:
 *   - default fields + buttons = 48px (Carbon `lg` / 3rem) — CSP/ISP default (report-form-size mixin)
 *   - schedule data-table controls = 40px (Carbon `md` / 2.5rem) compact — CSP's R13 precedent
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
  declaredValue(scss, selector, /block-size:\s*([\d.]+)rem/)

/** The body of the `.cds--data-table` scope that compacts the dense schedule tables. */
const dataTable =
  rulesIn(SOURCE).find((rule) => rule.selectors.includes('.cds--data-table'))?.body ?? ''

describe('control-height contract (_overrides.scss / Story 30.2 / #312)', () => {
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

  test('schedule data-tables compact their controls to 40px (md / 2.5rem)', () => {
    expect(dataTable).not.toBe('')
    for (const control of [
      '.cds--text-input',
      '.cds--select-input',
      '.cds--list-box',
      '.cds--list-box__field',
      '.cds--search',
      '.cds--btn',
    ]) {
      expect(height(dataTable, control), control).toBe('2.5')
    }
    expect(blockSize(dataTable, '.cds--date-picker__input')).toBe('2.5')
  })
})
