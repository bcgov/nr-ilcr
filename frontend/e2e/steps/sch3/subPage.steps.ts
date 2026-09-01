import { Given, When, Then, expect } from '../fixtures';
import { settleBeforeReadingSpy } from '../../pages/common/settle';
import {
  CONFIRM_NAVIGATION_BODY,
  OA_ROW,
  OA_ROW_EDITED_TOTAL,
  SEEDED_OTHER_ACCEPTABLE,
  UNACCEPTABLE_ROW,
} from '../../fixtures/sch3/schedule3-test-data';
import { getOtherAcceptable, getUnacceptable, putOtherAcceptable } from './schedule3Api';

/**
 * UC-SCH3-001 — the two Schedule 3 cost sub-pages (AF1 / AF2 and their exception paths).
 *
 * One step set serves both pages because the rewrite renders them from one generic component; the
 * scenario names which page it is on (`world.sch3SubPageTitle`, set by the navigation step), so a step
 * never has to repeat it. Every selector lives in `pages/sch3/schedule3SubPage.ts`.
 *
 * The sub-page persistence model the assertions rely on (`hooks/useEditableCostRows`): Add, Remove and
 * Save each PUT the WHOLE row set and the server reconciles insert/update/delete. A row that fails the
 * advisory validation is never sent — which is what the zero-write assertions prove.
 */

const title = (world: { sch3SubPageTitle?: string }): string => {
  expect(
    world.sch3SubPageTitle,
    'no sub-page is open — a navigation step must run before this one',
  ).toBeTruthy();
  return world.sch3SubPageTitle!;
};

// ---------------------------------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------------------------------

When('I add an other-acceptable cost row', async ({ schedule3SubPage, world }) => {
  await schedule3SubPage.addRow({
    description: OA_ROW.description,
    total: String(OA_ROW.total),
    pop: String(OA_ROW.pop),
  });
  world.sch3RowDescription = OA_ROW.description;
});

When('I add an included-unacceptable cost row', async ({ schedule3SubPage, world }) => {
  await schedule3SubPage.addRow({
    description: UNACCEPTABLE_ROW.description,
    total: String(UNACCEPTABLE_ROW.total),
  });
  world.sch3RowDescription = UNACCEPTABLE_ROW.description;
});

When(
  'I add a sub-page row with no description and a total of {string}',
  async ({ schedule3SubPage }, total) => {
    await schedule3SubPage.addRow({ description: '', total });
  },
);

When(
  'I add a sub-page row described {string} with a total of {string}',
  async ({ schedule3SubPage, world }, description, total) => {
    await schedule3SubPage.addRow({ description, total });
    world.sch3RowDescription = description;
  },
);

When('I change the added row total to the edited value', async ({ schedule3SubPage, world }) => {
  await schedule3SubPage.editRowValue(
    title(world),
    world.sch3RowDescription!,
    'total',
    String(OA_ROW_EDITED_TOTAL),
  );
});

When('I save the sub-page', async ({ schedule3SubPage }) => {
  await schedule3SubPage.save();
});

When('I remove the added row', async ({ schedule3SubPage, world }) => {
  await schedule3SubPage.removeRow(title(world), world.sch3RowDescription!);
});

When('I go back to Schedule 3', async ({ schedule3SubPage }) => {
  await schedule3SubPage.back();
});

When('I note the sub-page write count', async ({ schedule3MutationSpy, world }) => {
  world.sch3MutationsBefore = schedule3MutationSpy.mutations;
});

// ---------------------------------------------------------------------------------------------------
// Preconditions
// ---------------------------------------------------------------------------------------------------

Given(
  'an other-acceptable cost row has already been saved',
  async ({ request, world }) => {
    // Seeded through the app's own batch endpoint, not typed: the behaviour under test is what happens
    // when the row is REMOVED, not how it was created (S04 covers that).
    await putOtherAcceptable(request, world.scheduleKey!, [
      { description: OA_ROW.description, total: OA_ROW.total, pop: OA_ROW.pop },
    ]);
    world.sch3RowDescription = OA_ROW.description;
  },
);

// ---------------------------------------------------------------------------------------------------
// GAP-3 — leaving a sub-page with an UNSAVED in-place edit. The inbound confirm is asserted on every
// navigation (`schedule3Page.openSubPage`); these cover the way OUT, which nothing asserted before.
// ---------------------------------------------------------------------------------------------------

Then('the sub-page lists the seeded other-acceptable row', async ({ schedule3SubPage, world }) => {
  world.sch3RowDescription = SEEDED_OTHER_ACCEPTABLE.description;
  expect(await schedule3SubPage.descriptions(title(world))).toContain(
    SEEDED_OTHER_ACCEPTABLE.description,
  );
});

/** In-place edit only — Save is never pressed, which is what leaves the page dirty. */
When('I change the seeded row total to {string}', async ({ schedule3SubPage, world }, value) => {
  await schedule3SubPage.editRowValue(
    title(world),
    SEEDED_OTHER_ACCEPTABLE.description,
    'total',
    value,
  );
});

When('I press Back on the sub-page', async ({ schedule3SubPage }) => {
  await schedule3SubPage.pressBack();
});

Then('the sub-page warns me about leaving with unsaved edits', async ({ schedule3SubPage }) => {
  await expect(
    schedule3SubPage.leaveDialog,
    'Back walked away from an unsaved row edit with no warning (legacy confirmed with '
      + 'confirmNavigationMsg on the sub-page Back button)',
  ).toBeVisible();
  // Same verbatim legacy string the inbound confirm uses — one message, both directions.
  await expect(
    schedule3SubPage.leaveDialog.getByText(CONFIRM_NAVIGATION_BODY, { exact: true }),
  ).toBeVisible();
});

When('I cancel leaving the sub-page', async ({ schedule3SubPage }) => {
  await schedule3SubPage.cancelLeave();
});

Then('Schedule 3 is displayed', async ({ schedule3Page }) => {
  await expect(schedule3Page.costTable).toBeVisible();
});

When('I confirm leaving the sub-page', async ({ schedule3SubPage }) => {
  await schedule3SubPage.confirmLeave();
});

Then('the sub-page row total reads {string}', async ({ schedule3SubPage, world }, expected) => {
  expect(
    await schedule3SubPage.rowValue(title(world), SEEDED_OTHER_ACCEPTABLE.description, 'total'),
    'cancelling the leave prompt discarded the edit it was supposed to protect',
  ).toBe(expected);
});

/**
 * The half that makes "discard" mean discard. An app that persisted on the way out would look identical
 * on screen, so this reads the stored row through the API.
 */
Then('the stored other-acceptable row total is unchanged', async ({ request, world }) => {
  const doc = await getOtherAcceptable(request, world.scheduleKey!);
  const row = (doc.rows ?? []).find(
    (r) => r.description === SEEDED_OTHER_ACCEPTABLE.description,
  );
  expect(row, 'the seeded other-acceptable row is gone from storage').toBeTruthy();
  expect(
    row?.total,
    'leaving the sub-page PERSISTED an edit the reporter chose to discard',
  ).toBe(SEEDED_OTHER_ACCEPTABLE.total);
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the rendered sub-page
// ---------------------------------------------------------------------------------------------------

/**
 * DIV-5's assertion: removing a row must ASK first. Deliberately RED today — the trash button goes
 * straight to `useEditableCostRows.removeRow` -> `persist(next, 'delete')` with no dialog, so a
 * mis-click destroys a recorded cost with no undo. Legacy prompted with `confirmDeleteMsg`
 * (`schedule3SubtotalOtherCosts.xhtml:94-96`).
 */
Then('the sub-page asks me to confirm the removal', async ({ schedule3SubPage }) => {
  await expect(
    schedule3SubPage.anyDialog,
    'removing a sub-page row destroyed it immediately with no confirmation and no undo — legacy asked ' +
      '"This will delete the current record. Do you want to continue?" first (defects.md DIV-5)',
  ).toBeVisible();
});

/** The other half of DIV-5: until the prompt is confirmed, the row must still exist. */
Then('the removed row is still stored', async ({ request, world }) => {
  const doc = await getOtherAcceptable(request, world.scheduleKey!);
  expect(
    (doc.rows ?? []).map((r) => r.description),
    'the row was deleted before any confirmation was given (defects.md DIV-5)',
  ).toContain(world.sch3RowDescription!);
});

Then('the sub-page lists the added row', async ({ schedule3SubPage, world }) => {
  await expect
    .poll(async () => schedule3SubPage.descriptions(title(world)), {
      message: `"${world.sch3RowDescription}" never appeared in the "${title(world)}" list`,
    })
    .toContain(world.sch3RowDescription!);
});

Then('the sub-page no longer lists the added row', async ({ schedule3SubPage, world }) => {
  await expect
    .poll(async () => schedule3SubPage.descriptions(title(world)), {
      message: `"${world.sch3RowDescription}" is still listed after the removal`,
    })
    .not.toContain(world.sch3RowDescription!);
});

Then('the sub-page shows no records', async ({ schedule3SubPage, world }) => {
  await expect(schedule3SubPage.emptyState(title(world))).toBeVisible();
});

Then(
  'the added row shows a total of {string} and a Crown of {string}',
  async ({ schedule3SubPage, world }, total, crown) => {
    await expect
      .poll(
        async () => [
          await schedule3SubPage.rowValue(title(world), world.sch3RowDescription!, 'total'),
          await schedule3SubPage.rowDerivedCell(title(world), world.sch3RowDescription!, 'Crown'),
        ],
        { message: `the added row never showed total ${total} / Crown ${crown}` },
      )
      .toEqual([total, crown]);
  },
);

Then('the sub-page Totals row shows {string}', async ({ schedule3SubPage, world }, expected) => {
  await expect
    .poll(async () => (await schedule3SubPage.totalsCells(title(world))).join(' / '), {
      message: `the "${title(world)}" Totals footer never showed ${expected}`,
    })
    .toBe(expected);
});

Then(
  // The parentheses are ESCAPED: an unescaped "(" opens an optional group in a Cucumber expression, so
  // the step would silently never match the literal label.
  'the Annual Rents \\(Forest Act, S111) figure shows {string}',
  async ({ schedule3SubPage }, expected) => {
    expect(await schedule3SubPage.annualRentsS111Value()).toBe(expected);
    // BR-04/BR-07: the figure is carried from the main page's Annual Rents Harvest amount and is never
    // enterable here.
    await expect(schedule3SubPage.annualRentsS111).toBeDisabled();
  },
);

Then('the sub-page row is not added', async ({ schedule3SubPage, world }) => {
  await expect(schedule3SubPage.emptyState(title(world))).toBeVisible();
});

Then('the sub-page rows are read-only', async ({ schedule3SubPage, world }) => {
  // Proven by counting what IS rendered: the read-only render has no row inputs and no Add panel.
  const editable = await schedule3SubPage
    .table(title(world))
    .getByRole('textbox', { name: 'Edit description' })
    .count();
  expect(editable, 'the read-only sub-page still renders editable row inputs').toBe(0);
  await expect(schedule3SubPage.addDescription).toHaveCount(0);
  await expect(schedule3SubPage.saveButton).toHaveCount(0);
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the stored rows (API read-back)
// ---------------------------------------------------------------------------------------------------

Then('the stored other-acceptable rows are the added row', async ({ request, world }) => {
  await expect
    .poll(
      async () => {
        const doc = await getOtherAcceptable(request, world.scheduleKey!);
        return (doc.rows ?? []).map((r) => [r.description, r.total ?? null, r.pop ?? null, r.crown ?? null]);
      },
      { message: 'the added other-acceptable group was never stored' },
    )
    .toEqual([[OA_ROW.description, OA_ROW.total, OA_ROW.pop, OA_ROW.total - OA_ROW.pop]]);
});

Then('the stored other-acceptable row carries the edited total', async ({ request, world }) => {
  await expect
    .poll(
      async () => {
        const doc = await getOtherAcceptable(request, world.scheduleKey!);
        return (doc.rows ?? []).map((r) => [r.total ?? null, r.crown ?? null]);
      },
      { message: 'the in-place edit was never persisted' },
    )
    .toEqual([[OA_ROW_EDITED_TOTAL, OA_ROW_EDITED_TOTAL - OA_ROW.pop]]);
});

Then('the stored included-unacceptable rows are the added row', async ({ request, world }) => {
  await expect
    .poll(
      async () => {
        const doc = await getUnacceptable(request, world.scheduleKey!);
        return (doc.rows ?? []).map((r) => [r.description, r.total ?? null]);
      },
      { message: 'the added included-unacceptable row was never stored' },
    )
    .toEqual([[UNACCEPTABLE_ROW.description, UNACCEPTABLE_ROW.total]]);
});

Then('no other-acceptable rows are stored', async ({ request, world }) => {
  await expect
    .poll(async () => (await getOtherAcceptable(request, world.scheduleKey!)).count, {
      message: 'an other-acceptable group is still stored',
    })
    .toBe(0);
});

Then('no included-unacceptable rows are stored', async ({ request, world }) => {
  await expect
    .poll(async () => (await getUnacceptable(request, world.scheduleKey!)).count, {
      message: 'an included-unacceptable row is still stored',
    })
    .toBe(0);
});

Then('no sub-page write was attempted', async ({ page, schedule3MutationSpy, world }) => {
  // Same barrier as the main page's zero-write assertion: the negative must hold over a window, not at
  // one instant.
  await settleBeforeReadingSpy(page);
  expect(
    schedule3MutationSpy.mutations,
    'a mutating sub-page request was sent even though the row was rejected client-side',
  ).toBe(world.sch3MutationsBefore ?? 0);
});
