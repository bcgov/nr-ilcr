import { Then, expect } from '../fixtures';
import { assertNoA11yViolations } from '../../pages/common/axe';

/**
 * Cross-domain assertion steps — no domain vocabulary, reusable by any feature. Domain-specific
 * assertions (banners, read-backs, field gating) live under steps/<domain>/.
 */

Then('the {string} view has no WCAG 2.1 AA accessibility violations', async ({ page }, label) => {
  await assertNoA11yViolations(page, label);
});

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

Then('I should see the warning {string}', async ({ page }, message) => {
  // A non-blocking warning notification (Carbon warning InlineNotification subtitle). .first() as above.
  await expect(page.getByText(message).first()).toBeVisible();
});

Then('I should be returned to {string}', async ({ page }, target) => {
  const re = new RegExp(`${target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`);
  await expect(page).toHaveURL(re);
});
