import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A tripwire over the shared Carbon overrides, added by the Story 11.3 code review (finding M10).
 *
 * The ComboBox menu-item rule is CROSS-CUTTING: Schedules 2, 4, 8, 9 and 10 all render through it.
 * Schedule 10's TSA descriptions were merely the first long enough to expose the latent bug — Carbon
 * pins BOTH the menu row and the option to `--cds-list-box-menu-item-height`, so an option allowed to
 * wrap rendered its second line outside that height and was clipped mid-word.
 *
 * It is asserted at SOURCE level on purpose, and this is a tripwire rather than a behaviour test:
 * jsdom does not compile or apply the SCSS, so no rendering test in this suite can observe the
 * computed height. What this can do is fail loudly if someone removes the `auto` block-size or the
 * floor that keeps single-line options unchanged — which is the regression that would silently
 * re-clip five schedules' dropdowns. Verifying it visually is Story 11.4's browser pass.
 */
describe('shared Carbon overrides: the ComboBox menu-item rule', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/styles/_overrides.scss'), 'utf8')

  test('lets a wrapped option grow, keeping the fixed height only as a floor', () => {
    // Both selectors must be freed: freeing the option alone leaves the ROW clipping it.
    expect(source).toMatch(
      /--list-box__menu-item,\s*\n\s*\.#\{[^}]*\}--list-box__menu-item__option/,
    )
    expect(source).toContain('block-size: auto')
    expect(source).toContain('min-block-size: var(--cds-list-box-menu-item-height, 2.5rem)')
  })

  test('the option wraps rather than truncating, with real vertical padding', () => {
    expect(source).toContain('white-space: normal')
    // Carbon centres a single line by line-height alone; wrapped text needs padding to sit inside
    // the row rather than against its borders.
    expect(source).toContain('padding-block: $spacing-03')
  })
})

/**
 * The side-nav close button (#411 Overall 8), matching nr-csp LayoutHeader/index.scss:22-32.
 *
 * Worth a tripwire because it REVERSES an earlier deliberate decision, and the comment explaining
 * that decision was itself the reason it looked wrong: Carbon's light active background was treated
 * as something to fight, so the override forced `background-color: transparent` plus a translucent
 * overlay to keep the glyph white in every state. CSP leans on that background instead and recolours
 * the glyph. Anyone reading only the old rationale would "fix" this straight back.
 */
describe('shared Carbon overrides: the side-nav close button', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/styles/_overrides.scss'), 'utf8')

  test('the open-state glyph takes the inverse layer token, not a literal', () => {
    // The token is what flips the X between themes on its own — #161616 on the light layer, #f4f4f4
    // on the dark. A hardcoded colour would be right in one theme and wrong in the other.
    expect(source).toMatch(
      /--header__action--active\s*\{\s*color:\s*var\(--cds-layer-selected-inverse\)/,
    )
  })

  test('nothing paints over Carbon active background again', () => {
    // The reverted rule's translucent overlay is what hid the white button. Re-adding it would take
    // the X back to white-on-blue while leaving the rule above in place, looking correct.
    expect(source).not.toContain('rgba(255, 255, 255, 0.16)')
  })
})
