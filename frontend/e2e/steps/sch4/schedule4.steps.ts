import { Given, Then, When, expect } from '../fixtures';
import { settleBeforeReadingSpy } from '../../pages/common/settle';
import {
  CATEGORY_CODE_BY_LABEL,
  CLIENT,
  GRID_ROW_LABELS,
  GUARD_ANCHORS,
  READ_ONLY_ANCHORS,
  isMutatingAnchor,
  millOptionText,
  namedAnchor,
  subPageByLabel,
} from '../../fixtures/sch4/schedule4-test-data';
import {
  addRow,
  category,
  findLocation,
  getSchedule4,
  requireLocation,
  saveLocation,
  type SeedCategory,
} from './schedule4Api';

/**
 * Schedule 4 (UC-SCH4-001) steps — the location list, the New/Edit/Copy/View panel, the category grid,
 * delete, and the API read-backs. Sub-page steps live in `./subPage.steps.ts`, Check Status in
 * `./checkStatus.steps.ts`. No DOM selectors here: everything goes through `pages/sch4/*`.
 *
 * Every phrase is prefixed "Schedule 4" on purpose. Schedule 11 also has locations, an "Add New Location"
 * panel and a Check Status button, and playwright-bdd matches steps by phrase across the WHOLE suite — an
 * unqualified "the location {string} is listed" would be ambiguous with (or steal from) that domain.
 *
 * Anchor names resolve through the fixture's own `namedAnchor()`, so the anchor table is the single place a
 * (mill, year) is chosen and documented.
 */

/** Blank data-table cell → null (a genuinely unset amount), never 0 — the two mean different things. */
const cellNum = (raw: string | undefined): number | null => {
  const value = (raw ?? '').trim();
  return value === '' ? null : Number(value);
};

/** Resolve a category LABEL to its legacy cost-item code, failing loud on a typo in a `.feature`. */
function categoryCode(label: string): number {
  const code = CATEGORY_CODE_BY_LABEL[label];
  if (code === undefined) {
    throw new Error(
      `unknown Schedule 4 category "${label}" in a data table. Known: ${Object.keys(CATEGORY_CODE_BY_LABEL).join(', ')}.`,
    );
  }
  return code;
}

/** Turn a `| category | distance | volume | cost |` table into the seed shape. */
function seedCategories(rows: Record<string, string>[]): SeedCategory[] {
  return rows.map((row) => ({
    code: categoryCode(row.category.trim()),
    volume: cellNum(row.volume),
    cost: cellNum(row.cost),
    distance: cellNum(row.distance),
  }));
}

// ---------------------------------------------------------------------------------------------------
// Preconditions
// ---------------------------------------------------------------------------------------------------

Given(
  'the Schedule 4 anchor {string} is an editable Draft with no locations',
  async ({ request, world, schedule4Cleanup }, name) => {
    const anchor = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    // Register cleanup the moment we know we MIGHT write, so a mid-scenario failure still tears down.
    // The validate-only anchor is deliberately NOT registered: nothing may ever be written there, and a
    // cleanup sweep would mask a leak instead of letting the read-back assertion catch it.
    if (isMutatingAnchor(anchor.key)) {
      schedule4Cleanup.push({ key: anchor.key });
    }

    const doc = await getSchedule4(request, anchor.key);
    expect(doc.trackStatus, 'precondition: the Schedules 1-10 track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: the schedule must be editable').toBe(true);
    // EMPTY, not merely "no location of ours": the list, count and delete assertions downstream all read
    // the whole table, so a stray location would quietly invalidate them.
    expect(
      doc.locations.map((l) => l.name),
      `precondition: Schedule 4 anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) must hold NO locations at rest — re-ground fixtures/sch4/schedule4-test-data.ts, or delete the residue`,
    ).toEqual([]);
  },
);

Given(
  'the Schedule 4 location {string} is already saved with:',
  async ({ request, world }, name, table: { hashes: () => Record<string, string>[] }) => {
    expect(
      world.scheduleKey,
      'a Schedule 4 anchor precondition must run before seeding a location',
    ).toBeTruthy();
    const doc = await saveLocation(request, world.scheduleKey!, {
      name,
      categories: seedCategories(table.hashes()),
    });
    const location = requireLocation(doc, name);
    world.sch4LocationName = name;
    world.sch4LocationId = location.id ?? undefined;
  },
);

Given(
  'the Schedule 4 location {string} is already saved with comments {string}',
  async ({ request, world }, name, comments) => {
    const doc = await saveLocation(request, world.scheduleKey!, { name, comments });
    world.sch4LocationName = name;
    world.sch4LocationId = requireLocation(doc, name).id ?? undefined;
  },
);

Given(
  'the Schedule 4 location {string} is already saved with only its name',
  async ({ request, world }, name) => {
    const doc = await saveLocation(request, world.scheduleKey!, { name });
    world.sch4LocationName = name;
    world.sch4LocationId = requireLocation(doc, name).id ?? undefined;
  },
);

Given(
  'that Schedule 4 location already has these {string} rows:',
  async ({ request, world }, label, table: { hashes: () => Record<string, string>[] }) => {
    const def = subPageByLabel(label);
    expect(
      world.sch4LocationId,
      'a seeded-location precondition must run before seeding its sub-page rows',
    ).toBeTruthy();
    let rowId: number | undefined;
    for (const row of table.hashes()) {
      const doc = await addRow(request, world.scheduleKey!, world.sch4LocationId!, {
        type: def.type,
        description: row.description,
        distance: cellNum(row.distance),
        volume: cellNum(row.volume),
        cost: cellNum(row.cost),
        cycle: cellNum(row.cycle),
      });
      const location = requireLocation(doc, world.sch4LocationName!);
      rowId = location.subPageRows.find((r) => r.description === row.description)?.id;
      expect(rowId, `seeded ${label} row "${row.description}" is not in the read-back`).toBeTruthy();
    }
    // The LAST seeded row's id, so an in-place-edit scenario can address its cells (`#row-{id}-{field}`).
    world.sch4RowId = rowId;
    world.sch4SubPageLabel = label;
  },
);

Given('the Schedule 4 read-only anchor {string}', async ({ request, world }, which) => {
  const anchor = READ_ONLY_ANCHORS[which as 'submitted' | 'verified'];
  expect(anchor, `unknown Schedule 4 read-only anchor "${which}" (use submitted|verified)`).toBeTruthy();
  world.scheduleKey = anchor.key;
  world.millOption = millOptionText(anchor.mill);
  world.sch4LocationName = anchor.location;

  const doc = await getSchedule4(request, anchor.key);
  expect(doc.trackStatus, `precondition: anchor "${which}" must still be track "${anchor.trackStatus}"`).toBe(
    anchor.trackStatus,
  );
  expect(doc.editable, `precondition: anchor "${which}" must NOT be editable`).toBe(false);
  // The seed patch supplies the only non-Draft location in the DB that carries amounts to render.
  expect(
    findLocation(doc, anchor.location),
    `precondition: the seed patch location "${anchor.location}" is missing from ${anchor.key.millId}/${anchor.key.year} — run ./scripts/apply-patches.sh`,
  ).toBeTruthy();
});

Given('the Schedule 4 guard anchor {string}', async ({ request, world }, which) => {
  const guard = GUARD_ANCHORS[which];
  expect(guard, `unknown Schedule 4 guard anchor "${which}"`).toBeTruthy();
  world.scheduleKey = guard.key;
  world.millOption = millOptionText(guard.mill);
  // Prove the guard still fires at the API before driving the browser, so a drifted anchor reads as a
  // data problem here rather than as a mysteriously missing banner later.
  const res = await request.get(
    `/api/v1/schedule4?millId=${guard.key.millId}&year=${guard.key.year}`,
  );
  expect(
    res.status(),
    `precondition: guard anchor "${which}" must still return HTTP ${guard.expectHttp}`,
  ).toBe(guard.expectHttp);
});

Given('a spy is watching the Schedule 4 write requests', async ({ schedule4MutationSpy }) => {
  // Referencing the fixture installs its route before any step navigates.
  expect(schedule4MutationSpy.mutations).toBe(0);
});

// ---------------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------------

When('I open Schedule 4', async ({ schedule4Page }) => {
  await schedule4Page.openViaNav();
});

When('I open Schedule 4 expecting a guard message', async ({ schedule4Page }) => {
  await schedule4Page.openViaNavExpectingGuard();
});

When('I open Schedule 4 with no working context', async ({ schedule4Page }) => {
  await schedule4Page.openWithNoContext();
});

When('I reopen Schedule 4', async ({ schedule4Page }) => {
  await schedule4Page.reload();
});

// ---------------------------------------------------------------------------------------------------
// The location list
// ---------------------------------------------------------------------------------------------------

Then('the Schedule 4 location list is empty', async ({ schedule4Page }) => {
  await expect(schedule4Page.emptyState).toBeVisible();
  expect(await schedule4Page.listedLocationNames()).toEqual(['No locations have been added.']);
});

Then('the Schedule 4 location {string} is listed', async ({ schedule4Page }, name) => {
  await expect(schedule4Page.locationRow(name)).toHaveCount(1);
});

Then('the Schedule 4 location {string} is not listed', async ({ schedule4Page }, name) => {
  await expect(schedule4Page.locationRow(name)).toHaveCount(0);
});

When('I note the listed Schedule 4 locations', async ({ schedule4Page, world }) => {
  world.sch4ListedBefore = await schedule4Page.listedLocationNames();
});

Then('the Schedule 4 location list is unchanged', async ({ schedule4Page, world }) => {
  expect(
    world.sch4ListedBefore,
    'note the listed Schedule 4 locations before asserting they are unchanged',
  ).toBeTruthy();
  expect(await schedule4Page.listedLocationNames()).toEqual(world.sch4ListedBefore);
});

Then(
  'the Schedule 4 location {string} offers a {string} action',
  async ({ schedule4Page }, name, action) => {
    await expect(schedule4Page.rowAction(name, action)).toBeVisible();
  },
);

Then(
  'the Schedule 4 location {string} has no {string} action',
  async ({ schedule4Page }, name, action) => {
    await expect(schedule4Page.rowAction(name, action)).toHaveCount(0);
  },
);

Then(
  'the Schedule 4 {string} action for {string} is disabled',
  async ({ schedule4Page }, action, name) => {
    await expect(schedule4Page.rowAction(name, action)).toBeDisabled();
  },
);

Then('the Schedule 4 Add New Location button is disabled', async ({ schedule4Page }) => {
  await expect(schedule4Page.addNewLocationButton).toBeDisabled();
});

Then('the Schedule 4 Add New Location button is enabled', async ({ schedule4Page }) => {
  await expect(schedule4Page.addNewLocationButton).toBeEnabled();
});

Then('the Schedule 4 Check Status button is disabled', async ({ schedule4Page }) => {
  await expect(schedule4Page.checkStatusButton).toBeDisabled();
});

Then('the Schedule 4 Check Status button is enabled', async ({ schedule4Page }) => {
  await expect(schedule4Page.checkStatusButton).toBeEnabled();
});

Then('the Schedule 4 location list is not displayed', async ({ schedule4Page }) => {
  await expect(schedule4Page.locationsTable).toHaveCount(0);
  await expect(schedule4Page.addNewLocationButton).toHaveCount(0);
});

// ---------------------------------------------------------------------------------------------------
// The location panel
// ---------------------------------------------------------------------------------------------------

When('I hover the Schedule 4 location {string} row', async ({ schedule4Page }, name) => {
  // The axe helper parks the pointer before every scan, so a hover-state assertion must set it here.
  await schedule4Page.hoverLocationRow(name);
});

When('I add a new Schedule 4 location', async ({ schedule4Page }) => {
  await schedule4Page.clickAddNewLocation();
});

When('I open the Schedule 4 location {string} for edit', async ({ schedule4Page, world }, name) => {
  await schedule4Page.openLocation(name, 'Edit');
  world.sch4LocationName = name;
});

When('I open the Schedule 4 location {string} for viewing', async ({ schedule4Page, world }, name) => {
  await schedule4Page.openLocation(name, 'View');
  world.sch4LocationName = name;
});

When('I copy the Schedule 4 location {string}', async ({ schedule4Page }, name) => {
  await schedule4Page.copyLocation(name);
});

Then('the Schedule 4 panel heading is {string}', async ({ schedule4Page }, heading) => {
  await expect(schedule4Page.panelHeading).toHaveText(heading);
});

Then('the Schedule 4 location panel is closed', async ({ schedule4Page }) => {
  await expect(schedule4Page.panel).toHaveCount(0);
});

When('I enter {string} as the Schedule 4 location name', async ({ schedule4Page }, value) => {
  await schedule4Page.setName(value);
});

When('I enter Schedule 4 comments {string}', async ({ schedule4Page }, value) => {
  await schedule4Page.setComments(value);
});

When(
  'I enter {string} in the Schedule 4 {string} {string} cell',
  async ({ schedule4Page }, value, label, field) => {
    await schedule4Page.setCategoryField(label, field as 'volume' | 'cost' | 'distance', value);
  },
);

When(
  'I clear the Schedule 4 {string} {string} cell',
  async ({ schedule4Page }, label, field) => {
    await schedule4Page.setCategoryField(label, field as 'volume' | 'cost' | 'distance', '');
  },
);

When(
  'I enter the following Schedule 4 category amounts:',
  async ({ schedule4Page }, table: { hashes: () => Record<string, string>[] }) => {
    for (const row of table.hashes()) {
      for (const field of ['distance', 'volume', 'cost'] as const) {
        const value = (row[field] ?? '').trim();
        if (value === '') continue; // an omitted column is "not entered", not "entered blank"
        await schedule4Page.setCategoryField(row.category.trim(), field, value);
      }
    }
  },
);

When('I save the Schedule 4 location', async ({ schedule4Page }) => {
  await schedule4Page.clickSave();
});

When('I go back from the Schedule 4 panel', async ({ schedule4Page }) => {
  await schedule4Page.clickBack('Back');
});

When('I close the Schedule 4 view panel', async ({ schedule4Page }) => {
  await schedule4Page.clickBack('Close');
});

Then('the Schedule 4 location name shows {string}', async ({ schedule4Page }, expected) => {
  await expect(schedule4Page.nameInput).toHaveValue(expected);
});

Then('the Schedule 4 location name is empty', async ({ schedule4Page }) => {
  await expect(schedule4Page.nameInput).toHaveValue('');
});

Then(
  'the Schedule 4 location name field is invalid with {string}',
  async ({ schedule4Page }, message) => {
    await expect(schedule4Page.nameError).toHaveText(message);
  },
);

Then('the Schedule 4 comments show {string}', async ({ schedule4Page }, expected) => {
  await expect(schedule4Page.commentsInput).toHaveValue(expected);
});

Then('the Schedule 4 view panel shows the comments {string}', async ({ schedule4Page }, expected) => {
  await expect(schedule4Page.commentsReadOnly).toHaveText(expected);
});

Then('the Schedule 4 view panel shows the location name {string}', async ({ schedule4Page }, name) => {
  await expect(schedule4Page.nameReadOnly).toHaveText(`Location Name: ${name}`);
});

// ---------------------------------------------------------------------------------------------------
// The category grid
// ---------------------------------------------------------------------------------------------------

Then(
  'the Schedule 4 {string} {string} cell shows {string}',
  async ({ schedule4Page }, label, field, expected) => {
    await expect(schedule4Page.categoryField(label, field as 'volume' | 'cost' | 'distance')).toHaveValue(
      expected,
    );
  },
);

Then(
  'the Schedule 4 {string} {string} cell is invalid with {string}',
  async ({ schedule4Page }, label, field, message) => {
    await expect(
      schedule4Page.categoryFieldError(label, field as 'volume' | 'cost' | 'distance'),
    ).toHaveText(message);
  },
);

Then(
  'the Schedule 4 {string} {string} cell has no inline error',
  async ({ schedule4Page }, label, field) => {
    await expect(
      schedule4Page.categoryFieldError(label, field as 'volume' | 'cost' | 'distance'),
    ).toHaveCount(0);
  },
);

Then(
  'the Schedule 4 category grid shows:',
  async ({ schedule4Page }, table: { hashes: () => Record<string, string>[] }) => {
    for (const row of table.hashes()) {
      const label = row.category.trim();
      const actual = await schedule4Page.categoryRowValues(label);
      expect(
        actual,
        `Schedule 4 grid row "${label}" — expected [distance, volume, cost, $/m³]`,
      ).toEqual([
        (row.distance ?? '').trim(),
        (row.volume ?? '').trim(),
        (row.cost ?? '').trim(),
        (row.perUnit ?? '').trim(),
      ]);
    }
  },
);

Then('the Schedule 4 grid rows are in legacy order', async ({ schedule4Page }) => {
  // The 12 category rows and the 3 sub-page group rows interleaved by legacy cost-item code (40-55),
  // with the dead code 54 absent. The sub-page labels carry their live row count, so compare on the
  // count-stripped label.
  const actual = (await schedule4Page.gridRowLabels()).map((label) =>
    label.replace(/\(\d+\):$/, '(0):'),
  );
  expect(actual).toEqual(GRID_ROW_LABELS);
});

Then('the Schedule 4 category grid is read-only', async ({ schedule4Page }) => {
  // ZERO inputs: a read-only cell renders its value as text, so any input at all means the panel came
  // up editable. This is what makes the read-only VALUE assertions meaningful (see categoryRowValues).
  expect(
    await schedule4Page.editableGridFieldIds(),
    'the Schedule 4 category grid must render no inputs when the report is not in Draft',
  ).toEqual([]);
});

Then(
  'the Schedule 4 sub-page link {string} shows {int} rows',
  async ({ schedule4Page }, label, expected) => {
    expect(await schedule4Page.subPageCount(label)).toBe(expected);
  },
);

Then(
  'the Schedule 4 sub-page row {string} totals show:',
  async ({ schedule4Page }, label, table: { hashes: () => Record<string, string>[] }) => {
    const [row] = table.hashes();
    const actual = await schedule4Page.subPageRowTotals(label);
    expect(actual, `Schedule 4 grid totals for "${label}"`).toEqual([
      (row.distance ?? '').trim(),
      (row.volume ?? '').trim(),
      (row.cost ?? '').trim(),
      (row.perUnit ?? '').trim(),
      (row.cycle ?? '').trim(),
    ]);
  },
);

// ---------------------------------------------------------------------------------------------------
// Delete (NAV-004)
// ---------------------------------------------------------------------------------------------------

When('I delete the Schedule 4 location {string}', async ({ schedule4Page }, name) => {
  await schedule4Page.clickDeleteLocation(name);
});

Then('the Schedule 4 delete confirmation asks {string}', async ({ schedule4Page }, message) => {
  await expect(schedule4Page.deleteModal).toContainText(message);
  await expect(schedule4Page.deleteModalPrimaryButton).toBeVisible();
});

When('I confirm the Schedule 4 delete', async ({ schedule4Page }) => {
  await schedule4Page.confirmDelete();
});

When('I cancel the Schedule 4 delete', async ({ schedule4Page }) => {
  await schedule4Page.cancelDelete();
});

Then('the Schedule 4 delete confirmation is dismissed', async ({ schedule4Page }) => {
  await expect(schedule4Page.deleteModal).toHaveCount(0);
});

// ---------------------------------------------------------------------------------------------------
// Notifications / guards
// ---------------------------------------------------------------------------------------------------

Then('the Schedule 4 mill and reporting year guard message is shown', async ({ schedule4Page }) => {
  await expect(schedule4Page.notification(CLIENT.millYearNotSelected)).toBeVisible();
  await expect(schedule4Page.notification(CLIENT.millYearNotSelectedTitle)).toBeVisible();
});

Then('the Schedule 4 page is blocked with {string}', async ({ schedule4Page }, detail) => {
  await expect(schedule4Page.notification(detail)).toBeVisible();
  await expect(schedule4Page.notification(CLIENT.titleLoadFailed)).toBeVisible();
});

// ---------------------------------------------------------------------------------------------------
// Mutation spy — prove the negative
// ---------------------------------------------------------------------------------------------------

When('I note the Schedule 4 mutation count', async ({ schedule4MutationSpy, world }) => {
  world.sch4MutationsBefore = schedule4MutationSpy.mutations;
});

Then(
  'the Schedule 4 write request should not have been sent',
  async ({ page, schedule4MutationSpy }) => {
    // Cross the deterministic barrier first: a regression that renders the inline error and THEN fires
    // the request a tick later would otherwise read a tally of 0 and pass green.
    await settleBeforeReadingSpy(page);
    expect(
      schedule4MutationSpy.mutations,
      'a client-side rejection must not send any Schedule 4 write',
    ).toBe(0);
  },
);

Then(
  'no further Schedule 4 write should have been sent',
  async ({ page, schedule4MutationSpy, world }) => {
    expect(
      world.sch4MutationsBefore,
      'note the Schedule 4 mutation count before asserting no FURTHER write',
    ).toBeDefined();
    await settleBeforeReadingSpy(page);
    expect(
      schedule4MutationSpy.mutations,
      'no further Schedule 4 write may be sent after the rejected action',
    ).toBe(world.sch4MutationsBefore);
  },
);

// ---------------------------------------------------------------------------------------------------
// API read-backs (the source of truth for "it was stored")
// ---------------------------------------------------------------------------------------------------

Then('no Schedule 4 locations are stored', async ({ request, world }) => {
  // Poll: the assertion follows a UI-triggered write attempt, so a single-shot GET could win a race with
  // a request that HAD been fired.
  await expect
    .poll(
      async () => (await getSchedule4(request, world.scheduleKey!)).locations.map((l) => l.name),
      { message: 'no Schedule 4 location may be stored on this anchor' },
    )
    .toEqual([]);
});

Then('no Schedule 4 location named {string} is stored', async ({ request, world }, name) => {
  await expect
    .poll(
      async () =>
        findLocation(await getSchedule4(request, world.scheduleKey!), name) === undefined,
      { message: `Schedule 4 location "${name}" must not be stored` },
    )
    .toBe(true);
});

Then('the stored Schedule 4 location {string} is:', async ({ request, world }, name, table: { hashes: () => Record<string, string>[] }) => {
  // Poll the whole read-back: the save is fired by a UI click, so the commit can trail the success
  // banner by a tick.
  await expect
    .poll(
      async () => {
        const doc = await getSchedule4(request, world.scheduleKey!);
        const location = findLocation(doc, name);
        if (!location) return null;
        return table.hashes().map((row) => {
          const label = row.category.trim();
          const stored = category(location, categoryCode(label));
          return {
            category: label,
            distance: stored?.distance ?? null,
            volume: stored?.volume ?? null,
            cost: stored?.cost ?? null,
            perUnit: stored?.perUnit ?? null,
          };
        });
      },
      { message: `the stored Schedule 4 location "${name}" must match the expected amounts` },
    )
    .toEqual(
      table.hashes().map((row) => ({
        category: row.category.trim(),
        distance: cellNum(row.distance),
        volume: cellNum(row.volume),
        cost: cellNum(row.cost),
        perUnit: cellNum(row.perUnit),
      })),
    );
});

Then(
  'the stored Schedule 4 location {string} has the comments {string}',
  async ({ request, world }, name, expected) => {
    await expect
      .poll(
        async () =>
          requireLocation(await getSchedule4(request, world.scheduleKey!), name).comments ?? null,
        { message: `stored comments for Schedule 4 location "${name}"` },
      )
      .toBe(expected === '' ? null : expected);
  },
);

Then('the stored Schedule 4 location {string} carries no amounts', async ({ request, world }, name) => {
  await expect
    .poll(
      async () =>
        requireLocation(await getSchedule4(request, world.scheduleKey!), name).categories.length,
      { message: `Schedule 4 location "${name}" must carry no category amounts` },
    )
    .toBe(0);
});

Then('the Schedule 4 anchor stores {int} locations', async ({ request, world }, expected) => {
  await expect
    .poll(async () => (await getSchedule4(request, world.scheduleKey!)).locations.length, {
      message: 'stored Schedule 4 location count',
    })
    .toBe(expected);
});

Then(
  'the stored Schedule 4 location {string} revision is {int}',
  async ({ request, world }, name, expected) => {
    await expect
      .poll(
        async () =>
          requireLocation(await getSchedule4(request, world.scheduleKey!), name).revisionCount,
        { message: `stored revision for Schedule 4 location "${name}"` },
      )
      .toBe(expected);
  },
);
