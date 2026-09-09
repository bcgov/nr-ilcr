import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';

/**
 * Schedule 5 — Camp and Access Expenses (components/schedule5/index.tsx). All Schedule 5 DOM knowledge
 * lives here; steps carry domain vocabulary only.
 *
 * RE-GROUNDING NOTE — the legacy Gherkin addresses a JSF/PrimeFaces page that no longer exists, so none
 * of its locators are used. UC-SCH5-001-S01 names PrimeFaces naming-container ids
 * (`schedule5Form:newCampName`, `schedule5Form:newCateringFoodVolume`, `schedule5Form:existingCampDT`,
 * `schedule5Form:messages`) and the URL `/ILCR/schedule5.xhtml`. The rewrite's equivalents:
 *   - route `/schedule-5`, reached via Home + side-nav rather than a direct .xhtml load.
 *   - the descriptors are Carbon `TextInput`s / a `Select` addressed by their VISIBLE labels
 *     ("Camp Name", "Road Distance to Operating Area (km)", "Size of Camp (number of persons)",
 *     "Associated Camp Volume (m³)", "Isolated Camp") — not by id.
 *   - the 12 category rows are a shared `CategoryGrid` whose inputs are `hideLabel` TextInputs. Carbon
 *     keeps `labelText` as the accessible name under `hideLabel`, and the component strips the label's
 *     trailing ": " for it, so each input is `"<Category> volume"` / `"<Category> cost"` — e.g.
 *     `Catering and Food cost`. That is the ONLY stable handle: the ids are positional.
 *   - there is no `p:messages` panel. Results render as Carbon notifications with an explicit severity
 *     word in the title ("Success"), never colour alone.
 *
 * ELEVEN VOLUME FIELDS, TWELVE CATEGORIES — the asymmetry S01's BR-03 assertion depends on. GRID_ROWS
 * (components/schedule5/validation.ts) declares twelve `kind: 'category'` rows, of which `recoveries`
 * has `hasVolume: false` and renders an `AbsentCell` instead of an input. So the camp-volume
 * propagation lands in exactly eleven volume inputs, which is what the Gherkin says.
 *
 * DERIVED CELLS ARE READ-ONLY TEXT, not inputs: a row whose `readOnly` is true renders a bare
 * `TableCell` (index.tsx:203), so Camp Sub-Total / Camp Total / Access Expense Total / Camp and Access
 * are asserted as row TEXT, never via `inputValue()`.
 */
export class Schedule5Page {
  constructor(private readonly page: Page) {}

  // ---- readiness / navigation ---------------------------------------------------------------------

  /** The Existing Camps table — present once the schedule5 GET resolves (SECTION_HEADING). */
  get campsTable(): Locator {
    return this.page.getByRole('table', { name: 'Existing Camps' });
  }

  /** Open Schedule 5 via the side-nav (client-side, so the context saved on Home survives). */
  async openViaNav(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 5' });
    await expect(this.page).toHaveURL(/\/schedule-5$/);
    await expect(this.campsTable).toBeVisible();
  }

  // ---- the New Camp panel -------------------------------------------------------------------------

  get addNewCampButton(): Locator {
    return this.page.getByRole('button', { name: 'Add New Camp' });
  }

  /** The panel heading — the literal `New Camp Details` for a new camp, the camp's own name when editing. */
  get newCampPanelHeading(): Locator {
    return this.page.getByRole('heading', { name: 'New Camp Details' });
  }

  async openNewCampPanel(): Promise<void> {
    await this.addNewCampButton.click();
    await expect(this.newCampPanelHeading).toBeVisible();
  }

  // ---- descriptors --------------------------------------------------------------------------------

  /** The five descriptor controls, keyed by the vocabulary the feature file uses. */
  private descriptor(name: string): Locator {
    const labels: Record<string, string> = {
      'Camp Name': 'Camp Name',
      'Road Distance to Operating Area': 'Road Distance to Operating Area (km)',
      'Size of Camp': 'Size of Camp (number of persons)',
      'Associated Camp Volume': 'Associated Camp Volume (m³)',
      'Isolated Camp': 'Isolated Camp',
    };
    const label = labels[name];
    if (label === undefined) {
      throw new Error(
        `unknown Schedule 5 descriptor "${name}" — known: ${Object.keys(labels).join(', ')}`,
      );
    }
    return this.page.getByLabel(label, { exact: true });
  }

  async fillDescriptor(name: string, value: string): Promise<void> {
    await this.descriptor(name).fill(value);
  }

  async selectIsolatedCamp(text: string): Promise<void> {
    await this.descriptor('Isolated Camp').selectOption({ label: text });
  }

  async descriptorValue(name: string): Promise<string> {
    return this.descriptor(name).inputValue();
  }

  // ---- the category grid --------------------------------------------------------------------------

  /**
   * A category's volume or cost input. `category` is the GRID_ROWS label WITHOUT its trailing ": " —
   * exactly the accessible name the component builds (`row.label.replace(/:\s*$/, '')`).
   */
  categoryInput(category: string, half: 'volume' | 'cost'): Locator {
    return this.page.getByLabel(`${category} ${half}`, { exact: true });
  }

  async fillCategoryCost(category: string, value: string): Promise<void> {
    await this.categoryInput(category, 'cost').fill(value);
  }

  /** A derived row (`Camp Sub-Total: `, `Camp Total: `, …) as its whole table row, for text assertions. */
  derivedRow(label: string): Locator {
    return this.campsTablePanelRow(label);
  }

  /**
   * The grid row whose first cell starts with `label`. Scoped to the PANEL's grid rather than the page,
   * so the Existing Camps table cannot satisfy it. Carbon renders the grid as a table inside the panel;
   * the label cell keeps legacy's trailing ": ", so this matches on the leading text.
   */
  private campsTablePanelRow(label: string): Locator {
    return this.page
      .locator('.schedule-5__panel tr')
      .filter({ has: this.page.getByRole('cell', { name: new RegExp(`^${escapeRegExp(label)}`) }) });
  }

  // ---- results ------------------------------------------------------------------------------------

  get saveButton(): Locator {
    return this.page.getByRole('button', { name: 'Save', exact: true });
  }

  async save(): Promise<void> {
    await this.saveButton.click();
  }

  /** A row in the Existing Camps table, by camp name. */
  existingCampRow(campName: string): Locator {
    return this.campsTable.getByRole('row').filter({ hasText: campName });
  }
}

/** Escapes a literal for embedding in a RegExp — the derived labels contain `-` and `/`. */
function escapeRegExp(literal: string): string {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
