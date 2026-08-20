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
 * One field in the wrapping row. Each control carries its own Carbon label above it, so the fields
 * flow across the panel rather than stacking against a label column.
 */
const Field: FC<{ readonly className?: string; readonly children: ReactNode }> = ({
  className,
  children,
}) => <div className={`schedule-10__field ${className ?? ''}`.trim()}>{children}</div>

/** A derived or read-only value: label above, value below, aligned with the inputs beside it. */
const ReadOnlyField: FC<{
  readonly label: string
  readonly value: string
  readonly className?: string
}> = ({ label, value, className }) => (
  <Field className={className}>
    <span className="schedule-10__field-label">{label}</span>
    <span className="schedule-10__field-value">{value === '' ? '—' : value}</span>
  </Field>
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
  const blockOptions = supplyBlocksFor(codeLists.supplyBlocks, form.tsaOrTfl, form.supplyBlock)

  const describe = (options: readonly CodeDescription[], code: string): string =>
    options.find((option) => option.code === code)?.description ?? code

  if (readOnly) {
    return (
      <div className="schedule-10__fields">
        <ReadOnlyField label="Division" value={form.divisionName} />
        <ReadOnlyField label="Period Surveyed" value={form.constructionPeriod} />
        <ReadOnlyField
          label="Region"
          value={describe(codeLists.forestRegions, form.forestRegionCode)}
        />
        <ReadOnlyField label="TSA or TFL" value={describe(tsaOptions, form.tsaOrTfl)} />
        <ReadOnlyField
          label="Supply Block"
          value={tflLocated ? '' : describe(codeLists.supplyBlocks, form.supplyBlock)}
        />
        <ReadOnlyField label="TFL" value={tflLocated ? form.tflNumberCode : ''} />
        <ReadOnlyField label="Road Group" value={roadGroup ?? ''} />
      </div>
    )
  }

  return (
    <div className="schedule-10__fields">
      <Field>
        <TextInput
          id={id('division')}
          labelText="Division"
          maxLength={DIVISION_MAX}
          autoComplete="off"
          value={form.divisionName}
          disabled={disabled}
          invalid={Boolean(errors.divisionName)}
          invalidText={errors.divisionName ?? ''}
          onChange={(event) => onChange('divisionName', event.target.value)}
        />
      </Field>

      <Field>
        <TextInput
          id={id('period')}
          labelText="Period Surveyed"
          placeholder="YYYY-MM"
          autoComplete="off"
          value={form.constructionPeriod}
          disabled={disabled}
          invalid={Boolean(errors.constructionPeriod)}
          invalidText={errors.constructionPeriod ?? ''}
          onChange={(event) => onChange('constructionPeriod', event.target.value)}
        />
      </Field>

      <Field className="schedule-10__field--wide">
        <CodeComboBox
          id={id('region')}
          titleText="Region"
          items={[...codeLists.forestRegions]}
          selectedCode={form.forestRegionCode}
          disabled={disabled}
          invalid={Boolean(errors.forestRegionCode)}
          invalidText={errors.forestRegionCode}
          onSelect={(code) => onChange('forestRegionCode', code)}
        />
      </Field>

      <Field className="schedule-10__field--wide">
        <CodeComboBox
          id={id('tsa-or-tfl')}
          titleText="TSA or TFL"
          items={tsaOptions}
          selectedCode={form.tsaOrTfl}
          disabled={disabled}
          invalid={Boolean(errors.tsaOrTfl)}
          invalidText={errors.tsaOrTfl}
          onSelect={(code) => onChange('tsaOrTfl', code)}
        />
      </Field>

      <Field className="schedule-10__field--wide">
        <CodeComboBox
          id={id('supply-block')}
          titleText="Supply Block"
          items={blockOptions}
          selectedCode={form.supplyBlock}
          disabled={disabled || tflLocated}
          invalid={Boolean(errors.supplyBlock)}
          invalidText={errors.supplyBlock}
          onSelect={(code) => onChange('supplyBlock', code)}
        />
      </Field>

      <Field className="schedule-10__field--narrow">
        <TextInput
          id={id('tfl')}
          labelText="TFL"
          maxLength={TFL_MAX}
          autoComplete="off"
          value={form.tflNumberCode}
          disabled={disabled || !tflLocated}
          invalid={Boolean(errors.tflNumberCode)}
          invalidText={errors.tflNumberCode ?? ''}
          onChange={(event) => onChange('tflNumberCode', event.target.value)}
        />
      </Field>

      <ReadOnlyField
        label="Road Group"
        value={roadGroup ?? ''}
        className="schedule-10__field--narrow"
      />
    </div>
  )
}

export default PageFields
