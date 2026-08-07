import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import { MILL_YEAR_STORAGE_KEY, MSG_SAVED } from '../../fixtures/sch1/schedule1-test-data';

/**
 * Schedule 1 — Average Cost of Logging (components/schedule1/index.tsx). Writable fields are Carbon
 * TextInputs with stable ids, so selectors live here (not in steps). Line items #vol-<code>/#cost-<code>
 * for codes 12–18; silviculture #vol-1/#cost-1 (Actual $ Spent) & #vol-2/#cost-2 (Accrued less Actual);
 * #otherCostsVolume; #comments. The page requires the Home context to be set first, else it renders
 * "Please Select Mill and Reporting Year in the Home Page." and no inputs.
 */

/** Map re-grounded line-item labels (the app's own vocabulary) to their input ids. */
const FIELD_IDS: Record<string, { vol: string; cost: string }> = {
  'Standing Tree to Loaded Truck': { vol: '#vol-12', cost: '#cost-12' },
  'Log Transportation': { vol: '#vol-13', cost: '#cost-13' },
  'Road Management': { vol: '#vol-14', cost: '#cost-14' },
  'Road Construction Costs': { vol: '#vol-15', cost: '#cost-15' },
  'Post Logging Treatment': { vol: '#vol-16', cost: '#cost-16' },
  'Stumpage and Royalty': { vol: '#vol-17', cost: '#cost-17' },
  'Depletion and Amortization': { vol: '#vol-18', cost: '#cost-18' },
  'Actual $ Spent': { vol: '#vol-1', cost: '#cost-1' },
  'Accrued less Actual $ Spent': { vol: '#vol-2', cost: '#cost-2' },
};

/**
 * Fields addressed by a single label (outside the line-item vol/cost grid). Subtotal Other Costs volume
 * is the ONLY editable 8-digit-range volume in the app (Forest Mgmt Admin / Subtotal Company Logging
 * are read-only, unlike legacy) — so it is the exemplar for the 8-digit volume validation.
 */
const NAMED_FIELDS: Record<string, string> = {
  'Subtotal Other Costs volume': '#otherCostsVolume',
};

export class Schedule1Page {
  constructor(private readonly page: Page) {}

  /** The aria-labelled cost table — present only once a schedule document renders (not on a guard). */
  get companyLoggingTable(): Locator {
    return this.page.getByRole('table', { name: 'Company Logging Costs' });
  }

  /** Navigate Home -> Schedule 1 client-side via the side-nav Schedules submenu. */
  async gotoViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 1' });
    // Readiness anchor: the aria-labelled Company Logging Costs table only renders once the schedule
    // GET resolves (LoadingScreen shows until then).
    await expect(this.companyLoggingTable).toBeVisible();
  }

  /**
   * Navigate Home -> Schedule 1 for a GUARD state (S20 closed mill / S21 not found): the GET fails, so
   * no cost table renders — wait on the route landing instead, and let the caller assert the banner.
   */
  async gotoViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 1' });
    await expect(this.page).toHaveURL(/\/schedule-1$/);
  }

  /**
   * Open Schedule 1 with NO working context (S19): seed an empty MillYearContext into localStorage
   * (the app persists the selected context there; a null context is the supported empty state —
   * MillYearProvider.tsx) and load the route directly, so the page renders the contextMissing guard.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-1');
    await expect(this.page).toHaveURL(/\/schedule-1$/);
  }

  /**
   * Navigate Home -> Schedule 1 WITHOUT waiting for the form to be ready — for the closed-mill case
   * where the schedule GET returns 409 and the page renders a block message instead of the table.
   */
  async gotoViaNavRaw(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 1' });
  }

  /** The not-active / closed-mill block message (ERR-002 / S20, verbatim from the 409 detail). */
  blocked(text: string): Locator {
    return this.page.getByText(text);
  }

  field(id: string): Locator {
    return this.page.locator(id);
  }

  /** Enter the volume and/or cost for a labelled line item (blank cells are skipped). */
  async enterAmount(label: string, volume?: string, cost?: string): Promise<void> {
    const ids = FIELD_IDS[label];
    if (!ids) {
      throw new Error(`Unknown Schedule 1 line item label: "${label}"`);
    }
    if (volume !== undefined && volume !== '') {
      await this.field(ids.vol).fill(volume);
    }
    if (cost !== undefined && cost !== '') {
      await this.field(ids.cost).fill(cost);
    }
  }

  async enterComments(text: string): Promise<void> {
    await this.field('#comments').fill(text);
  }

  /** Resolve a single field label — a NAMED_FIELDS key, or "<line item> volume"/"<line item> cost". */
  fieldIdFor(label: string): string {
    const named = NAMED_FIELDS[label];
    if (named) {
      return named;
    }
    const match = label.match(/^(.*) (volume|cost)$/);
    if (match) {
      const ids = FIELD_IDS[match[1]];
      if (ids) {
        return match[2] === 'volume' ? ids.vol : ids.cost;
      }
    }
    throw new Error(`Unknown Schedule 1 field label: "${label}"`);
  }

  /** Type a raw value into a single labelled field (used by validation scenarios). */
  async enterField(label: string, value: string): Promise<void> {
    await this.field(this.fieldIdFor(label)).fill(value);
  }

  /** The input for a labelled field (used to assert an entered value survives a failed save, S23). */
  fieldByLabel(label: string): Locator {
    return this.field(this.fieldIdFor(label));
  }

  /** Two Save buttons (top + bottom) trigger the same action; the first is sufficient. */
  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save' }).first();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  /** SUC-001 success InlineNotification subtitle (API-owned, verbatim). */
  get successNotification(): Locator {
    return this.page.getByText(MSG_SAVED);
  }

  /** The Check Status action (ScheduleActions): tertiary button, disabled unless editable. */
  get checkStatusButton(): Locator {
    return this.page.getByRole('button', { name: 'Check Status' }).first();
  }

  /** Run BR-07 Check Status (POST /check-status). Result renders as success/error/warning columns. */
  async checkStatus(): Promise<void> {
    await this.checkStatusButton.click();
  }

  /** The Delete action (ScheduleActions): danger button, disabled unless editable. */
  get deleteButton(): Locator {
    return this.page.getByRole('button', { name: 'Delete' }).first();
  }

  /** The delete confirm Modal (Carbon dialog, aria-labelled by its "Delete schedule" heading). */
  get deleteConfirmDialog(): Locator {
    return this.page.getByRole('dialog', { name: 'Delete schedule' });
  }

  /** Open the delete confirm Modal (S13) — the action-bar Delete button. */
  async openDeleteConfirm(): Promise<void> {
    await this.deleteButton.click();
    await expect(this.deleteConfirmDialog).toBeVisible();
  }

  /** Confirm the delete — the modal's own primary Delete button (scoped to the dialog). */
  async confirmDelete(): Promise<void> {
    await this.deleteConfirmDialog.getByRole('button', { name: 'Delete' }).click();
  }

  /**
   * A representative editable amount input (Standing Tree volume). Rendered as a Carbon TextInput only
   * when the schedule is editable; a read-only (non-Draft) schedule renders the value as plain text, so
   * this locator resolves to zero elements — the S22 read-only proof.
   */
  get firstAmountInput(): Locator {
    return this.page.locator('#vol-12');
  }

  /** The Comments editor — a TextArea only when editable; read-only schedules render a <p> instead. */
  get commentsInput(): Locator {
    return this.page.locator('#comments');
  }

  /** The "Subtotal Other Costs(N):" ghost button — its label carries the itemized-row count N. */
  get otherCostsButton(): Locator {
    return this.page.getByRole('button', { name: /^Subtotal Other Costs\(\d+\):/ });
  }

  /** Parse the itemized Other-Costs count N from the "Subtotal Other Costs(N):" button label. */
  async otherCostsCount(): Promise<number> {
    const label = (await this.otherCostsButton.textContent()) ?? '';
    const match = label.match(/\((\d+)\)/);
    expect(match, `could not read the Other Costs count from "${label}"`).toBeTruthy();
    return Number(match![1]);
  }

  /**
   * Open the Other Costs sub-page from an editable schedule: click the count button, then confirm the
   * "Leave Schedule 1" discard-unsaved-edits Modal ("Continue"). Navigation is client-side, so the
   * in-memory mill/year context survives.
   */
  async openOtherCosts(): Promise<void> {
    await this.otherCostsButton.click();
    const dialog = this.page.getByRole('dialog', { name: 'Leave Schedule 1' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Continue' }).click();
  }
}
