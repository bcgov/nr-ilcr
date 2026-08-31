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

  /**
   * The sub-page Back action. Re-grounded 2026-08-07: the restyle (#237) shortened the label from
   * "Back to Schedule 1" to "Back" (`schedule1OtherCosts/index.tsx` `backLabel="Back"`), so it is
   * matched exactly rather than by substring.
   *
   * `.first()` is belt-and-braces, not a disambiguator: `EditableSubPageLayout` renders `backLabel` in
   * two places — the error-state branch (`index.tsx:101`, inside `if (errorDetail)`) and the normal
   * action row (`:138`) — but those are MUTUALLY EXCLUSIVE returns, so only ever one is in the DOM.
   * (An earlier version of this comment claimed a top and a bottom row; that was wrong.)
   */
  get backButton(): Locator {
    return this.page.getByRole('button', { name: 'Back', exact: true }).first();
  }

  /**
   * ANY dialog on the sub-page. Used by S12 (DIV-3 / bcgov#362) to assert that removing a row asks for
   * confirmation first — deliberately NOT pinned to a particular heading or body text, because the
   * chrome a fix would use is the developer's choice (the repo already has `ConfirmDeleteModal`). What
   * the legacy guarantee requires is that SOMETHING asks before the row is destroyed. Same shape as
   * `Schedule3SubPage.anyDialog`, since one fix in the shared hook turns both suites green.
   */
  get anyDialog(): Locator {
    return this.page.getByRole('dialog');
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

  /** A listed row, addressed by its current description (rows share order across all their cells). */
  private async row(description: string): Promise<Locator> {
    return this.table.locator('tbody tr').nth(await this.rowIndex(description));
  }

  /**
   * Which editable fields a listed row exposes, as their id KIND with the row-key suffix stripped
   * (`row-description-3` -> `row-description`). BR-06 says the Other-Costs volume is shared and NOT
   * editable per row, so this must be exactly description + cost — proving the absence by listing what
   * IS there, rather than asserting a volume locator finds nothing (which would also "pass" if the row
   * had failed to render at all).
   *
   * Identified by id, not accessible name: Carbon's `hideLabel` renders a visually-hidden `<label>`
   * associated by `for`/`id` rather than setting `aria-label`, and the ids are the same stable contract
   * the rest of this page object already addresses rows by.
   */
  async rowEditableFieldKinds(description: string): Promise<string[]> {
    const cells = (await this.row(description)).getByRole('textbox');
    const ids = await cells.evaluateAll((els) => els.map((e) => e.getAttribute('id') ?? ''));
    return ids.map((id) => id.replace(/-\d+$/, ''));
  }

  /**
   * The per-row Volume cell's rendered text. Legacy showed the shared volume in a DISABLED input here;
   * the React app renders it as plain text (`fmtNumber(volume)`), so it is read as text, not a value.
   * Either way the BR-06 guarantee — the row cannot carry its own volume — holds.
   */
  async rowVolumeText(description: string): Promise<string> {
    const cell = (await this.row(description)).locator('td').nth(1);
    return ((await cell.textContent()) ?? '').trim();
  }

  async backToSchedule1(): Promise<void> {
    await this.backButton.click();
  }
}
