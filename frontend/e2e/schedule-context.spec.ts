import { expect, test, type Page } from '@playwright/test'
import { HomePage } from './pages/home'
import { CLOSED_MILL, liveDataEnabled, MSG, OPEN_MILL_WITH_STATUS } from './utils'

// Story 1.5 AC2 (the headline outcome): a context saved on Home actually DRIVES a schedule page —
// Schedule 1 operates on the selected mill/year, demonstrably NOT the 514/2021 scaffold default.
// Also carries S06's schedule-page half: a closed mill's Schedule 1 is blocked from viewing
// (its working-context display half is asserted in tombstone.spec.ts — the two tests do not overlap).

// Navigate from Home to Schedule 1 entirely client-side (TanStack Router <Link>s), so the in-memory
// MillYearContext set by the Home Save survives. A full `page.goto('/schedule-1')` would reload the
// SPA and reset the context to the 514/2021 default — which is exactly what these scenarios disprove.
// The side nav defaults to a collapsed rail and "Schedules" is a submenu, so: expand the nav (only if
// collapsed — the header button's label toggles Open/Close menu, so a blind click would collapse an
// already-open rail), expand Schedules, then click the Schedule 1 link. `exact: true` on the link:
// role-name matching is substring by default and "Schedule 1" would also match the planned
// "Schedule 10/11" items (-navigation.ts: "Schedule 2–11 will be added here").
async function gotoSchedule1(page: Page): Promise<void> {
  const openMenu = page.getByRole('button', { name: 'Open menu', exact: true })
  if (await openMenu.isVisible()) {
    await openMenu.click()
  }
  const schedules = page.getByRole('button', { name: 'Schedules', exact: true })
  if ((await schedules.getAttribute('aria-expanded')) !== 'true') {
    await schedules.click()
  }
  await page.getByRole('link', { name: 'Schedule 1', exact: true }).click()
}

test.describe('Working context drives the schedule pages (Story 1.5 AC2 / S06)', () => {
  // Live-data gate (Task 5) — see home.spec.ts. Requires the seeded delivery DB; run with
  // `E2E_LIVE_DATA=1 npm run test:e2e`. Default CI runs only the app-shell smoke.
  test.skip(
    () => !liveDataEnabled,
    'requires a seeded delivery DB — run with E2E_LIVE_DATA=1 (see story runbook)',
  )

  test('AC2: a saved context drives Schedule 1 — selected mill/year, not the 514/2021 default', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()
    await home.selectContextAndSave(OPEN_MILL_WITH_STATUS)
    await expect(home.successMessage).toBeVisible()

    // Capture the Schedule 1 GET that Schedule1's mount effect fires, and assert its query carries
    // the SELECTED mill/year — the proof the Home context replaced the hardcoded 514/2021 default
    // (the exact equalities below rule the default out by themselves).
    const scheduleRequest = page.waitForRequest('**/api/v1/schedule1*')
    await gotoSchedule1(page)
    const request = await scheduleRequest

    const params = new URL(request.url()).searchParams
    expect(params.get('millId')).toBe(String(OPEN_MILL_WITH_STATUS.millId))
    expect(params.get('year')).toBe(String(OPEN_MILL_WITH_STATUS.year))

    // The page renders the selected context in its summary (dt "Mill" → dd millId).
    const millTerm = page.getByRole('term').filter({ hasText: 'Mill' }).first()
    await expect(millTerm.locator('xpath=following-sibling::dd[1]')).toHaveText(
      String(OPEN_MILL_WITH_STATUS.millId),
    )
  })

  test('S06: a closed mill is blocked from viewing its Schedule 1', async ({ page }) => {
    const home = new HomePage(page)
    await home.goto()
    await home.selectContextAndSave(CLOSED_MILL)
    await expect(home.successMessage).toBeVisible()

    await gotoSchedule1(page)

    // millViewable:false → the schedule GET returns 409 and the page surfaces the verbatim block.
    await expect(page.getByText(MSG.closedScheduleBlocked)).toBeVisible()
  })
})
