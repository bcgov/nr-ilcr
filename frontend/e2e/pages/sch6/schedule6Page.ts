import { type Locator, type Page, expect } from '@playwright/test';
import { SchedulePage } from '../common/schedulePage';
import { navigateViaSideNav } from '../common/authNav';
import { clickAwaitingCheckStatus } from '../common/checkStatus';
import { byId, fieldError } from '../common/carbonHelpers';
import {
  ADD_FIELD,
  ADD_PANEL_HEADING,
  EMPTY_LIST,
  GENERAL_COMMENTS_FIELD,
  MILL_YEAR_STORAGE_KEY,
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

  /**
   * Open Schedule 6 via the side-nav when a GUARD is expected.
   *
   * A separate path from `open()` because that one waits for the tombstone's working-context region,
   * which a guarded page never renders — the guard replaces the whole body. So this asserts only that
   * the route was reached. Anchored with `$` so a deeper path could not satisfy it.
   */
  async openExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 6' });
    await expect(this.page).toHaveURL(/\/schedule-6$/);
  }

  /**
   * Open Schedule 6 with NO working context (S06).
   *
   * Seeds an EMPTY MillYearContext into localStorage — the supported empty state
   * (`context/millYear/MillYearProvider.tsx`) — and loads the route directly, so the page renders its
   * context-missing guard WITHOUT ever issuing a request. Going through Home and declining to pick a
   * mill would leave the context UNSET rather than empty, which is a different state and would not
   * exercise the same branch.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-6');
    await expect(this.page).toHaveURL(/\/schedule-6$/);
  }

  /** A Carbon notification containing `text` — how every guard message reaches the reporter. */
  notification(text: string): Locator {
    return this.page.getByRole('status').filter({ hasText: text });
  }

  /**
   * The whole data-entry surface the three guards must suppress.
   *
   * Asserts ABSENCE (`toHaveCount(0)`), not disabled-ness: a guarded Schedule 6 returns its load state
   * instead of the body (`index.tsx:840`), so these controls are not in the page at all. Checking for
   * "disabled" would pass vacuously against an element that does not exist.
   */
  async expectDataEntrySuppressed(): Promise<void> {
    await expect(this.addToggle).toHaveCount(0);
    await expect(this.addReportButton).toHaveCount(0);
    await expect(this.totals).toHaveCount(0);
    await expect(this.generalComments).toHaveCount(0);
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

  /**
   * The Add panel's Volume and Cost inputs, for the validation slices (S13-S16).
   *
   * Exposed as locators as well as through `enterVolume`/`enterCost` because those slices assert what
   * the field STILL READS after an invalid entry is blurred: both masks return text they cannot parse
   * unchanged, deliberately, "so a typo stays on screen for the user to correct"
   * (utils/number.ts:59-64). That is a claim about the field, not about the form.
   */
  get addVolume(): Locator {
    return byId(this.page, ADD_FIELD.volume);
  }

  get addCost(): Locator {
    return byId(this.page, ADD_FIELD.cost);
  }

  /** The Add panel's area-type combo — S12 submits with this one left untouched. */
  get addAreaType(): Locator {
    return byId(this.page, ADD_FIELD.areaType);
  }

  /**
   * Fill Volume ALONE and blur.
   *
   * Separate from `enterAmounts` (which does both fields) because each of S13-S16 must put an invalid
   * value in exactly ONE field and a valid one in the other — if both were wrong, the error asserted
   * could have come from either and the two messages would be interchangeable. The blur is what runs
   * the app's own commit-and-re-group, which is the behaviour these slices are about.
   */
  async enterVolume(value: string): Promise<void> {
    await this.addVolume.fill(value);
    await this.addVolume.blur();
  }

  /** Fill Cost ALONE and blur. Mirror of `enterVolume` above. */
  async enterCost(value: string): Promise<void> {
    await this.addCost.fill(value);
    await this.addCost.blur();
  }

  /** Fill the per-record Comments field in the Add panel. */
  async enterComments(text: string): Promise<void> {
    await byId(this.page, ADD_FIELD.comments).fill(text);
  }

  /** The Add panel's TFL number input — only active on the TFL branch. */
  get addTflNumber(): Locator {
    return byId(this.page, ADD_FIELD.tflNumber);
  }

  /** The Add panel's Supply Block combo — disabled on the TFL branch (BR-02). */
  get addSupplyBlock(): Locator {
    return byId(this.page, ADD_FIELD.supplyBlock);
  }

  /**
   * Type a TFL number and blur.
   *
   * `fill` replaces, so this is also how a correction is entered. The blur is what lets the client's
   * own advisory validation run before submit — it catches blank and over-wide entries only, because
   * "valid" means "resolves to an RMG" and only the server can decide that.
   */
  async enterTflNumber(value: string): Promise<void> {
    await this.addTflNumber.fill(value);
    await this.addTflNumber.blur();
  }

  // ---- The schedule-level general comment ---------------------------------------------------------

  /**
   * The General Comments textarea. A DIFFERENT field from a record's Comments — a different column
   * with a different cap (3500 over a 4000-wide column, vs 400 for a record's), so it is located by
   * its own id rather than by the repeated "Comments" label.
   */
  get generalComments(): Locator {
    return byId(this.page, GENERAL_COMMENTS_FIELD);
  }

  /** Replace the general comment's text. */
  async enterGeneralComment(text: string): Promise<void> {
    await this.generalComments.fill(text);
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
   * `checkStatusButton0` the Gherkin names. `.first()` is deliberate and not a strict-mode dodge: the
   * two instances are genuinely the same control, and which one is clicked is not these slices' subject.
   */
  get checkStatusButton(): Locator {
    return this.page.getByRole('button', { name: 'Check Status', exact: true }).first();
  }

  /**
   * Click Check Status and WAIT for the verdict to land.
   *
   * Required by any scenario that asserts an ABSENCE — "the met banner is NOT shown". A bare click is
   * fine for a positive assertion because `toBeVisible` auto-waits, but an absence assertion made
   * immediately after the click can pass against a DOM that has not re-rendered, proving nothing. The
   * shared helper is best-effort by design: a blocked Check Status legitimately sends no request, so it
   * must not turn that correct no-op into a timeout. See `pages/common/checkStatus.ts`.
   */
  async runCheckStatus(): Promise<void> {
    await clickAwaitingCheckStatus(this.page, '/schedule6/check-status', async () => {
      await this.checkStatusButton.click();
    });
  }

  /**
   * Change a saved row's area type, by the option's rendered description (S19).
   *
   * Goes through the same filterable-combobox routine as the Add panel, because a row renders the very
   * same `CodeComboBox`. Expand the row first — `selectRowAreaType` does not do it, so that a caller
   * who forgot gets `expandRecord`'s clear message rather than a click timeout on a collapsed panel.
   */
  async selectRowAreaType(recordId: number, optionText: string): Promise<void> {
    await this.selectCombo(rowField(recordId).areaType, optionText);
  }

  /**
   * Overwrite a saved row's TFL number — including clearing it.
   *
   * This is how S10 reaches its state AT ALL. A TFL record with no TFL number cannot be SAVED (the add
   * endpoint answers 400 FLD-002, verified by probe), so the only way to put one in front of Check
   * Status is to save a valid TFL record and then blank the field on screen without saving. That works
   * because Check Status evaluates the ON-SCREEN payload — see the feature header.
   */
  async setRowTflNumber(recordId: number, value: string): Promise<void> {
    const field = byId(this.page, rowField(recordId).tflNumber);
    await field.fill(value);
    await field.blur();
  }

  /** The page-level Save (fans every row plus the general comment out in one PUT). */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true }).first();
  }

  // ---- The read-only (non-Draft) render, S17 ------------------------------------------------------

  /**
   * ALL instances of Save and Check Status, not just the first.
   *
   * The page renders each twice — above the schedule and again below the General Comment
   * (`actionBar`, index.tsx:880-915), mirroring legacy's `saveButton0`/`saveButton1` and
   * `checkStatusButton0`/`checkStatusButton1`, which S17's Gherkin names individually. The
   * single-instance getters above take `.first()` because which one is clicked is not those slices'
   * subject; here it IS the subject, because a page that disabled only the top pair would leave a live
   * Save on a locked schedule. So these assert over every instance.
   */
  get allSaveButtons(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  get allCheckStatusButtons(): Locator {
    return this.page.getByRole('button', { name: 'Check Status', exact: true });
  }

  /**
   * Assert every entry control on the page is locked.
   *
   * WHAT "LOCKED" MEANS FOR THE ADD SURFACE, and why this is not the Gherkin's literal claim. S17's
   * source lists six Add-FORM fields as disabled (`tsaNumberOneMenu`, `tflNumber`, `tsbNumberOneMenu`,
   * `vol`, `cos`, `comAdd`) because legacy rendered that panel inline, always present. The React page
   * renders it only while the toggle is open (`{showAdd && <AddPanel/>}`, index.tsx:971) and disables
   * the toggle itself (`disabled={entryLocked}`, :907) — so on a locked page the panel is not in the
   * DOM at all and cannot be opened. Asserting those six fields are "disabled" would fail against
   * elements that do not exist, and asserting them absent alone would pass vacuously on any page. So
   * the faithful pair is: the toggle EXISTS and is DISABLED, and the panel is ABSENT. That is strictly
   * stronger than legacy's six disabled fields — the entry surface is unreachable rather than inert.
   *
   * The per-record row fields ARE present and disabled (`disabled={entryLocked}`, :1020), so the
   * Gherkin's disabled-field claim is asserted there, on the rows, by `expectRowFieldsDisabled`.
   */
  async expectEntryControlsLocked(): Promise<void> {
    await expect(this.addToggle, 'the Add toggle must be present but disabled').toBeDisabled();
    await expect(
      this.addPanel,
      'the Add panel must not be in the page at all on a locked schedule — its toggle cannot open it',
    ).toHaveCount(0);

    // Both instances of each mirrored action, and the count is asserted first so a page that rendered
    // only one could not satisfy "every instance is disabled" trivially.
    await expect(this.allSaveButtons, 'both Save buttons should be rendered').toHaveCount(2);
    await expect(this.allCheckStatusButtons, 'both Check Status buttons should be rendered').toHaveCount(
      2,
    );
    for (const button of await this.allSaveButtons.all()) {
      await expect(button, 'every Save button must be disabled').toBeDisabled();
    }
    for (const button of await this.allCheckStatusButtons.all()) {
      await expect(button, 'every Check Status button must be disabled').toBeDisabled();
    }

    await expect(this.generalComments, 'the General Comments field must be disabled').toBeDisabled();
  }

  /**
   * Assert one saved record's own fields are disabled.
   *
   * Goes through `expandRecord` first for the reason VER-2 records: Carbon puts every accordion panel's
   * children in the DOM whichever one is open, so a `toBeDisabled` on a collapsed row asserts DOM state
   * rather than what the reporter can see.
   */
  async expectRowFieldsDisabled(ordinal: number, recordId: number): Promise<void> {
    await this.expandRecord(ordinal, recordId);
    const fields = rowField(recordId);
    for (const [name, selector] of Object.entries(fields)) {
      await expect(
        byId(this.page, selector),
        `row ${recordId}'s ${name} field must be disabled on a non-Draft schedule`,
      ).toBeDisabled();
    }
  }

  /** A saved record's per-record Comments input, by record id. */
  rowComments(recordId: number): Locator {
    return byId(this.page, rowField(recordId).comments);
  }

  /** A saved record's Supply Block combo, by record id. */
  rowSupplyBlock(recordId: number): Locator {
    return byId(this.page, rowField(recordId).supplyBlock);
  }

  /** A saved record's area-type combo, by record id. */
  rowAreaType(recordId: number): Locator {
    return byId(this.page, rowField(recordId).areaType);
  }

  /** A saved record's TFL number input, by record id. */
  rowTflNumber(recordId: number): Locator {
    return byId(this.page, rowField(recordId).tflNumber);
  }

  /**
   * The row Delete button for a record, by its 1-based ordinal.
   *
   * The name is the `aria-label`, `Delete Road Maintenance Report <ordinal>` (index.tsx:481) — NOT the
   * visible text, which is the bare legacy "Delete". The ordinal is appended deliberately, "so an
   * N-row schedule doesn't collapse into N identically-named buttons (Carbon renders every
   * AccordionItem's children into the DOM regardless of which panel is expanded)" — the same VER-2
   * behaviour that makes `expandRecord` necessary. A bare `name: 'Delete'` would be a strict-mode
   * violation the moment a second record exists, which is exactly S17's fixture.
   */
  rowDeleteButton(ordinal: number): Locator {
    return this.page.getByRole('button', {
      name: `Delete Road Maintenance Report ${String(ordinal)}`,
      exact: true,
    });
  }
}
