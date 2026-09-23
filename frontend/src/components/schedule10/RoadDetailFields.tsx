import OriginalValueIndicator from '@/components/core/OriginalValueIndicator'
import type { OriginalValues } from '@/interfaces/OriginalValue'
import type { CSSProperties, FC, ReactNode } from 'react'
import { TextInput } from '@carbon/react'
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

/**
 * A cell's place in the legacy road-detail grid, carried as custom properties so a media query can
 * drop back to a single stacked column without fighting inline styles for specificity.
 */
const at = (row: number, column: number): CSSProperties =>
  ({ '--cell-row': row, '--cell-column': column }) as CSSProperties

/**
 * One of the legacy grid's three column headings, and one of its four in-column sub-headings.
 *
 * Both take the SAME class, and so the same 1rem: legacy styles all seven with one `header` class
 * (`main.css:75`), which sets `font-weight: bold` and nothing else — no size change and, in
 * particular, no underline. Rendering the sub-headings a tier down at 0.875rem with a rule under the
 * three column headings was #440 items 6 and 7. The element level still nests (h4 above h5) because
 * the heading outline is real even where the type scale is flat.
 */
const Heading: FC<{ readonly children: ReactNode }> = ({ children }) => (
  <h4 className="schedule-10__detail-heading">{children}</h4>
)

const SubHeading: FC<{ readonly children: ReactNode }> = ({ children }) => (
  <h5 className="schedule-10__detail-heading">{children}</h5>
)

const Field: FC<{ readonly className?: string; readonly children: ReactNode }> = ({
  className,
  children,
}) => <div className={`schedule-10__field ${className ?? ''}`.trim()}>{children}</div>

/** A derived figure: label above, value below — text, so it is announced as a value. */
const Derived: FC<{ readonly label: string; readonly value: string }> = ({ label, value }) => (
  <Field>
    <span className="schedule-10__field-label">{label}:</span>
    <span className="schedule-10__field-value">{value}</span>
  </Field>
)

/** A cell of the road-detail grid: what to render, and which legacy row it belongs to. */
type Cell = { readonly row: number; readonly node: ReactNode }

/**
 * The Yes/No choice behind `Includes Detailed Engineering Costs`, as a code list, so it can go
 * through {@link CodeComboBox} like every other dropdown on this screen. `No` first, matching the
 * order legacy's `pageDtlECIncludeCosts` lists them in.
 */
const YES_NO: readonly CodeDescription[] = [
  { code: 'N', description: 'No' },
  { code: 'Y', description: 'Yes' },
]

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
   * `name` names the field for the INDICATOR, where the visible label is only unique within its
   * column — legacy prints `Length:` and `Surface Width:` once per column (see {@link numeric}).
   */
  const readOnlyField = (
    field: keyof RoadDetailFormValues,
    label: string,
    value: string,
    numericCompare = true,
    name = label,
  ): ReactNode => (
    <Field>
      <span className="schedule-10__field-label">{label}:</span>
      <span className="schedule-10__field-value">{value === '' ? '—' : value}</span>
      {indicator(field, name, numericCompare)}
    </Field>
  )

  const text = (key: keyof RoadDetailFormValues, label: string, maxLength?: number): ReactNode =>
    readOnly ? (
      readOnlyField(key, label, form[key], false)
    ) : (
      <Field>
        <TextInput
          id={id(key)}
          labelText={`${label}:`}
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

  /**
   * A bounded numeric, with the unit in the label rather than beside the box so a labelled column of
   * fields lines up.
   *
   * `accessibleName` exists because legacy's labels are only unique WITHIN a column: read straight
   * down the grid there are two `Length`, two `Surface Width`, two `TtT Transfer` and two
   * `Other Transfer`. Legacy gets away with it — they are table text, disambiguated by the column
   * heading above them. Here each one is a real `<label for>`, and two controls answering to the
   * same name is both an accessibility defect and ambiguous to every query that finds a field by its
   * label. So the VISIBLE text is legacy's (#440 item 3) and the ACCESSIBLE name keeps the column
   * prefix the field already had. Nothing is added to the screen.
   *
   * Note where the trailing colon does and does not go. It is part of a PRINTED label — real text,
   * so it can be selected and copied like the rest of the label — and it is written into the label
   * string rather than drawn by CSS, which is what a first cut of this got wrong: `content: ':'`
   * renders but cannot be selected. An `aria-label` standing in for an ambiguous printed label gets
   * no colon; it is a name, not something on screen. Nor does a HIDDEN label (the haul figures, the
   * Yes/No menu) — there is no printed text there to punctuate.
   */
  const numeric = (
    key: MaskedField,
    label: string,
    unit?: string,
    accessibleName?: string,
  ): ReactNode => {
    const labelWithUnit = unit ? `${label} (${unit})` : label
    const name = accessibleName ?? labelWithUnit
    return readOnly ? (
      readOnlyField(key, labelWithUnit, form[key], true, name)
    ) : (
      <Field>
        <CommaNumberInput
          id={id(key)}
          labelText={`${labelWithUnit}:`}
          aria-label={name === labelWithUnit ? undefined : name}
          autoComplete="off"
          value={form[key]}
          disabled={disabled || zeroedByBallast(key)}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onValueChange={(raw) => onChange(key, raw)}
          onBlur={() => onMask(key)}
        />
        {indicator(key, name)}
      </Field>
    )
  }

  const combo = (
    key: keyof RoadDetailFormValues,
    label: string,
    options: readonly CodeDescription[],
    opts: {
      readonly matchMode?: ComboMatchMode
      readonly disabled?: boolean
      readonly accessibleName?: string
    } = {},
  ): ReactNode => {
    const name = opts.accessibleName ?? label
    if (readOnly) {
      const stored = form[key]
      // The indicator compares the stored CODE (`form[key]`), exactly as the editable branch does —
      // the description shown here is a lookup of it, not the value the snapshot holds.
      return readOnlyField(key, label, stored === '' ? '' : describe(options, stored), false, name)
    }
    return (
      <Field>
        <CodeComboBox
          id={id(key)}
          titleText={`${label}:`}
          accessibleName={opts.accessibleName}
          items={[...options]}
          selectedCode={form[key]}
          matchMode={opts.matchMode}
          disabled={disabled || (opts.disabled ?? false)}
          invalid={Boolean(errors[key])}
          invalidText={errors[key]}
          onSelect={(code) => onChange(key, code)}
        />
        {indicator(key, name, false)}
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

  /**
   * One haul figure. The column heading above it already reads `Distance (km)`, so the control's own
   * label is visually hidden rather than repeated (legacy prints it once, as a heading) — hidden,
   * not dropped, so the field is still reachable by the name it has always answered to.
   */
  const haulFigure = (
    key: MaskedField,
    accessibleName: string,
    row: number,
    col: number,
  ): ReactNode =>
    readOnly ? (
      <div className="schedule-10__haul-cell" style={at(row, col)}>
        <span className="schedule-10__field-value">{form[key] === '' ? '—' : form[key]}</span>
        {indicator(key, accessibleName)}
      </div>
    ) : (
      <div className="schedule-10__haul-cell" style={at(row, col)}>
        <CommaNumberInput
          id={id(key)}
          labelText={accessibleName}
          hideLabel
          autoComplete="off"
          value={form[key]}
          disabled={disabled}
          invalid={Boolean(errors[key])}
          invalidText={errors[key] ?? ''}
          onValueChange={(raw) => onChange(key, raw)}
          onBlur={() => onMask(key)}
        />
        {indicator(key, accessibleName)}
      </div>
    )

  /**
   * The legacy road form is one table read ACROSS, not three lists read down: `schedule10.xhtml`
   * lays its three column-pairs out in 19 `p:row`s, so `BEC Zone`, `Actual Cost($)` and
   * `Surface Width:` share a row because they are all in row 5 — not because the three columns
   * happen to be the same length. Stacking each column independently (the shape this replaces) put
   * every field at whatever height its own column reached, which is #440 item 1.
   *
   * So each cell declares the LEGACY ROW it sits in and the grid places it there. Rows 7, 9 and 12
   * are deliberately vacant in the Road Information column: they held `ASM Code`, `Soil Moisture
   * Code (ILCR)` and `Boulder Area (%)`, which LD-1/LD-2/LD-3 removed. Reserving the rows rather
   * than closing the gap is what keeps every remaining field with its legacy row-mates — closing it
   * would slide `Side Slope` up beside `Less Bridges($)`. Row 8 is blank in legacy too.
   *
   * DOM order stays column-major, which is the tab order legacy's own `tabindex` scheme asks for:
   * 100s down Road Information, 200s down Sub-Grade, 300s down Additional Stabilizing.
   */
  const roadInformation: readonly Cell[] = [
    { row: 1, node: <Heading>Road Information</Heading> },
    { row: 2, node: text('roadName', 'Road Name', ROAD_NAME_MAX) },
    { row: 3, node: combo('roadLifetimeCode', 'Road Type', codeLists.roadLifetimes) },
    { row: 4, node: <SubHeading>Moisture</SubHeading> },
    // AC5: prefix match, not substring — typing `SBS` must not offer `ESSFmc`.
    {
      row: 5,
      node: combo('becbiogeoCatalogueId', 'BEC Zone', becOptions, { matchMode: 'prefix' }),
    },
    { row: 6, node: combo('relSoilMoistRgmClsCode', 'RSMR Class', codeLists.rsmrClasses) },
    // Row 7 — `ASM Code` (LD-1). Row 8 — blank in legacy. Row 9 — `Soil Moisture Code (ILCR)` (LD-2).
    { row: 10, node: <SubHeading>Shoulder</SubHeading> },
    { row: 11, node: numeric('sideSlopePct', 'Side Slope', '%') },
    // Row 12 — `Boulder Area (%)` (LD-3).
    { row: 13, node: <SubHeading>Material Type</SubHeading> },
    { row: 14, node: numeric('solidRockPct', 'Solid (Hard) Rock', '%') },
    { row: 15, node: numeric('rippableRockPct', 'Rippable Rock', '%') },
    { row: 16, node: numeric('coarsePct', 'Coarse', '%') },
    { row: 17, node: numeric('finePct', 'Fine', '%') },
    { row: 18, node: numeric('organicPct', 'Organic', '%') },
    { row: 19, node: <Derived label="Total (%)" value={String(previewMaterialTotal(form))} /> },
  ]

  const subGrade: readonly Cell[] = [
    { row: 1, node: <Heading>Sub-Grade</Heading> },
    { row: 2, node: numeric('sgLength', 'Length', 'km', 'Sub-Grade Length (km)') },
    {
      row: 3,
      node: numeric('sgSurfaceWidth', 'Surface Width', 'm', 'Sub-Grade Surface Width (m)'),
    },
    { row: 4, node: <SubHeading>Costs</SubHeading> },
    { row: 5, node: numeric('sgActualCost', 'Actual Cost', '$', 'Sub-Grade Actual Cost ($)') },
    { row: 6, node: numeric('sgTtTransfer', 'TtT Transfer', '$', 'Sub-Grade TtT Transfer ($)') },
    {
      row: 7,
      node: numeric('sgOtherTransfer', 'Other Transfer', '$', 'Sub-Grade Other Transfer ($)'),
    },
    {
      row: 11,
      node: (
        <Derived label="Total Costs ($)" value={fmtWholeCost(previewSubGradeTotalCosts(form))} />
      ),
    },
    { row: 12, node: numeric('lessBridges', 'Less Bridges', '$') },
    { row: 13, node: numeric('lessCulverts', 'Less Culverts', '$') },
    { row: 14, node: numeric('lessLandings', 'Less Landings', '$') },
    { row: 15, node: numeric('lessEndHaul', 'Less End Haul', '$') },
    { row: 16, node: numeric('lessOverland', 'Less Overland', '$') },
    { row: 17, node: numeric('lessOtherEng', 'Less OtherEng', '$') },
    {
      row: 18,
      node: <Derived label="Total ($)" value={fmtWholeCost(previewSubGradeTotal(form))} />,
    },
    {
      row: 19,
      node: <Derived label="$/km" value={fmtCurrency(previewSubGradeCostPerLength(form))} />,
    },
  ]

  const additionalStabilizing: readonly Cell[] = [
    { row: 1, node: <Heading>Additional Stabilizing</Heading> },
    {
      row: 2,
      node: combo('stBallastMethodCode', 'Code', codeLists.ballastMethods, {
        accessibleName: 'Ballast Method Code',
      }),
    },
    { row: 3, node: numeric('stLength', 'Length', 'km', 'Additional Stabilizing Length (km)') },
    {
      row: 5,
      node: numeric(
        'stSurfaceWidth',
        'Surface Width',
        'm',
        'Additional Stabilizing Surface Width (m)',
      ),
    },
    // The server replaces the material with `NA` on both `N` and `D`, so offering a choice here only
    // invites one that will be discarded.
    {
      row: 6,
      node: combo('stBallastMaterialCode', 'Type', codeLists.ballastMaterials, {
        disabled: materialForced,
      }),
    },
    { row: 7, node: numeric('stDepth', 'Depth', 'm') },
    { row: 11, node: numeric('stDistanceToSource', 'Distance to Source', 'km') },
    {
      row: 12,
      node: numeric('stActualCost', 'Actual Costs', '$', 'Additional Stabilizing Actual Costs ($)'),
    },
    {
      row: 13,
      node: numeric('stTtTransfer', 'TtT Transfer', '$', 'Additional Stabilizing TtT Transfer ($)'),
    },
    {
      row: 14,
      node: numeric(
        'stOtherTransfer',
        'Other Transfer',
        '$',
        'Additional Stabilizing Other Transfer ($)',
      ),
    },
    {
      row: 15,
      node: <Derived label="Total ($)" value={fmtWholeCost(previewStabilizingTotal(form))} />,
    },
    {
      row: 16,
      node: <Derived label="$/km" value={fmtCurrency(previewStabilizingCostPerLength(form))} />,
    },
  ]

  const column = (cells: readonly Cell[], index: number): ReactNode =>
    cells.map((cell) => (
      <div
        key={`${String(index)}-${String(cell.row)}`}
        className="schedule-10__detail-cell"
        style={at(cell.row, index)}
      >
        {cell.node}
      </div>
    ))

  return (
    <div className="schedule-10__detail-fields">
      <div className="schedule-10__detail-grid">
        {column(roadInformation, 1)}
        {column(subGrade, 2)}
        {column(additionalStabilizing, 3)}
      </div>

      {/*
        Legacy's closing grid, reproduced: `Includes Detailed Engineering Costs:` sits on ONE line
        with its Yes/No menu (#440 item 4), the menu shares its column with the two haul row labels
        below it, and `Distance km` / `Volume(m3)` / `$/m3/km` are printed once as column headings
        over the figures rather than repeated on every control (#440 item 5).
      */}
      <div className="schedule-10__haul-grid">
        <span className="schedule-10__field-label schedule-10__haul-inline-label" style={at(1, 1)}>
          Includes Detailed Engineering Costs:
        </span>
        <div className="schedule-10__haul-eng-cell" style={at(1, 2)}>
          {readOnly ? (
            <span className="schedule-10__field-value">{engineeringCostsValue}</span>
          ) : (
            /*
              The SAME control as every other dropdown on this screen. It was a Carbon `Select`,
              which is a bare `<select>`: it takes `background-color: var(--cds-field)` like the
              rest, but `.cds--select` carries no width of its own and Carbon's `.cds--form-item`
              is `align-items: flex-start`, so it shrank to its content and showed a sliver of
              field grey where `Road Type` and `BEC Zone` beside it show a full one. Two attempts
              to make a `<select>` agree with the combo boxes around it failed; using the combo box
              is the fix, and it leaves one dropdown component on the page instead of two.

              Its label is hidden rather than dropped: the printed one is the span to its left
              (#440 item 4), and the control keeps its accessible name.
            */
            <CodeComboBox
              id={id('detailedEngineeringCostInd')}
              titleText="Includes Detailed Engineering Costs"
              className="schedule-10__haul-eng-combo"
              items={[...YES_NO]}
              selectedCode={form.detailedEngineeringCostInd}
              disabled={disabled}
              // Legacy's menu offers no blank choice and the column is NOT NULL, so clearing
              // returns the field to its default rather than to a value Save could not send.
              onSelect={(code) => onChange('detailedEngineeringCostInd', code === '' ? 'N' : code)}
            />
          )}
          {/* The assembler serves this key (`YES_NO` format) and the first cut of the wiring
              rendered no indicator for it in either mode (review of PR #452). Text comparison:
              the form holds the `Y`/`N` code. */}
          {indicator('detailedEngineeringCostInd', 'Includes Detailed Engineering Costs', false)}
        </div>
        <span className="schedule-10__haul-column-head" style={at(1, 3)}>
          Distance (km)
        </span>
        <span className="schedule-10__haul-column-head" style={at(1, 4)}>
          Volume (m3)
        </span>
        <span className="schedule-10__haul-column-head" style={at(1, 5)}>
          $/m3/km
        </span>

        <span className="schedule-10__haul-row-head" style={at(2, 2)}>
          End Haul Details:
        </span>
        {haulFigure('endHaulDistance', 'End Haul Distance (km)', 2, 3)}
        {haulFigure('endHaulVolume', 'End Haul Volume (m3)', 2, 4)}
        <span className="schedule-10__field-value" style={at(2, 5)}>
          {fmtCurrency(endHaulRate)}
        </span>

        <span className="schedule-10__haul-row-head" style={at(3, 2)}>
          Overland Details:
        </span>
        {haulFigure('overlandDistance', 'Overland Distance (km)', 3, 3)}
        {haulFigure('overlandVolume', 'Overland Volume (m3)', 3, 4)}
        <span className="schedule-10__field-value" style={at(3, 5)}>
          {fmtCurrency(overlandRate)}
        </span>
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

      {/*
        There is NO standing "A material Type is required for this Additional Stabilizing code."
        line here any more (#440 item 8). It was an advance warning for a rule the form already
        enforces where the issue asks for it — on the field: `validateRoadDetail` marks the `Type`
        combo invalid with the server's own `Material Code Type: Value is required.` the moment Save
        is pressed on a `C` (or blank) ballast method. Legacy prints nothing in advance either; its
        `pageDtlASType` simply carries `required="#{...typeMandatory}"` and reports on submit.
      */}
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
