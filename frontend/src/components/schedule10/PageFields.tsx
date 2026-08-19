import type { FC, ReactNode } from 'react'
import { TextInput } from '@carbon/react'
import type { CodeDescription, Schedule10CodeLists } from '@/interfaces/Schedule10Response'
import CodeComboBox from '@/components/core/CodeComboBox'
import type { PageErrors, PageFormValues } from './validation'
import { DIVISION_MAX, TFL_MAX, TFL_SENTINEL, isTflLocated, supplyBlocksFor } from './validation'

type PageFieldsProps = {
  readonly idPrefix: string
  readonly form: PageFormValues
  readonly errors: PageErrors
  readonly codeLists: Schedule10CodeLists
  readonly disabled: boolean
  /** The server-derived road group for the page being edited; blank on a new or unmapped page. */
  readonly roadGroup: string | null
  readonly readOnly: boolean
  readonly onChange: (key: keyof PageFormValues, value: string) => void
}

/**
 * One labelled row of the page-information form. Legacy lays this out as a two-column grid with the
 * label right-aligned against its control, and each control at its own width.
 *
 * The visible text is a span rather than a label: each control already carries its own Carbon label
 * as the accessible name, and a second `<label for>` would concatenate into a doubled name.
 */
const FieldRow: FC<{
  readonly label: string
  readonly children: ReactNode
}> = ({ label, children }) => (
  <div className="schedule-10__field-row">
    <span className="schedule-10__field-label">{label}</span>
    <div className="schedule-10__field-control">{children}</div>
  </div>
)

/** A read-only value rendered as text, so a screen reader announces a value not a dead control. */
const ReadOnlyRow: FC<{ readonly label: string; readonly value: string }> = ({ label, value }) => (
  <div className="schedule-10__field-row">
    <span className="schedule-10__field-label">{label}</span>
    <div className="schedule-10__field-control">
      <span className="schedule-10__derived-value">{value === '' ? '—' : value}</span>
    </div>
  </div>
)

/**
 * The page-information fields, in legacy screen order.
 *
 * The location is one control plus two dependents: choosing the TFL sentinel disables Supply Block
 * and enables the TFL number, and choosing a TSA does the reverse while narrowing the supply blocks
 * to that TSA. Road Group is derived from whichever branch is active and is never editable.
 */
const PageFields: FC<PageFieldsProps> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  roadGroup,
  readOnly,
  onChange,
}) => {
  const tflLocated = isTflLocated(form.tsaOrTfl)
  const id = (name: string) => `${idPrefix}-${name}`

  // Legacy's TSA control carries the real TSA numbers plus the sentinel that switches the page to a
  // TFL location; picking it is what "selecting TFL" means.
  const tsaOptions: CodeDescription[] = [
    ...codeLists.tsaNumbers,
    { code: TFL_SENTINEL, description: TFL_SENTINEL },
  ]
  const blockOptions = supplyBlocksFor(codeLists.supplyBlocks, form.tsaOrTfl)

  const describe = (options: readonly CodeDescription[], code: string): string =>
    options.find((option) => option.code === code)?.description ?? code

  if (readOnly) {
    return (
      <div className="schedule-10__fields">
        <ReadOnlyRow label="Division:" value={form.divisionName} />
        <ReadOnlyRow label="Period Surveyed:" value={form.constructionPeriod} />
        <ReadOnlyRow
          label="Region:"
          value={describe(codeLists.forestRegions, form.forestRegionCode)}
        />
        <ReadOnlyRow label="TSA or TFL:" value={describe(tsaOptions, form.tsaOrTfl)} />
        <ReadOnlyRow
          label="Supply Block:"
          value={tflLocated ? '' : describe(codeLists.supplyBlocks, form.supplyBlock)}
        />
        <ReadOnlyRow label="TFL:" value={tflLocated ? form.tflNumberCode : ''} />
        <ReadOnlyRow label="Road Group:" value={roadGroup ?? ''} />
      </div>
    )
  }

  return (
    <div className="schedule-10__fields">
      <FieldRow label="Division:">
        <TextInput
          id={id('division')}
          labelText="Division"
          hideLabel
          maxLength={DIVISION_MAX}
          autoComplete="off"
          className="schedule-10__control--medium"
          value={form.divisionName}
          disabled={disabled}
          invalid={Boolean(errors.divisionName)}
          invalidText={errors.divisionName ?? ''}
          onChange={(event) => onChange('divisionName', event.target.value)}
        />
      </FieldRow>

      <FieldRow label="Period Surveyed:">
        <TextInput
          id={id('period')}
          labelText="Period Surveyed"
          hideLabel
          placeholder="YYYY-MM"
          autoComplete="off"
          className="schedule-10__control--medium"
          value={form.constructionPeriod}
          disabled={disabled}
          invalid={Boolean(errors.constructionPeriod)}
          invalidText={errors.constructionPeriod ?? ''}
          onChange={(event) => onChange('constructionPeriod', event.target.value)}
        />
      </FieldRow>

      <FieldRow label="Region:">
        <CodeComboBox
          id={id('region')}
          titleText="Region"
          className="schedule-10__control--wide"
          items={[...codeLists.forestRegions]}
          selectedCode={form.forestRegionCode}
          disabled={disabled}
          invalid={Boolean(errors.forestRegionCode)}
          invalidText={errors.forestRegionCode}
          onSelect={(code) => onChange('forestRegionCode', code)}
        />
      </FieldRow>

      <FieldRow label="TSA or TFL:">
        <CodeComboBox
          id={id('tsa-or-tfl')}
          titleText="TSA or TFL"
          className="schedule-10__control--wider"
          items={tsaOptions}
          selectedCode={form.tsaOrTfl}
          disabled={disabled}
          invalid={Boolean(errors.tsaOrTfl)}
          invalidText={errors.tsaOrTfl}
          onSelect={(code) => onChange('tsaOrTfl', code)}
        />
      </FieldRow>

      <FieldRow label="Supply Block:">
        <CodeComboBox
          id={id('supply-block')}
          titleText="Supply Block"
          className="schedule-10__control--widest"
          items={blockOptions}
          selectedCode={form.supplyBlock}
          disabled={disabled || tflLocated}
          invalid={Boolean(errors.supplyBlock)}
          invalidText={errors.supplyBlock}
          onSelect={(code) => onChange('supplyBlock', code)}
        />
      </FieldRow>

      <FieldRow label="TFL:">
        <TextInput
          id={id('tfl')}
          labelText="TFL"
          hideLabel
          maxLength={TFL_MAX}
          autoComplete="off"
          className="schedule-10__control--tiny"
          value={form.tflNumberCode}
          disabled={disabled || !tflLocated}
          invalid={Boolean(errors.tflNumberCode)}
          invalidText={errors.tflNumberCode ?? ''}
          onChange={(event) => onChange('tflNumberCode', event.target.value)}
        />
      </FieldRow>

      <ReadOnlyRow label="Road Group:" value={roadGroup ?? ''} />
    </div>
  )
}

export default PageFields
