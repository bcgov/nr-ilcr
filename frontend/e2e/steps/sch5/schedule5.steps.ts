import { Given, When, Then, expect } from '../fixtures';
import {
  ADD_ANCHOR,
  NEW_CAMP_COSTS,
  NEW_CAMP_DESCRIPTORS,
  NEW_CAMP_EXPECTED_TOTALS,
  NEW_CAMP_NAME,
  VOLUME_BEARING_CATEGORY_LABELS,
  millOptionText,
  type Sch5Anchor,
} from '../../fixtures/sch5/schedule5-test-data';
import { findCampByName, getSchedule5 } from './schedule5Api';

/**
 * UC-SCH5-001 (Schedule 5 — Report Camp and Access Expenses) steps.
 *
 * Domain vocabulary only: every locator lives in `pages/sch5/schedule5Page.ts`, every pinned value in
 * `fixtures/sch5/schedule5-test-data.ts`. The Home-context step
 * ("I have selected that mill and reporting year on the Home page") is REUSED from `steps/common/` —
 * this file only sets `world.scheduleKey` / `world.millOption` for it, exactly as sch3 and sch4 do.
 */

const ANCHORS: Record<string, Sch5Anchor> = {
  add: ADD_ANCHOR,
};

// ---------------------------------------------------------------------------------------------------
// Preconditions
// ---------------------------------------------------------------------------------------------------

Given(
  'the Schedule 5 anchor {string} is an editable Draft with no camps',
  async ({ request, world }, name) => {
    const anchor = ANCHORS[name];
    expect(
      anchor,
      `unknown Schedule 5 anchor "${name}" — see fixtures/sch5/schedule5-test-data.ts`,
    ).toBeTruthy();

    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);

    // Assert the at-rest state this scenario builds on rather than trusting preflight alone: preflight
    // runs once per RUN, and a parallel sibling could in principle have left something behind since.
    const doc = await getSchedule5(request, anchor.key);
    expect(doc.editable, `Schedule 5 anchor "${name}" must be editable`).toBe(true);
    expect(doc.trackStatus, `Schedule 5 anchor "${name}" must be Draft`).toBe('D');
    expect(
      doc.camps.map((c) => c.campName),
      `Schedule 5 anchor "${name}" must hold no camps at rest`,
    ).toEqual([]);
  },
);

Given('no camp named {string} exists for that mill and year', async ({ request, world }, campName) => {
  const existing = await findCampByName(request, world.scheduleKey!, campName);
  expect(
    existing,
    `"${campName}" already exists on ${world.scheduleKey!.millId}/${world.scheduleKey!.year} — BR-02 `
      + 'would reject the save as a duplicate. Delete the leftover camp and re-run.',
  ).toBeUndefined();
});

// ---------------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------------

When('I open Schedule 5', async ({ schedule5Page }) => {
  await schedule5Page.openViaNav();
});

When('I start a new camp', async ({ schedule5Page }) => {
  await schedule5Page.openNewCampPanel();
});

// ---------------------------------------------------------------------------------------------------
// Entry
// ---------------------------------------------------------------------------------------------------

Then('the New Camp Details panel is shown with its descriptor fields blank', async ({ schedule5Page }) => {
  await expect(schedule5Page.newCampPanelHeading).toBeVisible();
  for (const name of ['Camp Name', 'Road Distance to Operating Area', 'Size of Camp', 'Associated Camp Volume', 'Isolated Camp']) {
    expect(await schedule5Page.descriptorValue(name), `"${name}" should start blank`).toBe('');
  }
});

/**
 * Registers cleanup BEFORE any value is typed, so a failure anywhere after this still tears the camp
 * down if the save happened to land.
 */
When('I enter the camp descriptors', async ({ schedule5Page, schedule5Cleanup, world }) => {
  schedule5Cleanup.push({ key: world.scheduleKey!, campName: NEW_CAMP_NAME });

  await schedule5Page.fillDescriptor('Camp Name', NEW_CAMP_NAME);
  await schedule5Page.fillDescriptor(
    'Road Distance to Operating Area',
    NEW_CAMP_DESCRIPTORS.roadDistanceToOperatingArea,
  );
  await schedule5Page.fillDescriptor('Size of Camp', NEW_CAMP_DESCRIPTORS.sizeOfCamp);
  await schedule5Page.fillDescriptor(
    'Associated Camp Volume',
    NEW_CAMP_DESCRIPTORS.associatedCampVolume,
  );
  await schedule5Page.selectIsolatedCamp(NEW_CAMP_DESCRIPTORS.isolatedCamp);
});

/**
 * BR-03 — the camp volume propagates into every volume-bearing category.
 *
 * Asserts all ELEVEN, not a sample: the twelfth category (`recoveries`) has no volume cell at all, and
 * the count is precisely what the slice pins. A spot-check on one row would pass even if propagation
 * had silently stopped covering the Equipment and Supplies rows.
 */
Then('the camp volume {string} is propagated into all 11 category volume fields', async ({ schedule5Page }, volume) => {
  expect(
    VOLUME_BEARING_CATEGORY_LABELS.length,
    'the fixture must list exactly the eleven volume-bearing categories (GRID_ROWS hasVolume:true)',
  ).toBe(11);

  for (const category of VOLUME_BEARING_CATEGORY_LABELS) {
    await expect(
      schedule5Page.categoryInput(category, 'volume'),
      `BR-03: "${category} volume" should carry the camp volume`,
    ).toHaveValue(volume);
  }
});

When('I enter the fixed-category costs', async ({ schedule5Page }) => {
  for (const { label, cost } of NEW_CAMP_COSTS) {
    await schedule5Page.fillCategoryCost(label, cost);
  }
});

// ---------------------------------------------------------------------------------------------------
// Save and verification
// ---------------------------------------------------------------------------------------------------

When('I save the camp', async ({ schedule5Page }) => {
  await schedule5Page.save();
});

Then('{string} is listed in the Existing Camps table', async ({ schedule5Page }, campName) => {
  await expect(schedule5Page.existingCampRow(campName)).toBeVisible();
});

/**
 * The read-back that makes this a persistence test rather than a banner test.
 *
 * Polled, never single-shot: the Save is fired by a click, so the GET can otherwise race the commit.
 * Asserts the SERVER-DERIVED totals — the four the slice names plus the two $/m³ figures — because
 * those are the values BR-04/CNT-001 are actually about, and they are computed server-side from what
 * was persisted, so they prove the whole record landed rather than just its name.
 */
Then('the saved camp carries the expected derived totals', async ({ request, world }) => {
  await expect
    .poll(
      async () => {
        const camp = await findCampByName(request, world.scheduleKey!, NEW_CAMP_NAME);
        if (camp === undefined) {
          return null;
        }
        return {
          campSubTotalCost: camp.campSubTotal.cost,
          campTotalCost: camp.campTotal.cost,
          accessExpenseTotalCost: camp.accessExpenseTotal.cost,
          campAndAccessTotalCost: camp.campAndAccessTotal.cost,
          campTotalCostPerVolume: camp.campTotal.costPerVolume,
          campAndAccessTotalCostPerVolume: camp.campAndAccessTotal.costPerVolume,
        };
      },
      {
        message:
          `"${NEW_CAMP_NAME}" was not persisted with the expected derived totals on `
          + `${world.scheduleKey!.millId}/${world.scheduleKey!.year}`,
      },
    )
    .toEqual(NEW_CAMP_EXPECTED_TOTALS);
});
