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
    // Same budget as the URL: under a loaded full-suite run the page can take longer than the 10 s
    // default to render after the route resolves (seen 2026-09-29 in a full-suite gate run).
    await expect(this.detailsPanel).toBeVisible({ timeout: NAVIGATION_BUDGET });
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

  /** A user's rendered "User To Mill Status" cell — exactly "Active" or "Inactive". */
  userStatusCell(userGuid: string, label: 'Active' | 'Inactive'): Locator {
    return this.userRow(userGuid).getByRole('cell', { name: label, exact: true });
  }

  /** A user's row, found through its own action button. */
  userRow(userGuid: string): Locator {
    return this.page
      .getByRole('table', { name: 'Associated Licensee User' })
      .getByRole('row')
      .filter({ has: this.page.getByRole('button', { name: `View user ${userGuid}`, exact: true }) });
  }

  userViewButton(userGuid: string): Locator {
    return this.page.getByRole('button', { name: `View user ${userGuid}`, exact: true });
  }

  /** The panel's own Add — the only button named exactly "Add" on the page. */
  get addUserButton(): Locator {
    return this.page
      .getByRole('region', { name: 'Associated Licensee User', exact: true })
      .getByRole('button', { name: 'Add', exact: true });
  }

  /** Every GUID listed in the licensee table, read off each row's View control. */
  async listedUserGuids(): Promise<string[]> {
    const views = this.page
      .getByRole('table', { name: 'Associated Licensee User' })
      .getByRole('button', { name: /^View user / });
    const labels = await views.evaluateAll((els) => els.map((e) => e.getAttribute('aria-label') ?? ''));
    return labels.map((l) => l.replace(/^View user /, '')).sort();
  }

  // ---- the "Find and Add User" dialog (S05 / S07) ----

  get addUserDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Find and Add User' });
  }

  /**
   * Find a BCeID Business user by user ID and choose them — choosing IS the add (no OK button).
   * The directory behind the picker must be stubbed first (see `stubDirectory`).
   */
  async addUserByBceid(userId: string, optionLabel: string): Promise<void> {
    await this.addUserButton.click();
    await expect(this.addUserDialog).toBeVisible();
    await this.addUserDialog.getByRole('combobox', { name: 'Identity provider' }).click();
    await this.page.getByRole('option', { name: 'BCeID Business', exact: true }).click();
    await this.addUserDialog.getByRole('combobox', { name: 'User ID' }).fill(userId);
    await this.page.getByRole('option', { name: optionLabel, exact: true }).click();
  }

  // ---- the "Find and select Mill to Import" dialog (S02 / S14) ----

  get importDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Find and select Mill to Import' });
  }

  async openImport(): Promise<void> {
    await this.importMillButton.click();
    await expect(this.importDialog).toBeVisible();
  }

  async searchImportByNumber(millNumber: string): Promise<void> {
    await this.importDialog.getByLabel('Number:', { exact: true }).fill(millNumber);
    await this.importDialog.getByRole('button', { name: 'Search', exact: true }).click();
  }

  /** The icon-only Import control in a result row, named after the mill. */
  importRow(millNumber: string): Locator {
    return this.importDialog.getByRole('button', { name: `Import mill ${millNumber}`, exact: true });
  }

  /** CNF-001 — the screen's only confirmation. */
  get confirmDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Confirmation' });
  }

  // ---- the "Find and select Mill" dialog ----

  /** The search dialog's result table — absent when a search returned nothing. */
  get searchResults(): Locator {
    return this.searchDialog.getByRole('table', { name: 'Mill search results' });
  }

  /** Search by name AND status (the Status dropdown's own labels: "Any", "Active", "Close"). */
  async searchByNameAndStatus(millName: string, status: string): Promise<void> {
    await this.searchDialog.getByLabel('Number:', { exact: true }).fill('');
    await this.searchDialog.getByLabel('Name:', { exact: true }).fill(millName);
    await this.searchDialog.getByRole('combobox', { name: 'Status:' }).click();
    await this.page.getByRole('option', { name: status, exact: true }).click();
    await this.searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  }

  /** The Status column of every result row, in display order. */
  async listedStatuses(): Promise<string[]> {
    await expect(this.searchResults).toBeVisible();
    const rows = this.searchResults.locator('tbody').getByRole('row');
    return (await rows.evaluateAll((trs) =>
      trs.map((tr) => (tr.querySelectorAll('td')[2]?.textContent ?? '').trim()),
    ));
  }

  async searchImportByName(millName: string): Promise<void> {
    await this.importDialog.getByLabel('Number:', { exact: true }).fill('');
    await this.importDialog.getByLabel('Name:', { exact: true }).fill(millName);
    await this.importDialog.getByRole('button', { name: 'Search', exact: true }).click();
  }

  /** Search by name, which the Number field does not constrain. */
  async searchByName(millName: string): Promise<void> {
    await this.searchDialog.getByLabel('Number:', { exact: true }).fill('');
    await this.searchDialog.getByLabel('Name:', { exact: true }).fill(millName);
    await this.searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  }

  get searchDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Find and select Mill' });
  }

  async openSearch(): Promise<void> {
    await this.selectMillButton.click();
    await expect(this.searchDialog).toBeVisible();
  }

  /** Search by mill number — the one criterion that identifies a single mill. */
  async searchByNumber(millNumber: string): Promise<void> {
    // The criteria AND together, so a Name left from an earlier search would silently narrow this one.
    await this.searchDialog.getByLabel('Name:', { exact: true }).fill('');
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

/** One directory record, in the backend's `DirectoryUser` shape. */
type DirectoryRecord = { userGuid: string; idpUsername: string };

/**
 * Stand in for the NR User Lookup directory — the ONE request a mills scenario does not send to the
 * real backend. The directory is off in every environment (`ilcr.user-lookup.enabled: false`, so the
 * endpoint 404s and the picker disables itself), which leaves Find and Add User and View's carried
 * user unreachable without it. The stub answers `GET /api/v1/users/lookup` from `users` exactly as the
 * backend would: an exact `userGuid` lookup or an exact BCeID `userId`, else an empty list (a miss,
 * not a failure). Nothing else is intercepted — the add, the lists and every message are real.
 */
export async function stubDirectory(page: Page, users: readonly DirectoryRecord[]): Promise<void> {
  await page.route('**/api/v1/users/lookup**', async (route) => {
    const url = new URL(route.request().url());
    const guid = url.searchParams.get('userGuid');
    const userId = url.searchParams.get('userId');
    const hits = users.filter(
      (u) => (guid !== null && u.userGuid === guid) || (userId !== null && u.idpUsername === userId),
    );
    await route.fulfill({ json: hits });
  });
}

/** The Mill Associations page ("Users" in the side-nav) — only what S10 lands on and reads. */
export class MillAssociationsPage {
  constructor(private readonly page: Page) {}

  get userDetails(): Locator {
    return this.page.getByRole('table', { name: 'User details' });
  }

  /** A row of the selected user's Associated Mills table, found by its mill-number action. */
  associatedMill(millNumber: string): Locator {
    return this.page
      .getByRole('table', { name: 'Associated mills' })
      .getByRole('button', { name: new RegExp(`^(Deactivate|Activate) mill ${millNumber}$`) });
  }
}
