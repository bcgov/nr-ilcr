import { type Page, expect } from '@playwright/test';
import { NAVIGATION_BUDGET } from './settle';

/**
 * Cross-domain browser-interaction helpers — app entry, side-nav, and Carbon date entry. No domain
 * vocabulary, so every page object reuses them from here.
 *
 * ILCR runs with security OFF locally (`ilcr.security.enabled=false`): a backend MockPrincipalFilter
 * seeds the principal and a frontend mock-user selector drives the UI, so the app opens ALREADY
 * authenticated with NO `/welcome` login page. There is therefore no SCS-style IDIR login dance —
 * "logging in" is just opening the app. Navigation is TanStack Router (client-side <Link>s), so we
 * navigate by clicking nav links, NOT via history.pushState (that is React-Router-specific).
 */

/** The app name rendered as the Carbon Header aria-label — proves the shell mounted. */
const APP_NAME = 'Interior Logging Cost Reports (ILCR)';

/**
 * Open the app at Home (`/`). Security is off, so no login step is needed.
 *
 * The shell assertion gets the NAVIGATION budget (30 s), not the default 10 s `expect` timeout: this is a
 * first-paint readiness check, not a state assertion. The Vite dev server compiles on demand, so with
 * several workers opening the SPA at once a cold first paint can exceed 10 s — observed once as a lone
 * "element(s) not found" on the app banner during a 137-scenario parallel run, which reproduced nowhere
 * afterwards. Waiting longer for the FIRST render is the honest fix; a retry would just have hidden it.
 * Every assertion after this one keeps the strict default, so a genuine hang still fails fast.
 */
export async function openApp(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('banner', { name: APP_NAME })).toBeVisible({ timeout: NAVIGATION_BUDGET });
}

/**
 * Navigate the primary side-nav (client-side, so the in-memory MillYearContext set on Home survives —
 * a full page.goto() would reload the SPA and reset it). A top-level item is a link, a group (e.g.
 * "Schedules") is an expandable button holding the schedule links. Reused by every schedule domain.
 *
 * Since #316 the nav is EXPANDED BY DEFAULT at Carbon's `lg` breakpoint (>=1056px) and stays open
 * across navigation, so at this suite's pinned 1280px viewport the "Open menu" branch below is
 * normally skipped. The guard is kept, not deleted: it still applies below `lg`, and it is what let
 * this helper survive the #316 default change without an edit.
 */
export async function navigateViaSideNav(
  page: Page,
  opts: { group?: string; link: string },
): Promise<void> {
  // The header toggle's label flips Open/Close menu, so only click it when the nav is collapsed —
  // a blind click would collapse an already-open nav (which, since #316, is the default at lg+).
  const openMenu = page.getByRole('button', { name: 'Open menu', exact: true });
  if (await openMenu.isVisible()) {
    await openMenu.click();
  }
  if (opts.group) {
    const group = page.getByRole('button', { name: opts.group, exact: true });
    if ((await group.getAttribute('aria-expanded')) !== 'true') {
      await group.click();
    }
  }
  // `exact` so e.g. "Schedule 1" doesn't also match a future "Schedule 10"/"Schedule 11".
  await page.getByRole('link', { name: opts.link, exact: true }).click();
}

/**
 * Commit a value into a Carbon DatePicker input: click, type, then Escape (dismiss the calendar
 * overlay) and blur (onBlur reads the typed value). Shared across every domain's date fields.
 */
export async function setDateField(page: Page, id: string, value: string): Promise<void> {
  const input = page.locator(id);
  await input.click();
  await input.fill(value);
  await page.keyboard.press('Escape');
  await input.blur();
}
