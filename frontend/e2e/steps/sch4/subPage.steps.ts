import { Then, When, expect } from '../fixtures';
import { type RowField } from '../../pages/sch4/schedule4SubPage';
import { subPageByLabel } from '../../fixtures/sch4/schedule4-test-data';
import { getSchedule4, requireLocation, rowsOfType } from './schedule4Api';

/**
 * Schedule 4 sub-page steps — the three list sub-pages (Towing Total 43 / Truck Rehaul-Dewater/Transfer
 * 46 / Other Transportation 55): the NAV-002/NAV-003 save-before-navigation flows, the add-row form, the
 * per-row in-place edit, the NAV-005 row delete, the running totals, and the column sort.
 *
 * The sub-page is a URL STATE of `/schedule-4` (`?loc=<id>&sub=<TYPE>`), not a separate view, so "open"
 * here means "confirm the nav modal and land on the sub-page state", and browser Back returns to the list.
 */

/** Blank data-table cell → an unset field. Kept identical to the main step file's rule. */
const cell = (raw: string | undefined): string => (raw ?? '').trim();

// ---------------------------------------------------------------------------------------------------
// Opening a sub-page
// ---------------------------------------------------------------------------------------------------

When(
  'I click the Schedule 4 {string} sub-page link',
  async ({ schedule4Page, world }, label) => {
    world.sch4SubPageLabel = label;
    await schedule4Page.clickSubPageLink(label);
  },
);

When(
  'I open the Schedule 4 {string} sub-page from the saved location',
  async ({ schedule4Page, schedule4SubPage, world }, label) => {
    // NAV-002 — leaving a SAVED location's panel: unsaved panel edits are discarded, no save happens.
    world.sch4SubPageLabel = label;
    await schedule4Page.openSubPage(label, 'existing');
    await schedule4SubPage.expectOpen(label);
  },
);

When(
  'I open the Schedule 4 {string} sub-page from the new location',
  async ({ schedule4Page, schedule4SubPage, world }, label) => {
    // NAV-003 — leaving an UNSAVED new location: the app saves it first, then opens the sub-page.
    world.sch4SubPageLabel = label;
    await schedule4Page.openSubPage(label, 'new');
    await schedule4SubPage.expectOpen(label);
  },
);

When(
  'I open the Schedule 4 {string} sub-page directly',
  async ({ schedule4Page, schedule4SubPage, world }, label) => {
    // View mode: a read-only panel opens the sub-page with no confirmation at all.
    world.sch4SubPageLabel = label;
    await schedule4Page.clickSubPageLink(label);
    await schedule4SubPage.expectOpen(label);
  },
);

/**
 * The two nav confirmations are addressed by NAME rather than "whichever is open", so a scenario states
 * which legacy prompt it expects and the step can never silently assert the wrong one:
 *   - "unsaved-changes" = NAV-002, leaving a SAVED location's panel (edits discarded);
 *   - "save-first"      = NAV-003, leaving an UNSAVED new location (the app saves it first).
 */
Then(
  'the Schedule 4 unsaved-changes confirmation asks {string}',
  async ({ schedule4Page }, message) => {
    await expect(schedule4Page.navExistingModal).toBeVisible();
    await expect(schedule4Page.navExistingModal).toContainText(message);
  },
);

Then(
  'the Schedule 4 save-first confirmation asks {string}',
  async ({ schedule4Page }, message) => {
    await expect(schedule4Page.navNewModal).toBeVisible();
    await expect(schedule4Page.navNewModal).toContainText(message);
  },
);

/**
 * The confirmation legacy raised from the SUB-PAGE's Back button (NAV-001) — a different control from the
 * two above, which guard the location PANEL. Asserted by message rather than by a named modal because the
 * app has no such dialog yet (DIV-3); see `schedule4SubPage.confirmDialogAsking`.
 */
Then(
  'the Schedule 4 sub-page unsaved-changes confirmation asks {string}',
  async ({ schedule4SubPage }, message) => {
    await expect(schedule4SubPage.confirmDialogAsking(message)).toBeVisible();
  },
);

/**
 * Confirm a nav prompt WITHOUT asserting where it lands. Deliberately separate from the compound
 * "open … from the saved/new location" steps: NAV-003's blocked arm confirms the prompt and must then
 * STAY on the panel, so a step that waited for the sub-page URL could never express it.
 */
When('I confirm the Schedule 4 save-first prompt', async ({ schedule4Page }) => {
  await schedule4Page.resolveNavPrompt('new', 'confirm');
});

When('I confirm the Schedule 4 unsaved-changes prompt', async ({ schedule4Page }) => {
  await schedule4Page.resolveNavPrompt('existing', 'confirm');
});

Then('the Schedule 4 {string} sub-page is open', async ({ schedule4SubPage, world }, label) => {
  world.sch4SubPageLabel = label;
  await schedule4SubPage.expectOpen(label);
});

Then('the Schedule 4 sub-page is not open', async ({ page }) => {
  await expect(page).not.toHaveURL(/sub=/);
});

When('I cancel the Schedule 4 unsaved-changes prompt', async ({ schedule4Page }) => {
  await schedule4Page.resolveNavPrompt('existing', 'cancel');
});

When('I cancel the Schedule 4 save-first prompt', async ({ schedule4Page }) => {
  await schedule4Page.resolveNavPrompt('new', 'cancel');
});

Then('the Schedule 4 sub-page trail shows {string}', async ({ schedule4Page }, expected) => {
  await expect(schedule4Page.trail).toContainText(expected);
});

When('I go back from the Schedule 4 sub-page', async ({ schedule4SubPage }) => {
  await schedule4SubPage.clickBack();
});

When('I press the browser Back button', async ({ page }) => {
  await page.goBack();
});

// ---------------------------------------------------------------------------------------------------
// The add-row form
// ---------------------------------------------------------------------------------------------------

When(
  'I enter the following Schedule 4 row values:',
  async ({ schedule4SubPage }, table: { hashes: () => Record<string, string>[] }) => {
    const [row] = table.hashes();
    for (const field of ['description', 'distance', 'volume', 'cost', 'cycle'] as const) {
      const value = cell(row[field]);
      if (value === '') continue; // an omitted column is "not entered"
      await schedule4SubPage.setAddField(field, value);
    }
  },
);

When(
  'I enter {string} in the Schedule 4 row {string} field',
  async ({ schedule4SubPage }, value, field) => {
    await schedule4SubPage.setAddField(field as RowField, value);
  },
);

When('I add the Schedule 4 row', async ({ schedule4SubPage }) => {
  await schedule4SubPage.clickAddRow();
});

Then(
  'the Schedule 4 row {string} field is invalid with {string}',
  async ({ schedule4SubPage }, field, message) => {
    await expect(schedule4SubPage.addFieldError(field as RowField)).toHaveText(message);
  },
);

Then('the Schedule 4 row {string} field is not rendered', async ({ schedule4SubPage }, field) => {
  await expect(schedule4SubPage.addField(field as RowField)).toHaveCount(0);
});

Then('the Schedule 4 add-row form is not rendered', async ({ schedule4SubPage, world }) => {
  await expect(schedule4SubPage.addForm).toHaveCount(0);
  await expect(schedule4SubPage.addFormHeading(world.sch4SubPageLabel!)).toHaveCount(0);
});

// ---------------------------------------------------------------------------------------------------
// The rows table
// ---------------------------------------------------------------------------------------------------

Then(
  'the Schedule 4 {string} row {string} is listed',
  async ({ schedule4SubPage }, label, description) => {
    // Poll the live descriptions: the row appears once the add-row POST's recomputed document lands, so a
    // single-shot read can race the commit.
    await expect
      .poll(async () => schedule4SubPage.rowDescriptions(label), {
        message: `Schedule 4 ${label} rows must include "${description}"`,
      })
      .toContain(description);
  },
);

Then(
  'the Schedule 4 {string} row {string} is not listed',
  async ({ schedule4SubPage }, label, description) => {
    await expect
      .poll(async () => schedule4SubPage.rowDescriptions(label), {
        message: `Schedule 4 ${label} rows must NOT include "${description}"`,
      })
      .not.toContain(description);
  },
);

Then('the Schedule 4 {string} table is empty', async ({ schedule4SubPage }, label) => {
  await expect(schedule4SubPage.emptyState(label)).toBeVisible();
});

Then(
  'the Schedule 4 {string} table lists {int} rows',
  async ({ schedule4SubPage }, label, expected) => {
    await expect
      .poll(async () => schedule4SubPage.rowCount(label), {
        message: `Schedule 4 ${label} row count`,
      })
      .toBe(expected);
  },
);

Then(
  'the Schedule 4 {string} row {string} shows:',
  async ({ schedule4SubPage }, label, description, table: { hashes: () => Record<string, string>[] }) => {
    const [expected] = table.hashes();
    const hasCycle = subPageByLabel(label).hasCycle;
    const wanted = [
      description,
      cell(expected.distance),
      cell(expected.volume),
      cell(expected.cost),
      ...(hasCycle ? [cell(expected.cycle)] : []),
      cell(expected.perUnit),
    ];
    await expect
      .poll(async () => schedule4SubPage.rowValues(label, description), {
        message: `Schedule 4 ${label} row "${description}" — [description, distance, volume, cost${hasCycle ? ', cycle' : ''}, $/m³]`,
      })
      .toEqual(wanted);
  },
);

Then(
  'the Schedule 4 {string} totals show:',
  async ({ schedule4SubPage }, label, table: { hashes: () => Record<string, string>[] }) => {
    const [expected] = table.hashes();
    const hasCycle = subPageByLabel(label).hasCycle;
    const wanted = [
      cell(expected.distance),
      cell(expected.volume),
      cell(expected.cost),
      ...(hasCycle ? [cell(expected.cycle)] : []),
    ];
    await expect
      .poll(async () => schedule4SubPage.totals(label), {
        message: `Schedule 4 ${label} running totals — [distance, volume, cost${hasCycle ? ', cycle' : ''}]`,
      })
      .toEqual(wanted);
  },
);

Then('the Schedule 4 {string} rows are read-only', async ({ schedule4SubPage }, label) => {
  // ZERO inputs in the table: a read-only row renders its values as text. Same reasoning as the
  // category grid's read-only assertion — it is what makes the value assertions mean anything.
  expect(
    await schedule4SubPage.editableRowFieldIds(label),
    `the Schedule 4 ${label} table must render no inputs when the report is not in Draft`,
  ).toEqual([]);
});

// ---------------------------------------------------------------------------------------------------
// In-place row edit (the control the legacy `.feature` omitted — see coverage.md Spec gap)
// ---------------------------------------------------------------------------------------------------

When(
  'I change the seeded Schedule 4 row {string} to {string}',
  async ({ schedule4SubPage, world }, field, value) => {
    expect(
      world.sch4RowId,
      'a seeded-row precondition must run before editing the row in place',
    ).toBeTruthy();
    await schedule4SubPage.setRowField(world.sch4RowId!, field as RowField, value);
  },
);

Then(
  'the seeded Schedule 4 row {string} cell is invalid with {string}',
  async ({ schedule4SubPage, world }, field, message) => {
    await expect(schedule4SubPage.rowFieldError(world.sch4RowId!, field as RowField)).toHaveText(
      message,
    );
  },
);

When('I save the Schedule 4 sub-page', async ({ schedule4SubPage }) => {
  await schedule4SubPage.clickSave();
});

// ---------------------------------------------------------------------------------------------------
// Row delete (NAV-005)
// ---------------------------------------------------------------------------------------------------

When(
  'I delete the Schedule 4 {string} row {string}',
  async ({ schedule4SubPage }, label, description) => {
    await schedule4SubPage.clickDeleteRow(label, description);
  },
);

Then('the Schedule 4 row delete confirmation asks {string}', async ({ schedule4SubPage }, message) => {
  await expect(schedule4SubPage.deleteRowModal).toContainText(message);
});

When('I confirm the Schedule 4 row delete', async ({ schedule4SubPage }) => {
  await schedule4SubPage.confirmDeleteRow();
});

// ---------------------------------------------------------------------------------------------------
// Column sort
// ---------------------------------------------------------------------------------------------------

When(
  'I sort the Schedule 4 {string} rows by {string}',
  async ({ schedule4SubPage }, label, column) => {
    await schedule4SubPage.clickColumnHeader(label, column);
  },
);

Then(
  'the Schedule 4 {string} row order is {string}',
  async ({ schedule4SubPage }, label, expected) => {
    const wanted = expected.split(',').map((part: string) => part.trim());
    expect(await schedule4SubPage.rowDescriptions(label)).toEqual(wanted);
  },
);

Then(
  'the Schedule 4 {string} {string} column is sorted {string}',
  async ({ schedule4SubPage }, label, column, direction) => {
    expect(await schedule4SubPage.columnSort(label, column)).toBe(direction);
  },
);

// ---------------------------------------------------------------------------------------------------
// API read-backs for sub-page rows
// ---------------------------------------------------------------------------------------------------

Then(
  'the stored Schedule 4 {string} rows for {string} are:',
  async ({ request, world }, label, name, table: { hashes: () => Record<string, string>[] }) => {
    const def = subPageByLabel(label);
    const expected = table.hashes().map((row) => ({
      description: cell(row.description),
      distance: cell(row.distance) === '' ? null : Number(cell(row.distance)),
      volume: cell(row.volume) === '' ? null : Number(cell(row.volume)),
      cost: cell(row.cost) === '' ? null : Number(cell(row.cost)),
      cycle: cell(row.cycle) === '' ? null : Number(cell(row.cycle)),
      perUnit: cell(row.perUnit) === '' ? null : Number(cell(row.perUnit)),
    }));
    await expect
      .poll(
        async () => {
          const doc = await getSchedule4(request, world.scheduleKey!);
          const location = requireLocation(doc, name);
          return rowsOfType(location, def.code).map((row) => ({
            description: row.description ?? '',
            distance: row.distance ?? null,
            volume: row.volume ?? null,
            cost: row.cost ?? null,
            cycle: row.cycle ?? null,
            perUnit: row.perUnit ?? null,
          }));
        },
        { message: `the stored Schedule 4 ${label} rows for location "${name}"` },
      )
      .toEqual(expected);
  },
);

Then(
  'no Schedule 4 {string} rows are stored for {string}',
  async ({ request, world }, label, name) => {
    const def = subPageByLabel(label);
    await expect
      .poll(
        async () => {
          const doc = await getSchedule4(request, world.scheduleKey!);
          const location = requireLocation(doc, name);
          return rowsOfType(location, def.code).length;
        },
        { message: `no Schedule 4 ${label} row may be stored for "${name}"` },
      )
      .toBe(0);
  },
);
