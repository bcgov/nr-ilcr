import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import { fieldError } from '../common/carbonHelpers';
import { clickAwaitingCheckStatus } from '../common/checkStatus';
import {
  ACTION,
  CONFIRM_DELETE,
  FIELD_ID,
  MILL_YEAR_STORAGE_KEY,
} from '../../fixtures/sch2/schedule2-test-data';

/**
 * Schedule 2 — Purchased/Private Log Costs & Sales (components/schedule2/index.tsx). All Schedule 2 DOM
 * knowledge lives here; steps carry domain vocabulary only.
 *
 * RE-GROUNDING NOTE — the legacy Gherkin's locators describe a JSF/PrimeFaces page that no longer
 * exists, so none of them are used:
 *   - route `/schedule-2` reached via Home + side-nav, not `schedule2.xhtml`
 *   - stable Carbon ids (`#purchasedLogCostCost`, `#lessLogSalesVolume`, `#lessLogSalesCost`,
 *     `#comments`), not PrimeFaces naming-container ids (`schedule2Form:purchasedLogCostCos`). The
 *     legacy `[UNKNOWN — no explicit id]` markers on the comments textarea and the three buttons are
 *     RESOLVED by the rewrite — every control is addressable.
 *   - the delete confirm is a Carbon Modal ("Delete"/"Cancel"), not `.ui-confirmdialog-yes`.
 *   - there is no `p:messages` panel: results render as Carbon InlineNotifications with an explicit
 *     severity word in the title (WCAG 2.1 AA — never colour alone).
 *   - Delete on a never-saved schedule is rendered-but-DISABLED, where legacy omitted it from the DOM
 *     entirely. Same user-visible outcome (delete unavailable), different mechanism — see coverage.md.
 *     The app states the reason in a visually-hidden span ("Available once the schedule is saved"),
 *     which legacy had no need to do; `deleteUnavailableHint` addresses it.
 *
 * TOP-AND-BOTTOM ACTION BARS, DELIBERATELY ASYMMETRIC: the page emits an action bar above and below
 * the table, but only the BOTTOM one carries Delete — legacy's shape (schedule2.xhtml:35-36 vs
 * :172-178), restored by defect #292; before that fix Delete was rendered on both. So Save and Check
 * Status resolve TWO elements (a bare `getByRole` would throw in strict mode) while Delete resolves
 * ONE, in the bottom bar. Every action getter below is therefore explicit about which bar it drives —
 * `.first()` for the top bar, `bottomAction()` to prove the bottom bar is wired identically, and
 * `deleteButton` scoped to `.last()` because the top bar has no Delete to find.
 *
 * Carbon specifics: `TextInput` keeps its `labelText` as the accessible name even under `hideLabel`;
 * the numeric fields are `CommaNumberInput`s, which DISPLAY a grouped value ("50,000") while holding a
 * raw one ("50000"), so `inputValue()` reads the grouped form the user actually sees.
 */
export class Schedule2Page {
  constructor(private readonly page: Page) {}

  // ---- readiness / navigation ---------------------------------------------------------------------

  /** The cost table — present once the schedule2 GET resolves. */
  get table(): Locator {
    return this.page.getByRole('table', { name: 'Purchased / Private Log Costs' });
  }

  /** Open Schedule 2 via the side-nav (client-side, so the context saved on Home survives). */
  async openViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 2' });
    await expect(this.page).toHaveURL(/\/schedule-2$/);
    await expect(this.table).toBeVisible();
  }

  /**
   * Navigate for a GUARD state (closed mill 409 / no report-status row 404): the GET fails so no table
   * renders — wait on the route landing only and let the scenario assert the block message.
   */
  async openViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 2' });
    await expect(this.page).toHaveURL(/\/schedule-2$/);
  }

  /**
   * Open Schedule 2 with NO working context (S09): seed an empty MillYearContext into localStorage (the
   * supported empty state — MillYearProvider.tsx) and load the route directly, so the page renders its
   * contextMissing guard without ever issuing a request.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-2');
    await expect(this.page).toHaveURL(/\/schedule-2$/);
  }

  /** Reload the current route in place — proves a save survived a full document refetch. */
  async reload(): Promise<void> {
    await this.page.reload();
    await expect(this.table).toBeVisible();
  }

  // ---- editable fields ----------------------------------------------------------------------------

  get item25Cost(): Locator {
    return this.page.locator(FIELD_ID.item25Cost);
  }

  get item26Volume(): Locator {
    return this.page.locator(FIELD_ID.item26Volume);
  }

  get item26Cost(): Locator {
    return this.page.locator(FIELD_ID.item26Cost);
  }

  get comments(): Locator {
    return this.page.locator(FIELD_ID.comments);
  }

  /** Resolve the Gherkin-facing field token to its input. Keeps field names out of the step layer. */
  field(name: string): Locator {
    switch (name) {
      case 'Purchased Log Cost cost':
        return this.item25Cost;
      case 'Less Log Sales volume':
        return this.item26Volume;
      case 'Less Log Sales cost':
        return this.item26Cost;
      case 'comments':
        return this.comments;
      default:
        throw new Error(
          `unknown Schedule 2 field "${name}". Use "Purchased Log Cost cost", "Less Log Sales volume", "Less Log Sales cost" or "comments".`,
        );
    }
  }

  /** The `#id` selector behind a field token, for Carbon inline-error scoping. */
  private fieldId(name: string): string {
    switch (name) {
      case 'Purchased Log Cost cost':
        return FIELD_ID.item25Cost;
      case 'Less Log Sales volume':
        return FIELD_ID.item26Volume;
      case 'Less Log Sales cost':
        return FIELD_ID.item26Cost;
      case 'comments':
        return FIELD_ID.comments;
      default:
        throw new Error(`unknown Schedule 2 field "${name}"`);
    }
  }

  /**
   * Type a value into a field. `fill('')` on a CommaNumberInput clears it, which is how the
   * blank-field slices (S03/S04) express "left empty" on an anchor that arrives pre-populated.
   */
  async setField(name: string, value: string): Promise<void> {
    await this.field(name).fill(value);
    // The comma-regrouping runs on `change`; blur so the displayed value has settled before the next
    // step reads it (and so the advisory validator has seen the final value).
    await this.field(name).blur();
  }

  /** What a field currently DISPLAYS (grouped, as the user sees it). */
  async fieldValue(name: string): Promise<string> {
    return this.field(name).inputValue();
  }

  /** The Carbon inline error rendered under a field, if any. */
  fieldError(name: string): Locator {
    return fieldError(this.page, this.fieldId(name));
  }

  // ---- actions (top + bottom bars) ------------------------------------------------------------------

  /** An action button in the TOP action bar (the page renders each button twice). */
  private action(name: string): Locator {
    return this.page.getByRole('button', { name, exact: true }).first();
  }

  /** The same action button in the BOTTOM bar — used to prove both bars are wired identically. */
  bottomAction(name: string): Locator {
    return this.page.getByRole('button', { name, exact: true }).last();
  }

  /** How many times an action button is rendered (legacy parity: Save/Check Status appear twice). */
  async actionCount(name: string): Promise<number> {
    return this.page.getByRole('button', { name, exact: true }).count();
  }

  get saveButton(): Locator {
    return this.action(ACTION.save);
  }

  get checkStatusButton(): Locator {
    return this.action(ACTION.checkStatus);
  }

  /**
   * The page's Delete button. Scoped to the action bars so it can never resolve the confirm modal's own
   * "Delete" primary button, which carries the same accessible name once the modal is open.
   */
  get deleteButton(): Locator {
    // The BOTTOM bar (`.last()`), which is the only bar that carries Delete — see the header note.
    // Scoped to the action bars so it can never resolve the confirm modal's own "Delete" primary,
    // and NOT via `bottomAction()`, whose page-wide `.last()` would resolve exactly that.
    return this.page.locator('.schedule-2__actions').last().getByRole('button', {
      name: ACTION.delete,
      exact: true,
    });
  }

  /**
   * The reason a greyed Delete gives ("Available once the schedule is saved"). Rendered only when the
   * schedule is editable but has never been saved — defect #292 kept legacy's rule (no delete without
   * a persisted record) while changing the mechanism from "not rendered" to "disabled", and a
   * disabled Carbon button is not focusable, so the reason has to be stated somewhere.
   *
   * It is `cds--visually-hidden`: in the accessibility tree, NOT on the page. `getByText` still finds
   * it, but do not assert `toBeVisible()` — assert `toBeAttached()` and the `aria-describedby` wiring.
   */
  get deleteUnavailableHint(): Locator {
    return this.page.getByText('Available once the schedule is saved', { exact: true });
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click();
  }

  /** Press Check Status, settling on the server's answer when one is sent — see `clickAwaitingCheckStatus`. */
  async clickCheckStatus(): Promise<void> {
    await clickAwaitingCheckStatus(this.page, '/schedule2/check-status', () =>
      this.checkStatusButton.click(),
    );
  }

  /** Open the delete confirm modal. */
  async clickDelete(): Promise<void> {
    await this.deleteButton.click();
    await expect(this.confirmModal).toBeVisible();
  }

  // ---- delete confirmation --------------------------------------------------------------------------

  /** The Carbon confirm Modal (NOT a PrimeFaces confirmDialog, NOT a native browser dialog). */
  get confirmModal(): Locator {
    return this.page.getByRole('dialog').filter({ hasText: CONFIRM_DELETE.body });
  }

  get confirmModalHeading(): Locator {
    return this.page.getByText(CONFIRM_DELETE.heading, { exact: true });
  }

  async confirmDelete(): Promise<void> {
    await this.confirmModal
      .getByRole('button', { name: CONFIRM_DELETE.primary, exact: true })
      .click();
  }

  async cancelDelete(): Promise<void> {
    await this.confirmModal
      .getByRole('button', { name: CONFIRM_DELETE.secondary, exact: true })
      .click();
    await expect(this.confirmModal).toBeHidden();
  }

  // ---- table rows ------------------------------------------------------------------------------------

  /** The data row whose first cell holds `label` (e.g. "Subtotal:"). */
  row(label: string): Locator {
    return this.table.locator('tbody tr').filter({
      has: this.page.getByRole('cell', { name: label, exact: true }),
    });
  }

  /**
   * A row's three value cells as the user sees them: [Volume, Cost, $/m³].
   *
   * A cell holding an editable input reports that input's DISPLAYED (grouped) value; a read-only cell
   * reports its text. That distinction matters — the same row renders as inputs when the schedule is
   * editable and as plain text when it is not, and both forms must be assertable by one step.
   *
   * JUSTIFIED BRANCH (the suite otherwise bans conditionals that steer a test): this one selects a
   * READ STRATEGY, never a code path through the assertion — every scenario still runs one deterministic
   * route and asserts the same expected values either way. It cannot mask a wrong render mode, because
   * which mode is in force is asserted separately and explicitly: the read-only scenarios call
   * `the Schedule 2 fields are read-only` (which requires ZERO inputs), and the editable scenarios type
   * into those inputs, so a cell silently switching kind fails there first.
   */
  async rowValues(label: string): Promise<[string, string, string]> {
    const cells = this.row(label).locator('td');
    const values: string[] = [];
    // Skip cell 0 (the row label); read the three value columns.
    for (let i = 1; i <= 3; i += 1) {
      const cell = cells.nth(i);
      const input = cell.locator('input');
      values.push(
        (await input.count()) > 0 ? await input.inputValue() : (await cell.innerText()).trim(),
      );
    }
    return [values[0], values[1], values[2]];
  }

  /**
   * The `id` of every editable input inside the cost table, in DOM order.
   *
   * This is what makes BR-03 assertable: the Purchased/Private volume is CARRIED from Schedule 3 and must
   * not be enterable here. `rowValues()` deliberately reads an input's value *or* a cell's text, so it
   * cannot tell the two apart — asserting the displayed "10" proves the value, not that the cell is
   * read-only. This does.
   */
  async editableFieldIds(): Promise<string[]> {
    return this.table.locator('input').evaluateAll((els) => els.map((e) => e.id));
  }

  /** Every row label currently rendered, in order — proves the legacy row order survives. */
  async rowLabels(): Promise<string[]> {
    const cells = this.table.locator('tbody tr td:first-child');
    return (await cells.allInnerTexts()).map((t) => t.trim());
  }

  // ---- notifications ----------------------------------------------------------------------------------

  /**
   * A Carbon InlineNotification carrying `text` (title or subtitle). Used for the Save success message,
   * the Check Status result, and every error banner — all of which render through the same shared
   * `NotificationColumn` / `PageState` shape.
   *
   * Addressed by ROLE, not by Carbon's `.cds--inline-notification` class: verified against the running
   * app 2026-08-13 that the notification container carries `role="status"` for EVERY kind, error
   * included (`getByRole('alert')` matches nothing on this page). A role survives a Carbon class rename;
   * the class would not.
   */
  notification(text: string): Locator {
    return this.page.getByRole('status').filter({ hasText: text });
  }

  // ---- read-only render --------------------------------------------------------------------------------

  /**
   * The comments value as rendered in READ-ONLY mode (a `<p>`, not a textarea). Null comments render as
   * an em dash.
   */
  get commentsReadOnly(): Locator {
    return this.page.locator('.schedule-2__comments');
  }

  /** True when the page is in editable mode (the comments textarea only exists when editable). */
  async isEditable(): Promise<boolean> {
    return (await this.comments.count()) > 0;
  }
}
