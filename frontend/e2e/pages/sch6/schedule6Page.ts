import { type Locator, type Page, expect } from '@playwright/test';
import { SchedulePage } from '../common/schedulePage';
import { byId, fieldError } from '../common/carbonHelpers';
import {
  ADD_FIELD,
  ADD_PANEL_HEADING,
  EMPTY_LIST,
  rowField,
} from '../../fixtures/sch6/schedule6-test-data';

/**
 * Schedule 6 (Report Road Management Costs) — the only place sch6 DOM selectors live.
 *
 * ---------------------------------------------------------------------------------------------------
 * WHY EVERY FIELD IS ADDRESSED BY ID, NOT BY LABEL
 * ---------------------------------------------------------------------------------------------------
 * The Add panel and EVERY saved record's row render the SAME six labels — "TSA or TFL", "TFL",
 * "Supply Block", "Volume m³", "Cost $", "Comments" (components/schedule6/index.tsx:251-350, one
 * `RoadRecordFields` shared by both). So `getByLabel('Volume m³')` resolves to one element on an empty
 * anchor and to N+1 the moment a record exists: a strict-mode violation that would appear only once a
 * scenario had successfully saved, i.e. in the middle of the happy path. sch5 shipped a latent
 * violation of exactly this class (caught in review, PR #479).
 *
 * The ids are stable and structured — `idPrefix` is `add` for the panel and `row-<recordId>` for a
 * record (index.tsx:396, 448) — so they are the resilient locator here, which is what the suite's own
 * guidance means by "a stable `#id`". Both shapes are pinned in the fixture, never inlined here.
 *
 * `byId` rather than `page.locator('#…')` for the same reason Schedule 4 needs it: a bare CSS id
 * selector may not begin with a digit. sch6's own prefixes never do, but the helper costs nothing and
 * keeps one idiom across domains.
 *
 * ---------------------------------------------------------------------------------------------------
 * THE TWO CLASSIFICATION FIELDS ARE FILTERABLE COMBOBOXES
 * ---------------------------------------------------------------------------------------------------
 * "TSA or TFL" and "Supply Block" are `CodeComboBox` (Carbon `ComboBox`), whose options render the
 * code's DESCRIPTION (`itemToString: item.description`) and which filters as you type
 * (`shouldFilterItem`). Selection therefore TYPES to filter and then clicks the exact option, rather
 * than scrolling — the supply-block catalogue is 272 entries, so a scroll-and-click would be both slow
 * and dependent on list order.
 *
 * Supply Block is FILTERED BY THE CHOSEN TSA (`supplyBlocksFor` keeps blocks whose code starts with the
 * TSA code), so the TSA must be selected FIRST or the block is not offered at all. `selectSupplyBlock`
 * does not enforce the order — it asserts the option is there, which fails with a clear message if the
 * caller got it wrong.
 */
export class Schedule6Page {
  private readonly schedule: SchedulePage;

  constructor(private readonly page: Page) {
    this.schedule = new SchedulePage(page);
  }

  /** Open Schedule 6 via the side-nav (client-side, so the saved working context survives). */
  async open(): Promise<void> {
    await this.schedule.open('Schedule 6');
  }

  // ---- The Add panel (legacy's toggled `roadAddPanel`) --------------------------------------------

  /**
   * The Add/Close toggle. Located by ROLE AND NAME because it has no stable id — the legacy Gherkin's
   * own "Open Items Carried Forward" flags this, and the React page renders a plain Carbon `Button`
   * whose accessible name is its text.
   */
  get addToggle(): Locator {
    return this.page.getByRole('button', { name: 'Add', exact: true });
  }

  /** The Add panel itself — a labelled section, so it is addressable by role. */
  get addPanel(): Locator {
    return this.page.getByRole('region', { name: ADD_PANEL_HEADING });
  }

  /** `Add Report` — posts immediately (add-is-save), independent of the page-level Save. */
  get addReportButton(): Locator {
    return this.page.getByRole('button', { name: 'Add Report', exact: true });
  }

  /** Open the Add panel and wait for it to actually be on screen. */
  async openAddPanel(): Promise<void> {
    await this.addToggle.click();
    await expect(this.addPanel).toBeVisible();
  }

  /**
   * Assert the Add panel's inputs are blank.
   *
   * This is S01's opening claim, and it is about the PANEL not the anchor: the anchor being record-free
   * is the preflight's job. A panel that re-opened carrying a previous entry's values would make the
   * rest of the scenario pass while testing something else.
   */
  async assertAddPanelBlank(): Promise<void> {
    for (const id of [ADD_FIELD.areaType, ADD_FIELD.supplyBlock, ADD_FIELD.volume, ADD_FIELD.cost]) {
      await expect(byId(this.page, id), `${id} should be blank in a freshly opened Add panel`).toHaveValue(
        '',
      );
    }
  }

  // ---- Field entry --------------------------------------------------------------------------------

  /**
   * Pick an option in a filterable CodeComboBox: open it, type to narrow the list, click the exact
   * option. Asserting the option's presence before clicking is what turns "the TSA was not selected
   * first, so this block is not offered" into a readable failure instead of a click timeout.
   */
  private async selectCombo(id: string, optionText: string): Promise<void> {
    const combo = byId(this.page, id);
    await combo.click();
    await combo.fill(optionText);
    const option = this.page.getByRole('option', { name: optionText, exact: true });
    await expect(
      option,
      `no option "${optionText}" in ${id}. For Supply Block this usually means the TSA was not selected `
        + 'first — the block list is filtered to codes starting with the chosen TSA (supplyBlocksFor).',
    ).toBeVisible();
    await option.click();
    await expect(combo).toHaveValue(optionText);
  }

  /** Select the area type ("TSA or TFL") by its rendered description, e.g. "Arrow TSA" or "TFL". */
  async selectAreaType(optionText: string): Promise<void> {
    await this.selectCombo(ADD_FIELD.areaType, optionText);
  }

  /** Select the Supply Block by its rendered description. Requires the TSA to be selected already. */
  async selectSupplyBlock(optionText: string): Promise<void> {
    await this.selectCombo(ADD_FIELD.supplyBlock, optionText);
  }

  /**
   * Fill Volume and Cost, then BLUR so the app's own commit-and-re-group runs.
   *
   * The blur is load-bearing, not hygiene: both fields re-group on blur (volume via `groupInput`, cost
   * via `groupFixedInput(…, 0)`) and the $/m³ baseline is committed by the same handler
   * (`onRateCommit`). Asserting the derived rate without blurring would read a stale value and read as
   * a derived-figure defect. `blur()` on the element rather than a click elsewhere, so nothing else on
   * the page is incidentally activated.
   */
  async enterAmounts(volume: string, cost: string): Promise<void> {
    const vol = byId(this.page, ADD_FIELD.volume);
    await vol.fill(volume);
    await vol.blur();
    const cos = byId(this.page, ADD_FIELD.cost);
    await cos.fill(cost);
    await cos.blur();
  }

  /** Fill the per-record Comments field in the Add panel. */
  async enterComments(text: string): Promise<void> {
    await byId(this.page, ADD_FIELD.comments).fill(text);
  }

  /** The Carbon error text under an Add-panel field, for the validation slices. */
  addFieldError(field: keyof typeof ADD_FIELD): Locator {
    return fieldError(this.page, ADD_FIELD[field]);
  }

  // ---- The record list ----------------------------------------------------------------------------

  /** The empty-list placeholder legacy inherited from PrimeFaces ("No records found."). */
  get emptyList(): Locator {
    return this.page.getByText(EMPTY_LIST, { exact: true });
  }

  /** A saved record's Volume input, by record id — proves the row rendered with the stored value. */
  rowVolume(recordId: number): Locator {
    return byId(this.page, rowField(recordId).volume);
  }

  /** A saved record's Cost input, by record id. */
  rowCost(recordId: number): Locator {
    return byId(this.page, rowField(recordId).cost);
  }

  /**
   * A derived figure on a saved record's row, read from the row's own definition list.
   *
   * RMG and $ / m³ are the two values with no input of their own: they are rendered by `FieldValue`
   * inside `<dl className="schedule-6__derived">` (index.tsx:285-287, 344-346). Scoped to the row's
   * fieldset via the row's Volume input so a second record cannot satisfy the assertion.
   */
  rowDerived(recordId: number, label: 'RMG' | '$ / m³'): Locator {
    return this.page
      .locator(`xpath=//*[@id="${rowField(recordId).volume.slice(1)}"]/ancestor::div[contains(@class,"schedule-6__fields")][1]`)
      .locator('.schedule-6__derived')
      .filter({ hasText: label })
      .locator('dd');
  }

  /** The Add panel's own $ / m³ cell, which DOES track the blurred volume/cost (unlike its RMG). */
  get addCostPerVolume(): Locator {
    return this.addPanel.locator('.schedule-6__derived').filter({ hasText: '$ / m³' }).locator('dd');
  }

  // ---- Totals and actions -------------------------------------------------------------------------

  /** The Totals section — a labelled region (index.tsx:1046). */
  get totals(): Locator {
    return this.page.getByRole('region', { name: 'Totals' });
  }

  /** A totals figure by its label, so the three are never positionally addressed. */
  total(label: string): Locator {
    return this.totals.locator('.schedule-6__derived, div').filter({ hasText: label }).locator('dd').first();
  }

  /**
   * Check Status. Legacy carried the button twice (above the schedule and below the General Comment);
   * the React page mirrors that (`actionBar`, index.tsx:880-900), so this takes the FIRST — the
   * `checkStatusButton0` the S01 Gherkin names. `.first()` is deliberate and not a strict-mode dodge:
   * the two instances are genuinely the same control, and which one is clicked is not S01's subject.
   */
  get checkStatusButton(): Locator {
    return this.page.getByRole('button', { name: 'Check Status', exact: true }).first();
  }

  /** The page-level Save (fans every row plus the general comment out in one PUT). */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true }).first();
  }
}
