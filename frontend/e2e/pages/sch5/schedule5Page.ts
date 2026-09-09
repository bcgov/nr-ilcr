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

  // ---- the existing-camp panel --------------------------------------------------------------------

  /**
   * The `Edit` action on a camp's row.
   *
   * Scoped to the row, not the page: every row carries its own Edit/Copy/Delete trio, so an unscoped
   * `getByRole('button', { name: 'Edit' })` resolves one per camp and throws in strict mode the moment
   * a scenario has two (S11's camp-switch will).
   *
   * On a NON-editable document the row renders `View` instead of the Edit/Copy/Delete trio
   * (index.tsx:1198-1207) — so a failure to find Edit here means the document is read-only, which is
   * S19's fixture rather than a broken locator.
   */
  editButtonFor(campName: string): Locator {
    return this.existingCampRow(campName).getByRole('button', { name: 'Edit' });
  }

  /**
   * The open panel's heading. An EXISTING camp's panel is headed by the camp's OWN name; only the new
   * camp panel uses the `New Camp Details` literal (index.tsx:1280-1284).
   */
  campPanelHeading(campName: string): Locator {
    return this.page.getByRole('heading', { name: campName });
  }

  async openEditPanel(campName: string): Promise<void> {
    await this.editButtonFor(campName).click();
    await expect(this.campPanelHeading(campName)).toBeVisible();
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

  async categoryValue(category: string, half: 'volume' | 'cost'): Promise<string> {
    return this.categoryInput(category, half).inputValue();
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

  // ---- copy (S03) ---------------------------------------------------------------------------------

  /** The `Copy` action on a camp's row — scoped to the row for the same reason as Edit. */
  copyButtonFor(campName: string): Locator {
    return this.existingCampRow(campName).getByRole('button', { name: 'Copy' });
  }

  /**
   * Copy opens the NEW-camp panel pre-filled, so its heading is the `New Camp Details` literal rather
   * than the source camp's name — `panelMode` is 'copy', which is neither 'edit' nor 'view'
   * (index.tsx:1282-1284). Asserting that is what distinguishes a copy from having reopened the source.
   */
  async copyCamp(campName: string): Promise<void> {
    await this.copyButtonFor(campName).click();
    await expect(this.newCampPanelHeading).toBeVisible();
  }

  // ---- expense sub-pages (S04 / S05) ----------------------------------------------------------------

  /**
   * The grid row label that navigates to a sub-page. It is a `Button kind="ghost"` whose text carries
   * the LIVE row count — `Other Camp Expenses (0): ` — so this matches on the prefix rather than the
   * whole string, and `subPageLinkWithCount` is used where the count itself is the assertion.
   */
  subPageLink(gridLabel: string): Locator {
    return this.page.getByRole('button', { name: new RegExp(`^${escapeRegExp(gridLabel)}\\s*\\(`) });
  }

  /** The same control, pinned to an exact count — for "the label reflects the updated count". */
  subPageLinkWithCount(gridLabel: string, count: number): Locator {
    return this.page.getByRole('button', {
      name: new RegExp(`^${escapeRegExp(gridLabel)}\\s*\\(${count}\\)`),
    });
  }

  /** Sub-page add-form fields. Carbon keeps the trailing ": " in the accessible name. */
  subPageField(field: 'Description' | 'Volume' | 'Cost $'): Locator {
    return this.page.getByLabel(`${field}: `, { exact: true });
  }

  get subPageAddButton(): Locator {
    return this.page.getByRole('button', { name: 'Add', exact: true });
  }

  get subPageBackButton(): Locator {
    return this.page.getByRole('button', { name: 'Back', exact: true });
  }

  /** The sub-page's own list table, by its header ("Other Camp Expenses" / "Other Access Expenses"). */
  subPageList(listHeader: string): Locator {
    return this.page.getByRole('table', { name: listHeader });
  }

  /**
   * A row in the sub-page list, found by its Description INPUT's value.
   *
   * Not `filter({ hasText })`: the list's Description and Cost cells are editable `hideLabel`
   * TextInputs, so the typed text lives in `value` and is not in the row's text content at all. Only
   * Volume and `$/m³` are rendered as text (index.tsx:541-545), which is why the volume assertion can
   * still read the row's text.
   */
  subPageRow(listHeader: string, description: string): Locator {
    return this.subPageList(listHeader)
      .getByRole('row')
      .filter({ has: this.page.getByRole('textbox', { name: 'Description' }) })
      .filter({ has: this.page.locator(`input[value="${description}"]`) });
  }

  /** The Description inputs in the list — used to assert a value without depending on row order. */
  subPageDescriptions(listHeader: string): Locator {
    return this.subPageList(listHeader).getByRole('textbox', { name: 'Description' });
  }

  /** Every Schedule 5 confirm is a Carbon Modal with Yes/No; the heading is what distinguishes them. */
  async confirmModal(heading: string): Promise<void> {
    const modal = this.page.getByRole('presentation').filter({ hasText: heading });
    await expect(modal.first()).toBeVisible();
    await this.page.getByRole('button', { name: 'Yes', exact: true }).click();
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
