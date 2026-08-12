import { Given, When, Then, expect } from '../fixtures';
import {
  CHECK_STATUS_ANCHORS,
  CHECK_STATUS_SEED_ANCHORS,
  CLEAR_AMOUNTS_ANCHOR,
  CLEAR_AMOUNTS_ANCHOR_MILL,
  CLEAR_GUARDED_ANCHOR,
  CLEAR_GUARDED_ANCHOR_MILL,
  CROWN_PREFILL_ANCHOR,
  CROWN_PREFILL_ANCHOR_MILL,
  CROWN_PREFILL_VOLUME,
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
import {
  makeSchedule1FirstEntry,
  countSchedule1Volumes,
  snapshotSchedule1,
} from './schedule1DbRestore';
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

Given(
  'the crown pre-fill target is an editable Draft with no volumes entered',
  async ({ request, world, schedule1DeleteRestore }) => {
    // S02 needs a genuine FIRST ENTRY — every stored detail volume null (Schedule1Service.allVolumesEmpty)
    // — which no seeded schedule is in and the app still cannot produce through the API: a blanking PUT
    // does now clear the fields it carries (BUG-2 fixed 2026-08-11), but allVolumesEmpty tests EVERY
    // stored detail volume and the PUT contract does not reach them all. So: snapshot the dedicated
    // target, NULL its volumes at the DB, and register the exact delete-then-reinsert restore. Nothing
    // here writes through the app.
    world.scheduleKey = CROWN_PREFILL_ANCHOR;
    world.millOption = millOptionText(CROWN_PREFILL_ANCHOR_MILL);

    const { millId, year } = CROWN_PREFILL_ANCHOR;
    const res = await request.get(scheduleUrl(millId, year));
    expect(res.ok(), `precondition: GET Schedule 1 for ${millId}/${year} -> HTTP ${res.status()}`).toBeTruthy();
    const doc = (await res.json()) as {
      trackStatus: string;
      editable: boolean;
      schedule3CrownVolume: number | null;
    };
    expect(doc.trackStatus, 'precondition: pre-fill target must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: pre-fill target must be editable').toBe(true);
    // Pin the Schedule 3 source: without a crown volume BR-03 never fires and the scenario would pass
    // vacuously. A re-extract that drops it fails HERE, as a re-ground, not as a confusing UI timeout.
    expect(
      Number(doc.schedule3CrownVolume),
      'precondition: the target’s Schedule 3 must carry the pinned Crown Timber (item 119) volume',
    ).toBe(CROWN_PREFILL_VOLUME);

    snapshotSchedule1(millId, year);
    schedule1DeleteRestore.push(CROWN_PREFILL_ANCHOR);
    makeSchedule1FirstEntry(millId, year);
  },
);

Then(
  'every Schedule 1 volume field is pre-filled with the Schedule 3 Crown Timber volume',
  async ({ schedule1Page }) => {
    // BR-03 copies the crown volume into the full legacy 13-field volume set: line items 12–18 + the
    // volume-only rows 143/144, and silviculture 1, 2, 139, 140. The shared Other-Costs volume is
    // deliberately NOT pre-filled — asserting that too keeps the "13-field set" claim honest.
    // Values are compared UNGROUPED: the inputs render "325,411" since the restyle (#237) seeds them
    // via `numStrGroup()`. That is deliberate legacy parity, so assert the number, not its punctuation.
    const expected = String(CROWN_PREFILL_VOLUME);
    for (const code of [12, 13, 14, 15, 16, 17, 18, 143, 144, 1, 2, 139, 140]) {
      await expect
        .poll(
          () => schedule1Page.amountValue(`#vol-${code}`),
          { message: `volume field #vol-${code} should carry the pre-filled crown volume` },
        )
        .toBe(expected);
    }
    // The shared Other-Costs volume is deliberately OUTSIDE the pre-filled 13-field set. `first-entry`
    // removed this schedule's item-19 rows, so it renders empty — assert that directly rather than
    // merely "not the crown value", which would also pass on any other stray number.
    expect(
      await schedule1Page.amountValue('#otherCostsVolume'),
      'the shared Other-Costs volume is outside the pre-filled set',
    ).toBe('');
  },
);

Then('the pre-filled Schedule 1 volumes are not yet persisted', async ({ world }) => {
  // WRN-001 says "Please check and save schedule" — the pre-fill is SERVED ONLY. The GET renders the
  // pre-filled values, so an API read-back cannot tell served from stored; only the column can.
  const { millId, year } = world.scheduleKey!;
  expect(
    countSchedule1Volumes(millId, year),
    'the crown pre-fill must not write to the database until the user saves',
  ).toBe(0);
});

Given(
  'the clear-amounts target is an editable Draft',
  async ({ request, world, schedule1DeleteRestore }) => {
    // clear-amounts writes values and then clears them. Snapshot and re-insert the exact rows rather
    // than relying on the blank-fields PUT restore: this target is POPULATED, so it must come back with
    // its original values, not merely blanked. (Before BUG-2 was fixed the PUT could not even blank the
    // five volume-only fields; now it can, but a verbatim restore is still what this anchor needs.)
    world.scheduleKey = CLEAR_AMOUNTS_ANCHOR;
    world.millOption = millOptionText(CLEAR_AMOUNTS_ANCHOR_MILL);

    const { millId, year } = CLEAR_AMOUNTS_ANCHOR;
    const res = await request.get(scheduleUrl(millId, year));
    expect(res.ok(), `precondition: GET Schedule 1 for ${millId}/${year} -> HTTP ${res.status()}`).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean };
    expect(doc.trackStatus, 'precondition: clear-amounts target must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: clear-amounts target must be editable').toBe(true);

    snapshotSchedule1(millId, year);
    schedule1DeleteRestore.push(CLEAR_AMOUNTS_ANCHOR);
  },
);

Given(
  'the guarded-fields clear target is an editable Draft',
  async ({ request, world, schedule1DeleteRestore }) => {
    // A SEPARATE key from the clear-amounts target: both scenarios snapshot/restore, and the suite is
    // fullyParallel, so sharing one schedule races (value bleed + failed restores).
    world.scheduleKey = CLEAR_GUARDED_ANCHOR;
    world.millOption = millOptionText(CLEAR_GUARDED_ANCHOR_MILL);

    const { millId, year } = CLEAR_GUARDED_ANCHOR;
    const res = await request.get(scheduleUrl(millId, year));
    expect(res.ok(), `precondition: GET Schedule 1 for ${millId}/${year} -> HTTP ${res.status()}`).toBeTruthy();
    const doc = (await res.json()) as { trackStatus: string; editable: boolean };
    expect(doc.trackStatus, 'precondition: guarded-fields target must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: guarded-fields target must be editable').toBe(true);

    snapshotSchedule1(millId, year);
    schedule1DeleteRestore.push(CLEAR_GUARDED_ANCHOR);
  },
);

When('I clear the Schedule 1 {string} field', async ({ schedule1Page }, label) => {
  await schedule1Page.enterField(label, '');
});

When('I enter the following Schedule 1 field values:', async ({ schedule1Page }, dataTable) => {
  for (const { field, value } of dataTable.hashes()) {
    await schedule1Page.enterField(field, value);
  }
});

When('I clear the following Schedule 1 fields:', async ({ schedule1Page }, dataTable) => {
  for (const { field } of dataTable.hashes()) {
    await schedule1Page.enterField(field, '');
  }
});

Then(
  'the saved Schedule 1 volumes for the following rows should be empty:',
  async ({ request, world }, dataTable) => {
    const rows = dataTable.hashes().map((r: { row: string }) => Number(r.row));
    const { millId, year } = world.scheduleKey!;
    // One poll over ALL rows: re-read the record until every listed row is blank, then report the map
    // so a failure names exactly which fields are still holding a value.
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return { error: `GET -> HTTP ${res.status()}` };
        const doc = await res.json();
        return Object.fromEntries(rows.map((row) => [row, volumeOfRow(doc, row)]));
      })
      .toEqual(Object.fromEntries(rows.map((row) => [row, null])));
  },
);

/** Where a row's stored volume surfaces in the GET: the two silviculture-only rows and item 19 are not
 * `lineItems` entries, so a read-back keyed purely on `lineItems` would silently miss them. */
function volumeOfRow(
  doc: {
    lineItems: { costItemCode: number; volume: number | null }[];
    silviculture: Record<string, { volume: number | null } | null>;
    otherCosts: { volume: number | null };
  },
  row: number,
): number | null {
  if (row === 139 || row === 140) {
    return doc.silviculture[row === 139 ? 'lessAdmin' : 'total']?.volume ?? null;
  }
  if (row === 19) {
    return doc.otherCosts.volume ?? null;
  }
  return doc.lineItems.find((li) => li.costItemCode === row)?.volume ?? null;
}

Then(
  'the saved Schedule 1 volume for row {int} should be empty',
  async ({ request, world }, row) => {
    const { millId, year } = world.scheduleKey!;
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return `GET -> HTTP ${res.status()}`;
        return volumeOfRow(await res.json(), row);
      })
      .toBeNull();
  },
);

Then(
  'the Schedule 1 {string} field still shows {string}',
  async ({ schedule1Page }, label, value) => {
    // A failed save must keep the entered values in the form (S23) — nothing is cleared or reloaded.
    // Compared UNGROUPED: the restyle (#237) groups an amount on BLUR (`onBlur={groupField(...)}`), so
    // clicking Save re-punctuates anything four digits or longer. Asserting the raw number keeps this
    // step honest for any value, not just the short one S23 happens to use.
    await expect
      .poll(() => schedule1Page.amountValue(schedule1Page.fieldIdFor(label)), {
        message: `Schedule 1 "${label}" should still hold the entered value`,
      })
      .toBe(value);
  },
);

Then(
  'the Schedule 1 {string} field should be empty',
  async ({ schedule1Page }, label) => {
    // Asserts what a freshly-RENDERED form holds, so it reads the value the GET served rather than the
    // stored column (the API read-back steps cover the column). Used after a reopen to prove a cleared
    // field comes back blank instead of the old number — and, on the shared Other Costs volume, that
    // the page renders at all when that row's volume is null (defects.md BUG-3).
    await expect
      .poll(() => schedule1Page.amountValue(schedule1Page.fieldIdFor(label)), {
        message: `Schedule 1 "${label}" should render empty`,
      })
      .toBe('');
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

/**
 * Read-back for the VOLUME-ONLY line items (143 Forest Mgmt Admin, 144 Subtotal Company Logging). Their
 * cost is a Schedule 3 pull / derivation, so only the volume round-trips through the PUT — asserting a
 * cost here would assert a server-owned figure, not what the scenario wrote.
 */
Then(
  'the saved Schedule 1 should have line item {int} with volume {int}',
  async ({ request, world }, code, volume) => {
    const { millId, year } = world.scheduleKey!;
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return null;
        const doc = (await res.json()) as {
          lineItems: { costItemCode: number; volume: number | null }[];
        };
        const li = doc.lineItems.find((x) => x.costItemCode === code);
        return li && li.volume !== null ? Number(li.volume) : null;
      })
      .toBe(volume);
  },
);

/** The silviculture block keys whose VOLUME is user-entered but whose cost is pulled (139) / derived (140). */
const VOLUME_ONLY_SILV: Record<string, 'lessAdmin' | 'total'> = {
  'Less Silviculture Admin': 'lessAdmin',
  'Total Silviculture': 'total',
};

Then(
  'the saved Schedule 1 should have {string} silviculture with volume {int}',
  async ({ request, world }, label, volume) => {
    const key = VOLUME_ONLY_SILV[label];
    expect(key, `unknown volume-only silviculture row: "${label}"`).toBeTruthy();
    const { millId, year } = world.scheduleKey!;
    await expect
      .poll(async () => {
        const res = await request.get(scheduleUrl(millId, year));
        if (!res.ok()) return null;
        const doc = (await res.json()) as {
          silviculture: Record<string, { volume: number | null } | null>;
        };
        const row = doc.silviculture[key];
        return row && row.volume !== null ? Number(row.volume) : null;
      })
      .toBe(volume);
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
