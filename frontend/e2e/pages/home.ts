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

  async goto(): Promise<void> {
    // Home no longer fetches GET /v1/mill-context on mount — the working-context display moved off the
    // Home banner (removed) onto the schedule ScheduleTombstone, which fetches it on the schedule page.
    // So Home's only mount fetches are the mills/years lists; the visible dropdown proves they landed.
    await this.page.goto(baseURL)
    await expect(this.millDropdown).toBeVisible()
    // Fail fast at the entry point if the option lists failed to load — Home renders the dropdowns
    // (empty) alongside the "Unable to load" notification, which would otherwise surface as an
    // opaque option-not-found timeout deep inside a scenario.
    await expect(this.page.getByText('Unable to load')).toHaveCount(0)
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
