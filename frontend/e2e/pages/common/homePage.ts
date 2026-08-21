import { type Locator, type Page, expect } from '@playwright/test';
import { openApp } from './authNav';
import { MSG_SAVED } from '../../fixtures/sch1/schedule1-test-data';

/**
 * Home — the Mill and Reporting Year page (components/home/index.tsx). Selecting a (mill, year) and
 * Saving sets the in-memory MillYearContext that every schedule page reads, so this page object is
 * common: every schedule domain drives it to reach a working context.
 *
 * Carbon-aware role locators: the two Dropdowns expose comboboxes named by their titleText; options
 * render as role="option" once open; the Save confirmation is a Carbon InlineNotification whose
 * verbatim subtitle is the API-owned success message.
 */
export class HomePage {
  constructor(private readonly page: Page) {}

  get millDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Mill', exact: true });
  }

  get yearDropdown(): Locator {
    return this.page.getByRole('combobox', { name: 'Reporting Year', exact: true });
  }

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save' });
  }

  /**
   * The working-context banner (Layout/ContextBanner.tsx) — a labelled region, addressable by role.
   * The banner is the UI read-back of a saved context: Home's Save updates the in-memory
   * MillYearContext, which the banner re-fetches and re-renders (GET /v1/mill-context).
   */
  get banner(): Locator {
    return this.page.getByRole('region', { name: 'Working context' });
  }

  async open(): Promise<void> {
    await openApp(this.page);
    // Home shows a LoadingScreen until the mills/years fetch resolves; the dropdown proves it landed.
    //
    // NAVIGATION budget (30 s), not the default 10 s `expect` timeout — for the same reason `openApp`'s
    // shell check has one: this is FIRST-FETCH readiness, not a state assertion. The shell can paint while
    // `/v1/mills` + `/v1/reporting-years` are still in flight, and under a parallel stress run (many workers
    // each cold-loading the SPA through the Vite dev server) that gap exceeded 10 s once in 514 executions —
    // failing here, before the scenario had done anything. Stabilised rather than retried away; every
    // assertion after this one keeps the strict default, so a genuine hang still fails fast.
    await expect(this.millDropdown).toBeVisible({ timeout: 30_000 });
    // Fail fast at the entry point if the option lists failed to load.
    await expect(this.page.getByText('Unable to load')).toHaveCount(0);
  }

  /** Select a (mill, year) context and Save it, waiting for the success confirmation. */
  async selectContextAndSave(millOption: string, year: number): Promise<void> {
    await this.selectMill(millOption);
    await this.selectYear(year);
    await this.saveButton.click();
    await expect(this.page.getByText(MSG_SAVED)).toBeVisible();
  }

  // ---- UC-SEC-001 granular helpers (Home as a tested subject, not just a precondition) ----

  /** Pick a mill by its option text (`${millNumber} - ${millName}`). */
  async selectMill(millOption: string): Promise<void> {
    await this.millDropdown.click();
    await this.page.getByRole('option', { name: millOption, exact: true }).click();
  }

  /** Pick a reporting year by its numeric label. */
  async selectYear(year: number): Promise<void> {
    await this.yearDropdown.click();
    await this.page.getByRole('option', { name: String(year), exact: true }).click();
  }

  /** Click Save WITHOUT asserting an outcome — the scenario asserts success or error itself. */
  async save(): Promise<void> {
    await this.saveButton.click();
  }

  /** A banner line, matched exactly (Mill line or a status line). */
  bannerLine(text: string): Locator {
    return this.banner.getByText(text, { exact: true });
  }

  /**
   * Prove the option lists actually populated from `GET /v1/mills` + `/v1/reporting-years` (AC1 land):
   * open each dropdown, assert a known real option is present, then close it again.
   */
  async assertOptionListsPopulated(millOption: string, year: number): Promise<void> {
    await this.millDropdown.click();
    await expect(this.page.getByRole('option', { name: millOption, exact: true })).toBeVisible();
    await this.millDropdown.click(); // toggle closed
    await this.yearDropdown.click();
    await expect(this.page.getByRole('option', { name: String(year), exact: true })).toBeVisible();
    await this.yearDropdown.click(); // toggle closed
  }
}
