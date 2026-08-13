import type { FC } from 'react'
import { Column, Dropdown, Grid, TextArea, TextInput } from '@carbon/react'
import type { CulvertCodeLists, CulvertCodeOption } from '@/interfaces/Schedule7bResponse'
import type { CulvertErrors, CulvertFormValues, CostField, MaskedField } from './validation'
import { COMMENTS_MAX_LENGTH, previewTotalCost } from './validation'

// Legacy renders the same nine-field layout in the Add panel and in every list row
// (schedule7B.xhtml:73-232 vs :286-520), so both callers share this one editor. Labels and their order
// are transcribed from the legacy page and must not be reworded or resequenced: reporters navigate
// this form by shape. The ONE deliberate departure is spacing before a unit suffix — legacy's spacing
// is erratic ("Span (mm)" beside "Rise(mm)" and "Length(m)"), and the team asked for the space
// everywhere, so the wording is legacy's but the gap is ours.

// A total renders through a mask, never a recompute of a served figure. A null total means "no
// contributing costs" and must render BLANK — showing "0" would assert a figure the data lacks.
const money = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : value.toLocaleString('en-US')

type Props = {
  readonly idPrefix: string
  readonly form: CulvertFormValues
  readonly errors: CulvertErrors
  readonly codeLists: CulvertCodeLists
  readonly disabled: boolean
  /**
   * The SERVED total for this row (BR-05, computed server-side). Passed only while the row is
   * untouched, which is when it is the authoritative figure (AD-5); an edited row — and the Add
   * panel, which has no stored row — passes nothing and gets the live preview instead, reproducing
   * legacy's re-render of the total on every cost change.
   */
  readonly serverTotal?: number | null
  readonly onChange: <K extends keyof CulvertFormValues>(key: K, value: string) => void
  // Re-apply a numeric field's legacy display mask once the user leaves it. On blur rather than on
  // change so inserting a separator (or a forced decimal) mid-word cannot move the caret while typing.
  readonly onMask: (key: MaskedField) => void
}

const CulvertFields: FC<Props> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  serverTotal,
  onChange,
  onMask,
}) => {
  const text = (
    field: keyof CulvertFormValues,
    label: string,
    inputProps: {
      inputMode?: 'numeric' | 'decimal'
      onBlur?: () => void
    } = {},
  ) => {
    return (
      <TextInput
        id={`${idPrefix}-${field}`}
        labelText={label}
        size="sm"
        disabled={disabled}
        value={form[field]}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
        // Legacy carried `autocomplete="off"` on every one of these inputs (schedule7B.xhtml:99,
        // :113, :138, :151, :175, :185 and the row equivalents) — a culvert's measurements are not
        // the kind of value a browser should be offering from a previous form.
        autoComplete="off"
        onChange={(event) => onChange(field, event.target.value)}
        {...inputProps}
      />
    )
  }

  /**
   * A masked numeric field. EVERY numeric input on this page carried a legacy display mask, so all of
   * them re-format on blur — not just the money. Each parse of a form string strips the grouping
   * (`parseDecimalInput`), so the mask is display only and never reaches the wire.
   */
  const masked = (field: MaskedField, label: string, inputMode: 'numeric' | 'decimal') =>
    text(field, label, { inputMode, onBlur: () => onMask(field) })

  const cost = (field: CostField, label: string) => masked(field, label, 'numeric')

  const items = codeLists.culvertTypes as CulvertCodeOption[]

  return (
    <Grid fullWidth condensed className="schedule-7b__fields">
      {/* Legacy lays these out three-across, reading left-to-right then down: Type / Span / Rise, then
          Length / No of Pieces, then the two costs and the read-only total. The sequence is
          load-bearing — reporters transcribe from a paper form in this order — so it stays a plain CSS
          grid of equal thirds rather than Carbon columns, which cannot split sixteen into three even
          parts. */}
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-7b__field-grid">
          <Dropdown<CulvertCodeOption>
            id={`${idPrefix}-culvertTypeCode`}
            titleText="Type"
            label="Select"
            size="sm"
            items={items}
            itemToString={(item) => item?.description ?? ''}
            // `null`, not `undefined`: an undefined `selectedItem` hands the control back to
            // downshift's internal state, so a cleared code would leave the old label on screen. The
            // cast is Carbon's own type inconsistency — its `onChange` hands back `ItemType | null`
            // while the prop is declared `ItemType | undefined` (Dropdown.d.ts:13 vs :123).
            selectedItem={
              (items.find((item) => item.code === form.culvertTypeCode) ?? null) as
                CulvertCodeOption | undefined
            }
            disabled={disabled}
            invalid={Boolean(errors.culvertTypeCode)}
            invalidText={errors.culvertTypeCode}
            onChange={({ selectedItem }) => onChange('culvertTypeCode', selectedItem?.code ?? '')}
          />
          {masked('spanSize', 'Span (mm)', 'numeric')}
          {masked('riseSize', 'Rise (mm)', 'numeric')}

          {masked('length', 'Length (m)', 'decimal')}
          {masked('culvertPieceCount', 'No of Pieces', 'numeric')}
          {/* Legacy's second row holds only two fields; the spacer keeps the cost row below it on the
              same three-column rhythm. */}
          <div />

          {cost('materialCost', 'Material costs ($)')}
          {cost('installCost', 'Install costs ($)')}
          {/* Total costs is server-derived (BR-05) and legacy renders it `disabled="true"`
              (schedule7B.xhtml:197), so it is never a control here either. An untouched row shows the
              SERVED figure — the authoritative one (AD-5), never recomputed from the fields beside
              it. Legacy did, however, keep the total current as you typed, re-rendering it on every
              cost change (`:180,190` add form, `:440,460` rows), so once a row is edited (and in the
              Add panel, where nothing is stored yet) `previewTotalCost` takes over; it is display
              only and never sent, and the served figure replaces it on the next echo.
              Rendered the way every other schedule renders a derived value: plain text at normal
              weight, never a control. The label stays in the accessible tree, so the figure
              is announced with a name — the visual cue that this is not editable is the absence of a
              field, which a screen reader cannot see. */}
          <div className="schedule-7b__total">
            <span className="schedule-7b__total-label">Total costs ($)</span>
            <span className="schedule-7b__total-value">
              {money(serverTotal === undefined ? previewTotalCost(form) : serverTotal)}
            </span>
          </div>
        </div>
      </Column>

      {/* Comments keeps its label ABOVE the field: legacy gives it a row of its own spanning the grid
          (schedule7B.xhtml:214-231), not the label-beside-field treatment of the nine cells above. */}
      <Column sm={4} md={8} lg={16}>
        <TextArea
          id={`${idPrefix}-comments`}
          labelText="Comments"
          // Legacy sized this box `rows="10"` (schedule7B.xhtml:221,490) — ten rows, not the two its
          // Schedule 7A twin used (`schedule7A.xhtml:454,1168`). A culvert's comment is where the
          // "Others" type justifies itself, so legacy gave it real room; the difference between the
          // two pages is deliberate on legacy's part and is reproduced.
          rows={10}
          // `enableCounter` + `maxCount` also applies the hard `maxLength` legacy set alongside its
          // counter, so typing stops at the cap exactly as it did there. The counter itself reads
          // used-of-limit ("14/3500") rather than legacy's `{0} characters remaining.` — Carbon's
          // built-in direction, matched to the Schedule 7A twin at the team's call (recorded
          // deviation).
          enableCounter
          maxCount={COMMENTS_MAX_LENGTH}
          disabled={disabled}
          value={form.comments}
          invalid={Boolean(errors.comments)}
          invalidText={errors.comments}
          onChange={(event) => onChange('comments', event.target.value)}
        />
      </Column>
    </Grid>
  )
}

export default CulvertFields
