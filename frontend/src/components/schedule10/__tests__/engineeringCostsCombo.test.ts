import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * `Includes Detailed Engineering Costs` is Yes or No, never blank: legacy's `pageDtlECIncludeCosts`
 * (`schedule10.xhtml:1500-1506`) lists `No` then `Yes` with no empty choice, and the column is NOT
 * NULL. Carbon's ComboBox shows a clear button whenever a value is selected, so the screen's
 * stylesheet hides it on this one control, and narrows the menu to the word and the chevron while its
 * cell keeps the 7.5rem the haul grid's alignment depends on (#440, PRs #495/#496).
 *
 * A source tripwire, like `styles/__tests__/overrides.test.ts`: vitest runs in jsdom with no CSS
 * compiled, so it pins the rule text, not the rendering.
 */
describe('Schedule 10 — the Detailed Engineering Costs menu', () => {
  const source = readFileSync(
    resolve(process.cwd(), 'src/components/schedule10/index.scss'),
    'utf8',
  )

  test('hides the clear button, so the value can only be Yes or No', () => {
    expect(source).toMatch(
      /\.schedule-10__haul-eng-combo \.cds--list-box__selection \{\s*display: none;\s*\}/,
    )
  })

  test('the cell keeps the 7.5rem the haul grid alignment was built on', () => {
    expect(source).toMatch(/\.schedule-10__haul-eng-cell \{\s*min-inline-size: 7\.5rem;\s*\}/)
  })

  test('the menu is narrowed to the word and the chevron', () => {
    expect(source).toMatch(/\.schedule-10__haul-eng-combo \{\s*inline-size: 5\.5rem;\s*\}/)
    expect(source).toMatch(
      /\.schedule-10__haul-eng-combo \.cds--list-box \.cds--text-input \{\s*padding-inline-end: 2\.75rem;\s*\}/,
    )
  })
})
