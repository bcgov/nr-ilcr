import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'

/**
 * A source tripwire over the "every shaded summary row is bold" rule (Story 30.5 / #312 Overall 5),
 * added by the 30.5 code review (2026-08-31). It follows the pattern of
 * `schedule7a/__tests__/layout-rules.test.ts` and `styles/__tests__/overrides.test.ts`.
 *
 * READ THIS BEFORE TRUSTING IT. This is NOT behaviour coverage:
 *   - vitest runs in jsdom and `vitest.config.ts` sets `css: false`, so no stylesheet is ever compiled
 *     or applied. Nothing here can assert a rendered font weight.
 *   - It asserts the RULE TEXT: each named summary-row selector's block must carry `font-weight: 700`.
 *     It fails loudly if a rule is deleted/renamed or its weight is lowered — the exact silent drift
 *     30.5 fixed (the rows were a mix of 400 / 600 / 700 before the pass).
 *
 * Scope: the rows this change touched AND the rows it deliberately relied on being 700 already, so the
 * "every shaded summary row is bold" baseline is pinned rather than assumed.
 */
const ROOT = resolve(process.cwd(), 'src/components')

// Each entry: the scss file, the exact selector that must carry the bold weight, and a human label.
// The blocks contain no nested braces, so a `{[^}]*font-weight: 700}` match is exact.
const SUMMARY_ROWS: ReadonlyArray<{ file: string; selector: string; label: string }> = [
  // Rows this change set/raised to 700.
  {
    file: 'schedule1/index.scss',
    selector: 'tr.schedule-1__grand-total-row td',
    label: 'Sch1 grand total',
  },
  {
    file: 'schedule2/index.scss',
    selector: '.schedule-2__section-start td',
    label: 'Sch2 subtotal bands',
  },
  { file: 'schedule4/index.scss', selector: '.schedule-4__totals-row td', label: 'Sch4 totals' },
  // Sch5 section HEADER: bold on the label cell only (the row also holds the repeated
  // Volume/Cost/$ captions). No band — a header is not a calculated row.
  {
    file: 'schedule5/index.scss',
    selector: '.schedule-5__section-row td:first-child',
    label: 'Sch5 section label',
  },
  // Sch5's actual calculated rows, which carry the band (PR #381 review).
  {
    file: 'schedule5/index.scss',
    selector: '.schedule-5__derived-row td',
    label: 'Sch5 derived totals',
  },
  { file: 'schedule5SubPage/index.scss', selector: '&__totals', label: 'Sch5 sub-page totals' },
  { file: 'schedule8/index.scss', selector: '.schedule-8__totals-row td', label: 'Sch8 totals' },
  // Rows relied on as "already 700" — pin the baseline the story's completeness claim depends on.
  {
    file: 'schedule1OtherCosts/index.scss',
    selector: '.schedule-1-other-costs__totals td',
    label: 'Sch1 Other Costs totals',
  },
  {
    file: 'schedule3SubPage/index.scss',
    selector: '.schedule-3-sub__totals td',
    label: 'Sch3 sub-page totals',
  },
  { file: 'schedule11/index.scss', selector: '.schedule-11__totals td', label: 'Sch11 totals' },
  { file: 'core/SubPanel/index.scss', selector: '.sub-panel__title', label: 'Sub-panel title bar' },
]

// Escape a selector for use inside a RegExp, then allow flexible whitespace between tokens.
function selectorPattern(selector: string): RegExp {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+')
  return new RegExp(`${escaped}\\s*\\{[^}]*font-weight:\\s*700`)
}

describe('Shaded summary rows stay bold (source tripwire, not a behaviour test — Story 30.5 / #312 Overall 5)', () => {
  test.each(SUMMARY_ROWS)(
    '$label ($file) keeps font-weight: 700 on `$selector`',
    ({ file, selector }) => {
      const source = readFileSync(resolve(ROOT, file), 'utf8').replace(/\/\/[^\n]*/g, '')
      expect(
        source,
        `${file}: the summary-row rule "${selector}" must carry font-weight: 700 — a shaded total/subtotal ` +
          `row lost its bold, re-opening #312 Overall 5`,
      ).toMatch(selectorPattern(selector))
    },
  )
})
