import { test } from '@playwright/test'
import { app_shell } from './pages/app-shell'

// Reconciled for Story 1.5 (Task 2): the former `qsos.spec.ts` asserted the removed Dashboard at
// `/`; renamed and retargeted to the app-shell smoke (header + mock-user selector + primary nav)
// that survived Story 1.3's Dashboard→Home swap. Data-independent by design — this is the one suite
// the default CI e2e job runs without the E2E_LIVE_DATA gate. The Home page's own behavior lives in
// home.spec.ts.
test.describe.parallel('App shell', () => {
  test('App shell renders header and primary navigation', async ({ page }) => {
    await app_shell(page)
  })
})
