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

/**
 * The visible text is a span rather than a label: each control already carries its own Carbon label
 * as the accessible name, and a second `<label for>` would concatenate into a doubled name.
 */
const Row: FC<{
  readonly label: string
  readonly children: ReactNode
}> = ({ label, children }) => (
  <div className="schedule-10__detail-row">
    <span className="schedule-10__field-label">{label}</span>
    <div className="schedule-10__field-control">{children}</div>
  </div>
)

/** A derived figure: text rather than a disabled input, so it is announced as a value. */
const Derived: FC<{ readonly label: string; readonly value: string }> = ({ label, value }) => (
  <Row label={label}>
    <span className="schedule-10__derived-value">{value}</span>
  </Row>
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

  const text = (
    key: keyof RoadDetailFormValues,
    label: string,
    extra?: { readonly maxLength?: number },
  ): ReactNode =>
    readOnly ? (
      <span className="schedule-10__derived-value">{form[key] === '' ? '—' : form[key]}</span>
    ) : (
      <TextInput
        id={id(key)}
        labelText={label}
        hideLabel
        autoComplete="off"
        maxLength={extra?.maxLength}
        value={form[key]}
        disabled={disabled}
        invalid={Boolean(errors[key])}
        invalidText={errors[key] ?? ''}
        onChange={(event) => onChange(key, event.target.value)}
      />
    )

  const numeric = (key: MaskedField, label: string, suffix?: string): ReactNode => {
    if (readOnly) {
      return (
        <span className="schedule-10__derived-value">
          {form[key] === '' ? '—' : form[key]}
          {suffix ? ` ${suffix}` : ''}
        </span>
      )
    }
    return (
      <div className="schedule-10__numeric">
        <CommaNumberInput
          id={id(key)}
          labelText={label}
          hideLabel
          autoComplete="off"
          value={form[key]}
          disabled={disabled}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onValueChange={(raw) => onChange(key, raw)}
          onBlur={() => onMask(key)}
        />
        {suffix && <span className="schedule-10__suffix">{suffix}</span>}
      </div>
    )
  }

  const combo = (
    key: keyof RoadDetailFormValues,
    label: string,
    options: readonly CodeDescription[],
  ): ReactNode =>
    readOnly ? (
      <span className="schedule-10__derived-value">
        {form[key] === '' ? '—' : describe(options, form[key])}
      </span>
    ) : (
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
          <Row label="Road Name:">
            {text('roadName', 'Road Name', { maxLength: ROAD_NAME_MAX })}
          </Row>
          <Row label="Road Type:">
            {combo('roadLifetimeCode', 'Road Type', codeLists.roadLifetimes)}
          </Row>

          <SubHeading>Moisture</SubHeading>
          <Row label="BEC Zone:">{combo('becbiogeoCatalogueId', 'BEC Zone', becOptions)}</Row>
          <Row label="RSMR Class:">
            {combo('relSoilMoistRgmClsCode', 'RSMR Class', codeLists.rsmrClasses)}
          </Row>

          <SubHeading>Shoulder</SubHeading>
          <Row label="Side Slope:">{numeric('sideSlopePct', 'Side Slope', '%')}</Row>

          <SubHeading>Material Type</SubHeading>
          <Row label="Solid (Hard) Rock:">{numeric('solidRockPct', 'Solid (Hard) Rock', '%')}</Row>
          <Row label="Rippable Rock:">{numeric('rippableRockPct', 'Rippable Rock', '%')}</Row>
          <Row label="Coarse:">{numeric('coarsePct', 'Coarse', '%')}</Row>
          <Row label="Fine:">{numeric('finePct', 'Fine', '%')}</Row>
          <Row label="Organic:">{numeric('organicPct', 'Organic', '%')}</Row>
          <Derived label="Total:" value={`${String(previewMaterialTotal(form))} %`} />
        </Section>

        <Section heading="Sub-Grade">
          <Row label="Length:">{numeric('sgLength', 'Sub-Grade Length', 'km')}</Row>
          <Row label="Surface Width:">
            {numeric('sgSurfaceWidth', 'Sub-Grade Surface Width', 'm')}
          </Row>

          <SubHeading>Costs</SubHeading>
          <Row label="Actual Cost($):">{numeric('sgActualCost', 'Sub-Grade Actual Cost')}</Row>
          <Row label="TtT Transfer($):">{numeric('sgTtTransfer', 'Sub-Grade TtT Transfer')}</Row>
          <Row label="Other Transfer($):">
            {numeric('sgOtherTransfer', 'Sub-Grade Other Transfer')}
          </Row>
          <Derived label="Total Costs($):" value={fmtCurrency(previewSubGradeTotalCosts(form))} />
          <Row label="Less Bridges($):">{numeric('lessBridges', 'Less Bridges')}</Row>
          <Row label="Less Culverts($):">{numeric('lessCulverts', 'Less Culverts')}</Row>
          <Row label="Less Landings($):">{numeric('lessLandings', 'Less Landings')}</Row>
          <Row label="Less End Haul($):">{numeric('lessEndHaul', 'Less End Haul')}</Row>
          <Row label="Less Overland($):">{numeric('lessOverland', 'Less Overland')}</Row>
          <Row label="Less OtherEng($):">{numeric('lessOtherEng', 'Less OtherEng')}</Row>
          <Derived label="Total($):" value={fmtCurrency(previewSubGradeTotal(form))} />
          <Derived label="$/km:" value={fmtCurrency(previewSubGradeCostPerLength(form))} />
        </Section>

        <Section heading="Additional Stabilizing">
          <Row label="Code:">
            {combo('stBallastMethodCode', 'Ballast Method Code', codeLists.ballastMethods)}
          </Row>
          <Row label="Length:">{numeric('stLength', 'Additional Stabilizing Length', 'km')}</Row>
          <Row label="Surface Width:">
            {numeric('stSurfaceWidth', 'Additional Stabilizing Surface Width', 'm')}
          </Row>
          <Row label="Type:">
            {combo('stBallastMaterialCode', 'Type', codeLists.ballastMaterials)}
          </Row>
          <Row label="Depth:">{numeric('stDepth', 'Depth', 'm')}</Row>
          <Row label="Distance to Source:">
            {numeric('stDistanceToSource', 'Distance to Source', 'km')}
          </Row>
          <Row label="Actual Costs($):">
            {numeric('stActualCost', 'Additional Stabilizing Actual Costs')}
          </Row>
          <Row label="TtT Transfer($):">
            {numeric('stTtTransfer', 'Additional Stabilizing TtT Transfer')}
          </Row>
          <Row label="Other Transfer($):">
            {numeric('stOtherTransfer', 'Additional Stabilizing Other Transfer')}
          </Row>
          <Derived label="Total($):" value={fmtCurrency(previewStabilizingTotal(form))} />
          <Derived label="$/km:" value={fmtCurrency(previewStabilizingCostPerLength(form))} />
        </Section>
      </div>

      <div className="schedule-10__haul">
        <Row label="Includes Detailed Engineering Costs:">
          {readOnly ? (
            <span className="schedule-10__derived-value">
              {form.detailedEngineeringCostInd === 'Y' ? 'Yes' : 'No'}
            </span>
          ) : (
            <Select
              id={id('detailedEngineeringCostInd')}
              labelText="Includes Detailed Engineering Costs"
              hideLabel
              value={form.detailedEngineeringCostInd}
              disabled={disabled}
              onChange={(event) => onChange('detailedEngineeringCostInd', event.target.value)}
            >
              <SelectItem value="N" text="No" />
              <SelectItem value="Y" text="Yes" />
            </Select>
          )}
        </Row>

        <div className="schedule-10__haul-grid">
          <span />
          <span className="schedule-10__haul-heading">Distance km</span>
          <span className="schedule-10__haul-heading">Volume(m3)</span>
          <span className="schedule-10__haul-heading">$/m3/km</span>

          <span className="schedule-10__field-label">End Haul Details:</span>
          {numeric('endHaulDistance', 'End Haul Distance')}
          {numeric('endHaulVolume', 'End Haul Volume')}
          <span className="schedule-10__derived-value">{fmtNumber(endHaulRate)}</span>

          <span className="schedule-10__field-label">Overland Details:</span>
          {numeric('overlandDistance', 'Overland Distance')}
          {numeric('overlandVolume', 'Overland Volume')}
          <span className="schedule-10__derived-value">{fmtNumber(overlandRate)}</span>
        </div>
      </div>

      <div className="schedule-10__comments">
        <span className="schedule-10__field-label schedule-10__comments-label">
          If you have any comments, please enter them here:
        </span>
        {readOnly ? (
          <p className="schedule-10__derived-value">{form.comments === '' ? '—' : form.comments}</p>
        ) : (
          <TextArea
            id={id('comments')}
            labelText="Comments"
            hideLabel
            rows={7}
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
