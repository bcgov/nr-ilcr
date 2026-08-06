import type { FC } from 'react'
import { Column, Dropdown, Grid, TextArea, TextInput } from '@carbon/react'
import type { Bridge, BridgeCodeLists, BridgeCodeOption } from '@/interfaces/Schedule7aResponse'
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

// Server-computed values render through a mask, never a recompute. A null total means "no
// contributing costs" and must render BLANK — showing "0" would assert a figure the data lacks.
const money = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : value.toLocaleString('en-US')

type Props = {
  readonly idPrefix: string
  readonly form: BridgeFormValues
  readonly errors: BridgeErrors
  readonly codeLists: BridgeCodeLists
  readonly disabled: boolean
  // Absent in the Add panel: a bridge that does not exist yet has no server-computed totals.
  readonly totals?: Bridge | null
  readonly onChange: <K extends keyof BridgeFormValues>(key: K, value: string) => void
}

const BridgeFields: FC<Props> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  totals,
  onChange,
}) => {
  const text = (
    field: keyof BridgeFormValues,
    label: string,
    extra: {
      inputMode?: 'numeric' | 'decimal'
      maxLength?: number
      placeholder?: string
      helperText?: string
    } = {},
  ) => (
    <TextInput
      id={`${idPrefix}-${field}`}
      labelText={label}
      size="sm"
      disabled={disabled}
      value={form[field]}
      invalid={Boolean(errors[field])}
      invalidText={errors[field]}
      onChange={(event) => onChange(field, event.target.value)}
      {...extra}
    />
  )

  const code = (field: CodeField) => {
    const spec = codeSpec(field)
    const items = codeLists[spec.list] as readonly BridgeCodeOption[]
    return (
      <Dropdown<BridgeCodeOption>
        id={`${idPrefix}-${field}`}
        titleText={spec.label}
        label="Select"
        size="sm"
        items={items as BridgeCodeOption[]}
        itemToString={(item) => item?.description ?? ''}
        selectedItem={items.find((item) => item.code === form[field]) ?? null}
        disabled={disabled}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
        onChange={({ selectedItem }) => onChange(field, selectedItem?.code ?? '')}
      />
    )
  }

  const cost = (field: CostField, label: string) => text(field, label, { inputMode: 'numeric' })

  // A read-only server total. Rendered as text rather than a disabled input so screen readers
  // announce a value instead of an unusable control.
  const total = (label: string, value: number | null | undefined) => (
    <div className="schedule-7a__total">
      <span className="schedule-7a__total-label">{label}</span>
      <span className="schedule-7a__total-value">{money(value)}</span>
    </div>
  )

  return (
    <Grid fullWidth condensed className="schedule-7a__fields">
      <Column sm={4} md={4} lg={5}>
        {text('locationName', 'Name/Location of Bridge', { maxLength: LOCATION_MAX_LENGTH })}
      </Column>
      <Column sm={4} md={2} lg={3}>
        {/* The required yyyy-MM shape is otherwise only discoverable by failing a save. */}
        {text('builtDate', 'Date', { placeholder: 'YYYY-MM', maxLength: 7 })}
      </Column>
      <Column sm={4} md={2} lg={4}>
        {code('constructionTypeCode')}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {text('lifeSpan', 'Expected Life Span', { inputMode: 'numeric' })}
      </Column>

      <Column sm={4} md={4} lg={4}>
        {code('superstructureTypeCode')}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {code('deckTypeCode')}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {code('abutmentTypeCode')}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {text('abutmentHeight', 'Abutments Ht.(m)', { inputMode: 'decimal' })}
      </Column>

      <Column sm={4} md={4} lg={4}>
        {code('loadRatingCode')}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {text('length', 'Length (m)', { inputMode: 'decimal' })}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {text('width', 'Width (m)', { inputMode: 'decimal' })}
      </Column>
      <Column sm={4} md={4} lg={4}>
        {text('distance', 'Distance (km)', { inputMode: 'numeric' })}
      </Column>

      {COST_ROWS.map((row) => (
        <Column key={row.secondary?.field ?? row.label} sm={4} md={8} lg={16}>
          <div className="schedule-7a__cost-row">
            <span className="schedule-7a__cost-label">{row.label}</span>
            <div className="schedule-7a__cost-triple">
              {row.triple ? (
                <>
                  {cost(row.triple[0].field, row.triple[0].label)}
                  {cost(row.triple[1].field, row.triple[1].label)}
                  {cost(row.triple[2].field, row.triple[2].label)}
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
            {total('Material', totals?.totalMaterial)}
            {total('Deliver', totals?.totalDeliver)}
            {total('Install', totals?.totalInstall)}
          </div>
          <div className="schedule-7a__cost-secondary" />
        </div>
      </Column>

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
          <div className="schedule-7a__cost-secondary">{cost('otherCost', 'Other Costs ($)')}</div>
        </div>
      </Column>
      <Column sm={4} md={8} lg={16}>
        {total('Grand Total ($)', totals?.grandTotal)}
      </Column>
    </Grid>
  )
}

export default BridgeFields
