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

  /** The working-context region in the tombstone header (same landmark as the Home ContextBanner). */
  get context(): Locator {
    return this.page.getByRole('region', { name: 'Working context' });
  }

  /** Open a named schedule via the side-nav "Schedules" group (client-side; keeps the saved context). */
  async open(name: string): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: name });
    // The tombstone header renders from the resolved working context regardless of the body's state (a
    // closed/empty schedule still shows the header), so the region is the readiness anchor.
    await expect(this.context).toBeVisible();
  }

  /** A tombstone context line, matched exactly (the Mill line or a track-status line). */
  contextLine(text: string): Locator {
    return this.context.getByText(text, { exact: true });
  }
}
