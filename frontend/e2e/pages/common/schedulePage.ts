import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from './authNav';

/**
 * A schedule page's shared "tombstone" header (components/core/ScheduleTombstone) — the two-column
 * header every schedule renders, whose right column repeats the saved working context (mill line + both
 * track-status lines) inside the SAME `region[name="Working context"]` landmark the Home ContextBanner
 * uses (both render the shared `WorkingContextLines`, so the expected text is identical). Used to prove a
 * context saved on Home DISPLAYS on the schedule pages. Navigation is client-side (preserves the
 * in-memory MillYearContext). Common because the tombstone is shared across every schedule domain.
 */
export class SchedulePage {
  constructor(private readonly page: Page) {}

  /**
   * The working-context region in the tombstone header. SCOPED TO THE TOMBSTONE deliberately: Home's
   * PageTitle-hosted ContextBanner renders the SAME `region[name="Working context"]` landmark with the
   * SAME `WorkingContextLines` text, so an unscoped role query is satisfied by Home too and every
   * "the tombstone shows …" assertion would pass without ever leaving Home.
   */
  get context(): Locator {
    return this.page.locator('.schedule-tombstone').getByRole('region', { name: 'Working context' });
  }

  /** Open a named schedule via the side-nav "Schedules" group (client-side; keeps the saved context). */
  async open(name: string): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: name });
    // Prove we actually LANDED on the target schedule route before trusting the working-context region:
    // Home renders the SAME `region[name="Working context"]` with the SAME mill/status lines (after a
    // Home Save), so without this a nav that silently stayed on Home would let the tombstone assertions
    // pass falsely (PR #5 review). Route slug: "Schedule 2" -> "/schedule-2".
    const slug = name.toLowerCase().replace(/\s+/g, '-');
    await expect(this.page).toHaveURL(new RegExp(`/${slug}$`));
    // THE URL IS NOT ENOUGH, and this is the readiness anchor that matters.
    //
    // Client-side navigation flips the URL BEFORE the route's content swaps: while the target route
    // resolves, the router keeps the previous page mounted, so for a window the location reads
    // `/schedule-2` while the DOM is still Home — Home's banner satisfying both the URL check above and
    // the region below. The a11y sweep in this feature then scanned Home and reported Home's authored
    // welcome-message contrast (defects.md BUG-1) as a Schedule 2 violation: an untagged red that
    // failed roughly one run in four under parallel load (measured 2/8, 2026-08-26).
    //
    // The heading is route-specific — every schedule's ScheduleTombstone renders `<h1>{title}</h1>`,
    // where Home's PageTitle h1 reads "Mill and Reporting Year" — so it cannot be satisfied by the
    // outgoing page, which is exactly what the URL and the shared landmark both fail to guarantee.
    await expect(this.page.getByRole('heading', { level: 1, name })).toBeVisible();
    // The tombstone header renders from the resolved working context regardless of the body's state (a
    // closed/empty schedule still shows the header), so the region is the readiness anchor.
    await expect(this.context).toBeVisible();
  }

  /** A tombstone context line, matched exactly (the Mill line or a track-status line). */
  contextLine(text: string): Locator {
    return this.context.getByText(text, { exact: true });
  }
}
