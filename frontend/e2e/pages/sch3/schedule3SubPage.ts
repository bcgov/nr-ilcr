import { type Locator, type Page, expect } from '@playwright/test';
import { ROUTE_SCHEDULE_3 } from '../../fixtures/sch3/schedule3-test-data';

/**
 * The two Schedule 3 cost sub-pages — Other Costs (`/schedule-3/other-acceptable-costs`) and Included
 * Unacceptable Costs (`/schedule-3/included-unacceptable-costs`). ONE page object serves both, because
 * the rewrite renders them from a single generic component (`components/schedule3SubPage`) configured
 * per page; only the table's accessible name and the numeric columns differ. Duplicating this per
 * sub-page would duplicate every selector for no behavioural difference.
 *
 * SELECTOR CONTRACT (`components/schedule3SubPage/index.tsx`)
 *   `#add-description` / `#add-total` / `#add-pop`   the Add panel (`#add-pop` only on Other Costs)
 *   `#row-description-<key>` / `#row-total-<key>` / `#row-pop-<key>`   the in-place row inputs, where
 *       `<key>` is a CLIENT-side mount counter, not the server id — so rows are addressed by ROW ORDER
 *       resolved from their description, exactly as the sch1 Other Costs page object does.
 *   Row inputs are Carbon `hideLabel` TextInputs, so their accessible names are "Edit description",
 *       "Edit total" and "Edit PO&P".
 *   `#annualRentsS111`   the read-only Annual Rents (Forest Act, S111) figure (Unacceptable page only).
 *
 * BEHAVIOUR THE STEPS DEPEND ON (`hooks/useEditableCostRows`): Add, Remove and Save each persist the
 * WHOLE row set in one PUT (the server reconciles insert/update/delete) — Add and Remove immediately,
 * Save on demand. A row that fails the advisory validation is NOT sent at all: the inline error renders
 * and no request is made, which is what the zero-write assertions prove.
 */
export class Schedule3SubPage {
  constructor(private readonly page: Page) {}

  /** The rows table, addressed by the accessible name its page config supplies. */
  table(title: string): Locator {
    return this.page.getByRole('table', { name: title });
  }

  /** Readiness anchor after navigating in from Schedule 3. */
  async expectLoaded(title: string): Promise<void> {
    await expect(this.table(title)).toBeVisible();
  }

  // ---- the Add panel -----------------------------------------------------------------------------

  get addDescription(): Locator {
    return this.page.locator('#add-description');
  }

  get addTotal(): Locator {
    return this.page.locator('#add-total');
  }

  /** Other Costs only — the Included Unacceptable page has a single numeric column. */
  get addPop(): Locator {
    return this.page.locator('#add-pop');
  }

  get addButton(): Locator {
    return this.page.getByRole('button', { name: 'Add', exact: true });
  }

  /**
   * Fill the Add panel and submit. A blank cell is still typed (so the field is cleared rather than
   * left holding a previous value), and every numeric field is blurred, which is what re-groups it and
   * commits it to the footer mirror.
   */
  async addRow(fields: { description: string; total?: string; pop?: string }): Promise<void> {
    await this.addDescription.fill(fields.description);
    if (fields.total !== undefined) {
      await this.addTotal.fill(fields.total);
      await this.addTotal.blur();
    }
    if (fields.pop !== undefined) {
      await this.addPop.fill(fields.pop);
      await this.addPop.blur();
    }
    await this.addButton.click();
  }

  // ---- the listed rows ---------------------------------------------------------------------------

  /**
   * The live description values currently listed. On an editable schedule each row renders its
   * description as a controlled TextInput, so the value lives on the input property — not as row text —
   * and must be read via the DOM value (a `hasText` match would miss it entirely).
   */
  async descriptions(title: string): Promise<string[]> {
    const inputs = this.table(title).getByRole('textbox', { name: 'Edit description' });
    return inputs.evaluateAll((els) => els.map((e) => (e as HTMLInputElement).value));
  }

  /** Row descriptions as TEXT — the read-only render, where rows are plain cells rather than inputs. */
  async readOnlyDescriptions(title: string): Promise<string[]> {
    const cells = this.table(title).locator('tbody tr td:first-child');
    const texts = await cells.allInnerTexts();
    // Drop the "Totals" footer row, which shares the same first-column position.
    return texts.map((t) => t.trim()).filter((t) => t !== 'Totals' && t !== 'No records found.');
  }

  /** Row order for a listed description — the stable handle, since row ids are client-side counters. */
  private async rowIndex(title: string, description: string): Promise<number> {
    const index = (await this.descriptions(title)).indexOf(description);
    expect(index, `sub-page row "${description}" not found in "${title}"`).toBeGreaterThanOrEqual(0);
    return index;
  }

  /** A listed row's numeric input value (`total` / `pop`), separators stripped. */
  async rowValue(title: string, description: string, field: 'total' | 'pop'): Promise<string> {
    const index = await this.rowIndex(title, description);
    const name = field === 'total' ? 'Edit total' : 'Edit PO&P';
    const input = this.table(title).getByRole('textbox', { name }).nth(index);
    return (await input.inputValue()).replaceAll(',', '');
  }

  /** Edit a listed row's numeric value in place (persisted later by Save). */
  async editRowValue(
    title: string,
    description: string,
    field: 'total' | 'pop',
    value: string,
  ): Promise<void> {
    const index = await this.rowIndex(title, description);
    const name = field === 'total' ? 'Edit total' : 'Edit PO&P';
    const input = this.table(title).getByRole('textbox', { name }).nth(index);
    await input.fill(value);
    await input.blur();
  }

  /** Edit a listed row's description in place (persisted later by Save). */
  async editRowDescription(title: string, description: string, next: string): Promise<void> {
    const index = await this.rowIndex(title, description);
    await this.table(title).getByRole('textbox', { name: 'Edit description' }).nth(index).fill(next);
  }

  /**
   * A listed row's derived read-only cell (Crown $ on Other Costs) as rendered text. Read positionally:
   * the derived columns follow the editable ones and carry no id.
   */
  async rowDerivedCell(title: string, description: string, columnLabel: string): Promise<string> {
    const index = await this.rowIndex(title, description);
    const headers = await this.table(title).locator('thead th').allInnerTexts();
    const column = headers.findIndex((h) => h.trim().startsWith(columnLabel));
    expect(column, `no "${columnLabel}" column in "${title}" (have: ${headers.join(' | ')})`).toBeGreaterThan(0);
    const cell = this.table(title).locator('tbody tr').nth(index).locator('td').nth(column);
    return ((await cell.textContent()) ?? '').trim().replaceAll(',', '');
  }

  /** The per-row delete — an icon-only "Remove" button that persists the whole set immediately. */
  async removeRow(title: string, description: string): Promise<void> {
    const index = await this.rowIndex(title, description);
    await this.table(title).getByRole('button', { name: 'Remove' }).nth(index).click();
  }

  /**
   * The "Totals" footer cells (the numeric columns, left to right), separators stripped.
   *
   * The leading label cell is dropped, and so is the trailing EMPTY cell the editable render adds to
   * line the footer up with the per-row Action column — otherwise every assertion on this row would
   * have to carry a meaningless trailing blank, and the same footer would read differently on the
   * read-only render (which has no Action column).
   */
  async totalsCells(title: string): Promise<string[]> {
    const row = this.table(title).locator('tbody tr').filter({ hasText: 'Totals' });
    await expect(row, `no Totals footer row in "${title}"`).toHaveCount(1);
    const texts = await row.locator('td').allInnerTexts();
    const cells = texts.slice(1).map((t) => t.trim().replaceAll(',', ''));
    while (cells.length > 0 && cells[cells.length - 1] === '') {
      cells.pop();
    }
    return cells;
  }

  /** The empty-state cell the table renders with no rows. */
  emptyState(title: string): Locator {
    return this.table(title).getByText('No records found.', { exact: true });
  }

  /** The read-only Annual Rents (Forest Act, S111) figure — Included Unacceptable Costs page only. */
  get annualRentsS111(): Locator {
    return this.page.locator('#annualRentsS111');
  }

  async annualRentsS111Value(): Promise<string> {
    return (await this.annualRentsS111.inputValue()).replaceAll(',', '');
  }

  // ---- actions -----------------------------------------------------------------------------------

  /** Persist the whole edited row set. Disabled while saving and while the table is empty. */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  get backButton(): Locator {
    return this.page.getByRole('button', { name: 'Back', exact: true }).first();
  }

  /** The unsaved-edits guard shown by Back after an in-place edit (`modalHeading="Leave page"`). */
  get leaveDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Leave page' });
  }

  /**
   * The three halves of the dirty-Back guard, exposed separately so `subpage-back.feature` (GAP-3) can
   * assert the WARNING itself, then Cancel, then Continue. `back()` below is the one-shot form used by
   * scenarios that only need to get out.
   */
  async pressBack(): Promise<void> {
    await this.backButton.click();
  }

  /** Dismiss the "Leave page" guard and stay put, edit intact. */
  async cancelLeave(): Promise<void> {
    await this.leaveDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(this.leaveDialog).toBeHidden();
  }

  /** Accept the "Leave page" guard: discard the un-persisted edit and navigate back. */
  async confirmLeave(): Promise<void> {
    await this.leaveDialog.getByRole('button', { name: 'Continue', exact: true }).click();
    await expect(this.page).toHaveURL(new RegExp(`${ROUTE_SCHEDULE_3}$`));
  }

  /** Back to Schedule 3. `confirmLeave` handles the dirty case (an un-persisted in-place edit). */
  async back(opts: { confirmLeave?: boolean } = {}): Promise<void> {
    await this.backButton.click();
    if (opts.confirmLeave) {
      await expect(this.leaveDialog).toBeVisible();
      await this.leaveDialog.getByRole('button', { name: 'Continue', exact: true }).click();
    }
    await expect(this.page).toHaveURL(new RegExp(`${ROUTE_SCHEDULE_3}$`));
  }

  /**
   * ANY open dialog on the sub-page. Used by the DIV-5 red, which asserts that removing a row asks for
   * confirmation first — deliberately NOT pinned to a particular heading or body text, because the
   * chrome a fix would use is the developer's choice (the repo already has `ConfirmDeleteModal`). What
   * the legacy guarantee requires is that SOMETHING asks before the row is destroyed.
   */
  get anyDialog(): Locator {
    return this.page.getByRole('dialog');
  }

  /** The inline validation message under an Add-panel field (Carbon `invalidText`). */
  addFieldError(message: string): Locator {
    return this.page.getByText(message, { exact: true });
  }
}
