import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { join } from 'node:path'
import { LARGE_VIEWPORT_QUERY } from '@/context/layout/LayoutProvider'

/**
 * Source-level tripwire for the side-nav style contract.
 *
 * Vitest runs with `css: false`, so no test in this repo evaluates the layout CSS. That leaves three
 * hand-copied couplings between `Layout/index.scss` and Carbon with nothing watching them — and the
 * failure mode is silent: the page and the nav panel drift apart, or the inset escapes its breakpoint
 * and shoves mobile content off-screen, with a fully green suite. This reads the sources and pins
 * them. It is not a substitute for a browser check; it is the thing that fails on a Carbon upgrade.
 */
const require_ = createRequire(import.meta.url)

const carbonSideNavScss = readFileSync(
  require_.resolve('@carbon/styles/scss/components/ui-shell/side-nav/_side-nav.scss'),
  'utf8',
)
const carbonGridConfigScss = readFileSync(
  require_.resolve('@carbon/grid/scss/_config.scss'),
  'utf8',
)
// Resolved from the project root: under the jsdom environment `import.meta.url` is not a file: URL.
const layoutScss = readFileSync(join(process.cwd(), 'src/components/Layout/index.scss'), 'utf8')

describe('side-nav style contract (Layout/index.scss vs Carbon)', () => {
  test('Carbon still sizes the expanded panel at mini-units(32) — our $side-nav-width mirrors it', () => {
    // `.cds--side-nav--ux { inline-size: mini-units(32) }` — 32 * 0.5rem = 16rem.
    expect(carbonSideNavScss).toMatch(/--side-nav--ux\s*\{[^}]*inline-size:\s*mini-units\(32\)/)
    expect(layoutScss).toMatch(/\$side-nav-width:\s*16rem;/)
  })

  test('Carbon still uses the transition timing our content inset copies', () => {
    expect(carbonSideNavScss).toContain('inline-size 0.11s cubic-bezier(0.2, 0, 1, 0.9)')
    expect(layoutScss).toMatch(/\$side-nav-transition:\s*0\.11s cubic-bezier\(0\.2, 0, 1, 0\.9\);/)
  })

  test('the inset rule stays inside a breakpoint(lg) block', () => {
    // Hoisting these two selectors out of the `@include breakpoint('lg')` block is a plausible
    // "simplification" that would push content and the fixed footer 16rem off-screen on every
    // viewport below 1056px, and every unit test would still pass.
    const insetBlock = layoutScss.match(
      /@include breakpoint\('lg'\)\s*\{[^{]*\{[^}]*margin-inline-start:\s*\$side-nav-width;[^}]*\}\s*\}/,
    )
    expect(insetBlock).not.toBeNull()
    expect(insetBlock?.[0]).toContain('.app-content--nav-expanded')
    expect(insetBlock?.[0]).toContain('.app-footer--nav-expanded')
  })

  test("the provider's JS breakpoint matches Carbon's lg width and the SCSS block", () => {
    // Carbon: `lg: (... width: convert.to-rem(1056px))` → 66rem. If IBM moves `lg`, this fails here
    // rather than as a mystery 16rem overlap at some width nobody tested.
    expect(carbonGridConfigScss).toMatch(/\blg:\s*\([\s\S]*?width:\s*convert\.to-rem\(1056px\)/)
    expect(LARGE_VIEWPORT_QUERY).toBe('(min-width: 66rem)')
    expect(layoutScss).toContain("@include breakpoint('lg')")
  })

  test("reduced motion zeroes BOTH our transition and Carbon's panel", () => {
    const reducedMotion = layoutScss.match(
      /@media \(prefers-reduced-motion: reduce\)\s*\{[^}]*\{[^}]*\}\s*\}/,
    )
    expect(reducedMotion).not.toBeNull()
    for (const selector of ['.app-content', '.app-footer', '.cds--side-nav']) {
      expect(reducedMotion?.[0]).toContain(selector)
    }
  })
})
