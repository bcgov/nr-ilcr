import { Given, When, Then, expect } from '../fixtures';
import {
  OPEN_WITH_STATUS,
  DEFAULT_CONTEXT,
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

Then('the working context is pre-selected on landing', async ({ homePage }) => {
  // Re-grounding proof: the mount default (millYearDefaults.ts 13050/2017) is present in both list
  // endpoints, so Home pre-selects both dropdowns and Carbon offers no clear-to-placeholder control —
  // this is exactly why the empty-dropdown slices S04/S05/S08 are not UI-reproducible (see defects.md).
  await expect(homePage.millDropdown).toContainText(millOptionText(DEFAULT_CONTEXT));
  await expect(homePage.millDropdown).not.toContainText(PLACEHOLDER.mill);
  await expect(homePage.yearDropdown).toContainText(String(DEFAULT_CONTEXT.year));
  await expect(homePage.yearDropdown).not.toContainText(PLACEHOLDER.year);
  // The banner is populated on landing (before any Save), keyed on that default context.
  await expect(homePage.bannerLine(bannerMillLine(DEFAULT_CONTEXT))).toBeVisible();
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
