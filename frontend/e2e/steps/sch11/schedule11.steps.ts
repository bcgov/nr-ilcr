import { settleBeforeReadingSpy } from '../../pages/common/settle';
import { Given, When, Then, expect } from '../fixtures';
import {
  A11Y_ANCHOR,
  ADD_ANCHOR,
  BEC_PRIMARY,
  BEC_SECONDARY,
  BEC_POPULATED_FIRST_OPTION,
  BEC_POPULATED_PREFIX,
  CANCEL_DELETE_ANCHOR,
  CHECK_MET_ANCHOR,
  CHECK_MISSING_ACTUAL_ANCHOR,
  CHECK_MISSING_PLANNED_ANCHOR,
  CORRECTION_ANCHOR,
  DELETE_ANCHOR,
  EMPTY_TABLE_TEXT,
  GUARD_ANCHORS,
  GUARD_ANCHOR_ROWS,
  INLINE_EDIT_ANCHOR,
  MARKER,
  MSG,
  MULTI_ADD_ANCHOR,
  PERSIST_ANCHOR,
  STALE_EDIT_ANCHOR,
  type BecOption,
  type Sch11Anchor,
  TRACK_INDEPENDENCE_ANCHOR,
  VALIDATION_ANCHOR,
  millOptionText,
} from '../../fixtures/sch11/schedule11-test-data';
import { type Sch11Column } from '../../pages/sch11/schedule11Page';
import {
  type Sch11Document,
  type Sch11Location,
  addLocation,
  editLocationAsAnotherSession,
  getSchedule11,
  locationByMarker,
  locationsByMarker,
} from './schedule11Api';

/**
 * Schedule 11 (UC-SCH11-001) steps. Domain vocabulary only — no DOM (that lives in Schedule11Page /
 * HomePage). Success is asserted with the shared "I should see the message" step; persistence is always
 * verified by API read-back (GET the document and assert the stored record), never by the toast alone.
 * Rejections prove the NEGATIVE with the mutation spy — that no write was even attempted — not merely
 * that the page did not move.
 */


/** The named mutating anchors a scenario can claim. Each is owned by exactly one scenario. */
const MUTATING_ANCHORS: Record<string, { anchor: Sch11Anchor; marker: string }> = {
  add: { anchor: ADD_ANCHOR, marker: MARKER.add },
  'multi-add': { anchor: MULTI_ADD_ANCHOR, marker: MARKER.multiFirst },
  'inline-edit': { anchor: INLINE_EDIT_ANCHOR, marker: MARKER.inlineEdit },
  delete: { anchor: DELETE_ANCHOR, marker: MARKER.delete },
  'cancel-delete': { anchor: CANCEL_DELETE_ANCHOR, marker: MARKER.cancelDelete },
  persist: { anchor: PERSIST_ANCHOR, marker: MARKER.persist },
  'check-met': { anchor: CHECK_MET_ANCHOR, marker: MARKER.checkMet },
  'check-missing-actual': {
    anchor: CHECK_MISSING_ACTUAL_ANCHOR,
    marker: MARKER.checkMissingActual,
  },
  'check-missing-planned': {
    anchor: CHECK_MISSING_PLANNED_ANCHOR,
    marker: MARKER.checkMissingPlanned,
  },
  'track-independence': {
    anchor: TRACK_INDEPENDENCE_ANCHOR,
    marker: MARKER.trackIndependence,
  },
  a11y: { anchor: A11Y_ANCHOR, marker: MARKER.a11y },
  correction: { anchor: CORRECTION_ANCHOR, marker: MARKER.correction },
  'stale-edit': { anchor: STALE_EDIT_ANCHOR, marker: MARKER.staleEdit },
};

/**
 * The anchor the scenario claimed, with a loud, actionable failure instead of an opaque TypeError from a
 * bare `world.scheduleKey!` when a precondition step was forgotten.
 */
const claimedKey = (world: { scheduleKey?: { millId: number; year: number } }) => {
  expect(
    world.scheduleKey,
    'no Schedule 11 anchor claimed — a scenario must start with a "the Schedule 11 anchor …" / "guard anchor …" Given',
  ).toBeTruthy();
  return world.scheduleKey!;
};

const namedAnchor = (name: string): { anchor: Sch11Anchor; marker: string } => {
  const entry = MUTATING_ANCHORS[name];
  expect(
    entry,
    `unknown Schedule 11 anchor "${name}". Known: ${Object.keys(MUTATING_ANCHORS).join(', ')}`,
  ).toBeTruthy();
  return entry;
};

/**
 * Assert the anchor holds NO rows at all before the scenario seeds its own.
 *
 * WHY THIS IS ASSERTED RATHER THAN ASSUMED: the downstream footer-total, row-count and empty-table
 * assertions are only true of an otherwise-empty anchor — happy-path's "the anchor starts pristine, so
 * the single row IS the totals", multiple-locations' `lists 2 locations`, delete's `table is empty` plus
 * blank footer. ONE escaped row (a killed run, a partial cleanup, a manual poke at the seeded DB) turns
 * those into confusing reds with nothing pointing at the cause. Listing the offending rows here makes the
 * failure self-diagnosing. Preflight makes the same check once for the whole set, so the usual case fails
 * before any browser opens; this is the per-scenario backstop for a row that appeared mid-run.
 *
 * The S10 track-independence anchor is the ONE anchor that legitimately carries seeded rows — it has its
 * own precondition below and deliberately asserts no totals or counts.
 */
const expectNoRowsAtRest = (doc: Sch11Document, anchor: Sch11Anchor): void => {
  expect(
    doc.locations.map((l) => l.location),
    `precondition: anchor ${anchor.key.millId}/${anchor.key.year} must hold NO locations before this scenario seeds its own, found ${doc.locations.length}. Delete the listed rows (leftover E2E markers, or a hand-edit of the seeded DB) and re-run.`,
  ).toEqual([]);
};

/** Resolve the Gherkin-facing BEC token to a real, discovered catalogue option. */
const becByToken = (token: string): BecOption => {
  if (token === 'primary') return BEC_PRIMARY;
  if (token === 'secondary') return BEC_SECONDARY;
  throw new Error(`unknown Biogeo token "${token}". Use "primary", "secondary" or "free text".`);
};

/**
 * Gherkin table `| field | value |` → a lookup, preserving "absent means do not touch".
 *
 * `dataTable.hashes()` (the suite's established convention — see steps/sch1) keys each row by the
 * HEADER row, so the table must declare `| field | value |` and this reads those two columns.
 */
interface GherkinDataTable {
  hashes: () => Record<string, string>[];
}

const tableToMap = (dataTable: GherkinDataTable): Map<string, string> => {
  const map = new Map<string, string>();
  for (const row of dataTable.hashes()) {
    map.set((row.field ?? '').trim(), (row.value ?? '').trim());
  }
  return map;
};

// =====================================================================================================
// Preconditions
// =====================================================================================================

Given(
  'the Schedule 11 anchor {string} is a pristine editable Draft',
  async ({ request, world, schedule11Cleanup }, name) => {
    const { anchor, marker } = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    world.sch11Marker = marker;
    // Register cleanup the moment we know we will write, so a mid-scenario failure still tears down.
    schedule11Cleanup.push({ key: anchor.key, marker });

    const doc = await getSchedule11(request, anchor.key);
    expect(doc.trackStatus, 'precondition: silviculture track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: schedule must be editable').toBe(true);
    // PRISTINE means EMPTY, not merely "no row of ours" — see expectNoRowsAtRest. An earlier version only
    // checked for this scenario's own marker, which left every totals/row-count assertion downstream
    // depending on an emptiness nobody verified.
    expectNoRowsAtRest(doc, anchor);
  },
);

Given(
  'the Schedule 11 anchor {string} has a seeded location {string}',
  async ({ request, world, schedule11Cleanup }, name, marker) => {
    const { anchor } = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    world.sch11Marker = marker;
    schedule11Cleanup.push({ key: anchor.key, marker });

    const doc = await getSchedule11(request, anchor.key);
    expect(doc.trackStatus, 'precondition: silviculture track must be Draft ("D")').toBe('D');
    expect(doc.editable, 'precondition: schedule must be editable').toBe(true);
    // The seeded row must be the ONLY row: inline-edit reads the footer totals as that row's values and
    // delete asserts the table goes empty afterwards, so a stray row breaks both.
    expectNoRowsAtRest(doc, anchor);

    await addLocation(request, anchor.key, {
      location: marker,
      enhancedIndicator: true,
      biogeoclimaticCatalogueId: BEC_PRIMARY.id,
      netArea: 100,
      actualCost: 5000,
      plannedCost: 4500,
    });
  },
);

Given(
  'the Schedule 11 anchor {string} has a seeded location {string} with no {string} cost',
  async ({ request, world, schedule11Cleanup }, name, marker, which) => {
    // S05/S06 — a location whose Actual (or Planned) cost is NULL. Missing means NULL, never zero:
    // BR-07 keys on null, so seeding 0 here would silently make the scenario prove nothing.
    const { anchor } = namedAnchor(name);
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    world.sch11Marker = marker;
    schedule11Cleanup.push({ key: anchor.key, marker });

    const doc = await getSchedule11(request, anchor.key);
    expect(doc.editable, 'precondition: schedule must be editable').toBe(true);
    // Check Status reports one line PER offending row, so a stray row could add error lines this scenario
    // never accounted for (or satisfy "requirements met" from data it did not seed).
    expectNoRowsAtRest(doc, anchor);

    const lower = which.toLowerCase();
    expect(['actual', 'planned'], `unknown cost "${which}"`).toContain(lower);
    await addLocation(request, anchor.key, {
      location: marker,
      enhancedIndicator: false,
      biogeoclimaticCatalogueId: BEC_SECONDARY.id,
      netArea: 10,
      actualCost: lower === 'actual' ? null : 1200,
      plannedCost: lower === 'planned' ? null : 1200,
    });

    // Prove the seed really landed with NO cost there — the whole scenario rests on it.
    // Jackson is configured non_null, so an absent cost is OMITTED from the JSON entirely rather than
    // serialized as null: `undefined` and `null` both mean "missing" on this wire, hence the loose check.
    const seeded = await locationByMarker(request, anchor.key, marker);
    const seededCost = lower === 'actual' ? seeded.actualCost : seeded.plannedCost;
    expect(
      seededCost ?? null,
      `precondition: seeded ${lower} cost must be missing, got ${String(seededCost)}`,
    ).toBeNull();
  },
);

Given(
  'the Schedule 11 location {string} will also be cleaned up',
  async ({ world, schedule11Cleanup }, marker) => {
    // A scenario that creates MORE than one row registers each extra marker explicitly, so teardown
    // deletes every row it made. Without this a second row would survive and poison the pristine-anchor
    // precondition on the next run.
    const key = world.scheduleKey;
    expect(key, 'a Schedule 11 precondition must claim an anchor first').toBeTruthy();
    schedule11Cleanup.push({ key: key!, marker });
  },
);

Given('the Schedule 11 validate-only anchor is an editable Draft', async ({ request, world }) => {
  // S14–S19: reached, never written to. Deliberately NOT one of the mutating anchors and NOT registered
  // for cleanup — the client-side gate returns before the POST, so no write ever lands. That is what
  // keeps these scenarios parallel-safe against each other.
  world.scheduleKey = VALIDATION_ANCHOR.key;
  world.millOption = millOptionText(VALIDATION_ANCHOR.mill);

  const doc = await getSchedule11(request, VALIDATION_ANCHOR.key);
  expect(doc.trackStatus, 'precondition: silviculture track must be Draft ("D")').toBe('D');
  expect(doc.editable, 'precondition: schedule must be editable (Add panel rendered)').toBe(true);
  // Deliberately does NOT seed `world.sch11RowCountBefore`. It used to, from `doc.locations.length` — an
  // API count — while the only reader ("the listed Schedule 11 row count is unchanged") compares a UI
  // count from `schedule11Page.rowCount()`. Nothing read it, so it was a landmine rather than a bug: the
  // baseline is always taken through the UI by the explicit "I note the listed Schedule 11 row count" step.
});

Given('the Schedule 11 guard anchor {string}', async ({ request, world }, name) => {
  const anchor = GUARD_ANCHORS[name];
  expect(
    anchor,
    `unknown Schedule 11 guard anchor "${name}". Known: ${Object.keys(GUARD_ANCHORS).join(', ')}`,
  ).toBeTruthy();
  world.scheduleKey = anchor.key;
  world.millOption = millOptionText(anchor.mill);

  // Assert the GET status the guard state depends on, so DB drift fails as an obvious re-ground rather
  // than as a confusing UI timeout later.
  const res = await request.get(
    `/api/v1/schedule11?millId=${anchor.key.millId}&year=${anchor.key.year}`,
  );
  expect(
    res.status(),
    `guard anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) should GET HTTP ${anchor.expectHttp}`,
  ).toBe(anchor.expectHttp);

  if (anchor.expectHttp === 200) {
    const doc = (await res.json()) as { trackStatus: string | null; editable: boolean };
    expect(doc.editable, `guard anchor "${name}" must be read-only (editable:false)`).toBe(false);
    expect(
      doc.trackStatus,
      `guard anchor "${name}" must have a non-Draft silviculture track`,
    ).not.toBe('D');
  }
});

Given(
  'the Schedule 1-10 track is past Draft while the silviculture track is still Draft',
  async ({ request, world, schedule11Cleanup }) => {
    // S10 — the ONE seeded row proving BR-02 track independence. Assert BOTH sides explicitly: the
    // silviculture track is Draft AND editable, while the 1–10 track has advanced past Draft. If a
    // re-extract moves either, this fails as a re-ground rather than silently testing nothing.
    const anchor = TRACK_INDEPENDENCE_ANCHOR;
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    world.sch11Marker = MARKER.trackIndependence;
    schedule11Cleanup.push({ key: anchor.key, marker: MARKER.trackIndependence });

    const ctxRes = await request.get(
      `/api/v1/mill-context?millId=${anchor.key.millId}&year=${anchor.key.year}`,
    );
    expect(ctxRes.ok(), `mill-context GET returned HTTP ${ctxRes.status()}`).toBeTruthy();
    const ctx = (await ctxRes.json()) as {
      schedules1To10Status?: { code?: string } | null;
      schedule11Status?: { code?: string } | null;
    };
    expect(
      ['S', 'V'],
      `precondition: the Schedule 1-10 track must be past Draft, got ${String(ctx.schedules1To10Status?.code)}`,
    ).toContain(ctx.schedules1To10Status?.code);
    expect(
      ctx.schedule11Status?.code,
      'precondition: the silviculture track must still be Draft',
    ).toBe('D');

    const doc = await getSchedule11(request, anchor.key);
    expect(
      doc.editable,
      'precondition: Schedule 11 must remain EDITABLE despite the 1-10 track being past Draft',
    ).toBe(true);
    // NOT expectNoRowsAtRest: this is the ONE anchor that legitimately arrives with seeded locations
    // (23050/2016 carries "20173" and "20173-2" in the extract), because it is the only (mill, year) in
    // the seed with a past-Draft 1-10 track and a Draft silviculture track — there was no empty
    // alternative to pick. The scenario is written for that: it asserts the added row's stored record and
    // the live editing surface, never a row count or a footer total. Only OUR row must be absent, or the
    // add would hit the app's duplicate (location, biogeo) rule instead of succeeding.
    expect(
      doc.locations.filter((l) => l.location === MARKER.trackIndependence).length,
      `precondition: anchor ${anchor.key.millId}/${anchor.key.year} already holds a "${MARKER.trackIndependence}" row — leftover from an interrupted run; delete it and re-run.`,
    ).toBe(0);
  },
);

Given('a spy is watching the Schedule 11 location requests', async ({ schedule11MutationSpy }) => {
  // Referencing the fixture installs the route for this scenario only.
  expect(schedule11MutationSpy.mutations).toBe(0);
});

// =====================================================================================================
// Actions
// =====================================================================================================

When('I open Schedule 11', async ({ schedule11Page }) => {
  await schedule11Page.openViaNav();
});

When('I open Schedule 11 expecting a guard message', async ({ schedule11Page }) => {
  await schedule11Page.openViaNavExpectingGuard();
});

When('I open Schedule 11 with no working context', async ({ schedule11Page }) => {
  await schedule11Page.openWithNoContext();
});

When('I reopen Schedule 11', async ({ schedule11Page }) => {
  await schedule11Page.reload();
});

When('I fill the Add New Location panel:', async ({ schedule11Page }, dataTable: GherkinDataTable) => {
  // A field ABSENT from the table is deliberately left untouched — that is how the rejection
  // scenarios express "left empty" without typing and clearing.
  const values = tableToMap(dataTable);
  const biogeo = values.get('Biogeo');
  await schedule11Page.fillAddPanel({
    location: values.get('Location'),
    enhanced: values.get('Enhanced') as 'Yes' | 'No' | undefined,
    bec: biogeo && biogeo !== 'free text' ? becByToken(biogeo) : undefined,
    becFreeText: biogeo === 'free text' ? BEC_POPULATED_PREFIX : undefined,
    becFreeTextOption: biogeo === 'free text' ? BEC_POPULATED_FIRST_OPTION : undefined,
    netArea: values.get('NAR(ha)'),
    actualCost: values.get('Actual Cost'),
    plannedCost: values.get('Planned Cost'),
    comments: values.get('Comments'),
  });
});

When('I click Add', async ({ schedule11Page }) => {
  await schedule11Page.clickAdd();
});

When('I start editing the Schedule 11 location {string}', async ({ schedule11Page }, marker) => {
  await schedule11Page.startEdit(marker);
});

When('I change the inline {string} to {string}', async ({ schedule11Page }, field, value) => {
  switch (field) {
    case 'Location':
      await schedule11Page.editLocation.fill(value);
      break;
    case 'NAR(ha)':
      await schedule11Page.editNetArea.fill(value);
      break;
    case 'Actual Cost':
      await schedule11Page.editActualCost.fill(value);
      break;
    case 'Planned Cost':
      await schedule11Page.editPlannedCost.fill(value);
      break;
    case 'Comments':
      await schedule11Page.editComments.fill(value);
      break;
    case 'Enhanced':
      await schedule11Page.setEditEnhanced(value as 'Yes' | 'No');
      break;
    case 'Biogeo':
      await schedule11Page.setEditBec(becByToken(value));
      break;
    default:
      throw new Error(`unknown inline field "${field}"`);
  }
});

When(
  'another session changes the Schedule 11 location {string} to actual cost {int}',
  async ({ request, world }, marker, cost: number) => {
    // GAP-3: mutate the row through the API while the browser holds an OPEN editor. `startEdit` already
    // captured the row's revisionCount into React state, so this write bumps the stored token and the
    // browser's pending save becomes stale — exactly the two-user conflict, without needing a second
    // browser context.
    await editLocationAsAnotherSession(request, claimedKey(world), marker, { actualCost: cost });
  },
);

When('I save the inline edit', async ({ schedule11Page }) => {
  await schedule11Page.saveEdit();
});

When('I cancel the inline edit', async ({ schedule11Page }) => {
  await schedule11Page.cancelEdit();
});

When('I delete the Schedule 11 location {string}', async ({ schedule11Page }, marker) => {
  await schedule11Page.clickDelete(marker);
});

When('I confirm the delete', async ({ schedule11Page }) => {
  await schedule11Page.confirmDelete();
});

When('I cancel the delete', async ({ schedule11Page }) => {
  await schedule11Page.cancelDelete();
});

When('I run Check Status', async ({ schedule11Page }) => {
  await schedule11Page.checkStatusButton.click();
});

When('I note the Schedule 11 mutation count', async ({ world, schedule11MutationSpy }) => {
  world.sch11MutationsBefore = schedule11MutationSpy.mutations;
});

When('I note the listed Schedule 11 row count', async ({ world, schedule11Page }) => {
  world.sch11RowCountBefore = await schedule11Page.rowCount();
});

// =====================================================================================================
// Assertions — UI
// =====================================================================================================

Then('the Schedule 11 location {string} is listed', async ({ schedule11Page }, marker) => {
  await expect(schedule11Page.row(marker)).toHaveCount(1);
});

Then('the Schedule 11 location {string} is not listed', async ({ schedule11Page }, marker) => {
  await expect(schedule11Page.row(marker)).toHaveCount(0);
});

Then('the Schedule 11 table lists {int} locations', async ({ schedule11Page }, count: number) => {
  await expect
    .poll(async () => schedule11Page.rowCount(), {
      message: `expected ${count} listed Schedule 11 locations`,
    })
    .toBe(count);
});

Then(
  'the listed Schedule 11 row count is unchanged',
  async ({ world, schedule11Page }) => {
    expect(
      world.sch11RowCountBefore,
      'no baseline row count was noted — add the "I note the listed Schedule 11 row count" step first',
    ).not.toBeUndefined();
    await expect
      .poll(async () => schedule11Page.rowCount(), {
        message: 'the listed row count changed when it should not have',
      })
      .toBe(world.sch11RowCountBefore);
  },
);

Then(
  'the Schedule 11 row {string} shows {string} in {string}',
  async ({ schedule11Page }, marker, expected, column) => {
    await expect
      .poll(async () => schedule11Page.cell(marker, column as Sch11Column), {
        message: `row "${marker}" column "${column}" should read "${expected}"`,
      })
      .toBe(expected);
  },
);

Then(
  'the Schedule 11 footer total {string} shows {string}',
  async ({ schedule11Page }, column, expected) => {
    await expect
      .poll(async () => schedule11Page.total(column as Sch11Column), {
        message: `footer total "${column}" should read "${expected}"`,
      })
      .toBe(expected);
  },
);

Then('the Add New Location panel is rendered', async ({ schedule11Page }) => {
  await expect(schedule11Page.addPanelHeading).toBeVisible();
  await expect(schedule11Page.addLocation).toBeVisible();
});

Then('the Add New Location panel is not rendered', async ({ schedule11Page }) => {
  // Re-grounded divergence: legacy DISABLED the six Add fields; the React app omits the whole panel
  // when `editable` is false. Absence is the modern read-only contract (defects.md Divergence #3).
  await expect(schedule11Page.addPanelHeading).toHaveCount(0);
  await expect(schedule11Page.addLocation).toHaveCount(0);
});

Then('the Schedule 11 row actions are not rendered', async ({ schedule11Page }) => {
  await expect(schedule11Page.actionsHeader).toHaveCount(0);
  await expect(
    schedule11Page.table.getByRole('button', { name: 'Edit', exact: true }),
  ).toHaveCount(0);
  await expect(
    schedule11Page.table.getByRole('button', { name: 'Delete', exact: true }),
  ).toHaveCount(0);
});

Then('the Check Status button is disabled', async ({ schedule11Page }) => {
  await expect(schedule11Page.checkStatusButton).toBeDisabled();
});

Then('the Check Status button is enabled', async ({ schedule11Page }) => {
  await expect(schedule11Page.checkStatusButton).toBeEnabled();
});

Then('the Schedule 11 table is empty', async ({ schedule11Page }) => {
  await expect(schedule11Page.emptyPlaceholder(EMPTY_TABLE_TEXT)).toBeVisible();
});

Then('the Schedule 11 delete confirmation is dismissed', async ({ schedule11Page }) => {
  await expect(schedule11Page.confirmModal).toBeHidden();
});

Then('the Schedule 11 content is suppressed', async ({ schedule11Page }) => {
  // A guard state renders a PageState notification INSTEAD of the schedule body.
  await expect(schedule11Page.table).toHaveCount(0);
  await expect(schedule11Page.addLocation).toHaveCount(0);
});

// =====================================================================================================
// Assertions — API read-back (persistence is proven against the write path, not the toast)
// =====================================================================================================

/**
 * Format a stored field for comparison against the Gherkin table's plain value.
 *
 * Jackson is configured non_null, so a null column is OMITTED from the JSON rather than serialized as
 * null — `undefined` therefore also means "no value" and must render as the empty string, not the
 * literal "undefined". The `?? null` normalisation is the whole point of this helper.
 */
const asText = (value: number | string | boolean | null | undefined): string =>
  (value ?? null) === null ? '' : String(value);

Then(
  'the Schedule 11 location {string} is persisted as:',
  async ({ request, world }, marker, dataTable: GherkinDataTable) => {
    const key = world.scheduleKey!;
    const expected = tableToMap(dataTable);

    // Poll: the click that triggered the write can race the commit, so a single-shot GET could read
    // the pre-write document and fail spuriously (the skill's UI-triggered read-back rule).
    await expect
      .poll(
        async () => {
          const rows = await locationsByMarker(request, key, marker);
          if (rows.length !== 1) return null;
          const row = rows[0];
          const actual: Record<string, string> = {};
          for (const field of expected.keys()) {
            actual[field] = readField(row, field);
          }
          return JSON.stringify(actual);
        },
        {
          message: `Schedule 11 location "${marker}" on ${key.millId}/${key.year} should be persisted as ${JSON.stringify(
            Object.fromEntries(expected),
          )}`,
        },
      )
      .toBe(JSON.stringify(Object.fromEntries(expected)));
  },
);

/** Map a Gherkin field label onto the stored `SilvicultureLocation` member, as displayable text. */
function readField(row: Sch11Location, field: string): string {
  switch (field) {
    case 'Enhanced':
      // Require a REAL boolean before mapping. `enhancedIndicator ? 'Yes' : 'No'` reads a missing,
      // null or malformed field as "No", so a broken API response would silently satisfy every
      // `| Enhanced | No |` expectation — the read-back would pass while proving nothing.
      if (typeof row.enhancedIndicator !== 'boolean') {
        throw new Error(
          `location "${row.location}" returned enhancedIndicator=${JSON.stringify(
            row.enhancedIndicator,
          )} — expected a boolean; the API contract is broken, not the expectation`,
        );
      }
      return row.enhancedIndicator ? 'Yes' : 'No';
    case 'Biogeo':
      return asText(row.becLabel);
    case 'NAR(ha)':
      return asText(row.netArea);
    case 'Actual Cost':
      return asText(row.actualCost);
    case 'Planned Cost':
      return asText(row.plannedCost);
    case 'Total Cost':
      return asText(row.totalCost);
    case 'Total/NAR':
      return asText(row.costPerNetArea);
    case 'Comments':
      return asText(row.comments);
    default:
      throw new Error(`unknown persisted field "${field}"`);
  }
}

Then(
  'the Schedule 11 location {string} is not persisted',
  async ({ request, world }, marker) => {
    const key = world.scheduleKey!;
    const rows = await locationsByMarker(request, key, marker);
    expect(
      rows.length,
      `no Schedule 11 location "${marker}" should exist on ${key.millId}/${key.year}, found ${rows.length}`,
    ).toBe(0);
  },
);

Then(
  'the Schedule 11 location {string} is gone from the schedule',
  async ({ request, world }, marker) => {
    const key = world.scheduleKey!;
    await expect
      .poll(async () => (await locationsByMarker(request, key, marker)).length, {
        message: `Schedule 11 location "${marker}" should be deleted from ${key.millId}/${key.year}`,
      })
      .toBe(0);
  },
);

Then(
  'no Schedule 11 location mutation should have been sent',
  async ({ page, schedule11MutationSpy }) => {
    // The proof that a rejection is client-side: the Add/inline-edit gate returns BEFORE the request,
    // so the mutating endpoint is never called. Asserting only the inline error would also pass if a
    // request HAD been sent and rejected server-side, which is a materially different behaviour.
    //
    // Read the tally only after the deferral+round-trip barrier, so a request fired a tick after the
    // error render is already counted (see settleBeforeReadingSpy).
    await settleBeforeReadingSpy(page);
    expect(
      schedule11MutationSpy.mutations,
      'a rejected entry must fire NO POST/PUT/DELETE on /schedule11/locations',
    ).toBe(0);
  },
);

Then(
  'no further Schedule 11 location mutation should have been sent',
  async ({ page, world, schedule11MutationSpy }) => {
    // Same barrier as the absolute form above — the negative must hold over a window.
    await settleBeforeReadingSpy(page);
    expect(
      world.sch11MutationsBefore,
      'no baseline mutation count was noted — add the "I note the Schedule 11 mutation count" step first',
    ).not.toBeUndefined();
    expect(
      schedule11MutationSpy.mutations,
      'the rejected entry fired an additional mutating request',
    ).toBe(world.sch11MutationsBefore);
  },
);

Then(
  'the Schedule 11 location {string} still holds revision {int}',
  async ({ request, world }, marker, revision: number) => {
    const key = world.scheduleKey!;
    const row = await locationByMarker(request, key, marker);
    expect(
      row.revisionCount,
      `location "${marker}" revisionCount should still be ${revision} (no write happened)`,
    ).toBe(revision);
  },
);

/**
 * A genuinely VERBATIM assertion on the Check Status result — compares the region's raw `textContent`,
 * so embedded runs of whitespace survive.
 *
 * Use this (not the generic "I should see the error") for FLD-004, whose text carries a literal DOUBLE
 * space after "location". Playwright's text matchers normalize whitespace and would happily pass a
 * single-space regression.
 */
Then(
  'the Schedule 11 check status shows verbatim {string}',
  async ({ schedule11Page }, text) => {
    await expect
      .poll(async () => schedule11Page.checkResultRawText(), {
        message: `the Check Status result should contain the VERBATIM text ${JSON.stringify(text)} (raw textContent, whitespace preserved)`,
      })
      .toContain(text);
  },
);

/** Prove a delete never happened: the SUC-002 text must be absent from the page entirely. */
Then('no Schedule 11 delete confirmation message is shown', async ({ page }) => {
  await expect(page.getByText(MSG.deleted)).toHaveCount(0);
});

/** A read-only guard anchor's seeded row content — a POSITIVE assertion so S20 cannot pass vacuously. */
Then(
  'the Schedule 11 read-only table still shows the seeded row for {string}',
  async ({ schedule11Page }, anchorName) => {
    const row = GUARD_ANCHOR_ROWS[anchorName];
    expect(
      row,
      `no seeded-row reference pinned for guard anchor "${anchorName}" — add one to GUARD_ANCHOR_ROWS`,
    ).toBeTruthy();
    await expect(schedule11Page.row(row.location)).toHaveCount(1);
    await expect
      .poll(async () => schedule11Page.cell(row.location, 'Biogeo/Subzone/Variant'), {
        message: `read-only row "${row.location}" should still display its catalogue label`,
      })
      .toBe(row.becLabel);
  },
);
