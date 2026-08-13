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

Then('I should see the error {string}', async ({ page }, message) => {
  // .first(): some forms surface the same message in BOTH the error banner and the field's inline
  // text (2 matches) — asserting the message is visible somewhere is the intent, so avoid a strict-mode
  // violation on the legitimate duplicate.
  await expect(page.getByText(message).first()).toBeVisible();
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
