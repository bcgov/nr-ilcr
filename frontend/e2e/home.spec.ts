import { expect, test } from '@playwright/test'
import { HomePage } from './pages/home'
import { expectNoA11yViolations } from './utils/axe'
import {
  bannerMillLine,
  CLOSED_MILL,
  expectedStatusLines,
  liveDataEnabled,
  MILL_NO_STATUS,
  millOptionText,
  MSG,
  OPEN_MILL_ALT,
  OPEN_MILL_WITH_STATUS,
} from './utils'

// Story 1.5 — post-implementation (AD-10) verification of the Home flow (UC-SEC-001 S01–S08)
// against the running app on the seeded delivery-extract Oracle. These specs ASSERT observed
// behavior of the already-built 1.1–1.4 code; they are not a red phase.
//
// No data teardown: the Home "Save" is a read/resolve (GET /api/v1/mill-context) — it writes NO
// report rows — so, unlike the team e2e-testing skill's write-page guidance, these tests need no
// row cleanup. Do not add spurious teardown here.
test.describe('Home page — working context (Story 1.5)', () => {
  // Live-data gate (Task 5): these scenarios need the app running on the seeded delivery DB (see the
  // story runbook). They are gated behind E2E_LIVE_DATA so the default CI e2e job — which points at a
  // deployed URL whose data is not guaranteed to hold these pinned fixtures — runs only the
  // data-independent app-shell smoke (app-shell.spec.ts) and stays green. Run the manual gate with
  // `E2E_LIVE_DATA=1 npm run test:e2e`.
  test.skip(
    () => !liveDataEnabled,
    'requires a seeded delivery DB — run with E2E_LIVE_DATA=1 (see story runbook)',
  )

  test('S01 (land): dropdowns populate from the list endpoints; a11y clean on the empty page', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()

    await expect(home.millDropdown).toBeVisible()
    await expect(home.yearDropdown).toBeVisible()
    await expect(home.saveButton).toBeVisible()

    // Populate proof (AC1 bullet 1), not just visibility: each list actually contains its options.
    // Open → assert a known fixture option → close again (toggle), so the axe scan below runs
    // against the settled, closed-menu page state.
    await home.millDropdown.click()
    await expect(
      page.getByRole('option', { name: millOptionText(OPEN_MILL_WITH_STATUS), exact: true }),
    ).toBeVisible()
    await home.millDropdown.click()
    await home.yearDropdown.click()
    await expect(
      page.getByRole('option', { name: String(OPEN_MILL_WITH_STATUS.year), exact: true }),
    ).toBeVisible()
    await home.yearDropdown.click()

    // No banner before a Save: the default 514/2021 context resolves to no mill in this data.
    // (goto() awaited the mount-time mill-context response, so this is a settled state, not a race.)
    await expect(home.banner).toHaveCount(0)

    await expectNoA11yViolations(page, 'Home page — initial/empty state')
  })

  test('S01 (save): save establishes the context — SUC-001 + populated banner; a11y clean', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()

    await home.selectContextAndSave(OPEN_MILL_WITH_STATUS)

    // SUC-001, verbatim from the API (AD-8).
    await expect(home.successMessage).toBeVisible()
    // Banner populates with the mill line + both track-status lines (Sch 1-10 carries a real date,
    // Sch 11 falls back to "Not Initiated") — expected texts come from the fixture module.
    await expect(home.banner).toBeVisible()
    await expect(
      home.banner.getByText(bannerMillLine(OPEN_MILL_WITH_STATUS), { exact: true }),
    ).toBeVisible()
    for (const line of expectedStatusLines(OPEN_MILL_WITH_STATUS)) {
      await expect(home.banner.getByText(line, { exact: true })).toBeVisible()
    }

    await expectNoA11yViolations(page, 'Home page — banner populated after Save')
  })

  test('S03: change context and re-save — banner and statuses replace; no stale data remains', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()

    await home.selectContextAndSave(OPEN_MILL_WITH_STATUS)
    await expect(
      home.banner.getByText(bannerMillLine(OPEN_MILL_WITH_STATUS), { exact: true }),
    ).toBeVisible()

    await home.selectContextAndSave(OPEN_MILL_ALT)
    await expect(home.successMessage).toBeVisible()
    // Replacement, not just removal: the new mill line AND the new context's status lines render.
    await expect(
      home.banner.getByText(bannerMillLine(OPEN_MILL_ALT), { exact: true }),
    ).toBeVisible()
    for (const line of expectedStatusLines(OPEN_MILL_ALT)) {
      await expect(home.banner.getByText(line, { exact: true })).toBeVisible()
    }

    // The previous selection's mill line and its dated Sch 1-10 line are gone. (Only the DATED line
    // is absence-checked: the undated Sch 11 "Draft / Not Initiated" line is textually identical
    // across these Draft contexts, so absence-asserting it would false-fail against the
    // replacement banner.)
    await expect(
      home.banner.getByText(bannerMillLine(OPEN_MILL_WITH_STATUS), { exact: true }),
    ).toHaveCount(0)
    await expect(
      page.getByText(expectedStatusLines(OPEN_MILL_WITH_STATUS)[0], { exact: true }),
    ).toHaveCount(0)
  })

  test('S04: mill missing → Save blocked with the verbatim message (year present)', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()

    // Precondition (data-drift tripwire): the default-context mill (514) must be ABSENT from the
    // mill list, so the Mill dropdown is still on its placeholder — that emptiness is this
    // scenario's premise. If a re-seed ever adds mill id 514, this fails HERE with a clear message
    // instead of as a confusing "save succeeded" timeout below.
    await expect(home.millDropdown).toContainText('Select Mill')

    // Select a year but leave the Mill on its placeholder. (The Year dropdown also auto-fills to the
    // default-context year 2021 on landing — see the S05/S08 note below — so a year is present either
    // way; selecting one just makes the fixture explicit.)
    await home.selectYear(OPEN_MILL_WITH_STATUS.year)
    await home.save()

    await expect(home.fieldError(MSG.millRequired)).toBeVisible()
    // Only the mill message — the year is present, so its required message must NOT appear.
    await expect(home.fieldError(MSG.yearRequired)).toHaveCount(0)
    await expect(home.banner).toHaveCount(0)
  })

  // S05 (year missing) and S08 (both missing) are NOT reproducible through the UI on the delivery
  // data. On a fresh Home load the Reporting Year dropdown auto-selects the default MillYearContext
  // year (2021), which IS a real reporting year, and Carbon `Dropdown` offers no clear-to-placeholder
  // control — so the empty-year state can't be produced by clicking. The empty-mill/default-514 does
  // stay empty (mill 514 isn't in the list), which is why S04 above IS reachable. The year-required
  // and both-missing backend validation is verified two other ways:
  //   • Contract: GET /api/v1/mill-context?millId=12050&year= → "Reporting Year: Value is required.";
  //     ?millId=&year= → BOTH messages (captured in Story 1.5 Task 1 / Dev Agent Record).
  //   • Unit: Story 1.3 Vitest injects an empty context to exercise both paths
  //     (Home.test.tsx "Save on placeholders" and "missing year only" tests).
  // This is observed behavior, not a defect — the backend stays authoritative.
  test.skip('S05: year missing — not reproducible via UI (year auto-fills from default context)', () => {})
  test.skip('S08: both missing — not reproducible via UI (year auto-fills from default context)', () => {})

  // S02 (single-mill pre-select) is NOT reproducible on the delivery data (21-mill list) — recorded
  // not-reproducible per Pinned Decision 4; covered by Story 1.3 Vitest with a 1-mill MSW list.
  test.skip('S02: single-mill pre-select — not reproducible on delivery data (see story)', () => {})

  // S06 is split across two files: the BANNER half (closed mill saves + banners like an open mill)
  // is asserted HERE; the schedule-page BLOCK half (millViewable:false → 409) is asserted in
  // schedule-context.spec.ts, which re-saves the closed context itself. Neither test duplicates the
  // other — do not delete this one as "covered elsewhere".
  test('S06 (banner half): a closed mill saves and banners exactly like an open mill', async ({
    page,
  }) => {
    const home = new HomePage(page)
    await home.goto()

    await home.selectContextAndSave(CLOSED_MILL)

    await expect(home.successMessage).toBeVisible()
    await expect(home.banner).toBeVisible()
    await expect(home.banner.getByText(bannerMillLine(CLOSED_MILL), { exact: true })).toBeVisible()
  })

  test('S07: a no-status-row pair saves; banner shows the Mill line only', async ({ page }) => {
    const home = new HomePage(page)
    await home.goto()

    await home.selectContextAndSave(MILL_NO_STATUS)

    await expect(home.successMessage).toBeVisible()
    await expect(home.banner).toBeVisible()
    await expect(
      home.banner.getByText(bannerMillLine(MILL_NO_STATUS), { exact: true }),
    ).toBeVisible()
    // Both track-status lines are suppressed (no ILCR_MILL_REPORT_STATUS row) — no error either.
    await expect(home.banner.getByText(/^Sch 1-10 - Status:/)).toHaveCount(0)
    await expect(home.banner.getByText(/^Sch 11 - Status:/)).toHaveCount(0)
  })
})
