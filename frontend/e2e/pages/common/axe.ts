import { type Page, expect } from '@playwright/test';
import { AxeBuilder } from '@axe-core/playwright';

/**
 * Accessibility gate shared by every domain (NFR1 / issue #74 AC4 / HOME-1.5 AC4). Runs axe-core against
 * the current page and asserts ZERO WCAG 2.1 A/AA violations. The tag set is wcag2a + wcag2aa +
 * **wcag21a + wcag21aa**: the 2.0 tags alone silently exclude the 2.1-only rules (autocomplete-valid,
 * label-content-name-mismatch, …), so a "2.1 AA" claim needs the 2.1 tags too (the same correction the
 * app team's Story 1.5 review applied). Any violation is printed (rule + impact + nodes + help URL) so a
 * real finding can be triaged with a recorded disposition rather than failing opaquely.
 */

const WCAG_2_1_AA_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

export async function assertNoA11yViolations(page: Page, label: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(WCAG_2_1_AA_TAGS).analyze();
  const { violations } = results;
  if (violations.length > 0) {
    const report = violations
      .map(
        (v) =>
          `  [${v.impact ?? 'n/a'}] ${v.id}: ${v.help}\n` +
          `      nodes: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}\n` +
          `      ${v.helpUrl}`,
      )
      .join('\n');
    // Surfaced in the test output/trace so BA/QA can triage each finding (NFR1 disposition).
    console.error(`axe WCAG 2.1 AA violations on ${label}:\n${report}`);
  }
  expect(
    violations.map((v) => v.id),
    `WCAG 2.1 AA violations on ${label} — fix, or record a disposition per NFR1`,
  ).toEqual([]);
}
