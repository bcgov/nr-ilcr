import { Given, When, Then, expect } from '../fixtures';
import {
  ADD_ANCHOR,
  COPY_ANCHOR,
  SUBPAGE_EXISTING_ANCHOR,
  SUBPAGE_NEW_ANCHOR,
  SUB_PAGES,
  EDIT_ANCHOR,
  EDIT_CAMP_BASELINE,
  EDIT_CAMP_CHANGES,
  EDIT_CAMP_DISPLAY,
  EDIT_CAMP_EXPECTED_REVISION,
  EDIT_CAMP_EXPECTED_TOTALS,
  NEW_CAMP_COSTS,
  NEW_CAMP_DESCRIPTORS,
  NEW_CAMP_EXPECTED_TOTALS,
  NEW_CAMP_NAME,
  VOLUME_BEARING_CATEGORY_LABELS,
  millOptionText,
  type Sch5Anchor,
} from '../../fixtures/sch5/schedule5-test-data';
import { createCamp, findCampByName, getSchedule5 } from './schedule5Api';

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
  edit: EDIT_ANCHOR,
  copy: COPY_ANCHOR,
  'subpage-existing': SUBPAGE_EXISTING_ANCHOR,
  'subpage-new': SUBPAGE_NEW_ANCHOR,
};

/** Resolve the sub-page vocabulary a feature uses ("camp"/"access") to its verbatim app strings. */
function subPage(key: string): (typeof SUB_PAGES)[keyof typeof SUB_PAGES] {
  const def = SUB_PAGES[key as keyof typeof SUB_PAGES];
  expect(def, `unknown Schedule 5 sub-page "${key}" — known: ${Object.keys(SUB_PAGES).join(', ')}`)
    .toBeTruthy();
  return def;
}

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

/**
 * S02's precondition: a camp that already holds stored values.
 *
 * Registers cleanup BEFORE creating, so a failure between the POST and the assertions still tears the
 * camp down. Created through the app's own POST rather than SQL — see `createCamp`.
 */
Given(
  'a camp named {string} already exists with stored descriptor and expense values',
  async ({ request, world, schedule5Cleanup }, campName) => {
    schedule5Cleanup.push({ key: world.scheduleKey!, campName });
    const created = await createCamp(request, world.scheduleKey!, {
      ...EDIT_CAMP_BASELINE,
      campName,
    });
    expect(
      created.revisionCount,
      'a freshly created camp should start at revisionCount 0',
    ).toBe(0);
  },
);

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
// S02 — editing an existing camp
// ---------------------------------------------------------------------------------------------------

When('I edit the {string} camp', async ({ schedule5Page }, campName) => {
  await schedule5Page.openEditPanel(campName);
});

/**
 * The panel must open PRE-FILLED with what was stored — the thing that distinguishes an edit from a
 * blank add, and the precondition for the change assertions that follow.
 *
 * Checks all five descriptors and one representative category half rather than every cell: the twelve
 * categories are rendered by the SAME `CategoryGrid` component from one `GRID_ROWS` table, so they
 * populate together or not at all. The volume column is separately worth asserting because BR-03's
 * propagation writes it, and a reopened camp must show the STORED volume rather than a re-propagated
 * one.
 */
Then(
  'the {string} panel is populated with the stored values',
  async ({ schedule5Page }, campName) => {
    await expect(schedule5Page.campPanelHeading(campName)).toBeVisible();

    // The GROUPED display form — a reopened panel is seeded from the served document through
    // masks.ts, so 1000 comes back as "1,000". See EDIT_CAMP_DISPLAY for why.
    expect(await schedule5Page.descriptorValue('Camp Name')).toBe(campName);
    expect(await schedule5Page.descriptorValue('Road Distance to Operating Area')).toBe(
      EDIT_CAMP_DISPLAY.roadDistanceToOperatingArea,
    );
    expect(await schedule5Page.descriptorValue('Size of Camp')).toBe(EDIT_CAMP_DISPLAY.sizeOfCamp);
    expect(await schedule5Page.descriptorValue('Isolated Camp')).toBe(
      EDIT_CAMP_DISPLAY.isolatedCamp,
    );

    expect(await schedule5Page.categoryValue('Catering and Food', 'cost')).toBe(
      EDIT_CAMP_DISPLAY.cateringAndFoodCost,
    );
    expect(await schedule5Page.categoryValue('Catering and Food', 'volume')).toBe(
      EDIT_CAMP_DISPLAY.cateringAndFoodVolume,
    );
  },
);

When('I change the road distance and the Catering and Food cost', async ({ schedule5Page }) => {
  await schedule5Page.fillDescriptor(
    'Road Distance to Operating Area',
    EDIT_CAMP_CHANGES.roadDistanceToOperatingArea,
  );
  await schedule5Page.fillCategoryCost('Catering and Food', EDIT_CAMP_CHANGES.cateringAndFoodCost);
});

/**
 * Read-back for the edit. Asserts the derived totals AND `revisionCount`, because only the latter
 * proves an UPDATE happened rather than the page re-rendering what was already there — the totals
 * alone would also be satisfied by a create.
 */
Then('the edited camp carries the recalculated totals', async ({ request, world }, ) => {
  await expect
    .poll(
      async () => {
        const camp = await findCampByName(request, world.scheduleKey!, EDIT_CAMP_BASELINE.campName);
        if (camp === undefined) {
          return null;
        }
        return {
          campSubTotalCost: camp.campSubTotal.cost,
          campTotalCost: camp.campTotal.cost,
          accessExpenseTotalCost: camp.accessExpenseTotal.cost,
          campAndAccessTotalCost: camp.campAndAccessTotal.cost,
          campTotalCostPerVolume: camp.campTotal.costPerVolume,
          accessExpenseTotalCostPerVolume: camp.accessExpenseTotal.costPerVolume,
          campAndAccessTotalCostPerVolume: camp.campAndAccessTotal.costPerVolume,
          cateringAndFoodCostPerVolume: camp.cateringAndFood?.costPerVolume ?? null,
          revisionCount: camp.revisionCount,
        };
      },
      {
        message:
          `"${EDIT_CAMP_BASELINE.campName}" did not persist the edit on `
          + `${world.scheduleKey!.millId}/${world.scheduleKey!.year}`,
      },
    )
    .toEqual({ ...EDIT_CAMP_EXPECTED_TOTALS, revisionCount: EDIT_CAMP_EXPECTED_REVISION });
});

// ---------------------------------------------------------------------------------------------------
// S03 — copying a camp
// ---------------------------------------------------------------------------------------------------

When('I copy the {string} camp', async ({ schedule5Page }, campName) => {
  await schedule5Page.copyCamp(campName);
});

/**
 * The copy panel carries every descriptor and category amount from the source — but its Camp Name is
 * BLANK, which is what forces the rename.
 *
 * The source Gherkin says the opposite ("including schedule5Form:newCampName set to 'North Camp'").
 * It is wrong about legacy, and this test follows legacy: `CampReportType.java:120-121` — legacy's own
 * copy constructor clones every field and then sets `campName = null`. The rewrite reproduces that
 * (`seedForm(camp, keepName=false)`, index.tsx:122-140). Logged as SPEC-2 in defects.md; asserting the
 * Gherkin's version here would have pinned a behaviour neither system has ever had.
 */
Then('the new camp panel is pre-filled from {string}', async ({ schedule5Page }, _sourceName) => {
  expect(
    await schedule5Page.descriptorValue('Camp Name'),
    'a copied camp must open with a BLANK name (legacy CampReportType.java:120-121) — see SPEC-2',
  ).toBe('');
  expect(await schedule5Page.descriptorValue('Road Distance to Operating Area')).toBe(
    EDIT_CAMP_DISPLAY.roadDistanceToOperatingArea,
  );
  expect(await schedule5Page.descriptorValue('Size of Camp')).toBe(EDIT_CAMP_DISPLAY.sizeOfCamp);
  expect(await schedule5Page.categoryValue('Catering and Food', 'cost')).toBe(
    EDIT_CAMP_DISPLAY.cateringAndFoodCost,
  );
});

When(
  'I rename the new camp to {string}',
  async ({ schedule5Page, schedule5Cleanup, world }, campName) => {
    // Registered before the rename, so the copy is torn down even if the save fails downstream.
    schedule5Cleanup.push({ key: world.scheduleKey!, campName });
    await schedule5Page.fillDescriptor('Camp Name', campName);
  },
);

/** A copy must ADD a camp, not move one — so the source has to still be there afterwards. */
Then('both {string} and {string} are stored', async ({ request, world }, first, second) => {
  await expect
    .poll(
      async () => {
        const doc = await getSchedule5(request, world.scheduleKey!);
        return doc.camps.map((c) => c.campName).sort();
      },
      { message: `expected both "${first}" and "${second}" on the anchor after the copy` },
    )
    .toEqual([first, second].sort());
});

// ---------------------------------------------------------------------------------------------------
// S04 / S05 — the expense sub-pages
// ---------------------------------------------------------------------------------------------------

When('I open the {string} sub-page', async ({ schedule5Page }, key) => {
  const def = subPage(key);
  await schedule5Page.subPageLink(def.gridLabel).click();
});

Then('the {string} sub-page is shown', async ({ page, schedule5Page }, key) => {
  const def = subPage(key);
  // The sub-page level is a search param on the SAME route, not a separate URL (routes/schedule-5.tsx).
  await expect(page).toHaveURL(new RegExp(`/schedule-5\\?.*sub=${def.sub}`));
  await expect(page.getByRole('heading', { name: def.addHeader })).toBeVisible();
  await expect(schedule5Page.subPageList(def.listHeader)).toBeVisible();
});

When('I add the sub-page row {string} costing {string}', async ({ schedule5Page }, description, cost) => {
  await schedule5Page.subPageField('Description').fill(description);
  await schedule5Page.subPageField('Cost $').fill(cost);
  await schedule5Page.subPageAddButton.click();
});

/**
 * The added row's Volume must default from the camp's Associated Camp Volume — the sub-page's own
 * echo of BR-03, and the one behaviour here that is not simply "a list works".
 */
Then(
  'the {string} list holds {string} with the camp volume defaulted',
  async ({ schedule5Page }, key, description) => {
    const def = subPage(key);

    // The Description cell is an INPUT, so assert its value rather than the row's text. Volume is
    // read-only TEXT in the same row (index.tsx:541-545) and renders through fmtVolume, i.e. grouped.
    await expect(schedule5Page.subPageDescriptions(def.listHeader)).toHaveValue(description);
    await expect(schedule5Page.subPageRow(def.listHeader, description)).toContainText(
      EDIT_CAMP_DISPLAY.cateringAndFoodVolume,
    );
  },
);

// Deliberately NOT the bare "I save the sub-page": sch3 already owns that text for its own sub-page
// page object (steps/sch3/subPage.steps.ts:77), and playwright-bdd rightly rejects two definitions of
// one step. sch3's own "I go back to Schedule 3" sets the precedent — a sub-page step that drives a
// domain's page object carries that domain's name.
When('I save the Schedule 5 sub-page', async ({ schedule5Page }) => {
  await schedule5Page.save();
});

/**
 * Back ALWAYS asks for confirmation on an editable sub-page — `requestBack` has no dirty check and
 * goes straight to the confirm unless the document is read-only (index.tsx:357-365), where legacy
 * renders a bare Back. So a saved, pristine list still prompts, and the step must answer it.
 */
When('I go back to the camp list', async ({ page, schedule5Page }) => {
  await schedule5Page.subPageBackButton.click();
  await schedule5Page.confirmModal('Leave expense list');
  await expect(page).toHaveURL(/\/schedule-5(?!\?.*sub=)/);
  await expect(schedule5Page.campsTable).toBeVisible();
});

Then(
  'the {string} link shows a count of {int}',
  async ({ schedule5Page }, key, count) => {
    await expect(schedule5Page.subPageLinkWithCount(subPage(key).gridLabel, count)).toBeVisible();
  },
);

When('I confirm the {string} dialog', async ({ schedule5Page }, heading) => {
  await schedule5Page.confirmModal(heading);
});

Then('I should see the confirm text {string}', async ({ page }, text) => {
  await expect(page.getByText(text)).toBeVisible();
});

/** S05's precondition: a new camp panel filled in far enough to be saveable, but NOT saved. */
When(
  'I start a new camp named {string} without saving',
  async ({ schedule5Page, schedule5Cleanup, world }, campName) => {
    schedule5Cleanup.push({ key: world.scheduleKey!, campName });
    await schedule5Page.openNewCampPanel();
    await schedule5Page.fillDescriptor('Camp Name', campName);
    // Isolated Camp is REQUIRED (S12) — without it the auto-save behind the confirm would be rejected
    // and S05 would fail for a reason that belongs to a different slice.
    await schedule5Page.selectIsolatedCamp(NEW_CAMP_DESCRIPTORS.isolatedCamp);
    await schedule5Page.fillDescriptor(
      'Associated Camp Volume',
      NEW_CAMP_DESCRIPTORS.associatedCampVolume,
    );
  },
);

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
