import { When, Then, expect } from '../fixtures';
import {
  fixtureByKey,
  bannerMillLine,
  expectedStatusLines,
} from '../../fixtures/sec/working-context-test-data';

/**
 * UC-SEC-001 — the working context a Home Save establishes DISPLAYS on the schedule pages' shared
 * tombstone header (components/core/ScheduleTombstone, the "banner → tombstone" move in bcgov #227).
 * Ports the former Home-banner display arm (S01/S03/S06/S07) to where the lines now render, using
 * Schedule 2 (a ScheduleTombstone page). Read-only (Home Save is a resolve GET), so teardown-free.
 * Reuses the Home select/save steps (steps/sec/working-context.steps.ts) via the shared HomePage.
 */

When('I open {string} from the side-nav', async ({ schedulePage }, name) => {
  await schedulePage.open(name);
});

Then('the working-context tombstone shows the {string} context', async ({ schedulePage }, key) => {
  const f = fixtureByKey(key);
  await expect(schedulePage.contextLine(bannerMillLine(f))).toBeVisible();
  for (const line of expectedStatusLines(f)) {
    await expect(schedulePage.contextLine(line)).toBeVisible();
  }
});

Then(
  'the working-context tombstone shows only the mill line for {string}',
  async ({ schedulePage }, key) => {
    const f = fixtureByKey(key);
    await expect(schedulePage.contextLine(bannerMillLine(f))).toBeVisible();
    // No ILCR_MILL_REPORT_STATUS row → both track-status lines suppressed, and no error (S07).
    await expect(schedulePage.context.getByText(/^Sch 1-10 - Status:/)).toHaveCount(0);
    await expect(schedulePage.context.getByText(/^Sch 11 - Status:/)).toHaveCount(0);
  },
);

Then(
  'the working-context tombstone shows no closed-mill wording for {string}',
  async ({ schedulePage }, key) => {
    const f = fixtureByKey(key);
    // A closed mill's tombstone renders exactly like an open mill's — the mill line shows and there is
    // NO closed-mill-specific text in the header (the closed-mill BLOCK is the schedule body's concern,
    // covered by context-drives-schedule @S06/@S20).
    await expect(schedulePage.contextLine(bannerMillLine(f))).toBeVisible();
    await expect(schedulePage.context.getByText(/closed|not viewable|CLS/i)).toHaveCount(0);
  },
);

Then('the schedule page no longer shows the {string} context', async ({ page }, key) => {
  const f = fixtureByKey(key);
  // Replacement, not merely removal: the previous mill line and its DATED Sch 1-10 line are gone
  // page-wide. Only the dated line is absence-checked — the undated "Draft / Not Initiated" line is
  // textually identical across Draft contexts, so asserting its absence would false-fail (source note
  // carried from bcgov's tombstone.spec.ts S03).
  await expect(page.getByText(bannerMillLine(f), { exact: true })).toHaveCount(0);
  const datedStatusLine = expectedStatusLines(f)[0];
  await expect(page.getByText(datedStatusLine, { exact: true })).toHaveCount(0);
});
