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
 *     with the subtitle "Value Required" (EF3 / `missingRequiredFieldMsg`)
 *   - the whole schedule      → SUC-006's banner, ONLY when every location passes
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

Then(
  'the Schedule 4 check-status result for {string} is not met',
  async ({ schedule4Page }, name) => {
    // The per-location "required" notification is titled with the location name, and its subtitle is the
    // verbatim bundle text — assert BOTH, so a title without its message (or vice versa) fails.
    const banner = schedule4Page.notification(CLIENT.titleLocationRequired(name));
    await expect(banner).toBeVisible();
    await expect(banner).toContainText(CLIENT.valueRequired);
    await expect(
      schedule4Page.notification(`All requirements for ${name} have been met.`),
    ).toHaveCount(0);
  },
);

Then(
  'the Schedule 4 check-status reports {int} required-value issues for {string}',
  async ({ schedule4Page }, count, name) => {
    await expect(schedule4Page.notification(CLIENT.titleLocationRequired(name))).toHaveCount(count);
  },
);

/**
 * DIV-2 in this UC's defects.md — the legacy Check Status message NAMED the field a value was
 * required for (`"Location : <name> - Lakeside Dry Dump (Cost $) " + "Value Required"`), and the backend
 * still returns the cost-item `code` per issue for exactly that purpose (Story 10.4 §Decision 4). The
 * page drops it, so two missing categories on one location render as two identical "Value Required"
 * notifications and the reporter cannot tell which line to fix.
 *
 * Asserted as "the category is named SOMEWHERE in the Check Status output" rather than against the legacy
 * JSF string: the notification shape was deliberately re-grounded (title carries the location, subtitle
 * the message), so pinning the old literal would assert a format nobody intends to bring back. What is
 * genuinely missing is the field identity.
 */
Then(
  'the Schedule 4 check-status names the {string} category as the missing one',
  async ({ schedule4Page }, label) => {
    const messages = await schedule4Page.checkStatusMessages();
    expect(
      messages.join(' | '),
      `the Check Status output must identify WHICH category needs a value — expected "${label}" to appear in it`,
    ).toContain(label);
  },
);
