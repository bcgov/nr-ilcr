import { Then, When, expect } from '../fixtures';
import { CLIENT } from '../../fixtures/sch4/schedule4-test-data';

/**
 * Schedule 4 Check Status steps (BR-07 / EF3 / SUC-005 / SUC-006).
 *
 * Asserted entirely through the UI — the rendered notifications — because what matters is what the
 * reporter is shown. The endpoint's own contract (MET/ISSUES, per-location breakdown, mutates-nothing) is
 * already covered by the backend's `Schedule4CheckStatusIT`; re-proving it from here would test the
 * server twice and the screen not at all.
 *
 * The per-location shape the app renders (confirmed against the running app):
 *   - a location that passes  → a success notification titled "Check Status" with SUC-005's
 *     "All requirements for <name> have been met."
 *   - a location that fails   → one warning notification PER missing field, titled "<name> — required"
 *     with the subtitle "<field>: Value Required" (EF3 / `missingRequiredFieldMsg`, field named per #326)
 *   - the whole schedule      → SUC-006's banner, ONLY when every location passes
 *
 * Since issue #465 (legacy parity) the only field the check can fail is the location description, which
 * the save refuses to store blank — so no scenario can produce the failing shape, and the steps below
 * assert the passing one plus the ABSENCE of any "required" banner.
 */

When('I check Schedule 4 status', async ({ schedule4Page }) => {
  await schedule4Page.clickCheckStatus();
});

Then(
  'the Schedule 4 check-status result for {string} is met',
  async ({ schedule4Page }, name) => {
    await expect(schedule4Page.notification(`All requirements for ${name} have been met.`)).toBeVisible();
  },
);

/**
 * No location is flagged — the assertion that pins #465: the states the old §Decision 1 rule flagged
 * (a Volume with no Cost, on a category or a sub-page row) must produce NO "<name> — required" banner
 * and no "Value Required" text anywhere in the Check Status output.
 */
Then('the Schedule 4 check-status shows no required-value issue', async ({ schedule4Page }) => {
  await expect(schedule4Page.notification(CLIENT.titleLocationRequiredSuffix)).toHaveCount(0);
  const messages = await schedule4Page.checkStatusMessages();
  expect(messages.join(' | ')).not.toContain(CLIENT.valueRequired);
});
