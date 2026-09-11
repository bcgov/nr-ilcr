import { Given, When, Then, expect } from '../fixtures';
import {
  ADD_ANCHOR,
  ACCESS_DESC_BLANK_ANCHOR,
  CAMP_DESC_BLANK_ANCHOR,
  CHECK_MISSING_ANCHOR,
  SUBPAGE_COST_ANCHOR,
  SUBPAGE_COST_ACCESS_ANCHOR,
  CHECK_MET_ANCHOR,
  DELETE_ANCHOR,
  CAMP_SWITCH_ANCHOR,
  COPY_DUPLICATE_ANCHOR,
  DUPLICATE_NAME_ANCHOR,
  REQUIRED_FIELD_ANCHOR,
  VALIDATION_ANCHOR,
  VALIDATION_MESSAGES,
  DISCARDED_COST,
  DISCARD_CLOSE_ANCHOR,
  RECOVERIES_ANCHOR,
  SAME_NAME_A_ANCHOR,
  SAME_NAME_B_ANCHOR,
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
  GUARDS,
  GUARD_MESSAGES,
  CHECK_MISSING_BASELINE,
  CHECK_MISSING_FIX_DISTANCE,
  SUB_PAGE_HOST_CAMP,
  READ_ONLY_ANCHOR,
  VIEW_CAMP_DERIVED,
  VIEW_CAMP_DISPLAY,
  VIEW_CAMP_NAME,
  scheduleUrl,
  millOptionText,
  type Sch5Anchor,
} from '../../fixtures/sch5/schedule5-test-data';
import { createCamp, findCampByName, getSchedule5, getSubPageRows } from './schedule5Api';
import type { APIRequestContext } from '@playwright/test';
import type { ScheduleKey } from '../../fixtures/sch5/schedule5-test-data';

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
  'check-met': CHECK_MET_ANCHOR,
  delete: DELETE_ANCHOR,
  'same-name-b': SAME_NAME_B_ANCHOR,
  recoveries: RECOVERIES_ANCHOR,
  'discard-close': DISCARD_CLOSE_ANCHOR,
  'camp-switch': CAMP_SWITCH_ANCHOR,
  'required-field': REQUIRED_FIELD_ANCHOR,
  validation: VALIDATION_ANCHOR,
  'duplicate-name': DUPLICATE_NAME_ANCHOR,
  'copy-duplicate': COPY_DUPLICATE_ANCHOR,
  'check-missing': CHECK_MISSING_ANCHOR,
  'access-desc-blank': ACCESS_DESC_BLANK_ANCHOR,
  'camp-desc-blank': CAMP_DESC_BLANK_ANCHOR,
  'subpage-cost': SUBPAGE_COST_ANCHOR,
  'subpage-cost-access': SUBPAGE_COST_ACCESS_ANCHOR,
};

/** Resolve the sub-page vocabulary a feature uses ("camp"/"access") to its verbatim app strings. */
function subPage(key: string): (typeof SUB_PAGES)[keyof typeof SUB_PAGES] {
  const def = SUB_PAGES[key as keyof typeof SUB_PAGES];
  expect(def, `unknown Schedule 5 sub-page "${key}" — known: ${Object.keys(SUB_PAGES).join(', ')}`)
    .toBeTruthy();
  return def;
}

/**
 * The STORED descriptions of a sub-page's rows, in served order.
 *
 * Resolves the feature's "camp"/"access" vocabulary to the camp id and the REST path, so the
 * scenarios never carry either. A null description reads as `''` — the write path deliberately sends
 * a blank as `null` (`toRowRequest`), and a test asserting the round-trip should not have to care
 * which of the two the column happens to hold.
 */
async function subPageRowDescriptions(
  request: APIRequestContext,
  key: ScheduleKey,
  pageKey: string,
): Promise<string[]> {
  const camp = await findCampByName(request, key, SUB_PAGE_HOST_CAMP);
  expect(camp, `the sub-page host camp "${SUB_PAGE_HOST_CAMP}" is missing from the anchor`).toBeDefined();
  const path =
    subPage(pageKey).sub === 'CAMP' ? 'other-camp-expenses' : 'other-access-expenses';
  const rows = await getSubPageRows(request, key, camp!.campId, path);
  return rows.map((r) => r.description ?? '');
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
// S12 / S13 / S14 / S15 — validation
// ---------------------------------------------------------------------------------------------------

/** Fill a descriptor and blur, so its validator reports. Blur is the commit point (index.tsx:734). */
When(
  'I enter {string} in the {string} field',
  async ({ schedule5Page }, value, field) => {
    await schedule5Page.fillDescriptorAndCommit(field, value);
  },
);

Then(
  'the {string} field shows the error {string}',
  async ({ schedule5Page }, field, message) => {
    await expect(schedule5Page.descriptorError(field)).toHaveText(message);
  },
);

Then(
  'the {string} cost field shows the error {string}',
  async ({ schedule5Page }, category, message) => {
    await expect(schedule5Page.categoryError(category, 'cost')).toHaveText(message);
  },
);

Then('the {string} field shows no error', async ({ schedule5Page }, field) => {
  await expect(schedule5Page.descriptorError(field)).toHaveCount(0);
});

Then('the {string} cost field shows no error', async ({ schedule5Page }, category) => {
  await expect(schedule5Page.categoryError(category, 'cost')).toHaveCount(0);
});

/** S13/S14: the duplicate-name rejection is a banner, not an inline field error. */
Then('I should see the camp-name duplicate error', async ({ page }) => {
  await expect(page.getByText(VALIDATION_MESSAGES.campNameDuplicate).first()).toBeVisible();
});

When('I try to save the camp', async ({ schedule5Page }) => {
  await schedule5Page.save();
});

/**
 * Prove the negative for a rejected save.
 *
 * The inline error alone only shows the page complained; it does not show that nothing reached the
 * database. This reads the anchor back and asserts the camp list is unchanged — which is the claim
 * "the entry is not persisted" actually makes.
 */
Then('no camp named {string} is stored', async ({ request, world }, campName) => {
  const camp = await findCampByName(request, world.scheduleKey!, campName);
  expect(
    camp,
    `"${campName}" must NOT have been persisted — the save was supposed to be rejected`,
  ).toBeUndefined();
});

/**
 * The anchor holds EXACTLY these camps — the right way to prove a duplicate was rejected.
 *
 * `no camp named X is stored` cannot do it here: name lookup is case-insensitive (as BR-02 itself is),
 * so asking whether "NORTH CAMP" exists always finds the seeded "North Camp" and the assertion fails
 * against a perfectly correct rejection. Pinning the whole list distinguishes "the duplicate was not
 * created" from "the original is still there", which is exactly the distinction this slice is about.
 */
Then('the anchor holds exactly {string}', async ({ request, world }, expected) => {
  const doc = await getSchedule5(request, world.scheduleKey!);
  expect(
    doc.camps.map((c) => c.campName).sort(),
    'the rejected save must not have added a camp',
  ).toEqual([expected]);
});

// ---------------------------------------------------------------------------------------------------
// S10 — close with unsaved changes  |  S11 — switch camps while editing
// ---------------------------------------------------------------------------------------------------

/** S11's precondition: two camps on one anchor, both seeded from the same baseline. */
Given(
  'camps named {string} and {string} already exist',
  async ({ request, schedule5Cleanup, world }, first, second) => {
    for (const campName of [first, second]) {
      schedule5Cleanup.push({ key: world.scheduleKey!, campName });
      await createCamp(request, world.scheduleKey!, { ...EDIT_CAMP_BASELINE, campName });
    }
  },
);

When('I close the camp panel', async ({ schedule5Page }) => {
  await schedule5Page.closeButton.click();
});

/** Click Edit on another camp while one panel is dirty — the switch must be intercepted, not honoured. */
When('I click Edit on the {string} camp', async ({ schedule5Page }, campName) => {
  await schedule5Page.clickEditFor(campName);
});

Then('the camp panel is closed', async ({ schedule5Page }) => {
  await schedule5Page.expectPanelClosed();
});

/**
 * The discard must not have reached the database.
 *
 * A confirm dialog can be dismissed correctly on screen while the edit was already flushed — asserting
 * the panel closed proves only that the panel closed. This reads the camp back and pins the ORIGINAL
 * cost, which is the whole point of "the unsaved changes are discarded".
 */
Then(
  '{string} still holds its original Catering and Food cost',
  async ({ request, world }, campName) => {
    const camp = await findCampByName(request, world.scheduleKey!, campName);
    expect(camp, `"${campName}" should still exist on the anchor`).toBeDefined();
    expect(
      camp?.cateringAndFood?.cost,
      `"${campName}" must still hold its stored cost — the discarded edit (${DISCARDED_COST}) must `
        + 'never have been persisted',
    ).toBe(EDIT_CAMP_BASELINE.cateringAndFood.cost);
  },
);

// ---------------------------------------------------------------------------------------------------
// S09 — Recoveries reduces the Camp Total
// ---------------------------------------------------------------------------------------------------

When(
  'I enter a {string} cost of {string}',
  async ({ schedule5Page }, category, cost) => {
    await schedule5Page.fillCategoryCostAndCommit(category, cost);
  },
);

/**
 * A derived row's on-screen figure — the CLIENT-SIDE mirror, before any save.
 *
 * The four derived rows are read-only text, never inputs (index.tsx:203), and they render through
 * `fmtCost`, so 1000 reads as "1,000". S09 never saves, so nothing here can be a served figure: this
 * is the mirror the app maintains while the panel is editable (#291).
 */
Then('the {string} row shows {string}', async ({ schedule5Page }, label, value) => {
  await expect(schedule5Page.derivedRow(label)).toContainText(value);
});

// ---------------------------------------------------------------------------------------------------
// S08 — the same camp name under a different mill/year
// ---------------------------------------------------------------------------------------------------

/**
 * Seeds the name on the OTHER anchor without disturbing the working context.
 *
 * Deliberately does NOT touch `world.scheduleKey`: the browser journey stays entirely in the second
 * mill-year, and the first one only has to hold the name. So no Home context switch is needed — the
 * slice is about BR-02's SCOPE, not about navigation.
 */
Given(
  'a camp named {string} already exists under a different mill and year',
  async ({ request, schedule5Cleanup, world }, campName) => {
    expect(
      `${SAME_NAME_A_ANCHOR.key.millId}/${SAME_NAME_A_ANCHOR.key.year}`,
      'S08 needs two DISTINCT anchors — the whole slice is that the name is free in the second one',
    ).not.toBe(`${world.scheduleKey!.millId}/${world.scheduleKey!.year}`);

    schedule5Cleanup.push({ key: SAME_NAME_A_ANCHOR.key, campName });
    await createCamp(request, SAME_NAME_A_ANCHOR.key, { ...EDIT_CAMP_BASELINE, campName });
  },
);

/**
 * The minimum a camp needs to save: a name and the Isolated Camp selection. Every other descriptor and
 * all twelve category amounts are optional — a blank optional field is CLEARED, not invalid
 * (`validation.ts`). S08 relies on that, and S12 is the mirror that proves these two ARE required.
 */
When(
  'I name the new camp {string} and set Isolated Camp to {string}',
  async ({ schedule5Page, schedule5Cleanup, world }, campName, isolated) => {
    schedule5Cleanup.push({ key: world.scheduleKey!, campName });
    await schedule5Page.fillDescriptor('Camp Name', campName);
    await schedule5Page.selectIsolatedCamp(isolated);
  },
);

/** The other mill-year must be untouched — a save that "succeeded" by moving the camp would not do. */
Then(
  '{string} is still stored under the other mill and year',
  async ({ request }, campName) => {
    const camp = await findCampByName(request, SAME_NAME_A_ANCHOR.key, campName);
    expect(
      camp?.campName,
      `"${campName}" should still be on ${SAME_NAME_A_ANCHOR.key.millId}/`
        + `${SAME_NAME_A_ANCHOR.key.year} — BR-02 scopes uniqueness per mill/year, so saving the same `
        + 'name elsewhere must ADD a camp, never move one',
    ).toBe(campName);
  },
);

// ---------------------------------------------------------------------------------------------------
// S06 — Check Status  |  S07 — Delete a camp
// ---------------------------------------------------------------------------------------------------

// Domain-qualified because sch11 already owns the bare "I run Check Status"
// (steps/sch11/schedule11.steps.ts:458) and playwright-bdd rejects two definitions of one step. Same
// reason as "I save the Schedule 5 sub-page" above.
When('I run Schedule 5 Check Status', async ({ schedule5Page }) => {
  await schedule5Page.runCheckStatus();
});

When('I delete the {string} camp', async ({ schedule5Page }, campName) => {
  await schedule5Page.deleteButtonFor(campName).click();
});

Then('{string} is no longer listed in the Existing Camps table', async ({ schedule5Page }, campName) => {
  await expect(schedule5Page.existingCampRow(campName)).toHaveCount(0);
});

/**
 * Prove the delete reached the database, not just the table.
 *
 * A row can vanish from a client-side list without anything being persisted, so the UI assertion alone
 * would pass against a purely optimistic removal.
 */
Then('no camps are stored on the anchor', async ({ request, world }) => {
  await expect
    .poll(
      async () => (await getSchedule5(request, world.scheduleKey!)).camps.map((c) => c.campName),
      { message: `expected no camps on ${world.scheduleKey!.millId}/${world.scheduleKey!.year}` },
    )
    .toEqual([]);
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

// ---------------------------------------------------------------------------------------------------
// S20 — Check Status finds missing required values
// ---------------------------------------------------------------------------------------------------

/**
 * A camp that SAVES but does not PASS Check Status.
 *
 * The two rule sets are deliberately different, and this slice only exists because of the gap: only
 * Camp Name and Isolated Camp are required to save, while Check Status additionally tests road
 * distance, size of camp, associated camp volume and the four sub-list conditions
 * (`Schedule5Service.evaluateCamp`). So the camp stores cleanly with its road distance absent, and
 * only Check Status objects — which is exactly the state a licensee reaches by saving early.
 */
Given(
  'a camp named {string} already exists with no road distance',
  async ({ request, world, schedule5Cleanup }, campName) => {
    schedule5Cleanup.push({ key: world.scheduleKey!, campName });
    const created = await createCamp(request, world.scheduleKey!, {
      ...CHECK_MISSING_BASELINE,
      campName,
    });
    // Nullish rather than strictly null: the serializer OMITS an absent field rather than sending
    // `null`, so the served camp has no `roadDistanceToOperatingArea` key at all. Both spellings
    // mean "absent", which is the only thing this precondition cares about.
    expect(
      created.roadDistanceToOperatingArea ?? null,
      'the S20 precondition must store with NO road distance — that is the whole slice',
    ).toBeNull();
  },
);

When('I fill in the missing road distance and save', async ({ schedule5Page }) => {
  await schedule5Page.fillDescriptor(
    'Road Distance to Operating Area',
    CHECK_MISSING_FIX_DISTANCE,
  );
  await schedule5Page.save();
});

// ---------------------------------------------------------------------------------------------------
// S21 / S22 / S23 — the expense sub-pages' own validation
// ---------------------------------------------------------------------------------------------------

/** Type into the add form WITHOUT clicking Add — so a scenario can leave one field deliberately blank. */
When(
  'I enter the sub-page description {string} and cost {string}',
  async ({ schedule5Page }, description, cost) => {
    await schedule5Page.subPageField('Description').fill(description);
    await schedule5Page.subPageField('Cost $').fill(cost);
  },
);

When('I click the sub-page Add button', async ({ schedule5Page }) => {
  await schedule5Page.subPageAddButton.click();
});

Then(
  'the sub-page {string} field shows the error {string}',
  async ({ schedule5Page }, field, message) => {
    await expect(
      schedule5Page.subPageAddFieldError(field as 'Description' | 'Cost $'),
    ).toHaveText(message);
  },
);

/**
 * Prove the rejected Add added nothing.
 *
 * The inline error alone only shows the form complained. Add COMMITS to the server when it succeeds
 * (`handleAdd` -> `save`), so "the row is not added" is a claim about the stored list, and a row
 * count is the way to check it that does not depend on knowing the row's description — which for a
 * blank-description rejection there is none of.
 */
Then('the {string} list holds {int} rows', async ({ schedule5Page }, key, count) => {
  await expect(schedule5Page.subPageRows(subPage(key).listHeader)).toHaveCount(count);
});

/** Clear a stored row's description in the GRID — the S21/S22 timing difference lives here. */
When('I clear the first sub-page row description', async ({ schedule5Page }) => {
  await schedule5Page.subPageRowInput(0, 'description').fill('');
});

When(
  'I set the first sub-page row description to {string}',
  async ({ schedule5Page }, value) => {
    await schedule5Page.subPageRowInput(0, 'description').fill(value);
  },
);

Then(
  'the first sub-page row description shows the error {string}',
  async ({ schedule5Page }, message) => {
    await expect(schedule5Page.subPageRowError(0, 'description')).toHaveText(message);
  },
);

/**
 * The CAMP page's grid defers its required check to Save, so nothing may appear on change.
 *
 * This is the assertion that makes S22 different from S21 rather than a second copy of it. It is a
 * negative, so it is worth saying why it is not vacuous: the identical action on the ACCESS page
 * (S21) DOES raise the error immediately, and that scenario asserts it — the pair only means
 * something because both halves are checked.
 */
Then('the first sub-page row description shows no error', async ({ schedule5Page }) => {
  await expect(schedule5Page.subPageRowError(0, 'description')).toHaveCount(0);
});

/**
 * The sub-page's stored row list, read back through the API.
 *
 * A blocked Save is a claim about the DATABASE, and the grid still shows whatever was typed — so the
 * screen cannot answer it. This reads the camp's served sub-page rows instead.
 */
Then(
  'the stored {string} rows are exactly {string}',
  async ({ request, world }, key, expected) => {
    const descriptions = await subPageRowDescriptions(request, world.scheduleKey!, key);
    expect(
      descriptions,
      `the ${key} sub-page's STORED rows should be [${expected}] — a blocked save must not persist`,
    ).toEqual(expected === '' ? [] : expected.split('|'));
  },
);

// ---------------------------------------------------------------------------------------------------
// S16 / S17 / S18 — the EF2 guards  |  S19 — the read-only render
// ---------------------------------------------------------------------------------------------------

/**
 * A guard anchor, proved at the API BEFORE the browser is driven.
 *
 * The check is not ceremony: both fixtures ARE failure responses, so nothing about them is
 * self-evident from a passing suite. If 25051 were ever re-opened, or 16050/2022 seeded, the scenario
 * would fail with a missing banner — which reads as an app defect. Asserting the status here makes it
 * read as the data problem it would be.
 */
Given('the Schedule 5 guard anchor {string}', async ({ request, world }, name) => {
  const guard = GUARDS[name];
  expect(
    guard,
    `unknown Schedule 5 guard anchor "${name}" — known: ${Object.keys(GUARDS).join(', ')}`,
  ).toBeTruthy();

  world.scheduleKey = guard.anchor.key;
  world.millOption = millOptionText(guard.anchor.mill);

  const res = await request.get(scheduleUrl(guard.anchor.key.millId, guard.anchor.key.year));
  expect(
    res.status(),
    `precondition: Schedule 5 guard anchor "${name}" `
      + `(${guard.anchor.key.millId}/${guard.anchor.key.year}) must still answer HTTP `
      + `${guard.expectHttp}`,
  ).toBe(guard.expectHttp);
});

/**
 * S19's anchor: the one Submitted document, holding the one seeded camp.
 *
 * Asserts BOTH halves at the API first — non-editable AND holding exactly the seeded camp. The second
 * is what keeps "the row-action column shows a single View button" unambiguous: with two camps the
 * assertion could pass on the wrong row.
 */
Given('the Schedule 5 read-only anchor holds the seeded camp', async ({ request, world }) => {
  world.scheduleKey = READ_ONLY_ANCHOR.key;
  world.millOption = millOptionText(READ_ONLY_ANCHOR.mill);

  const doc = await getSchedule5(request, READ_ONLY_ANCHOR.key);
  expect(
    doc.editable,
    `the read-only anchor (${READ_ONLY_ANCHOR.key.millId}/${READ_ONLY_ANCHOR.key.year}) must NOT be `
      + 'editable — S19 is the non-Draft render',
  ).toBe(false);
  expect(
    doc.camps.map((c) => c.campName),
    `the read-only anchor must hold exactly "${VIEW_CAMP_NAME}" — seeded by `
      + 'real-test-data-patches/sch5/view-mode-camp.sql. Run frontend/e2e/scripts/apply-patches.sh.',
  ).toEqual([VIEW_CAMP_NAME]);
});

When('I open Schedule 5 with no working context', async ({ schedule5Page }) => {
  await schedule5Page.openWithNoContext();
});

When('I open Schedule 5 expecting a guard message', async ({ schedule5Page }) => {
  await schedule5Page.openViaNavExpectingGuard();
});

Then('the Schedule 5 mill and reporting year guard message is shown', async ({ schedule5Page }) => {
  await expect(schedule5Page.notification(GUARD_MESSAGES.millYearNotSelected)).toBeVisible();
  await expect(schedule5Page.notification(GUARD_MESSAGES.millYearNotSelectedTitle)).toBeVisible();
});

/** The API's own `detail`, rendered under the load-failure title rather than in a `p:messages` panel. */
Then('the Schedule 5 page is blocked with {string}', async ({ schedule5Page }, detail) => {
  await expect(schedule5Page.notification(detail)).toBeVisible();
  await expect(schedule5Page.notification(GUARD_MESSAGES.loadFailedTitle)).toBeVisible();
});

Then('the Schedule 5 data-entry panel is suppressed', async ({ schedule5Page }) => {
  await schedule5Page.expectDataEntrySuppressed();
});

Then('the Schedule 5 page-level actions are disabled', async ({ schedule5Page }) => {
  await expect(schedule5Page.addNewCampButton).toBeDisabled();
  await expect(schedule5Page.checkStatusButton).toBeDisabled();
});

/**
 * STA-001, re-grounded. The source Gherkin expects Delete and Copy to be RENDERED-BUT-DISABLED; the
 * rewrite drops them from the DOM entirely and leaves a single `View` (index.tsx:1193-1208, which
 * cites the epics AC and Schedule 6's precedent as deviation (B)). Net user-reachable behaviour is
 * identical — there is no write action either way — so this asserts the ABSENCE rather than a
 * disabled state, and the feature file records why.
 */
Then('the {string} row offers only a View action', async ({ schedule5Page }, campName) => {
  await expect(schedule5Page.viewButtonFor(campName)).toBeVisible();
  await expect(
    schedule5Page.rowWriteActionsFor(campName),
    'a non-editable document must render no Edit/Delete/Copy at all (deviation (B))',
  ).toHaveCount(0);
});

When('I view the {string} camp', async ({ schedule5Page }, campName) => {
  await schedule5Page.openViewPanel(campName);
});

/**
 * The panel opened for VIEWING carries the stored values and cannot be edited.
 *
 * Two different mechanisms, asserted separately because they differ: the four descriptors stay Carbon
 * `TextInput`s with `readOnly` (so the value is in `inputValue()`), while `Isolated Camp` is a
 * `Select` and is `disabled` instead. The category grid meanwhile holds no inputs at all.
 */
Then('the {string} panel is read-only with the stored values', async ({ schedule5Page }, campName) => {
  await expect(schedule5Page.campPanelHeading(campName)).toBeVisible();

  expect(await schedule5Page.descriptorValue('Camp Name')).toBe(VIEW_CAMP_DISPLAY.campName);
  expect(await schedule5Page.descriptorValue('Road Distance to Operating Area')).toBe(
    VIEW_CAMP_DISPLAY.roadDistanceToOperatingArea,
  );
  expect(await schedule5Page.descriptorValue('Size of Camp')).toBe(VIEW_CAMP_DISPLAY.sizeOfCamp);
  expect(await schedule5Page.descriptorValue('Associated Camp Volume')).toBe(
    VIEW_CAMP_DISPLAY.associatedCampVolume,
  );
  expect(await schedule5Page.descriptorValue('Isolated Camp')).toBe(VIEW_CAMP_DISPLAY.isolatedCamp);

  for (const name of [
    'Camp Name',
    'Road Distance to Operating Area',
    'Size of Camp',
    'Associated Camp Volume',
  ]) {
    await schedule5Page.expectDescriptorReadOnly(name);
  }
  await schedule5Page.expectIsolatedCampDisabled();
});

Then('the Schedule 5 category grid is read-only', async ({ schedule5Page }) => {
  await schedule5Page.expectCategoryGridReadOnly();
});

/**
 * The category amounts render as TEXT, and the derived rows carry the SERVED figures.
 *
 * `derived` is null on a non-editable document (index.tsx:1191), so none of these can be the
 * client-side mirror — which is the point: S19 proves the read path, where S01 proved the write path
 * that produced the very same numbers.
 */
Then('the read-only panel shows the stored amounts and totals', async ({ schedule5Page }) => {
  await expect(schedule5Page.categoryRow('Catering and Food: ')).toContainText(
    VIEW_CAMP_DISPLAY.cateringAndFoodCost,
  );
  await expect(schedule5Page.categoryRow('Catering and Food: ')).toContainText(
    VIEW_CAMP_DISPLAY.cateringAndFoodVolume,
  );

  for (const { label, cost, perVolume } of VIEW_CAMP_DERIVED) {
    const row = schedule5Page.derivedRow(label);
    await expect(row, `the "${label.trim()}" row should show ${cost}`).toContainText(cost);
    await expect(row, `the "${label.trim()}" row should show $/m³ ${perVolume}`).toContainText(
      perVolume,
    );
  }
});

/** Save must be disabled in a View panel; Close must NOT be — it is the only way out (index.tsx:1327-1346). */
Then('the read-only panel offers no Save but can be closed', async ({ schedule5Page }) => {
  await expect(schedule5Page.saveButton).toBeDisabled();
  await expect(schedule5Page.closeButton).toBeEnabled();
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
