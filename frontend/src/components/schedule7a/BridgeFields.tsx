import type { FC } from 'react'
import { Column, Dropdown, Grid, TextArea, TextInput } from '@carbon/react'
import type { Bridge, BridgeCodeLists, BridgeCodeOption } from '@/interfaces/Schedule7aResponse'
import { numStrGroup } from '@/utils/number'
import type { BridgeErrors, BridgeFormValues, CodeField, CostField } from './validation'
import { COMMENTS_MAX_LENGTH, LOCATION_MAX_LENGTH } from './validation'

// Legacy renders the same 27-field layout in the Add panel and in every list row, so both callers
// share this one editor. Labels and their order are transcribed from the legacy page and must not be
// reworded or resequenced — reporters navigate this form by shape.

type CodeSpec = { field: CodeField; label: string; list: keyof BridgeCodeLists }

const CODE_SPECS: readonly CodeSpec[] = [
  { field: 'constructionTypeCode', label: 'New/Used', list: 'constructionTypes' },
  { field: 'superstructureTypeCode', label: 'Superstructure Type', list: 'superstructureTypes' },
  { field: 'deckTypeCode', label: 'Decking Type', list: 'deckTypes' },
  { field: 'abutmentTypeCode', label: 'Abutments Type', list: 'abutmentTypes' },
  { field: 'loadRatingCode', label: 'Load Rating', list: 'loadRatings' },
]

const codeSpec = (field: CodeField): CodeSpec =>
  CODE_SPECS.find((spec) => spec.field === field) as CodeSpec

// The legacy cost grid is a matrix: three rows of Material/Deliver/Install, each paired with one
// standalone cost on the right.
// Each triple entry carries its own accessible label. The row heading and the column heading are
// separate visual cells in the legacy grid, so a control that inherited "<row> <column>" would be
// named something written nowhere in the source ("Superstructure ($) Material"); these spell the
// field out instead, keeping the money unit where the other labels put it.
type CostCell = { field: CostField; label: string }

type CostRowSpec = {
  label: string
  triple: readonly [CostCell, CostCell, CostCell] | null
  secondary: CostCell | null
}

const COST_ROWS: readonly CostRowSpec[] = [
  {
    label: '',
    triple: null,
    secondary: { field: 'sitePlanCost', label: 'Site Plan / Gen. Arr. ($)' },
  },
  {
    label: 'Superstructure ($)',
    triple: [
      { field: 'superstructureMaterialCost', label: 'Superstructure Material ($)' },
      { field: 'superstructureDeliverCost', label: 'Superstructure Deliver ($)' },
      { field: 'superstructureInstallCost', label: 'Superstructure Install ($)' },
    ],
    secondary: { field: 'approachCost', label: 'Approach works ($)' },
  },
  {
    label: 'Abutments ($)',
    triple: [
      { field: 'abutmentMaterialCost', label: 'Abutments Material ($)' },
      { field: 'abutmentDeliverCost', label: 'Abutments Deliver ($)' },
      { field: 'abutmentInstallCost', label: 'Abutments Install ($)' },
    ],
    secondary: { field: 'afterInstallCost', label: 'Certification After install ($)' },
  },
]

// Server-computed totals render through the shared en-CA numStrGroup (Story 29.8 — one app locale, no
// local en-US copy). A null total means "no contributing costs" and must render BLANK — numStrGroup
// returns '' on null, so "0" (a figure the data lacks) is never shown.

type Props = {
  readonly idPrefix: string
  readonly form: BridgeFormValues
  readonly errors: BridgeErrors
  readonly codeLists: BridgeCodeLists
  readonly disabled: boolean
  // The four derived totals: the served bridge for an untouched row, the display-only mirror once the
  // reporter has committed a cost, and the mirror from the start in the Add panel (#291).
  readonly totals?: Pick<
    Bridge,
    'totalMaterial' | 'totalDeliver' | 'totalInstall' | 'grandTotal'
  > | null
  readonly onChange: <K extends keyof BridgeFormValues>(key: K, value: string) => void
  // Re-group a money field once the user leaves it (schedule 3's `groupField` idiom). On blur rather
  // than on change so inserting a separator mid-word cannot move the caret while typing.
  readonly onGroup: (key: CostField) => void
}

const BridgeFields: FC<Props> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  totals,
  onChange,
  onGroup,
}) => {
  const text = (
    field: keyof BridgeFormValues,
    label: string,
    extra: {
      inputMode?: 'numeric' | 'decimal'
      maxLength?: number
      placeholder?: string
      helperText?: string
      hideLabel?: boolean
      onBlur?: () => void
      // Money only. Legacy right-aligns the cost boxes so a column of amounts lines up on its digits
      // and meets the total below it, but leaves the measurements (life span, height, length, width,
      // distance) left-aligned — so this cannot be inferred from `inputMode`, which both groups set.
      rightAlign?: boolean
    } = {},
  ) => {
    // Split off: `rightAlign` is ours, not a TextInput prop, and would reach the DOM via the spread.
    const { rightAlign, ...inputProps } = extra
    return (
      <TextInput
        id={`${idPrefix}-${field}`}
        labelText={label}
        size="sm"
        className={rightAlign ? 'schedule-7a__num' : undefined}
        disabled={disabled}
        value={form[field]}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
        onChange={(event) => onChange(field, event.target.value)}
        {...inputProps}
      />
    )
  }

  const code = (field: CodeField) => {
    const spec = codeSpec(field)
    const items = codeLists[spec.list] as readonly BridgeCodeOption[]
    const selected = items.find((item) => item.code === form[field]) ?? null
    return (
      <Dropdown<BridgeCodeOption>
        id={`${idPrefix}-${field}`}
        titleText={spec.label}
        label="Select"
        size="sm"
        // NO `title` here, deliberately (#295 code review). The New/Used descriptions run to 49
        // characters ("RU-Replacement installation with a Used structure"), so a narrow cell truncates
        // the closed control — but Carbon ALREADY sets `title={itemToString(selectedItem)}` on the
        // control itself (Dropdown.js:275), so the hover text needs nothing from us. Passing `title`
        // made it worse: Carbon spreads unknown props onto the WRAPPER, and the menu is a descendant of
        // that wrapper, so the selected option's tooltip floated over the open list — the one place the
        // whole description is readable. The other reading is the open menu, which the app already wraps
        // app-wide (`styles/_overrides.scss`, added for the shared code selectors).
        items={items as BridgeCodeOption[]}
        itemToString={(item) => item?.description ?? ''}
        // `null`, not `undefined`: an undefined `selectedItem` hands the control back to downshift's
        // internal state, so a cleared code would leave the old label on screen. The cast is Carbon's
        // own type inconsistency — its `onChange` hands back `ItemType | null` while the prop is
        // declared `ItemType | undefined` (Dropdown.d.ts:13 vs :123).
        selectedItem={selected as BridgeCodeOption | undefined}
        disabled={disabled}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
        onChange={({ selectedItem }) => onChange(field, selectedItem?.code ?? '')}
      />
    )
  }

  // Inside the Material/Deliver/Install matrix the row label and the column heading already name the
  // cell, and legacy prints no third caption — `hideLabel` drops the visible text while Carbon keeps
  // the accessible name, so a screen reader still hears "Superstructure Material ($)".
  // Money displays with thousands separators, as the legacy costConverter formatted it. Every parse
  // of a form string already strips grouping (`parseDecimalInput`), so the grouped text is display
  // only and never reaches the wire.
  const cost = (field: CostField, label: string, hideLabel = false) =>
    text(field, label, {
      inputMode: 'numeric',
      hideLabel,
      rightAlign: true,
      onBlur: () => onGroup(field),
    })

  // A server-computed total. Rendered the way every other schedule renders a derived value (see
  // schedule-3__num / schedule-4__num): plain right-aligned text at normal weight, never a control.
  // The label stays in the accessible tree even when hidden, so the figure is announced with a name —
  // the visual cue that these are not editable is the absence of a field, which a screen reader
  // cannot see.
  const total = (label: string, value: number | null | undefined, hideLabel = false) => (
    <div className="schedule-7a__total">
      <span className={hideLabel ? 'cds--visually-hidden' : 'schedule-7a__total-label'}>
        {label}
      </span>
      <span className="schedule-7a__total-value">{numStrGroup(value)}</span>
    </div>
  )

  return (
    <Grid fullWidth condensed className="schedule-7a__fields">
      {/* Legacy lays the twelve attribute fields out three-across, reading left-to-right then down
          (schedule7A.xhtml:243-411). The sequence is load-bearing — reporters transcribe from a
          paper form in this order — so it stays a plain CSS grid of equal thirds rather than Carbon
          columns, which cannot split sixteen into three even parts. */}
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-7a__field-grid">
          {text('locationName', 'Name/Location of Bridge', { maxLength: LOCATION_MAX_LENGTH })}
          {/* The required yyyy-MM shape is otherwise only discoverable by failing a save. */}
          {text('builtDate', 'Date', { placeholder: 'YYYY-MM', maxLength: 7 })}
          {code('constructionTypeCode')}

          {text('lifeSpan', 'Expected Life Span', { inputMode: 'numeric' })}
          {code('superstructureTypeCode')}
          {code('deckTypeCode')}

          {code('abutmentTypeCode')}
          {text('abutmentHeight', 'Abutments Ht.(m)', { inputMode: 'decimal' })}
          {code('loadRatingCode')}

          {text('length', 'Length (m)', { inputMode: 'decimal' })}
          {text('width', 'Width (m)', { inputMode: 'decimal' })}
          {text('distance', 'Distance (km)', { inputMode: 'numeric' })}
        </div>
      </Column>

      {COST_ROWS.map((row) => (
        <Column key={row.secondary?.field ?? row.label} sm={4} md={8} lg={16}>
          <div className="schedule-7a__cost-row">
            <span className="schedule-7a__cost-label">{row.label}</span>
            <div className="schedule-7a__cost-triple">
              {row.triple ? (
                <>
                  {cost(row.triple[0].field, row.triple[0].label, true)}
                  {cost(row.triple[1].field, row.triple[1].label, true)}
                  {cost(row.triple[2].field, row.triple[2].label, true)}
                </>
              ) : (
                <>
                  <span className="schedule-7a__cost-heading">Material</span>
                  <span className="schedule-7a__cost-heading">Deliver</span>
                  <span className="schedule-7a__cost-heading">Install</span>
                </>
              )}
            </div>
            {row.secondary && (
              <div className="schedule-7a__cost-secondary">
                {cost(row.secondary.field, row.secondary.label)}
              </div>
            )}
          </div>
        </Column>
      ))}

      {/* Legacy's Total row carries no fourth cost — Other Costs sits beside Comments on the row
          below (schedule7A.xhtml:412-447 vs :448-480). */}
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-7a__cost-row">
          <span className="schedule-7a__cost-label">Total ($)</span>
          <div className="schedule-7a__cost-triple">
            {total('Material', totals?.totalMaterial, true)}
            {total('Deliver', totals?.totalDeliver, true)}
            {total('Install', totals?.totalInstall, true)}
          </div>
          <div className="schedule-7a__cost-secondary" />
        </div>
      </Column>

      {/* Grand Total closes the right-hand standalone-cost column directly under Other Costs, as in
          legacy — not as a full-width row of its own. */}
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-7a__cost-row">
          <span className="schedule-7a__cost-label">Comments</span>
          <TextArea
            id={`${idPrefix}-comments`}
            labelText="Comments"
            hideLabel
            rows={2}
            enableCounter
            maxCount={COMMENTS_MAX_LENGTH}
            disabled={disabled}
            value={form.comments}
            invalid={Boolean(errors.comments)}
            invalidText={errors.comments}
            onChange={(event) => onChange('comments', event.target.value)}
          />
          <div className="schedule-7a__cost-secondary">
            {cost('otherCost', 'Other Costs ($)')}
            {total('Grand Total ($)', totals?.grandTotal)}
          </div>
        </div>
      </Column>
    </Grid>
  )
}

export default BridgeFields
