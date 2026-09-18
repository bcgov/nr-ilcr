import { Given, When, Then, expect } from '../fixtures';
import {
  ADD_ANCHOR,
  AREA_TYPE_CORRECTION_ANCHOR,
  NO_COMPUTED_RATE,
  READ_ONLY_ANCHOR,
  S12_RECORD,
  S17_GENERAL_COMMENT,
  S17_RECORDS,
  S17_TOTALS,
  VALIDATION_AMOUNTS,
  CHECK_MISSING_COST_ANCHOR,
  CHECK_MISSING_SUPPLY_BLOCK_ANCHOR,
  CHECK_MISSING_TFL_ANCHOR,
  EDIT_ANCHOR,
  GENERAL_COMMENT_ANCHOR,
  GUARDS,
  GUARD_MESSAGES,
  INVALID_TFL,
  type Sch6Anchor,
  S01_RECORD,
  S01_TOTALS,
  S02_EDITED,
  S02_SEEDED,
  REQUIREMENTS_MET,
  S03_RECORD,
  S04_GENERAL_COMMENT,
  S05_RECORD,
  S09_RECORD,
  S10_RECORD,
  S11_RECORD,
  TFL_ANCHOR,
  TFL_CORRECTION_ANCHOR,
  TFL_OPTION,
  VALIDATE_ONLY_ANCHOR,
  VALID_TFL,
  millOptionText,
  scheduleUrl,
} from '../../fixtures/sch6/schedule6-test-data';
import { addRecord, readSchedule6 } from './schedule6Api';

/**
 * UC-SCH6-001 (Schedule 6 — Report Road Management Costs) steps.
 *
 * No DOM selectors here — every interaction goes through `schedule6Page`. The working-context Given is
 * NOT redefined: `steps/common/home-context.steps.ts` owns "I have selected that mill and reporting
 * year on the Home page", and a second definition is the duplicate-step error playwright-bdd rightly
 * rejects. This file's job is to set `world.scheduleKey` + `world.millOption` before that step runs.
 */

/** The sch6 anchors a Given can name, so the `.feature` reads in app vocabulary rather than ids. */
const ANCHORS: Record<string, Sch6Anchor> = {
  add: ADD_ANCHOR,
  edit: EDIT_ANCHOR,
  tfl: TFL_ANCHOR,
  'general-comment': GENERAL_COMMENT_ANCHOR,
  'tfl-correction': TFL_CORRECTION_ANCHOR,
  'validate-only': VALIDATE_ONLY_ANCHOR,
  'check-missing-cost': CHECK_MISSING_COST_ANCHOR,
  'check-missing-tfl': CHECK_MISSING_TFL_ANCHOR,
  'check-missing-supply-block': CHECK_MISSING_SUPPLY_BLOCK_ANCHOR,
  'area-type-correction': AREA_TYPE_CORRECTION_ANCHOR,
};

Given(
  'the Schedule 6 anchor {string} is an editable Draft with no road records',
  async ({ request, world }, name: string) => {
    const anchor = ANCHORS[name];
    expect(anchor, `unknown Schedule 6 anchor "${name}" — add it to ANCHORS in schedule6.steps.ts`).toBeTruthy();

    // Re-asserted here as well as in preflight ON PURPOSE. Preflight runs ONCE per run; this scenario
    // may execute many minutes later, in parallel with others, and the claim it opens with ("the list
    // shows the empty placeholder") depends on the anchor still being empty AT THIS MOMENT. A record
    // that escaped another run would otherwise surface as a confusing UI assertion failure.
    const doc = await readSchedule6(request, anchor.key);
    expect(doc.trackStatus, `anchor "${name}" must be a Draft 1-10 track`).toBe('D');
    expect(doc.editable, `anchor "${name}" must be editable`).toBe(true);
    expect(
      doc.roadRecords.map((r) => r.recordId),
      `anchor "${name}" (${anchor.key.millId}/${anchor.key.year}) must hold NO road records at rest`,
    ).toEqual([]);

    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
  },
);

Given(
  'I will record a road maintenance record commented {string}',
  async ({ world, schedule6Cleanup }, comments: string) => {
    // REGISTERED BEFORE ANYTHING IS SAVED. The registration cannot wait for a recordId: a failure
    // between clicking `Add Report` and reading the response is exactly when cleanup matters, and at
    // that point the row may exist with no id known to the test.
    world.sch6RecordComment = comments;
    schedule6Cleanup.push({ key: world.scheduleKey!, comments });
  },
);

When('I open Schedule 6', async ({ schedule6Page }) => {
  await schedule6Page.open();
});

Then('the Schedule 6 record list shows no records', async ({ schedule6Page }) => {
  await expect(schedule6Page.emptyList).toBeVisible();
});

When('I open the Add Road Maintenance report panel', async ({ schedule6Page }) => {
  await schedule6Page.openAddPanel();
});

Then('the Add panel is shown with its fields blank', async ({ schedule6Page }) => {
  await expect(schedule6Page.addPanel).toBeVisible();
  await schedule6Page.assertAddPanelBlank();
});

When('I enter the S01 road record', async ({ schedule6Page }) => {
  // ORDER IS CONTRACTUAL: the Supply Block list is filtered to codes starting with the chosen TSA
  // (utils/codes.ts supplyBlocksFor), so selecting the block before the area type offers nothing.
  await schedule6Page.selectAreaType(S01_RECORD.areaTypeOption);
  await schedule6Page.selectSupplyBlock(S01_RECORD.supplyBlockOption);
  await schedule6Page.enterAmounts(S01_RECORD.volumeInput, S01_RECORD.costInput);
  await schedule6Page.enterComments(S01_RECORD.comments);
});

Then(
  'the Add panel shows the computed cost per volume {string}',
  async ({ schedule6Page }, expected: string) => {
    // The panel mirrors $ / m³ from the BLURRED volume/cost (index.tsx:404), which is why
    // `enterAmounts` blurs both fields. It deliberately does NOT mirror RMG — see the feature header.
    await expect(schedule6Page.addCostPerVolume).toHaveText(expected);
  },
);

When('I submit the Add panel', async ({ schedule6Page }) => {
  await schedule6Page.addReportButton.click();
});

Then(
  'the road record is persisted with its derived figures',
  async ({ request, world }) => {
    // THE API READ-BACK, not the screen. After `Add Report` the page re-seeds its row forms from the
    // response (index.tsx:796-799), so the row looks identical whether or not anything was stored —
    // a UI-only assertion here would prove rendering, not persistence.
    //
    // `expect.poll` rather than a single GET: the POST is fired from a click, so the read can race the
    // commit.
    const key = world.scheduleKey!;
    const comment = world.sch6RecordComment!;

    await expect
      .poll(
        async () => (await readSchedule6(request, key)).roadRecords.filter((r) => r.comments === comment).length,
        {
          message: `no stored Schedule 6 record commented "${comment}" on ${key.millId}/${key.year}`,
        },
      )
      .toBe(1);

    const doc = await readSchedule6(request, key);
    const record = doc.roadRecords.find((r) => r.comments === comment)!;

    expect(record.areaType, 'stored area type').toBe(S01_RECORD.areaTypeCode);
    expect(record.supplyBlock, 'stored supply block').toBe(S01_RECORD.supplyBlockCode);
    expect(record.tflNumber, 'a TSA record must carry no TFL number (BR-02 clears the counterpart)').toBeFalsy();
    expect(record.volume, 'stored volume').toBe(Number(S01_RECORD.volumeInput));
    expect(record.cost, 'stored cost').toBe(Number(S01_RECORD.costInput));
    // The two SERVER-DERIVED figures. RMG comes from the supply block and is not computed on the
    // client at all; $ / m³ is cost / volume through the same whole-dollar rounding the screen uses.
    expect(record.rmg, 'server-derived RMG for supply block 01B').toBe(S01_RECORD.rmg);
    expect(record.costPerVolume, 'server-derived cost per volume').toBe(
      Number(S01_RECORD.costPerVolumeDisplay),
    );

    // Captured so the row-scoped locators below can address the new row at all.
    world.sch6RecordId = record.recordId;
  },
);

Then('the new record row shows its derived figures', async ({ schedule6Page, world }) => {
  const recordId = world.sch6RecordId!;
  expect(recordId, 'a previous step must capture the new recordId').toBeTruthy();

  // EXPANDED FIRST, and this is not ceremony. Every record renders inside a COLLAPSED Carbon
  // AccordionItem (no `open` prop, index.tsx:1007-1013) and Carbon puts every item's children in the
  // DOM whichever panel is open (index.tsx:479). So a bare toHaveValue here would pass on a row the
  // reporter cannot see — asserting DOM state instead of the screen. `expandRecord` waits on
  // VISIBILITY, which is what makes the four assertions below claims about the UI.
  //
  // Ordinal 1, not the recordId: this anchor held no records at rest, so the new one is the first row.
  await schedule6Page.expandRecord(1, recordId);

  await expect(schedule6Page.rowVolume(recordId)).toHaveValue(S01_RECORD.volumeDisplay);
  await expect(schedule6Page.rowCost(recordId)).toHaveValue(S01_RECORD.costDisplay);
  // RMG is asserted HERE, on the row, rather than in the Add panel — the panel is passed rmg=""
  // deliberately (index.tsx:401). See the feature header's re-grounding note and defects.md VER-1.
  await expect(schedule6Page.rowDerived(recordId, 'RMG')).toHaveText(S01_RECORD.rmg);
  await expect(schedule6Page.rowDerived(recordId, '$ / m³')).toHaveText(
    S01_RECORD.costPerVolumeDisplay,
  );
});

Then('the schedule totals are recomputed from the new record', async ({ request, schedule6Page, world }) => {
  // One record on a previously empty anchor, so each total equals that record's own figure — which is
  // what makes this assertion meaningful rather than tautological: the anchor's at-rest totals were
  // asserted to be 0 by preflight, so a non-zero total can only have come from this record.
  //
  // The labels are the FIELD names, not "Total …" — "Volume m³" / "Cost $" / "$ / m³"
  // (index.tsx:1051-1053) — and the page object matches them anchored end-to-end, so "Volume m³"
  // cannot also be satisfied by "$ / m³".
  await expect(schedule6Page.total('Volume m³')).toHaveText(S01_TOTALS.volume);
  await expect(schedule6Page.total('Cost $')).toHaveText(S01_TOTALS.cost);
  await expect(schedule6Page.total('$ / m³')).toHaveText(S01_TOTALS.costPerVolume);

  const doc = await readSchedule6(request, world.scheduleKey!);
  expect(doc.totalVolume, 'server total volume').toBe(Number(S01_RECORD.volumeInput));
  expect(doc.totalCost, 'server total cost').toBe(Number(S01_RECORD.costInput));
  expect(doc.totalCostPerVolume, 'server total cost per volume').toBe(
    Number(S01_TOTALS.costPerVolume),
  );
});

// ---- S02 — Edit an Existing Road Maintenance Record ------------------------------------------------

Given(
  'a road maintenance record commented {string} already exists on that anchor',
  async ({ request, world, schedule6Cleanup }, comments: string) => {
    // Registered BEFORE the create, so a failure inside the POST still tears down.
    world.sch6RecordComment = comments;
    schedule6Cleanup.push({ key: world.scheduleKey!, comments });

    // Reached through the app's own POST rather than seeded in SQL: the row S02 then edits is a
    // genuinely app-created record, and nothing has to be mirrored into the CI seed.
    const created = await addRecord(request, world.scheduleKey!, {
      areaType: S02_SEEDED.areaTypeCode,
      supplyBlock: S02_SEEDED.supplyBlockCode,
      volume: S02_SEEDED.volume,
      cost: S02_SEEDED.cost,
      comments,
    });
    world.sch6RecordId = created.recordId;
  },
);

Then('the record row shows the amounts it was created with', async ({ schedule6Page, world }) => {
  const recordId = world.sch6RecordId!;
  await schedule6Page.expandRecord(1, recordId);
  await expect(schedule6Page.rowVolume(recordId)).toHaveValue(S02_SEEDED.volumeDisplay);
  await expect(schedule6Page.rowCost(recordId)).toHaveValue(S02_SEEDED.costDisplay);
  await expect(schedule6Page.rowDerived(recordId, '$ / m³')).toHaveText(
    S02_SEEDED.costPerVolumeDisplay,
  );
});

When("I change the record's volume and cost", async ({ schedule6Page, world }) => {
  await schedule6Page.setRowAmounts(
    world.sch6RecordId!,
    S02_EDITED.volumeInput,
    S02_EDITED.costInput,
  );
});

Then(
  'the record row shows the recomputed cost per volume {string}',
  async ({ schedule6Page, world }, expected: string) => {
    // BEFORE any save: the row's $ / m³ tracks the blurred inputs, exactly as the Add panel's does, so
    // this proves the client-side derivation independently of the round-trip that follows.
    await expect(schedule6Page.rowDerived(world.sch6RecordId!, '$ / m³')).toHaveText(expected);
  },
);

When('I save the schedule', async ({ schedule6Page }) => {
  await schedule6Page.saveButton.click();
});

Then('the edited amounts are persisted', async ({ request, world }) => {
  const key = world.scheduleKey!;
  const recordId = world.sch6RecordId!;

  // Polled, because the PUT is fired from a click and the read can race the commit.
  await expect
    .poll(
      async () => {
        const doc = await readSchedule6(request, key);
        return doc.roadRecords.find((r) => r.recordId === recordId)?.volume ?? null;
      },
      { message: `record ${recordId} never reached volume ${S02_EDITED.volumeInput}` },
    )
    .toBe(Number(S02_EDITED.volumeInput));

  const doc = await readSchedule6(request, key);
  const record = doc.roadRecords.find((r) => r.recordId === recordId)!;

  expect(record.cost, 'stored cost after the edit').toBe(Number(S02_EDITED.costInput));
  expect(record.costPerVolume, 'server-recomputed cost per volume').toBe(
    Number(S02_EDITED.costPerVolumeDisplay),
  );
  // The EDIT MUST NOT HAVE CREATED A SECOND ROW. Schedule 6's page-level Save posts every served
  // record in one PUT, so a bug that treated an edited row as new would leave the old one behind and
  // still show the new figures — the totals would then be double and this is what catches it.
  expect(
    doc.roadRecords.length,
    'editing a record must update it in place, not add another row',
  ).toBe(1);
  // Untouched by S02, so still what the record was created with — proves the PUT did not blank the
  // fields it was not asked to change.
  expect(record.areaType, 'area type must be unchanged by an amounts-only edit').toBe(
    S02_SEEDED.areaTypeCode,
  );
  expect(record.supplyBlock, 'supply block must be unchanged by an amounts-only edit').toBe(
    S02_SEEDED.supplyBlockCode,
  );
  expect(record.rmg, 'RMG is derived from the unchanged supply block').toBe(S02_SEEDED.rmg);
});

Then('the schedule totals are recomputed from the edited record', async ({ request, schedule6Page, world }) => {
  await expect(schedule6Page.total('Volume m³')).toHaveText(S02_EDITED.volumeDisplay);
  await expect(schedule6Page.total('Cost $')).toHaveText(S02_EDITED.costDisplay);
  await expect(schedule6Page.total('$ / m³')).toHaveText(S02_EDITED.costPerVolumeDisplay);

  const doc = await readSchedule6(request, world.scheduleKey!);
  expect(doc.totalVolume, 'server total volume').toBe(Number(S02_EDITED.volumeInput));
  expect(doc.totalCost, 'server total cost').toBe(Number(S02_EDITED.costInput));
  expect(doc.totalCostPerVolume, 'server total cost per volume').toBe(
    Number(S02_EDITED.costPerVolumeDisplay),
  );
});

// DOMAIN-SCOPED NAME, deliberately: sch11 already defines a bare "I run Check Status"
// (steps/sch11/schedule11.steps.ts), and a second definition is the duplicate-step error bddgen
// rightly rejects. Every schedule has this button, so the unqualified phrasing cannot belong to two
// domains at once — the suite's existing convention is to qualify it ("I run Schedule 5 Check Status",
// "I run Check Status on Schedule 3"). Promoting one shared step to steps/common/ would need a common
// schedule-page abstraction these page objects do not have today.
When('I run Schedule 6 Check Status', async ({ schedule6Page }) => {
  // Goes through runCheckStatus (which WAITS for the verdict) rather than a bare click, so the same
  // step is sound for the slices that assert an ABSENCE as well as the ones that assert a banner.
  await schedule6Page.runCheckStatus();
});

// ---- S03 — Record a TFL instead of a TSA -----------------------------------------------------------

When('I select the TFL area type', async ({ schedule6Page }) => {
  // "TFL" is a SYNTHETIC SENTINEL the control adds to the list, not a served code — the backend does
  // not serve it (areaTypeOptions / LookUpCacheDAO.java:229-230). Its option text is the sentinel
  // itself, unlike a real TSA whose option text is the code description.
  await schedule6Page.selectAreaType(TFL_OPTION);
});

Then('the TFL number field is enabled and Supply Block is disabled', async ({ schedule6Page }) => {
  // BR-02 keeps exactly ONE side of the classification populated, and the form enforces it by
  // disabling the other side rather than by validating after the fact. Both halves are asserted
  // because either one alone would pass on a form that disabled nothing.
  await expect(schedule6Page.addTflNumber).toBeEnabled();
  await expect(schedule6Page.addSupplyBlock).toBeDisabled();
});

When('I enter the S03 TFL road record', async ({ schedule6Page }) => {
  await schedule6Page.enterTflNumber(S03_RECORD.tflNumber);
  await schedule6Page.enterAmounts(S03_RECORD.volumeInput, S03_RECORD.costInput);
  await schedule6Page.enterComments(S03_RECORD.comments);
});

Then('the TFL road record is persisted with its derived RMG', async ({ request, world }) => {
  const key = world.scheduleKey!;
  const comment = world.sch6RecordComment!;

  await expect
    .poll(
      async () =>
        (await readSchedule6(request, key)).roadRecords.filter((r) => r.comments === comment).length,
      { message: `no stored TFL record commented "${comment}" on ${key.millId}/${key.year}` },
    )
    .toBe(1);

  const doc = await readSchedule6(request, key);
  const record = doc.roadRecords.find((r) => r.comments === comment)!;

  expect(record.areaType, 'a TFL record is served with the TFL sentinel as its area type').toBe(
    TFL_OPTION,
  );
  expect(record.tflNumber, 'stored TFL number').toBe(S03_RECORD.tflNumber);
  // BR-02 counterpart-clear, asserted rather than assumed: choosing TFL must NULL both TSA columns
  // (Schedule6Service:597, Schedule6DAO.java:221-224). A form that merely disabled Supply Block while
  // still posting a stale value would pass every other assertion here.
  expect(record.supplyBlock, 'a TFL record must carry no supply block (BR-02)').toBeFalsy();
  // RMG comes from the TFL code via the fixed RoadGroupLookup table, NOT from a supply block — "48"
  // resolves to "10", deliberately different from the TSA path's "15" so a confused branch fails.
  expect(record.rmg, 'RMG derived from the TFL code').toBe(S03_RECORD.rmg);
  expect(record.costPerVolume, 'server-derived cost per volume').toBe(
    Number(S03_RECORD.costPerVolumeDisplay),
  );

  world.sch6RecordId = record.recordId;
});

// ---- S04 — the schedule-level general comment ------------------------------------------------------

Given('I will set the schedule general comment', async ({ world, schedule6CommentCleanup }) => {
  // Registered BEFORE the save. The undo is a comment-clearing PUT, not a record DELETE: on an empty
  // schedule the comment lives on a bare BR-09 placeholder row, and clearing the comment is what
  // removes it (Schedule6Service:428-437).
  schedule6CommentCleanup.push(world.scheduleKey!);
});

When('I enter the schedule general comment', async ({ schedule6Page }) => {
  await schedule6Page.enterGeneralComment(S04_GENERAL_COMMENT);
});

Then('the general comment field retains the text', async ({ schedule6Page }) => {
  await expect(schedule6Page.generalComments).toHaveValue(S04_GENERAL_COMMENT);
});

Then('the general comment is persisted without adding a road record', async ({ request, world }) => {
  const key = world.scheduleKey!;

  await expect
    .poll(async () => (await readSchedule6(request, key)).generalComments ?? null, {
      message: `the general comment never reached the server on ${key.millId}/${key.year}`,
    })
    .toBe(S04_GENERAL_COMMENT);

  const doc = await readSchedule6(request, key);
  // THE PLACEHOLDER MUST NOT BE SERVED AS A RECORD. Saving a comment on an empty schedule inserts a
  // bare placeholder row to carry it (Schedule6Service:425), and the read side excludes any row whose
  // classification is entirely blank (:470). If that exclusion ever broke, the screen would grow a
  // phantom row with no area type, no supply block and no cost — which Check Status would then report
  // as a failing record. This is the assertion that would catch it, and it is what S18 builds on.
  expect(
    doc.roadRecords.map((r) => r.recordId),
    'a general comment must not surface as a road record',
  ).toEqual([]);
  // Totals stay at zero for the same reason: a phantom row would drag them off zero.
  expect(doc.totalVolume, 'totals are unaffected by a comment-only schedule').toBe(0);
  expect(doc.totalCost, 'totals are unaffected by a comment-only schedule').toBe(0);
});

// ---- S05 — an invalid TFL number is rejected -------------------------------------------------------

Given('I am watching for Schedule 6 writes', async ({ schedule6MutationSpy }) => {
  // Touching the fixture is what INSTALLS its page.route, so this must run before the action under
  // test — the same reason sch1 spy has an explicit step. Asserting a clean start also proves the spy
  // is actually wired rather than silently counting nothing.
  expect(schedule6MutationSpy.mutations, 'the spy should start with no writes seen').toBe(0);
});

When('I enter an out-of-range TFL number with valid amounts', async ({ schedule6Page, world }) => {
  await schedule6Page.enterTflNumber(INVALID_TFL.number);
  // Volume and cost are VALID on purpose: the only reason the submit can fail is the TFL number, so a
  // rejection cannot be credited to some other field.
  await schedule6Page.enterAmounts(S05_RECORD.volumeInput, S05_RECORD.costInput);
  // The comment is entered whenever the scenario registered one, which makes the REJECT arm
  // self-cleaning too. It should store nothing — that is the whole assertion — but if the app ever
  // regressed and stored it anyway, a comment-less row would be invisible to the comment-keyed
  // cleanup and would strand the SHARED validate-only anchor, turning one real failure into every
  // later validation slice failing for the wrong reason.
  if (world.sch6RecordComment) {
    await schedule6Page.enterComments(world.sch6RecordComment);
  }
});

Then('the rejection came from the server, on exactly one attempt', async ({ schedule6MutationSpy }) => {
  // ONE request, not ZERO — and this expectation was corrected after the first run asserted zero and
  // failed. The client CANNOT pre-empt this particular rejection: "valid" means "resolves to an RMG"
  // and the RMG table (RoadGroupLookup) is server-side, so the client gate only catches BLANK and
  // OVER-WIDE TFL entries (validation.ts:170-176) and a two-character invalid code passes it
  // untouched. So the correct behaviour is exactly one POST that the server answers 400. The app was
  // right and the assertion was wrong.
  //
  // WHAT THIS STILL BUYS, now that it is not a no-write check: it pins the rejection to the SERVER
  // round-trip rather than the client, which is the behavioural difference from legacy this slice
  // exists to record (defects.md VER-3); and `toBe(1)` rather than `toBeGreaterThan(0)` catches a
  // silent retry or a double-submit, which would store the record twice on a later valid attempt.
  // /check-status is excluded by contract, so a Check Status in the same scenario cannot inflate it.
  expect(
    schedule6MutationSpy.mutations,
    'a rejected TFL entry should cost exactly one server round-trip — 0 would mean the client '
      + 'pre-empted it (it cannot), more than 1 a retry or double-submit',
  ).toBe(1);
});

Then('no road record was stored on that anchor', async ({ request, world }) => {
  // THE NEGATIVE THAT ACTUALLY MATTERS, proved server-side. "The value is not accepted" is a claim
  // about the DATABASE, and an error banner does not establish it — the request was in fact sent, so
  // the only way to know it changed nothing is to look. This is the load-bearing half of the pair;
  // the spy above says HOW the rejection happened, this says that it held.
  const doc = await readSchedule6(request, world.scheduleKey!);
  expect(
    doc.roadRecords.map((r) => r.recordId),
    'the rejected entry must not have been stored',
  ).toEqual([]);
});

When('I correct the TFL number', async ({ schedule6Page }) => {
  await schedule6Page.enterTflNumber(VALID_TFL.number);
});

// ---- S09 / S10 / S11 — the Check Status "Value Required" outcomes ----------------------------------

Given(
  'a road maintenance record with no cost commented {string} already exists on that anchor',
  async ({ request, world, schedule6Cleanup }, comments: string) => {
    world.sch6RecordComment = comments;
    schedule6Cleanup.push({ key: world.scheduleKey!, comments });

    // STORABLE, verified by probe: a missing cost is a Check Status finding, never a save failure.
    const created = await addRecord(request, world.scheduleKey!, {
      areaType: S09_RECORD.areaTypeCode,
      supplyBlock: S09_RECORD.supplyBlockCode,
      volume: S09_RECORD.volume,
      comments,
    });
    expect(created.cost ?? null, 'the seeded record must genuinely have NO cost stored').toBeNull();
    world.sch6RecordId = created.recordId;
  },
);

Given(
  'a road maintenance record with no supply block commented {string} already exists on that anchor',
  async ({ request, world, schedule6Cleanup }, comments: string) => {
    world.sch6RecordComment = comments;
    schedule6Cleanup.push({ key: world.scheduleKey!, comments });

    // Also STORABLE: only the Supply Block's WIDTH is enforced on write.
    const created = await addRecord(request, world.scheduleKey!, {
      areaType: S11_RECORD.areaTypeCode,
      volume: S11_RECORD.volume,
      cost: S11_RECORD.cost,
      comments,
    });
    expect(
      created.supplyBlock ?? null,
      'the seeded record must genuinely have NO supply block stored',
    ).toBeNull();
    world.sch6RecordId = created.recordId;
  },
);

Given(
  'a valid TFL road maintenance record commented {string} already exists on that anchor',
  async ({ request, world, schedule6Cleanup }, comments: string) => {
    world.sch6RecordComment = comments;
    schedule6Cleanup.push({ key: world.scheduleKey!, comments });

    // A VALID one, because the invalid state is NOT storable — the add endpoint answers 400 FLD-002 on
    // a TFL record with no number. S10 blanks the field on screen instead; see the feature header.
    const created = await addRecord(request, world.scheduleKey!, {
      areaType: TFL_OPTION,
      tflNumber: S10_RECORD.tflNumber,
      volume: S10_RECORD.volume,
      cost: S10_RECORD.cost,
      comments,
    });
    expect(created.tflNumber, 'the seeded TFL record must carry its number').toBe(
      S10_RECORD.tflNumber,
    );
    world.sch6RecordId = created.recordId;
  },
);

When("I clear the record's TFL number on screen", async ({ schedule6Page, world }) => {
  const recordId = world.sch6RecordId!;
  await schedule6Page.expandRecord(1, recordId);
  // NOT SAVED, deliberately. This is the whole mechanism of S10: the record on screen is now in a
  // state the write path would refuse, and Check Status still describes it because it evaluates the
  // payload the screen sends.
  await schedule6Page.setRowTflNumber(recordId, '');
});

Then('Check Status reports {string}', async ({ schedule6Page }, line: string) => {
  await expect(schedule6Page.notification(line)).toBeVisible();
  // The severity word, not colour alone (NFR1) — an ISSUES verdict renders under "Action required".
  await expect(schedule6Page.notification('Action required')).toBeVisible();
});

Then('Check Status does not report the schedule as met', async ({ schedule6Page }) => {
  // SOUND ONLY BECAUSE runCheckStatus WAITED for the verdict. An absence asserted straight after the
  // click could pass against a DOM that had not re-rendered — the vacuous-pass class that sch3's
  // DIV-6 mirror arm shipped with. See pages/common/checkStatus.ts.
  await expect(schedule6Page.notification(REQUIREMENTS_MET)).toHaveCount(0);
});

// ---- S06 / S07 / S08 — the three context guards ----------------------------------------------------

Given('the Schedule 6 guard anchor {string}', async ({ request, world }, name: string) => {
  const guard = GUARDS[name];
  expect(guard, `unknown Schedule 6 guard "${name}" — add it to GUARDS in the fixture`).toBeTruthy();

  // PROVED AT THE API BEFORE THE BROWSER IS DRIVEN. A guard's whole fixture is a failure mode, so if
  // the data drifted (a reopened mill, or a report-status row that should not exist) the GET would
  // answer 200 and the scenario would fail on a MISSING error banner — which reads as a UI defect.
  // Checking here means the failure names the data instead.
  const res = await request.get(scheduleUrl(guard.anchor.key.millId, guard.anchor.key.year));
  expect(
    res.status(),
    `guard "${name}" (${guard.anchor.key.millId}/${guard.anchor.key.year}) must answer HTTP `
      + `${guard.expectHttp}`,
  ).toBe(guard.expectHttp);

  world.scheduleKey = guard.anchor.key;
  world.millOption = millOptionText(guard.anchor.mill);
});

When('I open Schedule 6 with no working context', async ({ schedule6Page }) => {
  await schedule6Page.openWithNoContext();
});

When('I open Schedule 6 expecting a guard message', async ({ schedule6Page }) => {
  await schedule6Page.openExpectingGuard();
});

Then('the Schedule 6 mill and reporting year guard is shown', async ({ schedule6Page }) => {
  // NOTE THE ABSENT TRAILING SPACE. The legacy Gherkin quotes this message WITH one, because that is
  // the SERVER's ERR-001 literal — but this guard never reaches the server (the page suppresses the
  // request), so what renders is the CLIENT literal, which carries no trailing space by sibling
  // convention. Recorded in defects.md VER-4.
  await expect(schedule6Page.notification(GUARD_MESSAGES.millYearNotSelected)).toBeVisible();
  // The TITLE too: severity is carried by a word, never by colour alone (WCAG 2.1 AA).
  await expect(schedule6Page.notification(GUARD_MESSAGES.millYearNotSelectedTitle)).toBeVisible();
});

Then('the Schedule 6 page shows the closed-mill guard', async ({ schedule6Page }) => {
  await expect(schedule6Page.notification(GUARD_MESSAGES.millNotActive)).toBeVisible();
  await expect(schedule6Page.notification(GUARD_MESSAGES.millNotActiveTitle)).toBeVisible();
  // AND NOT the generic load-failure framing. This is the assertion that keeps the two apart: a mill
  // closed for the reporting year is a CONTEXT the reporter changes on the Home page, not the app
  // failing to load, so ScheduleLoadState titles it separately. The DETAIL alone reads identically
  // either way, so only the absence of this title distinguishes them.
  await expect(schedule6Page.notification(GUARD_MESSAGES.loadFailedTitle)).toHaveCount(0);
});

Then('the Schedule 6 page is blocked as a load failure', async ({ schedule6Page }) => {
  await expect(schedule6Page.notification(GUARD_MESSAGES.scheduleNotFound)).toBeVisible();
  // A missing record IS a load failure and keeps the generic title — the mirror of the closed-mill
  // assertion above, so the pair proves the framings are not swapped.
  await expect(schedule6Page.notification(GUARD_MESSAGES.loadFailedTitle)).toBeVisible();
});

Then('the Schedule 6 data-entry surface is suppressed', async ({ schedule6Page }) => {
  await schedule6Page.expectDataEntrySuppressed();
});

// ---- S12-S16 — the Add-panel field validations -----------------------------------------------------
//
// All five slices share one shape: put ONE bad value in the Add panel, leave everything else valid,
// submit, and assert the verbatim message plus the two halves of "the value is not accepted" — no
// request was sent (the spy) and nothing was stored (the read-back). The spy expects ZERO here, unlike
// S05's exactly-one, because `handleAdd` returns before building the POST when `validateRoadRecord`
// reports anything (index.tsx:682-686). See the fixture's S12-S16 header.

When('I enter valid amounts with no area type selected', async ({ schedule6Page, world }) => {
  // The area-type combo is deliberately NOT touched — that absence IS the slice. Volume and cost are
  // valid so the only possible ground for refusal is the missing classification.
  await schedule6Page.enterAmounts(VALIDATION_AMOUNTS.volumeInput, VALIDATION_AMOUNTS.costInput);
  if (world.sch6RecordComment) {
    await schedule6Page.enterComments(world.sch6RecordComment);
  }
});

Then('no area type is selected in the Add panel', async ({ schedule6Page }) => {
  // Asserted rather than assumed: if the combo ever defaulted to its first option (the synthetic "TFL"
  // sentinel sits at the top of the list), the panel would be VALID on open and this slice would be
  // testing nothing while still passing its error assertion for a different reason.
  await expect(schedule6Page.addAreaType).toHaveValue('');
});

When(
  'I enter the volume {string} with a valid cost',
  async ({ schedule6Page, world }, volume: string) => {
    // Classification first and complete, so the ONLY invalid field is the volume.
    await schedule6Page.selectAreaType(S12_RECORD.correction.areaTypeOption);
    await schedule6Page.selectSupplyBlock(S12_RECORD.correction.supplyBlockOption);
    await schedule6Page.enterVolume(volume);
    await schedule6Page.enterCost(VALIDATION_AMOUNTS.costInput);
    if (world.sch6RecordComment) {
      await schedule6Page.enterComments(world.sch6RecordComment);
    }
  },
);

When(
  'I enter the cost {string} with a valid volume',
  async ({ schedule6Page, world }, cost: string) => {
    await schedule6Page.selectAreaType(S12_RECORD.correction.areaTypeOption);
    await schedule6Page.selectSupplyBlock(S12_RECORD.correction.supplyBlockOption);
    await schedule6Page.enterVolume(VALIDATION_AMOUNTS.volumeInput);
    await schedule6Page.enterCost(cost);
    if (world.sch6RecordComment) {
      await schedule6Page.enterComments(world.sch6RecordComment);
    }
  },
);

Then(
  'the Add panel volume field still reads {string}',
  async ({ schedule6Page }, expected: string) => {
    // THE TYPO MUST SURVIVE THE BLUR. `groupInput` returns text it cannot parse UNCHANGED, on purpose
    // — "so a typo stays on screen for the user to correct" (utils/number.ts:59-64). A field that
    // blanked or zeroed a bad entry would hide the reporter's own mistake from them, and an in-range
    // value still re-groups (10000000 -> "10,000,000"), so this asserts the mask ran either way.
    await expect(schedule6Page.addVolume).toHaveValue(expected);
  },
);

Then('the Add panel cost field still reads {string}', async ({ schedule6Page }, expected: string) => {
  // Same contract as the volume above, through `groupFixedInput(_, 0)` instead of `groupInput`.
  await expect(schedule6Page.addCost).toHaveValue(expected);
});

Then('the Add panel shows no computed cost per volume', async ({ schedule6Page }) => {
  // THE DERIVED CELL MUST NOT MOVE. `commitRate` advances the $ / m³ baseline only from an entry that
  // passes the gate (index.tsx:533-542, ruled 2026-08-21): committing an unparseable or out-of-range
  // value would display a rate no Save could ever persist. On a freshly opened panel the baseline is
  // still EMPTY_RATE_INPUTS, so "held at its last valid figure" and "still blank" are the same claim —
  // and blank is the empty string, because ratioMask(null) returns '' (index.tsx:86-95).
  await expect(schedule6Page.addCostPerVolume).toHaveText(NO_COMPUTED_RATE);
});

Then('no Schedule 6 write was attempted', async ({ schedule6MutationSpy }) => {
  // ZERO, and the contrast with S05 is the point. These five rejections are decided entirely on the
  // client, so `handleAdd` returns before the POST is built — no round-trip is spent. S05's invalid TFL
  // number costs exactly ONE because only the server knows the RMG table. Asserting zero here is the
  // no-write proof the suite's guidance asks for; it does NOT replace the read-back below, since a spy
  // proves no request was sent and only a read proves nothing was stored.
  expect(
    schedule6MutationSpy.mutations,
    'a client-side validation failure must cost no mutating request at all — `handleAdd` returns '
      + 'before building the POST when validateRoadRecord reports anything',
  ).toBe(0);
});

Then(
  'the error {string} is still shown until the next submit',
  async ({ page }, message: string) => {
    // A REAL, DELIBERATE ASSERTION ABOUT STALENESS — not a weakened version of "the error cleared".
    // Correcting the field does NOT clear the message: `setAddField` only updates the form
    // (index.tsx:610-611) and `addErrors` is rewritten just twice, in `handleAdd` and in
    // `resetTransient` (index.tsx:563, 684-687). So the corrected value and the old error are on
    // screen together until Add Report is pressed again.
    //
    // NOT a divergence, and the distinction is deliberate: the legacy Gherkin never asserts that the
    // error clears — its correction arms end at "the field accepts the value" / "cal recomputes", both
    // true here. Legacy DID clear it on blur, but that follows from per-field ajax validation, and
    // submit-time validation is the design already accepted as not-a-defect in defects.md VER-3: with
    // no moment at which the app re-judges a field, nothing clears the message. Recorded as VER-5 with
    // the UX consequence flagged for BA/QA; clearing on edit would be a new requirement, not a fix.
    //
    // Asserted rather than ignored so the suite states what the app actually does today: if it is later
    // changed to clear the error on edit, THIS step fails and puts the decision in front of a human.
    await expect(page.getByText(message).first()).toBeVisible();
  },
);

When('I correct the volume', async ({ schedule6Page }) => {
  await schedule6Page.enterVolume(VALIDATION_AMOUNTS.volumeInput);
});

When('I correct the cost', async ({ schedule6Page }) => {
  await schedule6Page.enterCost(VALIDATION_AMOUNTS.costInput);
});

When('I select the area type and supply block', async ({ schedule6Page }) => {
  // ORDER IS CONTRACTUAL, as in S01: the Supply Block list is filtered to codes starting with the
  // chosen TSA (utils/codes.ts supplyBlocksFor), so the block is not offered until the TSA is set.
  await schedule6Page.selectAreaType(S12_RECORD.correction.areaTypeOption);
  await schedule6Page.selectSupplyBlock(S12_RECORD.correction.supplyBlockOption);
});

Then('the corrected road record is persisted', async ({ request, world }) => {
  const key = world.scheduleKey!;
  const comment = world.sch6RecordComment!;

  await expect
    .poll(
      async () =>
        (await readSchedule6(request, key)).roadRecords.filter((r) => r.comments === comment).length,
      { message: `the corrected record was not stored on ${key.millId}/${key.year}` },
    )
    .toBe(1);

  const doc = await readSchedule6(request, key);
  const record = doc.roadRecords.find((r) => r.comments === comment)!;

  expect(record.areaType, 'the SELECTED area type is what gets stored').toBe(
    S12_RECORD.correction.areaTypeCode,
  );
  expect(record.supplyBlock, 'stored supply block').toBe(S12_RECORD.correction.supplyBlockCode);
  expect(record.rmg, 'RMG derived server-side from the supply block').toBe(
    S12_RECORD.correction.rmg,
  );
  expect(record.volume, 'stored volume').toBe(Number(VALIDATION_AMOUNTS.volumeInput));
  expect(record.cost, 'stored cost').toBe(Number(VALIDATION_AMOUNTS.costInput));
  expect(record.costPerVolume, 'server-derived cost per volume').toBe(
    Number(VALIDATION_AMOUNTS.costPerVolumeDisplay),
  );
  // Exactly one row: the refused attempt must not have left a partial record for the successful retry
  // to sit beside. Cheap here (the refusal sent no request at all) but it is the assertion that would
  // catch a regression which started posting on a failed gate.
  expect(doc.roadRecords.length, 'the refused attempt must not have stored anything').toBe(1);

  world.sch6RecordId = record.recordId;
});

// ---- S17 — the schedule renders read-only when the report is not in Draft --------------------------

Given(
  'the Schedule 6 report for that mill and year is not in Draft',
  async ({ request, world }) => {
    const anchor = READ_ONLY_ANCHOR;
    const doc = await readSchedule6(request, anchor.key);

    // NOT DRAFT, and `editable` FALSE — two separate claims, both asserted. The status code alone is
    // not enough: editability is role x status (ScheduleEditability), so a Submitted document is
    // read-only for the suite's ILCR_SUBMITTER but EDITABLE for an ADMIN. If the suite's identity were
    // ever changed, this Given fails here and names the reason, instead of the scenario going on to
    // assert that controls are disabled on a page that is legitimately editable.
    expect(
      doc.trackStatus,
      `the read-only anchor (${anchor.key.millId}/${anchor.key.year}) must not be a Draft 1-10 track`,
    ).not.toBe('D');
    expect(
      doc.editable,
      'the read-only anchor must be served non-editable for this suite\'s identity (ILCR_SUBMITTER '
        + 'edits at Draft only) — a true here means the seeded status drifted or the role changed',
    ).toBe(false);

    // The seeded records, resolved by COMMENT rather than by id or position: the local patch draws
    // recordIds from a sequence while the CI seed pins 3100/3101, so the ids differ by environment —
    // and every row locator is built from `row-<recordId>-*`. Matching on the comment is what makes
    // this scenario portable. The ORDINAL comes from the served order, not from the fixture, so the
    // accordion titles cannot be assumed either.
    const resolved = S17_RECORDS.map((expected) => {
      const index = doc.roadRecords.findIndex((r) => (r.comments ?? '') === expected.comments);
      expect(
        index,
        `no seeded record commented "${expected.comments}" on the read-only anchor — apply `
          + 'real-test-data-patches/sch6/view-mode-road-records.sql',
      ).toBeGreaterThanOrEqual(0);
      return { expected, record: doc.roadRecords[index], ordinal: index + 1 };
    });

    expect(
      doc.roadRecords.length,
      'the read-only anchor must hold exactly the seeded records — an extra one would make the totals '
        + 'assertions wrong while the page is right',
    ).toBe(S17_RECORDS.length);

    world.sch6ReadOnlyRecords = resolved;
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
  },
);

Then('every Schedule 6 entry control is disabled', async ({ schedule6Page }) => {
  // Covers the action bar (BOTH Save and BOTH Check Status instances — legacy's saveButton0/1 and
  // checkStatusButton0/1, which the Gherkin names individually), the General Comments field, and the
  // Add surface. The Add surface is re-grounded: legacy's six inline Add-form fields cannot be
  // asserted "disabled" here because the React panel is only rendered while its toggle is open, and
  // the toggle is disabled — so the panel is absent and unreachable. See the page object's own note.
  await schedule6Page.expectEntryControlsLocked();
});

Then("every saved record's own fields are disabled", async ({ schedule6Page, world }) => {
  const records = world.sch6ReadOnlyRecords!;
  for (const { record, ordinal } of records) {
    // This is where the Gherkin's disabled-field claim genuinely lands: the row editor shares the same
    // `RoadRecordFields` component as the Add panel, and its fields ARE present and disabled.
    await schedule6Page.expectRowFieldsDisabled(ordinal, record.recordId);
    await expect(
      schedule6Page.rowDeleteButton(ordinal),
      `row ${ordinal}'s Delete must be disabled on a non-Draft schedule`,
    ).toBeDisabled();
  }
});

Then('the existing road records remain visible with their stored figures', async ({ schedule6Page, world }) => {
  const records = world.sch6ReadOnlyRecords!;
  for (const { expected, record, ordinal } of records) {
    // Expanded first (VER-2): Carbon renders every accordion panel's children into the DOM whichever
    // one is open, so reading a collapsed row asserts DOM state rather than what a reporter can see.
    await schedule6Page.expandRecord(ordinal, record.recordId);

    await expect(schedule6Page.rowVolume(record.recordId)).toHaveValue(expected.volumeDisplay);
    await expect(schedule6Page.rowCost(record.recordId)).toHaveValue(expected.costDisplay);
    await expect(schedule6Page.rowDerived(record.recordId, 'RMG')).toHaveText(expected.rmg);
    await expect(schedule6Page.rowDerived(record.recordId, '$ / m³')).toHaveText(
      expected.costPerVolumeDisplay,
    );
    // The per-record comment, which is a DIFFERENT field from the schedule-level one asserted below
    // (different column, different cap — deviation E). Asserting both is what keeps the two apart.
    await expect(schedule6Page.rowComments(record.recordId)).toHaveValue(expected.comments);
  }
});

Then('the running totals remain visible', async ({ schedule6Page }) => {
  // 4.00 IS THE POINT. It is the total cost over the total volume, and it equals NEITHER record's own
  // rate (3.00 and 4.50) — so a page that echoed a single row's figures into the totals strip fails
  // here. A one-record fixture could not have caught that, which is why S17 seeds two.
  await expect(schedule6Page.total('Volume m³')).toHaveText(S17_TOTALS.volume);
  await expect(schedule6Page.total('Cost $')).toHaveText(S17_TOTALS.cost);
  await expect(schedule6Page.total('$ / m³')).toHaveText(S17_TOTALS.costPerVolume);
});

Then('the general comment remains visible', async ({ schedule6Page }) => {
  // Visible AND populated, not merely present: a disabled-but-blanked textarea would satisfy
  // "disabled" while losing the reporter's text, which is the failure mode that matters on a locked
  // page. The disabled assertion itself lives in `expectEntryControlsLocked`.
  await expect(schedule6Page.generalComments).toBeVisible();
  await expect(schedule6Page.generalComments).toHaveValue(S17_GENERAL_COMMENT);
});

Then('the corrected TFL record is persisted', async ({ request, world }) => {
  const key = world.scheduleKey!;
  const comment = world.sch6RecordComment!;

  await expect
    .poll(
      async () =>
        (await readSchedule6(request, key)).roadRecords.filter((r) => r.comments === comment).length,
      { message: `the corrected TFL record was not stored on ${key.millId}/${key.year}` },
    )
    .toBe(1);

  const doc = await readSchedule6(request, key);
  const record = doc.roadRecords.find((r) => r.comments === comment)!;

  expect(record.tflNumber, 'the CORRECTED TFL number is what gets stored').toBe(VALID_TFL.number);
  expect(record.rmg, 'the corrected TFL resolves to an RMG').toBe(VALID_TFL.rmg);
  expect(record.costPerVolume, 'server-derived cost per volume').toBe(
    Number(S05_RECORD.costPerVolumeDisplay),
  );
  // Exactly one row: the rejected attempt must not have left a partial record behind that the
  // successful retry then sat beside.
  expect(doc.roadRecords.length, 'the rejected attempt must not have stored anything').toBe(1);

  world.sch6RecordId = record.recordId;
});
