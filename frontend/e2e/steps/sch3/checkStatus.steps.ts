import { When, Then, expect } from '../fixtures';
import {
  CHECK_LABEL_CROWN_TIMBER,
  CHECK_LABEL_OA_TOTAL,
  CHECK_LABEL_POP_TIMBER,
  LINES,
  MSG_HARVEST_NOT_GE_POP,
  MSG_VALUE_REQUIRED,
  checkMessage,
} from '../../fixtures/sch3/schedule3-test-data';

/**
 * UC-SCH3-001 AF5 — Check Status (S09–S12). Kept in its own file because its assertions are about a
 * SET of rendered notifications rather than about the form, and because the "every mandatory field is
 * flagged" step needs the whole field inventory in one place.
 *
 * Check Status is asserted entirely through the UI: what matters is what the reporter is shown. Each
 * error renders as its own Carbon error notification whose subtitle is `<field label>: <message>`,
 * verbatim from the API (AD-8) — the labels are the legacy `checkStatusSchedule3.xhtml` wording, pinned
 * in the fixture.
 *
 * The endpoint mutates nothing by contract (AD-5), which is why every Check Status scenario can run on a
 * shared read-only anchor.
 */

When('I run Check Status on Schedule 3', async ({ schedule3Page }) => {
  await schedule3Page.checkStatus();
});

/**
 * Every mandatory value is missing on an empty schedule, so the check must flag all of them: the 11
 * Harvest amounts, the 8 PO&P amounts the check requires (Annual Rents, Scaling and Silviculture Admin
 * have none — BR-04), and both timber volumes. Asserted as a SET rather than one representative row,
 * because a check that silently stopped flagging one field is exactly the regression this catches.
 */
Then('every mandatory Schedule 3 field is flagged as required', async ({ page }) => {
  const expected: string[] = [];
  for (const line of LINES) {
    expected.push(checkMessage(`${line.label} (Harvest Total $)`, MSG_VALUE_REQUIRED));
    if (line.pop === 'entry') {
      expected.push(checkMessage(`${line.label} (PO&P $)`, MSG_VALUE_REQUIRED));
    }
  }
  expected.push(checkMessage(CHECK_LABEL_POP_TIMBER, MSG_VALUE_REQUIRED));
  expected.push(checkMessage(CHECK_LABEL_CROWN_TIMBER, MSG_VALUE_REQUIRED));

  for (const message of expected) {
    await expect(
      page.getByText(message, { exact: true }).first(),
      `Check Status did not flag "${message}"`,
    ).toBeVisible();
  }
});

/**
 * One named field flagged "Value Required". Parameterised by the VERBATIM backend label so a scenario can
 * name a specific field — used for the sub-page fields, whose missing-value states are only reachable
 * from seeded data (the Add panel refuses a blank description, and it always writes both rows of a
 * group).
 */
Then('the {string} field is flagged as required', async ({ page }, label) => {
  await expect(
    page.getByText(checkMessage(label, MSG_VALUE_REQUIRED), { exact: true }).first(),
    `Check Status did not flag "${label}" as required`,
  ).toBeVisible();
});

Then(
  'the {string} line is flagged as Harvest below PO&P',
  async ({ page }, label) => {
    await expect(
      page
        .getByText(checkMessage(`${label} (Harvest Total $)`, MSG_HARVEST_NOT_GE_POP), { exact: true })
        .first(),
    ).toBeVisible();
  },
);

Then('the other-acceptable subtotal is flagged as Harvest below PO&P', async ({ page }) => {
  await expect(
    page.getByText(checkMessage(CHECK_LABEL_OA_TOTAL, MSG_HARVEST_NOT_GE_POP), { exact: true }).first(),
  ).toBeVisible();
});

Then('the other-acceptable subtotal is not flagged as Harvest below PO&P', async ({ page }) => {
  // `toHaveCount(0)` rather than `not.toBeVisible()`: a message rendered anywhere in the DOM is a
  // failure, not just a visible one.
  await expect(
    page.getByText(checkMessage(CHECK_LABEL_OA_TOTAL, MSG_HARVEST_NOT_GE_POP), { exact: true }),
  ).toHaveCount(0);
});

Then(
  'the {string} line is not flagged as Harvest below PO&P',
  async ({ page }, label) => {
    await expect(
      page.getByText(checkMessage(`${label} (Harvest Total $)`, MSG_HARVEST_NOT_GE_POP), {
        exact: true,
      }),
    ).toHaveCount(0);
  },
);

Then('no Check Status errors are shown', async ({ page }) => {
  await expect(page.getByText(MSG_VALUE_REQUIRED, { exact: false })).toHaveCount(0);
  await expect(page.getByText(MSG_HARVEST_NOT_GE_POP, { exact: false })).toHaveCount(0);
});
