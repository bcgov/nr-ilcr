import { type Page, expect } from '@playwright/test';
import { AxeBuilder } from '@axe-core/playwright';

/**
 * Accessibility gate shared by every domain (NFR1 / issue #74 AC4 / HOME-1.5 AC4). Runs axe-core against
 * the current page and asserts ZERO WCAG 2.1 A/AA violations. The tag set is wcag2a + wcag2aa +
 * **wcag21a + wcag21aa**: the 2.0 tags alone silently exclude the 2.1-only rules (autocomplete-valid,
 * label-content-name-mismatch, …), so a "2.1 AA" claim needs the 2.1 tags too (the same correction the
 * app team's Story 1.5 review applied). Any violation is printed (rule + impact + nodes + help URL) so a
 * real finding can be triaged with a recorded disposition rather than failing opaquely.
 *
 * ONE EXCEPTION to that printing: a scenario tagged `@discovered-bug` is a KNOWN, already-triaged red, so
 * dumping the full rule/node/help-URL block on every run is noise that reads like a fresh emergency. Those
 * pass `known: true` and get a single line instead. The assertion is unchanged — the test still fails,
 * because that failing state IS the tracking signal. Only the logging is quieter.
 *
 * That quiet path is scoped BY RULE ID, not by the tag alone. `known: true` used to silence every
 * violation found in that scan, so a second, unrelated defect appearing in the same state would have been
 * folded into the one-line summary and lost among an expected red. Now only `KNOWN_A11Y_RULES` are
 * summarised; anything else still gets the full triage dump even inside a `@discovered-bug` scenario.
 */

const WCAG_2_1_AA_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

/**
 * Rule ids for the app-wide accessibility defects already found, triaged and recorded, so a scan that
 * hits one of them can report it in a single line instead of a full dump.
 *
 * `aria-valid-attr-value` (impact: critical) — Carbon `TextInput`'s invalid state renders
 * `aria-errormessage` pointing at an element it never announces, so validation errors never reach
 * assistive technology. It is a `@carbon/react` wiring issue present in EVERY schedule page's
 * validation-error state, not a Schedule 11 fault; tracked in `deferred-work.md` and in
 * `features/sch11/uc-sch11-001-report-costs/defects.md` BUG-1. Remove the id here the moment the app-wide
 * fix lands — the scan then goes green on its own and the `@discovered-bug` tag comes off with it.
 */
export const KNOWN_A11Y_RULES: readonly string[] = ['aria-valid-attr-value'];

export interface A11yOptions {
  /**
   * True when the caller's scenario is a documented `@discovered-bug` red (see the UC's defects.md).
   * Only violations whose rule id is in `KNOWN_A11Y_RULES` are then logged quietly; a violation outside
   * that list is treated as a fresh finding and printed in full regardless.
   */
  known?: boolean;
}

export async function assertNoA11yViolations(
  page: Page,
  label: string,
  opts: A11yOptions = {},
): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(WCAG_2_1_AA_TAGS).analyze();
  const { violations } = results;
  // Split by rule id, not by the caller's tag: an expected red must never hide an unexpected one.
  const known = opts.known ? violations.filter((v) => KNOWN_A11Y_RULES.includes(v.id)) : [];
  const fresh = violations.filter((v) => !known.includes(v));

  if (known.length > 0) {
    // Already triaged: one line, no rule dump. The expect() below still fails.
    console.log(
      `axe: ${String(known.length)} KNOWN violation(s) on ${label} ` +
        `(${known.map((v) => v.id).join(', ')}) — expected RED, see this UC's defects.md.`,
    );
  }
  if (fresh.length > 0) {
    const report = fresh
      .map(
        (v) =>
          `  [${v.impact ?? 'n/a'}] ${v.id}: ${v.help}\n` +
          `      nodes: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}\n` +
          `      ${v.helpUrl}`,
      )
      .join('\n');
    // Surfaced in the test output/trace so BA/QA can triage each finding (NFR1 disposition). Reached even
    // in a `@discovered-bug` scenario when the rule is not one of the recorded ones.
    console.error(`axe WCAG 2.1 AA violations on ${label}:\n${report}`);
  }
  expect(
    violations.map((v) => v.id),
    fresh.length > 0
      ? `WCAG 2.1 AA violations on ${label} — fix, or record a disposition per NFR1` +
          (known.length > 0
            ? ` (NEW: ${fresh.map((v) => v.id).join(', ')}; the other ${String(known.length)} are already tracked)`
            : '')
      : `KNOWN WCAG violation(s) on ${label} — expected RED, tracked in this UC's defects.md`,
  ).toEqual([]);
}
