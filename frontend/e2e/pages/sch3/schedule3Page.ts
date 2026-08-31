import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import { clickAwaitingCheckStatus } from '../common/checkStatus';
import {
  CONFIRM_NAVIGATION_BODY,
  MILL_YEAR_STORAGE_KEY,
  MSG_REQUIREMENTS_MET,
  ROUTE_SCHEDULE_3,
  type LineSpec,
  lineByLabel,
} from '../../fixtures/sch3/schedule3-test-data';

/**
 * Schedule 3 — Forest Management Administration Costs (`components/schedule3/index.tsx`), route
 * `/schedule-3`. Every selector lives here, never in a step.
 *
 * WHAT REPLACED THE LEGACY LOCATORS. The legacy `schedule3Form:*` JSF NamingContainer ids are all gone;
 * the rewrite gives every writable cell a stable Carbon id derived from its cost-item code:
 *   `#harvest-<27..37>`   the Harvest Total $ input (all 11 lines)
 *   `#pop-<code>`         the PO&P $ input — only the 8 both-column lines (27/28/30/31/32/34/35/36)
 *   `#popTimberVolume` / `#crownTimberVolume`   the two timber volumes
 *   `#overrideHarvestTotalPop`                  the Override Harvest ⁄ Total PO&P Select (N/Y)
 *   `#comments`                                 the comments TextArea
 * The derived columns (Crown $, every subtotal/total, both timber costs and all three $/m³ figures) are
 * PLAIN TEXT cells, so they are read positionally within their row rather than by id — see `lineCells`.
 *
 * The two tables are addressed by their accessible name. The cost table's `TableContainer` carries no
 * title, so its `aria-label` ("Administration Costs") is the name; the overhead table's container title
 * and `aria-label` are the SAME string, so it resolves either way (the app-wide
 * title-overrides-aria-label defect, bcgov/nr-ilcr#321, cannot bite here).
 */
export class Schedule3Page {
  constructor(private readonly page: Page) {}

  /** The 11 fixed lines + the subtotal/total rows + the Override row. Renders only with a document. */
  get costTable(): Locator {
    return this.page.getByRole('table', { name: 'Administration Costs' });
  }

  /** PO&P Timber / Crown Timber / Total Overhead — volume, cost, $/m³. */
  get overheadTable(): Locator {
    return this.page.getByRole('table', { name: 'Total Overhead and Cost Per Unit Calculation' });
  }

  // ---- navigation ---------------------------------------------------------------------------------

  /** Home -> Schedule 3 client-side via the side-nav Schedules submenu (keeps the saved context). */
  async gotoViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 3' });
    // Readiness anchor: the cost table renders only once the schedule GET resolves (LoadingScreen
    // shows until then).
    await expect(this.costTable).toBeVisible();
  }

  /**
   * Home -> Schedule 3 for a GUARD state (closed mill / not found): the GET fails, so no cost table
   * renders — wait on the route landing instead and let the caller assert the banner.
   */
  async gotoViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 3' });
    await expect(this.page).toHaveURL(new RegExp(`${ROUTE_SCHEDULE_3}$`));
  }

  /**
   * Open Schedule 3 with NO working context (S13): seed an empty MillYearContext into localStorage (the
   * app persists the selected context there and a null context is its supported empty state —
   * `MillYearProvider`) and load the route directly, so the page renders the contextMissing guard.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto(ROUTE_SCHEDULE_3);
    await expect(this.page).toHaveURL(new RegExp(`${ROUTE_SCHEDULE_3}$`));
  }

  /** Reload the current route in place (proves a save reached the DB, not just the client's repaint). */
  async reload(): Promise<void> {
    await this.page.reload();
    await expect(this.costTable).toBeVisible();
  }

  // ---- entry -------------------------------------------------------------------------------------

  field(selector: string): Locator {
    return this.page.locator(selector);
  }

  harvestInput(label: string): Locator {
    return this.field(`#harvest-${lineByLabel(label).code}`);
  }

  popInput(label: string): Locator {
    return this.field(`#pop-${lineByLabel(label).code}`);
  }

  get popTimberVolumeInput(): Locator {
    return this.field('#popTimberVolume');
  }

  get crownTimberVolumeInput(): Locator {
    return this.field('#crownTimberVolume');
  }

  get overrideSelect(): Locator {
    return this.field('#overrideHarvestTotalPop');
  }

  get commentsInput(): Locator {
    return this.field('#comments');
  }

  /**
   * Type a value and BLUR the field. The blur is load-bearing, not politeness: it is what re-groups the
   * text, advances the blur-committed snapshot the derived cells read (defect #291) and — on Annual
   * Rents Harvest — fires the S111 alert. Filling without blurring would leave the mirror a keystroke
   * behind whatever the scenario then asserts.
   */
  private async fillAndBlur(input: Locator, value: string): Promise<void> {
    await input.fill(value);
    await input.blur();
  }

  async enterHarvest(label: string, value: string): Promise<void> {
    await this.fillAndBlur(this.harvestInput(label), value);
  }

  async enterPop(label: string, value: string): Promise<void> {
    const line = lineByLabel(label);
    if (line.pop !== 'entry') {
      throw new Error(
        `Schedule 3 line "${label}" has no enterable PO&P (it is ${line.pop}) — see BR-04.`,
      );
    }
    await this.fillAndBlur(this.popInput(label), value);
  }

  async enterPopTimberVolume(value: string): Promise<void> {
    await this.fillAndBlur(this.popTimberVolumeInput, value);
  }

  async enterCrownTimberVolume(value: string): Promise<void> {
    await this.fillAndBlur(this.crownTimberVolumeInput, value);
  }

  /** The Override Harvest ⁄ Total PO&P Select carries the raw code ("N"/"Y") as its option value. */
  async selectOverride(value: 'N' | 'Y'): Promise<void> {
    await this.overrideSelect.selectOption(value);
  }

  async enterComments(text: string): Promise<void> {
    await this.commentsInput.fill(text);
  }

  /**
   * An input's value with thousands separators removed, so an assertion compares the NUMBER the field
   * carries rather than how it is punctuated (the app deliberately displays grouped values — legacy
   * parity, `numStrGroup`).
   */
  async inputValue(input: Locator): Promise<string> {
    return (await input.inputValue()).replaceAll(',', '');
  }

  // ---- reading the rendered figures --------------------------------------------------------------

  /**
   * One fixed line's row, addressed by ROW POSITION rather than by text. The 11 lines render in the
   * fixed legacy order (`ALL_LINE_CODES`), and a positional index cannot be confused by a label that is
   * also a substring of another cell's text.
   */
  private lineRow(line: LineSpec): Locator {
    const codes = ['27', '28', '29', '30', '31', '32', '33', '34', '35', '36', '37'];
    const index = codes.indexOf(String(line.code));
    // Guard the -1: Playwright reads `nth(-1)` as the LAST element, so an unknown code would silently
    // assert against the wrong row — a passing test proving nothing about the line it names. Theoretical
    // while the 11 codes are fixed, and one line to close (raised in review).
    if (index < 0) {
      throw new Error(
        `Schedule 3 line code ${line.code} ("${line.label}") is not one of the 11 fixed lines `
          + `(${codes.join(', ')}), so it has no row position. Fix the caller or LINES.`,
      );
    }
    return this.costTable.locator('tbody tr').nth(index);
  }

  /**
   * A fixed line's three money cells as RENDERED TEXT (`—` for a blank/hidden cell). Harvest and PO&P
   * come back as the input's value where the cell is an input, so one helper serves the editable and
   * the read-only render — which is what lets the read-only scenarios assert the same figures.
   */
  async lineCells(label: string): Promise<{ harvest: string; pop: string; crown: string }> {
    const line = lineByLabel(label);
    const row = this.lineRow(line);
    const cell = async (index: number): Promise<string> => {
      const td = row.locator('td').nth(index);
      const input = td.locator('input');
      if ((await input.count()) > 0) {
        // The cell is an editable input: read its VALUE. Branching on the render shape (not on a
        // behaviour) so one step can read both modes; the render mode itself is asserted separately and
        // explicitly by `editableFieldCount`.
        return (await input.inputValue()).replaceAll(',', '');
      }
      return ((await td.textContent()) ?? '').trim().replaceAll(',', '');
    };
    return { harvest: await cell(1), pop: await cell(2), crown: await cell(3) };
  }

  /**
   * A subtotal/total row's three money cells, addressed by the row's own label.
   *
   * The cell count is asserted before the cells are read (added 2026-08-31, raised in PR #402 review).
   * `texts[1].trim()` on a row that rendered fewer cells — a column added or removed, or
   * `filter({ hasText })` matching a differently-shaped row — throws `Cannot read properties of
   * undefined (reading 'trim')` from inside the caller's `expect.poll`, which reports a JS error rather
   * than the "the row shape changed" message the rest of this file takes care to produce.
   */
  async totalCells(rowLabel: string): Promise<[string, string, string]> {
    const row = this.costTable.locator('tbody tr').filter({ hasText: rowLabel });
    await expect(row, `no Schedule 3 total row matched "${rowLabel}"`).toHaveCount(1);
    const texts = await row.locator('td').allInnerTexts();
    expect(
      texts.length,
      `the Schedule 3 total row "${rowLabel}" rendered ${texts.length} cell(s) — this reads label + `
        + 'Harvest + PO&P + Crown, so the cost table\'s shape changed. Re-ground the page object.',
    ).toBeGreaterThanOrEqual(4);
    return [
      texts[1].trim().replaceAll(',', ''),
      texts[2].trim().replaceAll(',', ''),
      texts[3].trim().replaceAll(',', ''),
    ];
  }

  /** A Total Overhead table row: volume, total cost, $/m³ (volume is an input on the two timber rows). */
  async overheadCells(rowLabel: string): Promise<[string, string, string]> {
    const row = this.overheadTable.locator('tbody tr').filter({ hasText: rowLabel });
    await expect(row, `no Total Overhead row matched "${rowLabel}"`).toHaveCount(1);
    // Same guard as `totalCells`, for the same reason. Here a missing `td` would instead surface as a
    // locator timeout on `nth(2)` / `nth(3)` — slower, and it names the cell rather than the shape.
    // Auto-waiting and exact: every row of this table is label + volume + cost + $/m³.
    await expect(
      row.locator('td'),
      `the Total Overhead row "${rowLabel}" does not render exactly 4 cells (label + volume + cost + `
        + "$/m³) — the overhead table's shape changed. Re-ground the page object.",
    ).toHaveCount(4);
    const volumeCell = row.locator('td').nth(1);
    const volumeInput = volumeCell.locator('input');
    const volume =
      (await volumeInput.count()) > 0
        ? (await volumeInput.inputValue()).replaceAll(',', '')
        : ((await volumeCell.textContent()) ?? '').trim().replaceAll(',', '');
    const cost = ((await row.locator('td').nth(2).textContent()) ?? '').trim().replaceAll(',', '');
    const perUnit = ((await row.locator('td').nth(3).textContent()) ?? '').trim().replaceAll(',', '');
    return [volume, cost, perUnit];
  }

  /**
   * How many editable amount inputs the page renders. The read-only (non-Draft) render has ZERO — every
   * figure is plain text — so this proves the render MODE by counting what is there rather than by
   * asserting one locator finds nothing (which would also "pass" if the page had failed to render).
   */
  async editableFieldCount(): Promise<number> {
    return this.costTable.locator('input').count();
  }

  // ---- actions -----------------------------------------------------------------------------------

  /**
   * The action bars, deliberately asymmetric in the app: Save + Check Status above the schedule, Save +
   * Check Status + Delete below it (legacy parity). `.first()`/`.last()` therefore address the TOP and
   * BOTTOM bars, and Delete exists only on the bottom one.
   */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true }).first();
  }

  get bottomSaveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true }).last();
  }

  get checkStatusButton(): Locator {
    return this.page.getByRole('button', { name: 'Check Status', exact: true }).first();
  }

  get deleteButton(): Locator {
    return this.page.getByRole('button', { name: 'Delete', exact: true }).first();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  async saveFromBottomBar(): Promise<void> {
    await this.bottomSaveButton.click();
  }

  /**
   * Press Check Status, settling on the server's answer when one is sent (see `clickAwaitingCheckStatus`),
   * then on the outcome actually rendering — the met message or at least one notification.
   *
   * The second wait is specific to this domain and load-bearing for DIV-6's mirror arm (S26): that
   * scenario checks twice, and its "no longer flagged" assertion passed VACUOUSLY against a DOM that had
   * not re-rendered until this gate existed (found 2026-08-27).
   */
  async checkStatus(): Promise<void> {
    await clickAwaitingCheckStatus(this.page, '/schedule3/check-status', () =>
      this.checkStatusButton.click(),
    );
    await expect(this.checkStatusOutcome.first()).toBeVisible();
  }

  /**
   * Whatever Check Status rendered — the met message OR any error/warning notification. Used only as the
   * readiness gate above; scenarios assert the specific message, never this.
   */
  get checkStatusOutcome(): Locator {
    return this.page
      .getByText(MSG_REQUIREMENTS_MET, { exact: true })
      .or(this.page.locator('.cds--inline-notification'));
  }

  /** The delete confirm Modal (a Carbon dialog, aria-labelled by its "Delete schedule" heading). */
  get deleteConfirmDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Delete schedule' });
  }

  async openDeleteConfirm(): Promise<void> {
    await this.deleteButton.click();
    await expect(this.deleteConfirmDialog).toBeVisible();
  }

  /** Confirm the delete — the modal's OWN primary Delete button (scoped to the dialog). */
  async confirmDelete(): Promise<void> {
    await this.deleteConfirmDialog.getByRole('button', { name: 'Delete', exact: true }).click();
  }

  async cancelDelete(): Promise<void> {
    await this.deleteConfirmDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  }

  // ---- the two sub-page links (their labels carry the CNT-001 counts) -----------------------------

  get otherAcceptableLink(): Locator {
    return this.page.getByRole('button', { name: /^Subtotal Other Costs \(\d+\):$/ });
  }

  get unacceptableLink(): Locator {
    return this.page.getByRole('button', { name: /^Included Unacceptable Costs \(\d+\):$/ });
  }

  private async countFrom(link: Locator): Promise<number> {
    const label = (await link.textContent()) ?? '';
    const match = label.match(/\((\d+)\):/);
    expect(match, `could not read the count from "${label}"`).toBeTruthy();
    return Number(match![1]);
  }

  /** The itemized other-acceptable group count N from "Subtotal Other Costs (N):" (CNT-001). */
  async otherAcceptableCount(): Promise<number> {
    return this.countFrom(this.otherAcceptableLink);
  }

  /** The included-unacceptable count N — item-38 rows PLUS 1 when Annual Rents carries a Harvest. */
  async unacceptableCount(): Promise<number> {
    return this.countFrom(this.unacceptableLink);
  }

  /** The discard-unsaved-edits Modal shown before leaving an EDITABLE schedule for a sub-page. */
  get leaveDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Leave Schedule 3' });
  }

  /**
   * Open a sub-page from an editable schedule: click the count link, then confirm the "Leave Schedule 3"
   * modal. Navigation is client-side, so the in-memory mill/year context survives.
   */
  private async openSubPage(link: Locator, route: string): Promise<void> {
    await link.click();
    await expect(this.leaveDialog).toBeVisible();
    // The legacy `confirmNavigationMsg` warning, asserted on the way through rather than in a scenario
    // of its own: every sub-page entry crosses this modal, so a change to its wording (or its
    // disappearance) fails here immediately.
    await expect(
      this.leaveDialog.getByText(CONFIRM_NAVIGATION_BODY, { exact: true }),
      'the discard-unsaved-edits confirmation no longer carries the legacy confirmNavigationMsg text',
    ).toBeVisible();
    await this.leaveDialog.getByRole('button', { name: 'Continue', exact: true }).click();
    await expect(this.page).toHaveURL(new RegExp(`${route}$`));
  }

  async openOtherAcceptable(route: string): Promise<void> {
    await this.openSubPage(this.otherAcceptableLink, route);
  }

  async openUnacceptable(route: string): Promise<void> {
    await this.openSubPage(this.unacceptableLink, route);
  }

  /**
   * Open a sub-page from a READ-ONLY schedule. No confirm modal is shown here and that is the point: a
   * schedule that cannot be edited has no unsaved edits to lose (`openSubPage` in the app navigates
   * straight through), so waiting for the modal would hang.
   */
  /**
   * The save-first gate restored by defect #296 (legacy ALT-001/ALT-002): both cost sub-pages are
   * reachable only from a SAVED Schedule 3, so an unsaved one shows a passive "Save required" modal
   * instead of navigating. Before #296 this state could not arise — the parent page itself 404'd when
   * unsaved — which is why S18/S19 were dispositioned `not-applicable` until now.
   */
  get saveRequiredDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Save required' });
  }

  /** Click a sub-page link on an UNSAVED schedule and stay put behind the save-first gate. */
  async openSubPageBlocked(link: 'other-acceptable' | 'unacceptable'): Promise<void> {
    const target = link === 'other-acceptable' ? this.otherAcceptableLink : this.unacceptableLink;
    await target.click();
    await expect(this.saveRequiredDialog).toBeVisible();
  }

  async openOtherAcceptableReadOnly(route: string): Promise<void> {
    await this.otherAcceptableLink.click();
    // ORDER IS LOAD-BEARING, and was wrong until 2026-08-28 (raised in review).
    // `toHaveCount(0)` passes the instant the count is zero — which it is before React has rendered
    // anything at all — so asserting it FIRST proved nothing: had the read-only path regressed and
    // started showing the discard modal, this would very likely still have passed. That is precisely
    // the vacuous-negative-assertion class this suite documents elsewhere.
    // `toHaveURL` auto-waits for the client-side navigation to settle, so once it has resolved the
    // page is rendered and the absence of the dialog is a real observation.
    await expect(this.page).toHaveURL(new RegExp(`${route}$`));
    await expect(
      this.leaveDialog,
      'a read-only Schedule 3 must not ask to discard unsaved edits — there are none',
    ).toHaveCount(0);
  }

  async cancelLeave(): Promise<void> {
    await this.leaveDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  }
}
