import { Given, When, Then, expect } from '../fixtures';
import {
  OPEN_WITH_STATUS,
  millOptionText,
  bannerMillLine,
  expectedStatusLines,
  PLACEHOLDER,
  fixtureByKey,
} from '../../fixtures/sec/working-context-test-data';

/**
 * UC-SEC-001 (Home — Establish Working Context) steps. Domain vocabulary only — no DOM (that lives in
 * HomePage). Home "Save" is a read/resolve (GET /v1/mill-context) that writes nothing, so there is no
 * teardown and scenarios are parallel-safe. The observable outcome is the SUC-001 message plus the
 * working-context banner (Layout/ContextBanner.tsx), which is the UI read-back of the resolved context.
 */

const fixture = fixtureByKey;

Given('I am on the Home page', async ({ homePage }) => {
  await homePage.open();
});

Then('the mill and reporting-year option lists are populated', async ({ homePage }) => {
  // AC1 (S01 land): prove the lists actually loaded from GET /v1/mills + /v1/reporting-years — open each
  // dropdown and confirm a known real option is present (not merely that the dropdown is visible).
  await homePage.assertOptionListsPopulated(millOptionText(OPEN_WITH_STATUS), OPEN_WITH_STATUS.year);
});

Then('no working context is selected on landing', async ({ homePage }) => {
  // A first-ever visit has NO working context: both Carbon Dropdowns sit on their placeholder `label`
  // and the user is asked to choose. Until MillYearProvider dropped its 13050/2017 mount default
  // (commit e37649b), a context always existed, so Home reflected it and these placeholders were
  // unreachable — this step asserted the pre-selection instead. Local storage is empty in a fresh
  // Playwright browser context, so every scenario lands in the no-context state.
  await expect(homePage.millDropdown).toContainText(PLACEHOLDER.mill);
  await expect(homePage.yearDropdown).toContainText(PLACEHOLDER.year);
  // AC5: a null context renders NO banner at all (ContextBanner.tsx returns null) — the banner is the
  // read-back of a chosen context, so on landing there is nothing to read back.
  await expect(homePage.banner).toHaveCount(0);
});

When('I select the working context {string}', async ({ homePage }, key) => {
  const f = fixture(key);
  await homePage.selectMill(millOptionText(f));
  await homePage.selectYear(f.year);
});

When('I save the working context', async ({ homePage }) => {
  await homePage.save();
});

Then('the working-context banner shows the {string} context', async ({ homePage }, key) => {
  const f = fixture(key);
  await expect(homePage.bannerLine(bannerMillLine(f))).toBeVisible();
  for (const line of expectedStatusLines(f)) {
    await expect(homePage.bannerLine(line)).toBeVisible();
  }
});

Then(
  'the working-context banner shows only the mill line for the {string} context',
  async ({ homePage }, key) => {
    const f = fixture(key);
    await expect(homePage.bannerLine(bannerMillLine(f))).toBeVisible();
    // No report-status row for this pair → neither track's status line renders (both carry "- Status:").
    await expect(homePage.banner.getByText(/- Status:/)).toHaveCount(0);
  },
);

Then('the working-context banner no longer shows the {string} context', async ({ homePage }, key) => {
  const f = fixture(key);
  await expect(homePage.bannerLine(bannerMillLine(f))).toHaveCount(0);
});
