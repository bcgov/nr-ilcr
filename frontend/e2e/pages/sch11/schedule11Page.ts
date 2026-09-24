import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import {
  ALL_FLAGGED_TEXT,
  CONFIRM_DELETE,
  EMPTY_TABLE_TEXT,
  MILL_YEAR_STORAGE_KEY,
  type BecOption,
} from '../../fixtures/sch11/schedule11-test-data';

/**
 * Schedule 11 — Report Basic Silviculture Costs (components/schedule11/index.tsx). All Schedule 11 DOM
 * knowledge lives here; steps carry domain vocabulary only.
 *
 * RE-GROUNDING NOTE — the legacy Gherkin's locators do not survive the rewrite, so none are used:
 *   - route `/schedule-11` reached via Home + side-nav, not `/ext/ilcr/schedule11.xhtml`
 *   - stable Carbon ids (`#add-location`), not PrimeFaces naming-container ids
 *     (`addLocationForm:addDescription`)
 *   - since Story 26.2 the page is legacy's model again: EVERY row is a live input while the page is
 *     editable, Delete only FLAGS a row, and one page-level Save — above AND below the table, as legacy's
 *     `btnSaveTop`/`btnSave` — sends the edits and flags in one PUT. Add is still its own immediate POST.
 *     (defects.md DIV-1 / DIV-3, closed.)
 *   - TWO Check Status buttons (top and bottom), disabled while a change is unsaved (26.2 D7(a)).
 *   - the delete confirm is a Carbon Modal ("Delete"/"Cancel"), not PrimeFaces
 *     `.ui-confirmdialog-yes`/`-no`, and not a native browser dialog.
 *
 * Carbon specifics: `Dropdown` and `ComboBox` both expose `role="combobox"` named by their
 * titleText/aria-label; options are `role="option"` once open. Inputs keep their `labelText` as the
 * accessible name even under `hideLabel`, which is why each row's controls are addressable by name:
 * the page names them after the row's SERVED location ("Actual Cost ($) for E2E S03 edit"), so they
 * stay unambiguous with many rows on screen and do not change while the Location is being retyped.
 */
export class Schedule11Page {
  constructor(private readonly page: Page) {}

  // ---- readiness / navigation -----------------------------------------------------------------

  /** The locations table — present once the schedule11 GET resolves. */
  get table(): Locator {
    return this.page.getByRole('table', { name: 'Silviculture Locations' });
  }

  /** Open Schedule 11 via the side-nav (client-side, so the context saved on Home survives). */
  async openViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 11' });
    await expect(this.page).toHaveURL(/\/schedule-11$/);
    await expect(this.table).toBeVisible();
  }

  /**
   * Navigate for a GUARD state (S12 closed mill / S13 not found): the GET fails so no table renders —
   * wait on the route landing only and let the scenario assert the block message.
   */
  async openViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 11' });
    await expect(this.page).toHaveURL(/\/schedule-11$/);
  }

  /**
   * Open Schedule 11 with NO working context (S11): seed an empty MillYearContext into localStorage
   * (the supported empty state — MillYearProvider.tsx) and load the route directly, so the page
   * renders its contextMissing guard without ever issuing a request.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-11');
    await expect(this.page).toHaveURL(/\/schedule-11$/);
  }

  /** Reload the current route in place — proves a write survived without a separate Save (S09). */
  async reload(): Promise<void> {
    await this.page.reload();
    await expect(this.table).toBeVisible();
  }

  // ---- Add New Location panel ---------------------------------------------------------------------

  get addLocation(): Locator {
    return this.page.locator('#add-location');
  }

  get addNetArea(): Locator {
    return this.page.locator('#add-net-area');
  }

  get addActualCost(): Locator {
    return this.page.locator('#add-actual-cost');
  }

  get addPlannedCost(): Locator {
    return this.page.locator('#add-planned-cost');
  }

  get addComments(): Locator {
    return this.page.locator('#add-comments');
  }

  get addButton(): Locator {
    return this.page.getByRole('button', { name: 'Add', exact: true });
  }

  get addPanelHeading(): Locator {
    return this.page.getByRole('heading', { name: 'Add New Location' });
  }

  /** Both Check Status buttons — above and below the table. */
  get checkStatusButtons(): Locator {
    return this.page.getByRole('button', { name: 'Check Status', exact: true });
  }

  /** The TOP Check Status button, the one a user reaches first. */
  get checkStatusButton(): Locator {
    return this.checkStatusButtons.first();
  }

  /** Both page-level Save buttons — above and below the table. */
  get saveButtons(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  /** Save from the top bar. */
  async save(): Promise<void> {
    await this.saveButtons.first().click();
  }

  /** Save from the bottom bar — legacy's `btnSave` under the table. */
  async saveFromBottom(): Promise<void> {
    await this.saveButtons.last().click();
  }

  /** The Enhanced Yes/No Dropdown in the Add panel (titleText "Enhanced"). */
  get addEnhanced(): Locator {
    return this.page.getByRole('combobox', { name: 'Enhanced', exact: true });
  }

  /** The BEC type-ahead in the Add panel (titleText "Biogeo/Subzone/Variant"). */
  get addBec(): Locator {
    return this.page.getByRole('combobox', { name: 'Biogeo/Subzone/Variant', exact: true });
  }

  /** Pick Yes/No in a Carbon Dropdown addressed by its accessible name. */
  private async pickEnhanced(control: Locator, value: 'Yes' | 'No'): Promise<void> {
    await control.click();
    await this.page.getByRole('option', { name: value, exact: true }).click();
  }

  /**
   * Resolve a BEC option through the forced-selection type-ahead (BR-09): type the query, wait for the
   * server-filtered suggestion to arrive (250 ms debounce + round-trip — `expect` auto-retries rather
   * than a fixed wait), then CHOOSE it. Only a chosen option yields an id, so this is the only way a
   * valid row can be built.
   */
  private async pickBec(control: Locator, option: BecOption): Promise<void> {
    await control.click();
    await control.fill(option.query);
    const suggestion = this.page.getByRole('option', { name: option.label, exact: true });
    await expect(suggestion).toBeVisible();
    await suggestion.click();
  }

  async setAddEnhanced(value: 'Yes' | 'No'): Promise<void> {
    await this.pickEnhanced(this.addEnhanced, value);
  }

  async setAddBec(option: BecOption): Promise<void> {
    await this.pickBec(this.addBec, option);
  }

  /**
   * Type a prefix into the BEC field, WAIT for real suggestions to appear, and deliberately choose none
   * (S16). Waiting for the populated list is the load-bearing part: the slice's condition is free text
   * typed *while suggestions exist*, never picked. A term that matched nothing would leave the list empty
   * and never exercise forced selection at all.
   *
   * The open listbox is then dismissed with Escape so it cannot overlay the Add button — Carbon's
   * ComboBox closes the menu on Escape without selecting, which leaves the selection null exactly as the
   * slice requires.
   */
  async typeAddBecWithoutSelecting(prefix: string, expectedOption: string): Promise<void> {
    await this.addBec.click();
    await this.addBec.fill(prefix);
    await expect(
      this.page.getByRole('option', { name: expectedOption, exact: true }),
      `BEC suggestions should populate for "${prefix}" — S16 must reject free text while real options exist`,
    ).toBeVisible();
    await this.page.keyboard.press('Escape');
  }

  /**
   * Fill the Add panel. Every field is optional so the rejection scenarios can omit exactly one; a
   * field left `undefined` is not touched at all (so "left empty" really means untouched).
   */
  async fillAddPanel(values: {
    location?: string;
    enhanced?: 'Yes' | 'No';
    bec?: BecOption;
    /** A prefix typed into the BEC field but never chosen from (S16). */
    becFreeText?: string;
    /** A suggestion `becFreeText` must surface, so we can prove the list really populated. */
    becFreeTextOption?: string;
    netArea?: string;
    actualCost?: string;
    plannedCost?: string;
    comments?: string;
  }): Promise<void> {
    if (values.location !== undefined) {
      await this.addLocation.fill(values.location);
    }
    if (values.enhanced !== undefined) {
      await this.setAddEnhanced(values.enhanced);
    }
    if (values.bec !== undefined) {
      await this.setAddBec(values.bec);
    }
    if (values.becFreeText !== undefined) {
      await this.typeAddBecWithoutSelecting(values.becFreeText, values.becFreeTextOption ?? '');
    }
    if (values.netArea !== undefined) {
      await this.addNetArea.fill(values.netArea);
    }
    if (values.actualCost !== undefined) {
      await this.addActualCost.fill(values.actualCost);
    }
    if (values.plannedCost !== undefined) {
      await this.addPlannedCost.fill(values.plannedCost);
    }
    if (values.comments !== undefined) {
      await this.addComments.fill(values.comments);
    }
  }

  async clickAdd(): Promise<void> {
    await this.addButton.click();
  }

  // ---- rows ---------------------------------------------------------------------------------------

  /**
   * The data row for the location SERVED as `location`. An editable row holds its name in an input
   * named "Location for <served name>"; a read-only row renders it as the first cell's text. Matched
   * either way, scoped to the table body and to the FIRST cell, so a value that also appears in Comments
   * cannot select the wrong row; the footer "Totals" row and the placeholder row are excluded by
   * construction.
   */
  row(location: string): Locator {
    return this.table.locator('tbody tr').filter({
      has: this.page
        .locator('td')
        .first()
        .getByText(location, { exact: true })
        .or(this.page.getByRole('textbox', { name: `Location for ${location}`, exact: true })),
    });
  }

  /** Every Location value currently listed, in render order — the input's value on an editable row. */
  async listedLocations(): Promise<string[]> {
    const cells = this.table.locator('tbody tr:not(.schedule-11__totals) td:first-child');
    const texts = await cells.evaluateAll((tds) =>
      tds.map((td) => (td.querySelector('input') as HTMLInputElement | null)?.value ?? td.textContent ?? ''),
    );
    // Drop the placeholder row, which occupies the same first-cell position — both texts it can carry.
    // Matched against the pinned constants rather than a hand-typed prefix: with a local literal, a change
    // to the placeholder copy would silently make rowCount() return 1 for an empty table.
    return texts
      .map((t) => t.trim())
      .filter((t) => t !== '' && t !== EMPTY_TABLE_TEXT && t !== ALL_FLAGGED_TEXT);
  }

  /** Count of real data rows (excludes the footer Totals row and the empty-state placeholder). */
  async rowCount(): Promise<number> {
    return (await this.listedLocations()).length;
  }

  /**
   * Read one row's cell by column label, as the user sees it: an input's (or textarea's) VALUE on an
   * editable row — the raw number the user types over, "5500", not a mask — the Dropdown's selected
   * label for Enhanced, and otherwise the cell's OWN text: the formatted server figure ("10,000"). Own
   * text only, so an original-value indicator's tooltip inside the cell can never leak into the value.
   */
  async cell(location: string, column: Sch11Column): Promise<string> {
    const index = SCH11_COLUMN_INDEX[column];
    return this.row(location)
      .locator('td')
      .nth(index)
      .evaluate((td) => {
        const field = td.querySelector('input, textarea') as HTMLInputElement | HTMLTextAreaElement | null;
        if (field) {
          return field.value.trim();
        }
        const selected = td.querySelector('.cds--list-box__label');
        if (selected) {
          return (selected.textContent ?? '').trim();
        }
        return Array.from(td.childNodes)
          .filter((node) => node.nodeType === Node.TEXT_NODE)
          .map((node) => node.textContent ?? '')
          .join('')
          .trim();
      });
  }

  // ---- row editing (S03) — every row is live; nothing is written until Save ------------------------

  /** A row's text control, by its per-row accessible name ("NAR(ha) for <served location>"). */
  rowField(location: string, field: Sch11RowField): Locator {
    return this.page.getByRole('textbox', { name: `${field} for ${location}`, exact: true });
  }

  rowEnhanced(location: string): Locator {
    return this.page.getByRole('combobox', { name: `Enhanced for ${location}`, exact: true });
  }

  rowBec(location: string): Locator {
    return this.page.getByRole('combobox', {
      name: `Biogeo/Subzone/Variant for ${location}`,
      exact: true,
    });
  }

  async setRowEnhanced(location: string, value: 'Yes' | 'No'): Promise<void> {
    await this.pickEnhanced(this.rowEnhanced(location), value);
  }

  async setRowBec(location: string, option: BecOption): Promise<void> {
    await this.pickBec(this.rowBec(location), option);
  }

  // ---- delete (S07 / S08) -------------------------------------------------------------------------

  /** Open the delete confirm modal for a row. Confirming it only FLAGS the row until Save. */
  async clickDelete(location: string): Promise<void> {
    await this.row(location).getByRole('button', { name: 'Delete', exact: true }).click();
    await expect(this.confirmModal).toBeVisible();
  }

  /** The Carbon confirm Modal (NOT a PrimeFaces confirmDialog, NOT a native browser dialog). */
  get confirmModal(): Locator {
    return this.page.getByRole('dialog').filter({ hasText: CONFIRM_DELETE.body });
  }

  get confirmModalHeading(): Locator {
    return this.page.getByText(CONFIRM_DELETE.heading, { exact: true });
  }

  /** Confirm the delete (modal primary action). */
  async confirmDelete(): Promise<void> {
    await this.confirmModal
      .getByRole('button', { name: CONFIRM_DELETE.primary, exact: true })
      .click();
  }

  /** Dismiss the delete confirm without deleting (modal secondary action). */
  async cancelDelete(): Promise<void> {
    await this.confirmModal
      .getByRole('button', { name: CONFIRM_DELETE.secondary, exact: true })
      .click();
    await expect(this.confirmModal).toBeHidden();
  }

  // ---- footer totals (BR-08 / CNT-001) ------------------------------------------------------------

  get totalsRow(): Locator {
    return this.table.locator('tr.schedule-11__totals');
  }

  /** One footer total by column label, as the page formats it (server-computed; null renders blank). */
  async total(column: Sch11Column): Promise<string> {
    const index = SCH11_COLUMN_INDEX[column];
    return (await this.totalsRow.locator('td').nth(index).innerText()).trim();
  }

  // ---- read-only render (S20) ---------------------------------------------------------------------

  /** The Actions column header — rendered only when the schedule is editable. */
  get actionsHeader(): Locator {
    return this.table.getByRole('columnheader', { name: 'Actions', exact: true });
  }

  /** The empty-table placeholder. */
  emptyPlaceholder(text: string): Locator {
    return this.table.getByText(text, { exact: true });
  }

  /** A block/guard message anywhere on the page (ERR-001/002/003 render verbatim). */
  blocked(text: string): Locator {
    return this.page.getByText(text);
  }

  // ---- Check Status result (raw text, for genuinely verbatim assertions) --------------------------

  /** The Check Status result region (`schedule-11__check`) — present once a check has run. */
  get checkResult(): Locator {
    return this.page.locator('.schedule-11__check');
  }

  /**
   * The RAW `textContent` of the Check Status result region — deliberately NOT whitespace-normalized.
   *
   * WHY THIS EXISTS: Playwright's text matchers (`getByText`, `toContainText`) normalize runs of
   * whitespace, which makes the FLD-004 literal DOUBLE space in `"location  : …"` physically
   * unassertable through them — a regression collapsing it to one space would pass. Reading
   * `textContent` straight out of the DOM is the only way to pin it. (This gap was caught by the
   * 2026-07-30 review of the earlier, since-removed 25.4 attempt; see the story's Review Findings.)
   */
  async checkResultRawText(): Promise<string> {
    return this.checkResult.evaluate((el) => el.textContent ?? '');
  }
}

/** The Locations table's columns, in render order (COLUMNS in components/schedule11/index.tsx). */
/** The text controls of a live row, named as the page labels them. */
export type Sch11RowField =
  | 'Location'
  | 'NAR(ha)'
  | 'Actual Cost ($)'
  | 'Planned Cost ($)'
  | 'Comments';

export type Sch11Column =
  | 'Location'
  | 'Biogeo/Subzone/Variant'
  | 'ES'
  | 'NAR(ha)'
  | 'Actual Cost ($)'
  | 'Planned Cost ($)'
  | 'Total Act Plus Plan Cost ($)'
  | 'Total/NAR(ha)'
  | 'Comments';

/**
 * Column label → cell index. Kept beside the page object (not in a step file) because it is DOM
 * structure: the order mirrors `COLUMNS`, which the component asserts is legacy-verbatim.
 */
const SCH11_COLUMN_INDEX: Record<Sch11Column, number> = {
  Location: 0,
  'Biogeo/Subzone/Variant': 1,
  ES: 2,
  'NAR(ha)': 3,
  'Actual Cost ($)': 4,
  'Planned Cost ($)': 5,
  'Total Act Plus Plan Cost ($)': 6,
  'Total/NAR(ha)': 7,
  Comments: 8,
};
