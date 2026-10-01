import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A source tripwire over the on-band link colour (#411 Overall 5, added by the PR #414 E2E failure).
 *
 * WHY THIS EXISTS. Banding every calculated row put two greys behind label text that had only ever
 * been measured against white. Carbon's ghost button labels its text `$link-primary` (#0f62fe), which
 * clears WCAG 2.1 AA's 4.5:1 against pure white and nothing darker:
 *
 *     #0f62fe on #ffffff (no band) .................. 5.00:1  PASS
 *     #0f62fe on #f0f0f0 (--ilcr-band-subtotal) ..... 4.39:1  FAIL
 *     #0f62fe on #e0e0e0 (--ilcr-band-total) ........ 3.79:1  FAIL
 *
 * So the three ghost link-buttons that sit in banded rows — Sch1 "Subtotal Other Costs", Sch3
 * "Subtotal Other Costs" and "Included Unacceptable Costs" — turned the axe sweeps red the moment the
 * bands landed, with a `color-contrast` violation on each. `e2e/pages/common/axe.ts:66` had already
 * recorded this exact number (3.78:1) for Carbon's #e0e0e0 table HOVER layer under a ghost label; the
 * bands re-created it as a RESTING state, where the pointer-parking guard there cannot help.
 *
 * Blue 70 (`--cds-link-primary-hover`, #0043ce) clears both bands with room to spare — 6.84:1 on the
 * subtotal band, 5.90:1 on the total band — and stays a recognisable link blue.
 *
 * READ THIS BEFORE TRUSTING IT. Like `overrides.test.ts` and `summary-row-bold.test.ts`, this is NOT
 * behaviour coverage: vitest runs in jsdom with `css: false`, so no stylesheet is compiled and nothing
 * here can measure a real contrast ratio. The measurement lives in the axe sweeps
 * (`e2e/features/sch1|sch3/**\/accessibility.feature`). What this pins is the RULE TEXT, so deleting
 * the colour — or "tidying" it back to the default link token — fails in seconds here instead of
 * fifteen minutes into the E2E gate.
 */
const STYLES = resolve(process.cwd(), 'src/styles/_overrides.scss')
const COMPONENTS = resolve(process.cwd(), 'src/components')

/** Strip `//` line comments so the rationale above a rule can never satisfy an assertion. */
function readRules(path: string): string {
  return readFileSync(path, 'utf8').replace(/\/\/[^\n]*/g, '')
}

describe('on-band link colour is tokenised once (#411 Overall 5)', () => {
  test('_overrides.scss defines --ilcr-band-link alongside the two band tokens', () => {
    const source = readRules(STYLES)
    // Tokenised in the same `:root` as the bands themselves, for the reason that block already gives:
    // nine stylesheets each restating a value is how the band tiers drifted in the first place.
    expect(
      source,
      'the on-band link colour must be defined once in _overrides.scss, next to --ilcr-band-total/-subtotal',
    ).toMatch(/--ilcr-band-link:\s*var\(--cds-link-primary-hover\)/)
  })

  test('the token is a darker blue than the default link, not link-primary itself', () => {
    const source = readRules(STYLES)
    // The whole point is that #0f62fe fails on both bands. Pointing the token back at link-primary
    // would leave every rule below in place and reading correctly while restoring the violation.
    expect(source).not.toMatch(/--ilcr-band-link:\s*var\(--cds-link-primary\)/)
  })
})

// Each entry: the scss file, the exact selector whose block must carry the colour, and the label of
// the banded row the link sits in. Sch2/Sch5 bands hold plain text cells only, so they are absent by
// fact rather than by omission — add an entry here if a link ever lands on one of their rows.
const ON_BAND_LINKS: ReadonlyArray<{ file: string; selector: string; label: string }> = [
  {
    file: 'schedule1/index.scss',
    selector: '.schedule-1__other-costs-link.cds--btn',
    label: 'Sch1 Subtotal Other Costs (in .schedule-1__subtotal-row)',
  },
  {
    file: 'schedule3/index.scss',
    selector: '.schedule-3__link.cds--btn',
    label: 'Sch3 Subtotal Other Costs / Included Unacceptable Costs (in .schedule-3__subtotal-row)',
  },
]

/** Escape a selector for a RegExp, then allow flexible whitespace. Both blocks are brace-free. */
function selectorPattern(selector: string, declaration: string): RegExp {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+')
  return new RegExp(`${escaped}\\s*\\{[^}]*${declaration}`)
}

describe('ghost link-buttons on a banded row take the darker blue (#411 Overall 5)', () => {
  test.each(ON_BAND_LINKS)('$label takes --ilcr-band-link', ({ file, selector }) => {
    const source = readRules(resolve(COMPONENTS, file))
    expect(
      source,
      `${file}: "${selector}" must set color: var(--ilcr-band-link) — a ghost link on a band falls to ` +
        `4.39:1 (subtotal) or 3.79:1 (total) on Carbon's default #0f62fe, failing WCAG 2.1 AA and ` +
        `turning this schedule's axe sweep red`,
    ).toMatch(selectorPattern(selector, 'color:\\s*var\\(--ilcr-band-link\\)'))
  })
})
