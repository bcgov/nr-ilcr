import type { FC, ReactNode } from 'react'
import { Select, SelectItem, TextArea, TextInput } from '@carbon/react'
import type { CodeDescription, Schedule10CodeLists } from '@/interfaces/Schedule10Response'
import CodeComboBox from '@/components/core/CodeComboBox'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import { fmtCurrency, fmtNumber } from '@/utils/number'
import type { MaskedField, RoadDetailErrors, RoadDetailFormValues } from './validation'
import {
  COMMENTS_MAX,
  ROAD_NAME_MAX,
  ballastMaterialRequired,
  previewCostPerVolumePerLength,
  previewMaterialTotal,
  previewStabilizingCostPerLength,
  previewStabilizingTotal,
  previewSubGradeCostPerLength,
  previewSubGradeTotal,
  previewSubGradeTotalCosts,
} from './validation'

type RoadDetailFieldsProps = {
  readonly idPrefix: string
  readonly form: RoadDetailFormValues
  readonly errors: RoadDetailErrors
  readonly codeLists: Schedule10CodeLists
  readonly disabled: boolean
  readonly readOnly: boolean
  readonly onChange: (key: keyof RoadDetailFormValues, value: string) => void
  readonly onMask: (key: MaskedField) => void
}

/** One of the three legacy columns: Road Information, Sub-Grade, Additional Stabilizing. */
const Section: FC<{ readonly heading: string; readonly children: ReactNode }> = ({
  heading,
  children,
}) => (
  <section className="schedule-10__detail-column">
    <h4 className="schedule-10__detail-heading">{heading}</h4>
    {children}
  </section>
)

const SubHeading: FC<{ readonly children: ReactNode }> = ({ children }) => (
  <h5 className="schedule-10__detail-subheading">{children}</h5>
)

const Field: FC<{ readonly children: ReactNode }> = ({ children }) => (
  <div className="schedule-10__field">{children}</div>
)

/** A derived figure: label above, value below — text, so it is announced as a value. */
const Derived: FC<{ readonly label: string; readonly value: string }> = ({ label, value }) => (
  <Field>
    <span className="schedule-10__field-label">{label}</span>
    <span className="schedule-10__field-value">{value}</span>
  </Field>
)

const RoadDetailFields: FC<RoadDetailFieldsProps> = ({
  idPrefix,
  form,
  errors,
  codeLists,
  disabled,
  readOnly,
  onChange,
  onMask,
}) => {
  const id = (name: string) => `${idPrefix}-${name}`

  // A stored classification may have been de-listed since it was saved, in which case it is absent
  // from the offerable list. Appending it keeps the field showing what the row actually holds
  // instead of appearing unselected.
  const becOptions: CodeDescription[] = codeLists.becClassifications.map((bec) => ({
    code: String(bec.biogeoclimaticCatalogueId),
    description: bec.label ?? String(bec.biogeoclimaticCatalogueId),
  }))
  const selectedBec = form.becbiogeoCatalogueId
  if (selectedBec !== '' && !becOptions.some((option) => option.code === selectedBec)) {
    becOptions.push({ code: selectedBec, description: selectedBec })
  }

  const describe = (options: readonly CodeDescription[], code: string): string =>
    options.find((option) => option.code === code)?.description ?? code

  const readOnlyField = (label: string, value: string): ReactNode => (
    <Field>
      <span className="schedule-10__field-label">{label}</span>
      <span className="schedule-10__field-value">{value === '' ? '—' : value}</span>
    </Field>
  )

  const text = (key: keyof RoadDetailFormValues, label: string, maxLength?: number): ReactNode =>
    readOnly ? (
      readOnlyField(label, form[key])
    ) : (
      <Field>
        <TextInput
          id={id(key)}
          labelText={label}
          autoComplete="off"
          maxLength={maxLength}
          value={form[key]}
          disabled={disabled}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onChange={(event) => onChange(key, event.target.value)}
        />
      </Field>
    )

  // The unit rides in the label rather than beside the box, so a labelled column of fields lines up.
  const numeric = (key: MaskedField, label: string, unit?: string): ReactNode => {
    const labelWithUnit = unit ? `${label} (${unit})` : label
    return readOnly ? (
      readOnlyField(labelWithUnit, form[key])
    ) : (
      <Field>
        <CommaNumberInput
          id={id(key)}
          labelText={labelWithUnit}
          autoComplete="off"
          value={form[key]}
          disabled={disabled}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onValueChange={(raw) => onChange(key, raw)}
          onBlur={() => onMask(key)}
        />
      </Field>
    )
  }

  const combo = (
    key: keyof RoadDetailFormValues,
    label: string,
    options: readonly CodeDescription[],
  ): ReactNode =>
    readOnly ? (
      readOnlyField(label, form[key] === '' ? '' : describe(options, form[key]))
    ) : (
      <Field>
        <CodeComboBox
          id={id(key)}
          titleText={label}
          items={[...options]}
          selectedCode={form[key]}
          disabled={disabled}
          invalid={Boolean(errors[key])}
          invalidText={errors[key]}
          onSelect={(code) => onChange(key, code)}
        />
      </Field>
    )

  const endHaulRate = previewCostPerVolumePerLength(
    form.lessEndHaul,
    form.endHaulVolume,
    form.endHaulDistance,
  )
  const overlandRate = previewCostPerVolumePerLength(
    form.lessOverland,
    form.overlandVolume,
    form.overlandDistance,
  )

  return (
    <div className="schedule-10__detail-fields">
      <div className="schedule-10__detail-grid">
        <Section heading="Road Information">
          {text('roadName', 'Road Name', ROAD_NAME_MAX)}
          {combo('roadLifetimeCode', 'Road Type', codeLists.roadLifetimes)}

          <SubHeading>Moisture</SubHeading>
          {combo('becbiogeoCatalogueId', 'BEC Zone', becOptions)}
          {combo('relSoilMoistRgmClsCode', 'RSMR Class', codeLists.rsmrClasses)}

          <SubHeading>Shoulder</SubHeading>
          {numeric('sideSlopePct', 'Side Slope', '%')}

          <SubHeading>Material Type</SubHeading>
          {numeric('solidRockPct', 'Solid (Hard) Rock', '%')}
          {numeric('rippableRockPct', 'Rippable Rock', '%')}
          {numeric('coarsePct', 'Coarse', '%')}
          {numeric('finePct', 'Fine', '%')}
          {numeric('organicPct', 'Organic', '%')}
          <Derived label="Total (%)" value={String(previewMaterialTotal(form))} />
        </Section>

        <Section heading="Sub-Grade">
          {numeric('sgLength', 'Sub-Grade Length', 'km')}
          {numeric('sgSurfaceWidth', 'Sub-Grade Surface Width', 'm')}

          <SubHeading>Costs</SubHeading>
          {numeric('sgActualCost', 'Sub-Grade Actual Cost', '$')}
          {numeric('sgTtTransfer', 'Sub-Grade TtT Transfer', '$')}
          {numeric('sgOtherTransfer', 'Sub-Grade Other Transfer', '$')}
          <Derived label="Total Costs ($)" value={fmtCurrency(previewSubGradeTotalCosts(form))} />
          {numeric('lessBridges', 'Less Bridges', '$')}
          {numeric('lessCulverts', 'Less Culverts', '$')}
          {numeric('lessLandings', 'Less Landings', '$')}
          {numeric('lessEndHaul', 'Less End Haul', '$')}
          {numeric('lessOverland', 'Less Overland', '$')}
          {numeric('lessOtherEng', 'Less OtherEng', '$')}
          <Derived label="Total ($)" value={fmtCurrency(previewSubGradeTotal(form))} />
          <Derived label="$/km" value={fmtCurrency(previewSubGradeCostPerLength(form))} />
        </Section>

        <Section heading="Additional Stabilizing">
          {combo('stBallastMethodCode', 'Ballast Method Code', codeLists.ballastMethods)}
          {numeric('stLength', 'Additional Stabilizing Length', 'km')}
          {numeric('stSurfaceWidth', 'Additional Stabilizing Surface Width', 'm')}
          {combo('stBallastMaterialCode', 'Type', codeLists.ballastMaterials)}
          {numeric('stDepth', 'Depth', 'm')}
          {numeric('stDistanceToSource', 'Distance to Source', 'km')}
          {numeric('stActualCost', 'Additional Stabilizing Actual Costs', '$')}
          {numeric('stTtTransfer', 'Additional Stabilizing TtT Transfer', '$')}
          {numeric('stOtherTransfer', 'Additional Stabilizing Other Transfer', '$')}
          <Derived label="Total ($)" value={fmtCurrency(previewStabilizingTotal(form))} />
          <Derived label="$/km" value={fmtCurrency(previewStabilizingCostPerLength(form))} />
        </Section>
      </div>

      <div className="schedule-10__fields">
        {readOnly ? (
          readOnlyField(
            'Includes Detailed Engineering Costs',
            form.detailedEngineeringCostInd === 'Y' ? 'Yes' : 'No',
          )
        ) : (
          <Field>
            <Select
              id={id('detailedEngineeringCostInd')}
              labelText="Includes Detailed Engineering Costs"
              value={form.detailedEngineeringCostInd}
              disabled={disabled}
              onChange={(event) => onChange('detailedEngineeringCostInd', event.target.value)}
            >
              <SelectItem value="N" text="No" />
              <SelectItem value="Y" text="Yes" />
            </Select>
          </Field>
        )}
      </div>

      <h4 className="schedule-10__detail-heading">End Haul</h4>
      <div className="schedule-10__fields">
        {numeric('endHaulDistance', 'End Haul Distance', 'km')}
        {numeric('endHaulVolume', 'End Haul Volume', 'm3')}
        <Derived label="$/m3/km" value={fmtNumber(endHaulRate)} />
      </div>

      <h4 className="schedule-10__detail-heading">Overland</h4>
      <div className="schedule-10__fields">
        {numeric('overlandDistance', 'Overland Distance', 'km')}
        {numeric('overlandVolume', 'Overland Volume', 'm3')}
        <Derived label="$/m3/km" value={fmtNumber(overlandRate)} />
      </div>

      <div className="schedule-10__comments">
        {readOnly ? (
          <>
            <span className="schedule-10__field-label">
              If you have any comments, please enter them here:
            </span>
            <p className="schedule-10__field-value">{form.comments === '' ? '—' : form.comments}</p>
          </>
        ) : (
          <TextArea
            id={id('comments')}
            labelText="If you have any comments, please enter them here:"
            rows={5}
            enableCounter
            maxCount={COMMENTS_MAX}
            value={form.comments}
            disabled={disabled}
            invalid={Boolean(errors.comments)}
            invalidText={errors.comments ?? ''}
            onChange={(event) => onChange('comments', event.target.value)}
          />
        )}
      </div>

      {!readOnly && ballastMaterialRequired(form.stBallastMethodCode) && (
        <p className="schedule-10__hint">
          A material Type is required for this Additional Stabilizing code.
        </p>
      )}
    </div>
  )
}

export default RoadDetailFields
