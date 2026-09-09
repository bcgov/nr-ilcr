import { type Locator, type Page, expect } from '@playwright/test';

import { seedMockUser } from './mockUser';

/**
 * App-shell smoke page object — the persistent Layout chrome (Carbon Header, mock-user selector, primary
 * side-nav) that renders CLIENT-SIDE with NO backend / delivery-DB dependency. Mirrors the app team's
 * `nr-ilcr/frontend/e2e/pages/app-shell.ts` so this BDD suite can stand in for that data-independent CI
 * smoke. Deliberately asserts ONLY chrome — the Home form + data lives in the SEC page objects.
 *
 * Data-independence is enforced, not assumed: `openWithoutBackend` aborts every `/api` call, so the smoke
 * proves the shell renders against a frontend-only deploy (no seeded Oracle). The mock-user selector and
 * nav read client-side constants (`context/auth/mockUsers.ts`, TanStack Router), so no `/api` is needed.
 */
export class AppShellPage {
  constructor(private readonly page: Page) {}

  /** Carbon Header, aria-labelled with the app name — proves the shell mounted. */
  get header(): Locator {
    return this.page.getByRole('banner', { name: 'Interior Logging Cost Report (ILCR)' });
  }

  /** Mock-user selector — dev/security-off header chrome (client-side, not fetched). */
  get mockUserSelector(): Locator {
    return this.page.getByRole('combobox', { name: 'Mock user' });
  }

  /**
   * Open the app with the backend unreachable (all `/api` aborted); assert the header mounted.
   *
   * AS THE ADMINISTRATOR, and this scenario is the ONLY place in the suite that is: the nav
   * assertion covers `Generate Reports`, which is `adminOnly: true` in `routes/-navigation.ts`, so
   * a submitter would not render it at all. Stated here as a precondition rather than inherited
   * from whichever user `MOCK_USERS[0]` happens to be — that inheritance is exactly what silently
   * ran the whole suite as an administrator for a month (see `pages/common/mockUser.ts`). The
   * global `page` fixture seeds the submitter first; init scripts run in the order they were
   * added, so this later call wins.
   */
  async openWithoutBackend(): Promise<void> {
    await seedMockUser(this.page, 'admin');
    await this.page.route('**/api/**', (route) => route.abort());
    await this.page.goto('/');
    await expect(this.header).toBeVisible();
  }

  /**
   * Ensure the side-nav is open — only clicks if it is collapsed (the toggle flips Open/Close menu).
   * Since #316 the nav is expanded by default at Carbon's `lg` breakpoint, so at this suite's 1280px
   * viewport this is usually a no-op; the guard still matters below `lg`.
   */
  async ensureNavOpen(): Promise<void> {
    const openMenu = this.page.getByRole('button', { name: 'Open menu', exact: true });
    if (await openMenu.isVisible()) {
      await openMenu.click();
    }
  }

  /** A top-level nav link, matched exactly (so "Schedule 1" ≠ "Schedule 10/11"). */
  navLink(name: string): Locator {
    return this.page.getByRole('link', { name, exact: true });
  }

  /** A nav group toggle (e.g. the "Schedules" submenu), matched exactly. */
  navGroup(name: string): Locator {
    return this.page.getByRole('button', { name, exact: true });
  }
}
