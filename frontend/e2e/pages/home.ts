import { expect, type Locator, type Page } from '@playwright/test'
import { baseURL, millOptionText, MSG, type MillFixture } from '../utils'

// Page object for the Home (Mill and Reporting Year) page — components/home/index.tsx.
// Carbon-aware role locators per the story's Task 4: the two `Dropdown`s expose comboboxes named by
// their titleText; options render as role="option" once open; the Save confirmation and
// field-required errors are Carbon InlineNotifications whose verbatim subtitle text is the
// API-owned message (AD-8).
export class HomePage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  get millDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Mill', exact: true })
  }

  get yearDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Reporting Year', exact: true })
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save' })
  }

  // The SUC-001 confirmation (success InlineNotification subtitle, verbatim from the API).
  get successMessage(): Locator {
    return this.page.getByText(MSG.saved)
  }

  // A field-required error (error InlineNotification subtitle, verbatim from the 400 body).
  fieldError(text: string): Locator {
    return this.page.getByText(text)
  }

  // The working-context banner (ContextBanner.tsx) — a labelled region, so it is addressable by role.
  get banner(): Locator {
    return this.page.getByRole('region', { name: 'Working context' })
  }

  async goto(): Promise<void> {
    // Settle point for the banner's mount-time fetch: ContextBanner fires
    // GET /v1/mill-context for the DEFAULT context (514/2021) as soon as the app mounts. Registered
    // BEFORE navigation and awaited here so banner-absence assertions and axe scans run against a
    // settled DOM instead of racing an in-flight response (a vacuous-pass window otherwise).
    const contextSettled = this.page.waitForResponse('**/api/v1/mill-context*')
    await this.page.goto(baseURL)
    // Home shows a LoadingScreen until the mills/years fetch resolves; the dropdown proves it landed.
    await expect(this.millDropdown).toBeVisible()
    // Fail fast at the entry point if the option lists failed to load — Home renders the dropdowns
    // (empty) alongside the "Unable to load" notification, which would otherwise surface as an
    // opaque option-not-found timeout deep inside a scenario.
    await expect(this.page.getByText('Unable to load')).toHaveCount(0)
    await contextSettled
  }

  async selectMill(mill: MillFixture): Promise<void> {
    await this.millDropdown.click()
    await this.page.getByRole('option', { name: millOptionText(mill), exact: true }).click()
  }

  async selectYear(year: number): Promise<void> {
    await this.yearDropdown.click()
    await this.page.getByRole('option', { name: String(year), exact: true }).click()
  }

  async save(): Promise<void> {
    await this.saveButton.click()
  }

  // Select a full (mill, year) context and Save.
  async selectContextAndSave(mill: MillFixture): Promise<void> {
    await this.selectMill(mill)
    await this.selectYear(mill.year)
    await this.save()
  }
}
