import { type Locator, type Page } from '@playwright/test'

// Page object for a schedule page's ScheduleTombstone header. The working-context mill/status lines
// that used to live in the global banner (removed) now render in the tombstone's right column.
export class SchedulePage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // The tombstone's working-context block (right column of the two-column schedule header) — a labelled
  // region, so it is addressable by role (matching the role/label locators used elsewhere in the suite).
  get context(): Locator {
    return this.page.getByRole('region', { name: 'Working context' })
  }

  // Client-side nav from Home into a schedule via the side nav, so the in-memory MillYearContext set
  // by the Home Save survives — a full page.goto would reload the SPA and reset it to the scaffold
  // default. Expand the collapsed rail (only if collapsed — the header toggle flips an open rail
  // shut), then the Schedules submenu, then click the link. `exact: true`: "Schedule 1" would
  // substring-match "Schedule 11" otherwise. Mirrors schedule-context.spec.ts's gotoSchedule1.
  async open(name: string): Promise<void> {
    const openMenu = this.page.getByRole('button', { name: 'Open menu', exact: true })
    if (await openMenu.isVisible()) {
      await openMenu.click()
    }
    const schedules = this.page.getByRole('button', { name: 'Schedules', exact: true })
    if ((await schedules.getAttribute('aria-expanded')) !== 'true') {
      await schedules.click()
    }
    await this.page.getByRole('link', { name, exact: true }).click()
  }
}
