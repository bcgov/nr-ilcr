import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { OriginalValues } from '@/interfaces/OriginalValue'
import type { FC, ReactNode } from 'react'
import { Select, SelectItem, TextInput } from '@carbon/react'
import CommentsTextArea from '@/components/core/CommentsTextArea'
import type { CodeDescription, Schedule10CodeLists } from '@/interfaces/Schedule10Response'
import CodeComboBox from '@/components/core/CodeComboBox'
import type { ComboMatchMode } from '@/components/core/CodeComboBox'
import CommaNumberInput from '@/components/core/CommaNumberInput'
import { fmtCurrency, fmtWholeCost } from '@/utils/number'
import type { MaskedField, RoadDetailErrors, RoadDetailFormValues } from './validation'
import {
  BALLAST_ZEROED_FIELDS,
  COMMENTS_MAX,
  ROAD_NAME_MAX,
  ballastForcesMaterialNa,
  ballastMaterialRequired,
  ballastZeroesFigures,
  describe,
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
  /**
   * The Licensee's submitted values for this road detail, already flattened onto the form's field
   * names by {@code roadDetailOriginals} (Story 16.2, BR-04). Null at Draft.
   */
  readonly originals?: OriginalValues | null
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

const Field: FC<{ readonly className?: string; readonly children: ReactNode }> = ({
  className,
  children,
}) => <div className={`schedule-10__field ${className ?? ''}`.trim()}>{children}</div>

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
  originals,
}) => {
  // Legacy renders an indicator beside ~27 of these fields. It renders NONE on the derived totals,
  // and none on ASM Code / Soil Moisture Code / Boulder Area %, which business direction removed
  // from Schedule 10 entirely (PRD LD-1/2/3) — so those are absent here because the FIELDS are.
  const indicator = (key: keyof RoadDetailFormValues, label: string, numeric = true): ReactNode => (
    <OriginalValueIndicator
      originals={originals}
      field={key}
      current={form[key]}
      numeric={numeric}
      label={label}
    />
  )
  const id = (name: string) => `${idPrefix}-${name}`

  // `N` and `D` both have their material forced to `NA`; `N` additionally has its dimensions and two
  // of its three costs zeroed, which `buildStabilizing` sends rather than leaving to the server.
  const materialForced = ballastForcesMaterialNa(form.stBallastMethodCode)
  const figuresZeroed = ballastZeroesFigures(form.stBallastMethodCode)

  // An input whose entry `N` discards is disabled, so the rule reads the same way everywhere: the
  // material combo was already disabled in exactly this situation, and leaving these editable
  // invited entry that Save would silently replace with zero. `stTtTransfer` is NOT in the set —
  // the server keeps it on the `N` branch, so it stays editable.
  const zeroedByBallast = (key: MaskedField): boolean =>
    figuresZeroed && (BALLAST_ZEROED_FIELDS as readonly string[]).includes(key)

  // A stored classification may have been de-listed since it was saved, in which case it is absent
  // from the offerable list. Appending it keeps the field showing what the row actually holds instead
  // of appearing unselected — and it is appended with the row's OWN label, which the response carries
  // on `becClassification` even for a de-listed value. Falling back to the catalogue id put a bare
  // number where every other option reads `SBSmk1`.
  const becOptions: CodeDescription[] = codeLists.becClassifications.map((bec) => ({
    code: String(bec.biogeoclimaticCatalogueId),
    description: bec.label ?? String(bec.biogeoclimaticCatalogueId),
  }))
  const selectedBec = form.becbiogeoCatalogueId
  if (selectedBec !== '' && !becOptions.some((option) => option.code === selectedBec)) {
    becOptions.push({
      code: selectedBec,
      description: form.becbiogeoLabel === '' ? selectedBec : form.becbiogeoLabel,
    })
  }

  /**
   * A stored value shown as text, in the panel's View mode.
   *
   * It carries the indicator too, and must: `readOnly` here is `panelMode === 'view'`, which is the
   * mode an ILCR_SUBMITTER gets on a Submitted report (Story 16.1's matrix). Original-value
   * visibility is a STATUS gate, never a permission one (Story 16.2 AC6, pinned cell 3), so leaving
   * the indicator to the editable branch alone — as the first cut of this wiring did — hid every
   * road-detail original from the one role most likely to be auditing it (review of PR #452).
   *
   * `field` is nullable only for a value with no submitted counterpart to compare against.
   */
  const readOnlyField = (
    field: keyof RoadDetailFormValues | null,
    label: string,
    value: string,
    numericCompare = true,
  ): ReactNode => (
    <Field>
      <span className="schedule-10__field-label">{label}</span>
      <span className="schedule-10__field-value">{value === '' ? '—' : value}</span>
      {field !== null && indicator(field, label, numericCompare)}
    </Field>
  )

  const text = (key: keyof RoadDetailFormValues, label: string, maxLength?: number): ReactNode =>
    readOnly ? (
      readOnlyField(key, label, form[key], false)
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
        {indicator(key, label, false)}
      </Field>
    )

  // The unit rides in the label rather than beside the box, so a labelled column of fields lines up.
  const numeric = (key: MaskedField, label: string, unit?: string): ReactNode => {
    const labelWithUnit = unit ? `${label} (${unit})` : label
    return readOnly ? (
      readOnlyField(key, labelWithUnit, form[key])
    ) : (
      <Field>
        <CommaNumberInput
          id={id(key)}
          labelText={labelWithUnit}
          autoComplete="off"
          value={form[key]}
          disabled={disabled || zeroedByBallast(key)}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onValueChange={(raw) => onChange(key, raw)}
          onBlur={() => onMask(key)}
        />
        {indicator(key, labelWithUnit)}
      </Field>
    )
  }

  const combo = (
    key: keyof RoadDetailFormValues,
    label: string,
    options: readonly CodeDescription[],
    opts: { readonly matchMode?: ComboMatchMode; readonly disabled?: boolean } = {},
  ): ReactNode => {
    if (readOnly) {
      const stored = form[key]
      // The indicator compares the stored CODE (`form[key]`), exactly as the editable branch does —
      // the description shown here is a lookup of it, not the value the snapshot holds.
      return readOnlyField(key, label, stored === '' ? '' : describe(options, stored), false)
    }
    return (
      <Field>
        <CodeComboBox
          id={id(key)}
          titleText={label}
          items={[...options]}
          selectedCode={form[key]}
          matchMode={opts.matchMode}
          disabled={disabled || (opts.disabled ?? false)}
          invalid={Boolean(errors[key])}
          invalidText={errors[key]}
          onSelect={(code) => onChange(key, code)}
        />
        {indicator(key, label, false)}
      </Field>
    )
  }

  const engineeringCostsValue = form.detailedEngineeringCostInd === 'Y' ? 'Yes' : 'No'

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
          {/* AC5: prefix match, not substring — typing `SBS` must not offer `ESSFmc`. */}
          {combo('becbiogeoCatalogueId', 'BEC Zone', becOptions, { matchMode: 'prefix' })}
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
          <Derived label="Total Costs ($)" value={fmtWholeCost(previewSubGradeTotalCosts(form))} />
          {numeric('lessBridges', 'Less Bridges', '$')}
          {numeric('lessCulverts', 'Less Culverts', '$')}
          {numeric('lessLandings', 'Less Landings', '$')}
          {numeric('lessEndHaul', 'Less End Haul', '$')}
          {numeric('lessOverland', 'Less Overland', '$')}
          {numeric('lessOtherEng', 'Less OtherEng', '$')}
          <Derived label="Total ($)" value={fmtWholeCost(previewSubGradeTotal(form))} />
          <Derived label="$/km" value={fmtCurrency(previewSubGradeCostPerLength(form))} />
        </Section>

        <Section heading="Additional Stabilizing">
          {combo('stBallastMethodCode', 'Ballast Method Code', codeLists.ballastMethods)}
          {numeric('stLength', 'Additional Stabilizing Length', 'km')}
          {numeric('stSurfaceWidth', 'Additional Stabilizing Surface Width', 'm')}
          {/* The server replaces the material with `NA` on both `N` and `D`, so offering a choice
              here only invites one that will be discarded. */}
          {combo('stBallastMaterialCode', 'Type', codeLists.ballastMaterials, {
            disabled: materialForced,
          })}
          {numeric('stDepth', 'Depth', 'm')}
          {numeric('stDistanceToSource', 'Distance to Source', 'km')}
          {numeric('stActualCost', 'Additional Stabilizing Actual Costs', '$')}
          {numeric('stTtTransfer', 'Additional Stabilizing TtT Transfer', '$')}
          {numeric('stOtherTransfer', 'Additional Stabilizing Other Transfer', '$')}
          <Derived label="Total ($)" value={fmtWholeCost(previewStabilizingTotal(form))} />
          <Derived label="$/km" value={fmtCurrency(previewStabilizingCostPerLength(form))} />
        </Section>
      </div>

      <div className="schedule-10__fields">
        {readOnly ? (
          readOnlyField(
            'detailedEngineeringCostInd',
            'Includes Detailed Engineering Costs',
            engineeringCostsValue,
            false,
          )
        ) : (
          <Field className="schedule-10__field--compact">
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
            {/* The assembler serves this key (`YES_NO` format) and the first cut of the wiring
                rendered no indicator for it in either mode (review of PR #452). Text comparison:
                the form holds the `Y`/`N` code. */}
            {indicator('detailedEngineeringCostInd', 'Includes Detailed Engineering Costs', false)}
          </Field>
        )}
      </div>

      <h4 className="schedule-10__detail-heading">End Haul</h4>
      <div className="schedule-10__fields">
        {numeric('endHaulDistance', 'End Haul Distance', 'km')}
        {numeric('endHaulVolume', 'End Haul Volume', 'm3')}
        <Derived label="$/m3/km" value={fmtCurrency(endHaulRate)} />
      </div>

      <h4 className="schedule-10__detail-heading">Overland</h4>
      <div className="schedule-10__fields">
        {numeric('overlandDistance', 'Overland Distance', 'km')}
        {numeric('overlandVolume', 'Overland Volume', 'm3')}
        <Derived label="$/m3/km" value={fmtCurrency(overlandRate)} />
      </div>

      <div className="schedule-10__comments">
        {readOnly ? (
          <>
            <span className="schedule-10__field-label">
              If you have any comments, please enter them here:
            </span>
            <p className="schedule-10__field-value">{form.comments === '' ? '—' : form.comments}</p>
            {indicator('comments', 'Comments', false)}
          </>
        ) : (
          <>
            <CommentsTextArea
              id={id('comments')}
              labelText="If you have any comments, please enter them here:"
              rows={5}
              maxCount={COMMENTS_MAX}
              value={form.comments}
              disabled={disabled}
              invalid={Boolean(errors.comments)}
              invalidText={errors.comments ?? ''}
              onChange={(event) => onChange('comments', event.target.value)}
            />
            {/* The road detail's comments carry an indicator like its other fields — text, so the
                comparison is `equals` rather than by rounded value (PR #452 review). */}
            {indicator('comments', 'Comments', false)}
          </>
        )}
      </div>

      {/* Only once a method is actually chosen: a BLANK code lands in the `C` branch server-side, so
          `ballastMaterialRequired('')` is true and the hint fired on every untouched new road. */}
      {!readOnly &&
        form.stBallastMethodCode.trim() !== '' &&
        ballastMaterialRequired(form.stBallastMethodCode) && (
          <p className="schedule-10__hint">
            A material Type is required for this Additional Stabilizing code.
          </p>
        )}
      {!readOnly && figuresZeroed && (
        <p className="schedule-10__hint">
          This Additional Stabilizing code stores its length, surface width, depth, distance to
          source, actual cost and other transfer as zero, so those fields are disabled. TtT Transfer
          is still recorded as entered.
        </p>
      )}
    </div>
  )
}

export default RoadDetailFields
