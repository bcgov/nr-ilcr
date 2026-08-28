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
 * It reads the SCSS structurally rather than by loose regex: `topLevelRule()` finds a rule by its
 * (unindented) selector and returns the whole brace-balanced block, so the assertions are immune to
 * selector-list reordering, added comments/properties, and the compact `.cds--data-table` block
 * moving relative to the global rules. It is not a substitute for visual QA.
 */
const overrides = readFileSync(join(process.cwd(), 'src/styles/_overrides.scss'), 'utf8')
  // Flatten SCSS interpolations (`.#{variables.$bcgov-prefix}--btn` → `.cds--btn`) so the literal
  // `#{ }` braces don't confuse the brace-balancer in topLevelRule(); the prefix resolves to `cds`.
  .replace(/#\{[^}]*\}/g, 'cds')

/**
 * Return the brace-balanced `{ ... }` body of the first TOP-LEVEL rule whose selector line ends with
 * `selectorSuffix` (e.g. `--btn`). "Top-level" = the selector starts at column 0, which excludes the
 * same selector nested inside another block (e.g. `--btn` inside `.cds--data-table`). Empty if none.
 */
function topLevelRule(scss: string, selectorSuffix: string): string {
  const lines = scss.split('\n')
  const openLineIdx = lines.findIndex(
    (line) => /^\S/.test(line) && line.includes(`${selectorSuffix} {`),
  )
  if (openLineIdx === -1) {
    return ''
  }
  const from = scss.indexOf('{', scss.indexOf(lines[openLineIdx]))
  let depth = 0
  for (let i = from; i < scss.length; i++) {
    if (scss[i] === '{') depth++
    else if (scss[i] === '}') {
      depth--
      if (depth === 0) return scss.slice(from, i + 1)
    }
  }
  return ''
}

const HEIGHT_TOKEN = /--cds-layout-size-height-local:\s*([\d.]+)rem/
const BLOCK_SIZE = /block-size:\s*([\d.]+)rem/

describe('control-height contract (_overrides.scss / Story 30.2 / #312)', () => {
  test('buttons default to 48px (lg / 3rem)', () => {
    const block = topLevelRule(overrides, '--btn')
    expect(block).not.toBe('')
    expect(block.match(HEIGHT_TOKEN)?.[1]).toBe('3')
  })

  test('single-line fields default to 48px (lg / 3rem)', () => {
    // The global field-height rule is the selector group whose opening line ends with `--search {`.
    const block = topLevelRule(overrides, '--search')
    expect(block).not.toBe('')
    expect(block.match(HEIGHT_TOKEN)?.[1]).toBe('3')
  })

  test('DatePicker default block-size is 48px (3rem)', () => {
    const block = topLevelRule(overrides, '--date-picker__input')
    expect(block).not.toBe('')
    expect(block.match(BLOCK_SIZE)?.[1]).toBe('3')
  })

  test('schedule data-tables compact controls to 40px (md / 2.5rem)', () => {
    // The whole `.cds--data-table` block, brace-balanced — length/comments/order inside are irrelevant.
    const block = topLevelRule(overrides, '--data-table')
    expect(block).not.toBe('')
    expect(block.match(HEIGHT_TOKEN)?.[1]).toBe('2.5')
    expect(block.match(BLOCK_SIZE)?.[1]).toBe('2.5')
  })
})
