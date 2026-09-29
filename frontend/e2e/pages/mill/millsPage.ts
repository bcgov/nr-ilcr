import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav, openApp } from '../common/authNav';
import { NAVIGATION_BUDGET } from '../common/settle';

/**
 * Mills (UC-MILL-001, legacy `mills.xhtml`) — the only place mill-administration DOM selectors live.
 *
 * Every locator is a role or label lookup. The page's controls are all uniquely labelled: the three
 * editable Dropdowns carry distinct titleTexts ("Head Office :", "Head Office Contact :", "Division
 * Contact :" — Carbon names the combobox after its titleText, so `exact` is what keeps "Head Office :"
 * from also matching the contact dropdown), the search dialog is a Carbon Modal named "Find and select
 * Mill", and each result row's select control is a button named `Select mill <number>`
 * (MillSearchModal.tsx), so no `nth` or CSS is needed anywhere.
 *
 * The mill identity and the audit line are plain text runs, not form fields
 * (components/mills/index.tsx `mills__identity`), so they are asserted by text inside the Mill Details
 * panel rather than by id.
 */
export class MillsPage {
  constructor(private readonly page: Page) {}

  /**
   * Open Mills the way an administrator reaches it: Home, then Administration → Mills in the side-nav.
   * The caller must have seeded the ADMIN mock user — the Administration group is `adminOnly` and is not
   * rendered for anyone else (routes/-navigation.ts).
   */
  async open(): Promise<void> {
    await openApp(this.page);
    await navigateViaSideNav(this.page, { group: 'Administration', link: 'Mills' });
    await expect(this.page).toHaveURL(/\/mills$/, { timeout: NAVIGATION_BUDGET });
    await expect(this.detailsPanel).toBeVisible();
  }

  // ---- the page ----

  /**
   * The Mill Details SubPanel — rendered unconditionally, only its contents switch (mills.xhtml:22).
   * SubPanel is a `<section aria-labelledby>` its own heading, so it is a named `region`.
   */
  get detailsPanel(): Locator {
    return this.page.getByRole('region', { name: 'Mill Details', exact: true });
  }

  get selectMillButton(): Locator {
    return this.page.getByRole('button', { name: 'Select Mill', exact: true });
  }

  get importMillButton(): Locator {
    return this.page.getByRole('button', { name: 'Import Mill', exact: true });
  }

  get changeMillButton(): Locator {
    return this.page.getByRole('button', { name: 'Change Mill', exact: true });
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  /**
   * The mill-level status action. Exactly one of the two is rendered, chosen by the status code
   * (STA-001). Scoped to the details panel and matched exactly: every user row carries its own
   * `Deactivate user <guid>` / `Activate user <guid>` button.
   */
  get deactivateButton(): Locator {
    return this.detailsPanel.getByRole('button', { name: 'Deactivate', exact: true });
  }

  get activateButton(): Locator {
    return this.detailsPanel.getByRole('button', { name: 'Activate', exact: true });
  }

  get headOfficeDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Head Office :', exact: true });
  }

  get headOfficeContactDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Head Office Contact :', exact: true });
  }

  get divisionContactDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Division Contact :', exact: true });
  }

  /** The one-line identity run: `<number> - <name>`, inside the details panel. */
  identity(millNumber: string, millName: string): Locator {
    return this.detailsPanel.getByText(`${millNumber} - ${millName}`, { exact: true });
  }

  /** A plain text run inside the details panel (the status label, the audit line's values). */
  detailsText(text: string): Locator {
    return this.detailsPanel.getByText(text, { exact: true });
  }

  // ---- the "Associated Licensee User" table ----

  /** The single applicable per-row action (mills.xhtml:136-143), named after the row's GUID. */
  userDeactivateButton(userGuid: string): Locator {
    return this.page.getByRole('button', { name: `Deactivate user ${userGuid}`, exact: true });
  }

  userActivateButton(userGuid: string): Locator {
    return this.page.getByRole('button', { name: `Activate user ${userGuid}`, exact: true });
  }

  /** A user's row, found through its own action button, and its rendered status cell. */
  userRow(userGuid: string): Locator {
    return this.page
      .getByRole('table', { name: 'Associated Licensee User' })
      .getByRole('row')
      .filter({ has: this.page.getByRole('button', { name: `View user ${userGuid}`, exact: true }) });
  }

  // ---- the "Find and select Mill" dialog ----

  get searchDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Find and select Mill' });
  }

  async openSearch(): Promise<void> {
    await this.selectMillButton.click();
    await expect(this.searchDialog).toBeVisible();
  }

  /** Search by mill number — the one criterion that identifies a single mill. */
  async searchByNumber(millNumber: string): Promise<void> {
    await this.searchDialog.getByLabel('Number:', { exact: true }).fill(millNumber);
    await this.searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  }

  /** A result row's select control. Choosing it IS the select action — there is no OK button. */
  resultRow(millNumber: string): Locator {
    return this.searchDialog.getByRole('button', { name: `Select mill ${millNumber}`, exact: true });
  }

  // ---- the editable panel ----

  /** Open a Dropdown and pick one option by its exact label. */
  async choose(dropdown: Locator, option: string): Promise<void> {
    await dropdown.click();
    await this.page.getByRole('option', { name: option, exact: true }).click();
    await expect(dropdown).toContainText(option);
  }

  /**
   * Every option a Dropdown offers, in display order. Leaves the menu closed again.
   *
   * Scoped to the dropdown's OWN listbox (Carbon labels it with the same titleText as the combobox):
   * a page-wide `getByRole('option')` also matches the header's mock-user `<select>`, whose native
   * options are always in the tree.
   */
  async optionsOf(dropdown: Locator, label: string): Promise<string[]> {
    await dropdown.click();
    const options = this.page.getByRole('listbox', { name: label, exact: true }).getByRole('option');
    await expect(options.first()).toBeVisible();
    const labels = (await options.allInnerTexts()).map((t) => t.trim());
    await this.page.keyboard.press('Escape');
    return labels;
  }
}
