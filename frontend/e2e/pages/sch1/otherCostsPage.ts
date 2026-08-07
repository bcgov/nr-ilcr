import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Subtotal Other Costs sub-page (components/schedule1OtherCosts/index.tsx), reached from the main
 * Schedule 1 page. Add form is `#add-description` / `#add-cost` / the "Add" button (rendered only when
 * editable). Rows live in the aria-labelled "Other Cost List" table; on an editable Draft each row's
 * description is an inline TextInput (accessible name "Edit description") and the per-row delete is an
 * icon-only "Remove" button that persists the whole set IMMEDIATELY with no confirm modal (bcgov's
 * EditableSubPage rewrite dropped the legacy per-row confirm). Selectors live here, never in steps.
 */
export class OtherCostsPage {
  constructor(private readonly page: Page) {}

  /** The aria-labelled rows table — present once the sub-page GET resolves. */
  get table(): Locator {
    return this.page.getByRole('table', { name: 'Other Cost List' });
  }

  get addDescription(): Locator {
    return this.page.locator('#add-description');
  }

  get addCost(): Locator {
    return this.page.locator('#add-cost');
  }

  get addButton(): Locator {
    return this.page.getByRole('button', { name: 'Add', exact: true });
  }

  get backButton(): Locator {
    return this.page.getByRole('button', { name: 'Back to Schedule 1' });
  }

  /** Readiness anchor after navigating in from Schedule 1. */
  async expectLoaded(): Promise<void> {
    await expect(this.table).toBeVisible();
  }

  /** Fill the add form and submit (S09/S10/S11). Blank cells are still typed (to clear). */
  async addRow(description: string, cost: string): Promise<void> {
    await this.addDescription.fill(description);
    await this.addCost.fill(cost);
    await this.addButton.click();
  }

  /**
   * The live description values currently listed. On an editable Draft each row renders its description
   * as a controlled TextInput (name "Edit description"), so the value lives on the input property — not
   * as row text — and must be read via the DOM value (a `hasText` / `[value=]` match would miss it).
   */
  async descriptions(): Promise<string[]> {
    const inputs = this.table.getByRole('textbox', { name: 'Edit description' });
    return inputs.evaluateAll((els) => els.map((e) => (e as HTMLInputElement).value));
  }

  /**
   * Remove a listed row by its description. The per-row action is now an icon-only "Remove" button that
   * deletes immediately (no confirm modal). Description inputs and Remove buttons share row order, so the
   * value's index selects the matching Remove button.
   */
  async deleteRow(text: string): Promise<void> {
    const idx = (await this.descriptions()).indexOf(text);
    expect(idx, `Other Cost row "${text}" not found to delete`).toBeGreaterThanOrEqual(0);
    await this.table.getByRole('button', { name: 'Remove' }).nth(idx).click();
  }

  /** The per-row Save action (EditableSubPageLayout) — persists the WHOLE row set with `intent=save`. */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  /**
   * Row order for a listed description. Both inline inputs and the Remove button share row order, so an
   * index resolved from the description values addresses the whole row. Rows carry client-generated ids
   * (`#row-description-<key>`), and the key is a mount-order counter, not the server id — so the index
   * is the stable way in, not the id.
   */
  private async rowIndex(description: string): Promise<number> {
    const idx = (await this.descriptions()).indexOf(description);
    expect(idx, `Other Cost row "${description}" not found`).toBeGreaterThanOrEqual(0);
    return idx;
  }

  /** Edit a listed row's description in place (the row set is persisted later by Save). */
  async editRowDescription(description: string, next: string): Promise<void> {
    const idx = await this.rowIndex(description);
    await this.table.getByRole('textbox', { name: 'Edit description' }).nth(idx).fill(next);
  }

  /** Edit a listed row's cost in place (the row set is persisted later by Save). */
  async editRowCost(description: string, next: string): Promise<void> {
    const idx = await this.rowIndex(description);
    await this.table.getByRole('textbox', { name: 'Edit cost' }).nth(idx).fill(next);
  }

  /** Persist the whole edited row set (legacy `save()` → update(true)). */
  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async backToSchedule1(): Promise<void> {
    await this.backButton.click();
  }
}
