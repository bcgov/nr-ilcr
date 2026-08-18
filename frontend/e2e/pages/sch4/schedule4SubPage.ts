import { type Locator, type Page, expect } from '@playwright/test';
import { byId, fieldError } from '../common/carbonHelpers';
import {
  ACTION,
  FIELD_ID,
  MODAL,
  subPageByLabel,
} from '../../fixtures/sch4/schedule4-test-data';

/** The add-row / row-edit fields a sub-page carries (Cycle on Truck Rehaul only). */
export type RowField = 'description' | 'distance' | 'volume' | 'cost' | 'cycle';

/**
 * One Schedule 4 list sub-page — Towing Total (43) / Truck Rehaul-Dewater/Transfer (46, +Cycle) /
 * Other Transportation (55) — for a single location (components/schedule4/SubPage.tsx).
 *
 * RE-GROUNDING NOTE: legacy had three separate `.xhtml` views, each with its own `addXxxForm` +
 * `xxxForm` naming containers, so the same declared id (`description`, `volume`, …) existed three times
 * across three URLs. The rewrite renders ONE component parameterised by sub-page type at a URL STATE of
 * `/schedule-4` (`?loc=<id>&sub=<TYPE>`), so:
 *   - the add-row fields are one stable id set (`#subpage-description`, `#subpage-volume`, …) instead of
 *     `addTowingTotalForm:description` / `addTruckRehaulForm:description` / `addOther…:description`;
 *   - each existing row's in-place edit cells are `#row-{rowId}-{field}`, where the legacy dataTable's
 *     per-row inputs had no declared ids at all (`[UNKNOWN — no explicit id]`);
 *   - the rows table is addressed by its accessible name (`"<label> rows"`), not `[id$=":towingTotalDT"]`;
 *   - "Add" is "Add row", and the row Delete confirm is a Carbon Modal, not a `p:confirmDialog`;
 *   - Back returns to the location list by client-side navigation (browser Back works too) rather than a
 *     JSF forward carrying an `isReturningPage` flash flag.
 *
 * The running-totals footer is the legacy footer: the app sums the SERVER-derived rows for display, and
 * tracks in-progress cell edits before Save (legacy parity), which is why the totals are read from the
 * dedicated totals row rather than recomputed here.
 */
export class Schedule4SubPage {
  constructor(private readonly page: Page) {}

  /** The sub-page container — present once the `?loc=&sub=` route state renders. */
  get root(): Locator {
    return this.page.locator('.schedule-4__subpage');
  }

  /**
   * The rows table, addressed by its accessible name — which is the sub-page LABEL ("Towing Total"),
   * NOT the table's own `aria-label` ("Towing Total rows").
   *
   * Why: the table carries BOTH `aria-label="<label> rows"` and the `aria-labelledby` Carbon's
   * `TableContainer title` adds, and per the accessible-name spec `aria-labelledby` WINS — so the name
   * assistive technology (and `getByRole`) sees is the container title. Confirmed against the running
   * app 2026-08-17 via `ariaSnapshot()`: `- table "Towing Total"`. Matching on the `aria-label` string
   * silently resolves nothing. (That dead `aria-label` is filed as BUG-2 in this UC's defects.md.)
   *
   * `exact: true` because `getByRole`'s name matching is substring-by-default.
   */
  table(label: string): Locator {
    return this.page.getByRole('table', { name: label, exact: true });
  }

  /** The "Add <label>" form heading — absent (with the whole form) when the schedule is read-only. */
  addFormHeading(label: string): Locator {
    return this.root.getByRole('heading', { name: `Add ${label}` });
  }

  get addForm(): Locator {
    return this.root.locator('.schedule-4__subpage-form');
  }

  /** Wait until the sub-page for `label` has rendered. */
  async expectOpen(label: string): Promise<void> {
    await expect(this.page).toHaveURL(new RegExp(`sub=${subPageByLabel(label).type}`));
    await expect(this.table(label)).toBeVisible();
  }

  // ---- the add-row form ----------------------------------------------------------------------------

  addField(field: RowField): Locator {
    return byId(this.page, FIELD_ID.subPage(field));
  }

  /** The Carbon inline error under an add-row field (a range message, or Description's Value Required). */
  addFieldError(field: RowField): Locator {
    return fieldError(this.page, FIELD_ID.subPage(field));
  }

  async setAddField(field: RowField, value: string): Promise<void> {
    const input = this.addField(field);
    await input.fill(value);
    await input.blur();
  }

  get addRowButton(): Locator {
    return this.addForm.getByRole('button', { name: ACTION.addRow, exact: true });
  }

  async clickAddRow(): Promise<void> {
    await this.addRowButton.click();
  }

  // ---- the rows table -------------------------------------------------------------------------------

  /** The table's empty-state cell. */
  emptyState(label: string): Locator {
    return this.table(label).getByText('No rows have been added.', { exact: true });
  }

  /** How many data rows the table currently lists (the totals row is not a row of the list). */
  async rowCount(label: string): Promise<number> {
    if ((await this.emptyState(label).count()) > 0) return 0;
    return this.dataRows(label).count();
  }

  /** The data rows (everything but the totals footer), in render order. */
  private dataRows(label: string): Locator {
    return this.table(label).locator('tbody tr:not(.schedule-4__totals-row)');
  }

  /**
   * Every row's Description as displayed, in render order — the ONE place a row is identified.
   *
   * In edit mode the Description cell holds an input, so a row cannot be found by cell TEXT; it is read
   * through `inputValue()` instead. Deliberately NOT `input[value="…"]`: that matches the ATTRIBUTE, and
   * although React currently keeps it in sync (verified 2026-08-17), leaning on that would make the
   * NEGATIVE assertions ("the row is not listed") pass silently the day it stops — the exact failure mode
   * the prove-the-negative rule exists to prevent. Reading the live value cannot go stale.
   */
  async rowDescriptions(label: string): Promise<string[]> {
    const rows = this.dataRows(label);
    const count = await rows.count();
    const descriptions: string[] = [];
    for (let i = 0; i < count; i += 1) {
      const cell = rows.nth(i).locator('td').first();
      const input = cell.locator('input');
      descriptions.push(
        (await input.count()) > 0 ? await input.inputValue() : (await cell.innerText()).trim(),
      );
    }
    return descriptions;
  }

  /**
   * The row whose Description is `description`, resolved by INDEX from the live descriptions above.
   * Throws when it is absent, so a mistyped description in a `.feature` fails loudly rather than
   * matching nothing.
   */
  async row(label: string, description: string): Promise<Locator> {
    const index = (await this.rowDescriptions(label)).indexOf(description);
    if (index < 0) {
      throw new Error(
        `Schedule 4 ${label} has no row described "${description}" — listed: ${(await this.rowDescriptions(label)).join(', ') || '(none)'}`,
      );
    }
    return this.dataRows(label).nth(index);
  }

  /**
   * A row's displayed values: [Description, Distance, Volume, Cost, (Cycle,) $/m³].
   *
   * Same read-strategy branch as the main grid (an editable cell reports its input's displayed value, a
   * read-only cell its text) and justified the same way: which render mode is in force is asserted
   * separately and explicitly by the read-only scenarios.
   */
  async rowValues(label: string, description: string): Promise<string[]> {
    const cells = (await this.row(label, description)).locator('td');
    const count = await cells.count();
    // Trailing Action cell exists only in edit mode; never read it as a value.
    const valueCells = (await this.isEditable()) ? count - 1 : count;
    const values: string[] = [];
    for (let i = 0; i < valueCells; i += 1) {
      const cell = cells.nth(i);
      const input = cell.locator('input');
      values.push(
        (await input.count()) > 0 ? await input.inputValue() : (await cell.innerText()).trim(),
      );
    }
    return values;
  }

  /** One of a row's in-place edit cells (edit mode only). */
  rowField(rowId: number, field: RowField): Locator {
    return byId(this.page, FIELD_ID.row(rowId, field));
  }

  /** The Carbon inline error under an in-place edit cell (shown on Save, for touched rows only). */
  rowFieldError(rowId: number, field: RowField): Locator {
    return fieldError(this.page, FIELD_ID.row(rowId, field));
  }

  async setRowField(rowId: number, field: RowField, value: string): Promise<void> {
    const input = this.rowField(rowId, field);
    await input.fill(value);
    await input.blur();
  }

  /** True when the sub-page is editable — the add-row form only exists in Draft (SubPage.tsx). */
  async isEditable(): Promise<boolean> {
    return (await this.addForm.count()) > 0;
  }

  /** The `id` of every input inside the rows table, in DOM order (proves read-only has none). */
  async editableRowFieldIds(label: string): Promise<string[]> {
    return this.table(label)
      .locator('input')
      .evaluateAll((els) => els.map((e) => e.id));
  }

  // ---- the running-totals footer --------------------------------------------------------------------

  private totalsRow(label: string): Locator {
    return this.table(label).locator('.schedule-4__totals-row');
  }

  /** The totals footer as displayed: [Distance, Volume, Cost, (Cycle)] — the label cell is dropped. */
  async totals(label: string): Promise<string[]> {
    const cells = this.totalsRow(label).locator('td');
    const texts = (await cells.allInnerTexts()).map((t) => t.trim());
    const hasCycle = subPageByLabel(label).hasCycle;
    // cell 0 is the "Totals" label; then Distance, Volume, Cost, (Cycle), then the $/m³ + Action blanks.
    return texts.slice(1, hasCycle ? 5 : 4);
  }

  // ---- per-row delete + page actions ----------------------------------------------------------------

  async rowDeleteButton(label: string, description: string): Promise<Locator> {
    return (await this.row(label, description)).getByRole('button', {
      name: ACTION.delete,
      exact: true,
    });
  }

  /** Click a row's Delete, which opens the NAV-005 confirm modal. */
  async clickDeleteRow(label: string, description: string): Promise<void> {
    await (await this.rowDeleteButton(label, description)).click();
    await expect(this.deleteRowModal).toBeVisible();
  }

  get deleteRowModal(): Locator {
    return this.page.getByRole('dialog').filter({ hasText: MODAL.deleteRow.heading });
  }

  async confirmDeleteRow(): Promise<void> {
    await this.deleteRowModal
      .getByRole('button', { name: MODAL.deleteRow.primary, exact: true })
      .click();
  }

  private get actions(): Locator {
    return this.root.locator('.schedule-4__panel-actions');
  }

  get saveButton(): Locator {
    return this.actions.getByRole('button', { name: ACTION.save, exact: true });
  }

  get backButton(): Locator {
    return this.actions.getByRole('button', { name: ACTION.back, exact: true });
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click();
  }

  async clickBack(): Promise<void> {
    await this.backButton.click();
  }

  /** Click a sortable column header (three-state: unsorted → ascending → descending → unsorted). */
  async clickColumnHeader(label: string, column: string): Promise<void> {
    await this.table(label).getByRole('columnheader', { name: column }).click();
  }

  /** A column header's current `aria-sort` (Carbon renders it from the sort state). */
  async columnSort(label: string, column: string): Promise<string | null> {
    return this.table(label)
      .getByRole('columnheader', { name: column })
      .getAttribute('aria-sort');
  }
}
