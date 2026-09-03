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
  // Sch1's in-table summaries (Total Silviculture 140, Subtotal Company Logging 144, Subtotal Other
  // Costs) — the lighter tier #411 Overall 5 added beneath the grand total above.
  {
    file: 'schedule1/index.scss',
    selector: 'tr.schedule-1__subtotal-row td',
    label: 'Sch1 subtotals',
  },
  // Sch2: the band moved OFF `.schedule-2__section-start` in #411 Overall 5 — that selector is now a
  // group divider only (a heavier top border, no shading, no weight), so it is deliberately absent
  // here. Shading follows the row being calculated instead, in these two tiers.
  {
    file: 'schedule2/index.scss',
    selector: '.schedule-2__subtotal-row td',
    label: 'Sch2 subtotals',
  },
  {
    file: 'schedule2/index.scss',
    selector: '.schedule-2__total-row td',
    label: 'Sch2 grand total',
  },
  // Sch3's four derived rows, split by tier: Total Costs is the final figure, the rest intermediate.
  {
    file: 'schedule3/index.scss',
    selector: '.schedule-3__subtotal-row td',
    label: 'Sch3 subtotals',
  },
  {
    file: 'schedule3/index.scss',
    selector: '.schedule-3__total-row td',
    label: 'Sch3 total costs',
  },
  { file: 'schedule4/index.scss', selector: '.schedule-4__totals-row td', label: 'Sch4 totals' },
  // Sch5 section HEADER: the whole row, label AND the repeated Volume/Cost/$ captions. Was
  // `td:first-child` until #411 recognised those captions as this grid's column labels — legacy
  // repeats the column headers per section rather than heading the table once — and column labels are
  // bold app-wide. No band — a header is not a calculated row.
  {
    file: 'schedule5/index.scss',
    selector: '.schedule-5__section-row td',
    label: 'Sch5 section label and column captions',
  },
  // Sch5's actual calculated rows, which carry the band (PR #381 review). Camp and Access is the
  // schedule's final figure and took the darker tier in #411 Overall 5; the other three stay lighter.
  {
    file: 'schedule5/index.scss',
    selector: '.schedule-5__derived-row td',
    label: 'Sch5 derived subtotals',
  },
  {
    file: 'schedule5/index.scss',
    selector: '.schedule-5__total-row td',
    label: 'Sch5 camp and access total',
  },
  // Now `&__totals td`, not `&__totals`: #411 Overall 5 gave this row the band it was missing, and a
  // background belongs on the cells (Carbon paints td backgrounds, which would cover a tr's).
  { file: 'schedule5SubPage/index.scss', selector: '&__totals td', label: 'Sch5 sub-page totals' },
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
