import { settleBeforeReadingSpy } from '../../pages/common/settle';
import { Given, When, Then, expect } from '../fixtures';
import {
  A11Y_ANCHOR,
  BLANK_COST_ANCHOR,
  BLANK_SALES_ANCHOR,
  BOTTOM_BAR_ANCHOR,
  CANCEL_DELETE_ANCHOR,
  CHECK_MET_ANCHOR,
  CHECK_MISSING_ANCHOR,
  CLIENT,
  DELETE_ANCHOR,
  DELETE_UNAVAILABLE_ANCHOR,
  GUARD_ANCHORS,
  HAPPY_PATH_ANCHOR,
  PERSIST_ANCHOR,
  READ_ONLY_ANCHORS,
  RETRY_ANCHOR,
  SAVED_INCOMPLETE_ANCHOR,
  SAVE_ERROR_ANCHOR,
  type Sch2Anchor,
  UPDATE_ANCHOR,
  VALIDATION_ANCHOR,
  millOptionText,
} from '../../fixtures/sch2/schedule2-test-data';
import { getSchedule2, saveSchedule2, type Sch2CostBlock } from './schedule2Api';

/**
 * UC-SCH2-001 (Schedule 2) step definitions. Domain vocabulary only — every DOM detail lives in
 * `pages/sch2/schedule2Page.ts`, every pinned value in `fixtures/sch2/schedule2-test-data.ts`.
 *
 * REUSED rather than redefined (the skill's reuse-before-you-add rule):
 *   - `Given I have selected that mill and reporting year on the Home page` (steps/common/home-context)
 *   - `Then I should see the message/error/warning {string}` and `I should not see the message {string}`
 *     (steps/common/assertions) — Schedule 2 renders every result through the same shared Carbon
 *     InlineNotification shape those steps already target, so no sch2 copy exists.
 *   - `Then the {string} view has no WCAG 2.1 AA accessibility violations` (steps/common/assertions)
 *
 * Step texts are deliberately Schedule-2-qualified ("I save Schedule 2", "I check Schedule 2 status",
 * "I confirm the Schedule 2 delete") because the unqualified forms are already owned by other domains
 * (sch11 owns "I run Check Status" / "I confirm the delete"), and playwright-bdd rejects duplicates.
 */

// =====================================================================================================
// Anchor registry
// =====================================================================================================

const ANCHORS: Record<string, Sch2Anchor> = {
  'happy-path': HAPPY_PATH_ANCHOR,
  update: UPDATE_ANCHOR,
  'blank-cost': BLANK_COST_ANCHOR,
  'blank-sales': BLANK_SALES_ANCHOR,
  delete: DELETE_ANCHOR,
  'delete-unavailable': DELETE_UNAVAILABLE_ANCHOR,
  'check-met': CHECK_MET_ANCHOR,
  'check-missing': CHECK_MISSING_ANCHOR,
  'save-error': SAVE_ERROR_ANCHOR,
  retry: RETRY_ANCHOR,
  validation: VALIDATION_ANCHOR,
  persist: PERSIST_ANCHOR,
  'bottom-bar': BOTTOM_BAR_ANCHOR,
  'cancel-delete': CANCEL_DELETE_ANCHOR,
  'saved-incomplete': SAVED_INCOMPLETE_ANCHOR,
  a11y: A11Y_ANCHOR,
};

const namedAnchor = (name: string): Sch2Anchor => {
  const anchor = ANCHORS[name];
  expect(
    anchor,
    `unknown Schedule 2 anchor "${name}". Known: ${Object.keys(ANCHORS).join(', ')}`,
  ).toBeTruthy();
  return anchor;
};

/**
 * The anchor the scenario claimed, with a loud, actionable failure instead of an opaque TypeError from a
 * bare `world.scheduleKey!` when a precondition step was forgotten.
 */
const claimedKey = (world: { scheduleKey?: { millId: number; year: number } }) => {
  expect(
    world.scheduleKey,
    'no Schedule 2 anchor claimed — a scenario must start with a "the Schedule 2 anchor …" Given',
  ).toBeTruthy();
  return world.scheduleKey!;
};

/** Gherkin table `| field | value |` → a lookup, preserving "absent means do not touch". */
interface GherkinDataTable {
  hashes: () => Record<string, string>[];
  raw: () => string[][];
}

// =====================================================================================================
// Preconditions
// =====================================================================================================

Given(
  'the Schedule 2 anchor {string} is an unsaved editable Draft',
  async ({ request, world, schedule2Cleanup }, name) => {
    const anchor = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    // Register cleanup the moment we know a write is POSSIBLE, so a mid-scenario failure still tears
    // down. Restoring an anchor nothing wrote to is a no-op (DELETE is idempotent by contract), so this
    // is registered even for the validate-only anchors — cheap insurance against an unintended save.
    schedule2Cleanup.push({ key: anchor.key });

    const doc = await getSchedule2(request, anchor.key);
    expect(doc.trackStatus, 'precondition: the 1-10 track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: the schedule must be editable').toBe(true);
    // UNSAVED means no summary at all, which is what makes "the document opens empty", the derived-figure
    // expectations and delete-returns-to-empty true. Preflight checks the whole set once before any
    // browser opens; this is the per-scenario backstop for a save that escaped mid-run.
    expect(
      doc.revisionCount ?? null,
      `precondition: anchor ${anchor.key.millId}/${anchor.key.year} must hold NO saved Schedule 2 before this scenario writes its own (found revisionCount ${doc.revisionCount}). DELETE /api/v1/schedule2 for it and re-run.`,
    ).toBeNull();
    world.sch2RevisionAtOpen = null;
  },
);

Given(
  'the Schedule 2 anchor {string} has a saved schedule',
  async ({ request, world, schedule2Cleanup }, name) => {
    const anchor = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    schedule2Cleanup.push({ key: anchor.key });

    const doc = await getSchedule2(request, anchor.key);
    expect(doc.trackStatus, 'precondition: the 1-10 track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: the schedule must be editable').toBe(true);
    expect(
      doc.revisionCount ?? null,
      `precondition: anchor ${anchor.key.millId}/${anchor.key.year} must start UNSAVED so this scenario seeds a known state`,
    ).toBeNull();

    // Seed through the app's own endpoint — a known, complete saved state the scenario then edits or
    // deletes. Values are deliberately distinct from the happy path's so a cross-anchor mix-up is visible.
    const seeded = await saveSchedule2(request, anchor.key, {
      purchasedLogCostCost: 12345,
      lessLogSalesVolume: 20,
      lessLogSalesCost: 2000,
      comments: 'seeded by e2e',
    });
    world.sch2RevisionAtOpen = seeded.revisionCount ?? null;
  },
);

Given(
  'the Schedule 2 anchor {string} has a saved schedule with no purchased log cost',
  async ({ request, world, schedule2Cleanup }, name) => {
    const anchor = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    schedule2Cleanup.push({ key: anchor.key });

    const doc = await getSchedule2(request, anchor.key);
    expect(doc.editable, 'precondition: the schedule must be editable').toBe(true);
    expect(doc.revisionCount ?? null, 'precondition: anchor must start UNSAVED').toBeNull();

    // BR-07's failing side: the schedule EXISTS (so it is saved and deletable) but item 25 carries no
    // cost, which is exactly the state S03 leaves behind and S08 then reports on.
    const seeded = await saveSchedule2(request, anchor.key, {
      purchasedLogCostCost: null,
      lessLogSalesVolume: 20,
      lessLogSalesCost: 2000,
      comments: null,
    });
    world.sch2RevisionAtOpen = seeded.revisionCount ?? null;
  },
);

Given(
  'the Schedule 2 read-only anchor {string} is selected',
  async ({ request, world }, name) => {
    const anchor = READ_ONLY_ANCHORS[name as keyof typeof READ_ONLY_ANCHORS];
    expect(
      anchor,
      `unknown Schedule 2 read-only anchor "${name}". Known: ${Object.keys(READ_ONLY_ANCHORS).join(', ')}`,
    ).toBeTruthy();
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);

    // NO cleanup registration: this anchor is never written to, and its stored values are the very thing
    // the read-only scenarios assert — a stray DELETE here would destroy another run's ground truth.
    const doc = await getSchedule2(request, anchor.key);
    expect(
      doc.trackStatus,
      `precondition: read-only anchor "${name}" must be track "${anchor.trackStatus}"`,
    ).toBe(anchor.trackStatus);
    expect(doc.editable, 'precondition: the schedule must NOT be editable').toBe(false);
    world.sch2RevisionAtOpen = doc.revisionCount ?? null;
  },
);

Given('the Schedule 2 guard anchor {string}', async ({ request, world }, name) => {
  const guard = GUARD_ANCHORS[name];
  expect(
    guard,
    `unknown Schedule 2 guard anchor "${name}". Known: ${Object.keys(GUARD_ANCHORS).join(', ')}`,
  ).toBeTruthy();
  world.scheduleKey = guard.key;
  world.millOption = millOptionText(guard.mill);

  // Confirm the guard still fires the way it is pinned, so a scenario that then finds a rendered form
  // fails against the ANCHOR rather than looking like a page bug.
  const res = await request.get(
    `/api/v1/schedule2?millId=${guard.key.millId}&year=${guard.key.year}`,
  );
  expect(
    res.status(),
    `guard anchor "${name}" must still return HTTP ${guard.expectHttp}`,
  ).toBe(guard.expectHttp);
});

Given('a spy is watching the Schedule 2 save requests', async ({ schedule2MutationSpy }) => {
  // Referencing the fixture installs the route. Asserted later by the "should not have been sent" steps.
  expect(schedule2MutationSpy.mutations).toBe(0);
});

Given('the next Schedule 2 save will fail on the server', async ({ schedule2FailNextSave }) => {
  await schedule2FailNextSave.install();
});

// =====================================================================================================
// Actions
// =====================================================================================================

When('I open Schedule 2', async ({ schedule2Page }) => {
  await schedule2Page.openViaNav();
});

When('I open Schedule 2 expecting a guard message', async ({ schedule2Page }) => {
  await schedule2Page.openViaNavExpectingGuard();
});

When('I open Schedule 2 with no working context', async ({ schedule2Page }) => {
  await schedule2Page.openWithNoContext();
});

When('I reopen Schedule 2', async ({ schedule2Page }) => {
  await schedule2Page.reload();
});

When('I enter {string} in the Schedule 2 {string} field', async ({ schedule2Page }, value, field) => {
  await schedule2Page.setField(field, value);
});

When('I clear the Schedule 2 {string} field', async ({ schedule2Page }, field) => {
  await schedule2Page.setField(field, '');
});

When(
  'I enter the following Schedule 2 values:',
  async ({ schedule2Page }, dataTable: GherkinDataTable) => {
    for (const row of dataTable.hashes()) {
      const field = (row.field ?? '').trim();
      // An empty `value` cell means "clear this field" — the blank-field slices depend on it.
      await schedule2Page.setField(field, (row.value ?? '').trim());
    }
  },
);

When('I save Schedule 2', async ({ schedule2Page }) => {
  await schedule2Page.clickSave();
});

When('I save Schedule 2 from the bottom action bar', async ({ schedule2Page }) => {
  // Legacy rendered Save twice; the rewrite keeps both bars, so both must actually work.
  await schedule2Page.bottomAction('Save').click();
});

When('I check Schedule 2 status', async ({ schedule2Page }) => {
  await schedule2Page.clickCheckStatus();
});

When('I delete Schedule 2', async ({ schedule2Page }) => {
  await schedule2Page.clickDelete();
});

When('I confirm the Schedule 2 delete', async ({ schedule2Page }) => {
  await schedule2Page.confirmDelete();
});

When('I cancel the Schedule 2 delete', async ({ schedule2Page }) => {
  await schedule2Page.cancelDelete();
});

When('I note the Schedule 2 mutation count', async ({ world, schedule2MutationSpy }) => {
  world.sch2MutationsBefore = schedule2MutationSpy.mutations;
});

// =====================================================================================================
// Assertions — rendered document
// =====================================================================================================

Then(
  'the Schedule 2 {string} field shows {string}',
  async ({ schedule2Page }, field, expected) => {
    await expect
      .poll(() => schedule2Page.fieldValue(field), {
        message: `Schedule 2 "${field}" should display "${expected}"`,
      })
      .toBe(expected);
  },
);

Then(
  'the Schedule 2 {string} field is invalid with {string}',
  async ({ schedule2Page }, field, message) => {
    await expect(
      schedule2Page.fieldError(field),
      `Schedule 2 "${field}" should carry the inline error "${message}"`,
    ).toHaveText(message);
  },
);

Then('the Schedule 2 {string} field has no inline error', async ({ schedule2Page }, field) => {
  await expect(schedule2Page.fieldError(field)).toHaveCount(0);
});

/**
 * The whole rendered cost table, row by row: `| row | volume | cost | perUnit |`.
 *
 * This is the BR-06 assertion — it proves the SERVER recomputed every derived block from the entered
 * values, not merely that a success banner appeared. A cell holding an editable input reports that
 * input's displayed value, so the same step serves the editable and read-only renders.
 */
Then(
  'the Schedule 2 document shows:',
  async ({ schedule2Page }, dataTable: GherkinDataTable) => {
    for (const row of dataTable.hashes()) {
      const label = (row.row ?? '').trim();
      const expected: [string, string, string] = [
        (row.volume ?? '').trim(),
        (row.cost ?? '').trim(),
        (row.perUnit ?? '').trim(),
      ];
      await expect
        .poll(() => schedule2Page.rowValues(label), {
          message: `Schedule 2 row "${label}" should read [volume, cost, $/m³] = ${JSON.stringify(expected)}`,
        })
        .toEqual(expected);
    }
  },
);

/**
 * BR-03/BR-04's render half: the ONLY enterable cells are item 25's cost and item 26's volume + cost.
 * Everything else in the table — the carried Purchased/Private volume, every derived block, every $/m³ —
 * is read-only. Asserted as an exact ordered list so a new input appearing anywhere fails too.
 */
Then('only the purchased-log cost and both log-sales fields are editable', async ({ schedule2Page }) => {
  expect(await schedule2Page.editableFieldIds()).toEqual([
    'purchasedLogCostCost',
    'lessLogSalesVolume',
    'lessLogSalesCost',
  ]);
});

Then('the Schedule 2 rows are in legacy order', async ({ schedule2Page }) => {
  expect(await schedule2Page.rowLabels()).toEqual([
    'Purchased/Private Log Costs:',
    'Purchased/Private Wood Overhead:',
    'Subtotal:',
    '(less) Log Sales:',
    'Net Purchased/Private Log Cost:',
    'Total Company Logging Costs(Sch 1):',
    'Total Average Logging Costs:',
  ]);
});

Then('Save and Check Status are each rendered twice', async ({ schedule2Page }) => {
  // Legacy parity: both action bars (top and bottom) carry Save and Check Status.
  expect(await schedule2Page.actionCount('Save'), 'Save should appear in both action bars').toBe(2);
  expect(
    await schedule2Page.actionCount('Check Status'),
    'Check Status should appear in both action bars',
  ).toBe(2);
});

Then('the Schedule 2 comments show {string}', async ({ schedule2Page }, expected) => {
  await expect(schedule2Page.commentsReadOnly).toHaveText(expected);
});

// =====================================================================================================
// Assertions — control gating / render state
// =====================================================================================================

Then('the Schedule 2 actions are disabled', async ({ schedule2Page }) => {
  await expect(schedule2Page.saveButton).toBeDisabled();
  await expect(schedule2Page.checkStatusButton).toBeDisabled();
});

Then('the Schedule 2 Delete action is unavailable', async ({ schedule2Page }) => {
  // RE-GROUNDED from legacy: legacy omitted Delete from the DOM entirely (BR-08's `rendered`
  // condition); the React page renders it DISABLED instead. The user-visible outcome is what the slice
  // is about — delete cannot be initiated — so that is what is asserted, with the mechanism difference
  // recorded in coverage.md rather than silently swapped.
  await expect(schedule2Page.deleteButton).toBeDisabled();
});

Then('the Schedule 2 Delete action is available', async ({ schedule2Page }) => {
  await expect(schedule2Page.deleteButton).toBeEnabled();
  // The converse of the reason below: when Delete IS available there is nothing to explain, so the
  // element must be absent from the DOM entirely (not merely hidden — it is always hidden now).
  await expect(schedule2Page.deleteUnavailableHint).toHaveCount(0);
});

Then('the Schedule 2 delete-unavailable reason is announced', async ({ schedule2Page }) => {
  // nr-ilcr #292 decision 3: greying Delete rather than omitting it (legacy's `rendered`) costs a
  // screen-reader user the control entirely, because a disabled Carbon button is not focusable. The
  // app pays that cost with a programmatically-associated reason that is VISUALLY HIDDEN — it shipped
  // visible for one commit and read as clutter beside an already-greyed control (product call
  // 2026-08-24). So: attached and wired, deliberately NOT `toBeVisible()`. Both halves are asserted,
  // because losing either one undoes the decision in a different direction.
  await expect(schedule2Page.deleteUnavailableHint).toBeAttached();
  await expect(schedule2Page.deleteUnavailableHint).toHaveClass(/cds--visually-hidden/);
  const hintId = await schedule2Page.deleteUnavailableHint.getAttribute('id');
  await expect(schedule2Page.deleteButton).toHaveAttribute('aria-describedby', hintId ?? '');
});

Then('the Schedule 2 fields are read-only', async ({ schedule2Page }) => {
  // In read-only mode the editable controls are not rendered at all — the values become plain cells and
  // the comments textarea is replaced by a paragraph.
  expect(
    await schedule2Page.isEditable(),
    'the comments textarea must be absent when the schedule is not editable',
  ).toBe(false);
  await expect(schedule2Page.item25Cost).toHaveCount(0);
  await expect(schedule2Page.item26Volume).toHaveCount(0);
  await expect(schedule2Page.item26Cost).toHaveCount(0);
});

Then('the Schedule 2 input form is not displayed', async ({ schedule2Page }) => {
  await expect(schedule2Page.table).toHaveCount(0);
});

Then('the Schedule 2 delete confirmation is dismissed', async ({ schedule2Page }) => {
  await expect(schedule2Page.confirmModal).toHaveCount(0);
});

Then('the Schedule 2 delete confirmation asks {string}', async ({ schedule2Page }, text) => {
  await expect(schedule2Page.confirmModal).toContainText(text);
  await expect(schedule2Page.confirmModalHeading).toBeVisible();
});

// =====================================================================================================
// Assertions — persisted state (API read-back) and proof-of-no-write
// =====================================================================================================

/** Normalise a block to the `volume/cost/perUnit` triple, treating Jackson-omitted as null. */
const triple = (block: Sch2CostBlock) => ({
  volume: block.volume ?? null,
  cost: block.cost ?? null,
  perUnit: block.perUnit ?? null,
});

/**
 * Read back the STORED record and assert the entered line items — the skill's verify-by-API-read-back
 * rule. Polled, because the write is UI-triggered: a GET fired immediately after the click can race the
 * commit.
 */
Then(
  'the stored Schedule 2 record is:',
  async ({ request, world }, dataTable: GherkinDataTable) => {
    const key = claimedKey(world);
    const expected: Record<string, string> = {};
    for (const row of dataTable.hashes()) {
      expected[(row.field ?? '').trim()] = (row.value ?? '').trim();
    }
    await expect
      .poll(
        async () => {
          const doc = await getSchedule2(request, key);
          const actual: Record<string, string> = {};
          for (const field of Object.keys(expected)) {
            switch (field) {
              case 'purchasedLogCostCost':
                actual[field] = String(doc.purchasedLogCost.cost ?? '');
                break;
              case 'lessLogSalesVolume':
                actual[field] = String(doc.lessLogSales.volume ?? '');
                break;
              case 'lessLogSalesCost':
                actual[field] = String(doc.lessLogSales.cost ?? '');
                break;
              case 'comments':
                actual[field] = doc.comments ?? '';
                break;
              default:
                throw new Error(
                  `unknown stored Schedule 2 field "${field}". Use purchasedLogCostCost, lessLogSalesVolume, lessLogSalesCost or comments.`,
                );
            }
          }
          return actual;
        },
        {
          message: `the stored Schedule 2 record on ${key.millId}/${key.year} should match the table`,
        },
      )
      .toEqual(expected);
  },
);

Then('the stored Schedule 2 derived figures are:', async ({ request, world }, dataTable: GherkinDataTable) => {
  const key = claimedKey(world);
  const rows = dataTable.hashes();
  await expect
    .poll(
      async () => {
        const doc = await getSchedule2(request, key);
        const blocks: Record<string, Sch2CostBlock> = {
          subtotal: doc.subtotal,
          netPurchased: doc.netPurchased,
          totalAverage: doc.totalAverage,
          purchasedLogCost: doc.purchasedLogCost,
          lessLogSales: doc.lessLogSales,
        };
        return rows.map((row) => {
          const name = (row.block ?? '').trim();
          const block = blocks[name];
          if (!block) {
            throw new Error(
              `unknown Schedule 2 block "${name}". Use ${Object.keys(blocks).join(', ')}.`,
            );
          }
          const t = triple(block);
          return `${name}|${t.volume}|${t.cost}|${t.perUnit}`;
        });
      },
      { message: `the stored derived blocks on ${key.millId}/${key.year} should match the table` },
    )
    .toEqual(
      rows.map(
        (row) =>
          `${(row.block ?? '').trim()}|${(row.volume ?? '').trim()}|${(row.cost ?? '').trim()}|${(
            row.perUnit ?? ''
          ).trim()}`,
      ),
    );
});

Then('no Schedule 2 record is stored', async ({ request, world }) => {
  const key = claimedKey(world);
  // PROVE the negative at the source of truth, not by inference from the page: a rejected save must
  // leave the schedule with no summary at all.
  const doc = await getSchedule2(request, key);
  expect(
    doc.revisionCount ?? null,
    `no Schedule 2 summary should exist on ${key.millId}/${key.year} after a rejected save`,
  ).toBeNull();
});

Then(
  'the Schedule 2 save request should not have been sent',
  async ({ page, schedule2MutationSpy }) => {
    // Cross the shared settle barrier BEFORE reading the tally: the negative has to hold over a window,
    // not at one instant. Without it, a regression that renders the inline error and THEN fires the PUT a
    // tick later would read 0 and pass green. See pages/common/settle.ts for why this is an event-driven
    // barrier rather than a tuned `waitForTimeout`.
    await settleBeforeReadingSpy(page);
    // Client-side rejection must block the round-trip entirely — the strongest available proof that
    // nothing was written, and stronger than observing that the page did not move.
    expect(
      schedule2MutationSpy.mutations,
      'a client-rejected Save must not fire any mutating Schedule 2 request',
    ).toBe(0);
  },
);

Then(
  'no further Schedule 2 mutation should have been sent',
  async ({ page, world, schedule2MutationSpy }) => {
    // Same barrier as the absolute form above — the negative must hold over a window.
    await settleBeforeReadingSpy(page);
    expect(
      world.sch2MutationsBefore,
      'a scenario must call "I note the Schedule 2 mutation count" before asserting no FURTHER mutation',
    ).not.toBeUndefined();
    expect(
      schedule2MutationSpy.mutations,
      'no additional mutating Schedule 2 request should have been sent after the noted point',
    ).toBe(world.sch2MutationsBefore);
  },
);

Then('the stored Schedule 2 revision is unchanged', async ({ request, world }) => {
  const key = claimedKey(world);
  const doc = await getSchedule2(request, key);
  expect(
    doc.revisionCount ?? null,
    `the optimistic-lock token on ${key.millId}/${key.year} moved, so a write DID happen`,
  ).toBe(world.sch2RevisionAtOpen ?? null);
});

Then('the Schedule 2 schedule is stored', async ({ request, world }) => {
  const key = claimedKey(world);
  await expect
    .poll(async () => (await getSchedule2(request, key)).revisionCount ?? null, {
      message: `a Schedule 2 summary should exist on ${key.millId}/${key.year} after a successful save`,
    })
    .not.toBeNull();
});

Then('the mill and reporting year guard message is shown', async ({ schedule2Page }) => {
  await expect(schedule2Page.notification(CLIENT.millYearNotSelected)).toBeVisible();
  await expect(schedule2Page.notification(CLIENT.millYearNotSelectedTitle)).toBeVisible();
});
