import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

// Story 1.5 (AC4 / NFR1): ride axe on the Playwright suite. Run the WCAG 2.1 A + AA rulesets
// against the current page state and assert zero violations. Any violation is printed with its
// id/impact/help/affected-node count so a reviewer can triage it (and, per NFR1, either fix it or
// record a disposition in the story artifacts) rather than staring at an opaque failure.
//
// Tag note: axe-core tags WCAG 2.1-only rules (autocomplete-valid, label-content-name-mismatch,
// css-orientation-lock, avoid-inline-spacing) as wcag21a/wcag21aa — the wcag2a/wcag2aa tags alone
// would silently skip them, so all four tags are required to actually meet the 2.1 AA bar.
export async function expectNoA11yViolations(page: Page, context: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()

  if (results.violations.length > 0) {
    // eslint-disable-next-line no-console -- surfacing violation detail for triage is the point
    console.log(`\n[axe] ${context}: ${results.violations.length} WCAG 2.1 AA violation(s)`)
    for (const v of results.violations) {
      // eslint-disable-next-line no-console
      console.log(
        `  • [${v.impact ?? 'n/a'}] ${v.id} — ${v.help} (${v.nodes.length} node(s))\n    ${v.helpUrl}`,
      )
    }
  }

  expect(
    results.violations,
    `axe found WCAG 2.1 AA violations on "${context}" (see console output above for triage)`,
  ).toEqual([])
}
