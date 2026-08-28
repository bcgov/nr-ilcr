import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Source-level tripwire for the Story 30.2 control-height contract (#312).
 *
 * Vitest runs with `css: false`, so nothing in this repo evaluates `_overrides.scss`. This pins the
 * SOURCED CSP/ISP heights so a silent revert (a Carbon token rename, or a "cleanup" that drops a
 * rule) fails here rather than only in a browser:
 *   - default fields + buttons = 48px (Carbon `lg` / 3rem) — CSP/ISP default (report-form-size mixin)
 *   - schedule data-table controls = 40px (Carbon `md` / 2.5rem) compact — CSP's R13 precedent
 * It is not a substitute for visual QA; it is the thing that fails on an accidental revert.
 */
const overrides = readFileSync(join(process.cwd(), 'src/styles/_overrides.scss'), 'utf8')

describe('control-height contract (_overrides.scss / Story 30.2 / #312)', () => {
  test('buttons default to 48px (lg / 3rem)', () => {
    const btnBlock = overrides.match(/--btn \{[\s\S]*?\n\}/)
    expect(btnBlock).not.toBeNull()
    expect(btnBlock?.[0]).toMatch(/--cds-layout-size-height-local:\s*3rem;/)
  })

  test('single-line fields default to 48px (lg / 3rem)', () => {
    // The global field-height rule is the one selector group ending at `--search {`.
    expect(overrides).toMatch(/--search \{\s*--cds-layout-size-height-local:\s*3rem;\s*\}/)
  })

  test('DatePicker default block-size is 48px (3rem)', () => {
    expect(overrides).toMatch(/--date-picker__input \{\s*block-size:\s*3rem;\s*\}/)
  })

  test('schedule data-tables compact controls to 40px (md / 2.5rem)', () => {
    // Within the `.cds--data-table` scope, both the layout token and the DatePicker block-size drop
    // to 2.5rem so dense rows follow CSP's compact size while page-level controls stay 48px.
    expect(overrides).toMatch(
      /--data-table \{[\s\S]{0,600}--cds-layout-size-height-local:\s*2\.5rem;/,
    )
    expect(overrides).toMatch(/--data-table \{[\s\S]{0,700}block-size:\s*2\.5rem;/)
  })
})
