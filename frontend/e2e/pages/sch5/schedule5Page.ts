import { type Locator, type Page, expect } from '@playwright/test';
import { navigateViaSideNav } from '../common/authNav';
import { MILL_YEAR_STORAGE_KEY } from '../../fixtures/sch5/schedule5-test-data';

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

  /**
   * Navigate for a GUARD state (S17's closed mill -> 409, S18's absent record -> 404).
   *
   * Separate from `openViaNav` because that one waits for the Existing Camps table, which a guard
   * state never renders: the document GET fails, so the component returns a `PageState` carrying only
   * the error notification (index.tsx:1151-1161). Waiting on the table here would time out on a page
   * that is behaving exactly as the slice requires.
   */
  async openViaNavExpectingGuard(): Promise<void> {
    await navigateViaSideNav(this.page, { group: 'Schedules', link: 'Schedule 5' });
    await expect(this.page).toHaveURL(/\/schedule-5/);
  }

  /**
   * Open Schedule 5 with NO working context (S16).
   *
   * Seeds an EMPTY MillYearContext into localStorage — the supported empty state
   * (`MillYearProvider.tsx`) — and loads the route directly, so the page renders its `contextMissing`
   * guard (index.tsx:1130-1141) without ever issuing a request. Going through Home and declining to
   * pick a mill would leave the context unset rather than empty, which is a different state.
   */
  async openWithNoContext(): Promise<void> {
    await this.page.addInitScript(
      ([key]) => {
        window.localStorage.setItem(key, JSON.stringify({ millId: null, year: null }));
      },
      [MILL_YEAR_STORAGE_KEY],
    );
    await this.page.goto('/schedule-5');
    await expect(this.page).toHaveURL(/\/schedule-5$/);
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

  /**
   * Click Edit WITHOUT waiting for the panel — for S11, where a confirm intercepts the switch.
   *
   * Separate from `openEditPanel` on purpose: that one asserts the panel opened, which is exactly what
   * must NOT happen yet when another panel is dirty (`setPendingSwitch`, index.tsx:1216-1220).
   */
  async clickEditFor(campName: string): Promise<void> {
    await this.editButtonFor(campName).click();
  }

  /** The panel's Close button. */
  get closeButton(): Locator {
    return this.page.getByRole('button', { name: 'Close', exact: true });
  }

  /** No camp panel is open — neither the new-camp literal nor any camp's own heading. */
  async expectPanelClosed(campName?: string): Promise<void> {
    await expect(this.newCampPanelHeading).toHaveCount(0);
    if (campName !== undefined) {
      await expect(this.campPanelHeading(campName)).toHaveCount(0);
    }
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

  /**
   * Fill a descriptor AND blur, so its validator reports.
   *
   * "Blur is the commit point: a field's error appears only once the licensee has left it"
   * (index.tsx:734). A bare `fill()` leaves the field invalid but SILENT, so an inline-error assertion
   * would fail while the app is behaving correctly.
   */
  async fillDescriptorAndCommit(name: string, value: string): Promise<void> {
    const input = this.descriptor(name);
    await input.fill(value);
    await input.blur();
  }

  /**
   * The Carbon error rendered under a control, found from the control itself rather than from an id.
   *
   * Every `invalid`/`invalidText` Carbon field renders `.cds--form-requirement` inside its enclosing
   * `.cds--form-item`, so walking up from the labelled input scopes the message to that field without
   * needing to know the id — which matters here because the category grid's ids are POSITIONAL.
   */
  private errorFor(input: Locator): Locator {
    return input
      .locator('xpath=ancestor::div[contains(@class,"cds--form-item")][1]')
      .locator('.cds--form-requirement');
  }

  descriptorError(name: string): Locator {
    return this.errorFor(this.descriptor(name));
  }

  categoryError(category: string, half: 'volume' | 'cost'): Locator {
    return this.errorFor(this.categoryInput(category, half));
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

  /**
   * Fill a category cost AND commit it, so the derived rows recompute.
   *
   * BLUR IS THE COMMIT POINT. The four derived rows mirror the COMMITTED entry while the document is
   * editable (#291), not the keystroke — the app's own unit tests pin that ("not per keystroke"). A
   * bare `fill()` therefore leaves Camp Sub-Total / Camp Total showing the previous figures, and a
   * scenario asserting the live recompute would fail for a reason that has nothing to do with the
   * arithmetic it is testing.
   */
  async fillCategoryCostAndCommit(category: string, value: string): Promise<void> {
    const input = this.categoryInput(category, 'cost');
    await input.fill(value);
    await input.blur();
  }

  /** A derived row (`Camp Sub-Total: `, `Camp Total: `, …) as its whole table row, for text assertions. */
  derivedRow(label: string): Locator {
    return this.campsTablePanelRow(label);
  }

  /**
   * The grid row whose first cell starts with `label`. Scoped to the PANEL's grid rather than the page,
   * so the Existing Camps table cannot satisfy it.
   *
   * THE LABEL IS TRIMMED BEFORE MATCHING. GRID_ROWS keeps legacy's trailing ": " ("Camp Sub-Total: "),
   * but an accessible NAME is whitespace-normalised and trimmed, so a pattern ending in a space can
   * never match and the row silently resolves to nothing. Trimming keeps the specs readable — they
   * quote the label exactly as the app declares it — while the match uses the normalised form.
   * Anchoring with `^` still separates "Camp Total:" from "Camp Sub-Total:".
   */
  private campsTablePanelRow(label: string): Locator {
    const normalized = label.trim();
    return this.page
      .locator('.schedule-5__panel tr')
      .filter({
        has: this.page.getByRole('cell', { name: new RegExp(`^${escapeRegExp(normalized)}`) }),
      });
  }

  // ---- check status (S06) / delete (S07) ------------------------------------------------------------

  /**
   * Check Status is DISABLED while a panel is open (index.tsx:1419) — legacy's button was a full
   * postback so its verdict always reflected the screen, whereas the modern check reads only the
   * database. A scenario must therefore close any panel before running it.
   */
  get checkStatusButton(): Locator {
    return this.page.getByRole('button', { name: 'Check Status' });
  }

  async runCheckStatus(): Promise<void> {
    await expect(
      this.checkStatusButton,
      'Check Status is disabled while a camp panel is open — close the panel first',
    ).toBeEnabled();
    await this.checkStatusButton.click();
  }

  /** The `Delete` action on a camp's row — scoped to the row, like Edit and Copy. */
  deleteButtonFor(campName: string): Locator {
    return this.existingCampRow(campName).getByRole('button', { name: 'Delete' });
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

  // ---- sub-page validation (S21 / S22 / S23) --------------------------------------------------------

  /**
   * The inline error under an ADD-FORM field.
   *
   * Reuses `errorFor`, which walks up to the enclosing `.cds--form-item` — so it is scoped to the one
   * field without depending on an id, and works identically for the descriptor fields on the main
   * page and the add-form fields here.
   */
  subPageAddFieldError(field: 'Description' | 'Cost $'): Locator {
    return this.errorFor(this.subPageField(field));
  }

  /**
   * A GRID row's Description or Cost input, by row index.
   *
   * Addressed by the app's own stable ids (`sub-page-row-description-0`) rather than by accessible
   * name: both grid inputs are `hideLabel` with the labels `Description` / `Cost $`, and the ADD form
   * carries `Description: ` / `Cost $: ` — close enough that a name-based locator has to lean on the
   * trailing colon to tell a grid cell from the add form. The id says which row as well as which
   * field, which is what these scenarios actually need.
   */
  subPageRowInput(index: number, field: 'description' | 'cost'): Locator {
    return this.page.locator(`#sub-page-row-${field}-${index}`);
  }

  subPageRowError(index: number, field: 'description' | 'cost'): Locator {
    return this.errorFor(this.subPageRowInput(index, field));
  }

  /** Every data row currently in a sub-page list — the count is how "the row is not added" is proved. */
  subPageRows(listHeader: string): Locator {
    return this.subPageList(listHeader).getByRole('row').filter({
      has: this.page.getByRole('textbox', { name: 'Description', exact: true }),
    });
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

  // ---- guards (S16 / S17 / S18) and the read-only view (S19) ----------------------------------------

  /**
   * A Carbon notification carrying `text` — the title or the subtitle.
   *
   * Addressed by ROLE rather than by Carbon's `.cds--inline-notification` class: Carbon gives the
   * container `role="status"` for every kind, error included, and a role survives a Carbon class
   * rename where a class would not. Same choice as `pages/sch4/schedule4Page.ts:518`.
   */
  notification(text: string): Locator {
    return this.page.getByRole('status').filter({ hasText: text });
  }

  /** The whole data-entry surface the three guards must suppress. */
  async expectDataEntrySuppressed(): Promise<void> {
    await expect(this.campsTable).toHaveCount(0);
    await expect(this.addNewCampButton).toHaveCount(0);
    await expect(this.checkStatusButton).toHaveCount(0);
  }

  /**
   * The `View` action on a camp's row — the ONLY action a non-editable document renders.
   *
   * On an editable document this resolves nothing: `rowActions` returns the Edit/Delete/Copy trio and
   * View does not exist (index.tsx:1193-1208). That asymmetry is the assertion, so S19 checks both
   * directions rather than only the presence of View.
   */
  viewButtonFor(campName: string): Locator {
    return this.existingCampRow(campName).getByRole('button', { name: 'View', exact: true });
  }

  /** Every write action a camp row can carry — asserted ABSENT on a read-only document. */
  rowWriteActionsFor(campName: string): Locator {
    return this.existingCampRow(campName).getByRole('button', {
      name: /^(Edit|Delete|Copy)$/,
    });
  }

  async openViewPanel(campName: string): Promise<void> {
    await this.viewButtonFor(campName).click();
    await expect(this.campPanelHeading(campName)).toBeVisible();
  }

  /**
   * A descriptor is READ-ONLY but still an input.
   *
   * This is where Schedule 5 differs from Schedule 4, and the difference is worth pinning rather than
   * glossing. Schedule 4's view mode renders its values as text; Schedule 5 keeps the Carbon
   * `TextInput`s and sets `readOnly` on them (index.tsx:438-478), so the value is still in
   * `inputValue()` and the proof of read-only-ness is the attribute. `Isolated Camp` is the odd one
   * out — it is a `Select`, which Carbon disables rather than marking readonly (:479-483).
   */
  async expectDescriptorReadOnly(name: string): Promise<void> {
    await expect(this.descriptor(name)).toHaveAttribute('readonly', '');
  }

  async expectIsolatedCampDisabled(): Promise<void> {
    await expect(this.descriptor('Isolated Camp')).toBeDisabled();
  }

  /**
   * The category grid holds NO inputs at all in view mode.
   *
   * `AmountCell` renders a bare `TableCell` when read-only (index.tsx:202-203), so this is what makes
   * the value assertions meaningful: they are reading rendered text, not the contents of pre-filled
   * boxes a user could still type into.
   */
  async expectCategoryGridReadOnly(): Promise<void> {
    await expect(this.page.locator('.schedule-5__panel table').getByRole('textbox')).toHaveCount(0);
  }

  /** A category row as its whole table row, for text assertions when the cells are not inputs. */
  categoryRow(label: string): Locator {
    return this.campsTablePanelRow(label);
  }
}

/** Escapes a literal for embedding in a RegExp — the derived labels contain `-` and `/`. */
function escapeRegExp(literal: string): string {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
