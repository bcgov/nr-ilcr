import { type APIRequestContext } from '@playwright/test';
import { Given, When, Then, expect } from '../fixtures';
import {
  OTHER_COSTS_ANCHORS,
  millOptionText,
  scheduleUrl,
} from '../../fixtures/sch1/schedule1-test-data';
import { addOtherCost, listOtherCosts } from './otherCostsApi';

/**
 * Subtotal Other Costs sub-page steps (S09–S12). Domain vocabulary only — DOM lives in OtherCostsPage /
 * Schedule1Page; row/precondition data setup and read-backs go through the real Other-Costs API.
 */

async function assertEditableDraft(
  request: APIRequestContext,
  millId: number,
  year: number,
): Promise<void> {
  const res = await request.get(scheduleUrl(millId, year));
  expect(
    res.ok(),
    `precondition: GET Schedule 1 for ${millId}/${year} returned HTTP ${res.status()}`,
  ).toBeTruthy();
  const doc = (await res.json()) as { trackStatus: string; editable: boolean };
  expect(doc.trackStatus, 'precondition: Other Costs target must be Draft ("D")').toBe('D');
  expect(doc.editable, 'precondition: Other Costs target must be editable').toBe(true);
}

Given(
  'the Other Costs {string} target is an editable Draft',
  async ({ request, world, otherCostsCleanup }, name: string) => {
    const anchor = OTHER_COSTS_ANCHORS[name as keyof typeof OTHER_COSTS_ANCHORS];
    expect(anchor, `unknown Other Costs target "${name}"`).toBeTruthy();
    world.scheduleKey = anchor.key;
    world.millOption = millOptionText(anchor.mill);
    await assertEditableDraft(request, anchor.key.millId, anchor.key.year);
    // Register cleanup for any row this scenario will add (validate-only targets have no marker).
    if (anchor.marker) {
      otherCostsCleanup.push({
        millId: anchor.key.millId,
        year: anchor.key.year,
        marker: anchor.marker,
      });
    }
  },
);

Given('an itemized Other Cost line item exists to remove', async ({ request, world, otherCostsCleanup }) => {
  // S12 precondition: seed a row via the real API on the dedicated remove target, register its cleanup
  // (in case the UI delete under test fails), then the scenario removes it through the UI.
  const anchor = OTHER_COSTS_ANCHORS.remove;
  world.scheduleKey = anchor.key;
  world.millOption = millOptionText(anchor.mill);
  await assertEditableDraft(request, anchor.key.millId, anchor.key.year);
  otherCostsCleanup.push({
    millId: anchor.key.millId,
    year: anchor.key.year,
    marker: anchor.marker,
  });
  // Seed the row through the real API, then the scenario removes it through the UI.
  await addOtherCost(request, anchor.key.millId, anchor.key.year, anchor.marker, 4200);
});

Given('a spy is watching the Other Costs add request', async ({ otherCostsSpy }) => {
  expect(otherCostsSpy.mutations, 'spy must start at zero mutating requests').toBe(0);
});

When('I note the Subtotal Other Costs count', async ({ schedule1Page, world }) => {
  world.otherCostsCountBefore = await schedule1Page.otherCostsCount();
});

When('I open the Other Costs sub-page', async ({ schedule1Page, otherCostsPage }) => {
  await schedule1Page.openOtherCosts();
  await otherCostsPage.expectLoaded();
});

When('I add an Other Cost {string} with cost {string}', async ({ otherCostsPage }, description, cost) => {
  await otherCostsPage.addRow(description, cost);
});

When('I delete the Other Cost {string}', async ({ otherCostsPage }, description) => {
  await otherCostsPage.deleteRow(description);
});

When('I go back to Schedule 1', async ({ otherCostsPage, schedule1Page }) => {
  await otherCostsPage.backToSchedule1();
  await expect(schedule1Page.companyLoggingTable).toBeVisible();
});

Then('the Other Cost {string} appears in the Other Costs list', async ({ otherCostsPage }, description) => {
  // Editable rows render the description as an input value (not row text); poll the live values since
  // the list re-seeds from the save response after the whole-set PUT.
  await expect.poll(() => otherCostsPage.descriptions()).toContain(description);
});

Then('the Other Cost {string} is no longer in the Other Costs list', async ({ otherCostsPage }, description) => {
  await expect.poll(() => otherCostsPage.descriptions()).not.toContain(description);
});

Then('the Subtotal Other Costs count has increased by one', async ({ schedule1Page, world }) => {
  const before = world.otherCostsCountBefore;
  expect(before, 'the Other Costs count was not noted before the round-trip').not.toBeUndefined();
  await expect.poll(async () => schedule1Page.otherCostsCount()).toBe((before ?? 0) + 1);
});

Then(
  'the Other Cost {string} is persisted with cost {int}',
  async ({ request, world }, description, cost) => {
    const { millId, year } = world.scheduleKey!;
    await expect
      .poll(async () => {
        const row = (await listOtherCosts(request, millId, year)).find(
          (r) => r.description === description,
        );
        return row ? row.cost : null;
      })
      .toBe(cost);
  },
);

Then('the Other Cost {string} is not persisted', async ({ request, world }, description) => {
  const { millId, year } = world.scheduleKey!;
  await expect
    .poll(
      async () =>
        (await listOtherCosts(request, millId, year)).filter((r) => r.description === description)
          .length,
    )
    .toBe(0);
});

Then('the Other Cost add request should not have been sent', async ({ otherCostsSpy }) => {
  expect(
    otherCostsSpy.mutations,
    'a client-rejected Add must not send the mutating Other Costs PUT',
  ).toBe(0);
});
