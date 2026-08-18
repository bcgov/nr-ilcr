import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import { byId, fieldError } from '../common/carbonHelpers';
import { escapeRegExp } from '../common/urlMatch';
import {
  ACTION,
  CATEGORY_CODE_BY_LABEL,
  FIELD_ID,
  MILL_YEAR_STORAGE_KEY,
  MODAL,
  subPageByLabel,
} from '../../fixtures/sch4/schedule4-test-data';

/**
 * Schedule 4 — Special Log Transportation Systems, the location list + location panel
 * (components/schedule4/index.tsx). All Schedule 4 main-page DOM knowledge lives here; steps carry
 * domain vocabulary only. The three list sub-pages have their own page object
 * (`pages/sch4/schedule4SubPage.ts`) because they are a separate view, not a section of this one.
 *
 * RE-GROUNDING NOTE — the legacy Gherkin's locators describe a JSF/PrimeFaces page that no longer
 * exists, so none of them are used:
 *   - route `/schedule-4` reached via Home + side-nav, not `schedule4.xhtml`; the three sub-pages are
 *     URL states of that SAME route (`?loc=<id>&sub=TOWING|TRUCK_REHAUL|OTHER`), not separate
 *     `schedule4TowingTotal.xhtml` / `…TruckRehaul.xhtml` / `…OtherTransportation.xhtml` views — so the
 *     browser Back button steps back to the list.
 *   - ONE panel serves New/Edit/Copy/View (heading switches), where legacy had two `ui:include`
 *     fragments with two parallel id sets — every `new`-prefixed legacy id
 *     (`newLakeSideDryDumpVolume` vs `lakeSideDryDumpVolume`) collapses to a single
 *     `#{costItemCode}-{field}` id here, keyed by the legacy cost-item code.
 *   - stable Carbon ids (`#location-name`, `#location-comments`, `#40-volume`, `#47-distance`), not
 *     PrimeFaces naming-container ids (`schedule4Form:newLocationName`). Every legacy
 *     `[UNKNOWN — no explicit id]` marker on the Edit/Copy/Delete/Save/Add/Check-Status buttons is
 *     RESOLVED by the rewrite: each is an addressable Carbon button with an accessible name.
 *   - the delete confirm is a Carbon Modal ("Delete"/"Cancel"), not one of three `p:confirmDialog`
 *     variants (`:confirm`/`:confirm2`/`:confirm3`) chosen by row-state branch.
 *   - there is no `p:messages` panel: results render as Carbon InlineNotifications with an explicit
 *     severity word in the title (WCAG 2.1 AA — never colour alone).
 *   - the legacy read-only ROLLUP inputs per sub-page group (`towingTotalDist`/`Volume`/`Cost`/
 *     `CostVolume`) are gone: the group's totals render as plain cells on its grid row, summed from the
 *     sub-page rows. Same figures, no disabled inputs to address.
 *   - Copy/Delete on a non-Draft report are rendered-but-DISABLED, where legacy omitted the buttons
 *     from the DOM entirely. Same user-visible outcome, different mechanism — see coverage.md.
 *
 * Carbon specifics: the numeric grid cells are `CommaNumberInput`s, which DISPLAY a grouped value
 * ("1,200") while holding a raw one ("1200"), so `inputValue()` reads the grouped form the user
 * actually sees; `TextInput` keeps its `labelText` as the accessible name even under `hideLabel`.
 */
export class Schedule4Page {
  constructor(private readonly page: Page) {}

  // ---- readiness / navigation ---------------------------------------------------------------------

  /** The Existing Locations table — present once the schedule4 GET resolves. */
  get locationsTable(): Locator {
    return this.page.getByRole('table', { name: 'Existing Locations' });
  }

  /** The page-level action bar (Add New Location + Check Status). */
  private get actions(): Locator {
    return this.page.locator('.schedule-4__actions');
  }

  /** Open Schedule 4 via the side-nav (client-side, so the context saved on Home survives). */
  async openViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 4' });
    await expect(this.page).toHaveURL(/\/schedule-4$/);
    await expect(this.locationsTable).toBeVisible();
  }

  /**
   * Navigate for a GUARD state (closed mill 409 / no report-status row 404): the GET fails so no list
   * renders — wait on the route landing only and let the scenario assert the block message.
   */
  async openViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 4' });
    await expect(this.page).toHaveURL(/\/schedule-4/);
  }

  /**
   * Open Schedule 4 with NO working context (S15): seed an empty MillYearContext into localStorage (the
   * supported empty state — MillYearProvider.tsx) and load the route directly, so the page renders its
   * contextMissing guard without ever issuing a request.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-4');
    await expect(this.page).toHaveURL(/\/schedule-4$/);
  }

  /** Reload the current route in place — proves a save survived a full document refetch. */
  async reload(): Promise<void> {
    await this.page.reload();
    await expect(this.locationsTable).toBeVisible();
  }

  /** The page heading (the shared schedule tombstone's `<h1>`). */
  get heading(): Locator {
    return this.page.getByRole('heading', { level: 1, name: 'Schedule 4' });
  }

  /** The tombstone's breadcrumb trail under the heading (base › location › sub-page). */
  get trail(): Locator {
    return this.page.locator('.schedule-tombstone__subtitle');
  }

  // ---- the Existing Locations list ------------------------------------------------------------------

  /** Every location name currently listed, in render order. */
  async listedLocationNames(): Promise<string[]> {
    const rows = this.locationsTable.locator('tbody tr');
    if ((await rows.count()) === 0) return [];
    const cells = this.locationsTable.locator('tbody tr td:first-child');
    return (await cells.allInnerTexts()).map((text) => text.trim());
  }

  /** The list's empty-state cell (rendered instead of rows when the mill/year has no locations). */
  get emptyState(): Locator {
    return this.locationsTable.getByText('No locations have been added.', { exact: true });
  }

  /** The list row whose Location Name cell is exactly `name`. */
  locationRow(name: string): Locator {
    return this.locationsTable.locator('tbody tr').filter({
      has: this.page.getByRole('cell', { name, exact: true }),
    });
  }

  /** A per-row action button (Edit / View / Copy / Delete) on `name`'s row. */
  rowAction(name: string, action: string): Locator {
    return this.locationRow(name).getByRole('button', { name: action, exact: true });
  }

  /**
   * Rest the pointer on a location's row — for the DELIBERATE hover-state accessibility scan. The axe
   * helper parks the pointer before every other scan, so hover has to be asked for explicitly.
   */
  async hoverLocationRow(name: string): Promise<void> {
    await this.locationRow(name).hover();
  }

  /** Open a location's panel: Edit in Draft, View outside it (the same control, renamed). */
  async openLocation(name: string, action: 'Edit' | 'View' = ACTION.edit): Promise<void> {
    await this.rowAction(name, action).click();
    await expect(this.panel).toBeVisible();
  }

  /** Copy a location — opens a prefilled New panel with the name cleared (WRN-001). */
  async copyLocation(name: string): Promise<void> {
    await this.rowAction(name, ACTION.copy).click();
    await expect(this.panel).toBeVisible();
  }

  /** Click a row's Delete, which opens the confirm modal (NAV-004). */
  async clickDeleteLocation(name: string): Promise<void> {
    await this.rowAction(name, ACTION.delete).click();
    await expect(this.deleteModal).toBeVisible();
  }

  // ---- page-level actions ---------------------------------------------------------------------------

  get addNewLocationButton(): Locator {
    return this.actions.getByRole('button', { name: ACTION.addNewLocation, exact: true });
  }

  get checkStatusButton(): Locator {
    return this.actions.getByRole('button', { name: ACTION.checkStatus, exact: true });
  }

  /** Open a blank New Location panel. */
  async clickAddNewLocation(): Promise<void> {
    await this.addNewLocationButton.click();
    await expect(this.panel).toBeVisible();
  }

  async clickCheckStatus(): Promise<void> {
    await this.checkStatusButton.click();
  }

  // ---- the location panel (New / Edit / Copy / View) ------------------------------------------------

  get panel(): Locator {
    return this.page.locator('.schedule-4__panel');
  }

  /** The panel heading — also how a scenario tells New from Edit from Copy from View. */
  get panelHeading(): Locator {
    return this.panel.locator('.schedule-4__heading');
  }

  get nameInput(): Locator {
    return byId(this.page, FIELD_ID.locationName);
  }

  /** The Location Name as rendered in VIEW mode (a `<p>`, not an input). */
  get nameReadOnly(): Locator {
    return this.panel.locator('.schedule-4__field-label').first();
  }

  get commentsInput(): Locator {
    return byId(this.page, FIELD_ID.comments);
  }

  /** The comments value as rendered in VIEW mode (a `<p>`; blank renders as an em dash). */
  get commentsReadOnly(): Locator {
    return this.panel.locator('.schedule-4__comments');
  }

  /** The Carbon inline error under Location Name (ERR-001 — only shown once a Save has failed). */
  get nameError(): Locator {
    return fieldError(this.page, FIELD_ID.locationName);
  }

  async setName(value: string): Promise<void> {
    await this.nameInput.fill(value);
  }

  async setComments(value: string): Promise<void> {
    await this.commentsInput.fill(value);
  }

  /** True when the panel is editable (the name input only exists outside View mode). */
  async isPanelEditable(): Promise<boolean> {
    return (await this.nameInput.count()) > 0;
  }

  // ---- the category grid ---------------------------------------------------------------------------

  get categoryGrid(): Locator {
    return this.page.getByRole('table', { name: 'Transportation Categories' });
  }

  /** Resolve a Gherkin-facing category label ("Lakeside Dry Dump") to its legacy cost-item code. */
  private codeFor(label: string): number {
    const code = CATEGORY_CODE_BY_LABEL[label];
    if (code === undefined) {
      throw new Error(
        `unknown Schedule 4 category "${label}". Use one of: ${Object.keys(CATEGORY_CODE_BY_LABEL).join(', ')}.`,
      );
    }
    return code;
  }

  /** One editable grid cell input, addressed by its stable `#{code}-{field}` id. */
  categoryField(label: string, field: 'volume' | 'cost' | 'distance'): Locator {
    return byId(this.page, FIELD_ID.category(this.codeFor(label), field));
  }

  /** Type a value into a category cell. `''` clears it (how "left blank" is expressed on a prefill). */
  async setCategoryField(
    label: string,
    field: 'volume' | 'cost' | 'distance',
    value: string,
  ): Promise<void> {
    const input = this.categoryField(label, field);
    await input.fill(value);
    // The comma-regrouping runs on `change`; blur so the displayed value has settled before the next
    // step reads it (and so the advisory validator has seen the final value).
    await input.blur();
  }

  /** What a category cell currently DISPLAYS (grouped, as the user sees it). */
  async categoryFieldValue(label: string, field: 'volume' | 'cost' | 'distance'): Promise<string> {
    return this.categoryField(label, field).inputValue();
  }

  /** The Carbon inline error rendered under a category cell (a range message, or BR-04's marker). */
  categoryFieldError(label: string, field: 'volume' | 'cost' | 'distance'): Locator {
    return fieldError(this.page, FIELD_ID.category(this.codeFor(label), field));
  }

  /** The grid row for a category, matched on its label cell (`"Lakeside Dry Dump:"`). */
  categoryRow(label: string): Locator {
    return this.categoryGrid.locator('tbody tr').filter({
      has: this.page.getByRole('cell', { name: `${label}:`, exact: true }),
    });
  }

  /**
   * A category row's four value columns as the user sees them: [Distance, Volume, Cost, $/m³].
   *
   * A cell holding an editable input reports that input's DISPLAYED (grouped) value; a read-only cell
   * reports its text. That distinction matters — the same row renders as inputs when the schedule is
   * editable and as plain text when it is not, and both forms must be assertable by one step.
   *
   * JUSTIFIED BRANCH (the suite otherwise bans conditionals that steer a test): this one selects a READ
   * STRATEGY, never a code path through the assertion — every scenario still runs one deterministic
   * route and asserts the same expected values either way. It cannot mask a wrong render mode, because
   * which mode is in force is asserted separately and explicitly: the read-only scenarios call
   * `the Schedule 4 category grid is read-only` (which requires ZERO inputs), and the editable
   * scenarios type into those inputs, so a cell silently switching kind fails there first.
   */
  async categoryRowValues(label: string): Promise<[string, string, string, string]> {
    const cells = this.categoryRow(label).locator('td');
    const values: string[] = [];
    // Skip cell 0 (the row label); read Distance, Volume, Cost, $/m³ (cell 5 is the Cycle placeholder,
    // which a category row never carries — only a Truck Rehaul sub-page row does).
    for (let i = 1; i <= 4; i += 1) {
      const cell = cells.nth(i);
      const input = cell.locator('input');
      values.push(
        (await input.count()) > 0 ? await input.inputValue() : (await cell.innerText()).trim(),
      );
    }
    return [values[0], values[1], values[2], values[3]];
  }

  /** Every row label the grid currently renders, in order — proves the legacy row order survives. */
  async gridRowLabels(): Promise<string[]> {
    const cells = this.categoryGrid.locator('tbody tr td:first-child');
    return (await cells.allInnerTexts()).map((text) => text.trim());
  }

  /** The `id` of every editable input inside the category grid, in DOM order. */
  async editableGridFieldIds(): Promise<string[]> {
    return this.categoryGrid.locator('input').evaluateAll((els) => els.map((e) => e.id));
  }

  // ---- the sub-page group rows (inside the grid) ----------------------------------------------------

  /**
   * A sub-page group's link button inside the grid, e.g. `Towing Total (0):`. The row count is part of
   * the label (CNT-001), which is why it is matched by pattern rather than exact text.
   *
   * Rooted at `page`, NOT chained off `categoryGrid`, because this locator is also used as the `has:`
   * filter that finds the group's grid ROW — and a `filter({ has })` locator must be resolvable relative
   * to the row being filtered. One chained off another element resolves nothing there (it silently matched
   * zero rows, which is what made every "sub-page row totals" assertion time out on the first run). The
   * label pattern is unique on the page, so page-rooting costs nothing.
   */
  subPageLink(label: string): Locator {
    return this.page.getByRole('button', {
      name: new RegExp(`^${escapeRegExp(label)} \\(\\d+\\):$`),
    });
  }

  /** The live row count shown in a sub-page group's label (CNT-001). */
  async subPageCount(label: string): Promise<number> {
    const text = (await this.subPageLink(label).innerText()).trim();
    const match = /\((\d+)\):$/.exec(text);
    expect(match, `sub-page link "${text}" does not carry a "(N):" row count`).toBeTruthy();
    return Number(match![1]);
  }

  /** The grid row for a sub-page group (its label cell holds the link button). */
  subPageRow(label: string): Locator {
    return this.categoryGrid.locator('tbody tr').filter({ has: this.subPageLink(label) });
  }

  /**
   * A sub-page group row's read-only totals as displayed: [Distance, Volume, Cost, $/m³, Cycle].
   * Cycle is only ever populated for Truck Rehaul; the other two render an em dash.
   */
  async subPageRowTotals(label: string): Promise<[string, string, string, string, string]> {
    const cells = this.subPageRow(label).locator('td');
    const values: string[] = [];
    for (let i = 1; i <= 5; i += 1) {
      values.push((await cells.nth(i).innerText()).trim());
    }
    return [values[0], values[1], values[2], values[3], values[4]];
  }

  /** Click a sub-page group's link — from a panel this raises NAV-002 / NAV-003 first. */
  async clickSubPageLink(label: string): Promise<void> {
    await this.subPageLink(label).click();
  }

  /** Click the link and confirm the resulting nav modal, landing on the sub-page. */
  async openSubPage(label: string, kind: 'existing' | 'new'): Promise<void> {
    await this.clickSubPageLink(label);
    const modal = kind === 'new' ? this.navNewModal : this.navExistingModal;
    await expect(modal).toBeVisible();
    const primary = kind === 'new' ? MODAL.navNew.primary : MODAL.navExisting.primary;
    await modal.getByRole('button', { name: primary, exact: true }).click();
    await expect(this.page).toHaveURL(
      new RegExp(`sub=${subPageByLabel(label).type}`),
    );
  }

  // ---- panel actions -------------------------------------------------------------------------------

  private get panelActions(): Locator {
    return this.panel.locator('.schedule-4__panel-actions');
  }

  get saveButton(): Locator {
    return this.panelActions.getByRole('button', { name: ACTION.save, exact: true });
  }

  /** The panel's secondary button — "Back" while editable, "Close" in View mode. */
  backButton(label: 'Back' | 'Close' = ACTION.back): Locator {
    return this.panelActions.getByRole('button', { name: label, exact: true });
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click();
  }

  async clickBack(label: 'Back' | 'Close' = ACTION.back): Promise<void> {
    await this.backButton(label).click();
  }

  // ---- modals ---------------------------------------------------------------------------------------

  private modal(heading: string): Locator {
    return this.page.getByRole('dialog').filter({ hasText: heading });
  }

  /** NAV-004 — the danger confirm before a location delete. */
  get deleteModal(): Locator {
    return this.modal(MODAL.deleteLocation.heading);
  }

  /** NAV-002 — leaving a SAVED location's panel for a sub-page (unsaved edits discarded). */
  get navExistingModal(): Locator {
    return this.modal(MODAL.navExisting.heading);
  }

  /** NAV-003 — leaving an UNSAVED new location for a sub-page (must save first). */
  get navNewModal(): Locator {
    return this.modal(MODAL.navNew.heading);
  }

  /** The confirm modal's primary (destructive) button — asserted visible by the confirmation step. */
  get deleteModalPrimaryButton(): Locator {
    return this.deleteModal.getByRole('button', {
      name: MODAL.deleteLocation.primary,
      exact: true,
    });
  }

  /**
   * Confirm or dismiss whichever nav prompt a scenario named. `kind` is explicit rather than sniffed from
   * the DOM so a scenario always states which legacy prompt it expects (NAV-002 vs NAV-003).
   */
  async resolveNavPrompt(kind: 'existing' | 'new', action: 'confirm' | 'cancel'): Promise<void> {
    const modal = kind === 'new' ? this.navNewModal : this.navExistingModal;
    const labels = kind === 'new' ? MODAL.navNew : MODAL.navExisting;
    const button = action === 'confirm' ? labels.primary : labels.secondary;
    await modal.getByRole('button', { name: button, exact: true }).click();
    if (action === 'cancel') {
      await expect(modal).toBeHidden();
    }
  }

  async confirmDelete(): Promise<void> {
    await this.deleteModal
      .getByRole('button', { name: MODAL.deleteLocation.primary, exact: true })
      .click();
  }

  async cancelDelete(): Promise<void> {
    await this.deleteModal
      .getByRole('button', { name: MODAL.deleteLocation.secondary, exact: true })
      .click();
    await expect(this.deleteModal).toBeHidden();
  }

  // ---- notifications --------------------------------------------------------------------------------

  /**
   * A Carbon InlineNotification carrying `text` (title or subtitle). Used for the Save success message,
   * every Check Status line, the copy nudge and every error banner — all of which render through the
   * same Carbon InlineNotification shape.
   *
   * Addressed by ROLE, not by Carbon's `.cds--inline-notification` class: Carbon gives the notification
   * container `role="status"` for every kind (error included), and a role survives a Carbon class
   * rename where the class would not.
   */
  notification(text: string): Locator {
    return this.page.getByRole('status').filter({ hasText: text });
  }

  /** Every Check Status notification currently rendered, as its visible text. */
  async checkStatusMessages(): Promise<string[]> {
    const region = this.page.locator('.schedule-4__check');
    if ((await region.count()) === 0) return [];
    return (await region.getByRole('status').allInnerTexts()).map((text) =>
      text.replace(/\s+/g, ' ').trim(),
    );
  }
}
