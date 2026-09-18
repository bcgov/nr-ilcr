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

  /**
   * A record's accordion header, addressed by its 1-based ORDINAL.
   *
   * The title is `Road Maintenance report Id: <ordinal>` (index.tsx:1012), and that number is the
   * position in `roadRecords[]` — NOT the `recordId`. The distinction is load-bearing: the ordinal is
   * what legacy's `rowCounter` means and what the check-status lines key on, while `recordId` belongs
   * only in the URL. Getting them confused reads as an off-by-one in the app.
   */
  recordAccordion(ordinal: number): Locator {
    return this.page.getByRole('button', { name: `Road Maintenance report Id: ${ordinal}` });
  }

  /**
   * Expand a record's accordion panel and wait for its fields to be genuinely VISIBLE.
   *
   * WHY THIS IS NOT OPTIONAL. `AccordionItem` carries no `open` prop, so every row starts COLLAPSED,
   * and Carbon renders every item's children into the DOM regardless of which panel is expanded —
   * index.tsx:479 says so at the point where it works around the consequence. So `toHaveValue` on a
   * row field passes on a collapsed row: it asserts DOM state, not what the reporter can see. Every
   * row assertion therefore goes through here first, and waits on VISIBILITY rather than presence.
   */
  async expandRecord(ordinal: number, recordId: number): Promise<void> {
    const header = this.recordAccordion(ordinal);
    await expect(
      header,
      `no accordion titled "Road Maintenance report Id: ${ordinal}" — the ordinal is the 1-based `
        + 'position in roadRecords[], not the recordId',
    ).toBeVisible();
    await header.click();
    await expect(byId(this.page, rowField(recordId).volume)).toBeVisible();
  }

  /** A saved record's Volume input, by record id — assert only after `expandRecord`. */
  rowVolume(recordId: number): Locator {
    return byId(this.page, rowField(recordId).volume);
  }

  /** A saved record's Cost input, by record id — assert only after `expandRecord`. */
  rowCost(recordId: number): Locator {
    return byId(this.page, rowField(recordId).cost);
  }

  /**
   * Overwrite a saved row's Volume and Cost, blurring each so the app's commit-and-re-group runs.
   *
   * `fill` replaces rather than appends, which is what an edit means here. The blur matters for the
   * same reason it does in the Add panel: it is what commits the $ / m³ baseline.
   */
  async setRowAmounts(recordId: number, volume: string, cost: string): Promise<void> {
    const vol = this.rowVolume(recordId);
    await vol.fill(volume);
    await vol.blur();
    const cos = this.rowCost(recordId);
    await cos.fill(cost);
    await cos.blur();
  }

  /**
   * A `FieldValue` cell's value, located by its label EXACTLY.
   *
   * `FieldValue` renders `<div class="schedule-6__field"><dt>label</dt><dd>value</dd></div>`
   * (index.tsx:180-185), so the label lives in a `dt` and the figure in the sibling `dd`. Matching the
   * `dt` anchored end-to-end is what keeps "Volume m³" from also being satisfied by "$ / m³" and keeps
   * a bare `hasText: 'Cost'` from matching two cells — a substring filter here is how a totals
   * assertion can silently read the wrong number.
   */
  private fieldValue(scope: Locator, label: string): Locator {
    const exact = new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`);
    return scope
      .locator('.schedule-6__field')
      .filter({ has: this.page.locator('dt').filter({ hasText: exact }) })
      .locator('dd');
  }

  /**
   * A derived figure on a saved record's row. Scoped to THAT row's field grid via its own Volume
   * input, so a second record's identical labels cannot satisfy the assertion.
   */
  rowDerived(recordId: number, label: 'RMG' | '$ / m³'): Locator {
    const rowScope = this.page.locator(
      `xpath=//*[@id="${rowField(recordId).volume.slice(1)}"]/ancestor::div[contains(@class,"schedule-6__fields")][1]`,
    );
    return this.fieldValue(rowScope, label);
  }

  /** The Add panel's own $ / m³ cell, which DOES track the blurred volume/cost (unlike its RMG). */
  get addCostPerVolume(): Locator {
    return this.fieldValue(this.addPanel, '$ / m³');
  }

  // ---- Totals and actions -------------------------------------------------------------------------

  /** The Totals section — a labelled region (index.tsx:1046). */
  get totals(): Locator {
    return this.page.getByRole('region', { name: 'Totals' });
  }

  /**
   * A totals figure by its label. The three labels are the FIELD names, not "Total …":
   * "Volume m³", "Cost $", "$ / m³" (index.tsx:1051-1053).
   */
  total(label: 'Volume m³' | 'Cost $' | '$ / m³'): Locator {
    return this.fieldValue(this.totals, label);
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
