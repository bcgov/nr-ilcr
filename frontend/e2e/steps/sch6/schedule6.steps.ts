import { Given, When, Then, expect } from '../fixtures';
import {
  ADD_ANCHOR,
  type Sch6Anchor,
  S01_RECORD,
  S01_TOTALS,
  millOptionText,
} from '../../fixtures/sch6/schedule6-test-data';
import { readSchedule6 } from './schedule6Api';

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
  await expect(schedule6Page.total('Volume')).toHaveText(S01_TOTALS.volume);
  await expect(schedule6Page.total('Cost')).toHaveText(S01_TOTALS.cost);

  const doc = await readSchedule6(request, world.scheduleKey!);
  expect(doc.totalVolume, 'server total volume').toBe(Number(S01_RECORD.volumeInput));
  expect(doc.totalCost, 'server total cost').toBe(Number(S01_RECORD.costInput));
  expect(doc.totalCostPerVolume, 'server total cost per volume').toBe(
    Number(S01_TOTALS.costPerVolume),
  );
});

// DOMAIN-SCOPED NAME, deliberately: sch11 already defines a bare "I run Check Status"
// (steps/sch11/schedule11.steps.ts), and a second definition is the duplicate-step error bddgen
// rightly rejects. Every schedule has this button, so the unqualified phrasing cannot belong to two
// domains at once — the suite's existing convention is to qualify it ("I run Schedule 5 Check Status",
// "I run Check Status on Schedule 3"). Promoting one shared step to steps/common/ would need a common
// schedule-page abstraction these page objects do not have today.
When('I run Schedule 6 Check Status', async ({ schedule6Page }) => {
  await schedule6Page.checkStatusButton.click();
});
