import { Given, When, Then, expect } from '../fixtures';
import { settleBeforeReadingSpy } from '../../pages/common/settle';
import {
  ANCHORS,
  CROWN_APPLIED_KEY,
  CROWN_PUSH_VOLUME,
  HAPPY_PATH_COMMENTS,
  HAPPY_PATH_CROWN_TIMBER_VOLUME,
  HAPPY_PATH_DERIVED,
  HAPPY_PATH_LINES,
  HAPPY_PATH_POP_TIMBER_VOLUME,
  HAPPY_PATH_SCALING_POP,
  MSG_NOT_SAVED,
  MSG_SAVE_BEFORE_SUB_PAGE,
  MSG_SAVE_BEFORE_UNACCEPTABLE,
  RENDER_STATE_ANCHORS,
  ROUTE_OTHER_ACCEPTABLE,
  ROUTE_SCHEDULE_3,
  ROUTE_UNACCEPTABLE,
  SEEDED_BASE_LINES,
  SEEDED_CROWN_TIMBER_VOLUME,
  SEEDED_POP_TIMBER_VOLUME,
  SEEDED_WAGES_VIOLATION,
  lineByCode,
  lineByLabel,
  millOptionText,
  scheduleUrl,
} from '../../fixtures/sch3/schedule3-test-data';
import {
  blockValues,
  getSchedule1,
  getSchedule3,
  isMutatingAnchor,
  lineValues,
  saveSchedule3,
  schedule1IsSaved,
  schedule3IsSaved,
  schedule1PushedVolumes,
  schedule1Status,
  schedule1Volumes,
  schedule3Status,
  totalValues,
} from './schedule3Api';

/**
 * UC-SCH3-001 — Schedule 3 main-page steps. Sub-page steps live in `subPage.steps.ts` and the Check
 * Status steps in `checkStatus.steps.ts`, so no one file owns more than one concern.
 *
 * No DOM selector appears below: every interaction goes through `schedule3Page`. Cross-domain steps
 * (the Home working context, the message/error/warning assertions, the axe sweep) are reused from
 * `steps/common/` rather than restated here.
 */

// ---------------------------------------------------------------------------------------------------
// Preconditions
// ---------------------------------------------------------------------------------------------------

Given('the Schedule 3 anchor {string}', async ({ request, world, schedule3Cleanup }, key) => {
  const anchor = ANCHORS[key];
  expect(anchor, `unknown Schedule 3 anchor "${key}" — see fixtures/sch3/schedule3-test-data.ts`).toBeTruthy();
  world.scheduleKey = anchor.key;
  world.millOption = millOptionText(anchor.mill);
  world.sch3AnchorKey = key;

  // Fail as a RE-GROUND rather than as a confusing UI timeout if the anchor drifted.
  const doc = await getSchedule3(request, anchor.key);
  expect(
    [doc.trackStatus, doc.editable],
    `Schedule 3 anchor "${key}" (${anchor.key.millId}/${anchor.key.year}) is no longer an editable Draft — ${anchor.purpose}`,
  ).toEqual(['D', true]);
  world.sch3RevisionAtOpen = doc.revisionCount ?? null;

  // Register the teardown the moment the scenario is known to write, so a mid-scenario failure still
  // restores the anchor. The read-only anchors are deliberately NOT registered: nothing writes to them,
  // and a needless restore PUT would itself be a write.
  if (isMutatingAnchor(anchor.key)) {
    schedule3Cleanup.push({ key: anchor.key, withSchedule1: key === CROWN_APPLIED_KEY });
  }
});

Given(
  'the Schedule 3 render-state anchor {string}',
  async ({ request, world }, key) => {
    const anchor = RENDER_STATE_ANCHORS[key];
    expect(anchor, `unknown Schedule 3 render-state anchor "${key}"`).toBeTruthy();
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    world.sch3AnchorKey = key;

    // Pin the state the render depends on at the API level first: a drifted anchor then fails HERE with
    // a re-ground message instead of as an unexplained missing banner later.
    const { status, detail } = await schedule3Status(request, anchor.key);
    expect(
      status,
      `Schedule 3 render-state anchor "${key}" (${anchor.key.millId}/${anchor.key.year}) answered HTTP ${status}, expected ${anchor.expectHttp}`,
    ).toBe(anchor.expectHttp);
    if (anchor.detail !== undefined) {
      expect(detail, `anchor "${key}" 's guard detail changed`).toBe(anchor.detail);
    }
    if (anchor.track !== undefined) {
      const doc = await getSchedule3(request, anchor.key);
      expect([doc.trackStatus, doc.editable], `anchor "${key}" is no longer a read-only ${anchor.track}`).toEqual([
        anchor.track,
        false,
      ]);
    }
  },
);

Given('no mill and reporting year are selected', async ({ schedule3Page }) => {
  await schedule3Page.openWithNoContext();
});

Given(
  'Schedule 3 has been saved with every fixed line and both timber volumes',
  async ({ request, world }) => {
    // Seeded through the app's own PUT, not typed: the scenario under test is the Check Status outcome,
    // not the entry (which S01 covers in full).
    await saveSchedule3(request, world.scheduleKey!, {
      lines: HAPPY_PATH_LINES,
      popTimberVolume: HAPPY_PATH_POP_TIMBER_VOLUME,
      crownTimberVolume: HAPPY_PATH_CROWN_TIMBER_VOLUME,
    });
  },
);

// ---------------------------------------------------------------------------------------------------
// GAP-2 — the optimistic lock (AR11). One out-of-band API save is enough: the page copies
// `revisionCount` into React state when it loads, so a save from "another session" moves the stored
// token past the one the browser is holding. Deliberately uses the Wages/Salaries HARVEST cell and never
// a timber volume, so the BR-09 crown push cannot fire and Schedule 1 is never touched.
// ---------------------------------------------------------------------------------------------------

const WAGES_LINE = lineByLabel('Wages/Salaries, incl Benefits');

Given(
  'the Wages line has a saved Harvest of {string}',
  async ({ request, world }, value) => {
    await saveSchedule3(request, world.scheduleKey!, {
      lines: [{ code: WAGES_LINE.code, harvest: Number(value), pop: null }],
    });
  },
);

When(
  'another session changes the saved Wages line Harvest to {string}',
  async ({ request, world }, value) => {
    // Re-reads the CURRENT token before writing, which is exactly what a second session would do — so
    // this save succeeds and the browser's captured token goes stale.
    const saved = await saveSchedule3(request, world.scheduleKey!, {
      lines: [{ code: WAGES_LINE.code, harvest: Number(value), pop: null }],
    });
    expect(
      saved.revisionCount,
      "the other session's save did not move the revision token, so nothing would be stale",
    ).not.toBe(world.sch3RevisionAtOpen);
  },
);

Then('the stored Wages line Harvest is {string}', async ({ request, world }, expected) => {
  const doc = await getSchedule3(request, world.scheduleKey!);
  const [harvest] = lineValues(doc, WAGES_LINE.code);
  expect(
    harvest,
    'the refused save was written anyway — the other session\'s value was overwritten (AR11)',
  ).toBe(Number(expected));
});

// ---------------------------------------------------------------------------------------------------
// S18/S19 — the save-first gate on the two cost sub-pages, restored by the defect #296 fix. Reachable
// only because an unsaved Schedule 3 now OPENS (it used to 404), so these steps are new coverage rather
// than a re-grounding.
// ---------------------------------------------------------------------------------------------------

When(
  'I try to open the Schedule 3 {string} sub-page without saving',
  async ({ schedule3Page }, which) => {
    expect(
      ['Other Costs', 'Included Unacceptable Costs'],
      `unknown Schedule 3 sub-page "${which}"`,
    ).toContain(which);
    await schedule3Page.openSubPageBlocked(
      which === 'Other Costs' ? 'other-acceptable' : 'unacceptable',
    );
  },
);

Then('Schedule 3 tells me to save first', async ({ schedule3Page }) => {
  await expect(
    schedule3Page.saveRequiredDialog.getByText(MSG_SAVE_BEFORE_SUB_PAGE, { exact: true }),
    'the save-first gate no longer carries legacy schedule3.xhtml:267 verbatim',
  ).toBeVisible();
});

// DIV-7. Legacy words the two gates DIFFERENTLY — `schedule3.xhtml:293` for the Included Unacceptable
// link, `:267` for Other Costs. The app has one string for both, so this asserts the legacy guarantee and
// is RED until the second wording is restored. Deliberately a separate step: reusing the one above would
// have let S19 pass against the wrong message, which is exactly how this went unnoticed.
Then('Schedule 3 tells me to save first before Unacceptable costs', async ({ schedule3Page }) => {
  await expect(
    schedule3Page.saveRequiredDialog.getByText(MSG_SAVE_BEFORE_UNACCEPTABLE, { exact: true }),
    'the Included Unacceptable save-first gate does not carry legacy schedule3.xhtml:293 verbatim',
  ).toBeVisible();
});

Then('I am still on Schedule 3', async ({ page, schedule3Page }) => {
  await expect(page).toHaveURL(new RegExp(`${ROUTE_SCHEDULE_3}$`));
  await expect(schedule3Page.costTable).toBeVisible();
});

Given('a Crown Timber volume has already been saved', async ({ request, world }) => {
  // BR-09 fires when the entered Crown volume DIFFERS from the persisted one, so the scenario needs a
  // starting value to change away from.
  await saveSchedule3(request, world.scheduleKey!, {
    lines: HAPPY_PATH_LINES,
    popTimberVolume: HAPPY_PATH_POP_TIMBER_VOLUME,
    crownTimberVolume: HAPPY_PATH_CROWN_TIMBER_VOLUME,
  });
});

Given('Schedule 1 has been opened for the same mill and year', async ({ request, world }) => {
  // "Opened" = a category-1 summary exists (legacy isScheduleOpen()). Asserted on the saved-ness of the
  // document, NOT on a 404, because since defect #296 an unsaved Schedule 1 answers 200 empty+editable.
  expect(
    await schedule1IsSaved(request, world.scheduleKey!),
    `the crown-applied anchor ${world.scheduleKey!.millId}/${world.scheduleKey!.year} has no SAVED Schedule 1 — ` +
      're-run scripts/apply-patches.sh (real-test-data-patches/sch3/draft-anchors.sql adds it)',
  ).toBe(true);
});

Given('Schedule 1 has never been opened for the same mill and year', async ({ request, world }) => {
  // WRN-002 needs NO category-1 summary. Before defect #296 that showed up as a 404; now the GET serves
  // a 200 empty editable document either way, so the token's absence is the signal.
  expect(
    await schedule1IsSaved(request, world.scheduleKey!),
    `the crown-not-opened anchor ${world.scheduleKey!.millId}/${world.scheduleKey!.year} unexpectedly HAS a SAVED ` +
      'Schedule 1 (WRN-002 needs it absent)',
  ).toBe(false);
});

Given('the Schedule 3 save will fail', async ({ page, world }) => {
  // Force ERR-001 from the server side rather than corrupting data to provoke it: the PUT is answered
  // with the app's own 500 problem+json shape, so the page takes exactly its real failure path.
  const key = world.scheduleKey!;
  await page.route(`**${scheduleUrl(key.millId, key.year)}`, async (route) => {
    if (route.request().method() !== 'PUT') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'Internal Server Error',
        status: 500,
        detail: MSG_NOT_SAVED,
        instance: '/api/v1/schedule3',
      }),
    });
  });
});

Given('the Schedule 3 save is no longer failing', async ({ page, world }) => {
  const key = world.scheduleKey!;
  await page.unroute(`**${scheduleUrl(key.millId, key.year)}`);
});

// ---------------------------------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------------------------------

When('I open Schedule 3', async ({ schedule3Page }) => {
  await schedule3Page.gotoViaNav();
});

When('I open Schedule 3 expecting a guard', async ({ schedule3Page }) => {
  await schedule3Page.gotoViaNavExpectingGuard();
});

When('I reload the Schedule 3 page', async ({ schedule3Page }) => {
  await schedule3Page.reload();
});

// Prefixed "Schedule 3": Schedule 1 has its own Other Costs sub-page and already owns the unprefixed
// phrasing (steps/sch1/otherCosts.steps.ts), which playwright-bdd rightly rejects as a duplicate.
/**
 * Landing on the route is NOT readiness: the sub-page shows a LoadingScreen until its GET resolves, so
 * the rows table does not exist yet. Without waiting for it, a following ACTION step (as opposed to an
 * assertion, which polls) reads an empty row list and fails as "row not found" — which is exactly what
 * the first run of the S04 scenario did on its SECOND visit to the sub-page.
 */
When('I open the Schedule 3 Other Costs sub-page', async ({ schedule3Page, schedule3SubPage, world }) => {
  await schedule3Page.openOtherAcceptable(ROUTE_OTHER_ACCEPTABLE);
  world.sch3SubPageTitle = 'Other Costs';
  await schedule3SubPage.expectLoaded('Other Costs');
});

When(
  'I open the Schedule 3 Other Costs sub-page read-only',
  async ({ schedule3Page, schedule3SubPage, world }) => {
    await schedule3Page.openOtherAcceptableReadOnly(ROUTE_OTHER_ACCEPTABLE);
    world.sch3SubPageTitle = 'Other Costs';
    await schedule3SubPage.expectLoaded('Other Costs');
  },
);

When(
  'I open the Schedule 3 Included Unacceptable Costs sub-page',
  async ({ schedule3Page, schedule3SubPage, world }) => {
    await schedule3Page.openUnacceptable(ROUTE_UNACCEPTABLE);
    world.sch3SubPageTitle = 'Included Unacceptable Costs';
    await schedule3SubPage.expectLoaded('Included Unacceptable Costs');
  },
);

// ---------------------------------------------------------------------------------------------------
// Entry
// ---------------------------------------------------------------------------------------------------

When('I enter every fixed admin cost line and both timber volumes', async ({ schedule3Page }) => {
  for (const entered of HAPPY_PATH_LINES) {
    const line = lineByCode(entered.code);
    await schedule3Page.enterHarvest(line.label, String(entered.harvest));
    if (entered.pop !== null) {
      await schedule3Page.enterPop(line.label, String(entered.pop));
    }
  }
  await schedule3Page.enterPopTimberVolume(String(HAPPY_PATH_POP_TIMBER_VOLUME));
  await schedule3Page.enterCrownTimberVolume(String(HAPPY_PATH_CROWN_TIMBER_VOLUME));
});

When('I enter the additional comments', async ({ schedule3Page }) => {
  await schedule3Page.enterComments(HAPPY_PATH_COMMENTS);
});

When(
  'I enter {string} into the {string} Harvest field',
  async ({ schedule3Page }, value, label) => {
    await schedule3Page.enterHarvest(label, value);
  },
);

/**
 * BR-12 / DIV-6: empty a stored Harvest amount on screen WITHOUT saving, so Check Status has a mandatory
 * field that is present in the database and absent on the page. Deliberately its own step rather than
 * `I enter "" into …` — an empty-string argument reads like a typo in a feature file, and this is the one
 * place where clearing rather than changing is the whole point of the scenario.
 */
When('I clear the {string} Harvest amount', async ({ schedule3Page }, label) => {
  await schedule3Page.enterHarvest(label, '');
});

When('I enter {string} into the {string} PO&P field', async ({ schedule3Page }, value, label) => {
  await schedule3Page.enterPop(label, value);
});

When('I enter {string} into the PO&P Timber volume', async ({ schedule3Page }, value) => {
  await schedule3Page.enterPopTimberVolume(value);
});

When('I enter {string} into the Crown Timber volume', async ({ schedule3Page }, value) => {
  await schedule3Page.enterCrownTimberVolume(value);
});

When('I change the Crown Timber volume', async ({ schedule3Page }) => {
  await schedule3Page.enterCrownTimberVolume(String(CROWN_PUSH_VOLUME));
});

When(
  // NOT "Override Harvest / Total PO&P": a bare '/' in a Cucumber expression opens an alternation, so
  // the step reads the label without the app's fraction-slash glyph.
  'I set the Override Harvest and Total PO&P selection to {string}',
  async ({ schedule3Page }, value) => {
    await schedule3Page.selectOverride(value === 'Y' ? 'Y' : 'N');
  },
);

/**
 * The Annual Rents Harvest field raises ALT-001 as a browser `alert` on blur (legacy `onchange`). The
 * handler is registered BEFORE the blur — Playwright auto-dismisses an unhandled dialog, so a handler
 * registered afterwards would find nothing — and the message is captured for the Then to assert.
 */
When(
  'I enter {string} into the Annual Rents Harvest field',
  async ({ page, schedule3Page, world }, value) => {
    const seen: string[] = [];
    world.sch3Alerts = seen;
    page.on('dialog', (dialog) => {
      seen.push(dialog.message());
      void dialog.dismiss();
    });
    await schedule3Page.enterHarvest('Annual Rents', value);
  },
);

// ---------------------------------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------------------------------

When('I save Schedule 3', async ({ schedule3Page }) => {
  await schedule3Page.save();
});

When('I save Schedule 3 from the bottom action bar', async ({ schedule3Page }) => {
  await schedule3Page.saveFromBottomBar();
});

When('I delete Schedule 3 and confirm the prompt', async ({ schedule3Page }) => {
  await schedule3Page.openDeleteConfirm();
  await schedule3Page.confirmDelete();
});

When('I open the delete confirmation', async ({ schedule3Page }) => {
  await schedule3Page.openDeleteConfirm();
});

When('I cancel the delete confirmation', async ({ schedule3Page }) => {
  await schedule3Page.cancelDelete();
});

Then('the delete confirmation asks {string}', async ({ schedule3Page }, body) => {
  // The legacy `confirmDeleteMsg` text, now the body of a Carbon danger Modal.
  await expect(schedule3Page.deleteConfirmDialog.getByText(body, { exact: true })).toBeVisible();
});

When('I note the Schedule 3 write count', async ({ schedule3MutationSpy, world }) => {
  world.sch3MutationsBefore = schedule3MutationSpy.mutations;
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the rendered page
// ---------------------------------------------------------------------------------------------------

Then('the alert {string} was shown', async ({ world }, message) => {
  expect(
    world.sch3Alerts ?? [],
    `no browser alert was raised (expected ALT-001 "${message}")`,
  ).toContain(message);
});

Then(
  'the {string} line shows Harvest {string}, PO&P {string} and Crown {string}',
  async ({ schedule3Page }, label, harvest, pop, crown) => {
    // Poll: on the editable render these cells are the derived mirror, which settles on blur; after a
    // save they are repainted from the response. A single-shot read could race either.
    await expect
      .poll(async () => schedule3Page.lineCells(label), {
        message: `the "${label}" line never showed ${harvest} / ${pop} / ${crown}`,
      })
      .toEqual({ harvest, pop, crown });
  },
);

Then(
  'the {string} row shows {string}, {string} and {string}',
  async ({ schedule3Page }, rowLabel, harvest, pop, crown) => {
    await expect
      .poll(async () => schedule3Page.totalCells(rowLabel), {
        message: `the "${rowLabel}" row never showed ${harvest} / ${pop} / ${crown}`,
      })
      .toEqual([harvest, pop, crown]);
  },
);

Then(
  'the {string} overhead row shows volume {string}, cost {string} and per unit {string}',
  async ({ schedule3Page }, rowLabel, volume, cost, perUnit) => {
    await expect
      .poll(async () => schedule3Page.overheadCells(rowLabel), {
        message: `the "${rowLabel}" overhead row never showed ${volume} / ${cost} / ${perUnit}`,
      })
      .toEqual([volume, cost, perUnit]);
  },
);

Then('the Schedule 3 page shows the derived figures for those amounts', async ({ schedule3Page }) => {
  const d = HAPPY_PATH_DERIVED;
  await expect
    .poll(async () => schedule3Page.totalCells('Subtotal (Actual Costs)'))
    .toEqual([String(d.subtotalActualCosts.harvest), String(d.subtotalActualCosts.pop), String(d.subtotalActualCosts.crown)]);
  await expect
    .poll(async () => schedule3Page.totalCells('Total Costs'))
    .toEqual([String(d.totalCosts.harvest), String(d.totalCosts.pop), String(d.totalCosts.crown)]);
  await expect
    .poll(async () => schedule3Page.overheadCells('Total Overhead'))
    .toEqual([String(d.totalOverhead.volume), String(d.totalOverhead.cost), '1.40']);
});

Then('the Schedule 3 amounts are pre-filled with what was saved', async ({ schedule3Page }) => {
  for (const entered of HAPPY_PATH_LINES) {
    const line = lineByCode(entered.code);
    expect(
      await schedule3Page.inputValue(schedule3Page.harvestInput(line.label)),
      `"${line.label}" Harvest was not pre-filled`,
    ).toBe(String(entered.harvest));
    if (entered.pop !== null) {
      expect(
        await schedule3Page.inputValue(schedule3Page.popInput(line.label)),
        `"${line.label}" PO&P was not pre-filled`,
      ).toBe(String(entered.pop));
    }
  }
  expect(await schedule3Page.inputValue(schedule3Page.popTimberVolumeInput)).toBe(
    String(HAPPY_PATH_POP_TIMBER_VOLUME),
  );
  expect(await schedule3Page.inputValue(schedule3Page.crownTimberVolumeInput)).toBe(
    String(HAPPY_PATH_CROWN_TIMBER_VOLUME),
  );
  expect(await schedule3Page.commentsInput.inputValue()).toBe(HAPPY_PATH_COMMENTS);
});

Then('the Schedule 3 amount fields are read-only', async ({ schedule3Page }) => {
  // Proven by counting what IS rendered: a read-only schedule renders every figure as text, so the
  // count of inputs inside the cost table is zero. Asserting "no input matched" would also pass if the
  // table had failed to render at all.
  expect(
    await schedule3Page.editableFieldCount(),
    'the read-only render still carries editable amount inputs',
  ).toBe(0);
  await expect(schedule3Page.commentsInput).toHaveCount(0);
});

Then('the Schedule 3 Delete action is not offered', async ({ schedule3Page }) => {
  // Post-delete state since defect #296: the schedule is UNSAVED but still editable, so Save and Check
  // Status stay available (an unsaved schedule is saveable and checkable — that IS the fix) while Delete
  // is gated on a persisted record, as legacy gated it on isScheduleOpen(). Before #296 all three were
  // disabled because the page stranded the reporter on a read-only blank.
  await expect(schedule3Page.deleteButton).toBeDisabled();
  await expect(schedule3Page.saveButton).toBeEnabled();
  await expect(schedule3Page.checkStatusButton).toBeEnabled();
});

Then('the Schedule 3 actions are disabled', async ({ schedule3Page }) => {
  await expect(schedule3Page.saveButton).toBeDisabled();
  await expect(schedule3Page.checkStatusButton).toBeDisabled();
  await expect(schedule3Page.deleteButton).toBeDisabled();
});

Then('the Schedule 3 actions are enabled', async ({ schedule3Page }) => {
  await expect(schedule3Page.saveButton).toBeEnabled();
  await expect(schedule3Page.checkStatusButton).toBeEnabled();
  await expect(schedule3Page.deleteButton).toBeEnabled();
});

/**
 * The DIV-1 expectation: an enterable form. Used ONLY by the deliberately-red `no-create` scenario, so
 * it states what legacy did (open an empty schedule for entry) rather than what the app does today.
 */
Then('the Schedule 3 form is displayed for entry', async ({ schedule3Page }) => {
  await expect(
    schedule3Page.costTable,
    'Schedule 3 did not open an enterable form for a Draft mill-year whose schedule was never started ' +
      '(defects.md DIV-1 — legacy created the summary on the first Save; the rewrite has no create path)',
  ).toBeVisible();
  expect(
    await schedule3Page.editableFieldCount(),
    'the Schedule 3 form rendered but carries no editable amount inputs',
  ).toBeGreaterThan(0);
});

Then('the Schedule 3 form is not displayed', async ({ schedule3Page }) => {
  await expect(schedule3Page.costTable).toHaveCount(0);
  await expect(schedule3Page.overheadTable).toHaveCount(0);
});

Then('both Schedule 3 sub-page links are shown', async ({ schedule3Page }) => {
  await expect(schedule3Page.otherAcceptableLink).toBeVisible();
  await expect(schedule3Page.unacceptableLink).toBeVisible();
});

Then('the Other Costs count is {int}', async ({ schedule3Page }, count) => {
  await expect
    .poll(async () => schedule3Page.otherAcceptableCount(), {
      message: `the "Subtotal Other Costs (n):" link never showed ${count}`,
    })
    .toBe(count);
});

Then('the Included Unacceptable Costs count is {int}', async ({ schedule3Page }, count) => {
  await expect
    .poll(async () => schedule3Page.unacceptableCount(), {
      message: `the "Included Unacceptable Costs (n):" link never showed ${count}`,
    })
    .toBe(count);
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the stored record (API read-back; the UI's own repaint proves nothing on its own)
// ---------------------------------------------------------------------------------------------------

Then('the stored Schedule 3 holds those amounts', async ({ request, world }) => {
  await expect
    .poll(
      async () => {
        const doc = await getSchedule3(request, world.scheduleKey!);
        return HAPPY_PATH_LINES.map((entered) => {
          const [harvest, pop] = lineValues(doc, entered.code);
          return `${entered.code}:${harvest}/${pop}`;
        }).join(' ');
      },
      { message: 'the saved Schedule 3 never held every entered amount' },
    )
    .toBe(
      HAPPY_PATH_LINES.map((entered) => {
        // The three Harvest-only lines are stored with a server-owned PO&P: 0 for Annual Rents (29) and
        // Silviculture Admin (37) — legacy forces it — and the volume-ratio derivation for Scaling (33).
        const pop =
          entered.pop !== null
            ? entered.pop
            : entered.code === 33
              ? HAPPY_PATH_SCALING_POP
              : 0;
        return `${entered.code}:${entered.harvest}/${pop}`;
      }).join(' '),
    );
});

Then('the stored Schedule 3 holds the derived figures', async ({ request, world }) => {
  const d = HAPPY_PATH_DERIVED;
  await expect
    .poll(
      async () => {
        const doc = await getSchedule3(request, world.scheduleKey!);
        return {
          subtotalActualCosts: totalValues(doc.subtotalActualCosts),
          includedUnacceptableCosts: totalValues(doc.includedUnacceptableCosts),
          totalCosts: totalValues(doc.totalCosts),
          popTimber: blockValues(doc.popTimber),
          crownTimber: blockValues(doc.crownTimber),
          totalOverhead: blockValues(doc.totalOverhead),
        };
      },
      { message: 'the saved Schedule 3 never held the expected derived figures' },
    )
    .toEqual({
      subtotalActualCosts: [d.subtotalActualCosts.harvest, d.subtotalActualCosts.pop, d.subtotalActualCosts.crown],
      includedUnacceptableCosts: [
        d.includedUnacceptableCosts.harvest,
        d.includedUnacceptableCosts.pop,
        d.includedUnacceptableCosts.crown,
      ],
      totalCosts: [d.totalCosts.harvest, d.totalCosts.pop, d.totalCosts.crown],
      popTimber: [d.popTimber.volume, d.popTimber.cost, d.popTimber.perUnit],
      crownTimber: [d.crownTimber.volume, d.crownTimber.cost, d.crownTimber.perUnit],
      totalOverhead: [d.totalOverhead.volume, d.totalOverhead.cost, d.totalOverhead.perUnit],
    });
});

Then('the stored Schedule 3 holds the comments and Override selection', async ({ request, world }) => {
  await expect
    .poll(async () => {
      const doc = await getSchedule3(request, world.scheduleKey!);
      return [doc.comments ?? null, doc.overrideHarvestTotalPop ?? null];
    })
    .toEqual([HAPPY_PATH_COMMENTS, 'N']);
});

Then('the stored Crown Timber volume is the new one', async ({ request, world }) => {
  await expect
    .poll(async () => (await getSchedule3(request, world.scheduleKey!)).crownTimber.volume ?? null, {
      message: 'the changed Crown Timber volume was never stored',
    })
    .toBe(CROWN_PUSH_VOLUME);
});

Then('Schedule 3 no longer exists for that mill and year', async ({ request, world }) => {
  // RE-GROUNDED 2026-08-26 (defect #296): a deleted Schedule 3 no longer 404s — the GET serves a 200
  // empty EDITABLE document, which is the point of the fix (the reporter can start again immediately
  // rather than being stranded). "Gone" therefore means the summary row is gone, i.e. the document is
  // UNSAVED — no optimistic-lock token — which is exactly what the page's own `isScheduleSaved` reads.
  // Polled because the DELETE plus in-place redisplay is UI-triggered, so the commit can trail the click.
  await expect
    .poll(async () => await schedule3IsSaved(request, world.scheduleKey!), {
      message: 'Schedule 3 still reports a saved summary after the delete',
    })
    .toBe(false);
});

Then('the stored Schedule 3 is still empty', async ({ request, world }) => {
  const doc = await getSchedule3(request, world.scheduleKey!);
  const stored = doc.lineItems
    .filter((li) => (li.harvest ?? null) !== null || (li.pop ?? null) !== null)
    .map((li) => li.costItemCode);
  expect(stored, 'a rejected entry was written to the database anyway').toEqual([]);
  expect([doc.popTimber.volume ?? null, doc.crownTimber.volume ?? null]).toEqual([null, null]);
});

Then('the Schedule 3 optimistic-lock token has not moved', async ({ request, world }) => {
  const doc = await getSchedule3(request, world.scheduleKey!);
  expect(
    doc.revisionCount ?? null,
    'the Schedule 3 revision token advanced, so something was written',
  ).toBe(world.sch3RevisionAtOpen ?? null);
});

Then('no Schedule 3 write was attempted', async ({ page, schedule3MutationSpy, world }) => {
  // The negative has to hold over a WINDOW, not at one instant: a regression that renders the inline
  // error and then fires the request a tick later would read 0 and pass green. The shared barrier
  // drains the page's own deferrals and completes a real round-trip first.
  await settleBeforeReadingSpy(page);
  expect(
    schedule3MutationSpy.mutations,
    'a mutating Schedule 3 request was sent even though the entry was rejected client-side',
  ).toBe(world.sch3MutationsBefore ?? 0);
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the BR-09 push into Schedule 1
// ---------------------------------------------------------------------------------------------------

Then('the new Crown Timber volume is applied to Schedule 1', async ({ request, world }) => {
  // Two assertions, because BR-09 makes two promises. First: the pushed volume reached EVERY item the
  // push covers — named per item, so a failure says which one missed.
  const expected = Object.fromEntries(
    Object.keys(schedule1PushedVolumes(await getSchedule1(request, world.scheduleKey!))).map((k) => [
      k,
      CROWN_PUSH_VOLUME,
    ]),
  );
  await expect
    .poll(async () => schedule1PushedVolumes(await getSchedule1(request, world.scheduleKey!)), {
      message: 'the pushed Crown Timber volume never reached every Schedule 1 volume field',
    })
    .toEqual(expected);

  // Second: nothing ELSE on Schedule 1 holds a different volume — so the push cannot have left a stale
  // figure behind, whatever other volume rows the anchor happens to carry.
  const stored = schedule1Volumes(await getSchedule1(request, world.scheduleKey!));
  expect(
    [...new Set(stored)],
    'Schedule 1 holds a volume the crown push did not write',
  ).toEqual([CROWN_PUSH_VOLUME]);
});

Then('Schedule 1 still has not been opened', async ({ request, world }) => {
  // "Not opened" = no category-1 summary. Since defect #296 the GET answers 200 empty+editable whether or
  // not the summary exists, so this asserts saved-ness — the same signal the app uses. The behaviour under
  // test is unchanged: WRN-002 says the push was NOT applied, so it must not have created a Schedule 1.
  expect(
    await schedule1IsSaved(request, world.scheduleKey!),
    'Schedule 1 was created by the crown push, but WRN-002 says it was not applied',
  ).toBe(false);
});

// ---------------------------------------------------------------------------------------------------
// Assertions — the seeded read-only anchors (so a drifted patch fails as a re-ground, not as a mystery)
// ---------------------------------------------------------------------------------------------------

Then('the seeded Schedule 3 amounts are displayed', async ({ schedule3Page }) => {
  for (const seeded of SEEDED_BASE_LINES) {
    const label = lineByCode(seeded.code).label;
    const cells = await schedule3Page.lineCells(label);
    expect(cells.harvest, `seeded "${label}" Harvest`).toBe(String(seeded.harvest));
  }
  const wages = await schedule3Page.lineCells('Wages/Salaries, incl Benefits');
  expect([wages.harvest, wages.pop]).toEqual([
    String(SEEDED_WAGES_VIOLATION.harvest),
    String(SEEDED_WAGES_VIOLATION.pop),
  ]);
  const [popVolume] = await schedule3Page.overheadCells('Privately Owned & Purchased (PO&P) Timber');
  const [crownVolume] = await schedule3Page.overheadCells('Crown Timber');
  expect([popVolume, crownVolume]).toEqual([
    String(SEEDED_POP_TIMBER_VOLUME),
    String(SEEDED_CROWN_TIMBER_VOLUME),
  ]);
});

Then(
  'the {string} line PO&P cell shows {string}',
  async ({ schedule3Page }, label, expected) => {
    const cells = await schedule3Page.lineCells(label);
    expect(cells.pop, `the "${label}" PO&P cell`).toBe(expected === 'a dash' ? '—' : expected);
  },
);

Then('the {string} line has no PO&P input', async ({ schedule3Page }, label) => {
  const line = lineByLabel(label);
  await expect(
    schedule3Page.field(`#pop-${line.code}`),
    `"${label}" must not expose a PO&P input (BR-04)`,
  ).toHaveCount(0);
});

Then('the Schedule 3 comments show {string}', async ({ schedule3Page }, expected) => {
  expect(await schedule3Page.commentsInput.inputValue()).toBe(expected);
});

