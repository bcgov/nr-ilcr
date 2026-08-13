import type { FC } from 'react'
import { Column, Dropdown, Grid, TextArea, TextInput } from '@carbon/react'
import type { CodeDescription, Schedule9CodeLists } from '@/interfaces/Schedule9Response'
import { numStrFixed } from '@/utils/number'
import type { MaskedField, RecordErrors, RecordFormValues } from './validation'
import {
  COMMENTS_MAX_LENGTH,
  itemDescriptionEnabled,
  previewCostPerUnit,
  sideSlopeEnabled,
  sourceDescriptionEnabled,
  unitDescriptionEnabled,
} from './validation'

// Legacy renders the same field set in the Add panel and in every list row (schedule9.xhtml add form
// vs the accordion rows), so both callers share this one editor. Labels and their ORDER are
// transcribed from the legacy page — Company ID, Contractual Item, Item Other Description, Side Slope,
// Number of Units, Unit Type, Unit Other Description, Biogeoclimatic Zone, Cost, Source, Source Other
// Description, $/Unit, Comments — and must not be reworded or resequenced; reporters navigate by shape.

type Props = {
  readonly idPrefix: string
  readonly form: RecordFormValues
  readonly errors: RecordErrors
  readonly codeLists: Schedule9CodeLists
  readonly disabled: boolean
  /**
   * The SERVED $/Unit for this row (derived server-side, AD-5). Passed only while the row is
   * untouched — the authoritative figure; an edited row and the Add panel pass nothing and get the
   * live {@code previewCostPerUnit} instead, reproducing legacy's recompute as cost/units change.
   */
  readonly servedCostPerUnit?: number | null
  readonly onChange: <K extends keyof RecordFormValues>(key: K, value: string) => void
  // Re-apply a numeric field's legacy display mask once the user leaves it (blur, not change).
  readonly onMask: (key: MaskedField) => void
}

const ContractualWorkFields: FC<Props> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  servedCostPerUnit,
  onChange,
  onMask,
}) => {
  const text = (
    field: keyof RecordFormValues,
    label: string,
    inputProps: {
      inputMode?: 'numeric' | 'decimal'
      disabled?: boolean
      onBlur?: () => void
    } = {},
  ) => (
    <TextInput
      id={`${idPrefix}-${field}`}
      labelText={label}
      size="sm"
      disabled={disabled || Boolean(inputProps.disabled)}
      value={form[field]}
      invalid={Boolean(errors[field])}
      invalidText={errors[field]}
      autoComplete="off"
      onChange={(event) => onChange(field, event.target.value)}
      inputMode={inputProps.inputMode}
      onBlur={inputProps.onBlur}
    />
  )

  const masked = (
    field: MaskedField,
    label: string,
    inputMode: 'numeric' | 'decimal',
    off = false,
  ) => text(field, label, { inputMode, disabled: off, onBlur: () => onMask(field) })

  const select = (
    field: 'contractualItemCode' | 'unitCode' | 'biogeoclimaticZone' | 'sourceCode',
    label: string,
    items: readonly CodeDescription[],
  ) => {
    const options = items as CodeDescription[]
    return (
      <Dropdown<CodeDescription>
        id={`${idPrefix}-${field}`}
        titleText={label}
        label="Select"
        size="sm"
        items={options}
        itemToString={(item) => item?.description ?? ''}
        // `null`, not `undefined`: undefined hands control back to downshift's internal state, so a
        // cleared code would leave the old label on screen. The cast is Carbon's own type
        // inconsistency (onChange yields `ItemType | null`, the prop is declared `| undefined`).
        selectedItem={
          (options.find((item) => item.code === form[field]) ?? null) as CodeDescription | undefined
        }
        disabled={disabled}
        invalid={Boolean(errors[field])}
        invalidText={errors[field]}
        onChange={({ selectedItem }) => onChange(field, selectedItem?.code ?? '')}
      />
    )
  }

  return (
    <Grid fullWidth condensed className="schedule-9__fields">
      <Column sm={4} md={8} lg={16}>
        <div className="schedule-9__field-grid">
          {text('contractorId', 'Company ID')}
          {select('contractualItemCode', 'Contractual Item', codeLists.contractualItems)}
          {/* Enabled only for the "Other" item (114); disabled + cleared otherwise (BR-04). */}
          {text('itemDescription', 'Item Other Description', {
            disabled: !itemDescriptionEnabled(form.contractualItemCode),
          })}

          {/* Enabled only for road-deactivation items (111/112). */}
          {masked(
            'sideSlopePct',
            'Side Slope (%)',
            'numeric',
            !sideSlopeEnabled(form.contractualItemCode),
          )}
          {masked('numberOfUnits', 'Number of Units', 'decimal')}
          {select('unitCode', 'Unit Type', codeLists.unitTypes)}

          {/* Enabled only for the "Other" unit ("O"). */}
          {text('unitDescription', 'Unit Other Description', {
            disabled: !unitDescriptionEnabled(form.unitCode),
          })}
          {select('biogeoclimaticZone', 'Biogeoclimatic Zone', codeLists.biogeoclimaticZones)}
          {masked('cost', 'Cost', 'numeric')}

          {select('sourceCode', 'Source', codeLists.sources)}
          {/* Enabled only for source "O" or "S". */}
          {text('sourceDescription', 'Source Other Description', {
            disabled: !sourceDescriptionEnabled(form.sourceCode),
          })}
          {/* $/Unit is server-derived (AD-5) and legacy renders it disabled, so it is never a control.
              An untouched row shows the SERVED figure; an edited row / the Add panel previews it live. */}
          <div className="schedule-9__derived">
            <span className="schedule-9__derived-label">$/Unit</span>
            <span className="schedule-9__derived-value">
              {numStrFixed(
                servedCostPerUnit === undefined ? previewCostPerUnit(form) : servedCostPerUnit,
                2,
              )}
            </span>
          </div>
        </div>
      </Column>

      {/* Comments spans its own row (legacy gives it a full-width row of its own). */}
      <Column sm={4} md={8} lg={16}>
        <TextArea
          id={`${idPrefix}-comments`}
          labelText="Comments"
          rows={7}
          // enableCounter + maxCount also applies the hard maxLength, so typing stops at the cap.
          // Capped at 2000 — the backend column/DTO limit — NOT the legacy screen's 3500 (deviation):
          // the counter must not allow text the save would reject.
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

export default ContractualWorkFields
