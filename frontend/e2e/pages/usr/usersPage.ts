import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav, openApp } from '../common/authNav';
import { NAVIGATION_BUDGET } from '../common/settle';
import { type DirectoryUser, candidateLabel } from '../../fixtures/usr/users-test-data';

/**
 * Users (UC-USR-001 / UC-USR-002, legacy `users.xhtml`; route `/mill-associations`, "Users" in the
 * Administration side-nav) — the only place Users-page DOM selectors live.
 *
 * Every locator is a role or label lookup, confirmed against the running page on 2026-09-29:
 *  - the two SubPanels are `<section aria-labelledby>` their h3, so they are the named regions
 *    "User Details" and "Associated Mills";
 *  - the picker (DirectoryPicker.tsx) is two Carbon Dropdowns titled "Identity provider" / "Search by"
 *    and a ComboBox named after its criterion ("User ID" under BCeID). There is no Search button,
 *    no results table and no Select button — it is a type-ahead whose options are the candidates;
 *  - the selected user is the table "User details", whose account actions are the buttons
 *    "Activate account" / "Deactivate account";
 *  - the assignments are the table "Associated mills", each row's one action a button named
 *    `Activate mill <number>` / `Deactivate mill <number>`; adding is the Dropdown "Mill" + "Add".
 *
 * WHY THE OPTION IS SCOPED TO THE LISTBOX: the app header's mock-user selector is a native <select>
 * whose options (ILCR_ADMIN, ILCR_SUBMITTER) are also `option`s, so a page-wide option lookup is
 * ambiguous.
 */
/**
 * Stand in for the NR User Lookup directory — the ONE request a Users-page scenario does not send to the
 * real backend. The directory is off in every environment (`ilcr.user-lookup.enabled: false`: the route
 * 404s and the picker disables itself, DL-27), so without this nothing on the page is reachable. It
 * answers `GET /api/v1/users/lookup` from `users` the way the backend's BCeID lookup would — an exact
 * `userGuid` (the carry) or an exact `userId`, else an empty list (a miss, not a failure) — and records
 * every query string it saw, so a scenario can prove a search was NOT sent. Nothing else is
 * intercepted: the account, the assignments and every message are real.
 */
export async function stubUserDirectory(
  page: Page,
  users: readonly DirectoryUser[],
): Promise<URLSearchParams[]> {
  const seen: URLSearchParams[] = [];
  await page.route('**/api/v1/users/lookup**', async (route) => {
    const params = new URL(route.request().url()).searchParams;
    seen.push(params);
    const guid = params.get('userGuid');
    const userId = params.get('userId');
    const hits = users.filter(
      (u) => (guid !== null && u.userGuid === guid) || (userId !== null && u.idpUsername === userId),
    );
    await route.fulfill({ json: hits });
  });
  return seen;
}

export class UsersPage {
  constructor(private readonly page: Page) {}

  /** Home, then Administration → Users — the way an administrator reaches it (the group is admin-only). */
  async open(): Promise<void> {
    await openApp(this.page);
    await navigateViaSideNav(this.page, { group: 'Administration', link: 'Users' });
    await this.expectLoaded();
  }

  /** A direct URL open — for the carry (`?userGuid=`), which only a link from the Mills page writes. */
  async goto(search = ''): Promise<void> {
    await this.page.goto(`/mill-associations${search}`);
    await this.expectLoaded();
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/mill-associations(\?.*)?$/, { timeout: NAVIGATION_BUDGET });
    await expect(this.userDetailsPanel).toBeVisible({ timeout: NAVIGATION_BUDGET });
  }

  // ---- the page ----

  get heading(): Locator {
    return this.page.getByRole('heading', { name: 'Users', level: 1 });
  }

  get userDetailsPanel(): Locator {
    return this.page.getByRole('region', { name: 'User Details', exact: true });
  }

  get associatedMillsPanel(): Locator {
    return this.page.getByRole('region', { name: 'Associated Mills', exact: true });
  }

  // ---- the picker ----

  get identityProvider(): Locator {
    return this.page.getByRole('combobox', { name: 'Identity provider', exact: true });
  }

  /** The type-ahead. Named after its criterion: "User ID" under BCeID, and under IDIR's default. */
  get userSearch(): Locator {
    return this.userDetailsPanel.getByRole('combobox', { name: 'User ID', exact: true });
  }

  get candidates(): Locator {
    return this.page.getByRole('listbox', { name: 'User ID' }).getByRole('option');
  }

  candidate(u: DirectoryUser): Locator {
    return this.page
      .getByRole('listbox', { name: 'User ID' })
      .getByRole('option', { name: candidateLabel(u), exact: true });
  }

  /** The picker's `role="status"` note (no match / directory disabled). */
  get pickerNote(): Locator {
    return this.userDetailsPanel.getByRole('status');
  }

  async chooseBceid(): Promise<void> {
    await this.identityProvider.click();
    await this.page.getByRole('option', { name: 'BCeID Business', exact: true }).click();
    await expect(this.identityProvider).toContainText('BCeID Business');
  }

  /** Type a BCeID user ID into the type-ahead. The debounced lookup fires on its own. */
  async typeUserId(term: string): Promise<void> {
    await this.userSearch.fill(term);
  }

  // ---- the selected user ----

  get userDetails(): Locator {
    return this.page.getByRole('table', { name: 'User details', exact: true });
  }

  get activateAccountButton(): Locator {
    return this.userDetails.getByRole('button', { name: 'Activate account', exact: true });
  }

  get deactivateAccountButton(): Locator {
    return this.userDetails.getByRole('button', { name: 'Deactivate account', exact: true });
  }

  /** The body row of the User details table (the header row is `rowgroup` 1). */
  get selectedUserRow(): Locator {
    return this.userDetails.getByRole('rowgroup').nth(1).getByRole('row');
  }

  // ---- the assignments ----

  get millDropdown(): Locator {
    return this.associatedMillsPanel.getByRole('combobox', { name: 'Mill', exact: true });
  }

  get addButton(): Locator {
    return this.associatedMillsPanel.getByRole('button', { name: 'Add', exact: true });
  }

  get associatedMills(): Locator {
    return this.page.getByRole('table', { name: 'Associated mills', exact: true });
  }

  /** Every body row of the Associated mills table. */
  get assignmentRows(): Locator {
    return this.associatedMills.getByRole('rowgroup').nth(1).getByRole('row');
  }

  /** The row for one mill, found by its Mill # cell. */
  assignmentRow(millNumber: string): Locator {
    return this.assignmentRows.filter({
      has: this.page.getByRole('cell', { name: millNumber, exact: true }),
    });
  }

  activateMillButton(millNumber: string): Locator {
    return this.associatedMills.getByRole('button', { name: `Activate mill ${millNumber}`, exact: true });
  }

  deactivateMillButton(millNumber: string): Locator {
    return this.associatedMills.getByRole('button', {
      name: `Deactivate mill ${millNumber}`,
      exact: true,
    });
  }

  /** The row's "User To Mill Status" cell, matched exactly (the row's text also holds its button). */
  statusCell(millNumber: string, label: 'Active' | 'Inactive'): Locator {
    return this.assignmentRow(millNumber).getByRole('cell', { name: label, exact: true });
  }

  async addMill(optionLabel: string): Promise<void> {
    await this.millDropdown.click();
    await this.page.getByRole('option', { name: optionLabel, exact: true }).click();
    await expect(this.addButton).toBeEnabled();
    await this.addButton.click();
  }

  // ---- notifications ----

  /** A page notification (Carbon InlineNotification) by its title: Success / Warning / Error. */
  notification(title: 'Success' | 'Warning' | 'Error'): Locator {
    return this.page.getByRole('main').getByRole('status').filter({ hasText: title });
  }
}
