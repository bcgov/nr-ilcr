import { expect, test, type Page } from '@playwright/test'
import { HomePage } from './pages/home'
import { SchedulePage } from './pages/schedule'
import { expectNoA11yViolations } from './utils/axe'
import {
  bannerMillLine,
  CLOSED_MILL,
  expectedStatusLines,
  liveDataEnabled,
  MILL_NO_STATUS,
  type MillFixture,
  OPEN_MILL_ALT,
  OPEN_MILL_WITH_STATUS,
} from './utils'

// Story 1.5 (banner → tombstone): the working-context mill/status lines a Home Save establishes now
// display on the schedule pages' ScheduleTombstone header (the global ContextBanner was removed). Each
// scenario saves a context on Home, then navigates client-side into Schedule 2 (so the in-memory
// MillYearContext survives) and asserts the tombstone's context block. These port the former Home
// banner scenarios S01 (display) / S03 / S06 / S07 to where the lines now render.
test.describe('Working-context tombstone on the schedule pages (Story 1.5)', () => {
  // Live-data gate — see home.spec.ts. Requires the seeded delivery DB; run with
  // `E2E_LIVE_DATA=1 npm run test:e2e`. Default CI runs only the app-shell smoke.
  test.skip(
    () => !liveDataEnabled,
    'requires a seeded delivery DB — run with E2E_LIVE_DATA=1 (see story runbook)',
  )

  // Save a context on Home, then open Schedule 2 (whose header carries the tombstone). The Home Save's
  // SUC-001 is the settle point before the client-side nav that preserves the context.
  async function saveAndOpenSchedule2(page: Page, mill: MillFixture): Promise<SchedulePage> {
    const home = new HomePage(page)
    await home.goto()
    await home.selectContextAndSave(mill)
    await expect(home.successMessage).toBeVisible()
    const schedule = new SchedulePage(page)
    await schedule.open('Schedule 2')
    return schedule
  }

  test('S01 (display): an established context renders the mill line + both track statuses; a11y clean', async ({
    page,
  }) => {
    const schedule = await saveAndOpenSchedule2(page, OPEN_MILL_WITH_STATUS)

    // The mill line + both status lines (Sch 1-10 carries a real date, Sch 11 falls back to
    // "Not Initiated") — expected texts come from the fixture module.
    await expect(
      schedule.context.getByText(bannerMillLine(OPEN_MILL_WITH_STATUS), { exact: true }),
    ).toBeVisible()
    for (const line of expectedStatusLines(OPEN_MILL_WITH_STATUS)) {
      await expect(schedule.context.getByText(line, { exact: true })).toBeVisible()
    }

    await expectNoA11yViolations(page, 'Schedule 2 — tombstone with working context')
  })

  test('S03: switching the context replaces the tombstone lines; no stale data remains', async ({
    page,
  }) => {
    await saveAndOpenSchedule2(page, OPEN_MILL_WITH_STATUS)

    // Re-establish a DIFFERENT context on Home and reopen the schedule.
    const home = new HomePage(page)
    await home.goto()
    await home.selectContextAndSave(OPEN_MILL_ALT)
    await expect(home.successMessage).toBeVisible()
    const schedule = new SchedulePage(page)
    await schedule.open('Schedule 2')

    // Replacement, not just removal: the new mill line AND the new context's status lines render.
    await expect(
      schedule.context.getByText(bannerMillLine(OPEN_MILL_ALT), { exact: true }),
    ).toBeVisible()
    for (const line of expectedStatusLines(OPEN_MILL_ALT)) {
      await expect(schedule.context.getByText(line, { exact: true })).toBeVisible()
    }

    // The previous selection's mill line and its dated Sch 1-10 line are gone. (Only the DATED line
    // is absence-checked: the undated Sch 11 "Draft / Not Initiated" line is textually identical
    // across these Draft contexts, so absence-asserting it would false-fail against the new header.)
    await expect(
      page.getByText(bannerMillLine(OPEN_MILL_WITH_STATUS), { exact: true }),
    ).toHaveCount(0)
    await expect(
      page.getByText(expectedStatusLines(OPEN_MILL_WITH_STATUS)[0], { exact: true }),
    ).toHaveCount(0)
  })

  test('S06: a closed mill renders its tombstone exactly like an open mill', async ({ page }) => {
    const schedule = await saveAndOpenSchedule2(page, CLOSED_MILL)

    // The schedule BODY may be blocked (millViewable:false → 409), but the header tombstone still
    // renders the mill line, with no closed-mill-specific text (AC4 parity carried to the tombstone).
    await expect(
      schedule.context.getByText(bannerMillLine(CLOSED_MILL), { exact: true }),
    ).toBeVisible()
    await expect(schedule.context.getByText(/closed|not viewable|CLS/i)).toHaveCount(0)
  })

  test('S07: a no-status-row pair renders the Mill line only', async ({ page }) => {
    const schedule = await saveAndOpenSchedule2(page, MILL_NO_STATUS)

    await expect(
      schedule.context.getByText(bannerMillLine(MILL_NO_STATUS), { exact: true }),
    ).toBeVisible()
    // Both track-status lines are suppressed (no ILCR_MILL_REPORT_STATUS row) — no error either.
    await expect(schedule.context.getByText(/^Sch 1-10 - Status:/)).toHaveCount(0)
    await expect(schedule.context.getByText(/^Sch 11 - Status:/)).toHaveCount(0)
  })
})
