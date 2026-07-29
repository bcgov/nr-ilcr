import { expect, type Page } from '@playwright/test'
import { baseURL } from '../utils'

// App-shell smoke (Story 1.5 Task 2 — reconciled from the former `dashboard.ts`).
//
// Story 1.3 replaced the `/` Dashboard with the Home (Mill and Reporting Year) page, so the old
// Dashboard-BODY assertions are retired: "ILCR Workspace" and the user-details card ("User ID" /
// "Name" / "Email" / the ILCR_ADMIN role text) no longer exist at `/`. What SURVIVES the swap is the
// persistent Layout chrome — the app header/name, the mock-user selector (header chrome, not
// Dashboard body), and the primary navigation — which is what this smoke guards. The Home FORM
// itself is covered by home.spec.ts (page object pages/home.ts); this file intentionally stays a
// shell-only check so the two concerns don't overlap.
export const app_shell = async (page: Page) => {
  await page.goto(baseURL)

  // Persistent header (Carbon Header aria-label = the app name) — unchanged by the Dashboard→Home swap.
  await expect(
    page.getByRole('banner', { name: 'Interior Logging Cost Reports (ILCR)' }),
  ).toBeVisible()

  // Mock-user selector — header chrome that survives the swap (dev-only, security-off principal).
  await expect(page.getByRole('combobox', { name: 'Mock user' })).toBeVisible()

  // Primary navigation. The side nav defaults to a collapsed rail; expand it only when collapsed
  // (the header button's label toggles Open/Close menu — a blind click would collapse an open rail),
  // then confirm the top-level items. NOTE the nav changed with Home: the old "Dashboard" link is
  // now "Home", and a "Schedules" submenu was added (Schedule 1 lives under it).
  const openMenu = page.getByRole('button', { name: 'Open menu', exact: true })
  if (await openMenu.isVisible()) {
    await openMenu.click()
  }
  await expect(page.getByRole('link', { name: 'Home', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Schedules', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Submissions', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mill Associations', exact: true })).toBeVisible()
}
