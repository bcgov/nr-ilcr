import { Then, expect } from '../fixtures';
import { assertNoA11yViolations } from '../../pages/common/axe';

/**
 * Cross-domain assertion steps — no domain vocabulary, reusable by any feature. Domain-specific
 * assertions (banners, read-backs, field gating) live under steps/<domain>/.
 */

Then(
  'the {string} view has no WCAG 2.1 AA accessibility violations',
  async ({ page, $testInfo, $tags }, label) => {
    // An axe scan is CPU-heavy: it serialises the whole accessible tree and runs ~100 rules. A scenario
    // that scans TWICE (e.g. a page and then its open row editor) can exceed the 60 s default when
    // several copies run concurrently — observed 2026-08-10 as a timeout (never an assertion failure)
    // under `--repeat-each=3`, where 3 copies of each a11y scenario compete for 4 workers.
    //
    // Grant each scan its own headroom rather than raising the GLOBAL timeout: this keeps every
    // non-a11y scenario on the strict 60 s budget, so a genuine hang elsewhere still fails fast. Additive,
    // so a two-scan scenario gets twice the extension.
    $testInfo.setTimeout($testInfo.timeout + 60_000);
    // A scenario tagged `@discovered-bug` is a KNOWN, already-triaged red, so the helper logs one line
    // instead of dumping the full rule/node/help-URL block on every run — that dump reads like a fresh
    // emergency and buries any genuinely new finding next to it. The assertion is unchanged: the test still
    // fails, because the failing state is the tracking signal.
    await assertNoA11yViolations(page, label, { known: $tags.includes('@discovered-bug') });
  },
);

Then(
  'the {string} view has no WCAG 2.1 AA accessibility violations in its current pointer state',
  async ({ page, $testInfo, $tags }, label) => {
    // Same scan as above, but WITHOUT parking the pointer first — for a scenario that has deliberately
    // hovered something and is testing that state (see pages/common/axe.ts for why every other scan parks
    // it). Kept as its own phrase so a hover-dependent assertion can never be written by accident.
    $testInfo.setTimeout($testInfo.timeout + 60_000);
    await assertNoA11yViolations(page, label, {
      known: $tags.includes('@discovered-bug'),
      keepPointer: true,
    });
  },
);

Then('I should see the error {string}', async ({ page }, message) => {
  // .first(): some forms surface the same message in BOTH the error banner and the field's inline
  // text (2 matches) — asserting the message is visible somewhere is the intent, so avoid a strict-mode
  // violation on the legitimate duplicate.
  await expect(page.getByText(message).first()).toBeVisible();
});

Then('the error {string} is announced to assistive technology', async ({ page }, message) => {
  // WHY THIS EXISTS SEPARATELY FROM THE AXE SWEEP: axe checks the markup rules it knows about; it cannot
  // tell you that a dynamically-rendered error actually REACHES a screen reader. An app could satisfy axe
  // by dropping the offending `aria-errormessage` attribute entirely and the error would still be
  // announced to nobody — the scan would go green while the defect got worse. WCAG 4.1.3 needs the error
  // text inside a live region (`role="alert"`/`role="status"`, or `aria-live`), so that is asserted
  // directly rather than inferred.
  //
  // SOFT on purpose: this step is used in a `@discovered-bug` scenario that is expected RED (BUG-1 —
  // Carbon `TextInput` renders `invalidText` in a plain `div.cds--form-requirement` with no announcement
  // technique). A hard failure here would abort the scenario before its axe sweep runs, losing the sweep
  // that tracks the same defect from the other side. Soft keeps BOTH signals in one run; the scenario
  // still fails.
  const announced = page
    .locator('[role="alert"], [role="status"], [aria-live="polite"], [aria-live="assertive"]')
    .filter({ hasText: message });
  await expect
    .soft(
      announced.first(),
      `the error "${message}" must be rendered inside a live region (role="alert"/"status" or aria-live) so assistive technology announces it when it appears — visible red text alone is WCAG 4.1.3 failure`,
    )
    // Shorter than the 10 s global expect timeout: the caller has ALREADY asserted the error text is
    // visible, so the live region either wrapped it in the same render or does not exist. While BUG-1 is
    // open this assertion always exhausts its budget, and 10 s of that on every full run buys nothing.
    .toBeVisible({ timeout: 3_000 });
});

Then('I should see the message {string}', async ({ page }, message) => {
  // Generic visible-confirmation assertion (e.g. a success InlineNotification subtitle). .first() for
  // the same reason as above — a message may appear in more than one region.
  await expect(page.getByText(message).first()).toBeVisible();
});

Then('I should not see the message {string}', async ({ page }, message) => {
  // Absence assertion — for a message that is CONDITIONAL on a branch not taken (e.g. Schedule 11's
  // SUC-003 "requirements met", which must NOT appear alongside a Check Status failure). `toHaveCount(0)`
  // rather than `not.toBeVisible()` so a message rendered anywhere in the DOM fails, not just a visible one.
  await expect(page.getByText(message)).toHaveCount(0);
});

Then('I should see the warning {string}', async ({ page }, message) => {
  // A non-blocking warning notification (Carbon warning InlineNotification subtitle). .first() as above.
  await expect(page.getByText(message).first()).toBeVisible();
});

Then('I should be returned to {string}', async ({ page }, target) => {
  const re = new RegExp(`${target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`);
  await expect(page).toHaveURL(re);
});
