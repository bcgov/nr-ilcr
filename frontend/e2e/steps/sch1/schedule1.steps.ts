import { Given, When, Then, expect } from '../fixtures';
import {
  CHECK_STATUS_ANCHORS,
  CHECK_STATUS_SEED_ANCHORS,
  DELETE_ANCHOR,
  DELETE_ANCHOR_MILL,
  MUTABLE_DRAFT,
  MUTABLE_DRAFT_MILL,
  READONLY_ANCHOR,
  READONLY_ANCHOR_MILL,
  RENDER_STATE_ANCHORS,
  RETRY_ANCHOR,
  RETRY_ANCHOR_MILL,
  millOptionText,
  scheduleUrl,
} from '../../fixtures/sch1/schedule1-test-data';
import { snapshotSchedule1 } from './schedule1DbRestore';
import { addOtherCost } from './otherCostsApi';

/**
 * Schedule 1 (UC-SCH1-001) steps. Domain vocabulary only — no DOM (that lives in Schedule1Page /
 * HomePage). Success is asserted with the common "I should see the message" step; persistence is
 * verified by API read-back (GET the record and assert the persisted values, not merely the toast).
 */

Given(
  'the Schedule 1 for the test mill and reporting year is an editable Draft',
  async ({ request, world, schedule1Restore }) => {
    world.scheduleKey = MUTABLE_DRAFT;
    world.millOption = millOptionText(MUTABLE_DRAFT_MILL);
    // Register for restore-to-empty teardown the moment we know we will mutate it.
    schedule1Restore.push(MUTABLE_DRAFT);

    const res = await request.get(scheduleUrl(MUTABLE_DRAFT.millId, MUTABLE_DRAFT.year));
    expect(
      res.ok(),
      `precondition: GET Schedule 1 for ${MUTABLE_DRAFT.millId}/${MUTABLE_DRAFT.year} returned HTTP ${res.status()}`,
    ).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean };
    expect(doc.trackStatus, 'precondition: track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: schedule must be editable').toBe(true);
  },
);

Given(
  'the read-only Schedule 1 anchor is an editable Draft',
  async ({ request, world }) => {
    // Validate-only precondition: a populated, editable Draft that NO scenario writes to. Deliberately
    // NOT the happy-path (mutable) pair and NOT registered for restore — these scenarios prove Save is
    // blocked, so no write ever lands and there is nothing to clean up (keeps them parallel-safe).
    world.scheduleKey = READONLY_ANCHOR;
    world.millOption = millOptionText(READONLY_ANCHOR_MILL);

    const res = await request.get(scheduleUrl(READONLY_ANCHOR.millId, READONLY_ANCHOR.year));
    expect(
      res.ok(),
      `precondition: GET Schedule 1 for ${READONLY_ANCHOR.millId}/${READONLY_ANCHOR.year} returned HTTP ${res.status()}`,
    ).toBeTruthy();
    const doc = (await res.json()) as {
      trackStatus: string;
      editable: boolean;
      revisionCount: number | null;
    };
    expect(doc.trackStatus, 'precondition: track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: schedule must be editable').toBe(true);
    world.revisionAtOpen = doc.revisionCount ?? null;
  },
);

Given(
  'the Schedule 1 anchor {string} is an editable Draft',
  async ({ request, world }, name) => {
    // A named, read-only Check Status anchor (S14–S16). Check Status is a POST that mutates nothing,
    // so this sets context without registering restore — the anchor is left exactly as found. The
    // revisionCount captured here is re-read afterwards to prove Check Status changed no data.
    const anchor = CHECK_STATUS_ANCHORS[name];
    expect(anchor, `unknown Check Status anchor "${name}"`).toBeTruthy();
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);

    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    expect(
      res.ok(),
      `precondition: GET Schedule 1 for ${anchor.key.millId}/${anchor.key.year} returned HTTP ${res.status()}`,
    ).toBeTruthy();
    const doc = (await res.json()) as {
      trackStatus: string;
      editable: boolean;
      revisionCount: number | null;
    };
    expect(doc.trackStatus, 'precondition: anchor track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: anchor must be editable (Check Status enabled)').toBe(true);
    world.revisionAtOpen = doc.revisionCount ?? null;
  },
);

Given(
  'the Schedule 1 anchor {string} has a seeded Other Cost row',
  async ({ request, world, otherCostsCleanup }, name) => {
    // S17/S18: seed one itemized Other-Costs row through the real API, register its cleanup, then
    // Check Status reads it (read-only from here on).
    const anchor = CHECK_STATUS_SEED_ANCHORS[name as keyof typeof CHECK_STATUS_SEED_ANCHORS];
    expect(anchor, `unknown Check Status seed anchor "${name}"`).toBeTruthy();
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);

    const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
    expect(res.ok(), `precondition GET ${anchor.key.millId}/${anchor.key.year} -> ${res.status()}`).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean; revisionCount: number | null };
    expect(doc.trackStatus, 'anchor must be Draft').toBe('D');
    expect(doc.editable, 'anchor must be editable').toBe(true);
    world.revisionAtOpen = doc.revisionCount ?? null;

    otherCostsCleanup.push({ millId: anchor.key.millId, year: anchor.key.year, marker: anchor.marker });
    await addOtherCost(request, anchor.key.millId, anchor.key.year, anchor.marker, anchor.cost);
  },
);

Given('a spy is watching the Schedule 1 save request', async ({ schedule1SaveSpy }) => {
  // Touch the fixture so its page.route is installed BEFORE any Save is clicked; assert a clean start.
  expect(schedule1SaveSpy.puts, 'spy must start at zero PUTs').toBe(0);
});

Given('I have selected that mill and reporting year on the Home page', async ({ homePage, world }) => {
  await homePage.open();
  await homePage.selectContextAndSave(world.millOption!, world.scheduleKey!.year);
});

When('I open Schedule 1', async ({ schedule1Page }) => {
  await schedule1Page.gotoViaNav();
});

Given('the Schedule 1 render-state anchor {string} is selected', async ({ request, world }, name) => {
  // A named context-guard anchor (S20 closed / S21 not-found / S22 submitted). Read-only: the Home
  // Save that follows is a resolve GET (no report write), so no restore is registered. Assert the
  // schedule1 GET still returns the guard's HTTP status so a data drift fails a re-ground, not the UI.
  const anchor = RENDER_STATE_ANCHORS[name];
  expect(anchor, `unknown render-state anchor "${name}"`).toBeTruthy();
  world.scheduleKey = anchor.key;
  world.millOption = millOptionText(anchor.mill);

  const res = await request.get(scheduleUrl(anchor.key.millId, anchor.key.year));
  expect(
    res.status(),
    `precondition: Schedule 1 GET ${anchor.key.millId}/${anchor.key.year} must return HTTP ${anchor.expectHttp} for the "${name}" render state`,
  ).toBe(anchor.expectHttp);
});

When('I navigate to Schedule 1 expecting a guard', async ({ schedule1Page }) => {
  await schedule1Page.gotoViaNavExpectingGuard();
});

When('I open Schedule 1 with no working context', async ({ schedule1Page }) => {
  await schedule1Page.openWithNoContext();
});

Then('the Schedule 1 input form is not displayed', async ({ schedule1Page }) => {
  await expect(schedule1Page.companyLoggingTable).toHaveCount(0);
  await expect(schedule1Page.saveButton).toHaveCount(0);
});

Then('the Schedule 1 actions are disabled', async ({ schedule1Page }) => {
  await expect(schedule1Page.saveButton).toBeDisabled();
  await expect(schedule1Page.checkStatusButton).toBeDisabled();
  await expect(schedule1Page.deleteButton).toBeDisabled();
});

Then('the Schedule 1 amount and comment fields are read-only', async ({ schedule1Page }) => {
  // Editable schedules render Carbon inputs; a non-Draft schedule renders the values as plain text,
  // so neither the amount input nor the comments editor exists.
  await expect(schedule1Page.firstAmountInput).toHaveCount(0);
  await expect(schedule1Page.commentsInput).toHaveCount(0);
});

When('I enter the following Schedule 1 amounts:', async ({ schedule1Page }, dataTable) => {
  for (const row of dataTable.hashes()) {
    await schedule1Page.enterAmount(row['line item'], row['volume'], row['cost']);
  }
});

When('I enter Schedule 1 comments {string}', async ({ schedule1Page }, text) => {
  await schedule1Page.enterComments(text);
});

When('I enter {string} in the Schedule 1 {string} field', async ({ schedule1Page }, value, label) => {
  await schedule1Page.enterField(label, value);
});

When('I save Schedule 1', async ({ schedule1Page }) => {
  await schedule1Page.save();
});

When('I check Schedule 1 status', async ({ schedule1Page }) => {
  await schedule1Page.checkStatus();
});

Given(
  'the next {int} Schedule 1 save attempt(s) will fail',
  async ({ schedule1SaveFault }, times) => {
    // Arm the fault route (referencing the fixture installs it before any Save is clicked).
    schedule1SaveFault.failTimes = times;
  },
);

Given(
  'a saved editable Schedule 1 exists for the retry target',
  async ({ request, world, schedule1DeleteRestore }) => {
    // S24 retry persists on success, so snapshot the dedicated target first and restore it exactly on
    // teardown (delete-then-reinsert). Registered in the same snapshot/restore registry as delete.
    world.scheduleKey = RETRY_ANCHOR;
    world.millOption = millOptionText(RETRY_ANCHOR_MILL);

    const res = await request.get(scheduleUrl(RETRY_ANCHOR.millId, RETRY_ANCHOR.year));
    expect(
      res.ok(),
      `precondition: GET Schedule 1 for ${RETRY_ANCHOR.millId}/${RETRY_ANCHOR.year} returned HTTP ${res.status()}`,
    ).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean };
    expect(doc.trackStatus, 'precondition: retry target must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: retry target must be editable').toBe(true);

    snapshotSchedule1(RETRY_ANCHOR.millId, RETRY_ANCHOR.year);
    schedule1DeleteRestore.push(RETRY_ANCHOR);
  },
);

Then(
  'the Schedule 1 {string} field still shows {string}',
  async ({ schedule1Page }, label, value) => {
    // A failed save must keep the entered values in the form (S23) — nothing is cleared or reloaded.
    await expect(schedule1Page.fieldByLabel(label)).toHaveValue(value);
  },
);

Given(
  'a saved editable Schedule 1 exists for the delete target',
  async ({ request, world, schedule1DeleteRestore }) => {
    // The dedicated delete target — populated editable Draft. Snapshot it to the backup tables NOW
    // (before the UI delete) and register restore, so the destructive delete is reversed on teardown.
    world.scheduleKey = DELETE_ANCHOR;
    world.millOption = millOptionText(DELETE_ANCHOR_MILL);

    const res = await request.get(scheduleUrl(DELETE_ANCHOR.millId, DELETE_ANCHOR.year));
    expect(
      res.ok(),
      `precondition: GET Schedule 1 for ${DELETE_ANCHOR.millId}/${DELETE_ANCHOR.year} returned HTTP ${res.status()}`,
    ).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean };
    expect(doc.trackStatus, 'precondition: delete target must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: delete target must be editable').toBe(true);

    snapshotSchedule1(DELETE_ANCHOR.millId, DELETE_ANCHOR.year);
    schedule1DeleteRestore.push(DELETE_ANCHOR);
  },
);

When('I delete Schedule 1 and confirm the prompt', async ({ schedule1Page }) => {
  await schedule1Page.openDeleteConfirm();
  await schedule1Page.confirmDelete();
});

Then('the Schedule 1 should no longer exist', async ({ request, world }) => {
  // Prove the delete persisted through the real write path: the summary is gone (GET 404). Poll —
  // the DELETE + in-place redisplay is UI-triggered, so the commit can trail the click.
  const { millId, year } = world.scheduleKey!;
  await expect
    .poll(async () => (await request.get(scheduleUrl(millId, year))).status())
    .toBe(404);
});

Then('the Schedule 1 data should be unchanged', async ({ request, world }) => {
  // Prove Check Status is read-only: the persisted schedule's optimistic-lock token is identical
  // before and after — a POST that wrote anything would have bumped revisionCount.
  const { millId, year } = world.scheduleKey!;
  const res = await request.get(scheduleUrl(millId, year));
  expect(res.ok(), `read-back GET ${millId}/${year} returned HTTP ${res.status()}`).toBeTruthy();
  const doc = (await res.json()) as { revisionCount: number | null };
  expect(
    doc.revisionCount ?? null,
    'Check Status must not change Schedule 1 (revisionCount must be stable)',
  ).toBe(world.revisionAtOpen ?? null);
});

Then('the Schedule 1 save request should not have been sent', async ({ schedule1SaveSpy }) => {
  // Prove the negative: a rejected entry must fire ZERO mutating PUTs. Assert AFTER the save-blocked
  // banner has been awaited (handleSave aborts synchronously, so the count is settled by then).
  expect(schedule1SaveSpy.puts, 'a blocked Save must not send a PUT to /api/v1/schedule1').toBe(0);
});

Then(
  'the saved Schedule 1 should have line item {int} with volume {int} and cost {int}',
  async ({ request, world }, code, volume, cost) => {
    const { millId, year } = world.scheduleKey!;
    // Poll: a UI-triggered write can race the commit, so re-read until the persisted row matches.
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return null;
        const doc = (await res.json()) as {
          lineItems: { costItemCode: number; volume: number; cost: number }[];
        };
        const li = doc.lineItems.find((x) => x.costItemCode === code);
        return li ? { volume: Number(li.volume), cost: Number(li.cost) } : null;
      })
      .toEqual({ volume, cost });
  },
);

Then(
  'the saved Schedule 1 should have Actual Spent silviculture with volume {int} and cost {int}',
  async ({ request, world }, volume, cost) => {
    const { millId, year } = world.scheduleKey!;
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return null;
        const doc = (await res.json()) as {
          silviculture: { actualSpent: { volume: number; cost: number } | null };
        };
        const a = doc.silviculture.actualSpent;
        return a ? { volume: Number(a.volume), cost: Number(a.cost) } : null;
      })
      .toEqual({ volume, cost });
  },
);

Then('the saved Schedule 1 comments should be {string}', async ({ request, world }, expected) => {
  const { millId, year } = world.scheduleKey!;
  await expect
    .poll(async () => {
      const res = await request.get(scheduleUrl(millId, year));
      if (!res.ok()) return null;
      const doc = (await res.json()) as { comments: string | null };
      return doc.comments;
    })
    .toBe(expected);
});
